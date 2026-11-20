import assert from 'node:assert';
import {createWorld, createPlayer, step, groundAt, collide, findDoor, toggleDoor, breakObject, rebuild,
        PLAYER_RADIUS, PLAYER_HEIGHT, STEP_HEIGHT, WALK_SPEED}
    from '../../pluginsrc/com/eteks/sweethome3d/plugin/webxr/webxr-game-physics.js';

const near = (a, b, tolerance = 0.02) => Math.abs(a - b) <= tolerance;

// A house of 6 m x 4 m with a second level over its left half, a door in the south wall,
// a window in the north wall, a table, and stairs climbing toward -z
const scene = {
  levels: [{name: 'Ground', elevation: 0, height: 2.5, floor: true}, {name: 'First', elevation: 2.7, height: 2.5, floor: true}],
  walls: [
    {x1: 0, z1: 0, x2: 6, z2: 0, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'north', level: 0},
    {x1: 6, z1: 0, x2: 6, z2: 4, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'east', level: 0},
    {x1: 6, z1: 4, x2: 0, z2: 4, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'south', level: 0},
    {x1: 0, z1: 4, x2: 0, z2: 0, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'west', level: 0}],
  openings: [
    {x: 3, z: 4, width: 0.9, depth: 0.2, angle: Math.PI, sill: 0, height: 2.1, door: true, key: 'door', level: 0},
    {x: 4.5, z: 0, width: 1.2, depth: 0.2, angle: 0, sill: 0.9, height: 1.2, door: false, key: 'window', level: 0}],
  furniture: [
    {x: 4.5, z: 2, width: 1.2, depth: 0.8, angle: 0, elevation: 0, height: 0.75, stairs: false, name: 'Table', key: 'table', level: 0},
    {x: 5, z: 3.2, width: 0.4, depth: 0.4, angle: 0, elevation: 0, height: 0.3, stairs: false, name: 'Box', key: 'box', level: 0},
    {x: 3, z: 2, width: 40, depth: 30, angle: 0, elevation: -1, height: 2, stairs: false, shell: true, name: 'Terrain', key: 'terrain', level: 0}],
  rooms: [
    {level: 0, key: 'room0', points: [[0, 0], [6, 0], [6, 4], [0, 4]]},
    {level: 1, key: 'room1', points: [[0, 0], [3, 0], [3, 4], [0, 4]]}],
  stairs: [{level: 0, topLevel: 1, key: 'stairs', x: 1, z: 2, angle: 0, base: 0, rise: 2.7,
            flights: [{x1: -0.5, z1: -1.5, x2: 0.5, z2: 1.5, ux: 0, uz: -1, h0: 0, h1: 2.7}]}]
};
const world = createWorld(scene);
assert.strictEqual(world.panels.length, 2, 'a door and a window');
assert.strictEqual(world.solids.filter(solid => solid.key === 'terrain').length, 0, 'the ground and roofs stop no one');
const withTerrainLevel = createWorld({...scene, levels: [{name: 'Terrain', elevation: -1.7, height: 1.69, floor: false}, ...scene.levels],
    walls: [], openings: [], furniture: [], rooms: [], stairs: []});
assert.strictEqual(withTerrainLevel.base, 0, 'a level without room under the house is no basement to walk in');
assert.strictEqual(withTerrainLevel.lowest, -1.7);
// A ground that slopes from 1 m at x = -10 to -1 m at x = 30, with the room of the scene on it
const sloped = createWorld({...scene, walls: [], openings: [], furniture: [], stairs: [],
    terrain: [[-10, -10, 1, 30, -10, -1, 30, 20, -1], [-10, -10, 1, 30, 20, -1, -10, 20, 1]]});
assert.strictEqual(sloped.base, -1);
assert.ok(Math.abs(groundAt(sloped, 20, 10, 5) - -0.5) < 1e-6, 'the height of the ground down the slope');
assert.ok(Math.abs(groundAt(sloped, -5, 10, 5) - 0.75) < 1e-6, 'and up the slope');
assert.strictEqual(groundAt(sloped, 3, 2, 0.1), 0, 'the floor of a room, not the ground it is open under');
assert.strictEqual(groundAt(sloped, 100, 100, 5), 0, 'flat land beyond the ground');
// A ramp from the floor up to 1 m, beside the room
const ramped = createWorld({...scene, walls: [], openings: [], furniture: [], stairs: [],
    ramps: [[10, 0, 0, 14, 0, 1, 14, 2, 1], [10, 0, 0, 14, 2, 1, 10, 2, 0]]});
