# Documentation

## Using the application

| Guide | What it covers |
| --- | --- |
| [Getting started](GETTING_STARTED.md) | Install, the window, a first home, where each addition is in the menus |
| [Connecting Claude (MCP server)](MCP_SERVER.md) | Setup with Claude Desktop and Claude Code, the tools, pictures of the plan and the 3D view, security, troubleshooting |
| [Command reference](MCP_COMMANDS.md) | Every edit command of the MCP server and of the design assistant |
| [House modeling tutorial](HOUSE_MODELING_TUTORIAL.md) | A two-storey house with roofs, dormers, a garage and a sloped garden, step by step |
| [Design assistant](AI_ASSISTANT.md) | The chat inside the application, with your own AI provider |
| [Design tools](DESIGN_TOOLS.md) | Replace furniture, room shapes, styles, report and budget, versions, elevation drawings, 3D web page |
| [Tags, layers and furniture made by the AI](LAYERS_AND_FURNITURE_LIBRARIES.md) | Tags and layers of furniture, furniture built from parts, furniture libraries |
| [3D view navigation](VIEW_3D_NAVIGATION.md) | Zoom, standard views, doll house display, 360 degree panorama, room dimensions |
| [OpenGL renderer](OPENGL_RENDERER.md) | The optional renderer of the 3D view: quality options, measurements, limits |
| [Walking through a home in VR](WEBXR_VR.md) | Meta Quest and SteamVR headsets, through the browser |
| [Game mode](GAME_MODE.md) | The home played as a video game in the browser |
| [Review notes](REVIEW_NOTES.md) | Notes pinned during a visit, done by Claude |

Examples: [`examples/demo-house`](examples/demo-house) holds the commands of
the house modeling tutorial and a script that replays them.

## Developing

| Document | What it covers |
| --- | --- |
| [AGENTS.md](../AGENTS.md) | Layout of the sources, build, tests, pull requests, releases, rules for changes |
| [Performance](PERFORMANCE.md) | Benchmarks and how to measure loading, the plan and the 3D view |
| [Optimization progress](OPTIMIZATION_PROGRESS.md) | Ledger of accepted and rejected optimizations, next work |
| [Java modernization](JAVA_MODERNIZATION.md) | The plan for the Java and graphics dependencies |
| [Gap analysis](GAP_ANALYSIS.md) | What comparable programs offer, and what is still missing here |
| [Design assistant ledger](AI_ASSISTANT.md#development-ledger) | Phases, protocol and backlog of the assistant |

## Screenshots

The pictures of these guides are in [`images`](images);
[`tools/screenshots`](tools/screenshots) regenerates them. They show the demo
house of the tutorial in the development build under Linux; the application
looks the same under Windows and macOS apart from the window frame and fonts.
The 3D and plan pictures of the MCP pages are the actual output of
`get_3d_image` and `get_plan_image`.
