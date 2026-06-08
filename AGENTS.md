# AGENTS.md

## Repository Overview

This repository contains Sweet Home 3D 7.5, a Java Swing desktop interior-design
application. It is a legacy, non-modular Java codebase built with Apache Ant.
Platform-specific Java 3D, JOGL, and YafaRay native libraries are checked into
`lib/`, so do not replace or upgrade them casually.

The source encoding is ISO-8859-1. Preserve that encoding when editing existing
Java and properties files. New automation and documentation files should use
UTF-8/ASCII.

## Important Directories

- `src/com/eteks/sweethome3d/model`: domain model and persisted home state.
- `src/com/eteks/sweethome3d/viewcontroller`: UI-independent controllers and
  view interfaces.
- `src/com/eteks/sweethome3d/swing`: Swing views, plan rendering, dialogs, and
  desktop integration.
- `src/com/eteks/sweethome3d/j3d`: Java 3D scene construction, model loading,
  OBJ export, and photo renderers.
- `src/com/eteks/sweethome3d/io`: `.sh3d` persistence, XML import/export, and
  catalog loading.
- `src/com/eteks/sweethome3d/tools`: platform and resource utilities.
- `test/com/eteks/sweethome3d/junit`: JUnit 3-style tests run through JUnit 4.
- `lib`: bundled Java and native runtime dependencies.
- `install`: legacy installer definitions, icons, launchers, and templates.
- `pluginsrc`: separately packaged plugin sources.
- `.github/workflows`: CI and release automation.
- `docs/OPTIMIZATION_PROGRESS.md`: current optimization checkpoint, accepted
  and rejected experiments, validation state, and prioritized next work.

The intended dependency direction is documented and enforced by
`PackageDependenciesTest`: model is at the bottom; tools, controllers, Swing,
Java 3D, I/O, and the application layer build above it. Keep Swing and Java 3D
types out of the model package.

## Build And Test

Use the repository's `sweethome3d` Conda environment for the JDK and developer
tools. It pins a standard conda-forge OpenJDK 17 build because the JetBrains
Runtime may crash in Mesa GLX while Java 3D creates a rendering context.

```bash
scripts/setup-conda-env.sh
conda activate sweethome3d
```

The environment includes OpenJDK, JFR, Ant, Make, CMake, a C++ compiler, Git,
GitHub CLI, and the AWT/X11 libraries required by Java 3D. Linux GUI tests require WSLg/X11 or
Xvfb. The host must provide `xdpyinfo`, `glxinfo`, and optionally `Xvfb`
because conda-forge doesn't publish current runnable packages for these
diagnostic/server commands.

```bash
make build       # Compile application/resource JARs
make model-lod-native  # Build the meshoptimizer native LOD library
make jar         # Build install/SweetHome3D-7.5.jar
make test-core   # Deterministic model/platform tests; no display required
make test-gui    # Swing/controller tests; display required, known failing Java 3D tests excluded
make test-local-check  # Verify local X11 and GLX support
make test-wsl-gpu BENCHMARK_HOME=<file.sh3d>  # WSLg D3D12 Java 3D smoke test
make test-local  # Complete suite on Linux or WSL
make run         # Run the executable JAR
```

JUnit and Hamcrest are downloaded into `libtest/` on first test run. Override
`VERSION` for packaging, for example `make jar VERSION=7.5.1`. Developers using
a Conda environment may set `CONDA_ACTIVATE` to the activation command.

### WSL And VS Code

WSL2 with WSLg is the preferred local graphics test environment. From the VS
Code WSL terminal:

```bash
sudo apt-get update
sudo apt-get install -y x11-utils mesa-utils libgl1-mesa-dri xvfb
scripts/setup-conda-env.sh
conda activate sweethome3d
make test-local-check
make test-wsl-gpu BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d
make test-local
```

`test-local` uses the existing WSLg display when `DISPLAY` and GLX work. Force
a mode while troubleshooting:

```bash
make test-local TEST_DISPLAY_MODE=display  # Require WSLg/current X server
make test-local TEST_DISPLAY_MODE=xvfb     # Start a private Xvfb server
LIBGL_ALWAYS_SOFTWARE=1 make test-local    # Force Mesa software rendering
make test-local TEST_JAVA=/path/to/java    # Override only the test JVM
```

