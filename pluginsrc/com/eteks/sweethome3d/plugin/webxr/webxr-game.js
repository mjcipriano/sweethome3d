// Game mode of the Sweet Home 3D web viewer: walk, run, jump, climb and fly
// in the exported home as in a video game, in first or third person, open its
// doors and break what is in it - for the time of the game only.
import * as THREE from 'three';
import {MTLLoader} from 'three/addons/loaders/MTLLoader.js';
import {OBJLoader} from 'three/addons/loaders/OBJLoader.js';
import {mergeParts} from './webxr-merge.js';
import * as physics from './webxr-game-physics.js';
import {createNotesUI} from './webxr-notes-ui.js';

// The model is exported in centimeters
const MODEL_SCALE = 0.01;
const THIRD_PERSON_DISTANCE = 3.4;
const BREAK_REACH = 9;
const MAX_DEBRIS = 500;
const DOOR_OPEN_ANGLE = Math.PI * 0.55;

const statusElement = document.getElementById('status');
const hintElement = document.getElementById('hint');
const helpElement = document.getElementById('help');
const crosshairElement = document.getElementById('crosshair');
const startElement = document.getElementById('start');
const startTextElement = document.getElementById('startText');
const startBarElement = document.getElementById('startBar');

function setStart(message, fraction) {
  startTextElement.textContent = message;
  startBarElement.style.width = fraction >= 0 ? Math.round(Math.min(1, fraction) * 100) + '%' : '100%';
}
function megabytes(bytes) { return Math.round(bytes / 1048576) + ' MB'; }

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x9fc4e8);
const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.05, 2000);
camera.rotation.order = 'YXZ';
const renderer = new THREE.WebGLRenderer({antialias: true, powerPreference: 'high-performance'});
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
document.body.insertBefore(renderer.domElement, document.body.firstChild);
const modelGroup = new THREE.Group();
modelGroup.scale.setScalar(MODEL_SCALE);
scene.add(modelGroup);
scene.add(new THREE.HemisphereLight(0xffffff, 0x8a8478, 1.5));
const sun = new THREE.DirectionalLight(0xffffff, 1.6);
sun.position.set(30, 100, 50);
scene.add(sun);

// What the page tells about itself, also read by the tests
const game = {ready: false, failed: false, world: null, player: null, thirdPerson: false, pitch: 0,
              objects: new Map(), leaves: new Map(), hinges: new Map(), doors: new Map(), debris: [], broken: 0, meshes: 0, drawnMeshes: 0, locked: false};
window.game = game;

// ---- The home ----

function loadModel() {
  return new Promise((resolve, reject) => {
    new MTLLoader().setPath('./').load('scene.mtl', materials => {
      materials.preload();
      new OBJLoader().setMaterials(materials).setPath('./').load('scene.obj', resolve,
          event => {
            if (event.lengthComputable && event.total > 0) {
              setStart('Loading the home... ' + megabytes(event.loaded) + ' of ' + megabytes(event.total),
                  event.loaded / event.total);
            } else {
              setStart('Loading the home... ' + megabytes(event.loaded), -1);
            }
          }, reject);
    }, undefined, reject);
  });
}

// Returns the key of the item of the home a shape belongs to, or null
function keyOf(name) {
  const match = /^(?:piece|wall)_([A-Za-z0-9]+)(?:_|$)/.exec(name || '');
  return match ? match[1] : null;
}

// The models of doors and windows name the parts that turn on a hinge and the hinge itself.
// Returns the number of the hinge a shape turns on, or of the hinge it is, or null
function leafOf(name) {
  const match = /_on_hinge_(\d+)/.exec(name || '');
  return match ? match[1] : null;
}
function hingeOf(name) {
  const match = /sweethome3d_hinge_(\d+)/.exec(name || '');
  return match ? match[1] : null;
}

