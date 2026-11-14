// Viewer of the Sweet Home 3D WebXR preview: walks through the exported home
// at life size, on a desktop browser or in a headset (Meta Quest, SteamVR).
import * as THREE from 'three';
import {MTLLoader} from 'three/addons/loaders/MTLLoader.js';
import {OBJLoader} from 'three/addons/loaders/OBJLoader.js';
import {VRButton} from 'three/addons/webxr/VRButton.js';
import * as walk from './webxr-walk.js';
import {mergeParts} from './webxr-merge.js';

// The model is exported in centimeters
const MODEL_SCALE = 0.01;
const WALK_SPEED = 1.4;          // meters per second, a normal walking pace
const RUN_SPEED = 3.0;
const SNAP_TURN = Math.PI / 6;   // 30 degrees
const TELEPORT_SPEED = 6.5;      // initial speed of the teleport arc
const TELEPORT_MAX_TIME = 1.6;
const GRAVITY = 9.8;
const STICK_DEAD_ZONE = 0.15;

const statusElement = document.getElementById('status');
const logElement = document.getElementById('log');
const loadingElement = document.getElementById('loading');
const loadingTextElement = document.getElementById('loadingText');
const loadingBarElement = document.getElementById('loadingBar');
function setStatus(message) { statusElement.textContent = message; }
function logError(message) { console.error(message); logElement.textContent += message + '\n'; }
// Shows the progress of the loading in the middle of the page (fraction is
// a number from 0 to 1, or a negative number when it isn't known)
function setLoading(message, fraction) {
  setStatus(message);
  if (loadingElement) {
    loadingTextElement.textContent = message;
    loadingBarElement.style.width = (fraction >= 0 ? Math.round(100 * Math.min(1, fraction)) : 100) + '%';
    loadingBarElement.style.opacity = fraction >= 0 ? '1' : '0.35';
  }
}
function megabytes(bytes) { return Math.round(bytes / 1048576) + ' MB'; }

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x9fc4e8);
const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.05, 2000);
const renderer = new THREE.WebGLRenderer({antialias: true});
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setPixelRatio(window.devicePixelRatio);
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.xr.enabled = true;
// The floor of the visitor's real room is the floor of the visited level,
// so the visitor's eyes are at their real height and the home is life size
renderer.xr.setReferenceSpaceType('local-floor');
document.body.insertBefore(renderer.domElement, document.body.firstChild);
// The button to enter VR is shown once the home is loaded, see load()

// The rig is what moves through the home; the camera (the head) moves inside
// it as the visitor moves in their real room
const rig = new THREE.Group();
rig.rotation.order = 'YXZ';
scene.add(rig);
rig.add(camera);
const modelGroup = new THREE.Group();
modelGroup.scale.setScalar(MODEL_SCALE);
scene.add(modelGroup);
scene.add(new THREE.HemisphereLight(0xffffff, 0x8a8478, 1.5));
const sun = new THREE.DirectionalLight(0xffffff, 1.6);
sun.position.set(30, 100, 50);
scene.add(sun);

// Plan of the home (null if the description couldn't be loaded)
let plan = null;
let levelIndex = 0;
// Height of the visitor's feet above the floor of the level, on stairs
let stairHeight = 0;
let colliders = [];
let collisions = true;
let desktopPitch = 0;
const state = {ready: false, failed: false, meshes: 0, drawnMeshes: 0};
window.sweetHome3DPreview = state;
// For automated checks of the page
state.debugHead = () => { headPosition(); return [head.x, head.y, head.z, rig.rotation.y]; };
state.debugWalk = (moveX, moveZ) => {
  // By steps of 5 cm, as someone walking
  const steps = Math.max(1, Math.ceil(Math.hypot(moveX, moveZ) / 0.05));
  for (let i = 0; i < steps; i++) {
    walkInPlan(moveX / steps, moveZ / steps);
  }
  headPosition();
  return [head.x, head.y, head.z, levelIndex, stairHeight];
};

const head = new THREE.Vector3();
const forward = new THREE.Vector3();
const tmp = new THREE.Vector3();

function floorY() {
  return (plan && plan.levels[levelIndex] ? plan.levels[levelIndex].elevation : 0) + stairHeight;
}