Use `make test-wsl-gpu` when validating Java 3D work under WSLg. It requires a
real WSLg display, rejects software Mesa renderers, verifies the GLX renderer is
the WSLg D3D12 path by default, runs the safe Java 3D scene-update benchmark,
and runs the on-screen FPS smoke benchmark to confirm the application's own
`Canvas3D` renders frames on the same GPU path. This is a correctness and smoke
test for WSL; native Windows OpenGL performance still needs the packaged
Windows app or a Windows-native benchmark run.

#### Measuring real NVIDIA OpenGL FPS from WSL

WSLg renders through Mesa's D3D12 translation layer, whose frame rate is noisy
and not representative of native Windows OpenGL. To measure the actual GPU, run a
Windows JDK against the build through WSL's Windows interop (`cmd.exe` /
`powershell.exe`). This is how 3D frame-rate changes were measured for the
display-list and antialiasing work.

1. `make build`, then compile the benchmark into `build/performance-classes`:
   `javac -source 8 -target 8 -encoding ISO-8859-1 -cp "build/SweetHome3D.jar:build/Furniture.jar:build/Textures.jar:build/Examples.jar:build/Help.jar:lib/java3d-1.6/*:lib/*:test" -d build/performance-classes test/com/eteks/sweethome3d/performance/Home3DFpsBenchmark.java`
2. Stage to a local Windows folder (loading native DLLs over `\\wsl$` fails), for
   example `cp`-ing into `/mnt/c/sh3d-bench`: the `build/*.jar` dev jars,
   `build/performance-classes`, the Java 3D 1.6 jars `lib/java3d-1.6/*.jar` (not
   the legacy `lib/j3dcore.jar`/`vecmath.jar`), the non-3D `lib/*.jar` (batik,
   jeksparser, iText, freehep, sunflow, jmf), the
   `lib/java3d-1.6/windows/amd64/*.dll` JOGL natives,
   `lib/windows/x64/j3dcore-ogl.dll`, and the `.sh3d` home.
3. Run a Windows JDK with the natives on `-Djava.library.path` (Java 8 needs no
   `--add-opens`; a Java 11+ JDK needs the same `--add-opens`/`--add-exports` the
   packaged app uses, and enables JFR). The benchmark prints the OpenGL renderer
   (confirming the GPU, e.g. `NVIDIA GeForce GTX 1660 Ti`) and a deterministic
   average FPS from a fixed camera sweep, plus the view-dependent min/max:
   `cmd.exe /c '"C:\Program Files\Java\<jdk>\bin\java.exe" -Xmx2g -Djava.library.path=C:\sh3d-bench\natives -Djogamp.gluegen.UseTempJarCache=false -cp "C:\sh3d-bench\app\*;C:\sh3d-bench\perf;C:\sh3d-bench\j3d\*;C:\sh3d-bench\libs\*" com.eteks.sweethome3d.performance.Home3DFpsBenchmark C:\sh3d-bench\home.sh3d 16'`
   A Windows executable may also be invoked directly from Bash. Quote every
   Windows argument containing backslashes so Bash doesn't remove them:
   `'/mnt/c/Program Files/Java/<jdk>/bin/java.exe' -Xmx2g '-Djava.library.path=C:\sh3d-bench\natives' -Djogamp.gluegen.UseTempJarCache=false -cp 'C:\sh3d-bench\app\*;C:\sh3d-bench\perf;C:\sh3d-bench\j3d\*;C:\sh3d-bench\libs\*' com.eteks.sweethome3d.performance.Home3DFpsBenchmark 'C:\sh3d-bench\home.sh3d' 16 30`
   The third benchmark argument is the warm-up duration in seconds.
4. A/B Java 3D properties by prepending them, for example
   `-Dj3d.displaylist=true` or `-Dcom.eteks.sweethome3d.j3d.compileScene=false`.
   Run each two or three times; native frame rate is view-dependent and varies
   run to run, so compare medians, not single runs.

Avoid JetBrains Runtime for the complete Java 3D suite under Linux/WSL. It may
crash in Mesa's `libGLX_mesa.so` while Java 3D creates a rendering context.
The local runner and 3D benchmark reject JBR by default. Update
`environment.yml` whenever developer tools or native runtime libraries are
added to the `sweethome3d` environment.

The required Linux CI GUI suite uses `make test-gui`. The legacy Java 3D native
suite is a scheduled/manual compatibility probe until Java 3D and JOGL are
upgraded; current Mesa/GLX combinations may terminate the JVM before JUnit can
report or skip a test.

If `make test-local-check` cannot connect to `DISPLAY=:0`, restart WSL from
Windows with `wsl --shutdown`, reopen the folder through VS Code's WSL
extension, and retry. Do not run GUI tests from a plain Windows VS Code terminal
that merely invokes `wsl.exe`; use a terminal owned by the WSL extension.