// Gathers the shapes of the model in one group by item of the home, their
// parts that share a material merged in one mesh, so an item is drawn in a
// few calls, and can be turned (a door) or taken away (something broken)
function buildObjects(model) {
  const parts = [];
  const materials = new Map();
  const kept = [];
  model.updateMatrixWorld(true);
  model.traverse(node => {
    if (!node.isMesh) {
      return;
    }
    game.meshes++;
    const geometry = node.geometry;
    const attributes = geometry.attributes;
    if (geometry.index || !attributes.position || attributes.position.isInterleavedBufferAttribute) {
      kept.push(node);
      return;
    }
    let objectKey = keyOf(node.name) || '';
    if (objectKey) {
      const hinge = hingeOf(node.name);
      const leaf = leafOf(node.name);
      if (hinge !== null) {
        // Where the hinge is: the middle of its shapes
        const bounds = game.hinges.get(objectKey + '#' + hinge) || new THREE.Box3();
        geometry.computeBoundingBox();
        bounds.union(geometry.boundingBox);
        game.hinges.set(objectKey + '#' + hinge, bounds);
      } else if (leaf !== null) {
        objectKey += '#' + leaf;
      }
    }
    const list = Array.isArray(node.material) ? node.material : [node.material];
    const groups = geometry.groups.length > 0 ? geometry.groups
        : [{start: 0, count: attributes.position.count, materialIndex: 0}];
    for (const group of groups) {
      const material = list[group.materialIndex] || list[0];
      const count = Math.min(group.count, attributes.position.count - group.start);
      if (count > 0) {
        const key = objectKey + '|' + material.uuid;
        materials.set(key, material);
        parts.push({key, position: attributes.position.array,
            normal: attributes.normal ? attributes.normal.array : null,
            uv: attributes.uv ? attributes.uv.array : null, start: group.start, count});
      }
    }
  });
  const fixed = new THREE.Group();
  for (const node of kept) {
    fixed.attach(node);
  }
  for (const part of mergeParts(parts)) {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(part.position, 3));
    if (part.normal) {
      geometry.setAttribute('normal', new THREE.BufferAttribute(part.normal, 3));
    } else {
      geometry.computeVertexNormals();
    }
    if (part.uv) {
      geometry.setAttribute('uv', new THREE.BufferAttribute(part.uv, 2));
    }
    geometry.computeBoundingSphere();
    geometry.computeBoundingBox();
    const mesh = new THREE.Mesh(geometry, materials.get(part.key));
    const partKey = part.key.substring(0, part.key.indexOf('|'));
    const objectKey = partKey.split('#')[0];
    game.drawnMeshes++;
    if (objectKey) {
      let object = game.objects.get(objectKey);
      if (!object) {
        object = new THREE.Group();
        object.userData.key = objectKey;
        game.objects.set(objectKey, object);
        modelGroup.add(object);
      }
      if (partKey !== objectKey) {
        // A part that turns on a hinge, in a group of its own inside its item
        let leaf = game.leaves.get(partKey);
        if (!leaf) {
          leaf = new THREE.Group();
          game.leaves.set(partKey, leaf);
          object.add(leaf);
        }
        leaf.add(mesh);
      } else {
        object.add(mesh);
      }
    } else {
      fixed.add(mesh);
    }
  }
  modelGroup.add(fixed);
}

// Puts each door on a hinge at one of its ends
function hangDoors() {
  for (const panel of game.world.panels) {
    const object = game.objects.get(panel.key);
    if (panel.kind !== 'door' || !object) {
      continue;
    }
    // The leaves of the door turn on the hinges its model describes...
    const hinges = [];
    for (const [leafKey, leaf] of game.leaves) {
      const bounds = game.hinges.get(leafKey);
      if (leafKey.split('#')[0] === panel.key && bounds) {
        const middle = bounds.getCenter(new THREE.Vector3());
        const hinge = new THREE.Group();
        hinge.position.set(middle.x, 0, middle.z);
        object.add(hinge);
        hinge.add(leaf);
        leaf.position.set(-middle.x, 0, -middle.z);
        // A leaf turns away from the side of the door its hinge is on
        const along = (middle.x * MODEL_SCALE - panel.cx) * panel.cos + (middle.z * MODEL_SCALE - panel.cz) * panel.sin;
        hinges.push({hinge, sign: along > 0 ? -1 : 1});
      }
    }
    if (hinges.length === 0) {
      // ...or the whole door turns around one of its ends
      const hingeX = (panel.cx - panel.hx * panel.cos) / MODEL_SCALE;
      const hingeZ = (panel.cz - panel.hx * panel.sin) / MODEL_SCALE;
      const hinge = new THREE.Group();
      hinge.position.set(hingeX, 0, hingeZ);
      modelGroup.add(hinge);
      hinge.add(object);
      object.position.set(-hingeX, 0, -hingeZ);
      hinges.push({hinge, sign: 1});
    }
    game.doors.set(panel.key, {panel, hinges, angle: 0, target: 0});
  }
}

// ---- The player ----