// Puts the visitor on the floor, the stairs or the level found by walk.standAt
function standOn(place) {
  if (place.level !== levelIndex) {
    levelIndex = place.level;
    colliders = walk.buildColliders(plan, levelIndex);
    const name = plan.levels[levelIndex] && plan.levels[levelIndex].name;
    showMessage(name ? 'Level: ' + name : 'Level ' + (levelIndex + 1));
    stairHeight = place.height;
    updateStatus();
  }
  stairHeight = place.height;
  rig.position.y = floorY();
}

function headPosition() {
  camera.getWorldPosition(head);
  return head;
}

// Moves the rig so the visitor's head is above the given point of the plan
function placeHead(x, z) {
  headPosition();
  rig.position.x += x - head.x;
  rig.position.z += z - head.z;
  rig.position.y = floorY();
}

// Turns the rig around the visitor's head, so turning doesn't move them
function turn(angle) {
  headPosition();
  const x = head.x;
  const z = head.z;
  rig.rotation.y += angle;
  rig.updateMatrixWorld(true);
  placeHead(x, z);
}

function setLevel(index, announce) {
  levelIndex = index;
  stairHeight = 0;
  colliders = plan ? walk.buildColliders(plan, levelIndex) : [];
  rig.position.y = floorY();
  if (announce) {
    const name = plan && plan.levels[levelIndex] && plan.levels[levelIndex].name;
    showMessage(name ? 'Level: ' + name : 'Level ' + (levelIndex + 1));
  }
  updateStatus();
}

function changeLevel(step) {
  if (plan) {
    const next = walk.nextLevel(plan, levelIndex, step);
    if (next !== levelIndex) {
      setLevel(next, true);
    }
  }
}

// Puts the visitor where the virtual visitor of the home stands
function reset() {
  if (plan) {
    setLevel(walk.startLevel(plan), false);
    const yaw = plan.start.yaw;
    if (!renderer.xr.isPresenting) {
      // With a yaw of 0 the virtual visitor looks toward +z, a camera toward -z
      rig.rotation.set(0, Math.PI - yaw, 0);
      camera.position.set(0, plan.start.eyeHeight, 0);
      camera.rotation.set(0, 0, 0);
      desktopPitch = 0;
    } else {
      // In VR the visitor's head decides where they look: turn the rig so the
      // direction they face now is the one the virtual visitor looks in
      camera.getWorldDirection(forward);
      rig.rotation.y += Math.atan2(-Math.sin(yaw), Math.cos(yaw)) - Math.atan2(forward.x, forward.z);
    }
    rig.updateMatrixWorld(true);
    placeHead(plan.start.x, plan.start.z);
  } else {
    rig.position.set(0, 0, 0);
    camera.position.set(0, 1.6, 0);
  }
}

// Walks by (dx, dz) meters given in the visitor's own axes (dz > 0 is forward)
function walkBy(dx, dz) {
  if (dx === 0 && dz === 0) {
    return;
  }
  camera.getWorldDirection(forward);
  forward.y = 0;
  if (forward.lengthSq() < 1e-6) {
    return;
  }
  forward.normalize();
  // Right of the visitor
  const rightX = -forward.z;
  const rightZ = forward.x;
  walkInPlan(forward.x * dz + rightX * dx, forward.z * dz + rightZ * dx);
}

// Walks by (moveX, moveZ) meters along the axes of the plan
function walkInPlan(moveX, moveZ) {
  headPosition();
  let x = head.x + moveX;
  let z = head.z + moveZ;
  if (collisions) {
    [x, z] = walk.move(head.x, head.z, moveX, moveZ, colliders);
  }
  if (plan && collisions) {
    // Stairs are climbed, their sides and the holes in the floor block like walls
    const from = {level: levelIndex, height: stairHeight};
    let place = walk.standAt(plan, from, x, z);
    if (!place) {
      // Slide along what blocks
      for (const [slideX, slideZ] of [[x, head.z], [head.x, z]]) {
        place = walk.standAt(plan, from, slideX, slideZ);
        if (place) {
          x = slideX;
          z = slideZ;
          break;
        }
      }
    }
    if (!place) {
      return;
    }
    standOn(place);
  }
  placeHead(x, z);
}

// In VR the visitor may also step physically into a wall: keep their head out
function keepHeadOutOfWalls() {
  if (collisions && colliders.length > 0) {
    headPosition();
    const [x, z] = walk.move(head.x, head.z, 0, 0, colliders);
    if (x !== head.x || z !== head.z) {
      placeHead(x, z);
    }
  }
  if (collisions && plan && plan.stairs && plan.stairs.length > 0) {
    // Someone who really walks onto stairs climbs them too
    headPosition();
    const place = walk.standAt(plan, {level: levelIndex, height: stairHeight}, head.x, head.z);
    if (place && (place.level !== levelIndex || place.height !== stairHeight)) {
      const x = head.x;
      const z = head.z;
      standOn(place);
      placeHead(x, z);
    }
  }
}

