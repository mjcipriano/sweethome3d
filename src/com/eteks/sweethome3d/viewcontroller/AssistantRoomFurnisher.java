/*
 * AssistantRoomFurnisher.java 21 nov. 2026
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

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * Chooses the place of the furniture of a room by rules: pieces go against the free parts of
 * the walls or at a place computed from another piece, never over another piece, over the zone
 * a door needs to open, in front of a window if they are tall, or where they would close the
 * passage from a door to the rest of the room. The same room always gets the same layout.
 * @author Sweet Home 3D
 */
final class AssistantRoomFurnisher {
  /**
   * Finds the pieces of the catalog.
   */
  interface Catalog {
    /**
     * Returns the piece that matches the first of the <code>queries</code> that matches one,
     * or <code>null</code>.
     */
    CatalogPieceOfFurniture find(String [] queries);
  }

  /**
   * A piece and the place chosen for it.
   */
  static final class Placement {
    final CatalogPieceOfFurniture piece;
    final String                  role;
    final float                   x;
    final float                   y;
    /** The angle in radians: at 0 the front of a piece faces +y. */
    final float                   angle;
    final String                  where;
    final float [][]              points;

    Placement(CatalogPieceOfFurniture piece, String role, float x, float y, float angle, String where, float [][] points) {
      this.piece = piece;
      this.role = role;
      this.x = x;
      this.y = y;
      this.angle = angle;
      this.where = where;
      this.points = points;
    }
  }

  static final String [] TYPES = {"bedroom", "living_room", "dining_room", "kitchen", "office", "bathroom", "kids_room", "entry"};

  /** Pieces taller than this don't go in front of a window, in cm. */
  static final float  TALL = 100;
  /** The width of the passage kept from the doors, in cm. */
  static final float  PASSAGE = 60;
  /** The gap left between a piece and the wall or the piece beside it, in cm. */
  private static final float GAP = 1;
  /** The radius of what must go through the passages. A little under half of the passage, for the size of the cells. */
  private static final float RADIUS = PASSAGE / 2 - 3;

  private static final int ACCESS_NONE = 0;
  private static final int ACCESS_FRONT = 1;
  private static final int ACCESS_ANY = 2;
  private static final int ACCESS_BACK_OR_SIDES = 3;

  private static final int ALIGN_CENTER = 0;
  private static final int ALIGN_CORNER = 1;
  private static final int ALIGN_NEAR = 2;

  private static final int KIND_PIECE = 0;
  private static final int KIND_DOOR = 1;
  private static final int KIND_WINDOW = 2;

  /**
   * A side of the room.
   */
  private static final class Edge {
    float x;
    float y;
    /** Its direction. */
    float tx;
    float ty;
    /** Its normal, toward the inside of the room. */
    float nx;
    float ny;
    float length;
    /** How far the wall along it comes inside the room. */
    float inset;
    boolean door;
  }

  /**
   * A door or a window along a side.
   */
  private static final class Opening {
    int     edge;
    float   start;
    float   end;
    boolean door;
  }

  private static final class Obstacle {
    final float [][] points;
    final int        kind;

    Obstacle(float [][] points, int kind) {
      this.points = points;
      this.kind = kind;
    }
  }

  /**
   * What is looked for in the catalog for a role.
   */
  private static final class Item {
    final String                  role;
    final CatalogPieceOfFurniture piece;
    final float                   width;
    final float                   depth;
    final float                   height;

    Item(String role, CatalogPieceOfFurniture piece) {
      this.role = role;
      this.piece = piece;
      this.width = piece.getWidth();
      this.depth = piece.getDepth();
      this.height = piece.getHeight();
    }
  }

  /**
   * How a piece is put against a wall.
   */
  private static final class WallRule {
    int       align = ALIGN_CENTER;
    /** The point the piece comes as close as possible to. */
    float []  near;
    /** The direction the front of the piece must face. */
    float []  facing;
    /** 1 to prefer the place under a window, -1 to prefer a wall without window. */
    int       window;
    /** The length kept free on each side, if a wall is long enough. */
    float     reserve;
    boolean   avoidDoorWall;
    /** A side to avoid if another one suits. */
    int       avoidEdge = -1;
    int       access = ACCESS_FRONT;
  }

  private static final class Candidate {
    int   edge;
    float t;
    float score;
    int   order;
  }

  private final Catalog          catalog;
  private final int              maxItems;
  private final float [][]       polygon;
  private final Edge []          edges;
  private final Area             interior;
  private final List<float [][]> wallPolygons = new ArrayList<float [][]>();
  private final List<Obstacle>   obstacles = new ArrayList<Obstacle>();
  private final List<Opening>    openings = new ArrayList<Opening>();
  private final List<Placement>  placements = new ArrayList<Placement>();
  private final List<String>     skipped = new ArrayList<String>();
  private final float            roomArea;
  private int                    doorCount;
  private int                    windowCount;
  // The cells where the center of what walks through the room may be
  private float                  cellSize;
  private float                  gridX;
  private float                  gridY;
  private int                    gridWidth;
  private int                    gridHeight;
  private boolean []             freeCells;
  private boolean []             blockedCells;
  // The places that must stay joined: each one is a list of points, one of which is enough
  private final List<float [][]> requirements = new ArrayList<float [][]>();
  private int                    candidateOrder;

