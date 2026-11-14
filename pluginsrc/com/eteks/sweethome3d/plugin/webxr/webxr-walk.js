// Walk-through logic of the Sweet Home 3D WebXR preview: wall collisions with
// door openings, teleport validation and level lookup. It has no dependency, so
// it runs in the headset browser and in Node for the tests.
// All lengths are in meters; x and z are the plan axes (z = plan y).

/** Radius of the visitor's body. */
export const BODY_RADIUS = 0.15;
/** Openings lower than this above their floor are doors, the others windows. */
const DOOR_MAX_SILL = 0.3;
/** Openings must be at least this high to be walked through. */
const DOOR_MIN_HEIGHT = 1.5;
/** Arc walls are approximated by segments of this angle. */
const ARC_STEP = Math.PI / 16;

/**
 * Returns the wall boxes of one level, with gaps where doors stand.
 * A box is {ax, az, ux, uz, length, half}: start point, unit direction,
 * length and half thickness.
 */
export function buildColliders(scene, levelIndex) {
  const boxes = [];
  const doors = (scene.openings || []).filter(o => o.level === levelIndex
      && o.sill <= DOOR_MAX_SILL && o.height >= DOOR_MIN_HEIGHT);
  for (const wall of scene.walls || []) {
    if (wall.level !== levelIndex) {
      continue;
    }
    for (const [x1, z1, x2, z2] of wallSegments(wall)) {
      const length = Math.hypot(x2 - x1, z2 - z1);
      if (length < 1e-4) {
        continue;
      }
      const ux = (x2 - x1) / length;
      const uz = (z2 - z1) / length;
      const half = wall.thickness / 2;
      // Intervals of the wall left open by doors standing in it
      const gaps = [];
      for (const door of doors) {
        const dx = door.x - x1;
        const dz = door.z - z1;
        const along = dx * ux + dz * uz;
        const across = Math.abs(dz * ux - dx * uz);
        // A door belongs to the wall it stands in and is parallel to
        const parallel = Math.abs(Math.cos(door.angle) * ux + Math.sin(door.angle) * uz);
        if (across <= half + door.depth / 2 + 0.05 && parallel > 0.9
            && along > -door.width / 2 && along < length + door.width / 2) {
          gaps.push([along - door.width / 2, along + door.width / 2]);
        }
      }
      gaps.sort((a, b) => a[0] - b[0]);
      let start = 0;
      for (const [gapStart, gapEnd] of gaps) {
        if (gapStart > start + 1e-3) {
          boxes.push(box(x1, z1, ux, uz, start, Math.min(gapStart, length), half));
        }
        start = Math.max(start, gapEnd);
      }
      if (start < length - 1e-3) {
        boxes.push(box(x1, z1, ux, uz, start, length, half));
      }
    }
  }
  return boxes;
}

function box(x1, z1, ux, uz, from, to, half) {
  return {ax: x1 + ux * from, az: z1 + uz * from, ux, uz, length: to - from, half};
}

/** Returns the straight segments approximating a wall, round walls included. */
function wallSegments(wall) {
  if (!wall.arcExtent) {
    return [[wall.x1, wall.z1, wall.x2, wall.z2]];
  }
  // Circle through both ends seen under the angle arcExtent from its center
  const chord = Math.hypot(wall.x2 - wall.x1, wall.z2 - wall.z1);
  const radius = chord / 2 / Math.sin(Math.abs(wall.arcExtent) / 2);
  const middleX = (wall.x1 + wall.x2) / 2;
  const middleZ = (wall.z1 + wall.z2) / 2;
  const toCenter = Math.sqrt(Math.max(0, radius * radius - chord * chord / 4))
      * (Math.abs(wall.arcExtent) > Math.PI ? -1 : 1) * Math.sign(wall.arcExtent);
  const centerX = middleX - (wall.z2 - wall.z1) / chord * toCenter;
  const centerZ = middleZ + (wall.x2 - wall.x1) / chord * toCenter;
  const startAngle = Math.atan2(wall.z1 - centerZ, wall.x1 - centerX);
  const steps = Math.max(2, Math.ceil(Math.abs(wall.arcExtent) / ARC_STEP));
  const segments = [];
  let previousX = wall.x1;
  let previousZ = wall.z1;
  for (let i = 1; i <= steps; i++) {
    const angle = startAngle - wall.arcExtent * i / steps;
    const x = i === steps ? wall.x2 : centerX + radius * Math.cos(angle);
    const z = i === steps ? wall.z2 : centerZ + radius * Math.sin(angle);
    segments.push([previousX, previousZ, x, z]);
    previousX = x;
    previousZ = z;
  }
  return segments;
}