// ---- Messages shown in front of the visitor in VR ----
const messageCanvas = document.createElement('canvas');
messageCanvas.width = 1024;
messageCanvas.height = 512;
const messageTexture = new THREE.CanvasTexture(messageCanvas);
const messagePanel = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 0.6),
    new THREE.MeshBasicMaterial({map: messageTexture, transparent: true, depthTest: false}));
messagePanel.renderOrder = 10;
messagePanel.visible = false;
camera.add(messagePanel);
messagePanel.position.set(0, -0.1, -1.6);
let messageTimeout = 0;

function showMessage(text, seconds = 2.5) {
  const lines = text.split('\n');
  const context = messageCanvas.getContext('2d');
  context.clearRect(0, 0, messageCanvas.width, messageCanvas.height);
  context.fillStyle = 'rgba(0, 0, 0, 0.72)';
  context.fillRect(0, 0, messageCanvas.width, messageCanvas.height);
  context.fillStyle = '#ffffff';
  const size = lines.length > 2 ? 40 : 64;
  context.font = size + 'px Arial';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  const lineHeight = size * 1.35;
  lines.forEach((line, i) => context.fillText(line, messageCanvas.width / 2,
      messageCanvas.height / 2 + (i - (lines.length - 1) / 2) * lineHeight));
  messageTexture.needsUpdate = true;
  messagePanel.visible = true;
  messageTimeout = performance.now() + seconds * 1000;
}

const VR_HELP = 'Left stick: walk   (left grip: run)\n'
    + 'Right stick left / right: turn\n'
    + 'Right stick forward or trigger: aim, release to teleport\n'
    + 'A / B: level above / below\n'
    + 'X: walls block you on / off     Y: back to the start';

// ---- Teleport arc and target ----
const ARC_POINTS = 24;
const arcGeometry = new THREE.BufferGeometry();
arcGeometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(ARC_POINTS * 3), 3));
const arcMaterial = new THREE.LineBasicMaterial({color: 0x33ff88});
const arc = new THREE.Line(arcGeometry, arcMaterial);
arc.frustumCulled = false;
arc.visible = false;
scene.add(arc);
const targetMaterial = new THREE.MeshBasicMaterial({color: 0x33ff88, transparent: true, opacity: 0.8});
const target = new THREE.Mesh(new THREE.RingGeometry(0.18, 0.25, 32).rotateX(-Math.PI / 2), targetMaterial);
target.visible = false;
scene.add(target);
const teleport = {aiming: false, valid: false, x: 0, z: 0};
const origin = new THREE.Vector3();
const direction = new THREE.Vector3();

// Follows the path of a thrown ball from the controller down to the floor
function aimTeleport(controller) {
  controller.getWorldPosition(origin);
  controller.getWorldDirection(direction).negate();
  // The floor of the level, even for someone standing on stairs
  const y = floorY() - stairHeight;
  const velocityY = direction.y * TELEPORT_SPEED;
  // Time at which the ball reaches the floor
  const discriminant = velocityY * velocityY + 2 * GRAVITY * (origin.y - y);
  let time = discriminant >= 0 ? (velocityY + Math.sqrt(discriminant)) / GRAVITY : TELEPORT_MAX_TIME;
  const reachesFloor = discriminant >= 0 && time <= TELEPORT_MAX_TIME;
  time = Math.min(time, TELEPORT_MAX_TIME);
  const positions = arcGeometry.attributes.position;
  for (let i = 0; i < ARC_POINTS; i++) {
    const t = time * i / (ARC_POINTS - 1);
    positions.setXYZ(i, origin.x + direction.x * TELEPORT_SPEED * t,
        origin.y + velocityY * t - GRAVITY * t * t / 2,
        origin.z + direction.z * TELEPORT_SPEED * t);
  }
  positions.needsUpdate = true;
  teleport.x = origin.x + direction.x * TELEPORT_SPEED * time;
  teleport.z = origin.z + direction.z * TELEPORT_SPEED * time;
  headPosition();
  // No teleporting through a wall: doorways are the way to the next room
  teleport.valid = reachesFloor
      && !(collisions && walk.isBlocked(head.x, head.z, teleport.x, teleport.z, colliders));
  // Stairs can be teleported onto, not the holes in the floor
  teleport.place = teleport.valid && plan && collisions
      ? walk.standAt(plan, levelIndex, teleport.x, teleport.z) : null;
  if (teleport.valid && plan && collisions && !teleport.place) {
    teleport.valid = false;
  }
  const color = teleport.valid ? 0x33ff88 : 0xff4433;
  arcMaterial.color.setHex(color);
  targetMaterial.color.setHex(color);
  target.position.set(teleport.x, y + (teleport.place ? teleport.place.height : 0) + 0.02, teleport.z);
  arc.visible = true;
  target.visible = reachesFloor;
  teleport.aiming = true;
}

