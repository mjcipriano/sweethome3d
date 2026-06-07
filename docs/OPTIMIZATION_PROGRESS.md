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

## Tried And Rejected

Do not repeat these experiments without a materially different design or new
evidence.

| Experiment | Result | Decision |
| --- | --- | --- |
| Hand-written common-case decimal parser in `OBJLoader` | Three scene runs were 13.64 s, 13.96 s, and 17.37 s versus a 13.44 s median with only the reader optimization | Removed because it did not improve the end-to-end workload |
| JetBrains Runtime for Java 3D profiling under WSL | Native Mesa GLX crashes during rendering-context creation | Replaced with standard conda-forge OpenJDK 17 |
| 1920x1080 Java 3D off-screen frame benchmark on the legacy Java 3D/JOGL stack | Native crash in `libGLX_mesa.so` while creating the off-screen context | Keep `scene` mode as the safe default; retry after the graphics stack is upgraded |
| Complete legacy Java 3D JUnit suite on current WSLg/Mesa | JVM terminated in native `libGLX_mesa.so` before JUnit completed | Treat as a scheduled/manual compatibility probe; use `make test-gui` as the required stable GUI suite |

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
| Cross-platform GitHub Actions | Required before merge; record the run or PR check result below |
| Prerelease packaging on Windows, Linux, and macOS | Required after merge through `release.yml` |

## Next Work

Start future work from updated `main` after PR #6 and the prerelease checkpoint
are complete. Use a new branch rather than continuing on the merged checkpoint
branch.

1. Capture interactive startup and EDT latency.
   - Add timestamps for process start, first window shown, home loaded, first
     usable 2D paint, and first usable 3D frame.
   - Use JFR `JavaMonitorWait`, file I/O, allocation, and execution samples to
     identify work blocking the EDT.
2. Profile 2D interaction rather than only full repaint throughput.
   - Measure selection, drag, zoom, pan, wall editing, and invalidated repaint
     area on the complex home.
   - Inspect `PlanComponent`, `IconManager`, geometry `Area` construction, and
     asynchronous top-view icon decode/scale behavior.
3. Profile repeated 3D scene updates after initial construction.
   - Measure camera movement, furniture moves, level visibility changes, and
     texture updates without using the unstable large off-screen frame path.
   - Inspect `HomeComponent3D`, `ModelManager`, transformed geometry, normals,
     triangulation, and Java 3D scene graph update frequency.
4. Upgrade the graphics stack as an isolated project.
   - Upgrade Java 3D, JOGL, GlueGen, vecmath, and native binaries together.
   - Restore the complete Java 3D suite and frame benchmark on Windows, Linux,
     macOS, and WSL before raising the Java source/bytecode baseline.
5. Add controlled performance regression thresholds only after runners and
   workloads are stable enough to avoid noisy failures.

`docs/JAVA_MODERNIZATION.md` defines the larger Java and graphics migration
sequence. A newer JDK alone is not evidence of a speedup.

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