/** Pushes the point out of the boxes it's closer to than radius. */
function pushOut(x, z, boxes, radius) {
  for (let pass = 0; pass < 4; pass++) {
    let moved = false;
    for (const b of boxes) {
      const dx = x - b.ax;
      const dz = z - b.az;
      const u = dx * b.ux + dz * b.uz;
      const v = dz * b.ux - dx * b.uz;
      const cu = Math.min(Math.max(u, 0), b.length);
      const cv = Math.min(Math.max(v, -b.half), b.half);
      let du = u - cu;
      let dv = v - cv;
      let distance = Math.hypot(du, dv);
      if (distance >= radius) {
        continue;
      }
      let push;
      if (distance < 1e-6) {
        // Inside the wall: leave by its nearest face
        const faces = [[u + radius, -1, 0], [b.length - u + radius, 1, 0],
                       [v + b.half + radius, 0, -1], [b.half - v + radius, 0, 1]];
        faces.sort((f, g) => f[0] - g[0]);
        push = faces[0][0];
        du = faces[0][1];
        dv = faces[0][2];
      } else {
        push = radius - distance;
        du /= distance;
        dv /= distance;
      }
      // Back to world axes: u is (ux, uz), v is (-uz, ux)
      x += (du * b.ux - dv * b.uz) * push;
      z += (du * b.uz + dv * b.ux) * push;
      moved = true;
    }
    if (!moved) {
      break;
    }
  }
  return [x, z];
}

/**
 * Moves from (x, z) by (dx, dz), sliding along the walls, and returns the new [x, z].
 */
export function move(x, z, dx, dz, boxes, radius = BODY_RADIUS) {
  // Small steps so a fast move can't jump over a thin wall
  const steps = Math.max(1, Math.ceil(Math.hypot(dx, dz) / (radius / 2)));
  for (let i = 0; i < steps; i++) {
    [x, z] = pushOut(x + dx / steps, z + dz / steps, boxes, radius);
  }
  return [x, z];
}

/**
 * Returns true if someone can't go straight from (x1, z1) to (x2, z2), because
 * a wall is in the way or the destination is inside a wall.
 */
export function isBlocked(x1, z1, x2, z2, boxes, radius = BODY_RADIUS) {
  const distance = Math.hypot(x2 - x1, z2 - z1);
  const steps = Math.max(1, Math.ceil(distance / (radius / 2)));
  for (let i = 1; i <= steps; i++) {
    const x = x1 + (x2 - x1) * i / steps;
    const z = z1 + (z2 - z1) * i / steps;
    for (const b of boxes) {
      const dx = x - b.ax;
      const dz = z - b.az;
      const u = dx * b.ux + dz * b.uz;
      const v = dz * b.ux - dx * b.uz;
      const du = u - Math.min(Math.max(u, 0), b.length);
      const dv = v - Math.min(Math.max(v, -b.half), b.half);
      if (Math.hypot(du, dv) < radius * 0.5) {
        return true;
      }
    }
  }
  return false;
}

/**
 * Returns the index of the level the visitor starts at.
 */
export function startLevel(scene) {
  const levels = scene.levels || [];
  return scene.start && scene.start.level >= 0 && scene.start.level < levels.length
      ? scene.start.level : 0;
}

/**
 * Returns the index of the level above (step 1) or below (step -1) the given
 * one in elevation order, or the same index if there's none.
 */
