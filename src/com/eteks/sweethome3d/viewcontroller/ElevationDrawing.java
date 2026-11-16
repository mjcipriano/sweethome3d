/*
 * ElevationDrawing.java 16 Nov 2026
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;

/**
 * Draws the elevation of a home seen from one of its sides, as a SVG
 * drawing at the scale of 1 unit per centimeter: walls, doors, windows and
 * furniture are drawn as the rectangle they fill seen from that side, the
 * farthest first. With a section distance, what stands in front of the cut
 * is left out, which shows the inside of the home.
 * <p>It's a schematic drawing made from the sizes of the items, not a
 * projection of their 3D models.
 * @author Sweet Home 3D
 */
public final class ElevationDrawing {
  /** The sides a home can be seen from. */
  public static final String [] SIDES = {"south", "north", "east", "west"};

  private static final int MARGIN = 120;

  private ElevationDrawing() {
  }

  /**
   * A rectangle of the drawing.
   */
  private static final class Shape {
    private final float   left;
    private final float   right;
    private final float   bottom;
    private final float   top;
    private final float   depth;    // Distance to the viewer of its closest point
    private final int     kind;     // 0 wall, 1 door or window, 2 furniture
    private final String  name;
    private final Integer color;

    Shape(float left, float right, float bottom, float top, float depth, int kind, String name, Integer color) {
      this.left = left;
      this.right = right;
      this.bottom = bottom;
      this.top = top;
      this.depth = depth;
      this.kind = kind;
      this.name = name;
      this.color = color;
    }
  }

  /**
   * Returns the side a text designates, or <code>null</code>.
   */
  public static String parseSide(String text) {
    if (text != null) {
      String side = text.trim().toLowerCase(Locale.ENGLISH);
      if (side.equals("front") || side.equals("bottom")) {
        return "south";
      } else if (side.equals("back") || side.equals("top")) {
        return "north";
      } else if (side.equals("right")) {
        return "east";
      } else if (side.equals("left")) {
        return "west";
      }
      for (String knownSide : SIDES) {
        if (knownSide.equals(side)) {
          return knownSide;
        }
      }
    }
    return null;
  }

  /**
   * Returns the horizontal position in the drawing and the distance to the
   * viewer of a point of the plan, for a viewer standing on the given side.
   */
  private static float [] project(String side, float x, float y) {
    if ("north".equals(side)) {
      return new float [] {-x, y};
    } else if ("east".equals(side)) {
      return new float [] {-y, -x};
    } else if ("west".equals(side)) {
      return new float [] {y, x};
    } else {
      return new float [] {x, -y};
    }
  }

  private static void addShape(List<Shape> shapes, String side, float [][] points, float bottom, float top,
                               int kind, String name, Integer color) {
    float left = Float.MAX_VALUE;
    float right = -Float.MAX_VALUE;
    float depth = Float.MAX_VALUE;
    for (float [] point : points) {
      float [] projection = project(side, point [0], point [1]);
      left = Math.min(left, projection [0]);
      right = Math.max(right, projection [0]);
      depth = Math.min(depth, projection [1]);
    }
    if (right > left && top > bottom) {
      shapes.add(new Shape(left, right, bottom, top, depth, kind, name, color));
    }
  }

  private static void addFurniture(List<Shape> shapes, String side, List<HomePieceOfFurniture> furniture) {
    for (HomePieceOfFurniture piece : furniture) {
      if (!piece.isVisible() || piece.getLevel() != null && !piece.getLevel().isViewableAndVisible()) {
        continue;
      }
      if (piece instanceof HomeFurnitureGroup) {
        addFurniture(shapes, side, ((HomeFurnitureGroup)piece).getFurniture());
      } else {
        float bottom = piece.getGroundElevation();
        addShape(shapes, side, piece.getPoints(), bottom, bottom + piece.getHeight(),
            piece.isDoorOrWindow() ? 1 : 2, piece.getName(), piece.getColor());
      }
    }
  }

