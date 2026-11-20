// The pictures of the game mode and of the walk-through, and checks of both on the demo house.
export default async function ({evaluate, key, press, hold, shot, sleep, send}) {
  const base = process.env.SHOTS_URL;
  const type = async text => {
    await sleep(200);
    await evaluate(`(() => { const area = document.querySelector('#notePanel textarea'); area.value = ${JSON.stringify(text)}; area.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true})); })()`);
    await sleep(500);
  };
  const check = (condition, message) => { console.log((condition ? 'ok   ' : 'FAIL ') + message); if (!condition) { process.exitCode = 1; } };
  const player = async () => JSON.parse(await evaluate(`JSON.stringify({x: game.player.x, y: game.player.y, z: game.player.z})`));

  for (let i = 0; i < 240 && !(await evaluate('!!(window.game && (window.game.ready || window.game.failed))')); i++) { await sleep(500); }
  check(await evaluate('game.ready === true'), 'the game page loads');
  check(await evaluate(`performance.getEntriesByType('resource').every(entry => entry.name.startsWith(location.origin))`),
      'without anything from the Internet');
  await evaluate(`document.getElementById('start').style.display = 'none'`);
  await sleep(500); await shot('game-start');
  let p = await player();
  check(Math.abs(p.x - 1.5) < 0.05 && Math.abs(p.z - 6.4) < 0.05 && Math.abs(p.y) < 0.05, 'the player starts in the hall, on its floor');
  await evaluate(`document.getElementById('help').style.display = 'none'`);
  await hold('KeyW', 900); await sleep(200); await shot('game-door-closed');
  await press('KeyE'); await sleep(900); await shot('game-door-open');
  await hold('KeyW', 1500); await sleep(300);
  p = await player();
  check(p.z < 4.2, 'the door opened with E is walked through (z ' + p.z.toFixed(2) + ')');
  // Outdoors: the player follows the slope of the ground
  await evaluate(`game.player.x = 3.5; game.player.z = 13; game.player.y = 0.5; game.player.yaw = 0.25; game.thirdPerson = true; game.pitch = 0.12`); await sleep(900);
  p = await player();
  check(Math.abs(p.y - -0.46) < 0.06, 'the player stands on the slope in front of the house (y ' + p.y.toFixed(2) + ')');
  await shot('game-third-person');
  await hold('KeyS', 1500); await sleep(300);
  const lower = await player();
  check(lower.z > p.z + 1 && lower.y < p.y - 0.05, 'and goes down as he walks down the slope (y ' + lower.y.toFixed(2) + ')');
  // On the driveway, which slopes with the ground 4 cm above it
  await evaluate(`game.player.x = 13.1; game.player.z = 12; game.player.y = 0.5`); await sleep(900);
  p = await player();
  check(Math.abs(p.y - -0.336) < 0.05, 'the player stands on the sloped driveway (y ' + p.y.toFixed(2) + ')');
  await evaluate(`game.player.x = 3.5; game.player.z = 13; game.player.y = 0`); await sleep(400);
  // Flying
  await press('Space'); await sleep(80); await key('keydown', 'Space'); await sleep(1900); await key('keyup', 'Space');
  await evaluate(`game.pitch = -0.45`); await sleep(300); await shot('game-flying');
  check(await evaluate('game.player.flying && game.player.y > 5'), 'Space twice flies');
  await press('KeyF'); await sleep(2500);
  // Under the roof, on the first floor: the roof doesn't push the player out
  await evaluate(`game.thirdPerson = false; game.player.x = 7.0; game.player.z = 3.4; game.player.y = 2.62; game.player.yaw = 0; game.pitch = 0`); await sleep(600);
  p = await player();
  check(Math.abs(p.x - 7.0) < 0.05 && Math.abs(p.y - 2.62) < 0.05, 'the player stays in a bedroom under the roof');
  // Breaking the table of the dining room
  await evaluate(`game.player.x = 8.1; game.player.z = 6.95; game.player.y = 0; game.player.yaw = 0; game.pitch = -0.45`); await sleep(500);
  await shot('game-first-person');
  await evaluate(`game.pitch = -0.75`); await sleep(300);
  check(await evaluate('game.breakAimed().length') === 1, 'a click breaks the table');
  await sleep(350); await shot('game-broken');
  await press('KeyB'); await sleep(300);
  // Notes on a wall, a piece of furniture and a floor
  await evaluate(`game.player.x = 3.0; game.player.z = 1.5; game.player.yaw = 0; game.pitch = 0.1`); await sleep(300);
  await key('keydown', 'KeyN'); await type('add an outlet on this wall');
  await evaluate(`game.player.x = 1.3; game.player.z = 3.0; game.player.yaw = 0; game.pitch = -0.45`); await sleep(300);
  await key('keydown', 'KeyN'); await type('replace this sofa by a lighter one');
  await evaluate(`game.player.x = 3; game.player.z = 2.5; game.player.yaw = 0; game.pitch = -0.9`); await sleep(300);
  await key('keydown', 'KeyN'); await type('darker floor in this room');
  await evaluate(`game.player.x = 2.3; game.player.z = 3.9; game.player.yaw = 0; game.pitch = -0.25`); await sleep(400);
  await key('keydown', 'KeyT'); await sleep(400); await shot('game-notes');
  const targets = await evaluate(`game.notes.notes.map(note => note.target + (note.name ? ' ' + note.name : '')).join(', ')`);
  check(targets === 'wall, furniture Corner sofa, room', 'notes find their item: ' + targets);

  // The walk-through
  await send('Page.navigate', {url: base + 'index.html'});
  for (let i = 0; i < 240 && !(await evaluate('!!(window.sweetHome3DPreview && (window.sweetHome3DPreview.ready || window.sweetHome3DPreview.failed))')); i++) { await sleep(500); }
  check(await evaluate('window.sweetHome3DPreview.ready === true'), 'the walk-through page loads');
  await sleep(1500); await shot('walkthrough');
  const walkBy = async (x, z) => { const [px, , pz, level, height] = JSON.parse(await evaluate(`JSON.stringify(window.sweetHome3DPreview.debugWalk(${x}, ${z}))`)); return {x: px, z: pz, level, height}; };
  let place = await walkBy(0, 0);
  check(Math.abs(place.x - 1.5) < 0.05 && Math.abs(place.z - 6.4) < 0.05 && place.level === 1 && place.height === 0,
      'the visitor starts in the hall, on the ground floor');
  // Toward the west wall of the hall
  for (let i = 0; i < 30; i++) { place = await walkBy(-0.1, 0); }
  check(place.x > 0 && place.x < 0.6, 'a wall stops the visitor (x ' + place.x.toFixed(2) + ')');
  place = await walkBy(1.5 - place.x, 0);
  // Out by the front door, then down the garden
  for (let i = 0; i < 80; i++) { place = await walkBy(0, 0.1); }
  check(place.z > 12 && place.level === 1 && Math.abs(place.height - -0.0854 * (place.z - 7.6)) < 0.08,
      'outdoors the visitor follows the slope (z ' + place.z.toFixed(2) + ', height ' + place.height.toFixed(2) + ')');
}