function endTeleport() {
  if (teleport.aiming) {
    if (teleport.valid) {
      if (teleport.place) {
        standOn(teleport.place);
      }
      placeHead(teleport.x, teleport.z);
    }
    teleport.aiming = false;
    arc.visible = false;
    target.visible = false;
  }
}

// ---- Controllers ----
const controllers = [renderer.xr.getController(0), renderer.xr.getController(1)];
for (const controller of controllers) {
  // A short pointer so the visitor sees where their hands are
  const pointer = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), new THREE.Vector3(0, 0, -0.12)]),
      new THREE.LineBasicMaterial({color: 0xffffff}));
  controller.add(pointer);
  controller.add(new THREE.Mesh(new THREE.SphereGeometry(0.015, 12, 8),
      new THREE.MeshBasicMaterial({color: 0xffffff})));
  controller.visible = false;
  controller.addEventListener('connected', event => {
    controller.userData.handedness = event.data.handedness;
    controller.visible = event.data.targetRayMode === 'tracked-pointer';
  });
  controller.addEventListener('disconnected', () => {
    controller.userData.handedness = null;
    controller.visible = false;
  });
  rig.add(controller);
}

function controllerOf(handedness) {
  return controllers.find(controller => controller.userData.handedness === handedness);
}

function deadZone(value) {
  return Math.abs(value) < STICK_DEAD_ZONE ? 0 : value;
}

// Returns the thumbstick of a gamepad: axes 2 and 3 with the standard WebXR
// mapping (Quest Touch, Index...), axes 0 and 1 for controllers with one stick
function stick(gamepad) {
  const axes = gamepad.axes;
  return axes.length >= 4 ? [deadZone(axes[2]), deadZone(axes[3])] : [deadZone(axes[0] || 0), deadZone(axes[1] || 0)];
}

function pressed(gamepad, index) {
  return !!(gamepad.buttons[index] && gamepad.buttons[index].pressed);
}

// Buttons of the previous frame, to act once per press
const previousButtons = {left: [], right: []};
let snapTurnArmed = true;

function handleControllers(delta) {
  const session = renderer.xr.getSession();
  if (!session) {
    return;
  }
  let aiming = false;
  for (const source of session.inputSources) {
    const gamepad = source.gamepad;
    if (!gamepad || (source.handedness !== 'left' && source.handedness !== 'right')) {
      continue;
    }
    const [stickX, stickY] = stick(gamepad);
    const previous = previousButtons[source.handedness];
    const justPressed = index => pressed(gamepad, index) && !previous[index];
    if (source.handedness === 'left') {
      const speed = pressed(gamepad, 1) ? RUN_SPEED : WALK_SPEED;
      walkBy(stickX * speed * delta, -stickY * speed * delta);
      if (justPressed(4)) {
        collisions = !collisions;
        showMessage(collisions ? 'Walls block you' : 'You can walk through walls');
      }
      if (justPressed(5)) {
        reset();
      }
    } else {
      // Turning by steps is much more comfortable than turning smoothly
      if (Math.abs(stickX) > 0.6 && Math.abs(stickX) > Math.abs(stickY)) {
        if (snapTurnArmed) {
          turn(stickX > 0 ? -SNAP_TURN : SNAP_TURN);
          snapTurnArmed = false;
        }
      } else if (Math.abs(stickX) < 0.3) {
        snapTurnArmed = true;
      }
      if (stickY < -0.6 && Math.abs(stickY) > Math.abs(stickX) || pressed(gamepad, 0)) {
        const controller = controllerOf('right');
        if (controller) {
          aimTeleport(controller);
          aiming = true;
        }
      }
      if (justPressed(4)) {
        changeLevel(1);
      }
      if (justPressed(5)) {
        changeLevel(-1);
      }
    }
    for (let i = 0; i < gamepad.buttons.length; i++) {
      previous[i] = gamepad.buttons[i].pressed;
    }
  }
  if (!aiming) {
    endTeleport();
  }
  keepHeadOutOfWalls();
}

