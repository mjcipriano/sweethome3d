import assert from 'node:assert';
import {buildColliders, move, isBlocked, nextLevel, startLevel, standAt, groundHeightAt, MAX_STEP} from '../../pluginsrc/com/eteks/sweethome3d/plugin/webxr/webxr-walk.js';
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

// Stairs: a straight flight 1 m wide climbing 2.7 m over 4 m toward -z, centered at (10, 10)
const storey = {
  levels: [{elevation: 0, floor: true}, {elevation: 2.7, floor: true}],
  stairs: [{level: 0, topLevel: 1, x: 10, z: 10, angle: 0, base: 0, rise: 2.7,
            flights: [{x1: -0.5, z1: -2, x2: 0.5, z2: 2, ux: 0, uz: -1, h0: 0, h1: 2.7}]}]
};
assert.deepStrictEqual(standAt(storey, {level: 0, height: 0}, 5, 5), {level: 0, height: 0}, 'on the floor');
// Walking up from the bottom step
let place = standAt(storey, {level: 0, height: 0}, 10, 11.9);
assert.ok(place.level === 0 && near(place.height, 2.7 * 0.1 / 4), 'first step: ' + place.height);
let climbed = {level: 0, height: 0};
for (let stepZ = 12.2; stepZ > 7.5; stepZ -= 0.1) {
  climbed = standAt(storey, climbed, 10, stepZ);
  assert.ok(climbed, 'climbing at ' + stepZ);
}
assert.deepStrictEqual(climbed, {level: 1, height: 0}, 'arrived upstairs');
// ...and back down through the hole
for (let stepZ = 7.6; stepZ < 12.3; stepZ += 0.1) {
  climbed = standAt(storey, climbed, 10, stepZ);
  assert.ok(climbed, 'going down at ' + stepZ);
}
assert.deepStrictEqual(climbed, {level: 0, height: 0}, 'back downstairs');
// No climbing onto the side of the stairs where they are high, nor jumping off
assert.strictEqual(standAt(storey, {level: 0, height: 0}, 10, 9), null, 'side of the stairs');
assert.strictEqual(standAt(storey, {level: 0, height: 1.35}, 11, 10), null, 'no jumping off');
// Upstairs the hole is closed except at the top of the stairs
assert.strictEqual(standAt(storey, {level: 1, height: 0}, 10, 11), null, 'hole');
place = standAt(storey, {level: 1, height: 0}, 10, 8.1);
assert.ok(place.level === 0 && place.height > 2.6, 'top step from upstairs');
// A teleport lands on the stairs at their height
place = standAt(storey, 0, 10, 10);
assert.ok(place.level === 0 && near(place.height, 1.35), 'teleport on stairs');
assert.strictEqual(standAt(storey, 1, 10, 10), null, 'no teleport into the hole');
// Stairs turned by 90 degrees climb toward +x
const turned = {levels: storey.levels, stairs: [{...storey.stairs[0], angle: Math.PI / 2}]};
place = standAt(turned, 0, 11, 10);
assert.ok(near(place.height, 2.7 * 0.75), 'turned stairs: ' + place.height);
// L-shaped stairs: a landing then a flight toward +x
const lShaped = {levels: storey.levels, stairs: [{level: 0, topLevel: 1, x: 0, z: 0, angle: 0, base: 0, rise: 2.7,
  flights: [{x1: -1.5, z1: -0.5, x2: -0.5, z2: 1.5, ux: 0, uz: -1, h0: 0, h1: 1.2},
            {x1: -1.5, z1: -1.5, x2: -0.5, z2: -0.5, ux: 0, uz: 0, h0: 1.35, h1: 1.35},
            {x1: -0.5, z1: -1.5, x2: 1.5, z2: -0.5, ux: 1, uz: 0, h0: 1.35, h1: 2.7}]}]};
