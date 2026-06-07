# Optimization Progress

This file is the durable handoff ledger for performance and modernization work.
Read it before starting an optimization branch and update it before every
optimization pull request is merged. Detailed benchmark commands and historical
measurements remain in `docs/PERFORMANCE.md`.

## Current Checkpoint

- Updated: 2026-06-07
- Checkpoint branch: `perf/modernize-and-optimize`
- Pull request: <https://github.com/mjcipriano/sweethome3d/pull/6>
- Target: merge this checkpoint to `main`, then publish `7.5.1-beta.1` as a
  GitHub prerelease.
- Reference workload:
  `example-files/2025-11-27-House-Layout-v3.sh3d`
- Reference workload shape: 141 MB, 435 furniture items, 162 walls, 47 rooms,
  8 levels, 435 model references, and 148 distinct model contents.
- Development runtime: the pinned `sweethome3d` Conda environment using
  standard conda-forge OpenJDK 17.
- Graphics environment used for local measurements: WSLg direct rendering,
  D3D12, NVIDIA GeForce GTX 1660 Ti, Mesa 21.2.6.

Do not commit the reference home unless its size and license are explicitly
approved. The `example-files/` directory is ignored.

## Completed And Accepted

| Area | Change | Result | Commit |
| --- | --- | --- | --- |
| Home loading memory | Canonicalize embedded content with digest-keyed lookup | Retained load heap reduced from 199-203 MB to 112-114 MB, about 44% | `0b00814` |
| Home loading speed | Skip redundant eager ZIP inflation when a modern local archive has a readable digest manifest | Warm load reduced from 2.46-2.99 s to 1.50-1.86 s, about 35-45% | `4d5d8c3` |
| 2D measurement | Add a warmed, repeatable 1920x1080 plan-paint benchmark with optional JFR | Current warmed medians are 9-12 ms and p95 results are 11-15 ms | `35585b7`, `3573e1b` |
| Toolchain | Pin OpenJDK 17 and developer/runtime dependencies in `environment.yml` | Build, JFR, Git, GitHub CLI, and Java 3D runtime dependencies resolve from one Conda environment | `9e37c1d` |
| OBJ/MTL loading | Replace synchronized `BufferedReader` with a 64 KiB unsynchronized reader in the thread-confined parser | Controlled scene-construction median reduced from 15.09 s to 13.44 s, about 11% | `3573e1b` |
| 3D measurement | Add Java 3D scene/frame benchmark and optional JFR capture | Safe scene mode works with the complex reference home | `3573e1b` |
| Startup measurement | Add a cold-start phase benchmark (`make benchmark-startup`) covering prefs init, home load, plan creation, and first 2D paint, with optional JFR | Cold pass on the reference home is about 2.0 s total; cold first paint is about 0.46 s; harness is headless and adds no app behavior (task A1) | merged PR #7 |
| 2D interaction measurement | Add an interaction benchmark (`make benchmark-plan-interaction`) reporting apply cost, invalidated repaint area, and paint cost for select/move/zoom | Found that every interaction invalidates the full 1920x1080 viewport (`dirty_pct=100`), so a single-piece selection costs a full ~9-10 ms repaint; `PlanComponent` has no targeted `repaint(rectangle)` calls (task A2) | merged PR #9 |
| 2D interaction | Repaint only the area of the previously and newly selected items (plus an indicator margin) on selection change, instead of the whole plan | Selecting a single piece dropped from invalidating 100% of the viewport to ~1% and from a ~9-10 ms paint to sub-ms; pixel-coverage check and GUI suite confirm no artifacts (task C1, selection) | _pending_ |

## Tried And Rejected

Do not repeat these experiments without a materially different design or new
evidence.

| Experiment | Result | Decision |
| --- | --- | --- |
| Hand-written common-case decimal parser in `OBJLoader` | Three scene runs were 13.64 s, 13.96 s, and 17.37 s versus a 13.44 s median with only the reader optimization | Removed because it did not improve the end-to-end workload |
| JetBrains Runtime for Java 3D profiling under WSL | Native Mesa GLX crashes during rendering-context creation | Replaced with standard conda-forge OpenJDK 17 |
| 1920x1080 Java 3D off-screen frame benchmark on the legacy Java 3D/JOGL stack | Native crash in `libGLX_mesa.so` while creating the off-screen context | Keep `scene` mode as the safe default; retry after the graphics stack is upgraded |
| Complete legacy Java 3D JUnit suite on current WSLg/Mesa | JVM terminated in native `libGLX_mesa.so` before JUnit completed | Treat as a scheduled/manual compatibility probe; use `make test-gui` as the required stable GUI suite |
| OpenJDK 21 runtime vs 17 on the headless benchmarks (task A4) | Interleaved 4-round A/B on the reference home showed no repeatable difference: plan-render warmed median ~15 ms on both; startup cold ~2.16 s on both. Run-to-run variance on the WSL host (e.g. 2.0-3.4 s cold-start swings) swamps any JDK delta. No crashes or regressions on 21 | Keep the pinned OpenJDK 17; do not add a Java 21 dev toolchain without a materially quieter measurement host or a different workload. Bytecode stays at Java 8 either way |