assert.ok(Math.abs(groundAt(ramped, 11, 1, 0.2) - 0.25) < 1e-6, 'up a ramp');
assert.ok(Math.abs(groundAt(ramped, 13, 1, 0.7) - 0.75) < 1e-6);
assert.strictEqual(groundAt(ramped, 13.9, 1, 0), 0, 'its high end is out of reach from the floor');
assert.strictEqual(createWorld({...scene, levels: [{name: 'Basement', elevation: -2.5, height: 2.4, floor: true}, ...scene.levels],
    walls: [], openings: [], furniture: [], rooms: [], stairs: []}).base, -2.5);
assert.strictEqual(world.solids.filter(solid => solid.kind === 'wall' && solid.key === 'south').length, 3,
    'the south wall on both sides of its door and above it');
assert.strictEqual(world.solids.filter(solid => solid.key === 'north').length, 4, 'a wall around its window');

const run = (player, input, seconds) => {
  for (let time = 0; time < seconds; time += 1 / 60) {
    step(world, player, input, 1 / 60);
  }
  return player;
};

// Walking toward +x (yaw 90 degrees) until the east wall stops him
let player = createPlayer(world, 3, 3, 0, Math.PI / 2);
assert.ok(near(player.y, 0) && player.grounded, 'on the ground');
run(player, {forward: 1}, 0.5);
assert.ok(near(player.x, 3 + WALK_SPEED * 0.5, 0.1), 'walks at his speed: ' + player.x);
run(player, {forward: 1}, 3);
assert.ok(near(player.x, 6 - 0.1 - PLAYER_RADIUS), 'stopped by the east wall: ' + player.x);
assert.ok(near(player.y, 0), 'still on the ground');

// The table stops him, a jump puts him on it, and he may walk off it
player = createPlayer(world, 3, 2, 0, Math.PI / 2);
run(player, {forward: 1}, 2);
assert.ok(near(player.x, 4.5 - 0.6 - PLAYER_RADIUS), 'stopped by the table: ' + player.x);
step(world, player, {forward: 1, jump: true}, 1 / 60);
assert.ok(!player.grounded && player.vy > 0, 'jumping');
run(player, {forward: 1}, 0.3);
run(player, {}, 0.8);
assert.ok(near(player.y, 0.75) && player.grounded, 'on the table: ' + player.y);
run(player, {forward: 1}, 1.5);
assert.ok(near(player.y, 0) && player.x > 5, 'down the other side of the table: ' + player.x + ' ' + player.y);
// A low box is stepped onto without jumping
player = createPlayer(world, 4.2, 3.2, 0, Math.PI / 2);
run(player, {forward: 1}, 0.25);
assert.ok(near(player.y, 0.3), 'on the box: ' + player.y + ' at ' + player.x);

// The closed door stops him, opened it lets him out, and it isn't closed on him
player = createPlayer(world, 3, 3, 0, Math.PI);
run(player, {forward: 1}, 2);
assert.ok(player.z < 4, 'stopped by the closed door: ' + player.z);
const door = findDoor(world, player);
assert.ok(door && door.key === 'door', 'door within reach');
assert.strictEqual(toggleDoor(world, door, player), true, 'door opened');
run(player, {forward: 1}, 0.1);
assert.ok(player.z > 3.85 && player.z < 4.25, 'in the doorway: ' + player.z);
assert.strictEqual(toggleDoor(world, door, player), true, 'not closed on someone');
run(player, {forward: 1}, 1);
assert.ok(player.z > 4.5, 'outside: ' + player.z);
assert.strictEqual(toggleDoor(world, door, player), false, 'door closed');
// Beside the door, the wall
player = createPlayer(world, 1.5, 3, 0, Math.PI);
run(player, {forward: 1}, 2);
assert.ok(player.z < 4, 'stopped by the wall');
assert.strictEqual(findDoor(world, createPlayer(world, 1, 1, 0, 0)), null, 'no door within reach');

// The window is too high to walk through, and a jump isn't enough to go over its sill... but its wall is climbed
player = createPlayer(world, 4.5, 1, 0, 0);
run(player, {forward: 1}, 2);
assert.ok(player.z > 0.1, 'stopped under the window: ' + player.z);
assert.strictEqual(findDoor(world, player), null, 'a window is not a door');

