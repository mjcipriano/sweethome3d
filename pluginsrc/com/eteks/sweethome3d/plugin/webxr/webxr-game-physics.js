// Physics of the game mode of the Sweet Home 3D web viewer: someone who walks,
// runs, jumps, climbs and flies in a home described by scene.json. The home
// is a set of boxes (walls cut by their doors and windows, door and window
// panels, furniture), of floors (the rooms of each level) and of stairs.
// Kept free of three.js, so it can be tested with Node.js.
import {stairHeightAt, terrainHeightAt} from './webxr-walk.js';

export const PLAYER_RADIUS = 0.22;
export const PLAYER_HEIGHT = 1.75;
export const EYE_HEIGHT = 1.6;
export const STEP_HEIGHT = 0.45;   // what is walked onto without jumping
export const WALK_SPEED = 3.2;
export const RUN_SPEED = 6.5;
export const FLY_SPEED = 7;
export const FLY_RUN_SPEED = 16;
export const JUMP_SPEED = 5.2;     // jumps about 1.1 m high
export const CLIMB_SPEED = 2.4;
export const GRAVITY = 12;
const FLOOR_THICKNESS = 0.2;
const ARC_STEP = Math.PI / 16;

function box(kind, key, cx, cz, halfLength, halfThickness, angle, y0, y1) {
  return {kind, key, cx, cz, hx: halfLength, hz: halfThickness, cos: Math.cos(angle), sin: Math.sin(angle),
          y0, y1, active: true};
}

// A wall, or the part of a round wall, between two points
function addWallBoxes(world, wall, x1, z1, x2, z2, base, openings) {
  const length = Math.hypot(x2 - x1, z2 - z1);
  if (length < 1e-4) {
    return;
  }
  const angle = Math.atan2(z2 - z1, x2 - x1);
  const ux = (x2 - x1) / length;
  const uz = (z2 - z1) / length;
  const top = base + wall.height;
  const half = wall.thickness / 2;
  // Doors and windows standing in this wall, as intervals along it
  const gaps = [];
  for (const opening of openings) {
    if (opening.level !== wall.level) {
      continue;
    }
    const dx = opening.x - x1;
    const dz = opening.z - z1;
    const along = dx * ux + dz * uz;
    const across = Math.abs(-dx * uz + dz * ux);
    // In the wall, and set along it
    const turned = Math.abs(Math.sin(opening.angle - angle));
    if (across <= half + opening.depth / 2 + 0.05 && along > -opening.width / 2 && along < length + opening.width / 2
        && turned < 0.35) {
      gaps.push({from: Math.max(0, along - opening.width / 2), to: Math.min(length, along + opening.width / 2), opening});
    }
  }
  gaps.sort((gap1, gap2) => gap1.from - gap2.from);
  const add = (from, to, y0, y1) => {
    if (to - from > 0.01 && y1 - y0 > 0.01) {
      // Walls overlap by half their thickness at their ends, as they are drawn
      const start = from <= 0 ? from - half : from;
      const end = to >= length ? to + half : to;
      const middle = (start + end) / 2;
      const solid = box('wall', wall.key, x1 + ux * middle, z1 + uz * middle, (end - start) / 2, half, angle, y0, y1);
      world.solids.push(solid);
    }
  };
  let position = 0;
  for (const gap of gaps) {
    add(position, gap.from, base, top);
    // Under the sill and above the opening
    const sill = base + gap.opening.sill;
    add(gap.from, gap.to, base, sill);
    add(gap.from, gap.to, sill + gap.opening.height, top);
    // The door or the window itself
    if (!gap.opening.panel && gap.to - gap.from > 0.05) {
      const middle = (gap.from + gap.to) / 2;
      const panel = box(gap.opening.door ? 'door' : 'window', gap.opening.key, x1 + ux * middle, z1 + uz * middle,
          (gap.to - gap.from) / 2, Math.max(0.03, Math.min(half, gap.opening.depth / 2)), angle,
          sill, sill + gap.opening.height);
      panel.open = false;
      panel.wallKey = wall.key;
      gap.opening.panel = panel;
      world.solids.push(panel);
      world.panels.push(panel);
    }
    position = Math.max(position, gap.to);
  }
  add(position, length, base, top);
}

/**
 * Returns the world of the game built from the description of a home.
 */
