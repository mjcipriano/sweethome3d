# AI Design Assistant

This is the handoff source of truth for the AI design assistant feature, the
way `docs/OPTIMIZATION_PROGRESS.md` is for performance work. Update it in the
same commit or pull request as every accepted change, new command, protocol
change, or newly discovered blocker. Do not leave progress only in chat or a PR
comment.

The assistant is reachable from **Help -> Design assistant...**. All of its
logic lives in `viewcontroller` (`AssistantClient`, `AssistantCommandParser`,
`AssistantCommand`, `AssistantCommandExecutor`, `HomeAssistantContext`,
`JSONParser`) and `swing` (`AssistantPanel`), so the package dependency graph is
unchanged. It is multi-provider: any OpenAI-compatible endpoint (OpenRouter,
DeepSeek, LM Studio, Ollama) or the Anthropic API. Provider, URL, key and model
persist in user preferences (BYO key; an empty key works for local servers).

## Design constraints

- Java 8 source level, ISO-8859-1 encoding for existing files, no new runtime
  dependencies (the assistant uses only the JDK and the in-repo `JSONParser`).
- Keep model/Swing/Java3D types out of `viewcontroller` where avoidable; the
  executor talks to the existing `HomeController` sub-controllers.
- Every edit the assistant makes is one undoable turn: the whole turn is wrapped
  in `UndoableEditSupport.beginUpdate()/endUpdate()` so a single Edit > Undo
  reverts it.
- The model receives a plain-text project brief (`HomeAssistantContext`) plus a
  JSON command protocol; it replies with `{"reply": "...", "commands": [...]}`.
  The parser is tolerant of prose / Markdown fences and falls back to plain text.

## Status

### Phase 1 - read-only Q&A (DONE, shipped in v7.13.0)

`HomeAssistantContext.describeHome` builds a brief (levels, rooms + areas,
walls + length, furniture counts, largest model). `AssistantClient` does a single
request/response round-trip. Chat panel with in-panel Settings. Unit tests for
both provider protocols and the JSON reader.

### Phase 2 - editing command layer with grouped undo (DONE, shipped in v7.13.0)

Add-only command protocol: `add_furniture`, `add_door_or_window`, `add_room`,
`add_wall`, `select`. `AssistantCommandParser` extracts the protocol;
`AssistantCommandExecutor` applies it through the real controllers as one
undoable turn. Context includes the current selection so relative placement
works. Tests: parser (plain/JSON/fenced) and executor (add room + walls, one
undo reverts the turn).

### Phase 3 - modify existing homes, agentic, streaming (DONE, pending release)

Requested as one phase with three workstreams, built in dependency order:

