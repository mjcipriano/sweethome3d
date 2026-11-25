/*
 * WallFinishBands.java 25 nov. 2026
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.TextureImage;
import com.eteks.sweethome3d.model.Wall;

/**
 * The finishes of a side of a wall that differ from the bottom of the wall to its top:
 * bands that each go from the floor up to a height, drawn over the color or the texture
 * of the side. A wall keeps one color or texture for each of its sides, which is the finish
 * above the highest band. The bands are stored in properties of the wall, so a home which
 * has some is read by the versions of the application that don't know them, where the
 * sides show their own finish over their whole height.
 * @author Sweet Home 3D contributors
 */
public class WallFinishBands {
  /** The property of a wall that describes the bands of its left side, at JSON format. */
  public static final String LEFT_SIDE_PROPERTY = "com.eteks.sweethome3d.leftSideFinishBands";
  /** The property of a wall that describes the bands of its right side, at JSON format. */
  public static final String RIGHT_SIDE_PROPERTY = "com.eteks.sweethome3d.rightSideFinishBands";
  /** The count of bands that a side may have under its own finish. */
  public static final int    MAX_BANDS = 4;
  /** How far the surface of a band stands in front of the one of the band above it, in cm. */
  public static final float  BAND_THICKNESS = 0.4f;

  /**
   * A finish from the floor of a wall up to a height.
   */
  public static class Band {
    private final float       top;
    private final float       topAtEnd;
    private final Integer     color;
    private final HomeTexture texture;

    Band(float top, float topAtEnd, Integer color, HomeTexture texture) {
      this.top = top;
      this.topAtEnd = topAtEnd;
      this.color = color;
      this.texture = texture;
    }

    /**
     * Returns the height of the top of this band above the floor, at the start of its wall.
     */
    public float getTop() {
      return this.top;
    }

    /**
     * Returns the height of the top of this band above the floor, at the end of its wall.
     */
    public float getTopAtEnd() {
      return this.topAtEnd;
    }

    /**
     * Returns the color of this band, or <code>null</code> if it has a texture.
     */
    public Integer getColor() {
      return this.color;
    }

    /**
     * Returns the texture of this band, or <code>null</code> if it has a color.
     */
    public HomeTexture getTexture() {
      return this.texture;
    }
  }

  private WallFinishBands() {
    // Utility class
  }

  /**
   * Returns the name of the property that describes the bands of a side of a wall.
   */
  public static String getProperty(boolean leftSide) {
    return leftSide ? LEFT_SIDE_PROPERTY : RIGHT_SIDE_PROPERTY;
  }

  /**
   * Returns the name of the property of a wall that stores the image of the texture of a band.
   */
  static String getImageProperty(boolean leftSide, int index) {
    return getProperty(leftSide) + ".image" + index;
  }

  /**
   * Returns the bands of a side of <code>wall</code> from the lowest one to the highest one,
   * or an empty list if it has none. At most {@link #MAX_BANDS} bands are returned.
   */
  public static List<Band> getBands(Wall wall, boolean leftSide) {
    String description = wall.getProperty(getProperty(leftSide));
    if (description == null || description.length() == 0) {
      return Collections.emptyList();
    }
    List<Band> bands = new ArrayList<Band>();
    try {
      Object parsed = JSONParser.parse(description);
      if (parsed instanceof List) {
        int index = 0;
        for (Object item : (List<?>)parsed) {
          if (item instanceof Map && bands.size() < MAX_BANDS) {
            Map<?, ?> band = (Map<?, ?>)item;
            float top = getNumber(band, "to", Float.NaN);
            float topAtEnd = getNumber(band, "toAtEnd", top);
            Integer color = band.get("color") != null ? parseColor(band.get("color").toString()) : null;
            HomeTexture texture = null;
            Content image = wall.getContentProperty(getImageProperty(leftSide, index));
            if (band.get("texture") instanceof Map && image != null) {
              final Map<?, ?> textureDescription = (Map<?, ?>)band.get("texture");
              final Content textureImage = image;
              TextureImage catalogTexture = new TextureImage() {
                  public String getName() {
                    return String.valueOf(textureDescription.get("name"));
                  }

                  public String getCreator() {
                    return null;
                  }

                  public Content getImage() {
                    return textureImage;
                  }

                  public float getWidth() {
                    return getNumber(textureDescription, "width", 100);
                  }

                  public float getHeight() {
                    return getNumber(textureDescription, "height", 100);
                  }
                };
              texture = new HomeTexture(catalogTexture,
                  getNumber(textureDescription, "xOffset", 0), getNumber(textureDescription, "yOffset", 0),
                  getNumber(textureDescription, "angle", 0), getNumber(textureDescription, "scale", 1), true);
            }
            if (top > 0 && topAtEnd > 0 && (color != null || texture != null)) {
              bands.add(new Band(top, topAtEnd, color, texture));
            }
          }
          index++;
        }
      }
    } catch (RuntimeException ex) {
      // A description that can't be read: the side shows its own finish
      return Collections.emptyList();
    }
    return bands;
  }

  /**
   * Returns the color written as <code>RRGGBB</code>, or <code>null</code> if it can't be read.
   */
  static Integer parseColor(String text) {
    String value = text.trim();
    return AssistantCommandExecutor.parseColor(value.startsWith("#") ? value : "#" + value);
  }

  private static float getNumber(Map<?, ?> map, String key, float defaultValue) {
    Object value = map.get(key);
    return value instanceof Number && !Float.isNaN(((Number)value).floatValue())
        ? ((Number)value).floatValue() : defaultValue;
  }

  /**
   * Returns the description of a band stored in the property of a wall. The image
   * of a texture is stored apart, in the property {@link #getImageProperty(boolean, int)}.
   */
  static Map<String, Object> describe(float top, float topAtEnd, Integer color, HomeTexture texture) {
    Map<String, Object> band = new LinkedHashMap<String, Object>();
    band.put("to", Double.valueOf(top));
    if (topAtEnd != top) {
      band.put("toAtEnd", Double.valueOf(topAtEnd));
    }
    if (texture != null) {
      Map<String, Object> textureDescription = new LinkedHashMap<String, Object>();
      textureDescription.put("name", texture.getName());
      if (texture.getCatalogId() != null) {
        textureDescription.put("catalogId", texture.getCatalogId());
      }
      textureDescription.put("width", Double.valueOf(texture.getWidth()));
      textureDescription.put("height", Double.valueOf(texture.getHeight()));
      if (texture.getScale() != 1) {
        textureDescription.put("scale", Double.valueOf(texture.getScale()));
      }
      if (texture.getAngle() != 0) {
        textureDescription.put("angle", Double.valueOf(texture.getAngle()));
      }
      if (texture.getXOffset() != 0) {
        textureDescription.put("xOffset", Double.valueOf(texture.getXOffset()));
      }
      if (texture.getYOffset() != 0) {
        textureDescription.put("yOffset", Double.valueOf(texture.getYOffset()));
      }
      band.put("texture", textureDescription);
    } else if (color != null) {
      band.put("color", String.format("%06X", color.intValue() & 0xFFFFFF));
    }
    return band;
  }
}