export function createWorld(scene) {
  const levels = scene.levels && scene.levels.length > 0 ? scene.levels : [{elevation: 0, height: 2.5, floor: true}];
  // Someone walks outdoors at the height of the lowest level that has a floor: a level
  // without room, as the one of the ground built by set_terrain, isn't a basement
  const world = {scene, levels, solids: [], panels: [], floors: [], stairs: [], broken: new Set(),
                 base: Math.min(0, ...levels.filter(level => level.floor !== false).map(level => level.elevation)),
                 lowest: Math.min(0, ...levels.map(level => level.elevation))};
  // Where there is no ground built by set_terrain, the land is flat at this height
  world.flat = world.base;
  for (const triangle of scene.terrain || []) {
    world.base = Math.min(world.base, triangle[2], triangle[5], triangle[8]);
  }
  const openings = (scene.openings || []).map(opening => ({...opening, panel: null}));
  for (const wall of scene.walls || []) {
    const level = levels[wall.level];
    if (!level) {
      continue;
    }
    const height = wall.height > 0 ? wall.height : level.height || 2.5;
    const described = {...wall, height, key: wall.key || 'wall' + world.solids.length};
    if (wall.arcExtent) {
      // A round wall as a few straight ones, without its doors and windows
      const chord = Math.hypot(wall.x2 - wall.x1, wall.z2 - wall.z1);
      const radius = chord / 2 / Math.abs(Math.sin(wall.arcExtent / 2));
      const middleX = (wall.x1 + wall.x2) / 2;
      const middleZ = (wall.z1 + wall.z2) / 2;
      const toCenter = radius * Math.cos(wall.arcExtent / 2) * Math.sign(wall.arcExtent);
      const centerX = middleX - (wall.z2 - wall.z1) / chord * -toCenter;
      const centerZ = middleZ + (wall.x2 - wall.x1) / chord * -toCenter;
      const startAngle = Math.atan2(wall.z1 - centerZ, wall.x1 - centerX);
      const endAngle = Math.atan2(wall.z2 - centerZ, wall.x2 - centerX);
      let sweep = endAngle - startAngle;
      // The short or the long way round, as the extent of the arc tells
      while (sweep > Math.PI) { sweep -= 2 * Math.PI; }
      while (sweep < -Math.PI) { sweep += 2 * Math.PI; }
      if (Math.abs(wall.arcExtent) > Math.PI) {
        sweep -= Math.sign(sweep) * 2 * Math.PI;
      }
      const steps = Math.max(2, Math.ceil(Math.abs(sweep) / ARC_STEP));
      for (let i = 0; i < steps; i++) {
        const angle1 = startAngle + sweep * i / steps;
        const angle2 = startAngle + sweep * (i + 1) / steps;
        addWallBoxes(world, described, centerX + radius * Math.cos(angle1), centerZ + radius * Math.sin(angle1),
            centerX + radius * Math.cos(angle2), centerZ + radius * Math.sin(angle2), level.elevation, []);
      }
    } else {
      addWallBoxes(world, described, wall.x1, wall.z1, wall.x2, wall.z2, level.elevation, openings);
    }
  }
  for (const piece of scene.furniture || []) {
    const level = levels[piece.level];
    // A roof or the ground isn't the box of its size: it is drawn, and stops no one
    if (level && !piece.stairs && !piece.shell && piece.width > 0 && piece.depth > 0 && piece.height > 0) {
      const solid = box('furniture', piece.key, piece.x, piece.z, piece.width / 2, piece.depth / 2, piece.angle,
          level.elevation + piece.elevation, level.elevation + piece.elevation + piece.height);
      solid.name = piece.name;
      world.solids.push(solid);
    }
  }
  for (const room of scene.rooms || []) {
    const level = levels[room.level];
    if (level && room.points && room.points.length >= 3) {
      world.floors.push({level: room.level, y: level.elevation, points: room.points, key: room.key});
    }
  }
  for (const stair of scene.stairs || []) {
    const level = levels[stair.level];
    if (level) {
      // The square of the plan the stairs fill, where the floor above is open
      let radius = 0;
      for (const flight of stair.flights || []) {
        radius = Math.max(radius, Math.hypot(flight.x1, flight.z1), Math.hypot(flight.x2, flight.z2),
            Math.hypot(flight.x1, flight.z2), Math.hypot(flight.x2, flight.z1));
      }
      world.stairs.push({stair, y: level.elevation, radius, key: stair.key, active: true});
    }
  }
  return world;
}