climbed = {level: 0, height: 0};
for (let stepZ = 1.7; stepZ > -1; stepZ -= 0.1) {
  climbed = standAt(lShaped, climbed, -1, stepZ);
  assert.ok(climbed, 'first flight at ' + stepZ);
}
assert.ok(near(climbed.height, 1.35), 'on the landing');
for (let stepX = -1; stepX < 1.8; stepX += 0.1) {
  climbed = standAt(lShaped, climbed, stepX, -1);
  assert.ok(climbed, 'second flight at ' + stepX);
}
assert.deepStrictEqual(climbed, {level: 1, height: 0}, 'L stairs lead upstairs');
// Spiral stairs: 15 steps of 24 degrees around a post at (20, 20), each one a small
// landing above the previous one, as AssistantGeneratedModel.buildSpiralStairs describes them
{
  const radius = 0.98;
  const steps = 15;
  const stepHeight = 2.7 / steps;
  const stepAngle = 24 * Math.PI / 180;
  const flights = [];
  for (let i = 0; i < steps; i++) {
    const points = [[(i - 0.5) * stepAngle, radius * 0.35], [(i - 0.5) * stepAngle, radius], [i * stepAngle, radius],
                    [(i + 0.5) * stepAngle, radius], [(i + 0.5) * stepAngle, radius * 0.35]]
        .map(([angle, r]) => [r * Math.sin(angle), r * Math.cos(angle)]);
    flights.push({x1: Math.min(...points.map(p => p[0])), z1: Math.min(...points.map(p => p[1])),
                  x2: Math.max(...points.map(p => p[0])), z2: Math.max(...points.map(p => p[1])),
                  ux: 0, uz: 0, h0: (i + 1) * stepHeight, h1: (i + 1) * stepHeight});
  }
  const spiral = {
    levels: [{elevation: 0, floor: true}, {elevation: 2.7, floor: true}],
    stairs: [{level: 0, topLevel: 1, x: 20, z: 20, angle: 0, base: 0, rise: 2.7, flights}]
  };
  const at = (angle, r) => [20 + r * Math.sin(angle), 20 + r * Math.cos(angle)];
  // Up, turning around the post from the front of the stairs
  let visitor = standAt(spiral, {level: 0, height: 0}, ...at(-0.3, radius * 1.3));
  assert.deepStrictEqual(visitor, {level: 0, height: 0}, 'in front of the spiral stairs');
  let highest = 0;
  for (let angle = -0.15; angle <= (steps - 1) * stepAngle + 0.001; angle += 0.03) {
    const next = standAt(spiral, visitor, ...at(angle, radius * 0.75));
    assert.ok(next, 'spiral blocked at ' + angle + ' from ' + visitor.height);
    assert.ok(next.height >= visitor.height - 1e-9, 'spiral goes down at ' + angle);
    assert.ok(next.height - visitor.height <= MAX_STEP + 1e-9, 'spiral step too high at ' + angle);
    visitor = next;
    highest = Math.max(highest, visitor.height);
  }
  assert.ok(near(highest, 2.7), 'top of the spiral stairs: ' + highest);
  // Off the top step, on the floor above
  visitor = standAt(spiral, visitor, ...at((steps - 1) * stepAngle, radius * 1.3));
  assert.deepStrictEqual(visitor, {level: 1, height: 0}, 'spiral stairs lead upstairs');
  // The hole is closed elsewhere, and the bottom step can't be climbed from upstairs
  assert.strictEqual(standAt(spiral, visitor, ...at(Math.PI / 2, radius * 0.75)), null, 'hole of the spiral stairs');
  // Back down
  visitor = standAt(spiral, visitor, ...at((steps - 1) * stepAngle, radius * 0.75));
  assert.ok(visitor && visitor.level === 0 && near(visitor.height, 2.7), 'top step from upstairs');
  for (let angle = (steps - 1) * stepAngle; angle >= -0.2; angle -= 0.03) {
    const next = standAt(spiral, visitor, ...at(angle, radius * 0.75));
    assert.ok(next, 'spiral blocked going down at ' + angle);
    visitor = next;
  }
  visitor = standAt(spiral, visitor, ...at(-0.3, radius * 1.3));
  assert.deepStrictEqual(visitor, {level: 0, height: 0}, 'down the spiral stairs');
  // The bottom step isn't a way up to the top one, which is above it
  const low = standAt(spiral, {level: 0, height: 0}, ...at(0, radius * 0.75));
  assert.ok(low && low.height < 0.5, 'bottom step under the top one: ' + (low && low.height));
}
{
  // The ground built by set_terrain: a slope of 5 % along x, on a level of its own under the house
  const site = {levels: [{name: 'Terrain', elevation: -1.7, height: 1.69, floor: false},
                         {name: 'Ground', elevation: 0, height: 2.5, floor: true},
                         {name: 'First', elevation: 2.6, height: 2.5, floor: true}],
                rooms: [{level: 1, points: [[0, 0], [6, 0], [6, 4], [0, 4]]}],
                terrain: [[-20, -20, 1, 20, -20, -1, 20, 20, -1], [-20, -20, 1, 20, 20, -1, -20, 20, 1]],
                stairs: []};
  assert.strictEqual(groundHeightAt(site, 1, 3, 2), null, 'no ground in a room');
  assert.strictEqual(groundHeightAt(site, 2, 10, 10), null, 'nor from the floor above');
  assert.ok(Math.abs(groundHeightAt(site, 1, 10, 10) - -0.5) < 1e-9);
  assert.deepStrictEqual(standAt(site, {level: 1, height: 0}, 3, 2), {level: 1, height: 0}, 'on the floor of the room');
  const down = standAt(site, {level: 1, height: -0.45}, 10, 10);
  assert.ok(down && Math.abs(down.height - -0.5) < 1e-9, 'down the slope');
  const up = standAt(site, {level: 1, height: 0.7}, -14, 0);
  assert.ok(up && Math.abs(up.height - 0.7) < 1e-9, 'up the slope, higher than a step above the floor');
  assert.strictEqual(standAt(site, {level: 1, height: 0}, -14, 0), null, 'a bank higher than a step stops the visitor');
  assert.ok(Math.abs(standAt(site, 1, 10, 10).height - -0.5) < 1e-9, 'someone who arrives is put on the ground');
}
console.log('walk tests OK');