  /**
   * @param ignoredFurniture the furniture of the home that is supposed removed
   */
  AssistantRoomFurnisher(Home home, Room room, List<HomePieceOfFurniture> ignoredFurniture, Catalog catalog, int maxItems) {
    this.catalog = catalog;
    this.maxItems = maxItems;
    this.polygon = clean(room.getPoints());
    Area roomShape = getArea(this.polygon);
    // The walls
    Area wallsArea = new Area();
    for (Wall wall : home.getWalls()) {
      if (wall.isAtLevel(room.getLevel())) {
        float [][] points = wall.getPoints();
        if (points.length >= 3) {
          this.wallPolygons.add(points);
          wallsArea.add(getArea(points));
        }
      }
    }
    this.interior = new Area(roomShape);
    this.interior.subtract(wallsArea);
    this.roomArea = getSurface(this.interior);
    // The sides
    this.edges = new Edge [this.polygon.length];
    for (int i = 0; i < this.polygon.length; i++) {
      float [] point = this.polygon [i];
      float [] next = this.polygon [(i + 1) % this.polygon.length];
      Edge edge = new Edge();
      edge.x = point [0];
      edge.y = point [1];
      edge.length = (float)Math.hypot(next [0] - point [0], next [1] - point [1]);
      edge.tx = (next [0] - point [0]) / edge.length;
      edge.ty = (next [1] - point [1]) / edge.length;
      edge.nx = -edge.ty;
      edge.ny = edge.tx;
      float middleX = (point [0] + next [0]) / 2;
      float middleY = (point [1] + next [1]) / 2;
      if (!contains(this.polygon, middleX + edge.nx, middleY + edge.ny)) {
        edge.nx = -edge.nx;
        edge.ny = -edge.ny;
      }
      // The inside face of the wall along it
      for (float [][] wall : this.wallPolygons) {
        float [] range = getRange(edge, wall);
        float wallLength = 0;
        for (int j = 0; j < wall.length; j++) {
          float [] wallNext = wall [(j + 1) % wall.length];
          wallLength = Math.max(wallLength, Math.abs((wallNext [0] - wall [j][0]) * edge.tx + (wallNext [1] - wall [j][1]) * edge.ty));
        }
        // A wall along the side, not one that ends at it
        if (range [2] < 1 && range [3] > edge.inset && range [3] <= 60 && range [3] - range [2] <= 80
            && Math.min(range [1], edge.length) - Math.max(range [0], 0) > 10
            && wallLength >= (range [1] - range [0]) * 0.8f) {
          edge.inset = range [3];
        }
      }
      this.edges [i] = edge;
    }
    // The furniture already there, the doors and the windows
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (!piece.isAtLevel(room.getLevel()) || !piece.isVisible() || ignoredFurniture.contains(piece)) {
        continue;
      }
      if (piece.isDoorOrWindow()) {
        addOpening(piece);
      } else if (piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) == null && piece.getElevation() < 130
                 && isInRoom(piece)) {
        this.obstacles.add(new Obstacle(piece.getPoints(), KIND_PIECE));
      }
    }
    createGrid();
  }

  // ---- Results ----

  List<Placement> getPlacements() {
    return this.placements;
  }

  /**
   * Returns what wasn't placed, each with the reason.
   */
  List<String> getSkipped() {
    return this.skipped;
  }

  int getDoorCount() {
    return this.doorCount;
  }

  int getWindowCount() {
    return this.windowCount;
  }

  /**
   * Returns the area of the floor between the walls, in cm2.
   */
  float getRoomArea() {
    return this.roomArea;
  }

  /**
   * Returns the area of the floor no piece stands on, in cm2.
   */
  float getFreeArea() {
    Area free = new Area(this.interior);
    for (Obstacle obstacle : this.obstacles) {
      if (obstacle.kind == KIND_PIECE) {
        free.subtract(getArea(obstacle.points));
      }
    }
    return getSurface(free);
  }

  /**
   * Returns the type of room a name tells, or <code>null</code>.
   */
  static String guessType(String name) {
    if (name == null) {
      return null;
    }
    String text = name.toLowerCase(java.util.Locale.ENGLISH);
    if (text.contains("kid") || text.contains("child") || text.contains("nursery") || text.contains("boy") || text.contains("girl")) {
      return "kids_room";
    } else if (text.contains("bath") || text.contains("shower") || text.contains("toilet") || text.contains("wc")
               || text.contains("restroom") || text.contains("powder")) {
      return "bathroom";
    } else if (text.contains("bed") || text.contains("guest") || text.contains("master")) {
      return "bedroom";
    } else if (text.contains("dining")) {
      return "dining_room";
    } else if (text.contains("kitchen")) {
      return "kitchen";
    } else if (text.contains("living") || text.contains("lounge") || text.contains("family") || text.contains("sitting")
               || text.contains("great room") || text.contains("den")) {
      return "living_room";
    } else if (text.contains("office") || text.contains("study") || text.contains("work")) {
      return "office";
    } else if (text.contains("entry") || text.contains("entrance") || text.contains("hall") || text.contains("foyer")
               || text.contains("lobby") || text.contains("mudroom")) {
      return "entry";
    }
    return null;
  }

  // ---- Layouts ----

  /**
   * Places the furniture of a room of the given type.
   */
  void furnish(String type) {
    if ("bedroom".equals(type)) {
      furnishBedroom(false);
    } else if ("kids_room".equals(type)) {
      furnishBedroom(true);
    } else if ("living_room".equals(type)) {
      furnishLivingRoom();
    } else if ("dining_room".equals(type)) {
      placeTable("dining table", 8);
      WallRule rule = new WallRule();
      rule.window = -1;
      placeOnWall(find("sideboard", "sideboard", "dresser", "chest", "glass door cabinet"), rule);
      placeInCorner(find("plant", "plant"));
    } else if ("kitchen".equals(type)) {
      furnishKitchen();
    } else if ("office".equals(type)) {
      placeDesk();
      WallRule rule = new WallRule();
      rule.window = -1;
      placeOnWall(find("bookcase", "bookcase", "filled bookcase"), rule);
      placeInCorner(find("armchair", "armchair"));
      placeInCorner(find("plant", "plant"));
    } else if ("bathroom".equals(type)) {
      furnishBathroom();
    } else if ("entry".equals(type)) {
      WallRule rule = new WallRule();
      rule.window = -1;
      Placement wardrobe = placeOnWall(find("wardrobe", "wardrobe", "coat rack"), rule);
      rule = new WallRule();
      rule.avoidEdge = wardrobe != null ? getEdge(wardrobe) : -1;
      placeOnWall(find("console", "console", "chest", "dresser"), rule);
      placeInCorner(find("seat", "stool", "bench", "chair"));
      placeInCorner(find("plant", "plant"));
    }
  }

  private void furnishBedroom(boolean kids) {
    Item bed = kids ? find("bed", "bed 90x190", "single bed", "bunk bed", "bed")
        : find("bed", "bed", "double bed", "bed 140x190");
    Item nightstand = find(kids ? "bedside table" : "bedside tables", "bedside table", "nightstand");
    Placement bedPlacement = null;
    if (bed != null) {
      WallRule rule = new WallRule();
      rule.window = -1;
      rule.avoidDoorWall = true;
      rule.access = ACCESS_ANY;
      if (kids) {
        // Along a corner, which leaves the middle of the room free
        rule.align = ALIGN_CORNER;
      } else if (nightstand != null) {
        rule.reserve = nightstand.width + 2 * GAP;
      }
      bedPlacement = placeOnWall(bed, rule);
    }
    if (bedPlacement != null && nightstand != null) {
      int count = 0;
      int wanted = kids ? 1 : 2;
      for (int side = -1; side <= 1 && count < wanted; side += 2) {
        if (placeBeside(nightstand, bedPlacement, side)) {
          count++;
        }
      }
      if (count < wanted && !isFull()) {
        this.skipped.add(count == 0 ? "bedside table: no room beside the bed"
            : "second bedside table: no room on the other side of the bed");
      }
    }
    if (kids) {
      placeDesk();
    }
    WallRule rule = new WallRule();
    rule.window = -1;
    rule.avoidEdge = bedPlacement != null ? getEdge(bedPlacement) : -1;
    placeOnWall(find("wardrobe", "wardrobe"), rule);
    rule = new WallRule();
    rule.avoidEdge = bedPlacement != null ? getEdge(bedPlacement) : -1;
    if (kids) {
      rule.window = -1;
      placeOnWall(find("bookcase", "bookcase", "filled bookcase"), rule);
    } else {
      placeOnWall(find("dresser", "dresser", "chest"), rule);
    }
  }

  private void furnishLivingRoom() {
    Item sofa = find("sofa", "sofa", "couch", "corner sofa");
    Placement sofaPlacement = null;
    if (sofa != null) {
      WallRule rule = new WallRule();
      rule.avoidDoorWall = true;
      // Reached from its front or along the table that will stand in front of it
      rule.access = ACCESS_ANY;
      sofaPlacement = placeOnWall(sofa, rule);
    }
    Item unit = find("TV unit", "tv unit", "tv stand", "tv cabinet", "media unit");
    Placement unitPlacement = null;
    if (unit != null) {
      if (sofaPlacement != null) {
        // On the opposite wall, in front of the sofa
        float [] front = getFront(sofaPlacement.angle);
        WallRule rule = new WallRule();
        rule.align = ALIGN_NEAR;
        rule.near = new float [] {sofaPlacement.x + front [0] * 5000, sofaPlacement.y + front [1] * 5000};
        rule.facing = new float [] {-front [0], -front [1]};
        rule.access = ACCESS_NONE;
        unitPlacement = placeOnWall(unit, rule, false);
        if (unitPlacement == null && !isFull()) {
          this.skipped.add("TV unit: no free place on the wall that faces the sofa");
        }
      } else {
        WallRule rule = new WallRule();
        rule.window = -1;
        unitPlacement = placeOnWall(unit, rule);
      }
    }
    Item table = find("coffee table", "coffee table", "low table");
    Placement tablePlacement = null;
    if (table != null && sofaPlacement != null && !isFull()) {
      float [] front = getFront(sofaPlacement.angle);
      float distance = sofa.depth / 2 + 40 + table.depth / 2;
      float [][] points = getRectangle(sofaPlacement.x + front [0] * distance, sofaPlacement.y + front [1] * distance,
          table.width, table.depth, sofaPlacement.angle);
      // People still walk between the table and what faces the sofa
      float [][] walk = getRectangle(sofaPlacement.x + front [0] * (distance + table.depth / 2 + PASSAGE / 2),
          sofaPlacement.y + front [1] * (distance + table.depth / 2 + PASSAGE / 2), table.width, PASSAGE - 2, sofaPlacement.angle);
      if (isClear(walk, 0) && fits(points, table.height, ACCESS_NONE)) {
        tablePlacement = add(table, points, sofaPlacement.x + front [0] * distance, sofaPlacement.y + front [1] * distance,
            sofaPlacement.angle, "in front of the sofa", ACCESS_NONE);
      } else {
        this.skipped.add("coffee table: not enough room in front of the sofa");
      }
    }
    Item armchair = find("armchair", "armchair");
    if (armchair != null && sofaPlacement != null && !isFull()) {
      // At a right angle of the sofa, turned to the table
      float [] front = getFront(sofaPlacement.angle);
      float [] side = {(float)Math.cos(sofaPlacement.angle), (float)Math.sin(sofaPlacement.angle)};
      float along = sofa.depth / 2 + 40 + (table != null ? table.depth : 60) / 2;
      float across = Math.max(sofa.width, tablePlacement != null ? table.width : 0) / 2 + 30 + armchair.depth / 2;
      boolean placed = false;
      for (int direction = 1; direction >= -1 && !placed; direction -= 2) {
        float x = sofaPlacement.x + front [0] * along + side [0] * across * direction;
        float y = sofaPlacement.y + front [1] * along + side [1] * across * direction;
        // Its front faces the table
        float angle = getAngle(-side [0] * direction, -side [1] * direction);
        float [][] points = getRectangle(x, y, armchair.width, armchair.depth, angle);
        if (fits(points, armchair.height, ACCESS_ANY)) {
          add(armchair, points, x, y, angle, "beside the sofa, turned to the table", ACCESS_ANY);
          placed = true;
        }
      }
      if (!placed) {
        this.skipped.add("armchair: no room beside the sofa");
      }
    }
    WallRule rule = new WallRule();
    rule.window = -1;
    rule.avoidEdge = unitPlacement != null ? getEdge(unitPlacement) : -1;
    placeOnWall(find("bookcase", "bookcase", "filled bookcase"), rule);
    placeInCorner(find("plant", "plant"));
  }

  private void furnishKitchen() {
    // A run of units along the walls, from a corner
    String [][] units = {{"fridge", "fridge & freezer", "fridge", "refrigerator"}, {"kitchen cabinet", "kitchen cabinet", "base cabinet"},
        {"cooker", "cooker", "oven", "stove"}, {"kitchen cabinet", "kitchen cabinet", "base cabinet"}, {"sink", "sink"},
        {"dishwasher", "dishwasher"}, {"kitchen cabinet", "kitchen cabinet", "base cabinet"}};
    Placement previous = null;
    for (String [] unit : units) {
      String [] queries = new String [unit.length - 1];
      System.arraycopy(unit, 1, queries, 0, queries.length);
      Item item = find(unit [0], queries);
      if (item == null) {
        continue;
      }
      WallRule rule = new WallRule();
      if (previous == null) {
        rule.align = ALIGN_CORNER;
        rule.window = -1;
        rule.avoidDoorWall = true;
      } else {
        // Beside the previous unit
        rule.align = ALIGN_NEAR;
        rule.near = new float [] {previous.x, previous.y};
      }
      Placement placement = placeOnWall(item, rule);
      if (placement != null) {
        previous = placement;
      }
    }
    if (this.roomArea >= 120000) {
      placeTable("table", 4);
    }
  }

  private void furnishBathroom() {
    WallRule rule = new WallRule();
    rule.align = ALIGN_CORNER;
    rule.avoidDoorWall = true;
    Placement bath = placeOnWall(find("bath", "bath", "fitted bath", "bathtub"), rule);
    if (bath == null) {
      rule = new WallRule();
      rule.align = ALIGN_CORNER;
      rule.avoidDoorWall = true;
      rule.window = -1;
      bath = placeOnWall(find("shower", "shower"), rule);
    }
    rule = new WallRule();
    rule.avoidEdge = bath != null ? getEdge(bath) : -1;
    Placement basin = placeOnWall(find("washbasin", "washbasin with cabinet", "washbasin", "sink"), rule);
    rule = new WallRule();
    rule.align = ALIGN_CORNER;
    rule.avoidDoorWall = true;
    rule.avoidEdge = basin != null ? getEdge(basin) : -1;
    placeOnWall(find("toilet", "toilet unit", "toilet", "wc"), rule);
    if (this.roomArea >= 70000) {
      rule = new WallRule();
      rule.align = ALIGN_CORNER;
      placeOnWall(find("clothes washer", "clothes washer", "washing machine"), rule);
    }
  }

  /**
   * Places a desk, under a window if one has a free wall under it, and its chair.
   */
  private void placeDesk() {
    Item desk = find("desk", "desk");
    if (desk == null) {
      return;
    }
    WallRule rule = new WallRule();
    rule.window = 1;
    rule.access = ACCESS_FRONT;
    Placement placement = placeOnWall(desk, rule);
    Item chair = find("desk chair", "office chair", "desk chair", "chair");
    if (placement != null && chair != null && !isFull()) {
      // In front of the desk and turned to it
      float [] front = getFront(placement.angle);
      float distance = desk.depth / 2 + 5 + chair.depth / 2;
      float x = placement.x + front [0] * distance;
      float y = placement.y + front [1] * distance;
      float angle = getAngle(-front [0], -front [1]);
      float [][] points = getRectangle(x, y, chair.width, chair.depth, angle);
      if (fits(points, chair.height, ACCESS_BACK_OR_SIDES, this.requirements.size() - 1)) {
        // The chair is how the desk is reached
        this.requirements.remove(this.requirements.size() - 1);
        add(chair, points, x, y, angle, "at the desk", ACCESS_BACK_OR_SIDES);
      } else {
        this.skipped.add("desk chair: no room in front of the desk");
      }
    }
  }

  /**
   * Places a table in the middle of the free floor, with chairs around it turned to it.
   */
  private void placeTable(String role, int maxChairs) {
    Item table = find(role, "table", "dining table", "square table", "round table");
    if (table == null) {
      return;
    }
    Item chair = find("chairs", "chair", "dining chair", "stool");
    float chairDepth = chair != null ? chair.depth + 8 : 0;
    // Its length along the longest side of the room
    Edge longest = this.edges [0];
    for (Edge edge : this.edges) {
      if (edge.length > longest.length + 1) {
        longest = edge;
      }
    }
    float angle = getAngle(-longest.ty, longest.tx);
    float [] center = getCentroid();
    // The place nearest to the middle of the room where the table and its chairs fit, then the table alone
    float [] place = null;
    for (int attempt = chair != null ? 0 : 1; attempt < 2 && place == null; attempt++) {
      float margin = attempt == 0 ? chairDepth + 10 : 0;
      for (int ring = 0; ring <= 10 && place == null; ring++) {
        for (int step = 0; step < Math.max(1, ring * 8) && place == null; step++) {
          double direction = 2 * Math.PI * step / Math.max(1, ring * 8);
          float x = center [0] + (float)Math.cos(direction) * ring * 20;
          float y = center [1] + (float)Math.sin(direction) * ring * 20;
          if (isClear(getRectangle(x, y, table.width + 2 * margin, table.depth + 2 * margin, angle), table.height)
              && fits(getRectangle(x, y, table.width, table.depth, angle), table.height, ACCESS_ANY)) {
            place = new float [] {x, y};
          }
        }
      }
    }
    if (place == null) {
      if (!isFull()) {
        this.skipped.add(role + " \"" + table.piece.getName() + "\": no free floor of " + Math.round(table.width) + " x "
            + Math.round(table.depth) + " cm with a passage around it");
      }
      return;
    }
    Placement tablePlacement = add(table, getRectangle(place [0], place [1], table.width, table.depth, angle),
        place [0], place [1], angle, "in the middle of the free floor", ACCESS_ANY);
    if (chair == null || tablePlacement == null) {
      return;
    }
    // The chairs: along both long sides, then one at each end
    float [] front = getFront(angle);
    float [] side = {(float)Math.cos(angle), (float)Math.sin(angle)};
    int perSide = Math.max(1, Math.min(4, (int)(table.width / (chair.width + 12))));
    boolean ends = table.depth >= chair.width + 10;
    List<float []> seats = new ArrayList<float []>();
    float gap = 5;
    for (int i = 0; i < perSide; i++) {
      float offset = (i + 0.5f) * table.width / perSide - table.width / 2;
      for (int direction = 1; direction >= -1; direction -= 2) {
        float distance = table.depth / 2 + gap + chair.depth / 2;
        seats.add(new float [] {place [0] + side [0] * offset + front [0] * distance * direction,
            place [1] + side [1] * offset + front [1] * distance * direction, -front [0] * direction, -front [1] * direction});
      }
    }
    if (ends) {
      for (int direction = 1; direction >= -1; direction -= 2) {
        float distance = table.width / 2 + gap + chair.depth / 2;
        seats.add(new float [] {place [0] + side [0] * distance * direction, place [1] + side [1] * distance * direction,
            -side [0] * direction, -side [1] * direction});
      }
    }
    int placed = 0;
    int wanted = Math.min(maxChairs, seats.size());
    // The table is reached from its chairs
    int tableRequirement = this.requirements.size() - 1;
    for (int i = 0; i < seats.size() && placed < maxChairs && !isFull(); i++) {
      float [] seat = seats.get(i);
      float chairAngle = getAngle(seat [2], seat [3]);
      float [][] points = getRectangle(seat [0], seat [1], chair.width, chair.depth, chairAngle);
      if (fits(points, chair.height, ACCESS_BACK_OR_SIDES, tableRequirement)) {
        if (tableRequirement >= 0) {
          this.requirements.remove(tableRequirement);
          tableRequirement = -1;
        }
        add(chair, points, seat [0], seat [1], chairAngle, "at the table", ACCESS_BACK_OR_SIDES);
        placed++;
      }
    }
    if (placed < wanted && !isFull()) {
      this.skipped.add((wanted - placed) + " of " + wanted + " chairs: no room around the table with a passage behind them");
    }
  }

  // ---- Catalog ----

  private boolean isFull() {
    return this.placements.size() >= this.maxItems;
  }

  /**
   * Returns the piece of the catalog for a role, or <code>null</code> after noting that none matches.
   */
  private Item find(String role, String... queries) {
    if (isFull()) {
      return null;
    }
    CatalogPieceOfFurniture piece = this.catalog.find(queries);
    if (piece == null || !(piece.getWidth() > 0) || !(piece.getDepth() > 0)) {
      String note = role + ": no catalog piece matches \"" + queries [0] + "\"";
      if (!this.skipped.contains(note)) {
        this.skipped.add(note);
      }
      return null;
    }
    return new Item(role, piece);
  }

  // ---- Placement ----

  private Placement placeOnWall(Item item, WallRule rule) {
    return placeOnWall(item, rule, true);
  }

  /**
   * Places a piece against a wall, its back to it, at the best of the places that suit.
   */
  private Placement placeOnWall(Item item, WallRule rule, boolean note) {
    if (item == null || isFull()) {
      return null;
    }
    boolean tall = item.height > TALL;
    List<Candidate> candidates = new ArrayList<Candidate>();
    float longestFree = 0;
    this.candidateOrder = 0;
    for (int i = 0; i < this.edges.length; i++) {
      Edge edge = this.edges [i];
      if (rule.facing != null && edge.nx * rule.facing [0] + edge.ny * rule.facing [1] < 0.7f) {
        continue;
      }
      for (float [] segment : getFreeSegments(i, item.depth, tall)) {
        float length = segment [1] - segment [0];
        longestFree = Math.max(longestFree, length);
        float low = segment [0] + GAP + item.width / 2;
        float high = segment [1] - GAP - item.width / 2;
        if (high < low) {
          continue;
        }
        float middle = (low + high) / 2;
        float score = Math.min(length, 800);
        if (rule.avoidDoorWall && edge.door) {
          score -= 150;
        }
        if (rule.avoidEdge == i) {
          score -= 300;
        }
        if (rule.window < 0 && hasOpening(i, segment [0], segment [1], false)) {
          score -= 150;
        }
        if (rule.reserve > 0 && length >= item.width + 2 * rule.reserve + 2 * GAP) {
          score += 300;
        }
        if (rule.align == ALIGN_NEAR) {
          float t = (rule.near [0] - edge.x) * edge.tx + (rule.near [1] - edge.y) * edge.ty;
          t = Math.max(low, Math.min(high, t));
          float x = edge.x + edge.tx * t + edge.nx * (edge.inset + GAP + item.depth / 2);
          float y = edge.y + edge.ty * t + edge.ny * (edge.inset + GAP + item.depth / 2);
          float distance = (float)Math.hypot(x - rule.near [0], y - rule.near [1]);
          addCandidate(candidates, i, t, -distance);
          continue;
        }
        if (rule.window > 0) {
          // Centered under each window of the side
          for (Opening opening : this.openings) {
            if (opening.edge == i && !opening.door && opening.end > segment [0] && opening.start < segment [1]) {
              float t = Math.max(low, Math.min(high, (opening.start + opening.end) / 2));
              addCandidate(candidates, i, t, 2000 - Math.abs(t - (opening.start + opening.end) / 2));
            }
          }
        }
        if (rule.align == ALIGN_CORNER) {
          // The end of the segment that is a corner of the room first
          boolean startCorner = segment [0] <= edge.inset + 30;
          boolean endCorner = segment [1] >= edge.length - edge.inset - 30;
          addCandidate(candidates, i, low, score + (startCorner ? 100 : 0));
          addCandidate(candidates, i, high, score + (endCorner ? 99 : -1));
          addCandidate(candidates, i, middle, score - 60);
        } else {
          addCandidate(candidates, i, middle, score);
          addCandidate(candidates, i, low, score - 60);
          addCandidate(candidates, i, high, score - 61);
        }
        // The places between them
        int steps = Math.min(12, (int)((high - low) / 25));
        for (int step = 1; step < steps; step++) {
          float t = low + (high - low) * step / steps;
          addCandidate(candidates, i, t, score - 100 - Math.abs(t - middle) / 10);
        }
      }
    }
    Collections.sort(candidates, new Comparator<Candidate>() {
        public int compare(Candidate candidate1, Candidate candidate2) {
          int comparison = Float.compare(candidate2.score, candidate1.score);
          return comparison != 0 ? comparison : candidate1.order - candidate2.order;
        }
      });
    for (Candidate candidate : candidates) {
      Edge edge = this.edges [candidate.edge];
      float x = edge.x + edge.tx * candidate.t + edge.nx * (edge.inset + GAP + item.depth / 2);
      float y = edge.y + edge.ty * candidate.t + edge.ny * (edge.inset + GAP + item.depth / 2);
      float angle = getAngle(edge.nx, edge.ny);
      float [][] points = getRectangle(x, y, item.width, item.depth, angle);
      if (fits(points, item.height, rule.access)) {
        String where = "against the " + describe(edge) + " wall";
        if (rule.window > 0 && hasOpening(candidate.edge, candidate.t - item.width / 2, candidate.t + item.width / 2, false)) {
          where += ", under the window";
        }
        return add(item, points, x, y, angle, where, rule.access);
      }
    }
    if (note) {
      this.skipped.add(item.role + " \"" + item.piece.getName() + "\" (" + Math.round(item.width) + " x " + Math.round(item.depth)
          + " cm" + (tall ? ", " + Math.round(item.height) + " cm high" : "") + "): "
          + (candidates.isEmpty()
              ? "no free wall long enough" + (tall ? " away from the windows" : "") + " (the longest free one is "
                  + Math.round(longestFree) + " cm)"
              : "every free wall place would overlap a piece, a door zone or close the " + Math.round(PASSAGE) + " cm passage"));
    }
    return null;
  }

  private void addCandidate(List<Candidate> candidates, int edge, float t, float score) {
    Candidate candidate = new Candidate();
    candidate.edge = edge;
    candidate.t = t;
    candidate.score = score;
    candidate.order = this.candidateOrder++;
    candidates.add(candidate);
  }

  private Placement placeInCorner(Item item) {
    WallRule rule = new WallRule();
    rule.align = ALIGN_CORNER;
    rule.window = item != null && item.height > TALL ? -1 : 0;
    rule.avoidDoorWall = true;
    rule.access = ACCESS_NONE;
    return placeOnWall(item, rule);
  }

  /**
   * Places a piece against the wall of another one, on its left (-1) or on its right (1).
   */
  private boolean placeBeside(Item item, Placement neighbor, int direction) {
    if (isFull()) {
      return false;
    }
    Edge edge = this.edges [getEdge(neighbor)];
    float t = (neighbor.x - edge.x) * edge.tx + (neighbor.y - edge.y) * edge.ty
        + direction * (neighbor.piece.getWidth() / 2 + GAP + item.width / 2);
    float x = edge.x + edge.tx * t + edge.nx * (edge.inset + GAP + item.depth / 2);
    float y = edge.y + edge.ty * t + edge.ny * (edge.inset + GAP + item.depth / 2);
    float [][] points = getRectangle(x, y, item.width, item.depth, neighbor.angle);
    if (fits(points, item.height, ACCESS_NONE)) {
      add(item, points, x, y, neighbor.angle, "beside the " + neighbor.role, ACCESS_NONE);
      return true;
    }
    return false;
  }

  private Placement add(Item item, float [][] points, float x, float y, float angle, String where, int access) {
    Placement placement = new Placement(item.piece, item.role, x, y, angle, where, points);
    this.placements.add(placement);
    this.obstacles.add(new Obstacle(points, KIND_PIECE));
    block(points, this.blockedCells);
    float [][] accessPoints = getAccessPoints(points, access);
    if (accessPoints != null) {
      this.requirements.add(accessPoints);
    }
    return placement;
  }

  /**
   * Returns the index of the side a placed piece has its back to.
   */
  private int getEdge(Placement placement) {
    float [] front = getFront(placement.angle);
    int best = 0;
    float bestDistance = Float.MAX_VALUE;
    for (int i = 0; i < this.edges.length; i++) {
      Edge edge = this.edges [i];
      if (edge.nx * front [0] + edge.ny * front [1] > 0.9f) {
        float t = (placement.x - edge.x) * edge.tx + (placement.y - edge.y) * edge.ty;
        float distance = Math.abs((placement.x - edge.x) * edge.nx + (placement.y - edge.y) * edge.ny)
            + Math.max(0, -t) + Math.max(0, t - edge.length);
        if (distance < bestDistance) {
          bestDistance = distance;
          best = i;
        }
      }
    }
    return best;
  }

  private static String describe(Edge edge) {
    // The wall at the top of the plan has its normal downward
    if (edge.ny > 0.9f) {
      return "top";
    } else if (edge.ny < -0.9f) {
      return "bottom";
    } else if (edge.nx > 0.9f) {
      return "left";
    } else if (edge.nx < -0.9f) {
      return "right";
    }
    return (edge.ny > 0 ? "top " : "bottom ") + (edge.nx > 0 ? "left" : "right");
  }

  // ---- Checks ----

  private boolean fits(float [][] points, float height, int access) {
    return fits(points, height, access, -1);
  }

  /**
   * Returns <code>true</code> if a piece may stand on the given rectangle: inside the room, clear of the
   * other pieces, of the zones of the doors and, if it's tall, of the windows, and leaving a passage
   * from the doors to itself and to every piece placed before.
   * @param replacedRequirement the index of the place it will be reached from, or -1
   */
  private boolean fits(float [][] points, float height, int access, int replacedRequirement) {
    if (!isClear(points, height)) {
      return false;
    }
    boolean [] blocked = this.blockedCells.clone();
    block(points, blocked);
    List<float [][]> required = new ArrayList<float [][]>(this.requirements);
    if (replacedRequirement >= 0 && replacedRequirement < required.size()) {
      required.remove(replacedRequirement);
    }
    float [][] accessPoints = getAccessPoints(points, access);
    if (accessPoints != null) {
      required.add(accessPoints);
    }
    return isJoined(blocked, required);
  }

  /**
   * Returns <code>true</code> if the given rectangle is inside the room and over nothing.
   */
  private boolean isClear(float [][] points, float height) {
    // Inside the room and out of its walls
    Area outside = getArea(shrink(points, 0.5f));
    outside.subtract(this.interior);
    if (!outside.isEmpty() && getSurface(outside) > 1) {
      return false;
    }
    for (Obstacle obstacle : this.obstacles) {
      if ((obstacle.kind != KIND_WINDOW || height > TALL) && overlap(points, obstacle.points, 0.5f)) {
        return false;
      }
    }
    return true;
  }

  private static float [][] shrink(float [][] points, float distance) {
    float [] center = new float [2];
    for (float [] point : points) {
      center [0] += point [0] / points.length;
      center [1] += point [1] / points.length;
    }
    float [][] shrunk = new float [points.length][];
    for (int i = 0; i < points.length; i++) {
      float length = (float)Math.hypot(points [i][0] - center [0], points [i][1] - center [1]);
      float ratio = length > 2 * distance ? (length - distance * 1.5f) / length : 0.5f;
      shrunk [i] = new float [] {center [0] + (points [i][0] - center [0]) * ratio, center [1] + (points [i][1] - center [1]) * ratio};
    }
    return shrunk;
  }

  /**
   * Returns <code>true</code> if two convex polygons overlap by more than <code>tolerance</code>.
   */
  static boolean overlap(float [][] polygon1, float [][] polygon2, float tolerance) {
    for (int pass = 0; pass < 2; pass++) {
      float [][] polygon = pass == 0 ? polygon1 : polygon2;
      for (int i = 0; i < polygon.length; i++) {
        float [] next = polygon [(i + 1) % polygon.length];
        float axisX = -(next [1] - polygon [i][1]);
        float axisY = next [0] - polygon [i][0];
        float length = (float)Math.hypot(axisX, axisY);
        if (length < 1E-4f) {
          continue;
        }
        axisX /= length;
        axisY /= length;
        float min1 = Float.MAX_VALUE;
        float max1 = -Float.MAX_VALUE;
        for (float [] point : polygon1) {
          float projection = point [0] * axisX + point [1] * axisY;
          min1 = Math.min(min1, projection);
          max1 = Math.max(max1, projection);
        }
        float min2 = Float.MAX_VALUE;
        float max2 = -Float.MAX_VALUE;
        for (float [] point : polygon2) {
          float projection = point [0] * axisX + point [1] * axisY;
          min2 = Math.min(min2, projection);
          max2 = Math.max(max2, projection);
        }
        if (max1 <= min2 + tolerance || max2 <= min1 + tolerance) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns the parts of a side where a piece of the given depth may have its back, each one
   * as the distances of its ends from the start of the side.
   */
  private List<float []> getFreeSegments(int edgeIndex, float depth, boolean tall) {
    Edge edge = this.edges [edgeIndex];
    float near = edge.inset + 0.5f;
    float far = edge.inset + GAP + depth - 0.5f;
    List<float []> blocked = new ArrayList<float []>();
    for (Obstacle obstacle : this.obstacles) {
      if (obstacle.kind != KIND_WINDOW || tall) {
        float [] range = getRange(edge, obstacle.points);
        if (range [3] > near && range [2] < far && range [1] > 0 && range [0] < edge.length) {
          blocked.add(new float [] {range [0] - GAP, range [1] + GAP});
        }
      }
    }
    for (float [][] wall : this.wallPolygons) {
      float [] range = getRange(edge, wall);
      if (range [3] > near && range [2] < far && range [1] > 0 && range [0] < edge.length) {
        blocked.add(new float [] {range [0], range [1]});
      }
    }
    // The other sides that come in front of this one, in a room that isn't convex
    for (int i = 0; i < this.edges.length; i++) {
      if (i != edgeIndex) {
        Edge other = this.edges [i];
        float [] range = getRange(edge, new float [][] {{other.x, other.y},
            {other.x + other.tx * other.length, other.y + other.ty * other.length}});
        if (range [3] > near + 1 && range [2] < far - 1 && range [1] > 1 && range [0] < edge.length - 1
            && range [1] - range [0] > 1) {
          blocked.add(new float [] {range [0], range [1]});
        }
      }
    }
    Collections.sort(blocked, new Comparator<float []>() {
        public int compare(float [] range1, float [] range2) {
          return Float.compare(range1 [0], range2 [0]);
        }
      });
    List<float []> segments = new ArrayList<float []>();
    float start = 0;
    for (float [] range : blocked) {
      if (range [0] - start > 5) {
        segments.add(new float [] {start, range [0]});
      }
      start = Math.max(start, range [1]);
    }
    if (edge.length - start > 5) {
      segments.add(new float [] {start, edge.length});
    }
    return segments;
  }

  /**
   * Returns the bounds {along min, along max, inward min, inward max} of points in the frame of a side.
   */
  private static float [] getRange(Edge edge, float [][] points) {
    float [] range = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] point : points) {
      float t = (point [0] - edge.x) * edge.tx + (point [1] - edge.y) * edge.ty;
      float d = (point [0] - edge.x) * edge.nx + (point [1] - edge.y) * edge.ny;
      range [0] = Math.min(range [0], t);
      range [1] = Math.max(range [1], t);
      range [2] = Math.min(range [2], d);
      range [3] = Math.max(range [3], d);
    }
    return range;
  }

  private boolean hasOpening(int edge, float start, float end, boolean door) {
    for (Opening opening : this.openings) {
      if (opening.edge == edge && opening.door == door && opening.end > start && opening.start < end) {
        return true;
      }
    }
    return false;
  }

  // ---- Doors, windows and furniture of the room ----

  private boolean isInRoom(HomePieceOfFurniture piece) {
    if (contains(this.polygon, piece.getX(), piece.getY())) {
      return true;
    }
    float [][] points = piece.getPoints();
    for (float [] point : points) {
      if (contains(this.polygon, point [0], point [1])) {
        return true;
      }
    }
    for (float [] point : this.polygon) {
      if (contains(points, point [0], point [1])) {
        return true;
      }
    }
    return false;
  }

  /**
   * Records a door or a window if it's in a wall of the room, and the zone to keep free in front of it.
   */
  private void addOpening(HomePieceOfFurniture piece) {
    for (int i = 0; i < this.edges.length; i++) {
      Edge edge = this.edges [i];
      float t = (piece.getX() - edge.x) * edge.tx + (piece.getY() - edge.y) * edge.ty;
      float d = (piece.getX() - edge.x) * edge.nx + (piece.getY() - edge.y) * edge.ny;
      float width = piece.getWidth();
      // Along the side and in its wall
      boolean parallel = Math.abs(Math.cos(piece.getAngle()) * edge.ty - Math.sin(piece.getAngle()) * edge.tx) < 0.3;
      if (!parallel || d < -45 || d > edge.inset + 20 || Math.min(t + width / 2, edge.length) - Math.max(t - width / 2, 0) < 10) {
        continue;
      }
      Opening opening = new Opening();
      opening.edge = i;
      opening.start = t - width / 2;
      opening.end = t + width / 2;
      opening.door = piece.getElevation() <= 30 && piece.getHeight() >= 150;
      this.openings.add(opening);
      float depth;
      if (opening.door) {
        // Where its leaf turns and someone steps in
        depth = edge.inset + Math.max(PASSAGE, Math.min(width, 100));
        edge.door = true;
        this.doorCount++;
        float entry = edge.inset + Math.max(PASSAGE, Math.min(width, 100)) / 2;
        this.requirements.add(new float [][] {{edge.x + edge.tx * t + edge.nx * entry, edge.y + edge.ty * t + edge.ny * entry}});
      } else {
        depth = edge.inset + 35;
        this.windowCount++;
      }
      this.obstacles.add(new Obstacle(new float [][] {
          {edge.x + edge.tx * opening.start, edge.y + edge.ty * opening.start},
          {edge.x + edge.tx * opening.end, edge.y + edge.ty * opening.end},
          {edge.x + edge.tx * opening.end + edge.nx * depth, edge.y + edge.ty * opening.end + edge.ny * depth},
          {edge.x + edge.tx * opening.start + edge.nx * depth, edge.y + edge.ty * opening.start + edge.ny * depth}},
          opening.door ? KIND_DOOR : KIND_WINDOW));
      return;
    }
  }

  // ---- Passages ----

  /**
   * Computes the cells of the floor where the middle of someone who walks may be: in the room,
   * half a passage away from its walls and from its furniture.
   */
  private void createGrid() {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (float [] point : this.polygon) {
      minX = Math.min(minX, point [0]);
      minY = Math.min(minY, point [1]);
      maxX = Math.max(maxX, point [0]);
      maxY = Math.max(maxY, point [1]);
    }
    this.cellSize = Math.max(8, (float)Math.sqrt((maxX - minX) * (maxY - minY) / 12000));
    this.gridX = minX;
    this.gridY = minY;
    this.gridWidth = Math.max(1, (int)Math.ceil((maxX - minX) / this.cellSize));
    this.gridHeight = Math.max(1, (int)Math.ceil((maxY - minY) / this.cellSize));
    this.freeCells = new boolean [this.gridWidth * this.gridHeight];
    this.blockedCells = new boolean [this.freeCells.length];
    for (int j = 0; j < this.gridHeight; j++) {
      for (int i = 0; i < this.gridWidth; i++) {
        float x = this.gridX + (i + 0.5f) * this.cellSize;
        float y = this.gridY + (j + 0.5f) * this.cellSize;
        boolean free = this.interior.contains(x, y);
        for (int k = 0; free && k < this.polygon.length; k++) {
          float [] next = this.polygon [(k + 1) % this.polygon.length];
          free = Line2D.ptSegDist(this.polygon [k][0], this.polygon [k][1], next [0], next [1], x, y) >= RADIUS;
        }
        for (int k = 0; free && k < this.wallPolygons.size(); k++) {
          free = getDistance(this.wallPolygons.get(k), x, y) >= RADIUS;
        }
        this.freeCells [j * this.gridWidth + i] = free;
      }
    }
    for (Obstacle obstacle : this.obstacles) {
      if (obstacle.kind == KIND_PIECE) {
        block(obstacle.points, this.blockedCells);
      }
    }
    // A door that is closed from the start asks for nothing
    for (int i = this.requirements.size() - 1; i >= 0; i--) {
      if (getCell(this.requirements.get(i) [0], this.blockedCells) < 0) {
        this.requirements.remove(i);
      }
    }
    // The doors that are joined to the first one
    for (int i = this.requirements.size() - 1; i >= 1; i--) {
      List<float [][]> required = new ArrayList<float [][]>();
      required.add(this.requirements.get(0));
      required.add(this.requirements.get(i));
      if (!isJoined(this.blockedCells, required)) {
        this.requirements.remove(i);
      }
    }
  }

  /**
   * Marks the cells closer than half a passage to a polygon.
   */
  private void block(float [][] points, boolean [] blocked) {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (float [] point : points) {
      minX = Math.min(minX, point [0]);
      minY = Math.min(minY, point [1]);
      maxX = Math.max(maxX, point [0]);
      maxY = Math.max(maxY, point [1]);
    }
    int i1 = Math.max(0, (int)Math.floor((minX - RADIUS - this.gridX) / this.cellSize));
    int i2 = Math.min(this.gridWidth - 1, (int)Math.ceil((maxX + RADIUS - this.gridX) / this.cellSize));
    int j1 = Math.max(0, (int)Math.floor((minY - RADIUS - this.gridY) / this.cellSize));
    int j2 = Math.min(this.gridHeight - 1, (int)Math.ceil((maxY + RADIUS - this.gridY) / this.cellSize));
    for (int j = j1; j <= j2; j++) {
      for (int i = i1; i <= i2; i++) {
        int index = j * this.gridWidth + i;
        if (this.freeCells [index] && !blocked [index]
            && getDistance(points, this.gridX + (i + 0.5f) * this.cellSize, this.gridY + (j + 0.5f) * this.cellSize) < RADIUS) {
          blocked [index] = true;
        }
      }
    }
  }

  /**
   * Returns the distance from a point to a polygon, 0 if it's inside.
   */
  private static float getDistance(float [][] polygon, float x, float y) {
    if (contains(polygon, x, y)) {
      return 0;
    }
    double distance = Double.MAX_VALUE;
    for (int i = 0; i < polygon.length; i++) {
      float [] next = polygon [(i + 1) % polygon.length];
      distance = Math.min(distance, Line2D.ptSegDist(polygon [i][0], polygon [i][1], next [0], next [1], x, y));
    }
    return (float)distance;
  }

  /**
   * Returns the index of a walkable cell at the given point or beside it, or -1.
   */
  private int getCell(float [] point, boolean [] blocked) {
    int centerI = (int)Math.floor((point [0] - this.gridX) / this.cellSize);
    int centerJ = (int)Math.floor((point [1] - this.gridY) / this.cellSize);
    int reach = Math.max(1, Math.round(20 / this.cellSize));
    int best = -1;
    float bestDistance = Float.MAX_VALUE;
    for (int j = centerJ - reach; j <= centerJ + reach; j++) {
      for (int i = centerI - reach; i <= centerI + reach; i++) {
        if (i >= 0 && j >= 0 && i < this.gridWidth && j < this.gridHeight) {
          int index = j * this.gridWidth + i;
          float distance = (float)Math.hypot(i - centerI, j - centerJ);
          if (this.freeCells [index] && !blocked [index] && distance < bestDistance) {
            bestDistance = distance;
            best = index;
          }
        }
      }
    }
    return best;
  }

  /**
   * Returns <code>true</code> if one can walk between all the required places.
   */
  private boolean isJoined(boolean [] blocked, List<float [][]> required) {
    if (required.isEmpty()) {
      return true;
    }
    // Walk from the first door, or from the first place
    int start = -1;
    for (float [] point : required.get(0)) {
      start = getCell(point, blocked);
      if (start >= 0) {
        break;
      }
    }
    if (start < 0) {
      return false;
    }
    boolean [] reached = new boolean [blocked.length];
    int [] queue = new int [blocked.length];
    int head = 0;
    int tail = 0;
    queue [tail++] = start;
    reached [start] = true;
    while (head < tail) {
      int index = queue [head++];
      int i = index % this.gridWidth;
      int j = index / this.gridWidth;
      for (int direction = 0; direction < 4; direction++) {
        int ni = i + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
        int nj = j + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
        if (ni >= 0 && nj >= 0 && ni < this.gridWidth && nj < this.gridHeight) {
          int next = nj * this.gridWidth + ni;
          if (!reached [next] && this.freeCells [next] && !blocked [next]) {
            reached [next] = true;
            queue [tail++] = next;
          }
        }
      }
    }
    for (int k = 1; k < required.size(); k++) {
      boolean joined = false;
      for (float [] point : required.get(k)) {
        int centerI = (int)Math.floor((point [0] - this.gridX) / this.cellSize);
        int centerJ = (int)Math.floor((point [1] - this.gridY) / this.cellSize);
        int reach = Math.max(1, Math.round(20 / this.cellSize));
        for (int j = centerJ - reach; j <= centerJ + reach && !joined; j++) {
          for (int i = centerI - reach; i <= centerI + reach && !joined; i++) {
            joined = i >= 0 && j >= 0 && i < this.gridWidth && j < this.gridHeight && reached [j * this.gridWidth + i];
          }
        }
        if (joined) {
          break;
        }
      }
      if (!joined) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the points a piece is reached from, one of which is enough, or <code>null</code>.
   */
  private static float [][] getAccessPoints(float [][] points, int access) {
    if (access == ACCESS_NONE) {
      return null;
    }
    // The points of a rectangle: back left, back right, front right, front left
    float [][] middles = new float [4][];
    float [] center = {(points [0][0] + points [2][0]) / 2, (points [0][1] + points [2][1]) / 2};
    for (int i = 0; i < 4; i++) {
      float [] next = points [(i + 1) % 4];
      float x = (points [i][0] + next [0]) / 2;
      float y = (points [i][1] + next [1]) / 2;
      float length = (float)Math.hypot(x - center [0], y - center [1]);
      float reach = RADIUS + 10;
      middles [i] = new float [] {x + (x - center [0]) / length * reach, y + (y - center [1]) / length * reach};
    }
    if (access == ACCESS_FRONT) {
      return new float [][] {middles [2]};
    } else if (access == ACCESS_BACK_OR_SIDES) {
      return new float [][] {middles [0], middles [1], middles [3]};
    }
    return new float [][] {middles [2], middles [1], middles [3], middles [0]};
  }

  // ---- Geometry ----

  /**
   * Returns the corners of a piece: back left, back right, front right, front left.
   */
  static float [][] getRectangle(float x, float y, float width, float depth, float angle) {
    float cos = (float)Math.cos(angle);
    float sin = (float)Math.sin(angle);
    float [][] points = {{-width / 2, -depth / 2}, {width / 2, -depth / 2}, {width / 2, depth / 2}, {-width / 2, depth / 2}};
    for (float [] point : points) {
      float px = point [0];
      float py = point [1];
      point [0] = x + px * cos - py * sin;
      point [1] = y + px * sin + py * cos;
    }
    return points;
  }

  /**
   * Returns the direction the front of a piece faces.
   */
  static float [] getFront(float angle) {
    return new float [] {-(float)Math.sin(angle), (float)Math.cos(angle)};
  }

  /**
   * Returns the angle of a piece whose front faces the given direction.
   */
  static float getAngle(float frontX, float frontY) {
    double angle = Math.atan2(-frontX, frontY);
    if (angle < -1E-6) {
      angle += 2 * Math.PI;
    }
    return (float)Math.max(0, angle);
  }

  private float [] getCentroid() {
    // The middle of the floor between the walls
    double area = 0;
    double x = 0;
    double y = 0;
    for (float [][] outline : AssistantMeshBuilder.getOutlines(this.interior)) {
      for (int i = 0; i < outline.length; i++) {
        float [] next = outline [(i + 1) % outline.length];
        double cross = (double)outline [i][0] * next [1] - (double)next [0] * outline [i][1];
        area += cross;
        x += (outline [i][0] + next [0]) * cross;
        y += (outline [i][1] + next [1]) * cross;
      }
    }
    if (Math.abs(area) < 1) {
      return new float [] {this.polygon [0][0], this.polygon [0][1]};
    }
    return new float [] {(float)(x / (3 * area)), (float)(y / (3 * area))};
  }

  private static float [][] clean(float [][] points) {
    List<float []> cleaned = new ArrayList<float []>();
    for (float [] point : points) {
      if (cleaned.isEmpty() || Math.hypot(point [0] - cleaned.get(cleaned.size() - 1) [0],
              point [1] - cleaned.get(cleaned.size() - 1) [1]) > 0.5) {
        cleaned.add(point);
      }
    }
    if (cleaned.size() > 1 && Math.hypot(cleaned.get(0) [0] - cleaned.get(cleaned.size() - 1) [0],
            cleaned.get(0) [1] - cleaned.get(cleaned.size() - 1) [1]) <= 0.5) {
      cleaned.remove(cleaned.size() - 1);
    }
    return cleaned.toArray(new float [cleaned.size()][]);
  }

  static Area getArea(float [][] points) {
    GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);
    for (int i = 0; i < points.length; i++) {
      if (i == 0) {
        path.moveTo(points [i][0], points [i][1]);
      } else {
        path.lineTo(points [i][0], points [i][1]);
      }
    }
    path.closePath();
    return new Area(path);
  }

  /**
   * Returns the surface of an area.
   */
  static float getSurface(Area area) {
    double surface = 0;
    double startX = 0;
    double startY = 0;
    double x = 0;
    double y = 0;
    float [] coordinates = new float [6];
    for (PathIterator it = area.getPathIterator(null, 0.5); !it.isDone(); it.next()) {
      int type = it.currentSegment(coordinates);
      if (type == PathIterator.SEG_MOVETO) {
        startX = x = coordinates [0];
        startY = y = coordinates [1];
      } else if (type == PathIterator.SEG_LINETO) {
        surface += x * coordinates [1] - coordinates [0] * y;
        x = coordinates [0];
        y = coordinates [1];
      } else if (type == PathIterator.SEG_CLOSE) {
        surface += x * startY - startX * y;
        x = startX;
        y = startY;
      }
    }
    return (float)Math.abs(surface / 2);
  }

  /**
   * Returns <code>true</code> if a polygon contains a point.
   */
  static boolean contains(float [][] polygon, float x, float y) {
    boolean inside = false;
    for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      if ((polygon [i][1] > y) != (polygon [j][1] > y)
          && x < (polygon [j][0] - polygon [i][0]) * (y - polygon [i][1]) / (polygon [j][1] - polygon [i][1]) + polygon [i][0]) {
        inside = !inside;
      }
    }
    return inside;
  }
}