const avatar = new THREE.Group();
const avatarParts = {};
{
  const skin = new THREE.MeshLambertMaterial({color: 0xf0c8a0});
  const shirt = new THREE.MeshLambertMaterial({color: 0x2f7fd0});
  const trousers = new THREE.MeshLambertMaterial({color: 0x35405a});
  const add = (name, geometry, material, x, y, z) => {
    const pivot = new THREE.Group();
    pivot.position.set(x, y, z);
    const mesh = new THREE.Mesh(geometry, material);
    pivot.add(mesh);
    avatar.add(pivot);
    avatarParts[name] = pivot;
    return mesh;
  };
  add('body', new THREE.BoxGeometry(0.42, 0.6, 0.24), shirt, 0, 1.12, 0);
  add('head', new THREE.SphereGeometry(0.13, 16, 12), skin, 0, 1.6, 0);
  // Limbs hang from their pivot, to swing as he walks
  add('leftArm', new THREE.BoxGeometry(0.11, 0.6, 0.12), shirt, -0.28, 1.4, 0).position.y = -0.3;
  add('rightArm', new THREE.BoxGeometry(0.11, 0.6, 0.12), shirt, 0.28, 1.4, 0).position.y = -0.3;
  add('leftLeg', new THREE.BoxGeometry(0.16, 0.82, 0.18), trousers, -0.11, 0.82, 0).position.y = -0.41;
  add('rightLeg', new THREE.BoxGeometry(0.16, 0.82, 0.18), trousers, 0.11, 0.82, 0).position.y = -0.41;
}
avatar.visible = false;
scene.add(avatar);
let walkCycle = 0;

function respawn() {
  const start = game.world.scene.start || {x: 0, z: 0, yaw: 0, level: 0};
  // With a yaw of 0 the virtual visitor of the home looks toward +z, the player of the game toward -z
  game.player = physics.createPlayer(game.world, start.x, start.z, start.level || 0, Math.PI + (start.yaw || 0));
  const pushed = physics.collide(game.world, game.player.x, game.player.z, game.player.y);
  game.player.x = pushed.x;
  game.player.z = pushed.z;
  game.pitch = 0;
}

const keys = {};
let jumpPressed = false;
let lastSpace = 0;

function setFlying(flying) {
  game.player.flying = flying;
  game.player.vy = 0;
}

window.addEventListener('keydown', event => {
  if (!game.ready || notes.typing) {
    return;
  }
  if (event.code === 'KeyN' && !event.repeat) {
    event.preventDefault();
    notes.addAtAim();
    return;
  } else if (event.code === 'KeyT' && !event.repeat) {
    notes.toggleList();
    return;
  }
  if (event.code === 'Space') {
    event.preventDefault();
    if (!event.repeat) {
      const now = performance.now();
      if (now - lastSpace < 300) {
        setFlying(!game.player.flying);
      } else {
        jumpPressed = true;
      }
      lastSpace = now;
    }
  } else if (!event.repeat) {
    if (event.code === 'KeyF') {
      setFlying(!game.player.flying);
    } else if (event.code === 'KeyV') {
      game.thirdPerson = !game.thirdPerson;
    } else if (event.code === 'KeyE') {
      useDoor();
    } else if (event.code === 'KeyB') {
      rebuild();
    } else if (event.code === 'KeyR') {
      respawn();
    } else if (event.code === 'KeyH') {
      helpElement.style.display = helpElement.style.display === 'none' ? '' : 'none';
    }
  }
  keys[event.code] = true;
});
window.addEventListener('keyup', event => { keys[event.code] = false; });
window.addEventListener('blur', () => {
  for (const code in keys) {
    keys[code] = false;
  }
});

renderer.domElement.addEventListener('click', () => {
  if (game.ready && !game.locked && renderer.domElement.requestPointerLock) {
    renderer.domElement.requestPointerLock();
  }
});
startElement.addEventListener('click', () => {
  if (game.ready) {
    startElement.style.display = 'none';
    if (renderer.domElement.requestPointerLock) {
      renderer.domElement.requestPointerLock();
    }
  }
});
document.addEventListener('pointerlockchange', () => {
  game.locked = document.pointerLockElement === renderer.domElement;
  crosshairElement.style.display = game.locked ? 'block' : 'none';
});
document.addEventListener('mousemove', event => {
  if (game.locked && game.player) {
    game.player.yaw += event.movementX * 0.0024;
    game.pitch = Math.max(-1.5, Math.min(1.5, game.pitch - event.movementY * 0.0024));
  }
});
renderer.domElement.addEventListener('mousedown', event => {
  if (game.locked && event.button === 0 && !notes.typing) {
    breakAimed();
  }
});