- **3a - modify/delete (CRUD). DONE.** The assistant can act on items that
  already exist, not just add new ones. Commands: `move`, `rotate`, `resize`,
  `set_color`, `set_elevation`, `set_visible`, `rename`, `delete`. Targeting:
  existing items are referenced by a stable per-turn id exposed in the context
  (`F1` furniture, `W1` walls, `R1` rooms), by `"target":"selection"`, or by the
  `names` field. Property changes post a snapshot-based undoable edit
  (`ItemState` before/after); deletes reuse `PlanController.deleteItems`. The
  whole turn stays one undo. `HomeAssistantContext` lists each room (R#), wall
  (W#) and furniture item (F#, with position/angle/size); furniture is capped at
  80 listed items.
- **3b - agentic multi-step loop. DONE.** `AssistantPanel.runConversation`
  loops: after a turn's commands are applied it feeds back the applied-changes
  summary (with a refreshed project brief in the system prompt) and re-invokes
  the model, up to `MAX_ASSISTANT_STEPS` (5), stopping when the model returns no
  further commands. Provider-agnostic; network runs off the EDT, home reads/edits
  are marshalled onto it with `invokeAndWait`.
- **3c - streaming + chat UX. DONE.** `AssistantClient.sendMessageStreaming`
  reads server-sent events for both providers (`parseStreamDelta` is unit-tested
  for the OpenAI `choices[].delta.content` and Anthropic `content_block_delta`
  shapes) and forwards chunks to a `StreamHandler`; the panel streams them into
  the transcript and replaces the streamed text with the finalized reply +
  applied-changes footer. A "Preview edits before applying" checkbox shows a
  confirmation listing the commands before any edit runs; declining stops the
  multi-step loop. Remaining polish: rich Markdown rendering in the transcript
  (currently a wrapped `JTextArea`).

### Phase 4 - grounded, reliable, deeper edits (4a, 4b DONE)

The next increment focuses on making edits land on the right items and reach the
features that make Sweet Home 3D distinctive. Build in dependency order:

- **4a - catalog grounding. DONE.**
  - `HomeAssistantContext.describeCatalog` lists every furniture category and up
    to 60 item names per category in the system prompt;
    `HomeAssistantContext.buildSystemPrompt` (shared by `AssistantPanel` and the
    live tests) assembles role + protocol + catalog + project brief.
  - New `search_catalog` command (`{"action":"search_catalog","query":"..."}`):
    its matches are appended to the applied-changes summary, which the agentic
    loop (3b) already feeds back, so the model can search then add.
  - `findCatalogPiece` is now scored token matching (exact > all-words-present >
    substring > partial word overlap; plural "s" ignored; concise names
    preferred) instead of first-substring-wins. Unmatched `add_furniture` names
    report the 3 closest catalog names back to the model for self-correction.
  - Per-turn safety limits (the 4c item, pulled forward): max 30 commands per
    turn, coordinates beyond 1,000,000 cm and sizes beyond 100,000 cm rejected
    with a note in the summary. The protocol text documents the coordinate
    system (y down, north = -y; angle 0 faces +y) and door-on-wall placement.
  - `AssistantClient` now sends `max_tokens` 4096 (1024 could truncate
    multi-command JSON) and `temperature` 0.2 on both provider protocols.
  - Tests: scored matching, search summary, suggestions and safety limits in
    `AssistantCommandExecutorTest`; catalog brief/prompt in
    `HomeAssistantContextTest`; **`AssistantCannedProtocolTest`** runs the whole
    pipeline (request building, HTTP round-trip, parsing, execution) against a
    local mock chat-completions server returning canned replies captured from a
    real DeepSeek session, so the regular suites validate end-to-end behavior
    with **no network and no API key**. `AssistantDeepSeekLiveTest`
    (`make test-assistant-live`) is the optional live re-validation: opt-in via
    `-Dsweethome3d.liveAssistantTests=true`, key from the `DEEPSEEK_API` env var
    or a git-ignored `.env` at the repo root, skipped via JUnit assumptions
    otherwise. **The key stays local: never commit it, never wire it into CI;
    canned tests are the shared regression gate.** Verified live 2026-06-12:
    DeepSeek (`deepseek-chat`) grounds names to the exact catalog vocabulary
    and issues correct id-targeted relative moves.
- **4b - reduce-detail (LOD) editing. DONE.** `reduce_detail` / `restore_detail`
  commands target pieces by id, `names`, `"target":"selection"` or
  `"target":"all"` ("all" is honored only for these reversible toggles) and
  flip the per-piece `ModelLOD.LOW_POLY_PROPERTY` through the same
  snapshot-based undoable turn (the `ItemState` snapshot now includes the
  low-poly flag). The brief marks each piece `reduced model available` /
  `reduced detail ON` and reports models >= 1 MB, so the model knows which
  pieces are heavy and switchable; pieces without a generated LOD are reported
  back ("run 3D view > Generate model LOD cache") because LOD *generation*
  needs Java 3D and stays out of `viewcontroller`. Tests: executor toggle/
  undo/"all"/missing-LOD note, brief markers, and a live DeepSeek scenario
  ("the 3D view is slow") that produced a correct targeted `reduce_detail`.
- **4c - deeper geometry & appearance.** `duplicate`, `group`/`ungroup`,
  `set_texture`/material on furniture and walls, wall endpoint moves
  (`move_wall_point`), and room vertex edits - all through the same
  snapshot-based undoable turn. Add per-turn safety limits (max commands,
  coordinate sanity vs. plan bounds) so a bad reply can't make a huge mess.

### Backlog / later

- **Native tool/function calling.** Use Anthropic `tool_use` and OpenAI `tools`
  so commands arrive as structured arguments instead of JSON-in-prose, removing
  the tolerant text parsing and improving reliability.
- **Markdown transcript.** Replace the `JTextArea` with an HTML-rendering view
  and light Markdown formatting (the 3c polish item).
- **Conversation management.** "New chat" / clear, and optional save/restore of
  the transcript with the home.
- **Vision.** Send a rendered plan/3D snapshot to multimodal endpoints so the
  model can "see" the layout it is editing.
- **Cost & model feedback.** Show token usage / model name and surface provider
  errors (rate limits, bad key) more clearly than the current generic message.
- **Multi-level awareness.** Let the assistant target and report per `Level`,
  not just the selected level.

## Testing

```bash
make build
make test TEST_SOURCES="\
  test/com/eteks/sweethome3d/junit/AssistantCommandParserTest.java \
  test/com/eteks/sweethome3d/junit/AssistantCommandExecutorTest.java \
  test/com/eteks/sweethome3d/junit/AssistantClientTest.java \
  test/com/eteks/sweethome3d/junit/HomeAssistantContextTest.java"
make test-assistant-live   # real DeepSeek round-trips; needs DEEPSEEK_API or .env
```

`make test-gui` is required for `AssistantPanel` changes (Swing). Two unrelated
GUI tests (`PrintTest`, `TransferHandlerTest`) are known to flake on focus/dialog
timing under WSLg/Xvfb and are not caused by assistant changes.

Manual smoke test: configure a local LM Studio or Ollama endpoint in Settings,
open a home, and try prompts such as "what is the total floor area?",
"add a chair to the right of the selection", "move the sofa 50cm north",
"delete all chairs".
</content>
