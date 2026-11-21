#!/bin/bash
# Regenerates the pictures of the guides in docs/images from the demo house of
# docs/examples/demo-house, and checks the game mode and the walk-through on it.
# See README.md beside this file.
#
#   docs/tools/screenshots/make-screenshots.sh [renders] [windows] [web]
#
# Without argument, the three parts are run. The application window is shown on
# the display while the script runs.
set -u
TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$TOOLS/../../.." && pwd)"
export SHOTS_WORK="${SHOTS_WORK:-$ROOT/build/doc-screenshots}"
export SHOTS_HOME="$SHOTS_WORK/home"
export SHOTS_OUT="$SHOTS_WORK/out"
export SHOTS_URL="http://127.0.0.1:47997/"
IMAGES="$ROOT/docs/images"
STEPS="$ROOT/docs/examples/demo-house"
JAVA="${JAVA:-java}"
NODE="${NODE:-node}"
PARTS="${*:-renders windows web}"
export DISPLAY="${DISPLAY:-:0}"
fail() { touch "$SHOTS_WORK/failed"; }

for tool in "$JAVA" javac python3 xwininfo import convert montage; do
  command -v "$tool" > /dev/null || { echo "Missing command: $tool"; exit 1; }
done
[ -f "$ROOT/build/SweetHome3D.jar" ] || { echo "Run make build first"; exit 1; }
mkdir -p "$SHOTS_OUT" "$SHOTS_WORK/classes" "$SHOTS_WORK/web"
rm -f "$SHOTS_WORK/failed"
javac -source 8 -target 8 -nowarn -d "$SHOTS_WORK/classes" "$TOOLS/DocShots.java" 2> /dev/null || exit 1

ui() { python3 "$TOOLS/ui.py" "$@" > /dev/null; }
mcp() { python3 "$TOOLS/mcp.py" || fail; }
commands() { python3 "$TOOLS/mcp.py" --commands "$1" > /dev/null || fail; }
apply() { echo "x apply_commands {\"commands\":$1}" | python3 "$TOOLS/mcp.py" > /dev/null || fail; }
# shot <name> <start of the title of the window>
shot() {
  local id
  id=$(xwininfo -root -tree | grep -F "\"$2" | grep -v "Content window" | head -1 | awk '{print $1}')
  [ -n "$id" ] || { echo "No window $2"; fail; return; }
  import -window "$id" "$SHOTS_OUT/$1.png" && echo "window $1.png"
}
jpg() { for name in "$@"; do convert "$SHOTS_OUT/$name.png" -strip -quality 86 "$IMAGES/$name.jpg"; done; }
png() { for name in "$@"; do convert "$SHOTS_OUT/$name.png" -strip "$IMAGES/$name.png"; done; }

stop_application() {
  [ -n "${APPLICATION:-}" ] && kill "$APPLICATION" 2> /dev/null
  [ -n "${WEB_SERVER:-}" ] && kill "$WEB_SERVER" 2> /dev/null
  [ -n "${MODEL:-}" ] && kill "$MODEL" 2> /dev/null
}
trap stop_application EXIT

# Starts the application with an empty home, its own preferences folder and its MCP server
start_application() {
  [ -n "${APPLICATION:-}" ] && kill "$APPLICATION" 2> /dev/null && sleep 2
  rm -rf "$SHOTS_HOME"
  mkdir -p "$SHOTS_HOME/.eteks/sweethome3d"
  touch "$SHOTS_HOME/.eteks/sweethome3d/mcp-autostart"
  (cd "$ROOT" && exec "$JAVA" -Xmx4g -Duser.home="$SHOTS_HOME" -Dcom.eteks.sweethome3d.singleInstance=false \
      -Djogamp.gluegen.UseTempJarCache=false \
      -Djava.library.path="build/native/model-lod:lib/java3d-1.6/linux/amd64:lib/linux/x64" \
      --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED \
      --add-exports=java.desktop/sun.awt=ALL-UNNAMED \
      -cp "$SHOTS_WORK/classes:build/SweetHome3D.jar:build/Furniture.jar:build/Textures.jar:build/Examples.jar:build/Help.jar:lib/java3d-1.6/*:lib/*:libtest/jnlp.jar" \
      DocShots > "$SHOTS_WORK/application.log" 2>&1) &
  APPLICATION=$!
  for i in $(seq 1 90); do
    sleep 1
    ui size 1360 900 2> /dev/null && [ -f "$SHOTS_HOME/.eteks/sweethome3d/mcp-port" ] && break
  done
  sleep 3
}

build_house() {
  for step in "$STEPS"/0*.json; do commands "$step"; done
  echo 'x check_home {}' | LIMIT=200 python3 "$TOOLS/mcp.py" | grep -q "no issue found" || { echo "check_home reports issues"; fail; }
}

VIEW='{"view":"south_east","target":"building","width":900,"height":560}'