Do not rely on files under ignored `build/`, `classes/`, or `release/`.

## Agent Continuation Protocol

For performance or modernization work, read these files in order:

1. `AGENTS.md`
2. `docs/OPTIMIZATION_PROGRESS.md`
3. `docs/PERFORMANCE.md`
4. `docs/JAVA_MODERNIZATION.md`

`docs/OPTIMIZATION_PROGRESS.md` is the handoff source of truth. Update it in
the same commit or pull request as every accepted optimization, rejected
experiment, benchmark change, dependency migration, or newly discovered
blocker. Do not leave progress only in chat, a local profile, or a PR comment.

Before editing:

```bash
conda activate sweethome3d
git status --short
git checkout main
git pull --ff-only origin main
git checkout -b perf/<short-scope>
```

Never discard an existing dirty worktree. Treat unrecognized changes as
user-owned, inspect them, and stage only files that belong to the task. After a
checkpoint branch is merged, continue on a new branch from updated `main`.

For each performance hypothesis:

1. Capture a repeatable baseline on the same runtime, hardware, workload, and
   benchmark command.
2. Record sample count and median; record p95 for interaction/frame timings.
3. Change one meaningful variable at a time.
4. Run focused functional tests and the same benchmark.
5. Keep the change only when the result is repeatable and behavior remains
   correct. Record unsuccessful experiments in the progress ledger.
6. Update documentation before committing.

Profiles belong in ignored `profiles/`. Do not commit `.sh3d` benchmark files,
JFR recordings, native crash logs, core dumps, or generated build/release
artifacts.

## Git And Pull Requests

Use conventional commits because release-please derives versions and release
notes from them:

- `perf:` for measured runtime or memory improvements.
- `fix:` for user-visible correctness fixes.
- `feat:` for new user-visible capabilities.
- `test:` for test-only changes.
- `build:` or `ci:` for toolchain and workflow changes.
- `docs:` for documentation-only changes.
- Add `!` or a `BREAKING CHANGE:` footer only for an intentional incompatible
  change.

Before committing:

```bash
git diff --check
make test-core
make test-gui                 # Required for Swing/controller changes
make test-local-check         # Required before local Java 3D work
```

Run the relevant benchmark from `docs/PERFORMANCE.md` after any hot-path
change. The complete `make test-local` suite is a compatibility probe while the
legacy graphics stack can crash in native Mesa GLX; a native crash does not
replace the required stable `make test-gui` suite.

For model LOD work, `make model-lod-native` builds the meshoptimizer native
library from `native/model-lod` with the Conda CMake/C++ toolchain. `make jar`
embeds the platform native library under `native/<os>/x64/` in the executable
JAR; `ModelLODGenerator` first tries `java.library.path`, then extracts the
embedded library. Run
`make test TEST_SOURCES="test/com/eteks/sweethome3d/junit/ModelLODTest.java test/com/eteks/sweethome3d/j3d/ModelLODGeneratorTest.java"`
for focused persistence/native checks, then validate the manual UI path by
opening the reference home, choosing `3D view > Generate model LOD cache`,
saving, reopening, and confirming high-vertex furniture renders from the cache.

For complex-home FPS comparisons, set `BENCHMARK_WARMUP_SECONDS` (normally 30)
so asynchronous model insertion is distinguished from the measured interval.
Record both the warm-up and measurement durations.

Stage explicit paths, commit a coherent unit, push the branch, and create or
update a draft PR:

```bash
git add <intended-files>
git commit -m "perf: describe the measured change"
git push -u origin "$(git branch --show-current)"
gh pr create --draft --base main --fill
```

If a PR already exists, update its description with the workload, baseline,
result, tests, known limitations, and remaining work. Check CI before merge:

```bash
gh pr checks <number> --watch
```

Do not push directly to `main`, force-push shared branches, create release tags
manually, or commit unrelated user files.

## Release Automation

`release-please` manages semantic versions, `CHANGELOG.md`, tags, and GitHub
releases from conventional commits merged to `main`:

- `fix:` creates a patch release candidate.
- `feat:` creates a minor release candidate.
- `feat!:` / `fix!:` or a `BREAKING CHANGE:` footer creates a major release
  candidate.