renderer.xr.addEventListener('sessionstart', () => {
  // The headset now tracks the head: the camera no longer carries the eye height
  camera.position.set(0, 0, 0);
  camera.rotation.set(0, 0, 0);
  // Wait for the first head pose before placing the visitor
  state.placeInVR = 3;
  showMessage(VR_HELP, 12);
});
renderer.xr.addEventListener('sessionend', () => {
  endTeleport();
  messagePanel.visible = false;
  reset();
});

// ---- Desktop controls ----
const keys = {};
let turnArmed = true;
window.addEventListener('keydown', event => {
  keys[event.code] = true;
  if (event.code === 'KeyR') {
    reset();
  } else if (event.code === 'KeyC') {
    collisions = !collisions;
    updateStatus();
  } else if (event.code === 'PageUp') {
    changeLevel(1);
    event.preventDefault();
  } else if (event.code === 'PageDown') {
    changeLevel(-1);
    event.preventDefault();
  } else if ((event.code === 'KeyQ' || event.code === 'KeyE') && turnArmed) {
    turn(event.code === 'KeyQ' ? SNAP_TURN : -SNAP_TURN);
    turnArmed = false;
  }
});
window.addEventListener('keyup', event => {
  keys[event.code] = false;
  if (event.code === 'KeyQ' || event.code === 'KeyE') {
    turnArmed = true;
  }
});
let dragging = false;
renderer.domElement.addEventListener('pointerdown', event => {
  dragging = true;
  renderer.domElement.setPointerCapture(event.pointerId);
});
renderer.domElement.addEventListener('pointerup', () => { dragging = false; });
renderer.domElement.addEventListener('pointermove', event => {
  if (dragging && !renderer.xr.isPresenting) {
    turn(-event.movementX * 0.004);
    desktopPitch = Math.max(-1.4, Math.min(1.4, desktopPitch - event.movementY * 0.004));
    camera.rotation.set(desktopPitch, 0, 0);
  }
});

function handleKeyboard(delta) {
  if (renderer.xr.isPresenting) {
    return;
  }
  const speed = (keys['ShiftLeft'] || keys['ShiftRight'] ? RUN_SPEED : WALK_SPEED) * delta;
  const dz = (keys['KeyW'] || keys['ArrowUp'] ? 1 : 0) - (keys['KeyS'] || keys['ArrowDown'] ? 1 : 0);
  const dx = (keys['KeyD'] || keys['ArrowRight'] ? 1 : 0) - (keys['KeyA'] || keys['ArrowLeft'] ? 1 : 0);
  walkBy(dx * speed, dz * speed);
}

function updateStatus() {
  if (!state.ready) {
    return;
  }
  const level = plan && plan.levels.length > 1
      ? ' - level ' + (plan.levels[levelIndex].name || levelIndex + 1) : '';
  setStatus('Ready' + level + (collisions ? '' : ' - walking through walls')
      + (plan ? '' : ' - no plan data: walls don\'t block you'));
  state.level = levelIndex;
  state.stairHeight = stairHeight;
  state.colliders = colliders.length;
  state.collisions = collisions;
  headPosition();
  state.head = [head.x, head.y, head.z];
}

// ---- Loading ----
function loadModel() {
  return new Promise((resolve, reject) => {
    new MTLLoader().setPath('./').load('scene.mtl', materials => {
      materials.preload();
      new OBJLoader().setMaterials(materials).setPath('./').load('scene.obj', resolve,
          event => {
            if (event.lengthComputable && event.total > 0) {
              setLoading('Loading the home... ' + megabytes(event.loaded) + ' of ' + megabytes(event.total),
                  event.loaded / event.total);
              if (event.loaded >= event.total) {
                setLoading('Preparing the home...', -1);
              }
            } else {
              setLoading('Loading the home... ' + megabytes(event.loaded), -1);
            }
          }, reject);
    }, undefined, reject);
  });
}