if [[ " $PARTS " == *" renders "* ]]; then
  echo "== Pictures of each step of the tutorial"
  start_application
  commands "$STEPS/01-ground-floor.json"; commands "$STEPS/02-openings.json"
  printf 'tut-1-plan get_plan_image {}\ntut-1-3d get_3d_image %s\n' "$VIEW" | mcp
  commands "$STEPS/03-first-floor.json"; echo "tut-2-3d get_3d_image $VIEW" | mcp
  commands "$STEPS/04-furniture.json"; commands "$STEPS/05-roofs.json"; echo "tut-4-roofs get_3d_image $VIEW" | mcp
  commands "$STEPS/06-dormers-walls.json"; echo "tut-5-dormers get_3d_image $VIEW" | mcp
  commands "$STEPS/07-site.json"; echo 'tut-6-site get_3d_image {"view":"south_east","width":900,"height":560}' | mcp
  commands "$STEPS/08-check.json"
  echo "== Views of the finished house"
  mcp <<'XX'
house-3d get_3d_image {"view":"south_east","target":"building","width":1200,"height":750}
view-north-west get_3d_image {"view":"north_west","target":"building","width":900,"height":560}
view-top-ground get_3d_image {"view":"top","levels":"selected","level":"Ground floor","target":"building","width":900,"height":560}
view-inside-living get_3d_image {"view":"inside","target":"R1","level":"Ground floor","width":900,"height":560}
view-inside-kitchen get_3d_image {"view":"inside","target":"R2","level":"Ground floor","width":900,"height":560}
render-plain get_3d_image {"view":"south_west","target":"building","width":900,"height":560,"shadows":false,"ambient_occlusion":false}
render-shadows get_3d_image {"view":"south_west","target":"building","width":900,"height":560}
render-raytrace get_3d_image {"view":"south_west","target":"building","width":900,"height":560,"renderer":"raytrace","timeout":300}
plan-ground get_plan_image {"level":"Ground floor","target":"building"}
plan-first get_plan_image {"level":"First floor","target":"building"}
plan-kitchen get_plan_image {"level":"Ground floor","target":"R2"}
x apply_commands {"commands":[{"action":"hide_layer","tag":"roof"}]}
view-top-first get_3d_image {"view":"top","levels":"selected","level":"First floor","target":"building","width":900,"height":560}
x apply_commands {"commands":[{"action":"show_layer","tag":"roof"}]}
XX
  echo "compare get_3d_image {\"view\":\"south_west\",\"target\":\"building\",\"width\":800,\"height\":500,\"compare_with\":\"$SHOTS_OUT/render-raytrace.png\"}" | mcp
  rm -f "$SHOTS_OUT/elevation-south.svg"
  apply "[{\"action\":\"export_elevation\",\"side\":\"south\",\"path\":\"$SHOTS_OUT/elevation-south.svg\",\"section\":0}]"
  jpg house-3d view-north-west view-top-ground view-top-first view-inside-living view-inside-kitchen \
      render-plain render-shadows render-raytrace tut-1-3d tut-2-3d tut-4-roofs tut-5-dormers tut-6-site compare
  png plan-ground plan-first plan-kitchen tut-1-plan
  cp "$SHOTS_OUT/elevation-south.svg" "$IMAGES/"
fi

if [[ " $PARTS " == *" windows "* || " $PARTS " == *" web "* ]]; then
  start_application
  build_house
fi

