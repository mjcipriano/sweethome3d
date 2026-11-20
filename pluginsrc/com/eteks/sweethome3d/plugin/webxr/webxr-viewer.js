// Viewer of the Sweet Home 3D WebXR preview: walks through the exported home
// at life size, on a desktop browser or in a headset (Meta Quest, SteamVR).
import * as THREE from 'three';
import {MTLLoader} from 'three/addons/loaders/MTLLoader.js';
import {OBJLoader} from 'three/addons/loaders/OBJLoader.js';
import * as walk from './webxr-walk.js';
import {mergeParts} from './webxr-merge.js';
import {createNotesUI} from './webxr-notes-ui.js';

// The model is exported in centimeters
const MODEL_SCALE = 0.01;
// Size in meters of the squares of the plan by which the home is cut, so the
// parts of the home out of sight aren't drawn
const CHUNK_SIZE = 6;
// How far the visitor sees in meters: in VR the distance goes down a step each
// time the headset can't keep up, so a heavy home stays comfortable to visit
const VIEW_DISTANCES = [2000, 60, 35, 22, 14, 9];
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

// What happens in a headset can't be seen from the computer: the page sends
// the steps of the visit and its errors to Sweet Home 3D, which writes them
// in the file preview-log.txt of the exported folder
let reportCount = 0;
const reportedErrors = new Set();
function report(message) {
  if (reportCount++ < 300) {
    try {
      // A home exported as a web page has no server to write the log: stop at the first refusal
      fetch('log', {method: 'POST', body: message, keepalive: true})
        .then(response => { if (!response.ok) reportCount = Infinity; })
        .catch(() => { reportCount = Infinity; });
    } catch (ex) {
      // The visit goes on without the log
    }
  }
}
function reportError(where, ex) {
  const text = where + ': ' + (ex && ex.stack ? ex.stack : ex && ex.message ? ex.message : ex);
  if (!reportedErrors.has(text) && reportedErrors.size < 20) {
    reportedErrors.add(text);
    logError(text);
    report('ERROR ' + text);
  }
}
window.addEventListener('error', event => reportError('page', event.error || event.message));
window.addEventListener('unhandledrejection', event => reportError('promise', event.reason));

// Creates the renderer on the graphics card the headset uses. Without this, a
// computer with a headset linked to it (Quest Link, SteamVR) has to move the
// whole home to the other graphics card when VR starts, and may never finish
function createRenderer() {
  try {
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('webgl2', {alpha: false, depth: true, stencil: true, antialias: true,
        premultipliedAlpha: true, preserveDrawingBuffer: false, powerPreference: 'high-performance',
        xrCompatible: true});
    if (context) {
      return new THREE.WebGLRenderer({canvas, context, antialias: true});
    }
  } catch (ex) {
    console.warn(ex);
  }
  return new THREE.WebGLRenderer({antialias: true, powerPreference: 'high-performance'});
}

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x9fc4e8);
const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.05, 2000);
const renderer = createRenderer();
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setPixelRatio(window.devicePixelRatio);
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.xr.enabled = true;
// The floor of the visitor's real room is the floor of the visited level,
// so the visitor's eyes are at their real height and the home is life size
renderer.xr.setReferenceSpaceType('local-floor');
document.body.insertBefore(renderer.domElement, document.body.firstChild);
// The button to enter VR is shown once the home is loaded, see load()
renderer.domElement.addEventListener('webglcontextrestored', () => report('WebGL context restored'));
renderer.domElement.addEventListener('webglcontextlost', () => {
  report('ERROR WebGL context lost: the home is too heavy for the graphics memory of this device');
  setStatus('The graphics memory of this device is full: reload the page');
});

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
const state = {ready: false, failed: false, meshes: 0, drawnMeshes: 0, triangles: 0, viewDistance: VIEW_DISTANCES[0]};
window.sweetHome3DPreview = state;
// For automated checks of the page
state.debugHead = () => { headPosition(); return [head.x, head.y, head.z, rig.rotation.y]; };
// Meshes and triangles of the last picture, with the given view distance step
state.debugRender = viewDistance => {
  setViewDistance(viewDistance || 0);
  renderer.render(scene, camera);
  return [renderer.info.render.calls, renderer.info.render.triangles, camera.far];
};
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
  if (collisions && plan && (plan.stairs && plan.stairs.length > 0 || plan.terrain && plan.terrain.length > 0)) {
    // Someone who really walks onto stairs climbs them too, and follows the slopes of the ground
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
  vrStats.frames = 0;
  vrStats.since = performance.now();
  vrStats.samples = 0;
  const session = renderer.xr.getSession();
  report('VR session started, frame rate ' + (session && session.frameRate || 'unknown'));
  if (session) {
    session.addEventListener('visibilitychange', () => report('VR session ' + session.visibilityState));
  }
});
renderer.xr.addEventListener('sessionend', () => {
  endTeleport();
  messagePanel.visible = false;
  setViewDistance(0);
  reset();
  report('VR session ended');
});