// ---- Doors ----

function useDoor() {
  const panel = physics.findDoor(game.world, game.player);
  const door = panel && game.doors.get(panel.key);
  if (!panel) {
    return false;
  }
  const open = physics.toggleDoor(game.world, panel, game.player);
  if (door) {
    if (open) {
      // Away from who opens it
      const [, side] = [0, (game.player.z - panel.cz) * panel.cos - (game.player.x - panel.cx) * panel.sin];
      door.target = (side > 0 ? 1 : -1) * DOOR_OPEN_ANGLE;
    } else {
      door.target = 0;
    }
  }
  return true;
}
game.useDoor = useDoor;

function animateDoors(delta) {
  for (const door of game.doors.values()) {
    if (door.angle !== door.target) {
      const change = Math.sign(door.target - door.angle) * Math.min(Math.abs(door.target - door.angle), delta * 4);
      door.angle += change;
      for (const {hinge, sign} of door.hinges) {
        hinge.rotation.y = door.angle * sign;
      }
    }
  }
}

// ---- Breaking ----

const raycaster = new THREE.Raycaster();
const debrisGeometry = new THREE.BoxGeometry(1, 1, 1);
const box = new THREE.Box3();
const center = new THREE.Vector3();
const size = new THREE.Vector3();

// Returns the key of the item of the home at the center of the picture, or null
function aimedKey() {
  raycaster.setFromCamera(new THREE.Vector2(0, 0), camera);
  raycaster.far = BREAK_REACH + (game.thirdPerson ? THIRD_PERSON_DISTANCE : 0);
  const targets = [];
  for (const object of game.objects.values()) {
    if (object.visible) {
      targets.push(object);
    }
  }
  const hits = raycaster.intersectObjects(targets, true);
  for (const hit of hits) {
    let node = hit.object;
    while (node && !node.userData.key) {
      node = node.parent;
    }
    if (node) {
      return node.userData.key;
    }
  }
  return null;
}

function spawnDebris(object) {
  box.setFromObject(object);
  if (box.isEmpty()) {
    return;
  }
  box.getCenter(center);
  box.getSize(size);
  // The colors of the item
  const colors = [];
  object.traverse(node => {
    if (node.isMesh && node.material && node.material.color) {
      colors.push(node.material.color.getHex());
    }
  });
  const volume = Math.max(0.001, size.x * size.y * size.z);
  const count = Math.max(8, Math.min(48, Math.round(12 + volume * 14)));
  const pieceSize = Math.max(0.05, Math.min(0.28, Math.cbrt(volume / count) * 0.7));
  for (let i = 0; i < count && game.debris.length < MAX_DEBRIS; i++) {
    const material = new THREE.MeshLambertMaterial({color: colors.length > 0 ? colors[i % colors.length] : 0xbbbbbb,
        transparent: true});
    const mesh = new THREE.Mesh(debrisGeometry, material);
    mesh.scale.set(pieceSize * (0.5 + Math.random()), pieceSize * (0.5 + Math.random()), pieceSize * (0.5 + Math.random()));
    mesh.position.set(center.x + (Math.random() - 0.5) * size.x, center.y + (Math.random() - 0.5) * size.y,
        center.z + (Math.random() - 0.5) * size.z);
    mesh.rotation.set(Math.random() * 6, Math.random() * 6, Math.random() * 6);
    scene.add(mesh);
    // Thrown away from who broke it
    const awayX = mesh.position.x - game.player.x;
    const awayZ = mesh.position.z - game.player.z;
    const away = Math.hypot(awayX, awayZ) || 1;
    game.debris.push({mesh, vx: awayX / away * (1 + Math.random() * 2.5) + (Math.random() - 0.5) * 2,
        vy: 1.5 + Math.random() * 3.5, vz: awayZ / away * (1 + Math.random() * 2.5) + (Math.random() - 0.5) * 2,
        spin: (Math.random() - 0.5) * 10, life: 4 + Math.random() * 2, floor: Math.max(game.world.base, box.min.y)});
  }
}

function breakKey(key) {
  const broken = physics.breakObject(game.world, key);
  // What has no box in the world of the game (a ceiling light out of reach for instance) breaks too
  if (broken.length === 0 && game.objects.has(key) && game.objects.get(key).visible) {
    game.world.broken.add(key);
    broken.push(key);
  }
  for (const brokenKey of broken) {
    const object = game.objects.get(brokenKey);
    if (object && object.visible) {
      spawnDebris(object);
      object.visible = false;
      game.broken++;
    }
  }
  return broken;
}
game.breakKey = breakKey;

