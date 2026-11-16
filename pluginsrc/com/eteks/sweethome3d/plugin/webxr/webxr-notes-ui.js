// What the walk-through and the game mode show of the review notes: a pin at
// the point of each note, the box to write one, and their list.
import * as THREE from 'three';
import {createNote, describeNotes} from './webxr-notes.js';

const STORAGE_KEY = 'sweetHome3DReviewNotes';

/**
 * Creates the notes of a page.
 * @param options {scene: the three.js scene, getPlan: () => the description of the home,
 *     pick: () => {point: THREE.Vector3 in meters, key} or null, the point aimed at,
 *     onTyping: typing => void, called when the box to write a note opens and closes}
 */
export function createNotesUI(options) {
  const ui = {notes: [], typing: false, stored: false, markers: new Map(), listVisible: false};
  const style = document.createElement('style');
  style.textContent = '#notePanel{position:fixed;left:50%;top:38%;transform:translate(-50%,-50%);width:min(520px,calc(100vw - 32px));'
      + 'box-sizing:border-box;background:rgba(0,0,0,.86);color:#fff;padding:14px 16px;border-radius:8px;font:14px Arial,sans-serif;display:none;z-index:20}'
      + '#notePanel textarea{width:100%;box-sizing:border-box;height:74px;margin-top:8px;font:14px Arial,sans-serif;border-radius:4px;border:0;padding:6px}'
      + '#notePanel .where{color:#ffd98a}#notePanel .keys{color:#bbb;font-size:12px;margin-top:6px}'
      + '#noteList{position:fixed;right:10px;top:10px;width:min(380px,calc(100vw - 20px));max-height:calc(100vh - 20px);overflow:auto;'
      + 'box-sizing:border-box;background:rgba(0,0,0,.8);color:#fff;padding:10px 12px;border-radius:6px;font:13px Arial,sans-serif;display:none;z-index:15}'
      + '#noteList .note{border-top:1px solid rgba(255,255,255,.2);padding:6px 0}#noteList .done{opacity:.6}'
      + '#noteList .where{color:#ffd98a;font-size:12px}#noteList .reply{color:#a8e6a1;font-size:12px}'
      + '#noteList button{font:12px Arial,sans-serif;margin:4px 6px 0 0;cursor:pointer}';
  document.head.appendChild(style);
  const panel = document.createElement('div');
  panel.id = 'notePanel';
  panel.innerHTML = '<div>Note on <span class="where"></span></div><textarea maxlength="2000" '
      + 'placeholder="What should be done here? For instance: add an outlet here, fix this model, move this 30 cm to the left"></textarea>'
      + '<div class="keys">Enter saves the note - Shift+Enter starts a new line - Esc cancels</div>';
  document.body.appendChild(panel);
  const list = document.createElement('div');
  list.id = 'noteList';
  document.body.appendChild(list);
  const textArea = panel.querySelector('textarea');
  const where = panel.querySelector('.where');
  let pending = null;

  function describeTarget(note) {
    return (note.target || 'home') + (note.name ? ' "' + note.name + '"' : '')
        + (note.level ? ', ' + note.level : '') + ', ' + Math.round(note.z) + ' cm high';
  }

  // A round pin with the number of the note, always turned to who looks at it
  function createMarker(note) {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 96;
    const context = canvas.getContext('2d');
    context.beginPath();
    context.arc(48, 48, 42, 0, Math.PI * 2);
    context.fillStyle = note.status === 'done' ? '#3fae4a' : '#ffb300';
    context.fill();
    context.lineWidth = 6;
    context.strokeStyle = '#ffffff';
    context.stroke();
    context.fillStyle = '#1a1a1a';
    context.font = 'bold 44px Arial';
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(note.id.replace(/^n/, ''), 48, 50);
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({map: new THREE.CanvasTexture(canvas), depthTest: false}));
    sprite.scale.setScalar(0.2);
    sprite.renderOrder = 10;
    sprite.position.set(note.x / 100, note.z / 100, note.y / 100);
    return sprite;
  }

  function refresh() {
    for (const marker of ui.markers.values()) {
      options.scene.remove(marker);
      marker.material.map.dispose();
      marker.material.dispose();
    }
    ui.markers.clear();
    for (const note of ui.notes) {
      if (typeof note.x === 'number' && typeof note.y === 'number' && typeof note.z === 'number') {
        const marker = createMarker(note);
        ui.markers.set(note.id, marker);
        options.scene.add(marker);
      }
    }
    list.innerHTML = '';
    const title = document.createElement('div');
    title.innerHTML = '<strong>Review notes (' + ui.notes.length + ')</strong> - '
        + (ui.stored ? 'saved in the home: Claude reads them with get_notes'
            : 'kept in this browser only: copy them to give them to Claude');
    list.appendChild(title);
    const copy = document.createElement('button');
    copy.textContent = 'Copy all as text';
    copy.onclick = () => {
      const text = describeNotes(ui.notes);
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).catch(() => window.prompt('Copy the notes:', text));
      } else {
        window.prompt('Copy the notes:', text);
      }
    };
    list.appendChild(copy);
    for (const note of ui.notes) {
      const item = document.createElement('div');
      item.className = 'note' + (note.status === 'done' ? ' done' : '');
      const text = document.createElement('div');
      text.textContent = note.id.replace(/^n/, '') + '. ' + note.text + (note.status === 'done' ? ' (done)' : '');
      item.appendChild(text);
      const target = document.createElement('div');
      target.className = 'where';
      target.textContent = describeTarget(note);
      item.appendChild(target);
      if (note.reply) {
        const reply = document.createElement('div');
        reply.className = 'reply';
        reply.textContent = 'Reply: ' + note.reply;
        item.appendChild(reply);
      }
      const remove = document.createElement('button');
      remove.textContent = 'Delete';
      remove.onclick = () => {
        ui.notes = ui.notes.filter(other => other !== note);
        save();
        refresh();
      };
      item.appendChild(remove);
      list.appendChild(item);
    }
  }

  // The notes go to the application, which keeps them with the home; a page
  // put on a web site has no application behind it and keeps them in the browser
  function save() {
    try {
      window.localStorage.setItem(STORAGE_KEY + location.pathname, JSON.stringify(ui.notes));
    } catch (ex) {
      // No storage
    }
    return fetch('notes', {method: 'POST', headers: {'X-SweetHome3D-Notes': '1', 'Content-Type': 'application/json'},
        body: JSON.stringify(ui.notes)})
      .then(response => { ui.stored = response.ok; })
      .catch(() => { ui.stored = false; })
      .then(() => {
        if (ui.listVisible) {
          refresh();
        }
      });
  }

  ui.load = async () => {
    try {
      const response = await fetch('notes', {cache: 'no-store'});
      const notes = response.ok ? await response.json() : null;
      if (Array.isArray(notes)) {
        ui.notes = notes;
        ui.stored = true;
      }
    } catch (ex) {
      ui.stored = false;
    }
    if (!ui.stored) {
      try {
        const notes = JSON.parse(window.localStorage.getItem(STORAGE_KEY + location.pathname) || '[]');
        if (Array.isArray(notes)) {
          ui.notes = notes;
        }
      } catch (ex) {
        // No notes
      }
    }
    refresh();
    ui.ready = true;
  };

  function close() {
    panel.style.display = 'none';
    pending = null;
    ui.typing = false;
    textArea.blur();
    if (options.onTyping) {
      options.onTyping(false);
    }
  }

  /**
   * Adds a note with the given text at a point, without asking anything.
   */
  ui.add = (point, text, key = null) => {
    const plan = options.getPlan();
    if (!plan || !String(text).trim()) {
      return null;
    }
    const note = createNote(plan, ui.notes, point, text, key);
    ui.notes.push(note);
    refresh();
    ui.saving = save();
    return note;
  };

  /**
   * Opens the box to write a note about what is aimed at.
   */
  ui.addAtAim = () => {
    const plan = options.getPlan();
    const picked = plan && options.pick();
    if (!picked || ui.typing) {
      return false;
    }
    pending = {point: {x: picked.point.x, y: picked.point.y, z: picked.point.z}, key: picked.key || null};
    where.textContent = describeTarget(createNote(plan, ui.notes, pending.point, '?', pending.key));
    textArea.value = '';
    panel.style.display = 'block';
    ui.typing = true;
    if (options.onTyping) {
      options.onTyping(true);
    }
    setTimeout(() => textArea.focus(), 0);
    return true;
  };

  textArea.addEventListener('keydown', event => {
    event.stopPropagation();
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      const noted = pending;
      const text = textArea.value;
      close();
      if (noted && text.trim()) {
        ui.add(noted.point, text, noted.key);
      }
    }
  });
  textArea.addEventListener('keyup', event => event.stopPropagation());

  ui.toggleList = () => {
    ui.listVisible = !ui.listVisible;
    list.style.display = ui.listVisible ? 'block' : 'none';
    if (ui.listVisible) {
      refresh();
    }
  };
  return ui;
}