// Up the stairs to the level above, and its floor carries him
player = createPlayer(world, 1, 3.7, 0, 0);
run(player, {forward: 1}, 0.95);
assert.ok(player.y > 2.4 && player.y < 2.75, 'upstairs: ' + player.y + ' at ' + player.z);
player.yaw = Math.PI / 2;
run(player, {forward: 1}, 0.4);
assert.ok(near(player.y, 2.7) && player.x > 1.6, 'on the floor above: ' + player.x + ' ' + player.y);
// Off the edge of that floor he falls to the ground
run(player, {forward: 1}, 1.5);
assert.ok(near(player.y, 0) && player.x > 3, 'fell from the floor above: ' + player.x + ' ' + player.y);
// Under the floor above, a jump stops at the ceiling
player = createPlayer(world, 2, 1, 0, 0);
step(world, player, {jump: true}, 1 / 60);
let highest = 0;
for (let i = 0; i < 60; i++) {
  step(world, player, {}, 1 / 60);
  highest = Math.max(highest, player.y);
}
assert.ok(highest > 0.6 && highest + PLAYER_HEIGHT <= 2.5 + 0.01, 'ceiling: ' + highest);

// Climbing: pushing against a wall in the air while keeping jump pressed goes up to its top
player = createPlayer(world, 5.5, 1, 0, Math.PI / 2);
run(player, {forward: 1}, 0.5);
step(world, player, {forward: 1, jump: true, up: true}, 1 / 60);
run(player, {forward: 1, up: true}, 0.5);
assert.ok(player.climbing && player.y > 1 && player.y < 2, 'climbing the wall: ' + player.y);
run(player, {forward: 1, up: true}, 0.45);
run(player, {}, 0.5);
assert.ok(near(player.y, 2.5, 0.05) && player.grounded, 'on top of the wall: ' + player.y + ' at ' + player.x);
// Without keeping jump pressed he doesn't climb
player = createPlayer(world, 5.5, 1, 0, Math.PI / 2);
step(world, player, {forward: 1, jump: true}, 1 / 60);
run(player, {forward: 1}, 1.5);
assert.ok(near(player.y, 0), 'no climbing without the key');

// Flying: up, over the wall and down
player = createPlayer(world, 5, 3, 0, Math.PI / 2);
player.flying = true;
run(player, {up: true}, 0.5);
assert.ok(player.y > 3, 'flying up: ' + player.y);
run(player, {forward: 1}, 0.5);
assert.ok(player.x > 6.5, 'over the wall: ' + player.x);
const heightInFlight = player.y;
run(player, {}, 0.5);
assert.ok(near(player.y, heightInFlight), 'no gravity in flight');
player.flying = false;
run(player, {}, 2);
assert.ok(near(player.y, 0) && player.grounded, 'landed outside');

// Breaking: a broken table or wall doesn't stop anyone, a wall goes with its door, and everything is rebuilt
player = createPlayer(world, 3, 2, 0, Math.PI / 2);
assert.deepStrictEqual(breakObject(world, 'table'), ['table']);
run(player, {forward: 1}, 0.6);
assert.ok(player.x > 4.3 && near(player.y, 0), 'through the broken table: ' + player.x);
assert.deepStrictEqual(breakObject(world, 'south').sort(), ['door', 'south']);
player = createPlayer(world, 1.5, 3, 0, Math.PI);
run(player, {forward: 1}, 1);
assert.ok(player.z > 4.5, 'through the broken wall: ' + player.z);
assert.deepStrictEqual(breakObject(world, 'south'), [], 'already broken');
assert.deepStrictEqual(breakObject(world, 'stairs'), ['stairs']);
assert.ok(near(groundAt(world, 1, 1.5, 0), 0), 'no stairs any more');
rebuild(world);
assert.strictEqual(world.broken.size, 0);
player = createPlayer(world, 1.5, 3, 0, Math.PI);
run(player, {forward: 1}, 2);
assert.ok(player.z < 4, 'the wall is back');
assert.ok(groundAt(world, 1, 1.5, 1.6) > 1.7, 'the stairs are back');

// A fast run doesn't go through a thin wall, whatever the frame rate
player = createPlayer(world, 3, 1, 0, 0);
for (let i = 0; i < 40; i++) {
  step(world, player, {forward: 1, run: true}, 0.05);
}
assert.ok(player.z > 0.1, 'no tunnel through the wall: ' + player.z);
const pushed = collide(world, 5.95, 2.9, 0);
assert.ok(pushed.touched && pushed.nx < -0.9, 'pushed out of the east wall');
console.log('game tests OK');