function breakAimed() {
  const key = aimedKey();
  return key ? breakKey(key) : [];
}
game.breakAimed = breakAimed;
game.aimedKey = aimedKey;

function rebuild() {
  physics.rebuild(game.world);
  for (const object of game.objects.values()) {
    object.visible = true;
  }
  for (const door of game.doors.values()) {
    door.target = 0;
  }
  for (const piece of game.debris) {
    scene.remove(piece.mesh);
    piece.mesh.material.dispose();
  }
  game.debris.length = 0;
  game.broken = 0;
}
game.rebuild = rebuild;

function animateDebris(delta) {
  for (let i = game.debris.length - 1; i >= 0; i--) {
    const piece = game.debris[i];
    piece.vy -= physics.GRAVITY * delta;
    piece.mesh.position.x += piece.vx * delta;
    piece.mesh.position.y += piece.vy * delta;
    piece.mesh.position.z += piece.vz * delta;
    piece.mesh.rotation.x += piece.spin * delta;
    piece.mesh.rotation.z += piece.spin * 0.7 * delta;
    const rest = piece.floor + piece.mesh.scale.y / 2;
    if (piece.mesh.position.y < rest) {
      // Bounces, less and less
      piece.mesh.position.y = rest;
      piece.vy = Math.abs(piece.vy) * 0.35;
      piece.vx *= 0.6;
      piece.vz *= 0.6;
      piece.spin *= 0.5;
    }
    piece.life -= delta;
    piece.mesh.material.opacity = Math.min(1, piece.life);
    if (piece.life <= 0) {
      scene.remove(piece.mesh);
      piece.mesh.material.dispose();
      game.debris.splice(i, 1);
    }
  }
}

// ---- Review notes ----

// What the cross aims at: the point, and the key of the item of the home it is on
function pickAimed() {
  raycaster.setFromCamera(new THREE.Vector2(0, 0), camera);
  raycaster.far = 60;
  const hits = raycaster.intersectObject(modelGroup, true);
  for (const hit of hits) {
    let node = hit.object;
    let visible = true;
    let key = null;
    while (node && node !== modelGroup) {
      visible = visible && node.visible;
      key = key || node.userData.key || null;
      node = node.parent;
    }
    if (visible) {
      return {point: hit.point, key};
    }
  }
  return null;
}
const notes = createNotesUI({scene, getPlan: () => game.world && game.world.scene, pick: pickAimed,
    onTyping: typing => {
      if (typing) {
        for (const code in keys) {
          keys[code] = false;
        }
        if (document.pointerLockElement) {
          document.exitPointerLock();
        }
      } else if (renderer.domElement.requestPointerLock) {
        try {
          const locking = renderer.domElement.requestPointerLock();
          if (locking && locking.catch) {
            locking.catch(() => {});
          }
        } catch (ex) {
          // A click takes the mouse again
        }
      }
    }});
game.notes = notes;

// ---- Each picture ----

function readInput() {
  const input = {
    forward: (keys.KeyW || keys.ArrowUp ? 1 : 0) - (keys.KeyS || keys.ArrowDown ? 1 : 0),
    right: (keys.KeyD || keys.ArrowRight ? 1 : 0) - (keys.KeyA || keys.ArrowLeft ? 1 : 0),
    run: !!(keys.ShiftLeft || keys.ShiftRight),
    jump: jumpPressed,
    up: !!keys.Space,
    down: !!(keys.KeyC || keys.ControlLeft)};
  jumpPressed = false;
  return input;
}

function placeCamera() {
  const player = game.player;
  const headY = player.y + physics.EYE_HEIGHT;
  camera.rotation.set(game.pitch, -player.yaw, 0);
  if (!game.thirdPerson) {
    camera.position.set(player.x, headY, player.z);
    avatar.visible = false;
    return;
  }
  // Behind and above him, as near as needed to stay on his side of the walls
  const sin = Math.sin(player.yaw);
  const cos = Math.cos(player.yaw);
  const backX = -sin * Math.cos(game.pitch);
  const backY = -Math.sin(game.pitch);
  const backZ = cos * Math.cos(game.pitch);
  let distance = THIRD_PERSON_DISTANCE;
  for (let i = 1; i <= 17; i++) {
    const tried = THIRD_PERSON_DISTANCE * i / 17;
    if (physics.pointBlocked(game.world, player.x + backX * tried, headY + 0.15 + backY * tried, player.z + backZ * tried)) {
      distance = Math.max(0.3, THIRD_PERSON_DISTANCE * (i - 1) / 17);
      break;
    }
  }
  camera.position.set(player.x + backX * distance, headY + 0.15 + backY * distance, player.z + backZ * distance);
  avatar.visible = distance > 0.6;
  avatar.position.set(player.x, player.y, player.z);
  avatar.rotation.y = -player.yaw;
}

