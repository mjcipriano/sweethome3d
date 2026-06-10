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

### Phase 3 - modify existing homes, agentic, streaming (IN PROGRESS)

Requested as one phase with three workstreams, built in dependency order:

- **3a - modify/delete (CRUD).** Lets the assistant act on items that already
  exist, not just add new ones. New commands: `move`, `rotate`, `resize`,
  `set_color`, `set_elevation`, `set_visible`, `rename`, `delete`. Targeting:
  existing items are referenced by a stable per-turn id exposed in the context
  (`F1` furniture, `W1` walls, `R1` rooms), by name, or by the literal
  `selection`. Property changes post a custom undoable edit that snapshots the
  affected items' before/after state; deletes reuse `PlanController.deleteItems`.
  `HomeAssistantContext` lists each item with its id and current geometry.
- **3b - agentic multi-step loop.** After a turn's commands are applied, the
  updated project brief + a short result summary are fed back and the model is
  re-invoked, up to a small iteration cap, until it returns no further commands.
  Provider-agnostic (no native tool-calling required); the cap and a stop signal
  prevent runaway loops.
- **3c - streaming + chat UX.** Token streaming (SSE) for both providers, a
  proper transcript rendering, and a preview/confirmation step before edits are
  applied.

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
