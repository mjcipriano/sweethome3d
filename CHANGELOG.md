# Changelog

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
