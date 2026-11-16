// Review notes of the Sweet Home 3D web viewer: remarks pinned to a point of
// a wall, a piece of furniture, a door, a window or a room while visiting the
// home, which the application stores with the home and gives to its design
// assistant and to Claude. Kept free of three.js, so it can be tested with Node.js.

function pointInPolygon(points, x, z) {
  let inside = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, zi] = points[i];
    const [xj, zj] = points[j];
    if (zi > z !== zj > z && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
      inside = !inside;
    }
  }
  return inside;
}

// Coordinates of a point in the frame of a box set at (x, z) and turned by angle
function toLocal(box, x, z) {
  const dx = x - box.x;
  const dz = z - box.z;
  const cos = Math.cos(box.angle || 0);
  const sin = Math.sin(box.angle || 0);
  return [dx * cos + dz * sin, dz * cos - dx * sin];
}

/**
 * Returns the index of the level someone points at when he points at the height y.
 */
export function levelAt(scene, y) {
  const levels = scene.levels || [];
  let best = 0;
  for (let i = 0; i < levels.length; i++) {
    if (levels[i].elevation <= y + 0.05 && levels[i].elevation >= levels[best].elevation) {
      best = i;
    }
  }
  return best;
}

/**
 * Returns what the point (x, y, z) in meters is on: {target, key, name, level}
 * with target one of wall, door, window, furniture, room or ground.
 * @param key the key of the item the point was picked on, when the page knows it
 */
export function resolveTarget(scene, point, key = null) {
  const level = levelAt(scene, point.y);
  const levels = scene.levels || [];
  const result = (target, item, name) => ({target, key: item && item.key || null, name: name || null,
      level: item && item.level !== undefined ? item.level : level});
  const base = index => levels[index] ? levels[index].elevation : 0;
  if (key) {
    const opening = (scene.openings || []).find(item => item.key === key);
    if (opening) {
      return result(opening.door ? 'door' : 'window', opening);
    }
    const piece = (scene.furniture || []).find(item => item.key === key);
    if (piece) {
      return result('furniture', piece, piece.name);
    }
    const wall = (scene.walls || []).find(item => item.key === key);
    if (wall) {
      return result('wall', wall);
    }
    const room = (scene.rooms || []).find(item => item.key === key);
    if (room) {
      return result('room', room);
    }
  }
  // From where the point is: a door or a window, the smallest piece of furniture it is on...
  const margin = 0.06;
  for (const opening of scene.openings || []) {
    const [localX, localZ] = toLocal(opening, point.x, point.z);
    const bottom = base(opening.level) + opening.sill;
    if (Math.abs(localX) <= opening.width / 2 + margin && Math.abs(localZ) <= opening.depth / 2 + margin
        && point.y >= bottom - margin && point.y <= bottom + opening.height + margin) {
      return result(opening.door ? 'door' : 'window', opening);
    }
  }
  let found = null;
  let foundVolume = Infinity;
  for (const piece of scene.furniture || []) {
    const [localX, localZ] = toLocal(piece, point.x, point.z);
    const bottom = base(piece.level) + piece.elevation;
    const volume = piece.width * piece.depth * piece.height;
    if (Math.abs(localX) <= piece.width / 2 + margin && Math.abs(localZ) <= piece.depth / 2 + margin
        && point.y >= bottom - margin && point.y <= bottom + piece.height + margin && volume < foundVolume) {
      found = piece;
      foundVolume = volume;
    }
  }
  if (found) {
    return result('furniture', found, found.name);
  }
  // ...a wall...
  let nearest = null;
  let nearestDistance = Infinity;
  for (const wall of scene.walls || []) {
    const bottom = base(wall.level);
    const height = wall.height > 0 ? wall.height : 2.5;
    if (point.y < bottom - margin || point.y > bottom + height + margin) {
      continue;
    }
    const dx = wall.x2 - wall.x1;
    const dz = wall.z2 - wall.z1;
    const length2 = dx * dx + dz * dz;
    const t = length2 > 0 ? Math.max(0, Math.min(1, ((point.x - wall.x1) * dx + (point.z - wall.z1) * dz) / length2)) : 0;
    const distance = Math.hypot(point.x - wall.x1 - t * dx, point.z - wall.z1 - t * dz);
    // A round wall is away from its chord
    const reach = wall.thickness / 2 + margin + (wall.arcExtent ? Math.sqrt(length2) * 0.3 : 0);
    if (distance <= reach && distance < nearestDistance) {
      nearest = wall;
      nearestDistance = distance;
    }
  }
  if (nearest) {
    return result('wall', nearest);
  }
  // ...or the floor or the ceiling of a room
  for (const room of scene.rooms || []) {
    if (room.level === level && pointInPolygon(room.points, point.x, point.z)) {
      return result('room', room);
    }
  }
  return result('ground', null);
}

/**
 * Returns a new note about the point (x, y, z) in meters, with an id none of
 * the given notes has. The point is stored in the centimeters of the plan:
 * x and y in the plan, z its height.
 */
export function createNote(scene, notes, point, text, key = null) {
  const resolved = resolveTarget(scene, point, key);
  let number = notes.length + 1;
  while (notes.some(note => note.id === 'n' + number)) {
    number++;
  }
  const level = (scene.levels || [])[resolved.level];
  const note = {id: 'n' + number, text: String(text).trim().slice(0, 2000), status: 'open', target: resolved.target,
      x: Math.round(point.x * 1000) / 10, y: Math.round(point.z * 1000) / 10, z: Math.round(point.y * 1000) / 10,
      created: new Date().toISOString()};
  if (resolved.key) {
    note.key = resolved.key;
  }
  if (resolved.name) {
    note.name = resolved.name;
  }
  if (level && level.name) {
    note.level = level.name;
  }
  return note;
}

/**
 * Returns the notes as a text to give to an assistant.
 */
export function describeNotes(notes) {
  if (notes.length === 0) {
    return 'No review note.';
  }
  return 'Review notes of the home (coordinates in cm of the plan: x to the right, y downward, z = height):\n'
      + notes.map(note => '- ' + note.id + (note.status === 'done' ? ' [done] ' : ' [open] ') + '"' + note.text + '" - on '
          + (note.target || 'the home') + (note.name ? ' "' + note.name + '"' : '')
          + ' at x ' + Math.round(note.x) + ' y ' + Math.round(note.y) + ', height ' + Math.round(note.z)
          + (note.level ? ', level "' + note.level + '"' : '')
          + (note.reply ? ' - reply: ' + note.reply : '')).join('\n');
}
