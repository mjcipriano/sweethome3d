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

The intended dependency direction is documented and enforced by
`PackageDependenciesTest`: model is at the bottom; tools, controllers, Swing,
Java 3D, I/O, and the application layer build above it. Keep Swing and Java 3D
types out of the model package.

## Build And Test

Prerequisites: JDK 17, Apache Ant, GNU Make, and a working OpenGL environment.
Linux GUI tests require WSLg/X11 or Xvfb plus Mesa GLX utilities.

```bash
make build       # Compile application/resource JARs
make jar         # Build install/SweetHome3D-7.5.jar
make test-core   # Deterministic model/platform tests; no display required
make test-local-check  # Verify local X11 and GLX support
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
sudo apt-get install -y openjdk-17-jdk ant make x11-utils mesa-utils libgl1-mesa-dri xvfb
make test-local-check
make test-local TEST_JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

`test-local` uses the existing WSLg display when `DISPLAY` and GLX work. Force
a mode while troubleshooting:

```bash
make test-local TEST_DISPLAY_MODE=display  # Require WSLg/current X server
make test-local TEST_DISPLAY_MODE=xvfb     # Start a private Xvfb server
LIBGL_ALWAYS_SOFTWARE=1 make test-local    # Force Mesa software rendering
make test-local TEST_JAVA=/path/to/java    # Override only the test JVM
```

Avoid JetBrains Runtime for the complete Java 3D suite under Linux/WSL. It may
crash in Mesa's `libGLX_mesa.so` while Java 3D creates a rendering context.
The local runner rejects JBR by default. `TEST_JAVA_HOME` selects a standard
test JDK without changing the system-wide Java or the JDK used by VS Code.

If `make test-local-check` cannot connect to `DISPLAY=:0`, restart WSL from
Windows with `wsl --shutdown`, reopen the folder through VS Code's WSL
extension, and retry. Do not run GUI tests from a plain Windows VS Code terminal
that merely invokes `wsl.exe`; use a terminal owned by the WSL extension.

Do not rely on files under ignored `build/`, `classes/`, or `release/`.

## Release Automation

`ci.yml` runs core tests on Windows, Linux, and macOS. The complete graphics
suite runs on Linux/Xvfb on `main`, on schedule, and on manual dispatch.

`release.yml` builds:

- a self-contained executable JAR;
- a Windows x64 runnable ZIP with a bundled Java runtime;
- a Linux x64 runnable tarball with a bundled Java runtime;
- a macOS x64 runnable tarball containing an `.app` and bundled runtime;
- SHA-256 checksums.

Pushing a tag such as `v7.5.1` publishes a GitHub release. Manual dispatch can
build artifacts without publishing. Packages are currently unsigned. Windows
code signing and Apple signing/notarization require repository secrets and
should be added as separate guarded steps.

To reproduce a platform bundle locally:

```powershell
./scripts/package-release.ps1 -Version 7.5
./scripts/verify-release.ps1 -Version 7.5 -ArtifactDirectory release
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
