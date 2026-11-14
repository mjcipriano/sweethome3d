import assert from 'node:assert';
import {mergeParts} from '../../pluginsrc/com/eteks/sweethome3d/plugin/webxr/webxr-merge.js';

const triangle = (x) => new Float32Array([x, 0, 0, x + 1, 0, 0, x, 1, 0]);
const normals = new Float32Array([0, 0, 1, 0, 0, 1, 0, 0, 1]);
const uvs = new Float32Array([0, 0, 1, 0, 0, 1]);
// Two triangles in one geometry, of which only the second belongs to the part
const two = new Float32Array([...triangle(10), ...triangle(20)]);
const twoNormals = new Float32Array([...normals, 0, 1, 0, 0, 1, 0, 0, 1, 0]);

const merged = mergeParts([
  {key: 'wood', position: triangle(0), normal: normals, uv: uvs, start: 0, count: 3},
  {key: 'paint', position: triangle(5), normal: normals, uv: null, start: 0, count: 3},
  {key: 'wood', position: triangle(1), normal: normals, uv: uvs, start: 0, count: 3},
  {key: 'paint', position: two, normal: twoNormals, uv: null, start: 3, count: 3},
  // Same material without normals: can't share the arrays of the others
  {key: 'paint', position: triangle(7), normal: null, uv: null, start: 0, count: 3},
  {key: 'empty', position: triangle(9), normal: null, uv: null, start: 0, count: 0}
]);
assert.strictEqual(merged.length, 3, 'one mesh per material and kind of attributes');
const [wood, paint, flat] = merged;
assert.strictEqual(wood.key, 'wood');
assert.strictEqual(wood.parts, 2);
assert.deepStrictEqual(Array.from(wood.position), [...triangle(0), ...triangle(1)]);
assert.strictEqual(wood.normal.length, 18);
assert.deepStrictEqual(Array.from(wood.uv), [...uvs, ...uvs]);
assert.strictEqual(paint.key, 'paint');
assert.deepStrictEqual(Array.from(paint.position), [...triangle(5), ...triangle(20)], 'only the range of the part');
assert.deepStrictEqual(Array.from(paint.normal), [...normals, 0, 1, 0, 0, 1, 0, 0, 1, 0]);
assert.strictEqual(paint.uv, null);
assert.strictEqual(flat.key, 'paint');
assert.strictEqual(flat.normal, null);
assert.strictEqual(flat.position.length, 9);
// Plain arrays are accepted too
assert.deepStrictEqual(Array.from(mergeParts([{key: 'a', position: [1, 2, 3, 4, 5, 6], normal: null, uv: null, start: 1, count: 1}])[0].position), [4, 5, 6]);
console.log('webxr merge tests passed');