// Returns a copy of the model where the parts that share a material are
// merged in one mesh: a furnished home is exported as tens of thousands of
// meshes, far too many draw calls for a headset
function mergeModel(model) {
  const merged = new THREE.Group();
  const kept = [];
  const parts = [];
  const materials = new Map();
  const geometries = [];
  model.updateMatrixWorld(true);
  model.traverse(node => {
    if (node.isMesh) {
      state.meshes++;
    }
    if (node === model || node.isGroup && !node.isMesh) {
      return;
    }
    const geometry = node.geometry;
    const attributes = geometry && geometry.attributes;
    if (!node.isMesh || geometry.index || !attributes.position || attributes.color
        || attributes.position.isInterleavedBufferAttribute || node.children.length > 0) {
      kept.push(node);
      return;
    }
    const list = Array.isArray(node.material) ? node.material : [node.material];
    const groups = geometry.groups.length > 0 ? geometry.groups
        : [{start: 0, count: attributes.position.count, materialIndex: 0}];
    for (const group of groups) {
      const material = list[group.materialIndex] || list[0];
      // See-through parts stay apart, to be drawn from the farthest to the nearest
      const key = material.transparent || material.opacity < 1 ? material.uuid + '#' + node.id : material.uuid;
      materials.set(key, material);
      parts.push({key,
          position: attributes.position.array,
          normal: attributes.normal ? attributes.normal.array : null,
          uv: attributes.uv ? attributes.uv.array : null,
          start: group.start, count: Math.min(group.count, attributes.position.count - group.start)});
    }
    geometries.push(geometry);
  });
  for (const node of kept) {
    merged.attach(node);
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
    merged.add(new THREE.Mesh(geometry, materials.get(part.key)));
  }
  for (const geometry of geometries) {
    geometry.dispose();
  }
  return merged;
}

function showLoadError(message, detail) {
  state.failed = true;
  setStatus(message);
  logError(detail);
  if (loadingElement) {
    loadingElement.classList.add('error');
    loadingTextElement.textContent = message + '\n' + detail;
    loadingBarElement.style.width = '0';
  }
}

async function load() {
  try {
    const response = await fetch('scene.json');
    if (response.ok) {
      plan = await response.json();
    }
  } catch (ex) {
    logError('No plan description: ' + ex);
  }
  reset();
  try {
    setLoading('Loading the home...', 0);
    let model = await loadModel();
    setLoading('Preparing the home...', -1);
    // Let the page show the message before the long work
    await new Promise(resolve => setTimeout(resolve, 30));
    try {
      model = mergeModel(model);
    } catch (ex) {
      // The home is shown as it was loaded
      state.meshes = 0;
      model.traverse(node => {
        if (node.isMesh) {
          state.meshes++;
        }
      });
      logError('Meshes not merged: ' + (ex && ex.message ? ex.message : ex));
    }
    model.traverse(node => {
      if (node.isMesh) {
        state.drawnMeshes++;
      }
    });
    if (state.meshes === 0) {
      throw new Error('The exported home is empty.');
    }
    modelGroup.add(model);
    if (!plan) {
      // Without the plan, stand in the middle of the model
      const bounds = new THREE.Box3().setFromObject(modelGroup);
      bounds.getCenter(tmp);
      rig.position.set(tmp.x, bounds.min.y, tmp.z);
    }
    const bounds = new THREE.Box3().setFromObject(modelGroup);
    state.bounds = [bounds.min.x, bounds.min.y, bounds.min.z, bounds.max.x, bounds.max.y, bounds.max.z];
    state.ready = true;
    updateStatus();
    if (loadingElement) {
      loadingElement.style.display = 'none';
    }
    // Entering VR is proposed only now, so nobody enters an empty world
    document.body.appendChild(VRButton.createButton(renderer));
  } catch (ex) {
    const reason = String(ex && ex.message ? ex.message : ex);
    showLoadError('Couldn\'t load the home.', /string|memory|allocation/i.test(reason)
        ? reason + '\nThe home is too heavy for this browser: hide some furniture or levels'
            + ' in Sweet Home 3D and start the preview again.'
        : reason + '\nCheck that Sweet Home 3D is still running, then start the preview again.');
  }
}

const clock = new THREE.Clock();
renderer.setAnimationLoop(() => {
  const delta = Math.min(clock.getDelta(), 0.1);
  if (renderer.xr.isPresenting && state.placeInVR > 0 && --state.placeInVR === 0) {
    reset();
  }
  handleControllers(delta);
  handleKeyboard(delta);
  if (messagePanel.visible && performance.now() > messageTimeout) {
    messagePanel.visible = false;
  }
  renderer.render(scene, camera);
});
window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
load();
