# Changelog

## [7.8.0-beta.5](https://github.com/mjcipriano/sweethome3d/compare/v7.8.0-beta.3...v7.8.0-beta.5) (2026-06-10)


### Features

* "Display 3D view" toggle (stop 3D updates when hidden) ([748afb6](https://github.com/mjcipriano/sweethome3d/commit/748afb6482719dd4950a7c6cff75404b1ff89332))
* add a "Display 3D view" toggle that frees editing from 3D updates ([232adcc](https://github.com/mjcipriano/sweethome3d/commit/232adccd981269f91d4cb82ce3c1f817406ac240))
* add a configurable 3D mouse wheel zoom speed preference ([dc380f2](https://github.com/mjcipriano/sweethome3d/commit/dc380f27fd842f341153fb32e2b665b6cf239d6b))
* add a configurable 3D mouse wheel zoom speed preference ([fa00693](https://github.com/mjcipriano/sweethome3d/commit/fa00693f29fc15c785894c4fa68f23f56dc74813))
* AI design assistant (phase 1 - multi-provider read-only chat) ([d368b99](https://github.com/mjcipriano/sweethome3d/commit/d368b997af79c6dcd4ab80a0e2424b420d0ecc33))
* AI design assistant (phase 2 - editing command layer with grouped undo) ([56464c4](https://github.com/mjcipriano/sweethome3d/commit/56464c4a577e42e93f63a36c8718b48ced22a17c))
* **ai:** editing command layer with grouped undo (phase 2) ([668795a](https://github.com/mjcipriano/sweethome3d/commit/668795a1fa0e1d3437817558d5e042a810f24902))
* **ai:** home-context builder for the design assistant (phase 1 foundation) ([5553c8f](https://github.com/mjcipriano/sweethome3d/commit/5553c8ff217b93ac077726a7b63259cf45319a53))
* **ai:** multi-provider design assistant chat (phase 1, read-only Q&A) ([680ee96](https://github.com/mjcipriano/sweethome3d/commit/680ee96ecc9a5fbec1c98b01072a6eca13cd01b0))
* LOD usability - log reduction slider, vertex sort, reduced count, progress ([ccd9573](https://github.com/mjcipriano/sweethome3d/commit/ccd9573215820fd03022f468de624ca7afd2d452))
* LOD usability - log slider, vertex sort, reduced-count column, progress ([98cc3d4](https://github.com/mjcipriano/sweethome3d/commit/98cc3d48684aa4b30270aee0b66f3c532cf0e3f3))
* modern FlatLaf theme with a light/dark preference ([c90a60b](https://github.com/mjcipriano/sweethome3d/commit/c90a60be3a469340822de5fd9256ba8b08681118))
* modern FlatLaf theme with a light/dark preference ([bf3cf7f](https://github.com/mjcipriano/sweethome3d/commit/bf3cf7f4b329818e21792695a3f0ca90ab7d4455))
* pan the 3D view with a middle-button drag ([da52ea9](https://github.com/mjcipriano/sweethome3d/commit/da52ea9749ded13991f647c7bd24475a7d2d7120))
* pan the 3D view with a middle-button drag ([a6a90a1](https://github.com/mjcipriano/sweethome3d/commit/a6a90a132f7758e9bcc7ba0f0ce13356ff7f9306))
* per-piece reduced-detail (LOD) opt-in for the 3D view ([59002e4](https://github.com/mjcipriano/sweethome3d/commit/59002e414defb2d38422443bcc7c2f5bd2a4656a))
* per-piece reduced-detail (LOD) opt-in for the 3D view ([806f2ac](https://github.com/mjcipriano/sweethome3d/commit/806f2ac69a278104494c551d393a80ae5b527ac7))


### Bug Fixes

* add missing visibleOn/visibleOff strings so startup doesn't crash ([6ac9705](https://github.com/mjcipriano/sweethome3d/commit/6ac970563bd3b8c0f63e86e0119c75b7b84bb20b))
* keep menus re-enabled after a plan drag when a menu item has no Action ([8b21a93](https://github.com/mjcipriano/sweethome3d/commit/8b21a93fa4404cfe65bb98dd256469bcc06c7b85))
* keep menus re-enabled after a plan drag when an item has no Action ([92cdb79](https://github.com/mjcipriano/sweethome3d/commit/92cdb7946aa119609d846537f44a2d93842654d4))
* make furniture popup Visible On/Off act on the selected pieces ([5e61249](https://github.com/mjcipriano/sweethome3d/commit/5e612499e9a7cc0c7b787413c10fe9d0f0317989))
* make furniture popup Visible On/Off act on the selected pieces ([4be6978](https://github.com/mjcipriano/sweethome3d/commit/4be69786031649909dae306bfafa2fc095d3c2e1))
* prefer Java 3D 1.6 in development ([a7e5b9c](https://github.com/mjcipriano/sweethome3d/commit/a7e5b9c83070e8085dab05f1fb0248a69efafc52))
* release workflow — merge-safe artifact handling, Node 24, manual-only ([c79983e](https://github.com/mjcipriano/sweethome3d/commit/c79983e6234c74c625a65a164adc370f809024b6))
* release workflow — merge-safe artifacts, Node 24, manual-only ([3acb8fb](https://github.com/mjcipriano/sweethome3d/commit/3acb8fb6e8ea216c7d026313dc5fb2144d87399f))
* startup crash — add missing visibleOn/visibleOff resource strings ([71c914f](https://github.com/mjcipriano/sweethome3d/commit/71c914f64644c22c11fec01e1f174be0cce1bdb8))


### Performance Improvements

* adaptive default heap (-XX:MaxRAMPercentage=50) for releases ([72a69ef](https://github.com/mjcipriano/sweethome3d/commit/72a69ef56c13b75a62567b0f5360d4473cdd94f6))
* repaint only the moved piece's area instead of the whole plan (C1) ([d6cb827](https://github.com/mjcipriano/sweethome3d/commit/d6cb8278c2de4c2732502aa374fd388383c3fca6))
* repaint only the moved piece's area instead of the whole plan (C1) ([3fa6ec0](https://github.com/mjcipriano/sweethome3d/commit/3fa6ec0ad7ba74fee7e5f11e1ee74b1ec8d992fd))
* scope the repaint of a rotated piece too (C1) ([a6b5c0f](https://github.com/mjcipriano/sweethome3d/commit/a6b5c0f7bf01bb7b1409df9962faaa374a7e4fd2))
* scope the repaint of a rotated piece too (C1) ([39a7daa](https://github.com/mjcipriano/sweethome3d/commit/39a7daaf5b96802c5362f83ff18f614e5093b28f))
* use an adaptive default heap (-XX:MaxRAMPercentage=50) for releases ([e9c9bcf](https://github.com/mjcipriano/sweethome3d/commit/e9c9bcf0d853d1b53c0f6dca15bb2610ff302f00))
* validate Windows heap default ([6843002](https://github.com/mjcipriano/sweethome3d/commit/6843002e74cfeaa84e9fd5b8356cc29fd5b011c1))

## [7.8.0-beta.3](https://github.com/mjcipriano/sweethome3d/compare/v7.7.0-beta.3...v7.8.0-beta.3) (2026-06-08)


### Features

* add Visible On / Visible Off to furniture table right-click menu ([067f0a2](https://github.com/mjcipriano/sweethome3d/commit/067f0a2acc56c4e4b7e0a8d516293feadd7b2144))
* Visible On / Visible Off for multi-selected furniture rows ([a037853](https://github.com/mjcipriano/sweethome3d/commit/a03785337a3451072bd20892a2b1b831b3b3b971))

## [7.7.0-beta.3](https://github.com/mjcipriano/sweethome3d/compare/v7.7.0-beta.2...v7.7.0-beta.3) (2026-06-08)


### Bug Fixes

* don't compile the live interactive 3D scene (restores editing) ([9fa73b7](https://github.com/mjcipriano/sweethome3d/commit/9fa73b770021d6fa25e803c2ed7753dd0a23b58b))
* don't compile the live interactive 3D scene (restores Visible toggle + separate window) ([cd88968](https://github.com/mjcipriano/sweethome3d/commit/cd8896894f50d7da692911527c5ad7ecd6101e25))

## [7.7.0-beta.2](https://github.com/mjcipriano/sweethome3d/compare/v7.7.0-beta.1...v7.7.0-beta.2) (2026-06-08)


### Bug Fixes

* persist model LOD cache in the Home.xml entry ([bcf941f](https://github.com/mjcipriano/sweethome3d/commit/bcf941f00eaabcacbbc4d15bc0a08c500b736d21))
* persist model LOD cache in the Home.xml entry ([0e9448b](https://github.com/mjcipriano/sweethome3d/commit/0e9448b5a90f54dfa44054528c5a514875ae30ef))

## [7.7.0-beta.1](https://github.com/mjcipriano/sweethome3d/compare/v7.6.2-beta.1...v7.7.0-beta.1) (2026-06-08)


### Features

* add persistent model LOD caches ([37784f5](https://github.com/mjcipriano/sweethome3d/commit/37784f5409032bd5cc4eba633daa2c7bcc916b80))

## [7.6.2-beta.1](https://github.com/mjcipriano/sweethome3d/compare/v7.6.0-beta.1...v7.6.2-beta.1) (2026-06-08)


### Features

* vertex count feedback + menu fix + load-time simplification ([9877760](https://github.com/mjcipriano/sweethome3d/commit/98777600adf79f84bf639e8c762e98b05658bbd4))
* vertex count feedback, menu fix, load-time simplification ([6bcaf66](https://github.com/mjcipriano/sweethome3d/commit/6bcaf66f37fa325a703f63f2d67c081735f22b84))


### Bug Fixes

* restore model complexity feedback ([86bd098](https://github.com/mjcipriano/sweethome3d/commit/86bd09857476655c154b7ee3da742a5a7c24d6b7))
* restore model complexity feedback ([9fa57c4](https://github.com/mjcipriano/sweethome3d/commit/9fa57c4ebd77096b68642b65318d6893f7d72b48))

## [7.6.0-beta.1](https://github.com/mjcipriano/sweethome3d/compare/v7.5.4-beta.1...v7.6.0-beta.1) (2026-06-07)


### Features

* model simplification for 3D performance (D4) ([3523071](https://github.com/mjcipriano/sweethome3d/commit/35230716d24086cb520c8b1ef6743e1ffea2703b))

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
