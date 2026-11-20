import assert from 'node:assert';
import {resolveTarget, createNote, describeNotes, levelAt}
    from '../../pluginsrc/com/eteks/sweethome3d/plugin/webxr/webxr-notes.js';

const scene = {
  levels: [{name: 'Ground', elevation: 0, height: 2.5}, {name: 'First', elevation: 2.7, height: 2.5}],
  walls: [
    {x1: 0, z1: 0, x2: 6, z2: 0, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'north', level: 0},
    {x1: 0, z1: 0, x2: 6, z2: 0, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'northUp', level: 1},
    {x1: 6, z1: 0, x2: 6, z2: 4, thickness: 0.2, arcExtent: 0, height: 2.5, key: 'east', level: 0}],
  openings: [{x: 4.5, z: 0, width: 1.2, depth: 0.2, angle: 0, sill: 0.9, height: 1.2, door: false, key: 'window', level: 0},
             {x: 6, z: 2, width: 0.9, depth: 0.2, angle: Math.PI / 2, sill: 0, height: 2.1, door: true, key: 'door', level: 0}],
  furniture: [{x: 2, z: 2, width: 1.6, depth: 0.9, angle: 0, elevation: 0, height: 0.75, name: 'Table', key: 'table', level: 0},
              {x: 2, z: 2, width: 0.3, depth: 0.3, angle: 0, elevation: 0.75, height: 0.4, name: 'Lamp', key: 'lamp', level: 0},
              // A roof with downspouts: its box holds the whole home, from the ground up
              {x: 3, z: 2, width: 8, depth: 6, angle: 0, elevation: 0, height: 6, shell: true, name: 'Roof', key: 'roof', level: 0}],
  rooms: [{level: 0, key: 'living', points: [[0, 0], [6, 0], [6, 4], [0, 4]]},
          {level: 1, key: 'bedroom', points: [[0, 0], [6, 0], [6, 4], [0, 4]]}]
};
assert.strictEqual(levelAt(scene, 1.2), 0);
assert.strictEqual(levelAt(scene, 2.9), 1);

// From the point alone: a wall on the right level, a window in a wall, a door, the smallest
// piece of furniture, the floor of a room, the ground outside
assert.deepStrictEqual(resolveTarget(scene, {x: 1, y: 0.4, z: 0.09}), {target: 'wall', key: 'north', name: null, level: 0});
assert.strictEqual(resolveTarget(scene, {x: 1, y: 3.2, z: 0.09}).key, 'northUp');
assert.strictEqual(resolveTarget(scene, {x: 5, y: 0, z: 3}).target, 'room', 'the floor under a roof is its room');
assert.strictEqual(resolveTarget(scene, {x: 5, y: 0, z: 3}, 'roof').name, 'Roof', 'a roof aimed at is the roof');
assert.strictEqual(resolveTarget(scene, {x: 4.5, y: 1.5, z: 0.08}).target, 'window');
assert.strictEqual(resolveTarget(scene, {x: 4.5, y: 0.4, z: 0.08}).target, 'wall', 'under the window');
assert.strictEqual(resolveTarget(scene, {x: 5.92, y: 1, z: 2.1}).target, 'door');
assert.deepStrictEqual(resolveTarget(scene, {x: 2.5, y: 0.75, z: 2.1}), {target: 'furniture', key: 'table', name: 'Table', level: 0});
assert.strictEqual(resolveTarget(scene, {x: 2.05, y: 0.9, z: 2}).name, 'Lamp');
assert.deepStrictEqual(resolveTarget(scene, {x: 4, y: 0, z: 3}), {target: 'room', key: 'living', name: null, level: 0});
assert.strictEqual(resolveTarget(scene, {x: 4, y: 2.7, z: 3}).key, 'bedroom');
assert.strictEqual(resolveTarget(scene, {x: 12, y: 0, z: 3}).target, 'ground');
// The item the page picked wins over the geometry
assert.strictEqual(resolveTarget(scene, {x: 2.5, y: 0.75, z: 2.1}, 'east').key, 'east');
assert.strictEqual(resolveTarget(scene, {x: 2.5, y: 0.75, z: 2.1}, 'unknown').key, 'table');

// A note stores its point in the centimeters of the plan
const notes = [];
const note = createNote(scene, notes, {x: 1.234, y: 0.4, z: 0.09}, '  add an outlet here ');
assert.deepStrictEqual({...note, created: undefined}, {id: 'n1', text: 'add an outlet here', status: 'open', target: 'wall',
    x: 123.4, y: 9, z: 40, created: undefined, key: 'north', level: 'Ground'});
notes.push(note);
notes.push({...createNote(scene, notes, {x: 2.5, y: 0.75, z: 2.1}, 'fix this model'), id: 'n3'});
// Ids already used are skipped
assert.strictEqual(createNote(scene, notes, {x: 0, y: 0, z: 9}, 'x').id, 'n4');
const text = describeNotes(notes);
assert.ok(text.includes('- n1 [open] "add an outlet here" - on wall at x 123 y 9, height 40, level "Ground"'), text);
assert.ok(text.includes('on furniture "Table"'), text);
assert.strictEqual(describeNotes([]), 'No review note.');
console.log('notes tests OK');
