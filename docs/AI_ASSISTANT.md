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

## Testing

```bash
make build
make test TEST_SOURCES="\
  test/com/eteks/sweethome3d/junit/AssistantCommandParserTest.java \
  test/com/eteks/sweethome3d/junit/AssistantCommandExecutorTest.java \
  test/com/eteks/sweethome3d/junit/AssistantClientTest.java \
  test/com/eteks/sweethome3d/junit/HomeAssistantContextTest.java"
```

`make test-gui` is required for `AssistantPanel` changes (Swing). Two unrelated
GUI tests (`PrintTest`, `TransferHandlerTest`) are known to flake on focus/dialog
timing under WSLg/Xvfb and are not caused by assistant changes.

Manual smoke test: configure a local LM Studio or Ollama endpoint in Settings,
open a home, and try prompts such as "what is the total floor area?",
"add a chair to the right of the selection", "move the sofa 50cm north",
"delete all chairs".
</content>