// ---- Entering VR ----
const VR_START_TIMEOUT = 20000;
let vrSession = null;

// Creates the button which starts and stops the visit in VR. Each step is
// reported, to tell where it stops when the headset stays on a loading screen
function createVRButton() {
  const button = document.createElement('button');
  button.id = 'VRButton';
  button.style.cssText = 'position:fixed; bottom:20px; left:50%; transform:translateX(-50%); padding:12px 18px;'
      + ' border:1px solid #fff; border-radius:4px; background:rgba(0,0,0,0.55); color:#fff;'
      + ' font:normal 13px sans-serif; z-index:999;';
  const disable = text => {
    button.textContent = text;
    button.disabled = true;
    button.style.opacity = '0.6';
  };
  if (!navigator.xr) {
    disable(window.isSecureContext ? 'VR NOT SUPPORTED BY THIS BROWSER' : 'VR NEEDS THE HTTPS ADDRESS');
    return button;
  }
  button.textContent = 'ENTER VR';
  button.style.cursor = 'pointer';
  navigator.xr.isSessionSupported('immersive-vr').then(supported => {
    if (!supported) {
      disable('VR NOT SUPPORTED');
      report('VR not supported by this browser or no headset found');
    }
  }).catch(ex => {
    disable('VR NOT ALLOWED');
    reportError('VR support', ex);
  });
  button.addEventListener('click', () => {
    if (vrSession) {
      vrSession.end();
    } else if (!button.dataset.starting) {
      enterVR(button);
    }
  });
  return button;
}

async function enterVR(button) {
  report('Enter VR pressed');
  button.dataset.starting = 'true';
  button.textContent = 'STARTING VR...';
  let step = 'the headset did not answer the request of a VR session';
  const timeout = setTimeout(() => {
    report('ERROR VR not started after ' + VR_START_TIMEOUT / 1000 + ' s: ' + step);
    setStatus('VR didn\'t start: ' + step + '.');
    logError('With a headset linked to this computer (Quest Link, Air Link, SteamVR), check that its'
        + ' application runs and is the active OpenXR runtime, then reload this page.\n'
        + 'Otherwise open the https address shown by Sweet Home 3D in the browser of the headset itself.');
  }, VR_START_TIMEOUT);
  try {
    const session = await navigator.xr.requestSession('immersive-vr',
        {optionalFeatures: ['local-floor', 'bounded-floor', 'hand-tracking', 'layers']});
    report('VR session granted');
    step = 'the graphics of the page could not be attached to the headset';
    session.addEventListener('end', () => {
      vrSession = null;
      button.textContent = 'ENTER VR';
    });
    await renderer.xr.setSession(session);
    vrSession = session;
    button.textContent = 'EXIT VR';
    report('VR session attached to the page');
  } catch (ex) {
    button.textContent = 'ENTER VR';
    setStatus('VR didn\'t start');
    reportError('enter VR', ex);
  } finally {
    clearTimeout(timeout);
    delete button.dataset.starting;
  }
}

// ---- Smoothness of the visit in VR ----
const vrStats = {frames: 0, since: 0, samples: 0, viewDistance: 0};

function setViewDistance(index) {
  vrStats.viewDistance = index;
  camera.far = VIEW_DISTANCES[index];
  camera.updateProjectionMatrix();
  state.viewDistance = camera.far;
}

// Counts the pictures drawn in VR and shortens the view when they come too slowly
function watchFrameRate() {
  if (!renderer.xr.isPresenting) {
    return;
  }
  vrStats.frames++;
  const now = performance.now();
  const elapsed = now - vrStats.since;
  if (elapsed < 2500) {
    return;
  }
  const rate = vrStats.frames * 1000 / elapsed;
  vrStats.frames = 0;
  vrStats.since = now;
  const sample = vrStats.samples++;
  if (sample === 0) {
    // The first pictures are slow whatever the home: textures and shaders are prepared
    report('VR first pictures: ' + rate.toFixed(1) + ' per second');
    return;
  }
  const session = renderer.xr.getSession();
  const wanted = session && session.frameRate || 72;
  state.frameRate = rate;
  if (sample <= 8 || sample % 8 === 0) {
    report('VR ' + rate.toFixed(1) + ' pictures per second, ' + renderer.info.render.calls + ' meshes and '
        + renderer.info.render.triangles + ' triangles drawn, view ' + camera.far + ' m');
  }
  if (rate < wanted * 0.6 && vrStats.viewDistance < VIEW_DISTANCES.length - 1) {
    setViewDistance(vrStats.viewDistance + 1);
    report('VR view shortened to ' + camera.far + ' m');
    showMessage('This home is heavy for the headset:\nshowing what is within ' + camera.far + ' m', 4);
  }
}