function pointInPolygon(points, x, z) {
  let inside = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const xi = points[i][0];
    const zi = points[i][1];
    const xj = points[j][0];
    const zj = points[j][1];
    if (zi > z !== zj > z && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
      inside = !inside;
    }
  }
  return inside;
}

// Coordinates of a point in the frame of a box
function toLocal(solid, x, z) {
  const dx = x - solid.cx;
  const dz = z - solid.cz;
  return [dx * solid.cos + dz * solid.sin, dz * solid.cos - dx * solid.sin];
}

function blocks(solid) {
  return solid.active && !(solid.kind === 'door' && solid.open);
}

// True if the circle of the given radius around (x, z) is over the box
function over(solid, x, z, radius) {
  const [localX, localZ] = toLocal(solid, x, z);
  return Math.abs(localX) <= solid.hx + radius && Math.abs(localZ) <= solid.hz + radius;
}

// True if the floor of a level is open at (x, z) for stairs coming up from below
function inStairHole(world, levelIndex, x, z) {
  for (const entry of world.stairs) {
    if (entry.active && entry.stair.topLevel === levelIndex
        && stairHeightAt(entry.stair, x, z, null) !== null) {
      return true;
    }
  }
  return false;
}

/**
 * Returns the height of what someone whose feet are at the height `feet`
 * stands on at (x, z): the highest floor, step, piece of furniture or wall
 * top that isn't more than a step above his feet.
 */
export function groundAt(world, x, z, feet) {
  const reach = feet + STEP_HEIGHT;
  let ground = world.flat <= reach ? world.flat : world.base;
  const terrain = terrainHeightAt(world.scene, x, z);
  if (terrain !== null
      // The ground is open under the rooms it reaches
      && !world.floors.some(floor => floor.y <= terrain + 0.3 && pointInPolygon(floor.points, x, z))) {
    ground = terrain <= reach ? terrain : world.base;
  }
  for (const floor of world.floors) {
    if (floor.y > ground && floor.y <= reach && pointInPolygon(floor.points, x, z)
        && !inStairHole(world, floor.level, x, z)) {
      ground = floor.y;
    }
  }
  for (const entry of world.stairs) {
    if (entry.active) {
      const height = stairHeightAt(entry.stair, x, z, feet - entry.y);
      if (height !== null && entry.y + height > ground && entry.y + height <= reach) {
        ground = entry.y + height;
      }
    }
  }
  for (const solid of world.solids) {
    if (blocks(solid) && solid.y1 > ground && solid.y1 <= reach && over(solid, x, z, PLAYER_RADIUS * 0.4)) {
      ground = solid.y1;
    }
  }
  return ground;
}

/**
 * Returns the height of the lowest thing above the head of someone at
 * (x, z) whose head is at the height `head`, or Infinity.
 */
export function ceilingAt(world, x, z, head) {
  let ceiling = Infinity;
  for (const floor of world.floors) {
    const under = floor.y - FLOOR_THICKNESS;
    if (floor.y > world.base + 0.01 && under >= head - 0.05 && under < ceiling && pointInPolygon(floor.points, x, z)
        && !inStairHole(world, floor.level, x, z)) {
      ceiling = under;
    }
  }
  for (const solid of world.solids) {
    if (blocks(solid) && solid.y0 >= head - 0.05 && solid.y0 < ceiling && over(solid, x, z, PLAYER_RADIUS * 0.8)) {
      ceiling = solid.y0;
    }
  }
  return ceiling;
}

/**
 * Moves the circle of someone out of the boxes that stand between his feet
 * (above what he steps onto) and his head. Returns {x, z, touched, nx, nz}
 * where (nx, nz) is the direction he was pushed in.
 */
