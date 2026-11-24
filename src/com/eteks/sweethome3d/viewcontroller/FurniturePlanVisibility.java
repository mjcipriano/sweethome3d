/*
 * FurniturePlanVisibility.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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

import java.util.Locale;

import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;

/**
 * How a piece of furniture is drawn in the plan of the levels above its own
 * one, which it reaches when it's higher than its level: a roof placed on the
 * level of the walls it covers rises through the floor above, where the plan
 * would draw it as a solid shape over the rooms. A roof is shown the same way
 * in the plan of its own level, where it's above the rooms too. The choice is
 * stored in a property of the piece, saved with the home.
 * @author Sweet Home 3D
 */
public final class FurniturePlanVisibility {
  /** Property of a piece storing {@link #DRAW}, {@link #OUTLINE} or {@link #HIDE}. */
  public static final String PROPERTY = "planOnUpperLevels";
  /** The piece is drawn at upper levels like at its own one. */
  public static final String DRAW = "draw";
  /** Only a thin dashed outline of the piece is drawn at upper levels. */
  public static final String OUTLINE = "outline";
  /** The piece isn't drawn at upper levels. */
  public static final String HIDE = "hide";

  private FurniturePlanVisibility() {
    // Utility class
  }

  /**
   * Returns {@link #DRAW}, {@link #OUTLINE} or {@link #HIDE} for the given
   * text, or <code>null</code> if it's none of them.
   */
  public static String parse(String text) {
    if (text != null) {
      String value = text.trim().toLowerCase(Locale.ENGLISH);
      if (value.equals(DRAW) || value.equals("solid") || value.equals("show") || value.equals("default")) {
        return DRAW;
      } else if (value.equals(OUTLINE) || value.equals("dashed") || value.equals("outlined")) {
        return OUTLINE;
      } else if (value.equals(HIDE) || value.equals("hidden") || value.equals("none")) {
        return HIDE;
      }
    }
    return null;
  }

  /**
   * Returns how <code>piece</code> is drawn in the plan of the levels above
   * its own one. Without choice, roofs are outlined, the ground is hidden and other pieces are drawn.
   */
  public static String get(HomePieceOfFurniture piece) {
    String value = parse(piece.getProperty(PROPERTY));
    if (value != null) {
      return value;
    } else if ("terrain".equals(piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY))) {
      // The ground under a house would cover the plan of each of its levels
      return HIDE;
    } else if (isRoof(piece)) {
      return OUTLINE;
    } else {
      return DRAW;
    }
  }

  /**
   * Returns <code>true</code> if the plan draws <code>piece</code> with its picture seen from the
   * top whatever the preference of the user: a slab, a piece made of parts or a plant has such a picture,
   * and the icon of a catalog would show it in perspective in a white square.
   */
  public static boolean isDrawnFromTop(HomePieceOfFurniture piece) {
    return piece.getPlanIcon() != null
        && (piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null
            || piece.getProperty(AssistantShapeCommands.PARTS_PROPERTY) != null
            || piece.getProperty(AssistantPlantCommands.PLANT_PROPERTY) != null);
  }

  /**
   * Returns <code>true</code> if <code>piece</code> is a roof, which is above
   * the rooms even in the plan of its own level.
   */
  public static boolean isRoof(HomePieceOfFurniture piece) {
    return "true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY))
        || "roof".equals(piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY))
        || HomeItemTags.hasTag(piece, "roof");
  }

  /**
   * Returns how <code>piece</code> is drawn in the plan of <code>level</code>:
   * the value of {@link #get(HomePieceOfFurniture) get} at the levels above
   * its own one, and at its own level too if it's a roof or if it stands above
   * the height of the walls, {@link #DRAW} otherwise.
   */
  public static String getAtLevel(HomePieceOfFurniture piece, Level level) {
    if ("ceiling".equals(piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY))) {
      // The ceiling of a room would cover it in the plan
      return HIDE;
    }
    Level pieceLevel = piece.getLevel();
    if (pieceLevel != null && level != null && pieceLevel != level
        && pieceLevel.getElevation() < level.getElevation()) {
      return get(piece);
    } else if (isRoof(piece)
               || pieceLevel != null && pieceLevel.getHeight() > 0 && piece.getElevation() >= pieceLevel.getHeight() - 1) {
      // Above what the plan of the level shows
      String value = get(piece);
      return value == HIDE && !isRoof(piece) ? DRAW : value;
    } else {
      return DRAW;
    }
  }
}