  /**
   * Returns the SVG drawing of the elevation of <code>home</code>.
   * @param side    the side the viewer stands on: south (the bottom of the plan), north, east or west
   * @param section the distance in cm behind the closest point of the home
   *                where it's cut, or 0 to draw it from outside
   * @return the drawing, or <code>null</code> if the home has nothing to draw
   */
  public static String createSVG(Home home, String side, float section) {
    side = parseSide(side) != null ? parseSide(side) : "south";
    List<Shape> shapes = new ArrayList<Shape>();
    for (Wall wall : home.getWalls()) {
      Level level = wall.getLevel();
      if (level != null && !level.isViewableAndVisible()) {
        continue;
      }
      float bottom = level != null ? level.getElevation() : 0;
      float startHeight = wall.getHeight() != null ? wall.getHeight() : home.getWallHeight();
      float endHeight = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : startHeight;
      addShape(shapes, side, wall.getPoints(), bottom, bottom + Math.max(startHeight, endHeight), 0, null, null);
    }
    addFurniture(shapes, side, home.getFurniture());
    if (shapes.isEmpty()) {
      return null;
    }
    float front = Float.MAX_VALUE;
    for (Shape shape : shapes) {
      front = Math.min(front, shape.depth);
    }
    if (section > 0) {
      // Leave out what stands in front of the cut
      for (int i = shapes.size() - 1; i >= 0; i--) {
        if (shapes.get(i).depth < front + section) {
          shapes.remove(i);
        }
      }
      if (shapes.isEmpty()) {
        return null;
      }
    }
    // The farthest first; doors and windows after the wall they are in, whose face is closer than theirs
    Collections.sort(shapes, new Comparator<Shape>() {
        public int compare(Shape shape1, Shape shape2) {
          float depth1 = shape1.depth - (shape1.kind == 1 ? 60 : 0);
          float depth2 = shape2.depth - (shape2.kind == 1 ? 60 : 0);
          return Float.compare(depth2, depth1);
        }
      });
    float left = Float.MAX_VALUE;
    float right = -Float.MAX_VALUE;
    float bottom = 0;
    float top = -Float.MAX_VALUE;
    for (Shape shape : shapes) {
      left = Math.min(left, shape.left);
      right = Math.max(right, shape.right);
      bottom = Math.min(bottom, shape.bottom);
      top = Math.max(top, shape.top);
    }
    float width = right - left;
    float height = top - bottom;
    StringBuilder svg = new StringBuilder();
    svg.append(String.format(Locale.US,
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" viewBox=\"%.1f %.1f %.1f %.1f\">\n",
        width + 2 * MARGIN, height + 2 * MARGIN + 60 + 2 * Math.max(14, width / 90), -(float)MARGIN, -(float)MARGIN, width + 2 * MARGIN, height + 2 * MARGIN + 60 + 2 * Math.max(14, width / 90)));
    svg.append("<title>Elevation seen from the ").append(side)
       .append(section > 0 ? String.format(Locale.US, ", section at %.0f cm", section) : "").append("</title>\n");
    svg.append(String.format(Locale.US, "<rect x=\"%d\" y=\"%d\" width=\"%.1f\" height=\"%.1f\" fill=\"#FFFFFF\"/>\n",
        -MARGIN, -MARGIN, width + 2 * MARGIN, height + 2 * MARGIN + 60 + 2 * Math.max(14, width / 90)));
    // Lines and texts stay readable whatever the size of the home
    float fontSize = Math.max(14, width / 90);
    svg.append(String.format(Locale.US,
        "<g stroke=\"#333333\" stroke-width=\"%.1f\" font-family=\"sans-serif\" font-size=\"%.0f\">\n",
        Math.max(1.5f, width / 1500), fontSize));
    for (Shape shape : shapes) {
      String fill = shape.kind == 0 ? "#E4E1DA" : shape.kind == 1 ? "#DCEBF5" : "#EAD9BD";
      if (shape.kind == 2 && shape.color != null) {
        fill = String.format("#%06X", shape.color.intValue() & 0xFFFFFF);
      }
      svg.append(String.format(Locale.US, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"%s\"%s>",
          shape.left - left, top - shape.top, shape.right - shape.left, shape.top - shape.bottom, fill,
          shape.kind == 1 ? " stroke=\"#2F6EA5\"" : shape.kind == 2 ? " fill-opacity=\"0.6\" stroke=\"#8A7A5C\"" : ""));
      if (shape.name != null) {
        svg.append("<title>").append(escape(shape.name)).append("</title>");
      }
      svg.append("</rect>\n");
    }
    // Ground and floors
    svg.append(String.format(Locale.US, "<line x1=\"%d\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke-width=\"3\"/>\n",
        -MARGIN / 2, top, width + MARGIN / 2, top));
    for (Level level : home.getLevels()) {
      if (level.isViewableAndVisible() && level.getElevation() > 0 && level.getElevation() < top) {
        svg.append(String.format(Locale.US,
            "<line x1=\"%d\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#999999\" stroke-dasharray=\"8 6\"/>\n"
            + "<text x=\"%.1f\" y=\"%.1f\" stroke=\"none\" fill=\"#666666\">%s +%.0f</text>\n",
            -MARGIN / 2, top - level.getElevation(), width + MARGIN / 2, top - level.getElevation(),
            width + 8, top - level.getElevation() - 4, escape(level.getName()), level.getElevation()));
      }
    }
    // Overall sizes
    svg.append(String.format(Locale.US,
        "<line x1=\"0\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>\n"
        + "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" stroke=\"none\">%.0f cm</text>\n",
        top + 40, width, top + 40, width / 2, top + 44 + fontSize, width));
    svg.append(String.format(Locale.US,
        "<line x1=\"-40\" y1=\"%.1f\" x2=\"-40\" y2=\"%.1f\"/>\n"
        + "<text x=\"-48\" y=\"%.1f\" text-anchor=\"end\" stroke=\"none\">%.0f cm</text>\n",
        top - height, top, top - height / 2, height));
    svg.append(String.format(Locale.US,
        "<text x=\"0\" y=\"%.1f\" stroke=\"none\">Elevation seen from the %s%s - 1 unit = 1 cm</text>\n",
        top + 60 + 2 * fontSize, side, section > 0 ? String.format(Locale.US, ", section at %.0f cm", section) : ""));
    svg.append("</g>\n</svg>\n");
    return svg.toString();
  }

  private static String escape(String text) {
    return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