export function collide(world, x, z, feet) {
  let touched = false;
  let nx = 0;
  let nz = 0;
  for (let pass = 0; pass < 3; pass++) {
    let moved = false;
    for (const solid of world.solids) {
      if (!blocks(solid) || solid.y1 <= feet + STEP_HEIGHT || solid.y0 >= feet + PLAYER_HEIGHT) {
        continue;
      }
      const [localX, localZ] = toLocal(solid, x, z);
      const nearX = Math.max(-solid.hx, Math.min(solid.hx, localX));
      const nearZ = Math.max(-solid.hz, Math.min(solid.hz, localZ));
      let pushX = localX - nearX;
      let pushZ = localZ - nearZ;
      let distance = Math.hypot(pushX, pushZ);
      if (distance >= PLAYER_RADIUS) {
        continue;
      }
      if (distance < 1e-6) {
        // Inside the box: out by its nearest side
        const outX = solid.hx - Math.abs(localX);
        const outZ = solid.hz - Math.abs(localZ);
        if (outX < outZ) {
          pushX = Math.sign(localX) || 1;
          pushZ = 0;
          distance = -outX;
        } else {
          pushX = 0;
          pushZ = Math.sign(localZ) || 1;
          distance = -outZ;
        }
      } else {
        pushX /= distance;
        pushZ /= distance;
      }
      const depth = PLAYER_RADIUS - distance + 0.001;
      // Back in the frame of the plan
      const worldX = pushX * solid.cos - pushZ * solid.sin;
      const worldZ = pushX * solid.sin + pushZ * solid.cos;
      x += worldX * depth;
      z += worldZ * depth;
      nx += worldX;
      nz += worldZ;
      touched = true;
      moved = true;
    }
    if (!moved) {
      break;
    }
  }
  const length = Math.hypot(nx, nz);
  return {x, z, touched, nx: length > 0 ? nx / length : 0, nz: length > 0 ? nz / length : 0};
}

/**
 * Returns someone standing at (x, z) on the given level, looking at yaw
 * (0 = toward -z, growing to the right).
 */
export function createPlayer(world, x, z, levelIndex = 0, yaw = 0) {
  const level = world.levels[levelIndex] || world.levels[0];
  const player = {x, z, y: level.elevation, vy: 0, yaw, grounded: true, flying: false, climbing: false, moving: false};
  player.y = groundAt(world, x, z, player.y + 0.01);
  return player;
}

/**
 * Moves the player during dt seconds. input: {forward, right} in -1..1,
 * run, jump (pressed during this step), up and down (kept pressed).
 */
export function step(world, player, input, dt) {
  dt = Math.min(dt, 0.05);
  const speed = player.flying ? (input.run ? FLY_RUN_SPEED : FLY_SPEED) : (input.run ? RUN_SPEED : WALK_SPEED);
  let moveX = (input.right || 0);
  let moveZ = (input.forward || 0);
  const length = Math.hypot(moveX, moveZ);
  if (length > 1) {
    moveX /= length;
    moveZ /= length;
  }
  // Forward is where the player looks: (sin yaw, -cos yaw) in the plan
  const sin = Math.sin(player.yaw);
  const cos = Math.cos(player.yaw);
  const dx = (sin * moveZ + cos * moveX) * speed * dt;
  const dz = (-cos * moveZ + sin * moveX) * speed * dt;
  player.moving = length > 0.05;
  // Long moves in several steps, not to go through a thin wall
  const steps = Math.max(1, Math.ceil(Math.hypot(dx, dz) / (PLAYER_RADIUS * 0.8)));
  let touched = false;
  let againstX = 0;
  let againstZ = 0;
  for (let i = 0; i < steps; i++) {
    const result = collide(world, player.x + dx / steps, player.z + dz / steps, player.y);
    player.x = result.x;
    player.z = result.z;
    if (result.touched) {
      touched = true;
      againstX = result.nx;
      againstZ = result.nz;
    }
  }
  // Pushing against what stops him
  const pushing = touched && length > 0.05 && (dx * againstX + dz * againstZ) < 0;
  if (player.flying) {
    player.vy = ((input.up ? 1 : 0) - (input.down ? 1 : 0)) * speed;
    player.climbing = false;
  } else {
    if (input.jump && player.grounded) {
      player.vy = JUMP_SPEED;
      player.grounded = false;
    }
    if (pushing && input.up && !player.grounded) {
      // Climbs what he is pushing against
      player.vy = CLIMB_SPEED;
      player.climbing = true;
    } else {
      player.climbing = false;
      player.vy -= GRAVITY * dt;
    }
    player.vy = Math.max(player.vy, -30);
  }
  let y = player.y + player.vy * dt;
  if (player.vy > 0) {
    const ceiling = ceilingAt(world, player.x, player.z, player.y + PLAYER_HEIGHT);
    if (y + PLAYER_HEIGHT > ceiling) {
      y = Math.max(player.y, ceiling - PLAYER_HEIGHT);
      player.vy = 0;
    }
    player.grounded = false;
  }
  const ground = groundAt(world, player.x, player.z, Math.max(player.y, y));
  if (y < ground || y === ground && player.vy <= 0) {
    // Landed, or stepped onto something within reach of his feet
    y = ground;
    if (!player.flying) {
      player.vy = 0;
    }
    player.grounded = true;
  } else if (!player.flying && player.grounded && player.vy <= 0 && y - ground <= STEP_HEIGHT) {
    // Down a step or a slope without leaving the ground
    y = ground;
    player.vy = 0;
  } else {
    player.grounded = false;
  }
  player.y = y;
  // Never under the ground
  if (player.y < world.base) {
    player.y = world.base;
    player.vy = 0;
    player.grounded = true;
  }
  return player;
}