// ---- Desktop controls ----
// Review notes: N writes one about what is under the mouse, T lists them
const pointer = new THREE.Vector2(0, 0);
const noteRaycaster = new THREE.Raycaster();
renderer.domElement.addEventListener('pointermove', event => {
  pointer.set(event.clientX / window.innerWidth * 2 - 1, -(event.clientY / window.innerHeight) * 2 + 1);
});
const notes = createNotesUI({scene, getPlan: () => plan,
    pick: () => {
      noteRaycaster.setFromCamera(pointer, camera);
      noteRaycaster.far = 80;
      const hits = noteRaycaster.intersectObject(modelGroup, true);
      return hits.length > 0 ? {point: hits[0].point, key: null} : null;
    },
    onTyping: typing => {
      if (typing) {
        for (const code in keys) {
          keys[code] = false;
        }
      }
    }});
state.notes = notes;

const keys = {};
let turnArmed = true;
window.addEventListener('keydown', event => {
  if (notes.typing) {
    return;
  }
  if (event.code === 'KeyN' && !event.repeat && state.ready && !renderer.xr.isPresenting) {
    event.preventDefault();
    notes.addAtAim();
    return;
  } else if (event.code === 'KeyT' && !event.repeat) {
    notes.toggleList();
    return;
  }
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

const STANDALONE_HEADSET = /OculusBrowser|Quest|Pico|VR\b.*Android|Android.*\bVR/i.test(navigator.userAgent);

function releaseArray() {
  this.array = null;
}

// Returns the square of the plan where the middle of some vertices is
function chunkOf(position, start, count) {
  let minX = Infinity;
  let maxX = -Infinity;
  let minZ = Infinity;
  let maxZ = -Infinity;
  for (let i = start * 3, end = (start + count) * 3; i < end; i += 3) {
    const x = position[i];
    const z = position[i + 2];
    if (x < minX) { minX = x; }
    if (x > maxX) { maxX = x; }
    if (z < minZ) { minZ = z; }
    if (z > maxZ) { maxZ = z; }
  }
  const size = CHUNK_SIZE / MODEL_SCALE;
  return Math.floor((minX + maxX) / 2 / size) + ',' + Math.floor((minZ + maxZ) / 2 / size);
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
      const count = Math.min(group.count, attributes.position.count - group.start);
      if (count <= 0) {
        continue;
      }
      // See-through parts stay apart, to be drawn from the farthest to the nearest.
      // The others are merged by square of the plan, so what is behind the
      // visitor or too far isn't drawn
      const key = material.transparent || material.opacity < 1
          ? material.uuid + '#' + node.id
          : material.uuid + '@' + chunkOf(attributes.position.array, group.start, count);
      materials.set(key, material);
      parts.push({key,
          position: attributes.position.array,
          normal: attributes.normal ? attributes.normal.array : null,
          uv: attributes.uv ? attributes.uv.array : null,
          start: group.start, count});
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
    geometry.computeBoundingBox();
    state.triangles += part.position.length / 9;
    if (STANDALONE_HEADSET) {
      // Once in the graphics memory, the arrays aren't needed in the small memory
      // of a headset. A computer keeps them: its graphics may have to be
      // created again when a linked headset starts VR
      for (const name in geometry.attributes) {
        geometry.attributes[name].onUpload(releaseArray);
      }
    }
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
    notes.load();
    report('Home loaded in ' + Math.round(performance.now() / 1000) + ' s: ' + state.meshes + ' meshes merged in '
        + state.drawnMeshes + ', ' + Math.round(state.triangles) + ' triangles, '
        + (performance.memory ? megabytes(performance.memory.usedJSHeapSize) + ' used, ' : '')
        + navigator.userAgent);
    if (loadingElement) {
      loadingElement.style.display = 'none';
    }
    // Entering VR is proposed only now, so nobody enters an empty world
    document.body.appendChild(createVRButton());
  } catch (ex) {
    const reason = String(ex && ex.message ? ex.message : ex);
    report('ERROR load: ' + reason);
    showLoadError('Couldn\'t load the home.', /string|memory|allocation/i.test(reason)
        ? reason + '\nThe home is too heavy for this browser: hide some furniture or levels'
            + ' in Sweet Home 3D and start the preview again.'
        : reason + '\nCheck that Sweet Home 3D is still running, then start the preview again.');
  }
}

const clock = new THREE.Clock();
renderer.setAnimationLoop(() => {
  const delta = Math.min(clock.getDelta(), 0.1);
  // Whatever goes wrong with the controls, the picture must still be drawn:
  // a headset which gets no picture stays on its loading screen
  try {
    if (renderer.xr.isPresenting && state.placeInVR > 0 && --state.placeInVR === 0) {
      reset();
    }
    handleControllers(delta);
    handleKeyboard(delta);
    if (messagePanel.visible && performance.now() > messageTimeout) {
      messagePanel.visible = false;
    }
  } catch (ex) {
    reportError('controls', ex);
  }
  renderer.render(scene, camera);
  watchFrameRate();
});
window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
load();
