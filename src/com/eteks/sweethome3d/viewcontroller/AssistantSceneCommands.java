/*
 * AssistantSceneCommands.java 21 nov. 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.viewcontroller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomeMaterial;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Transformation;
import com.eteks.sweethome3d.model.UserPreferences;

/**
 * The commands that set the lights, the sun, the doors and the materials of models.
 */
class AssistantSceneCommands {
  /** The property of a home whose 3D view is lit by the sun at the date and time of its camera. */
  static final String SUN_FOLLOWS_TIME_PROPERTY = "sunFollowsTime";
  /** The angle in degrees the leaves of a door or a window were opened by. */
  static final String OPENING_ANGLE_PROPERTY = "assistantOpeningAngle";

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantSceneCommands(Home home, UserPreferences preferences, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class,
   * each one starting with two spaces and ending with a new line.
   */
  static String getReference() {
    return "  {\"action\":\"set_light\",\"ids\":[\"F1\"],\"power\":60}  (the power of lights in percent, 0 turning them off; \"on\":true|false; "
        + "instead of ids, \"all\":true or \"where\". It shows in pictures drawn by the ray tracer and in the color of rooms in the 3D view)\n"
        + "  {\"action\":\"set_sun\",\"date\":\"2026-06-21\",\"time\":\"17:30\",\"follow\":true}  (the date and the local time of the sun, "
        + "at the latitude, longitude and time zone of the compass (set_compass): the ray tracer always uses them, and with \"follow\":true the "
        + "3D view and the OpenGL pictures are lit and shadowed from where the sun is too. The answer gives its height and its direction, "
        + "sunrise and sunset. Without date or time, reports them)\n"
        + "  {\"action\":\"set_environment\",\"sky_color\":\"#RRGGBB\",\"ground_color\":\"#RRGGBB\",\"light_color\":\"#RRGGBB\","
        + "\"walls_transparency\":N,\"all_levels_visible\":true}  (the sky, the flat ground and the light of the 3D view; walls_transparency "
        + "from 0 opaque to 100; \"sky_texture\" and \"ground_texture\" take a texture name, or false to remove it)\n"
        + "  {\"action\":\"open_door\",\"ids\":[\"F1\"],\"angle\":90}  (turns the leaves of doors and windows on their hinges, in degrees: "
        + "a negative angle opens the other way, 0 or \"open\":false closes. Only the models made with hinges can open: the answer says which can't)\n"
        + "  {\"action\":\"list_materials\",\"id\":\"F1\"}  (the names of the materials of the 3D model of a piece, with the ones changed)\n"
        + "  {\"action\":\"set_material\",\"ids\":[\"F1\"],\"material\":\"<name>\",\"color\":\"#RRGGBB\"}  (recolors ONE material of the model "
        + "of a piece and leaves the others: the fabric of a sofa without its legs. \"texture\":\"<texture name>\" instead of color, "
        + "\"shininess\":0-100, \"reset\":true to give back the look of the model; material may be a part of the name)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("set_light".equals(action) || "set_lights".equals(action)) {
        setLight(command, tally, notes);
      } else if ("set_sun".equals(action)) {
        setSun(command, notes);
      } else if ("set_environment".equals(action)) {
        setEnvironment(command, notes);
      } else if ("open_door".equals(action) || "open_window".equals(action) || "close_door".equals(action)
                 || "close_window".equals(action)) {
        openDoor(command, action.startsWith("close"), tally, notes);
      } else if ("list_materials".equals(action)) {
        listMaterials(command, notes);
      } else if ("set_material".equals(action)) {
        setMaterial(command, tally, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    }
    return true;
  }

  private void post(final String name, final Runnable undo, final Runnable redo) {
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() throws CannotUndoException {
          super.undo();
          undo.run();
        }

        @Override
        public void redo() throws CannotRedoException {
          super.redo();
          redo.run();
        }

        @Override
        public String getPresentationName() {
          return name;
        }
      });
  }

  private static void collect(List<HomePieceOfFurniture> furniture, List<HomePieceOfFurniture> pieces) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collect(((HomeFurnitureGroup)piece).getFurniture(), pieces);
      } else {
        pieces.add(piece);
      }
    }
  }

  private List<HomePieceOfFurniture> getPieces(AssistantCommand command) {
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomeFurnitureGroup) {
        collect(((HomeFurnitureGroup)item).getFurniture(), pieces);
      } else if (item instanceof HomePieceOfFurniture) {
        pieces.add((HomePieceOfFurniture)item);
      }
    }
    return pieces;
  }

  private String describe(HomePieceOfFurniture piece) {
    String id = this.executor.getId(piece);
    return (id != null ? id + " " : "") + "\"" + piece.getName() + "\"";
  }

  // ---- Lights ----

  private void setLight(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    final List<HomeLight> lights = new ArrayList<HomeLight>();
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    if (command.getBoolean("all", false)) {
      collect(this.home.getFurniture(), pieces);
    } else {
      pieces = getPieces(command);
    }
    int others = 0;
    for (HomePieceOfFurniture piece : pieces) {
      if (piece instanceof HomeLight) {
        lights.add((HomeLight)piece);
      } else {
        others++;
      }
    }
    if (lights.isEmpty()) {
      throw new IOException(others > 0 ? "none of these pieces is a light: lights are the pieces of the Lights category of the catalog"
          : "give the lights with \"ids\", \"where\" or \"all\":true; this home has " + countLights() + " lights");
    }
    if (!command.has("power") && !command.has("on")) {
      StringBuilder text = new StringBuilder("Lights:");
      for (HomeLight light : lights) {
        text.append(' ').append(describe(light)).append(' ').append(Math.round(light.getPower() * 100)).append(" %;");
      }
      notes.add(text.toString() + " give \"power\" (0 to 100) or \"on\" to change them");
      return;
    }
    double percent = command.has("power") ? command.getDouble("power", 50) : command.getBoolean("on", true) ? 50 : 0;
    if (command.has("on") && !command.getBoolean("on", true)) {
      percent = 0;
    }
    if (percent < 0 || percent > 100) {
      throw new IOException("\"power\" goes from 0 to 100");
    }
    final float power = (float)(percent / 100);
    final float [] oldPowers = new float [lights.size()];
    for (int i = 0; i < oldPowers.length; i++) {
      oldPowers [i] = lights.get(i).getPower();
      lights.get(i).setPower(power);
    }
    post("Light power", new Runnable() {
        public void run() {
          for (int i = 0; i < oldPowers.length; i++) {
            lights.get(i).setPower(oldPowers [i]);
          }
        }
      }, new Runnable() {
        public void run() {
          for (HomeLight light : lights) {
            light.setPower(power);
          }
        }
      });
    tally [4] += lights.size();
    notes.add("Set the power of " + lights.size() + (lights.size() == 1 ? " light" : " lights") + " to " + Math.round(percent) + " %"
        + (percent == 0 ? " (off)" : "") + (others > 0 ? "; " + others + " pieces aren't lights" : "")
        + ". The ray tracer shows their light (get_3d_image with \"renderer\":\"raytrace\")");
  }

  private int countLights() {
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    collect(this.home.getFurniture(), pieces);
    int count = 0;
    for (HomePieceOfFurniture piece : pieces) {
      if (piece instanceof HomeLight) {
        count++;
      }
    }
    return count;
  }

  // ---- Sun ----

  private static final String [] DIRECTIONS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

  /**
   * Returns the height and the azimuth of the sun in degrees at the given time of the cameras,
   * the azimuth being clockwise from the true north.
   */
  private double [] getSun(long cameraTime) {
    Compass compass = this.home.getCompass();
    long time = Camera.convertTimeToTimeZone(cameraTime, compass.getTimeZone());
    return new double [] {Math.toDegrees(compass.getSunElevation(time)),
        (Math.toDegrees(compass.getSunAzimuth(time)) % 360 + 360) % 360};
  }

  private String describeSun(long cameraTime) {
    Compass compass = this.home.getCompass();
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(cameraTime);
    double [] sun = getSun(cameraTime);
    // Sunrise and sunset of that day, to the minute
    Calendar day = (Calendar)calendar.clone();
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
    int sunrise = -1;
    int sunset = -1;
    boolean up = getSun(day.getTimeInMillis()) [0] > 0;
    for (int minute = 1; minute < 24 * 60; minute++) {
      boolean nowUp = getSun(day.getTimeInMillis() + minute * 60000L) [0] > 0;
      if (nowUp && !up && sunrise < 0) {
        sunrise = minute;
      } else if (!nowUp && up) {
        sunset = minute;
      }
      up = nowUp;
    }
    String text = String.format(Locale.US, "%04d-%02d-%02d at %02d:%02d (%s), latitude %.2f, longitude %.2f: ",
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), compass.getTimeZone(),
        Math.toDegrees(compass.getLatitude()), Math.toDegrees(compass.getLongitude()));
    if (sun [0] > 0) {
      text += String.format(Locale.US, "the sun is %.0f degrees above the horizon, in the %s (azimuth %.0f degrees from the north)",
          sun [0], DIRECTIONS [(int)Math.round(sun [1] / 45) % 8], sun [1]);
      // In the plan, the top is north_direction degrees before the north
      double planAngle = ((sun [1] + Math.toDegrees(compass.getNorthDirection())) % 360 + 360) % 360;
      text += String.format(Locale.US, ", which is %.0f degrees clockwise from the top of the plan: it lights the sides of the building that face it",
          planAngle);
    } else {
      text += "the sun is under the horizon (night)";
    }
    if (sunrise >= 0 && sunset >= 0) {
      text += String.format(Locale.US, "; sunrise about %02d:%02d, sunset about %02d:%02d (within 10 minutes)", sunrise / 60, sunrise % 60, sunset / 60, sunset % 60);
    }
    return text;
  }

  private void setSun(AssistantCommand command, List<String> notes) throws IOException {
    final Camera [] cameras = {this.home.getTopCamera(), this.home.getObserverCamera()};
    final long oldTime = this.home.getCamera().getTime();
    final long [] oldTimes = {cameras [0].getTime(), cameras [1].getTime()};
    final String oldFollow = this.home.getProperty(SUN_FOLLOWS_TIME_PROPERTY);
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(oldTime);
    boolean changed = false;
    String date = command.getString("date");
    if (date != null) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(date.trim());
      if (!matcher.matches() || Integer.parseInt(matcher.group(2)) < 1 || Integer.parseInt(matcher.group(2)) > 12
          || Integer.parseInt(matcher.group(3)) < 1 || Integer.parseInt(matcher.group(3)) > 31) {
        throw new IOException("\"date\" is written YYYY-MM-DD");
      }
      calendar.set(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) - 1, Integer.parseInt(matcher.group(3)));
      changed = true;
    }
    String time = command.getString("time");
    if (time != null) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(time.trim());
      if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > 23 || Integer.parseInt(matcher.group(2)) > 59) {
        throw new IOException("\"time\" is written HH:MM, from 00:00 to 23:59");
      }
      calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(matcher.group(1)));
      calendar.set(Calendar.MINUTE, Integer.parseInt(matcher.group(2)));
      calendar.set(Calendar.SECOND, 0);
      changed = true;
    }
    final long newTime = calendar.getTimeInMillis();
    final String newFollow = command.has("follow") ? (command.getBoolean("follow", true) ? "true" : null) : oldFollow;
    boolean followChanged = newFollow == null ? oldFollow != null : !newFollow.equals(oldFollow);
    if (!changed && !followChanged) {
      notes.add("Sun: " + describeSun(oldTime) + ". The 3D view is " + (oldFollow != null ? "" : "not ")
          + "lit from the sun (\"follow\":true|false); give \"date\" and \"time\" to change them");
      return;
    }
    final boolean timeChanged = changed;
    Runnable redo = new Runnable() {
        public void run() {
          if (timeChanged) {
            cameras [0].setTime(newTime);
            cameras [1].setTime(newTime);
          }
          home.setProperty(SUN_FOLLOWS_TIME_PROPERTY, newFollow);
        }
      };
    redo.run();
    post("Sun", new Runnable() {
        public void run() {
          cameras [0].setTime(oldTimes [0]);
          cameras [1].setTime(oldTimes [1]);
          home.setProperty(SUN_FOLLOWS_TIME_PROPERTY, oldFollow);
        }
      }, redo);
    notes.add("Set the sun: " + describeSun(newTime) + ". "
        + (newFollow != null ? "The 3D view and the OpenGL pictures are lit and shadowed from it"
            : "The ray tracer uses it; the 3D view keeps its own lights (\"follow\":true lights it from the sun)"));
  }

  // ---- Environment ----

  private void setEnvironment(AssistantCommand command, List<String> notes) throws IOException {
    final HomeEnvironment environment = this.home.getEnvironment();
    final int oldSky = environment.getSkyColor();
    final int oldGround = environment.getGroundColor();
    final int oldLight = environment.getLightColor();
    final float oldAlpha = environment.getWallsAlpha();
    final boolean oldAllLevels = environment.isAllLevelsVisible();
    final HomeTexture oldSkyTexture = environment.getSkyTexture();
    final HomeTexture oldGroundTexture = environment.getGroundTexture();
    int sky = oldSky;
    int ground = oldGround;
    int light = oldLight;
    float alpha = oldAlpha;
    boolean allLevels = oldAllLevels;
    HomeTexture skyTexture = oldSkyTexture;
    HomeTexture groundTexture = oldGroundTexture;
    List<String> changes = new ArrayList<String>();
    if (command.getString("sky_color") != null) {
      sky = parse(command.getString("sky_color"), "sky_color");
      changes.add("sky color");
    }
    if (command.getString("ground_color") != null) {
      ground = parse(command.getString("ground_color"), "ground_color");
      changes.add("ground color");
    }
    if (command.getString("light_color") != null) {
      light = parse(command.getString("light_color"), "light_color");
      changes.add("light color");
    }
    if (command.has("walls_transparency")) {
      double transparency = command.getDouble("walls_transparency", 0);
      if (transparency < 0 || transparency > 100) {
        throw new IOException("\"walls_transparency\" goes from 0 (opaque) to 100");
      }
      alpha = (float)(transparency / 100);
      changes.add("walls transparency " + Math.round(transparency) + " %");
    }
    if (command.has("all_levels_visible")) {
      allLevels = command.getBoolean("all_levels_visible", true);
      changes.add(allLevels ? "all levels shown" : "the selected level and the ones under it shown");
    }
    if (command.has("sky_texture")) {
      skyTexture = getTexture(command, "sky_texture", notes);
      changes.add(skyTexture != null ? "sky texture " + skyTexture.getName() : "no sky texture");
    }
    if (command.has("ground_texture")) {
      groundTexture = getTexture(command, "ground_texture", notes);
      changes.add(groundTexture != null ? "ground texture " + groundTexture.getName() : "no ground texture");
    }
    if (changes.isEmpty()) {
      notes.add(String.format(Locale.US, "Environment of the 3D view: sky #%06X%s, ground #%06X%s, light #%06X, walls transparency %d %%, %s. "
          + "Give sky_color, ground_color, light_color, walls_transparency, all_levels_visible, sky_texture or ground_texture to change it",
          oldSky & 0xFFFFFF, oldSkyTexture != null ? " with the texture " + oldSkyTexture.getName() : "",
          oldGround & 0xFFFFFF, oldGroundTexture != null ? " with the texture " + oldGroundTexture.getName() : "",
          oldLight & 0xFFFFFF, Math.round(oldAlpha * 100), oldAllLevels ? "all levels shown" : "levels above the selected one hidden"));
      return;
    }
    final int newSky = sky;
    final int newGround = ground;
    final int newLight = light;
    final float newAlpha = alpha;
    final boolean newAllLevels = allLevels;
    final HomeTexture newSkyTexture = skyTexture;
    final HomeTexture newGroundTexture = groundTexture;
    Runnable redo = new Runnable() {
        public void run() {
          environment.setSkyColor(newSky);
          environment.setGroundColor(newGround);
          environment.setLightColor(newLight);
          environment.setWallsAlpha(newAlpha);
          environment.setAllLevelsVisible(newAllLevels);
          environment.setSkyTexture(newSkyTexture);
          environment.setGroundTexture(newGroundTexture);
        }
      };
    redo.run();
    post("3D view", new Runnable() {
        public void run() {
          environment.setSkyColor(oldSky);
          environment.setGroundColor(oldGround);
          environment.setLightColor(oldLight);
          environment.setWallsAlpha(oldAlpha);
          environment.setAllLevelsVisible(oldAllLevels);
          environment.setSkyTexture(oldSkyTexture);
          environment.setGroundTexture(oldGroundTexture);
        }
      }, redo);
    StringBuilder text = new StringBuilder("Changed the 3D view: ");
    for (int i = 0; i < changes.size(); i++) {
      text.append(i > 0 ? ", " : "").append(changes.get(i));
    }
    notes.add(text.toString());
  }

  private static int parse(String color, String name) throws IOException {
    Integer value = AssistantCommandExecutor.parseColor(color);
    if (value == null) {
      throw new IOException("\"" + name + "\" is written #RRGGBB");
    }
    return value.intValue() & 0xFFFFFF;
  }

  private HomeTexture getTexture(AssistantCommand command, String name, List<String> notes) throws IOException {
    String textureName = command.getString(name);
    if (textureName == null || "false".equalsIgnoreCase(textureName) || "none".equalsIgnoreCase(textureName)) {
      return null;
    }
    CatalogTexture texture = this.executor.getBuildingCommands().findTexture(textureName, null, null,
        command.getAction(), notes, new String [1]);
    if (texture == null) {
      throw new IOException("no texture is named \"" + textureName + "\" (use search_textures)");
    }
    return new HomeTexture(texture);
  }

  // ---- Doors ----

  private void openDoor(AssistantCommand command, boolean close, int [] tally, List<String> notes) throws IOException {
    AssistantCommandExecutor.ModelInspector inspector = this.executor.getModelInspector();
    if (inspector == null) {
      throw new IOException("3D models can't be read here");
    }
    List<HomePieceOfFurniture> pieces = getPieces(command);
    if (pieces.isEmpty()) {
      throw new IOException("give the doors or windows with \"ids\" or \"where\"");
    }
    float angle = close || command.has("open") && !command.getBoolean("open", true) ? 0
        : (float)command.getDouble("angle", 90);
    if (Math.abs(angle) > 180) {
      throw new IOException("\"angle\" goes from -180 to 180 degrees");
    }
    final List<HomePieceOfFurniture> changed = new ArrayList<HomePieceOfFurniture>();
    final List<Object []> newStates = new ArrayList<Object []>();
    List<String> fixed = new ArrayList<String>();
    for (HomePieceOfFurniture piece : pieces) {
      AssistantCommandExecutor.ModelOpening opening;
      try {
        opening = inspector.getOpening(piece.getModel(), piece.getModelRotation(), piece.getModelTransformations(), angle);
      } catch (RuntimeException ex) {
        fixed.add(describe(piece));
        continue;
      }
      if (opening.hingeCount == 0) {
        fixed.add(describe(piece) + (opening.railCount > 0 ? " (it slides)" : ""));
        continue;
      }
      // The box of the piece follows the one of its model, as when its leaves are turned in the window that modifies it
      float [] current = opening.currentBounds;
      float [] next = opening.newBounds;
      float scaleX = piece.getWidth() / Math.max(1E-6f, current [3] - current [0]);
      float scaleY = piece.getHeight() / Math.max(1E-6f, current [4] - current [1]);
      float scaleZ = piece.getDepth() / Math.max(1E-6f, current [5] - current [2]);
      float dx = scaleX * ((next [3] + next [0]) / 2 - (current [3] + current [0]) / 2);
      float dz = scaleZ * ((next [5] + next [2]) / 2 - (current [5] + current [2]) / 2);
      if (piece.isModelMirrored()) {
        dx = -dx;
      }
      double cos = Math.cos(piece.getAngle());
      double sin = Math.sin(piece.getAngle());
      newStates.add(new Object [] {opening.transformations,
          Float.valueOf((float)(piece.getX() + dx * cos - dz * sin)), Float.valueOf((float)(piece.getY() + dx * sin + dz * cos)),
          Float.valueOf(piece.getElevation() + scaleY * (next [1] - current [1])),
          Float.valueOf(scaleX * (next [3] - next [0])), Float.valueOf(scaleZ * (next [5] - next [2])),
          Float.valueOf(scaleY * (next [4] - next [1])),
          Math.abs(angle) < 0.01f ? null : AssistantStructureCommands.format(angle)});
      changed.add(piece);
    }
    if (!changed.isEmpty()) {
      final List<Object []> oldStates = new ArrayList<Object []>();
      for (HomePieceOfFurniture piece : changed) {
        oldStates.add(new Object [] {piece.getModelTransformations(), Float.valueOf(piece.getX()), Float.valueOf(piece.getY()),
            Float.valueOf(piece.getElevation()), Float.valueOf(piece.getWidth()), Float.valueOf(piece.getDepth()),
            Float.valueOf(piece.getHeight()), piece.getProperty(OPENING_ANGLE_PROPERTY)});
      }
      Runnable redo = new Runnable() {
          public void run() {
            setStates(changed, newStates);
          }
        };
      redo.run();
      post("Open doors", new Runnable() {
          public void run() {
            setStates(changed, oldStates);
          }
        }, redo);
      tally [4] += changed.size();
      StringBuilder text = new StringBuilder(Math.abs(angle) < 0.01f ? "Closed " : "Opened by " + AssistantStructureCommands.format(angle) + " degrees ");
      for (int i = 0; i < changed.size(); i++) {
        text.append(i > 0 ? ", " : "").append(describe(changed.get(i)));
      }
      notes.add(text.toString() + (Math.abs(angle) < 0.01f ? "" : ". If the leaves went through the wall the wrong way, give the opposite angle"));
    }
    if (!fixed.isEmpty()) {
      StringBuilder text = new StringBuilder("These models have no leaf on hinges and can't be opened: ");
      for (int i = 0; i < fixed.size(); i++) {
        text.append(i > 0 ? ", " : "").append(fixed.get(i));
      }
      notes.add(text.toString());
    }
  }

  private static void setStates(List<HomePieceOfFurniture> pieces, List<Object []> states) {
    for (int i = 0; i < pieces.size(); i++) {
      HomePieceOfFurniture piece = pieces.get(i);
      Object [] state = states.get(i);
      piece.setModelTransformations((Transformation [])state [0]);
      piece.setX((Float)state [1]);
      piece.setY((Float)state [2]);
      piece.setElevation((Float)state [3]);
      piece.setWidth((Float)state [4]);
      piece.setDepth((Float)state [5]);
      piece.setHeight((Float)state [6]);
      piece.setProperty(OPENING_ANGLE_PROPERTY, (String)state [7]);
    }
  }

  // ---- Materials ----

  private List<String> getMaterialNames(HomePieceOfFurniture piece) throws IOException {
    AssistantCommandExecutor.ModelInspector inspector = this.executor.getModelInspector();
    if (inspector == null) {
      throw new IOException("3D models can't be read here");
    }
    try {
      return inspector.getMaterialNames(piece.getModel());
    } catch (RuntimeException ex) {
      throw new IOException("the model of " + describe(piece) + " can't be read");
    }
  }

  private void listMaterials(AssistantCommand command, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> pieces = getPieces(command);
    if (pieces.isEmpty()) {
      throw new IOException("give the piece with \"id\"");
    }
    for (int p = 0; p < pieces.size() && p < 10; p++) {
      HomePieceOfFurniture piece = pieces.get(p);
      List<String> names = getMaterialNames(piece);
      HomeMaterial [] materials = piece.getModelMaterials();
      StringBuilder text = new StringBuilder("Materials of the model of " + describe(piece) + " (" + names.size() + "):");
      for (int i = 0; i < names.size(); i++) {
        text.append(i > 0 ? ", " : " ").append('"').append(names.get(i)).append('"');
        HomeMaterial material = find(materials, names.get(i));
        if (material != null) {
          text.append(material.getTexture() != null ? " (texture " + material.getTexture().getName() + ")"
              : material.getColor() != null ? String.format(" (#%06X)", material.getColor() & 0xFFFFFF) : " (changed)");
        }
      }
      notes.add(text.toString() + (piece.getColor() != null || piece.getTexture() != null
          ? ". The whole piece has a color or a texture, which set_material replaces" : ""));
    }
  }

  private static HomeMaterial find(HomeMaterial [] materials, String name) {
    if (materials != null) {
      for (HomeMaterial material : materials) {
        if (material != null && name.equals(material.getName())) {
          return material;
        }
      }
    }
    return null;
  }

  private void setMaterial(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> pieces = getPieces(command);
    if (pieces.isEmpty()) {
      throw new IOException("give the pieces with \"ids\" or \"where\"");
    }
    boolean reset = command.getBoolean("reset", false);
    String materialName = command.getString("material");
    if (materialName == null && !reset) {
      throw new IOException("give the name of the \"material\" (list_materials gives the names), or \"reset\":true");
    }
    Integer color = null;
    if (command.getString("color") != null) {
      color = Integer.valueOf(parse(command.getString("color"), "color") | 0xFF000000);
    }
    HomeTexture texture = command.getString("texture") != null ? getTexture(command, "texture", notes) : null;
    Float shininess = command.has("shininess")
        ? Float.valueOf((float)Math.max(0, Math.min(100, command.getDouble("shininess", 0))) / 100) : null;
    if (!reset && color == null && texture == null && shininess == null) {
      throw new IOException("give a \"color\", a \"texture\" or a \"shininess\" for the material");
    }
    final List<HomePieceOfFurniture> changed = new ArrayList<HomePieceOfFurniture>();
    final List<Object []> newStates = new ArrayList<Object []>();
    List<String> done = new ArrayList<String>();
    for (HomePieceOfFurniture piece : pieces) {
      HomeMaterial [] oldMaterials = piece.getModelMaterials();
      if (reset && materialName == null) {
        changed.add(piece);
        newStates.add(new Object [] {null, piece.getColor(), piece.getTexture()});
        done.add(describe(piece));
        continue;
      }
      List<String> names = getMaterialNames(piece);
      List<Integer> matches = new ArrayList<Integer>();
      for (int i = 0; i < names.size(); i++) {
        if (names.get(i).equalsIgnoreCase(materialName.trim())) {
          matches.clear();
          matches.add(Integer.valueOf(i));
          break;
        } else if (names.get(i).toLowerCase(Locale.ENGLISH).contains(materialName.trim().toLowerCase(Locale.ENGLISH))) {
          matches.add(Integer.valueOf(i));
        }
      }
      if (matches.isEmpty()) {
        notes.add("The model of " + describe(piece) + " has no material named \"" + materialName + "\"; its materials: " + names);
        continue;
      }
      HomeMaterial [] materials = new HomeMaterial [names.size()];
      for (int i = 0; i < materials.length; i++) {
        materials [i] = find(oldMaterials, names.get(i));
      }
      for (Integer match : matches) {
        HomeMaterial old = materials [match.intValue()];
        materials [match.intValue()] = reset ? null
            : new HomeMaterial(names.get(match.intValue()),
                texture != null ? null : color != null ? color : old != null ? old.getColor() : null,
                texture != null ? texture : color != null ? null : old != null ? old.getTexture() : null,
                shininess != null ? shininess : old != null ? old.getShininess() : null);
      }
      boolean any = false;
      for (HomeMaterial material : materials) {
        any |= material != null;
      }
      changed.add(piece);
      // The materials of a model show only without a color or a texture for the whole piece
      newStates.add(new Object [] {any ? materials : null, null, null});
      StringBuilder matched = new StringBuilder();
      for (Integer match : matches) {
        matched.append(matched.length() > 0 ? ", " : "").append('"').append(names.get(match.intValue())).append('"');
      }
      done.add(describe(piece) + " (" + matched + ")");
    }
    if (changed.isEmpty()) {
      return;
    }
    final List<Object []> oldStates = new ArrayList<Object []>();
    for (HomePieceOfFurniture piece : changed) {
      oldStates.add(new Object [] {piece.getModelMaterials(), piece.getColor(), piece.getTexture()});
    }
    Runnable redo = new Runnable() {
        public void run() {
          setMaterials(changed, newStates);
        }
      };
    redo.run();
    post("Materials", new Runnable() {
        public void run() {
          setMaterials(changed, oldStates);
        }
      }, redo);
    tally [4] += changed.size();
    StringBuilder text = new StringBuilder(reset ? "Gave back the materials of the model of " : "Changed the material of ");
    for (int i = 0; i < done.size(); i++) {
      text.append(i > 0 ? ", " : "").append(done.get(i));
    }
    notes.add(text.toString());
  }

  private static void setMaterials(List<HomePieceOfFurniture> pieces, List<Object []> states) {
    for (int i = 0; i < pieces.size(); i++) {
      Object [] state = states.get(i);
      pieces.get(i).setColor((Integer)state [1]);
      pieces.get(i).setTexture((HomeTexture)state [2]);
      pieces.get(i).setModelMaterials((HomeMaterial [])state [0]);
    }
  }
}
