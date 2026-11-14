// Merging of the meshes of the Sweet Home 3D WebXR preview. A home is exported
// as one mesh per part of each object (tens of thousands for a furnished
// house), and drawing them one by one is what a standalone headset can't do
// at 72 frames per second. The parts that share a material are merged here in
// one mesh. Kept free of three.js, so it can be tested with Node.js.

/**
 * Merges parts of non indexed geometries.
 * @param parts an array of {key, position, normal, uv, start, count} where
 *    position, normal (may be null) and uv (may be null) are the arrays of a
 *    geometry with 3, 3 and 2 numbers per vertex, and start / count the
 *    range of its vertices which belongs to the part
 * @return an array of {key, position, normal, uv, parts} with one item per
 *    key and kind of attributes, in the order keys were met
 */
export function mergeParts(parts) {
  const buckets = new Map();
  for (const part of parts) {
    if (part.count <= 0) {
      continue;
    }
    const id = part.key + (part.normal ? '|n' : '|') + (part.uv ? '|t' : '|');
    let bucket = buckets.get(id);
    if (!bucket) {
      bucket = {key: part.key, hasNormal: !!part.normal, hasUV: !!part.uv, count: 0, items: []};
      buckets.set(id, bucket);
    }
    bucket.count += part.count;
    bucket.items.push(part);
  }
  const merged = [];
  for (const bucket of buckets.values()) {
    const position = new Float32Array(bucket.count * 3);
    const normal = bucket.hasNormal ? new Float32Array(bucket.count * 3) : null;
    const uv = bucket.hasUV ? new Float32Array(bucket.count * 2) : null;
    let offset = 0;
    for (const part of bucket.items) {
      copy(part.position, part.start, part.count, 3, position, offset);
      if (normal) {
        copy(part.normal, part.start, part.count, 3, normal, offset);
      }
      if (uv) {
        copy(part.uv, part.start, part.count, 2, uv, offset);
      }
      offset += part.count;
    }
    merged.push({key: bucket.key, position, normal, uv, parts: bucket.items.length});
  }
  return merged;
}

function copy(source, start, count, size, target, offset) {
  if (source.subarray) {
    target.set(source.subarray(start * size, (start + count) * size), offset * size);
  } else {
    for (let i = 0; i < count * size; i++) {
      target[offset * size + i] = source[start * size + i];
    }
  }
}