Generated `hs_err_pid*.log` files are diagnostic artifacts and must not be
committed.

## Validation At This Checkpoint

| Check | Result |
| --- | --- |
| `scripts/setup-conda-env.sh` | Passed; reconciled the existing environment from `environment.yml` |
| Ant production build on OpenJDK 17 | Passed |
| `make test-core` | Passed, 7 tests |
| `make test-gui` on WSLg | Passed, 13 tests |
| `make test-local-check` | Passed with direct NVIDIA-backed rendering |
| `make benchmark-plan-render ...` | Passed; latest run median 9 ms, p95 11 ms |
| `make benchmark-home-3d ... BENCHMARK_MODE=scene` | Passed; final confirmation run 14.95 s |
| `ant -Dversion=7.5.1-beta.1 jarExecutable` | Passed; produced the prerelease executable JAR |
| Complete `make test-local TEST_DISPLAY_MODE=display` | Native Mesa GLX crash; known legacy graphics-stack limitation |
| Cross-platform GitHub Actions | Passed on Windows, Linux, macOS, and Linux GUI in [CI run 15](https://github.com/mjcipriano/sweethome3d/actions/runs/27095017708) |
| Prerelease packaging on Windows, Linux, and macOS | Required after merge through `release.yml` |

## Next Work

Start future work from updated `main`. Use a new `perf/<scope>` branch per
task rather than continuing on a merged checkpoint branch. The backlog below is
organized into workstreams; the design rationale, file pointers, and acceptance
criteria for each task live in the working plan. Order is A first, then B/C/D/E
in parallel, then F.

**Workstream A - Measurement foundations**

- A1. Startup/EDT timeline benchmark - **done in this branch** (`make
  benchmark-startup`). Cold pass on the reference home is about 2.0 s with a
  cold first paint near 0.46 s.
- A2. 2D interaction micro-benchmark - **done** (`make
  benchmark-plan-interaction`). Finding: every interaction currently
  invalidates the full viewport, which makes C1 the highest-value 2D task -
  give `PlanComponent` targeted `repaint(rectangle)` calls for localized
  interactions (selection, single-piece move) so a small change stops paying a
  full-frame repaint.
- A3. 3D scene-update micro-benchmark (camera move, furniture move, level
  visibility, texture update) in `scene` mode only, avoiding the unstable large
  off-screen frame path. Inspect `HomeComponent3D` and `ModelManager`.
- A4. Evaluate the Java 21 runtime against Java 17 - **done; rejected**. No
  repeatable win on the WSL host (see Tried And Rejected); keep OpenJDK 17.
  Re-run only on a quieter measurement host. A newer JDK alone is not evidence
  of a speedup. Note: the WSL host's run-to-run variance is large enough that
  small (sub-15%) wins are not measurable here; controlled regression
  thresholds (F2) need a quieter runner.

**Workstream B - Startup and EDT latency** (B1 move blocking work off the EDT;
B2 defer/parallelize startup init). Depends on A1.

**Workstream C - 2D interaction latency** (C1 shrink invalidated repaint scope -
selection done; the furniture-move/revalidate path and zoom remain; C2 cut
per-paint allocations and geometry rebuilds; C3 top-view icon decode/scale
path). Depends on A2.

**Workstream D - 3D scene, model loading and memory** (D1 model
load/clone/bounds caching audit; D2 parallelize model loading across cores; D3
reduce per-frame allocations in scene updates; D4 GPU-friendly geometry
construction). Depends on A3.

**Workstream E - Graphics environment tuning** (E1 central Windows+NVIDIA
auto-tuner with kill-switch and stock fallback, behind one class called from
`SweetHome3DBootstrap.main`; E2 measure each candidate property on
Windows+NVIDIA; E3 discrete-GPU launcher preference). Depends on A3.

**Workstream F - Graphics stack upgrade** (F1 unify/upgrade Java 3D, JOGL,
GlueGen, vecmath and native binaries together to stop the Mesa GLX crash and
restore the frame benchmark and full Java 3D suite; F2 3D regression
thresholds). Sequenced after the app-level wins.

`docs/JAVA_MODERNIZATION.md` defines the larger Java and graphics migration
sequence.

## Handoff Checklist

Every optimization agent must complete this list before handing work off:

- [ ] Read `AGENTS.md`, this file, `docs/PERFORMANCE.md`, and
  `docs/JAVA_MODERNIZATION.md`.
- [ ] Pull `main`, create a new `perf/<scope>` branch, and confirm the worktree
  does not contain unrelated user changes.
- [ ] Record the exact baseline command, runtime, hardware, workload, sample
  count, median, and p95 where applicable.
- [ ] Add the experiment to either **Completed And Accepted** or
  **Tried And Rejected**.
- [ ] Update **Validation At This Checkpoint** and **Next Work**.
- [ ] Add or update functional tests for changed behavior.
- [ ] Run the validation matrix required by `AGENTS.md`.
- [ ] Commit only intended files with a conventional commit.
- [ ] Push the branch and create or update a draft PR with measured results.
- [ ] Leave the repository with no generated crash reports or benchmark
  profiles staged.