function animateAvatar(delta) {
  const player = game.player;
  const moving = player.moving && (player.grounded || player.climbing);
  walkCycle += delta * (moving ? (keys.ShiftLeft || keys.ShiftRight ? 14 : 9) : 0);
  const swing = moving ? Math.sin(walkCycle) * 0.7 : 0;
  // Arms up when he flies or climbs
  const raised = player.flying ? -2.6 : player.climbing ? -2.2 + Math.sin(performance.now() / 120) * 0.5 : 0;
  avatarParts.leftLeg.rotation.x = player.grounded || player.climbing ? swing : 0.3;
  avatarParts.rightLeg.rotation.x = player.grounded || player.climbing ? -swing : -0.2;
  avatarParts.leftArm.rotation.x = raised || -swing;
  avatarParts.rightArm.rotation.x = raised ? raised + (player.climbing ? 1 : 0) * 0.6 : swing;
}

let lastStatus = '';
function updateHud() {
  const player = game.player;
  const level = game.world.levels.reduce((best, candidate, index) =>
      candidate.elevation <= player.y + 0.5 && candidate.elevation >= game.world.levels[best].elevation ? index : best, 0);
  const status = (player.flying ? 'Flying' : player.climbing ? 'Climbing' : player.grounded ? 'Walking' : 'In the air')
      + ' - ' + (game.thirdPerson ? 'third person' : 'first person')
      + (game.world.levels.length > 1 ? ' - level ' + (game.world.levels[level].name || level + 1) : '')
      + (game.broken > 0 ? ' - ' + game.broken + ' broken (B rebuilds)' : '');
  if (status !== lastStatus) {
    statusElement.textContent = lastStatus = status;
  }
  const panel = physics.findDoor(game.world, player);
  hintElement.style.display = panel && game.locked ? 'block' : 'none';
  if (panel) {
    hintElement.textContent = 'E - ' + (panel.open ? 'close' : 'open') + ' the door';
  }
}

async function load() {
  try {
    const response = await fetch('scene.json');
    if (!response.ok) {
      throw new Error('The description of the home is missing.');
    }
    game.world = physics.createWorld(await response.json());
    setStart('Loading the home...', 0);
    const model = await loadModel();
    setStart('Preparing the home...', -1);
    await new Promise(resolve => setTimeout(resolve, 30));
    buildObjects(model);
    if (game.meshes === 0) {
      throw new Error('The exported home is empty.');
    }
    hangDoors();
    notes.load();
    // Land around the home, as far as someone may walk or fly
    const land = new THREE.Mesh(new THREE.PlaneGeometry(2000, 2000).rotateX(-Math.PI / 2),
        new THREE.MeshLambertMaterial({color: 0x8fa87a}));
    land.position.y = game.world.lowest - 0.03;
    scene.add(land);
    respawn();
    game.ready = true;
    setStart('Click to play\n\nMouse to look, W A S D to move, Space to jump,\nF to fly, E for doors, click to break, B to rebuild', 1);
    document.getElementById('startTrack').style.display = 'none';
  } catch (ex) {
    game.failed = true;
    startElement.classList.add('error');
    setStart('Couldn\'t load the home.\n' + (ex && ex.message ? ex.message : ex)
        + '\nCheck that Sweet Home 3D is still running, then start the game again.', 1);
  }
}

const clock = new THREE.Clock();
renderer.setAnimationLoop(() => {
  const delta = Math.min(clock.getDelta(), 0.1);
  if (game.ready) {
    // Several small steps for a slow picture, so nothing is gone through
    const input = readInput();
    for (let remaining = delta; remaining > 1e-4; remaining -= 0.02) {
      physics.step(game.world, game.player, input, Math.min(remaining, 0.02));
      input.jump = false;
    }
    animateDoors(delta);
    animateDebris(delta);
    placeCamera();
    animateAvatar(delta);
    updateHud();
  }
  renderer.render(scene, camera);
});
window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
load();
