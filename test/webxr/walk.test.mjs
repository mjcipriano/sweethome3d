import assert from 'node:assert';
import {buildColliders, move, isBlocked, nextLevel, startLevel} from '../../pluginsrc/com/eteks/sweethome3d/plugin/webxr/webxr-walk.js';
const near = (a, b, eps = 1e-6) => Math.abs(a - b) < eps;
// A wall along x from 0 to 4 m at z = 2, 10 cm thick, with a 0.9 m door centered at x = 2
const scene = {
  levels: [{name: 'Ground', elevation: 0, height: 2.5}, {name: 'First', elevation: 2.7, height: 2.5}],
  walls: [{x1: 0, z1: 2, x2: 4, z2: 2, thickness: 0.1, level: 0, arcExtent: 0},
          {x1: 0, z1: 2, x2: 4, z2: 2, thickness: 0.1, level: 1, arcExtent: 0}],
  openings: [{x: 2, z: 2, width: 0.9, depth: 0.1, angle: 0, sill: 0, height: 2.1, level: 0},
             {x: 3.2, z: 2, width: 0.8, depth: 0.1, angle: 0, sill: 0.9, height: 1.2, level: 0}],
  start: {x: 1, z: 1, yaw: 0, level: 0}
};
const boxes = buildColliders(scene, 0);
assert.strictEqual(boxes.length, 2, 'door splits the wall, window does not');
assert.ok(near(boxes[0].length, 1.55) && near(boxes[1].ax, 2.45) && near(boxes[1].length, 1.55));
assert.strictEqual(buildColliders(scene, 1).length, 1, 'no door upstairs');

// Walking into the wall stops at radius + half thickness
let [x, z] = move(1, 1, 0, 3, boxes);
assert.ok(near(x, 1) && near(z, 2 - 0.05 - 0.15, 1e-3), 'stopped by the wall: ' + z);
// Walking diagonally slides along it
[x, z] = move(0.5, 1.5, 0.5, 1, boxes);
assert.ok(x > 0.95 && z < 1.81, 'slides: ' + x + ',' + z);
// Walking through the doorway passes
[x, z] = move(2, 1, 0, 2, boxes);
assert.ok(near(x, 2) && near(z, 3), 'through the door: ' + x + ',' + z);
// ...even slightly off center, but not through the window
[x, z] = move(2.25, 1, 0, 2, boxes);
assert.ok(z > 2.5, 'door edge passable: ' + z);
[x, z] = move(3.2, 1, 0, 2, boxes);
assert.ok(z < 1.81, 'window blocks');
// A fast move can't tunnel
[x, z] = move(1, 1, 0, 50, boxes);
assert.ok(z < 1.81, 'no tunnelling');
// Someone inside a wall is pushed out
[x, z] = move(1, 2.01, 0, 0, boxes);
assert.ok(near(z, 2.2, 1e-3), 'pushed out: ' + z);

assert.strictEqual(isBlocked(1, 1, 1, 3, boxes), true, 'teleport through wall');
assert.strictEqual(isBlocked(2, 1, 2, 3, boxes), false, 'teleport through door');
assert.strictEqual(isBlocked(1, 1, 3, 1.5, boxes), false, 'teleport in room');
assert.strictEqual(isBlocked(1, 1, 1, 2, boxes), true, 'teleport into wall');

// Round wall: half circle from (0,0) to (2,0)
const round = buildColliders({walls: [{x1: 0, z1: 0, x2: 2, z2: 0, thickness: 0.1, level: 0, arcExtent: Math.PI}]}, 0);
assert.ok(round.length >= 16);
for (const b of round) {
  assert.ok(near(Math.hypot(b.ax - 1, b.az), 1, 1e-6), 'arc points on the circle');
}
assert.ok(round.some(b => Math.abs(b.az) > 0.9), 'arc bulges');

assert.strictEqual(startLevel(scene), 0);
assert.strictEqual(nextLevel(scene, 0, 1), 1);
assert.strictEqual(nextLevel(scene, 1, 1), 1);
assert.strictEqual(nextLevel(scene, 0, -1), 0);
// Levels without a floor are skipped
const roofed = {levels: [{elevation: 0, floor: true}, {elevation: 2.5, floor: false}, {elevation: 2.7, floor: true}]};
assert.strictEqual(nextLevel(roofed, 0, 1), 2);
assert.strictEqual(nextLevel(roofed, 2, -1), 0);
assert.strictEqual(nextLevel(roofed, 1, 1), 2, 'leaves a level without floor');
console.log('walk tests OK');