if [[ " $PARTS " == *" windows "* ]]; then
  echo "== Windows of the application"
  ui item "Use OpenGL renderer"; ui item "Shadows"; ui item "Soft shading in corners"; ui item "Zoom out"
  apply '[{"action":"select_level","name":"Ground floor"},{"action":"set_camera","point_of_view":"Street side"}]'
  sleep 8; shot main-window "* Untitled"
  convert "$SHOTS_OUT/main-window.png" -strip -quality 90 "$IMAGES/main-window.jpg"
  convert "$SHOTS_OUT/main-window.png" -crop 960x34+2+24 +repage -strip "$IMAGES/toolbar.png"
  # The nine standard views, laid out as their keys on a numeric keypad
  views=("Corner: back left" "Back (from" "Corner: back right" "Left side" "Top" "Right side" \
         "Corner: front left" "Front (from" "Corner: front right")
  tiles=()
  for index in "${!views[@]}"; do
    ui item "${views[$index]}"; sleep 3; shot standard-view-$index "* Untitled"
    convert "$SHOTS_OUT/standard-view-$index.png" -crop 904x370+379+433 +repage -resize 452x185 "$SHOTS_OUT/standard-view-tile-$index.png"
    tiles+=("$SHOTS_OUT/standard-view-tile-$index.png")
  done
  montage "${tiles[@]}" -tile 3x3 -geometry +3+3 -background white -strip -quality 86 "$IMAGES/standard-views.jpg"
  # The doll house display, on the ground floor alone
  apply '[{"action":"set_camera","view":"south_west","ids":["R1","R5"]}]'
  ui item "Display selected level"; sleep 6; shot dollhouse-off "* Untitled"
  ui item "Hide walls in front of the camera"; sleep 5; shot dollhouse-on "* Untitled"
  ui item "Hide walls in front of the camera"; ui item "Display all levels"
  convert "$SHOTS_OUT/dollhouse-off.png" -crop 904x370+379+433 +repage "$SHOTS_OUT/dollhouse-1.png"
  convert "$SHOTS_OUT/dollhouse-on.png" -crop 904x370+379+433 +repage "$SHOTS_OUT/dollhouse-2.png"
  convert "$SHOTS_OUT/dollhouse-1.png" "$SHOTS_OUT/dollhouse-2.png" +append -strip -quality 86 "$IMAGES/dollhouse.jpg"
  # The MCP dialog, with the command of an installed application rather than the one of this build
  ui item "Claude MCP server"; sleep 2
  ui set 0 'claude mcp add --scope user sweethome3d -- /opt/sweethome3d/bin/sweethome3d-mcp --folder /home/you/.eteks/sweethome3d'
  sleep 1; shot mcp-server-dialog "Claude MCP server"; ui click OK; sleep 1
  # The assistant, answered by a canned local server
  python3 "$TOOLS/canned_model.py" & MODEL=$!
  ui item "Design assistant"; sleep 2; ui click "Settings..."; sleep 1
  ui set 0 https://openrouter.ai/api/v1; ui set 2 anthropic/claude-sonnet-4.5; sleep 1; shot assistant-settings "Assistant settings"
  ui set 0 http://127.0.0.1:47998/v1; ui set 2 demo; ui click OK; sleep 1
  ui set 1 "Add a reading corner to the living room"; ui click Send; sleep 8; shot assistant "Design assistant"
  ui close; kill "$MODEL"; MODEL=
  echo 'x undo {}' | python3 "$TOOLS/mcp.py" > /dev/null
  apply '[{"action":"add_tags","where":{"type":"furniture","level":"Ground floor","bbox":[600,-20,1010,430]},"tags":["kitchen"]},{"action":"add_tags","where":{"name":"Bed"},"tags":["bedroom","to buy"]},{"action":"add_tags","where":{"name":"Wardrobe"},"tags":["bedroom","to buy"]},{"action":"add_tags","where":{"name":"Corner sofa"},"tags":["to buy"]}]'
  ui item "Layers and tags"; sleep 2; shot layers-and-tags "Layers and tags"; ui close
  ui item "Add room from shape"; sleep 3; ui combo 0 1; ui set 0 "Family room"; ui set 1 600; ui set 2 500; sleep 1
  shot room-shape-dialog "Add room from shape"; ui click Cancel; sleep 1
  ui item "Home report and budget"; sleep 2; ui set 1 8; ui set 2 35; ui set 3 5; ui set 4 6; ui set 5 EUR
  ui click "Update budget"; sleep 1; shot home-report "Home report and budget"; ui click OK; sleep 1
  ui item "Apply style to rooms"; sleep 2; shot style-dialog "Apply style to rooms"; ui click Cancel; sleep 1
  ui item "What can I ask"; sleep 2; shot assistant-examples "What can I ask"; ui close; sleep 1
  ui item "Export elevation drawing"; sleep 2; shot elevation-dialog "Export elevation drawing"; ui click Cancel; sleep 1
  png mcp-server-dialog assistant assistant-settings assistant-examples layers-and-tags room-shape-dialog home-report \
      style-dialog elevation-dialog
fi

if [[ " $PARTS " == *" web "* ]]; then
  echo "== Game mode and walk-through, in headless Chrome"
  command -v "$NODE" > /dev/null && command -v google-chrome > /dev/null || { echo "Missing node or google-chrome"; exit 1; }
  apply '[{"action":"set_camera","mode":"visitor","x":150,"y":640,"height":165,"look_at_x":150,"look_at_y":200}]'
  rm -rf "$SHOTS_WORK/web" "$SHOTS_WORK/chrome-profile"; mkdir -p "$SHOTS_WORK/web"
  ui item "Export 3D web page"; sleep 3; ui set 0 "$SHOTS_WORK/web"; ui click Save; sleep 20; ui click Close
  folder=$(ls -d "$SHOTS_WORK"/web/*/ | head -1)
  [ -f "$folder/game.html" ] || { echo "The web page wasn't exported"; exit 1; }
  python3 -m http.server 47997 --bind 127.0.0.1 -d "$folder" > /dev/null 2>&1 & WEB_SERVER=$!
  sleep 2
  "$NODE" "$TOOLS/browser.mjs" "${SHOTS_URL}game.html" ./game-shots.mjs || fail
  jpg game-start game-door-closed game-door-open game-third-person game-flying game-first-person game-broken \
      game-notes walkthrough
fi

if [ -f "$SHOTS_WORK/failed" ]; then
  echo "Done WITH ERRORS, see above"
  exit 1
fi
echo "Done: pictures written to docs/images"
