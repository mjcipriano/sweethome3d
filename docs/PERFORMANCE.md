# Performance Work

Performance changes should be measured with repeatable workloads and verified
against functional tests. Do not commit private or large benchmark homes.

## Complex Home Loading

The local benchmark loads a home without starting Swing or Java 3D:

```sh
make benchmark-home-load \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_MODE=recorder \
  BENCHMARK_ITERATIONS=3
```

Capture a Java Flight Recorder profile with:

```sh
make benchmark-home-load \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  HOME_LOAD_JFR=profiles/home-load.jfr
```

The example home used during initial profiling is 141 MB and contains 435
furniture items, 162 walls, 47 rooms, and 8 levels.

## Initial Baseline

Measurements were taken on the same WSL development machine with a 4 GB
benchmark heap. Warm iterations are the useful comparison because the first
iteration includes JVM and class-loading costs.

| Implementation | Warm load time | Retained heap delta |
| --- | ---: | ---: |
| Original content lookup | 2.46-2.82 s | 199-203 MB |
| Digest-keyed content lookup | 2.56-2.99 s | 112-114 MB |
| Digest verification without eager ZIP inflation | 1.50-1.86 s | 113-114 MB |

The digest-keyed lookup reduces retained heap by about 44 percent. It does not
provide a statistically meaningful load-time improvement on its own. Avoiding
the redundant eager inflation pass for modern local files reduces warm load
time by roughly 35-45 percent. Legacy archives, URL inputs, and archives
without a readable digest manifest continue to use exhaustive ZIP validation.

## 2D Plan Rendering

Repeated off-screen plan painting can be measured without Java 3D:

```sh
make benchmark-plan-render \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_ITERATIONS=10
```

Set `PLAN_RENDER_JFR=profiles/plan-render.jfr` to record the render hot paths.

The initial complex-home baseline at 1920x1080 was about 820 ms for the first
paint. Repeated paints varied while asynchronous top-view icons were decoded,
then settled around 43-80 ms. JFR identified PNG decoding and image scaling as
the main warm-up costs, with geometry area construction and Java2D
rasterization as secondary costs.

## Optimization Rules

1. Capture a baseline before changing a hot path.
2. Keep damaged-home detection and repair behavior covered by tests.
3. Run `make test-core` for headless I/O and model changes.
4. Run `make test-local` for Swing or Java 3D changes.
5. Record hardware, Java version, workload, and before/after measurements.
6. Treat GPU acceleration as a rendering concern; it will not accelerate XML,
   ZIP, or model parsing.
