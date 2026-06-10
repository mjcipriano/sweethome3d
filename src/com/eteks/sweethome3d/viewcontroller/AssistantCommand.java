/*
 * AssistantCommand.java 10 June 2026
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
import java.util.List;
import java.util.Map;

/**
 * A single edit command produced by the AI design assistant, with typed access
 * to its parameters. Commands are parsed from the model reply by
 * {@link AssistantCommandParser}.
 * @author Sweet Home 3D
 */
public class AssistantCommand {
  private final String              action;
  private final Map<String, Object> parameters;

  AssistantCommand(String action, Map<String, Object> parameters) {
    this.action = action != null ? action.toLowerCase() : "";
    this.parameters = parameters;
  }

  /**
   * Returns the lower-case action name (e.g. <code>"add_furniture"</code>).
   */
  public String getAction() {
    return this.action;
  }

  /**
   * Returns the string value of the given parameter, or <code>null</code>.
   */
  public String getString(String name) {
    Object value = this.parameters.get(name);
    return value != null ? value.toString() : null;
  }

  /**
   * Returns the numeric value of the given parameter, or <code>defaultValue</code>.
   */
  public double getDouble(String name, double defaultValue) {
    Object value = this.parameters.get(name);
    if (value instanceof Number) {
      return ((Number)value).doubleValue();
    } else if (value instanceof String) {
      try {
        return Double.parseDouble(((String)value).trim());
      } catch (NumberFormatException ex) {
        // Fall through
      }
    }
    return defaultValue;
  }

  /**
   * Returns the list of strings of the given parameter, never <code>null</code>.
   */
  public List<String> getStringList(String name) {
    List<String> result = new ArrayList<String>();
    Object value = this.parameters.get(name);
    if (value instanceof List) {
      for (Object item : (List<?>)value) {
        if (item != null) {
          result.add(item.toString());
        }
      }
    } else if (value instanceof String) {
      result.add((String)value);
    }
    return result;
  }

  /**
   * Returns a list of <code>[x, y]</code> points for the given parameter (used
   * for room/wall polygons), never <code>null</code>.
   */
  public List<float []> getPoints(String name) {
    List<float []> points = new ArrayList<float []>();
    Object value = this.parameters.get(name);
    if (value instanceof List) {
      for (Object item : (List<?>)value) {
        if (item instanceof List && ((List<?>)item).size() >= 2) {
          List<?> coordinates = (List<?>)item;
          if (coordinates.get(0) instanceof Number && coordinates.get(1) instanceof Number) {
            points.add(new float [] {
                ((Number)coordinates.get(0)).floatValue(),
                ((Number)coordinates.get(1)).floatValue()});
          }
        }
      }
    }
    return points;
  }
}
