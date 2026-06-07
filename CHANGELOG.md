# Changelog

## [7.5.4-beta.1](https://github.com/mjcipriano/sweethome3d/compare/v7.5.3-beta.7...v7.5.4-beta.1) (2026-06-07)


### Performance Improvements

* render 3D with vertex arrays instead of display lists on the speed path ([41cb8a5](https://github.com/mjcipriano/sweethome3d/commit/41cb8a58d3692186be84bc6bf2374418c1bdc3f6))
* vertex arrays over display lists on speed path (D4 investigated/redirected) + native-NVIDIA test docs ([6d8c106](https://github.com/mjcipriano/sweethome3d/commit/6d8c1062d591591679733e3a283fffc5deebda90))

## [7.5.3-beta.7](https://github.com/mjcipriano/sweethome3d/compare/v7.5.2-beta.7...v7.5.3-beta.7) (2026-06-07)


### Performance Improvements

* tune WSLg rendering and align release version ([35e7a4e](https://github.com/mjcipriano/sweethome3d/commit/35e7a4ec7054181c217ef70d53f8e38fcf4f4cb8))
* tune WSLg rendering and align release version ([9268779](https://github.com/mjcipriano/sweethome3d/commit/92687793cbc32894b2429eabbe9bf84b8883d500))

## [7.5.2-beta.7](https://github.com/mjcipriano/sweethome3d/compare/v7.5.1-beta.7...v7.5.2-beta.7) (2026-06-07)


### Performance Improvements

* fix broken bounds cache (D1) and reduce scene-update allocations (D3) ([1a6d6ae](https://github.com/mjcipriano/sweethome3d/commit/1a6d6aecacc20d621b946dd97b8b55077b235619))
* fix broken bounds cache and reduce scene-update allocations ([ad287cb](https://github.com/mjcipriano/sweethome3d/commit/ad287cbc9718046f814bccaf36c98bb18b227640))

## [7.5.1-beta.7](https://github.com/mjcipriano/sweethome3d/compare/v7.5.1-beta.6...v7.5.1-beta.7) (2026-06-07)


### Performance Improvements

* compile 3D scene + skip transparency sort (fix ~1 FPS render loop) ([f4e493b](https://github.com/mjcipriano/sweethome3d/commit/f4e493b0a8b09e567033e99e6459e411bf28b54e))
* compile 3D scene graph and skip transparency sort for speed ([cbd46f0](https://github.com/mjcipriano/sweethome3d/commit/cbd46f05b9af3c13ee9164befd86db190dc1baf8))

## [7.5.1-beta.6](https://github.com/mjcipriano/sweethome3d/compare/v7.5.1-beta.5...v7.5.1-beta.6) (2026-06-07)


### Features

* 3D rendering diagnostics — GPU + live FPS (Help → 3D rendering information) ([23c1cd6](https://github.com/mjcipriano/sweethome3d/commit/23c1cd62fab2ddf21a97c99fab7a0e9743eecf9b))
* add 3D rendering diagnostics (GPU and live FPS) ([a7e890b](https://github.com/mjcipriano/sweethome3d/commit/a7e890b80c5642efc187c86473ba4dfe0137d89e))

## [7.5.1-beta.5](https://github.com/mjcipriano/sweethome3d/compare/v7.5.1-beta.4...v7.5.1-beta.5) (2026-06-07)


### Performance Improvements

* default Windows 3D rendering to speed (no antialiasing) ([70070fa](https://github.com/mjcipriano/sweethome3d/commit/70070fa3a76010b73ba1abb36dfa11da4bb57394))
* Windows 3D fast path — speed-default rendering + Optimus GPU guidance (E1) ([34f892f](https://github.com/mjcipriano/sweethome3d/commit/34f892f7147041027228d1bfa83c93e25f8708f3))

## [7.5.1-beta.4](https://github.com/mjcipriano/sweethome3d/compare/v7.5.1-beta.3...v7.5.1-beta.4) (2026-06-07)


### Bug Fixes

* rebuild on source change + model-preload kill-switch (D2 verification) ([0073c75](https://github.com/mjcipriano/sweethome3d/commit/0073c751a8e763877c08cb5e89529baae0d81be0))
* rebuild on source change and add model-preload kill-switch ([c4c9368](https://github.com/mjcipriano/sweethome3d/commit/c4c9368953d809ee96fef65eb482cdede1d94deb))

## [7.5.1-beta.3](https://github.com/mjcipriano/sweethome3d/compare/v7.5.1-beta.2...v7.5.1-beta.3) (2026-06-07)


### Performance Improvements

* parallel furniture-model preload for scene builds (D2) — ~51% faster ([c6d2d01](https://github.com/mjcipriano/sweethome3d/commit/c6d2d017faf28160490a4461600139613efbd1aa))
* preload furniture models in parallel for synchronous scene builds ([bf3c802](https://github.com/mjcipriano/sweethome3d/commit/bf3c8026774806783db6be4830007934a44fdd7a))

## [7.5.1-beta.2](https://github.com/mjcipriano/sweethome3d/compare/v7.5.0...v7.5.1-beta.2) (2026-06-07)


### Features

* automate semantic versioning and release assets ([5fd6b39](https://github.com/mjcipriano/sweethome3d/commit/5fd6b393b3eb80d16edb8195407c866ec26ee90c))
* automate semantic versioning and release assets ([c02cd3e](https://github.com/mjcipriano/sweethome3d/commit/c02cd3e0588702e864f1e561ed74a44834052602))


### Bug Fixes

* read the canonical version in CI ([1915608](https://github.com/mjcipriano/sweethome3d/commit/1915608e68dfbd2aeccd8e965842559476505bc3))


### Performance Improvements

* add 2D interaction benchmark ([5a14abc](https://github.com/mjcipriano/sweethome3d/commit/5a14abcab2345cf4f8297415ab3be2f305c5187c))
* add 2D interaction benchmark (task A2) ([e2e7388](https://github.com/mjcipriano/sweethome3d/commit/e2e7388230ac9cdfed0707db1d516490cedf019e))
* add cold-start phase benchmark ([70b5e30](https://github.com/mjcipriano/sweethome3d/commit/70b5e30af4301e609275698519ff140927f1d9ef))
* add cold-start phase benchmark (task A1) ([e4f22af](https://github.com/mjcipriano/sweethome3d/commit/e4f22afcbe0ad9de8b55e1cdb90eb0f090c02250))
* add repeatable 2d plan render benchmark ([35585b7](https://github.com/mjcipriano/sweethome3d/commit/35585b7beda068aeed6d65b9b8a91cc1bcf9bd27))
* avoid redundant home archive inflation ([4d5d8c3](https://github.com/mjcipriano/sweethome3d/commit/4d5d8c35e2380090a29d2872609b579e3a7fbe69))
* reduce memory used while loading homes ([0b00814](https://github.com/mjcipriano/sweethome3d/commit/0b00814b36f7b3f94883db7020c2ac864b0a2c47))
* reduce OBJ parsing overhead ([3573e1b](https://github.com/mjcipriano/sweethome3d/commit/3573e1b7e0c9f4cab4f49c0e28f49b2c7a622451))
* repaint only the selection area on selection change ([4d616fb](https://github.com/mjcipriano/sweethome3d/commit/4d616fb444cc77fdb00cb649615638350c713792))
* repaint only the selection area on selection change (C1) ([01efb9a](https://github.com/mjcipriano/sweethome3d/commit/01efb9ac3f83367ef136596d1a869dbc4f772d3d))


### Tests

* add 3D scene-update benchmark mode ([bf8380b](https://github.com/mjcipriano/sweethome3d/commit/bf8380bea9466f222c93c5d1007145bdfb4b6022))

## Changelog

Release notes are generated from conventional commits by release-please.
