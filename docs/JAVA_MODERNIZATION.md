# Java Modernization Plan

## Goal

Move Sweet Home 3D to a supported Java baseline without breaking file
compatibility or hiding performance regressions. The migration should make
future optimization easier, but JVM/source upgrades must be measured rather
than assumed to improve rendering.

## Current Constraints

- Application sources are compiled as Java 8 class files.
- CI and release packaging use a bundled Java 17 runtime.
- The application includes applet, JNLP, SecurityManager, and internal AWT
  compatibility code.
- Java 3D 1.6-era and JOGL native libraries are bundled per platform.
- The current Java 3D native pipeline can crash in Mesa GLX under WSL when run
  with JetBrains Runtime.

## Migration Sequence

### 1. Standardize Java 17

- Use Temurin/OpenJDK 17 for development, CI, tests, and packaged runtimes.
- Keep Java 8 bytecode temporarily so dependency upgrades are isolated from
  source-language changes.
- Make the complete GUI/3D suite pass on Windows, Linux, macOS, and WSL.
- Record startup, large-home load, 2D interaction, 3D interaction, and memory
  baselines.

Exit criteria: no JVM crashes, no skipped graphics tests, and repeatable
performance measurements.

### 2. Upgrade The Graphics Stack

- Upgrade Java 3D core/utilities/vecmath together.
- Upgrade JOGL and GlueGen together, including every platform native binary.
- Remove duplicate Java 3D 1.5 compatibility paths after supported-platform
  validation.
- Validate model loading, off-screen rendering, photos, OBJ export, and
  renderer teardown repeatedly to catch native resource leaks.

Exit criteria: supported Java 17 graphics dependencies, stable native context
creation, and equivalent rendering output.

### 3. Set Java 17 Source And Bytecode

- Change Ant compilation to `--release 17`.
- Remove applet and Java Web Start targets and their `javax.jnlp` stubs.
- Remove obsolete SecurityManager and pre-Java-9 compatibility branches.
- Replace reflective access to modern public APIs where possible.
- Remove module-opening flags only after tests prove they are unnecessary.

Exit criteria: Java 17 is the minimum documented runtime and no legacy
deployment code is included in desktop distributions.

### 4. Modernize Hot Paths

- Profile with Java Flight Recorder before modifying algorithms.
- Keep Swing EDT work bounded and move model/file work off the EDT.
- Reduce allocations and repeated geometry work in `PlanComponent`,
  `HomeComponent3D`, `ModelManager`, and icon/model caches.
- Add performance fixtures for representative small, medium, and large homes.
- Add regression thresholds only after benchmarks are stable on controlled
  runners.

### 5. Evaluate Java 21

Move packaged runtimes and source to Java 21 only after Java 17 and the upgraded
graphics stack are stable. Evaluate startup, memory, GC pauses, and rendering
latency against Java 17. Adopt Java 21 when the measurements and support window
justify it, not solely for newer syntax.

## Compatibility Rules

- `.sh3d` serialized and XML formats remain compatible.
- Plugin APIs require an explicit compatibility decision before changing the
  bytecode baseline.
- Native libraries must be tested on every released OS/architecture.
- Performance changes require before/after measurements and functional tests.

## References

- OpenJDK 21 project: https://openjdk.org/projects/jdk/21/
- JogAmp project: https://jogamp.org/
- Java 3D releases: https://github.com/hharrison/java3d-core/releases
