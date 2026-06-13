/*
 * AssistantCommandExecutor.java 10 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEditSupport;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * Applies the edit {@link AssistantCommand commands} produced by the AI design
 * assistant to a home. All the commands of one assistant turn are grouped into a
 * single undoable edit, so a single Edit &gt; Undo reverts the whole change.
 * Coordinates are in centimeters and angles in degrees, matching the project
 * description sent to the model.
 * @author Sweet Home 3D
 */
public class AssistantCommandExecutor {
  /** Upper bound on the commands applied in one turn, so a bad reply can't make a huge mess. */
  static final int   MAX_COMMANDS_PER_TURN = 30;
  /** Coordinates beyond this magnitude (10 km in cm) are considered nonsense and rejected. */
  static final float MAX_COORDINATE_CM = 1000000f;
  /** Sizes beyond this magnitude (1 km in cm) are considered nonsense and rejected. */
  static final float MAX_SIZE_CM = 100000f;
  /** Maximum number of matches reported for one search_catalog command. */
  private static final int MAX_SEARCH_RESULTS = 8;

  private final Home            home;
  private final UserPreferences preferences;
  private final HomeController  homeController;

  public AssistantCommandExecutor(Home home, UserPreferences preferences, HomeController homeController) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
  }

  /**
   * Runs the given <code>commands</code> as a single undoable change and returns
   * a short human-readable summary, or <code>null</code> if nothing was applied.
   * The summary also carries catalog search results and skipped-command notes so
   * the agentic loop can feed them back to the model.
   */
  public String execute(List<AssistantCommand> commands) {
    if (commands == null || commands.isEmpty()) {
      return null;
    }
    // furniture, doors/windows, rooms, walls, modified items, deleted items
    int [] tally = new int [6];
    List<String> notFound = new ArrayList<String>();
    List<String> notes = new ArrayList<String>();
    UndoableEditSupport undoSupport = this.homeController.getUndoableEditSupport();
    undoSupport.beginUpdate();
    try {
      int applied = 0;
      for (AssistantCommand command : commands) {
        if (++applied > MAX_COMMANDS_PER_TURN) {
          notes.add("Stopped after " + MAX_COMMANDS_PER_TURN
              + " commands (turn limit); " + (commands.size() - MAX_COMMANDS_PER_TURN)
              + " commands were ignored");
          break;
        }
        applyCommand(command, tally, notFound, notes);
      }
    } finally {
      undoSupport.endUpdate();
    }
    return buildSummary(tally, notFound, notes);
  }

  private void applyCommand(AssistantCommand command, int [] tally, List<String> notFound,
                            List<String> notes) {
    String action = command.getAction();
    if ("search_catalog".equals(action) || "find_furniture".equals(action)) {
      searchCatalogCommand(command, notes);
    } else if ("add_furniture".equals(action)) {
      addFurniture(command, false, tally, notFound, notes);
    } else if ("add_door_or_window".equals(action) || "add_door".equals(action)
        || "add_window".equals(action)) {
      addFurniture(command, true, tally, notFound, notes);
    } else if ("add_room".equals(action)) {
      addRoom(command, tally, notes);
    } else if ("add_wall".equals(action)) {
      addWall(command, tally, notes);
    } else if ("select".equals(action)) {
      select(command);
    } else if ("move".equals(action) || "rotate".equals(action) || "resize".equals(action)
        || "set_color".equals(action) || "set_elevation".equals(action)
        || "set_visible".equals(action) || "rename".equals(action)
        || "reduce_detail".equals(action) || "restore_detail".equals(action)) {
      modify(action, command, tally, notes);
    } else if ("delete".equals(action) || "remove".equals(action)) {
      delete(command, tally);
    }
    // Unknown actions are ignored so a single bad command doesn't break a turn
  }

  /**
   * Runs a <code>search_catalog</code> command and records its matches as a note,
   * which the multi-step loop reports back to the model.
   */
  private void searchCatalogCommand(AssistantCommand command, List<String> notes) {
    String query = command.getString("query");
    if (query == null) {
      query = command.getString("name");
    }
    if (query == null || query.trim().length() == 0) {
      return;
    }
    List<CatalogMatch> matches = searchCatalog(query, false, MAX_SEARCH_RESULTS);
    StringBuilder note = new StringBuilder("Catalog search \"").append(query.trim()).append("\": ");
    if (matches.isEmpty()) {
      note.append("no matches");
    } else {
      for (int i = 0; i < matches.size(); i++) {
        if (i > 0) {
          note.append(", ");
        }
        CatalogMatch match = matches.get(i);
        note.append('"').append(match.piece.getName()).append('"')
            .append(" (").append(match.categoryName);
        if (match.piece.isDoorOrWindow()) {
          note.append(", door/window");
        }
        note.append(')');
      }
    }
    notes.add(note.toString());
  }

  /**
   * Applies a property change (move/rotate/resize/recolor/...) to the items the
   * command targets, posting a single snapshot-based undoable edit so the change
   * is reverted with the rest of the turn.
   */
  private void modify(String action, AssistantCommand command, int [] tally, List<String> notes) {
    boolean detailAction = "reduce_detail".equals(action) || "restore_detail".equals(action);
    List<Selectable> targets;
    if (detailAction && "all".equalsIgnoreCase(String.valueOf(command.getString("target")))) {
      // "all" is only honored for the reversible detail toggles
      targets = new ArrayList<Selectable>(this.home.getFurniture());
    } else {
      targets = resolveTargets(command);
    }
    if (targets.isEmpty()) {
      return;
    }
    List<String> missingReducedModels = new ArrayList<String>();
    List<ItemState> before = captureStates(targets);
    int changed = 0;
    for (Selectable item : targets) {
      if (applyModification(action, command, item, missingReducedModels)) {
        changed++;
      }
    }
    if (!missingReducedModels.isEmpty()) {
      notes.add("No reduced model exists yet for " + join(missingReducedModels)
          + "; run 3D view > Generate model LOD cache, save, then ask again");
    }
    if (changed > 0) {
      List<ItemState> after = captureStates(targets);
      this.homeController.getUndoableEditSupport().postEdit(
          new AssistantModificationUndoableEdit(before, after));
      tally [4] += changed;
    }
  }

  private boolean applyModification(String action, AssistantCommand command, Selectable item,
                                    List<String> missingReducedModels) {
    if ("reduce_detail".equals(action)) {
      if (item instanceof HomePieceOfFurniture) {
        HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
        if (piece.getModel() != null && this.home.getModelLOD(piece.getModel()) != null) {
          if (!ModelLOD.isReducedDetailInView(piece)) {
            piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, "true");
            return true;
          }
        } else {
          missingReducedModels.add(piece.getName() != null ? piece.getName() : "Unnamed piece");
        }
      }
      return false;
    } else if ("restore_detail".equals(action)) {
      if (item instanceof HomePieceOfFurniture
          && ModelLOD.isReducedDetailInView((HomePieceOfFurniture)item)) {
        ((HomePieceOfFurniture)item).setProperty(ModelLOD.LOW_POLY_PROPERTY, null);
        return true;
      }
      return false;
    } else if ("move".equals(action)) {
      return moveItem(command, item);
    } else if ("rotate".equals(action)) {
      return rotateItem(command, item);
    } else if ("resize".equals(action)) {
      return resizeItem(command, item);
    } else if ("set_color".equals(action)) {
      if (item instanceof HomePieceOfFurniture) {
        Integer color = parseColor(command.getString("color"));
        if (color != null) {
          ((HomePieceOfFurniture)item).setColor(color);
          return true;
        }
      }
    } else if ("set_elevation".equals(action)) {
      if (item instanceof HomePieceOfFurniture && command.has("elevation")) {
        ((HomePieceOfFurniture)item).setElevation((float)command.getDouble("elevation", 0));
        return true;
      }
    } else if ("set_visible".equals(action)) {
      if (item instanceof HomePieceOfFurniture && command.has("visible")) {
        ((HomePieceOfFurniture)item).setVisible(command.getBoolean("visible", true));
        return true;
      }
    } else if ("rename".equals(action)) {
      String name = command.getString("name");
      if (name != null) {
        if (item instanceof HomePieceOfFurniture) {
          ((HomePieceOfFurniture)item).setName(name);
          return true;
        } else if (item instanceof Room) {
          ((Room)item).setName(name);
          return true;
        }
      }
    }
    return false;
  }

  private boolean moveItem(AssistantCommand command, Selectable item) {
    if (command.has("dx") || command.has("dy")) {
      double dx = command.getDouble("dx", 0);
      double dy = command.getDouble("dy", 0);
      if (!coordinateSane(dx) || !coordinateSane(dy)) {
        return false;
      }
      item.move((float)dx, (float)dy);
      return true;
    } else if (item instanceof HomePieceOfFurniture && (command.has("x") || command.has("y"))) {
      HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
      double x = command.getDouble("x", piece.getX());
      double y = command.getDouble("y", piece.getY());
      if (!coordinateSane(x) || !coordinateSane(y)) {
        return false;
      }
      piece.setX((float)x);
      piece.setY((float)y);
      return true;
    }
    return false;
  }

  private boolean rotateItem(AssistantCommand command, Selectable item) {
    if (!(item instanceof HomePieceOfFurniture)) {
      return false;
    }
    HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
    if (command.has("by")) {
      piece.setAngle(piece.getAngle() + (float)Math.toRadians(command.getDouble("by", 0)));
      return true;
    } else if (command.has("angle")) {
      piece.setAngle((float)Math.toRadians(command.getDouble("angle", 0)));
      return true;
    }
    return false;
  }

  private boolean resizeItem(AssistantCommand command, Selectable item) {
    boolean changed = false;
    if (item instanceof HomePieceOfFurniture) {
      HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
      double width = command.getDouble("width", Double.NaN);
      if (!Double.isNaN(width) && width > 0 && sizeSane(width)) {
        piece.setWidth((float)width);
        changed = true;
      }
      double depth = command.getDouble("depth", Double.NaN);
      if (!Double.isNaN(depth) && depth > 0 && sizeSane(depth)) {
        piece.setDepth((float)depth);
        changed = true;
      }
      double height = command.getDouble("height", Double.NaN);
      if (!Double.isNaN(height) && height > 0 && sizeSane(height)) {
        piece.setHeight((float)height);
        changed = true;
      }
    } else if (item instanceof Wall) {
      Wall wall = (Wall)item;
      double thickness = command.getDouble("thickness", Double.NaN);
      if (!Double.isNaN(thickness) && thickness > 0 && sizeSane(thickness)) {
        wall.setThickness((float)thickness);
        changed = true;
      }
      double height = command.getDouble("height", Double.NaN);
      if (!Double.isNaN(height) && height > 0 && sizeSane(height)) {
        wall.setHeight(Float.valueOf((float)height));
        changed = true;
      }
    }
    return changed;
  }

  private void delete(AssistantCommand command, int [] tally) {
    List<Selectable> targets = resolveTargets(command);
    if (targets.isEmpty()) {
      return;
    }
    this.homeController.getPlanController().deleteItems(targets);
    tally [5] += targets.size();
  }

  /**
   * Resolves the items a command targets, by stable id (<code>F#</code>/<code>W#</code>/
   * <code>R#</code> matching {@link HomeAssistantContext}), by the literal
   * <code>selection</code> target, or by name (the <code>names</code> field).
   */
  private List<Selectable> resolveTargets(AssistantCommand command) {
    List<Selectable> targets = new ArrayList<Selectable>();
    List<String> tokens = new ArrayList<String>();
    String singleId = command.getString("id");
    if (singleId != null) {
      tokens.add(singleId);
    }
    tokens.addAll(command.getStringList("ids"));
    String target = command.getString("target");
    if (target != null) {
      tokens.add(target);
    }
    boolean selectionRequested = false;
    for (String token : tokens) {
      if (token == null) {
        continue;
      }
      String trimmed = token.trim();
      if (trimmed.equalsIgnoreCase("selection") || trimmed.equalsIgnoreCase("selected")) {
        selectionRequested = true;
      } else {
        Selectable item = resolveId(trimmed);
        if (item != null && !targets.contains(item)) {
          targets.add(item);
        }
      }
    }
    if (selectionRequested) {
      for (Selectable item : this.home.getSelectedItems()) {
        if (!targets.contains(item)) {
          targets.add(item);
        }
      }
    }
    List<String> names = command.getStringList("names");
    if (!names.isEmpty()) {
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (piece.getName() != null && containsName(names, piece.getName()) && !targets.contains(piece)) {
          targets.add(piece);
        }
      }
      for (Room room : this.home.getRooms()) {
        if (room.getName() != null && containsName(names, room.getName()) && !targets.contains(room)) {
          targets.add(room);
        }
      }
    }
    return targets;
  }

  /**
   * Resolves a single <code>F#</code>/<code>W#</code>/<code>R#</code> id to the item
   * at that 1-based index in the home's furniture, walls or rooms, or <code>null</code>.
   * The ordering matches the ids shown by {@link HomeAssistantContext}.
   */
  private Selectable resolveId(String token) {
    if (token.length() < 2) {
      return null;
    }
    char type = Character.toUpperCase(token.charAt(0));
    int index;
    try {
      index = Integer.parseInt(token.substring(1).trim()) - 1;
    } catch (NumberFormatException ex) {
      return null;
    }
    if (index < 0) {
      return null;
    }
    if (type == 'F') {
      List<HomePieceOfFurniture> furniture = this.home.getFurniture();
      if (index < furniture.size()) {
        return furniture.get(index);
      }
    } else if (type == 'W') {
      List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
      if (index < walls.size()) {
        return walls.get(index);
      }
    } else if (type == 'R') {
      List<Room> rooms = this.home.getRooms();
      if (index < rooms.size()) {
        return rooms.get(index);
      }
    }
    return null;
  }

  /**
   * Parses a color given as <code>#RRGGBB</code>, <code>0xRRGGBB</code> or a decimal
   * integer into an RGB {@link Integer}, or <code>null</code> if it can't be parsed.
   */
  static Integer parseColor(String text) {
    if (text == null) {
      return null;
    }
    String value = text.trim();
    int radix = 10;
    if (value.startsWith("#")) {
      value = value.substring(1);
      radix = 16;
    } else if (value.startsWith("0x") || value.startsWith("0X")) {
      value = value.substring(2);
      radix = 16;
    }
    try {
      return Integer.valueOf((int)(Long.parseLong(value, radix) & 0xFFFFFF));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static List<ItemState> captureStates(List<Selectable> items) {
    List<ItemState> states = new ArrayList<ItemState>(items.size());
    for (Selectable item : items) {
      states.add(ItemState.capture(item));
    }
    return states;
  }

  private void addFurniture(AssistantCommand command, boolean doorOrWindow,
                            int [] tally, List<String> notFound, List<String> notes) {
    String name = command.getString("name");
    if (name == null || name.trim().length() == 0) {
      return;
    }
    CatalogPieceOfFurniture catalogPiece = findCatalogPiece(name, doorOrWindow);
    if (catalogPiece == null) {
      notFound.add(describeUnmatchedName(name));
      return;
    }
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    double width = command.getDouble("width", Double.NaN);
    double depth = command.getDouble("depth", Double.NaN);
    double elevation = command.getDouble("elevation", Double.NaN);
    if (!coordinateSane(x) || !coordinateSane(y)
        || !sizeSane(width) || !sizeSane(depth) || !sizeSane(elevation)) {
      notes.add("Ignored add_furniture \"" + name + "\" with out-of-range values");
      return;
    }
    HomePieceOfFurniture piece =
        this.homeController.getFurnitureController().createHomePieceOfFurniture(catalogPiece);
    if (!Double.isNaN(x)) {
      piece.setX((float)x);
    }
    if (!Double.isNaN(y)) {
      piece.setY((float)y);
    }
    piece.setAngle((float)Math.toRadians(command.getDouble("angle", 0)));
    if (!Double.isNaN(width) && width > 0) {
      piece.setWidth((float)width);
    }
    if (!Double.isNaN(depth) && depth > 0) {
      piece.setDepth((float)depth);
    }
    if (!Double.isNaN(elevation)) {
      piece.setElevation((float)elevation);
    }
    piece.setLevel(this.home.getSelectedLevel());
    this.homeController.getFurnitureController().addFurniture(Collections.singletonList(piece));
    if (doorOrWindow || catalogPiece.isDoorOrWindow()) {
      tally [1]++;
    } else {
      tally [0]++;
    }
  }

  /**
   * Describes a furniture name that didn't match the catalog, with the closest
   * catalog names so the model can retry with a real one.
   */
  private String describeUnmatchedName(String name) {
    List<CatalogMatch> closest = searchCatalog(name, false, 3);
    if (closest.isEmpty()) {
      return name;
    }
    StringBuilder description = new StringBuilder(name).append(" (closest catalog names: ");
    for (int i = 0; i < closest.size(); i++) {
      if (i > 0) {
        description.append(", ");
      }
      description.append('\'').append(closest.get(i).piece.getName()).append('\'');
    }
    return description.append(')').toString();
  }

  private void addRoom(AssistantCommand command, int [] tally, List<String> notes) {
    List<float []> points = command.getPoints("points");
    if (points.size() < 3) {
      return;
    }
    for (float [] point : points) {
      if (!coordinateSane(point [0]) || !coordinateSane(point [1])) {
        notes.add("Ignored add_room with out-of-range points");
        return;
      }
    }
    Room room = new Room(points.toArray(new float [points.size()][]));
    String name = command.getString("name");
    if (name != null && name.trim().length() > 0) {
      room.setName(name);
    }
    room.setLevel(this.home.getSelectedLevel());
    this.homeController.getPlanController().addRooms(Collections.singletonList(room));
    tally [2]++;
  }

  private void addWall(AssistantCommand command, int [] tally, List<String> notes) {
    float xStart = (float)command.getDouble("x1", Double.NaN);
    float yStart = (float)command.getDouble("y1", Double.NaN);
    float xEnd = (float)command.getDouble("x2", Double.NaN);
    float yEnd = (float)command.getDouble("y2", Double.NaN);
    if (Float.isNaN(xStart) || Float.isNaN(yStart) || Float.isNaN(xEnd) || Float.isNaN(yEnd)) {
      return;
    }
    if (!coordinateSane(xStart) || !coordinateSane(yStart)
        || !coordinateSane(xEnd) || !coordinateSane(yEnd)) {
      notes.add("Ignored add_wall with out-of-range coordinates");
      return;
    }
    float thickness = (float)command.getDouble("thickness", this.preferences.getNewWallThickness());
    float height = (float)command.getDouble("height", this.preferences.getNewWallHeight());
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
    wall.setLevel(this.home.getSelectedLevel());
    this.homeController.getPlanController().addWalls(Collections.singletonList(wall));
    tally [3]++;
  }

  /** Returns <code>true</code> if the value is absent (NaN) or a plausible plan coordinate. */
  private static boolean coordinateSane(double value) {
    return Double.isNaN(value) || Math.abs(value) <= MAX_COORDINATE_CM;
  }

  /** Returns <code>true</code> if the value is absent (NaN) or a plausible size/elevation. */
  private static boolean sizeSane(double value) {
    return Double.isNaN(value) || Math.abs(value) <= MAX_SIZE_CM;
  }

  private void select(AssistantCommand command) {
    List<String> names = command.getStringList("names");
    if (names.isEmpty()) {
      return;
    }
    List<Selectable> selection = new ArrayList<Selectable>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getName() != null && containsName(names, piece.getName())) {
        selection.add(piece);
      }
    }
    for (Room room : this.home.getRooms()) {
      if (room.getName() != null && containsName(names, room.getName())) {
        selection.add(room);
      }
    }
    if (!selection.isEmpty()) {
      this.home.setSelectedItems(selection);
    }
  }

  private static boolean containsName(List<String> names, String candidate) {
    for (String name : names) {
      if (name.equalsIgnoreCase(candidate)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the catalog piece whose name best matches <code>query</code>,
   * optionally restricted to doors and windows, or <code>null</code> if nothing
   * matches at all.
   */
  public CatalogPieceOfFurniture findCatalogPiece(String query, boolean doorOrWindow) {
    List<CatalogMatch> matches = searchCatalog(query, doorOrWindow, 1);
    return matches.isEmpty() ? null : matches.get(0).piece;
  }

  /**
   * Searches the furniture catalog for pieces matching <code>query</code>,
   * best match first. Matching is scored: an exact name beats a name containing
   * every query word, which beats substring and partial word matches; plural
   * "s" suffixes are ignored when comparing words.
   */
  public List<CatalogMatch> searchCatalog(String query, boolean doorOrWindow, int maxResults) {
    List<CatalogMatch> matches = new ArrayList<CatalogMatch>();
    if (query == null || this.preferences.getFurnitureCatalog() == null) {
      return matches;
    }
    String normalizedQuery = query.trim().toLowerCase();
    List<String> queryTokens = tokenize(normalizedQuery);
    for (FurnitureCategory category : this.preferences.getFurnitureCatalog().getCategories()) {
      for (CatalogPieceOfFurniture piece : category.getFurniture()) {
        if (doorOrWindow && !piece.isDoorOrWindow()) {
          continue;
        }
        String name = piece.getName();
        if (name == null) {
          continue;
        }
        int score = scoreMatch(name.toLowerCase(), normalizedQuery, queryTokens);
        if (score > 0) {
          matches.add(new CatalogMatch(piece, category.getName(), score));
        }
      }
    }
    Collections.sort(matches);
    if (matches.size() > maxResults) {
      return new ArrayList<CatalogMatch>(matches.subList(0, maxResults));
    }
    return matches;
  }

  /**
   * Scores how well a catalog piece <code>name</code> matches a query, both
   * already lower-case; 0 means no match. Word comparisons ignore a plural "s".
   */
  static int scoreMatch(String name, String query, List<String> queryTokens) {
    if (name.equals(query)) {
      return 1000;
    }
    List<String> nameTokens = tokenize(name);
    int matched = 0;
    for (String queryToken : queryTokens) {
      for (String nameToken : nameTokens) {
        if (tokensEqual(queryToken, nameToken)) {
          matched++;
          break;
        }
      }
    }
    int score;
    if (!queryTokens.isEmpty() && matched == queryTokens.size()) {
      // Every query word appears in the name ("table" matches "Round table")
      score = 500;
    } else if (name.contains(query)) {
      score = 400;
    } else if (query.contains(name)) {
      score = 350;
    } else if (matched > 0) {
      score = 100 + (200 * matched) / queryTokens.size();
    } else {
      return 0;
    }
    // Prefer concise names: "Door" beats "Sliding door" for the query "door"
    return score - Math.min(nameTokens.size(), 50);
  }

  private static boolean tokensEqual(String token1, String token2) {
    return token1.equals(token2)
        || singular(token1).equals(singular(token2));
  }

  private static String singular(String token) {
    return token.length() > 3 && token.endsWith("s")
        ? token.substring(0, token.length() - 1) : token;
  }

  /** Splits lower-case text into alphanumeric word tokens. */
  static List<String> tokenize(String text) {
    List<String> tokens = new ArrayList<String>();
    int start = -1;
    for (int i = 0; i <= text.length(); i++) {
      boolean wordChar = i < text.length() && Character.isLetterOrDigit(text.charAt(i));
      if (wordChar && start < 0) {
        start = i;
      } else if (!wordChar && start >= 0) {
        tokens.add(text.substring(start, i));
        start = -1;
      }
    }
    return tokens;
  }

  /**
   * A scored catalog search result, ordered best match first.
   */
  public static final class CatalogMatch implements Comparable<CatalogMatch> {
    final CatalogPieceOfFurniture piece;
    final String                  categoryName;
    final int                     score;

    CatalogMatch(CatalogPieceOfFurniture piece, String categoryName, int score) {
      this.piece = piece;
      this.categoryName = categoryName;
      this.score = score;
    }

    public int compareTo(CatalogMatch other) {
      if (this.score != other.score) {
        return other.score - this.score;
      }
      // Deterministic order between equal scores
      return this.piece.getName().compareTo(other.piece.getName());
    }
  }

  private String buildSummary(int [] tally, List<String> notFound, List<String> notes) {
    List<String> parts = new ArrayList<String>();
    addPart(parts, tally [0], "piece of furniture", "pieces of furniture");
    addPart(parts, tally [1], "door/window", "doors/windows");
    addPart(parts, tally [2], "room", "rooms");
    addPart(parts, tally [3], "wall", "walls");
    List<String> clauses = new ArrayList<String>();
    if (!parts.isEmpty()) {
      StringBuilder added = new StringBuilder("added ");
      for (int i = 0; i < parts.size(); i++) {
        if (i > 0) {
          added.append(i == parts.size() - 1 ? " and " : ", ");
        }
        added.append(parts.get(i));
      }
      clauses.add(added.toString());
    }
    if (tally [4] == 1) {
      clauses.add("modified 1 item");
    } else if (tally [4] > 1) {
      clauses.add("modified " + tally [4] + " items");
    }
    if (tally [5] == 1) {
      clauses.add("deleted 1 item");
    } else if (tally [5] > 1) {
      clauses.add("deleted " + tally [5] + " items");
    }
    StringBuilder summary = new StringBuilder();
    if (!clauses.isEmpty()) {
      summary.append("Applied changes: ");
      for (int i = 0; i < clauses.size(); i++) {
        if (i > 0) {
          summary.append(i == clauses.size() - 1 ? " and " : ", ");
        }
        summary.append(clauses.get(i));
      }
      summary.append(". Use Edit > Undo to revert.");
    }
    if (!notFound.isEmpty()) {
      if (summary.length() > 0) {
        summary.append('\n');
      }
      summary.append("Couldn't find furniture named: ").append(join(notFound)).append('.');
    }
    for (String note : notes) {
      if (summary.length() > 0) {
        summary.append('\n');
      }
      summary.append(note).append('.');
    }
    return summary.length() > 0 ? summary.toString() : null;
  }

  private static void addPart(List<String> parts, int count, String singular, String plural) {
    if (count == 1) {
      parts.add("1 " + singular);
    } else if (count > 1) {
      parts.add(count + " " + plural);
    }
  }

  private static String join(List<String> values) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append('"').append(values.get(i)).append('"');
    }
    return builder.toString();
  }

  /**
   * A snapshot of the mutable geometry and appearance of a single home item,
   * used to undo and redo the property changes the assistant makes.
   */
  private static final class ItemState {
    private final Selectable item;
    // Furniture
    private float     x, y, angle, width, depth, height, elevation;
    private Integer   color;
    private boolean   visible;
    // Wall
    private float     xStart, yStart, xEnd, yEnd, thickness;
    private Float     wallHeight;
    // Room
    private float [][] points;
    // Furniture or room
    private String    name;
    // Furniture reduced-detail flag (ModelLOD.LOW_POLY_PROPERTY)
    private String    lowPoly;

    private ItemState(Selectable item) {
      this.item = item;
    }

    static ItemState capture(Selectable item) {
      ItemState state = new ItemState(item);
      if (item instanceof HomePieceOfFurniture) {
        HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
        state.x = piece.getX();
        state.y = piece.getY();
        state.angle = piece.getAngle();
        state.width = piece.getWidth();
        state.depth = piece.getDepth();
        state.height = piece.getHeight();
        state.elevation = piece.getElevation();
        state.color = piece.getColor();
        state.visible = piece.isVisible();
        state.name = piece.getName();
        state.lowPoly = piece.getProperty(ModelLOD.LOW_POLY_PROPERTY);
      } else if (item instanceof Wall) {
        Wall wall = (Wall)item;
        state.xStart = wall.getXStart();
        state.yStart = wall.getYStart();
        state.xEnd = wall.getXEnd();
        state.yEnd = wall.getYEnd();
        state.thickness = wall.getThickness();
        state.wallHeight = wall.getHeight();
      } else if (item instanceof Room) {
        Room room = (Room)item;
        state.name = room.getName();
        state.points = room.getPoints();
      }
      return state;
    }

    void restore() {
      if (this.item instanceof HomePieceOfFurniture) {
        HomePieceOfFurniture piece = (HomePieceOfFurniture)this.item;
        piece.setX(this.x);
        piece.setY(this.y);
        piece.setAngle(this.angle);
        piece.setWidth(this.width);
        piece.setDepth(this.depth);
        piece.setHeight(this.height);
        piece.setElevation(this.elevation);
        piece.setColor(this.color);
        piece.setVisible(this.visible);
        piece.setName(this.name);
        piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, this.lowPoly);
      } else if (this.item instanceof Wall) {
        Wall wall = (Wall)this.item;
        wall.setXStart(this.xStart);
        wall.setYStart(this.yStart);
        wall.setXEnd(this.xEnd);
        wall.setYEnd(this.yEnd);
        wall.setThickness(this.thickness);
        wall.setHeight(this.wallHeight);
      } else if (this.item instanceof Room) {
        Room room = (Room)this.item;
        room.setName(this.name);
        if (this.points != null) {
          room.setPoints(this.points);
        }
      }
    }
  }

  /**
   * Reverts or replays the assistant's property changes by restoring the
   * before/after {@link ItemState snapshots} captured around them.
   */
  private static final class AssistantModificationUndoableEdit extends AbstractUndoableEdit {
    private final List<ItemState> before;
    private final List<ItemState> after;

    AssistantModificationUndoableEdit(List<ItemState> before, List<ItemState> after) {
      this.before = before;
      this.after = after;
    }

    @Override
    public void undo() {
      super.undo();
      for (ItemState state : this.before) {
        state.restore();
      }
    }

    @Override
    public void redo() {
      super.redo();
      for (ItemState state : this.after) {
        state.restore();
      }
    }

    @Override
    public String getPresentationName() {
      return "AI assistant edit";
    }
  }
}