Only `fix:`, `feat:`, and breaking changes trigger a release. `perf:`, `test:`,
`docs:`, `build:`, `ci:`, `refactor:`, and `chore:` commits do **not** open a
release PR on their own, so a `perf:`-only change merges to `main` with no
prerelease. When such a change should still produce a downloadable build, add a
`Release-As: <version>` footer to a commit in the branch (for example
`Release-As: 7.5.4-beta.1`); release-please then opens a release PR for exactly
that version regardless of the commit types. Use a prerelease suffix
(`-beta.N`) for test builds. This is the usual mechanism for shipping testable
performance prereleases.

After qualifying commits (or a `Release-As:` footer) reach `main`, the Release
workflow opens or updates a release PR. Merging that PR creates the version tag and GitHub release, then
builds and attaches the Windows, Linux, macOS, executable JAR, and checksum
artifacts. `version.txt` is the canonical version; release-please also updates
the annotated values in `Makefile` and `build.xml`. Packaged JAR manifests and
native launcher JVM options must carry that same version so the About dialog
matches the GitHub release. `scripts/verify-release.ps1` enforces the executable
JAR manifest value.

`workflow_dispatch` remains available for an explicit emergency SemVer release.
It accepts stable versions such as `7.5.1` and prerelease versions such as
`7.5.1-beta.1`. Set `prerelease=true` for prerelease versions. Run it from
`main` after the checkpoint PR is merged:

```bash
gh workflow run release.yml --ref main \
  -f version=7.5.1-beta.1 \
  -f prerelease=true
```

Wait for all platform packaging jobs to pass, then verify the GitHub release is
marked as a prerelease and contains the Windows, Linux, macOS, executable JAR,
and checksum assets. Record the release URL and validation result in
`docs/OPTIMIZATION_PROGRESS.md`.

`ci.yml` runs core tests on Windows, Linux, and macOS plus the non-Java3D GUI
suite on Linux/Xvfb. The legacy Java 3D compatibility probe runs on schedule
and manual dispatch.

`release.yml` builds:

- a self-contained executable JAR;
- a Windows x64 runnable ZIP with a bundled Java runtime;
- a Linux x64 runnable tarball with a bundled Java runtime;
- a macOS x64 runnable tarball containing an `.app` and bundled runtime;
- SHA-256 checksums.

Release tags are created by release-please rather than pushed manually. Manual
dispatch publishes the requested SemVer directly. Packages are currently
unsigned. Windows code signing and Apple signing/notarization require
repository secrets and should be added as separate guarded steps.

To reproduce a platform bundle locally:

```powershell
./scripts/package-release.ps1 -Version 7.5.0
./scripts/verify-release.ps1 -Version 7.5.0 -ArtifactDirectory release
```

## Change Guidelines

- Keep Java language compatibility at Java 8 unless deliberately changing the
  product baseline. The current Ant build uses `source`/`target` 1.8 on modern
  JDKs.
- Preserve GPL and third-party license files in every distribution.
- Do not commit downloaded JUnit JARs or generated release artifacts.
- Avoid broad formatting changes in legacy Java files.
- Add focused tests for model/controller/I/O changes. Rendering changes should
  include a deterministic scene or image assertion where practical.
- Treat serialized `.sh3d` compatibility as a public API. Do not change
  `serialVersionUID`, XML names, or `Home.CURRENT_VERSION` without migration
  analysis.

## Performance Work

Measure before optimizing. The main interactive hot paths are:

- `swing/PlanComponent.java` for 2D painting, hit testing, and repaint scope;
- `j3d/HomeComponent3D.java` and related branches for scene updates;
- `j3d/ModelManager.java` for model loading, cloning, caching, and bounds;
- `swing/IconManager.java` for icon decode/scale/cache behavior;
- `io/HomeFileRecorder.java` for save/load and ZIP/content handling.

Use Java Flight Recorder or async-profiler on realistic large `.sh3d` files.
Capture startup time, frame/update latency, allocation rate, EDT stalls, model
load time, and save/load time. Keep benchmark homes out of Git if their license
or size is unsuitable.

For UI responsiveness:

- Never block the Swing event dispatch thread with file, network, model-loading,
  or rendering work.
- Coalesce model events and repaint only invalidated regions.
- Cache expensive geometry, bounds, textures, and scaled images with explicit
  invalidation.
- Avoid allocating temporary collections, transforms, and shapes in paint or
  per-frame traversal loops.
- Verify changes in both 2D and 3D views and watch memory growth; speedups that
  create unbounded caches are regressions.

Follow `docs/JAVA_MODERNIZATION.md` for the Java and graphics dependency
migration sequence. Do not raise the source level independently of that plan.
Follow `docs/OPTIMIZATION_PROGRESS.md` for the current checkpoint and next
measured work.