/**
 * Returns the door closest to the player among those within reach in front of him, or null.
 */
export function findDoor(world, player, reach = 2.2) {
  let best = null;
  let bestScore = Infinity;
  const sin = Math.sin(player.yaw);
  const cos = Math.cos(player.yaw);
  for (const panel of world.panels) {
    if (panel.kind !== 'door' || !panel.active
        || panel.y1 < player.y + 0.3 || panel.y0 > player.y + PLAYER_HEIGHT) {
      continue;
    }
    const dx = panel.cx - player.x;
    const dz = panel.cz - player.z;
    const distance = Math.hypot(dx, dz);
    if (distance > reach + panel.hx) {
      continue;
    }
    // In front of him, unless he stands in the doorway
    const facing = distance > 0.01 ? (dx * sin - dz * cos) / distance : 1;
    if (distance > 0.9 && facing < 0.3) {
      continue;
    }
    const score = distance - facing * 0.5;
    if (score < bestScore) {
      bestScore = score;
      best = panel;
    }
  }
  return best;
}

/**
 * Opens or closes a door. A door isn't closed on someone standing in it.
 */
export function toggleDoor(world, panel, player) {
  if (panel.open && player) {
    const [localX, localZ] = toLocal(panel, player.x, player.z);
    if (Math.abs(localX) < panel.hx + PLAYER_RADIUS && Math.abs(localZ) < panel.hz + PLAYER_RADIUS
        && panel.y1 > player.y && panel.y0 < player.y + PLAYER_HEIGHT) {
      return panel.open;
    }
  }
  panel.open = !panel.open;
  return panel.open;
}

/**
 * Breaks what the key designates - a wall with its doors and windows, a door,
 * a window, a piece of furniture or stairs - and returns the keys of what was broken.
 */
export function breakObject(world, key) {
  const broken = [];
  const mark = brokenKey => {
    if (!world.broken.has(brokenKey)) {
      world.broken.add(brokenKey);
      broken.push(brokenKey);
    }
  };
  for (const solid of world.solids) {
    if (solid.active && (solid.key === key || solid.wallKey === key)) {
      solid.active = false;
      mark(solid.key);
    }
  }
  for (const entry of world.stairs) {
    if (entry.active && entry.key === key) {
      entry.active = false;
      mark(key);
    }
  }
  return broken;
}

/**
 * Puts back everything that was broken, and closes the doors.
 */
export function rebuild(world) {
  for (const solid of world.solids) {
    solid.active = true;
    if (solid.kind === 'door') {
      solid.open = false;
    }
  }
  for (const entry of world.stairs) {
    entry.active = true;
  }
  world.broken.clear();
}

/**
 * Returns true if the point (x, y, z) is inside something solid: a wall, a
 * closed door, a piece of furniture or a floor. Used to keep the camera that
 * follows the player on his side of the walls.
 */
export function pointBlocked(world, x, y, z) {
  if (y < world.base) {
    return true;
  }
  for (const solid of world.solids) {
    if (blocks(solid) && y > solid.y0 && y < solid.y1 && over(solid, x, z, 0.08)) {
      return true;
    }
  }
  for (const floor of world.floors) {
    if (floor.y > world.base + 0.01 && y < floor.y && y > floor.y - FLOOR_THICKNESS
        && pointInPolygon(floor.points, x, z) && !inStairHole(world, floor.level, x, z)) {
      return true;
    }
  }
  return false;
}