export function nextLevel(scene, levelIndex, step) {
  const levels = scene.levels || [];
  // Levels without a floor (roofs) are skipped, unless no level has one
  const anyFloor = levels.some(level => level.floor);
  const order = levels.map((level, index) => index)
      .filter(index => index === levelIndex || !anyFloor || levels[index].floor)
      .sort((a, b) => levels[a].elevation - levels[b].elevation || a - b);
  const position = order.indexOf(levelIndex) + step;
  return position >= 0 && position < order.length ? order[position] : levelIndex;
}

// ---- Stairs ----

/** Largest height someone steps up or down at once. */
export const MAX_STEP = 0.4;

/**
 * Returns the height above the floor of the stairs' level at (x, z) if the
 * point is on one of the flights or landings of the stairs, or null.
 */
function stairHeightAt(stair, x, z) {
  // Coordinates relative to the center of the stairs at angle 0
  const dx = x - stair.x;
  const dz = z - stair.z;
  const cos = Math.cos(stair.angle);
  const sin = Math.sin(stair.angle);
  const localX = dx * cos + dz * sin;
  const localZ = dz * cos - dx * sin;
  let height = null;
  for (const flight of stair.flights || []) {
    if (localX >= flight.x1 && localX <= flight.x2 && localZ >= flight.z1 && localZ <= flight.z2) {
      let progress = 0;
      if (flight.ux > 0) {
        progress = (localX - flight.x1) / (flight.x2 - flight.x1);
      } else if (flight.ux < 0) {
        progress = (flight.x2 - localX) / (flight.x2 - flight.x1);
      } else if (flight.uz > 0) {
        progress = (localZ - flight.z1) / (flight.z2 - flight.z1);
      } else if (flight.uz < 0) {
        progress = (flight.z2 - localZ) / (flight.z2 - flight.z1);
      }
      const flightHeight = (stair.base || 0) + flight.h0 + (flight.h1 - flight.h0) * progress;
      // Where two parts meet, the highest one is the one someone stands on
      height = height === null ? flightHeight : Math.max(height, flightHeight);
    }
  }
  return height;
}

/** Returns the height the stairs climb, up to the floor of the level they lead to. */
function stairTop(scene, stair) {
  const levels = scene.levels || [];
  return stair.topLevel >= 0 && levels[stair.topLevel] && levels[stair.level]
      ? levels[stair.topLevel].elevation - levels[stair.level].elevation
      : (stair.base || 0) + stair.rise;
}

/**
 * Returns where someone standing at (x, z) is: {level, height}, with height
 * the height of their feet above the floor of the level. It's on the stairs
 * of the level, on the stairs coming up from a lower level through the hole
 * in the floor, or on the floor itself. Returns null over a hole in the floor
 * too deep to step down. `from` is the {level, height} the visitor comes from,
 * or only a level index for someone who arrives from elsewhere (teleport).
 */
export function standAt(scene, from, x, z) {
  const position = typeof from === 'number' ? {level: from, height: null} : from;
  const stairs = scene.stairs || [];
  const reachable = height => position.height === null || Math.abs(height - position.height) <= MAX_STEP;
  // On stairs of the current level
  for (const stair of stairs) {
    if (stair.level === position.level) {
      const height = stairHeightAt(stair, x, z);
      if (height !== null) {
        return reachable(height) ? {level: position.level, height} : null;
      }
    }
  }
  // At the top of stairs, on the floor of the level they lead to
  if (position.height !== null && position.height > MAX_STEP) {
    for (const stair of stairs) {
      if (stair.level === position.level && stair.topLevel >= 0
          && Math.abs(stairTop(scene, stair) - position.height) <= MAX_STEP) {
        return standAt(scene, {level: stair.topLevel, height: 0}, x, z);
      }
    }
    return null;
  }
  // Over the hole of stairs coming up from a lower level
  for (const stair of stairs) {
    if (stair.topLevel === position.level) {
      const height = stairHeightAt(stair, x, z);
      if (height !== null) {
        return stairTop(scene, stair) - height <= MAX_STEP ? {level: stair.level, height} : null;
      }
    }
  }
  return {level: position.level, height: 0};
}
