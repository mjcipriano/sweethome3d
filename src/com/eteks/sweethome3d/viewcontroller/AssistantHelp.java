/*
 * AssistantHelp.java 23 nov. 2026
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The help of the edit commands, read from their reference: the list of the commands by area,
 * the syntax of one command, and a search of a word in all of them.
 */
public final class AssistantHelp {
  private static final String [][] GROUPS = {
      {"Rooms and walls", "add_rectangular_room add_room_shape add_room add_wall modify_room modify_wall open_to_above"},
      {"Wall finishes, openings, headers and railings", "paint_rooms add_opening add_header add_soffit add_railing modify_railing"},
      {"Levels and stairs", "add_level select_level modify_level delete_level merge_levels move_to_level add_stairs"},
      {"Furniture, doors and windows", "search_catalog add_furniture add_door_or_window modify_furniture replace_furniture duplicate "
          + "create_furniture modify_furniture_parts add_picture import_furniture add_to_catalog export_furniture_library "
          + "import_furniture_library furnish_room suggest_layout"},
      {"Changing items", "move rotate resize set_elevation set_visible rename delete select set_plan_visibility describe_item "
          + "reduce_detail restore_detail group align distribute mirror lock add_tags hide_layer"},
      {"Colors, textures and materials", "set_color set_texture apply_style import_texture list_materials set_material"},
      {"Roofs", "add_roof add_dormer modify_dormer remove_dormer modify_roof set_gutters fit_walls_to_roof headroom auto_roof"},
      {"Grading and terrain measures", "add_terrain_patch shift_terrain reseat_all modify_terrain_area terrain_areas add_pad flatten_area smooth_area add_berm grade_path extend_terrain "
          + "cut_fill_report contours spot_elevations slope_map site_report export_site trees_from_lidar"},
      {"Ground and site", "add_slab set_terrain set_site_defaults add_polyline add_property_line add_setback modify_polyline set_compass "
          + "set_georeference import_parcel get_coordinates geo_to_plan"},
      {"Fences, walls and gates", "add_fence add_gate modify_fence extend_fence join_fences split_fence trim_fence offset_fence "
          + "convert_to_fence fence_report"},
      {"Garden", "add_hedge add_path add_planting_bed"},
      {"Outdoor structures and site items", "add_deck add_patio add_pergola add_shed add_playset add_pool add_driveway "
          + "add_retaining_wall add_steps_on_slope add_drainage add_utility add_raised_bed add_callout"},
      {"Neighbors and context", "add_context_building add_context_vehicle"},
      {"Photos", "import_photos add_photo photo_viewpoint refine_photo_pose locate_from_photos"},
      {"GIS files and web sources", "import_geojson import_buildings import_osm import_parcels fetch_elevation set_basemap web_sources"},
      {"Trees and plants", "add_tree add_shrub add_trees import_trees update_trees relabel_trees grow_trees set_season set_plant_style forest_fill"},
      {"Labels, dimensions and plan", "add_label add_dimension auto_dimensions set_background_image"},
      {"Lights, sun, doors and camera", "set_light set_sun set_environment open_door set_camera store_point_of_view"},
      {"Checks, reports and schedules", "check_home fix_home home_report set_unit_prices export_elevation measure door_schedule "
          + "window_schedule room_schedule schedule export_schedule add_symbol add_symbols symbol_schedule list_symbols "
          + "framing_estimate find_duplicates find_floating optimize_models"},
      {"Undo groups and help", "begin_group help get_capabilities"}};

  /**
   * A command of the reference.
   */
  private static final class Entry {
    final String name;
    final String syntax;
    final String description;

    Entry(String name, String syntax, String description) {
      this.name = name;
      this.syntax = syntax;
      this.description = description;
    }
  }

  private AssistantHelp() {
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"help\"}  (the list of every command by area, with a line on each. "
        + "{\"action\":\"help\",\"command\":\"add_fence\"} gives the syntax of a command, its parameters and the commands related to it, "
        + "and with \"style\":\"chain_link\" the options of a style of fence; {\"action\":\"help\",\"search\":\"gate\"} gives the commands "
        + "and the parameters that mention a word. Changes nothing)\n"
        + "  {\"action\":\"get_capabilities\"}  (the version of the application, what is installed - styles of fences, types of gates, "
        + "coordinate systems, formats read and written - and the limits: commands per call, points of a terrain, parts of a piece, "
        + "sizes of files. Changes nothing)\n";
  }

  private static List<Entry> getEntries() {
    List<Entry> entries = new ArrayList<Entry>();
    String reference = HomeAssistantContext.getCommandReference();
    int end = reference.indexOf("\nTo MODIFY items");
    String marker = "\n  {\"action\":\"";
    int start = reference.indexOf(marker);
    while (start >= 0) {
      int next = reference.indexOf(marker, start + 1);
      String text = reference.substring(start + 3, next >= 0 ? next : reference.length());
      if (next < 0) {
        // The last command ends where the rules start
        int rules = text.indexOf("\nRules:");
        if (rules >= 0) {
          text = text.substring(0, rules);
        }
      }
      if (end > start && (next < 0 || next > end)) {
        // The explanations about ids between two commands aren't a part of the command before them
        int explanations = text.indexOf("\nTo MODIFY items");
        if (explanations >= 0) {
          text = text.substring(0, explanations);
        }
      }
      text = text.replace('\n', ' ').trim();
      String name = text.substring("{\"action\":\"".length(), text.indexOf('"', "{\"action\":\"".length()));
      int descriptionStart = text.indexOf("}  (");
      String syntax = descriptionStart >= 0 ? text.substring(0, descriptionStart + 1) : text;
      String description = descriptionStart >= 0 ? text.substring(descriptionStart + 4) : "";
      if (description.endsWith(")")) {
        description = description.substring(0, description.length() - 1);
      }
      entries.add(new Entry(name, syntax, description));
      start = next;
    }
    return entries;
  }

  private static String getGroup(String name) {
    for (String [] group : GROUPS) {
      if (Arrays.asList(group [1].split(" ")).contains(name)) {
        return group [0];
      }
    }
    return "Other commands";
  }

  /**
   * Returns the first words of a description, cut at the end of a clause.
   */
  private static String summarize(Entry entry) {
    String text = entry.description.length() > 0 ? entry.description : entry.syntax;
    int cut = text.length();
    for (String separator : new String [] {". ", ": ", "; "}) {
      int index = text.indexOf(separator);
      if (index > 20 && index < cut) {
        cut = index;
      }
    }
    text = text.substring(0, cut);
    return text.length() > 150 ? text.substring(0, 147) + "..." : text;
  }

  /**
   * Returns the help asked by a command.
   * @param command the name of a command, or <code>null</code>
   * @param search  a word to look for, or <code>null</code>
   * @param style   a style of fence, or <code>null</code>
   */
  /** The tools of the MCP server, which <code>help</code> tells from the commands. */
  private static final List<String> TOOLS = java.util.Arrays.asList("get_home", "list_catalog", "search_catalog", "search_textures",
      "check_home", "get_report", "get_notes", "update_note", "get_plan_image", "get_3d_image", "get_3d_info", "get_capabilities",
      "get_sun_study_image", "sun_study", "get_catalog_image", "export_blender", "apply_commands", "get_elevation_image", "undo", "redo",
      "get_history", "get_changes", "save_version", "list_versions", "compare_versions", "restore_version", "save_home", "list_homes",
      "open_home", "new_home", "switch_home", "export_home", "add_note", "start_visit");

  public static String getHelp(String command, String search, String style) {
    List<Entry> entries = getEntries();
    if (search != null && search.trim().length() > 0) {
      return search(entries, search.trim());
    } else if (command != null && command.trim().length() > 0) {
      return describe(entries, command.trim().toLowerCase(Locale.ENGLISH), style);
    }
    StringBuilder text = new StringBuilder("Commands of apply_commands, by area. {\"action\":\"help\",\"command\":\"<name>\"} gives "
        + "the syntax of one, {\"action\":\"help\",\"search\":\"<word>\"} finds a word.");
    Map<String, List<Entry>> groups = new LinkedHashMap<String, List<Entry>>();
    for (String [] group : GROUPS) {
      groups.put(group [0], new ArrayList<Entry>());
    }
    groups.put("Other commands", new ArrayList<Entry>());
    for (Entry entry : entries) {
      groups.get(getGroup(entry.name)).add(entry);
    }
    for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
      if (group.getValue().isEmpty()) {
        continue;
      }
      text.append("\n\n").append(group.getKey()).append(':');
      for (Entry entry : group.getValue()) {
        text.append("\n  ").append(entry.name).append(" - ").append(summarize(entry));
      }
    }
    return text.toString();
  }

  private static String describe(List<Entry> entries, String command, String style) {
    if (command.equals("add_fence") && style != null) {
      AssistantFenceBuilder.Style fenceStyle = AssistantFenceBuilder.getStyle(style);
      if (fenceStyle == null) {
        return "No style of fence \"" + style + "\". The styles: " + AssistantFenceBuilder.describeStyles(false);
      }
      return "Style of fence " + fenceStyle.name + " (" + fenceStyle.family + "): " + fenceStyle.description
          + ".\nIts options and what they are when the command doesn't give them (sizes in cm): " + fenceStyle.defaults
          + ".\n{\"action\":\"help\",\"command\":\"add_fence\"} gives the options of every style.";
    }
    Entry found = null;
    for (Entry entry : entries) {
      if (entry.name.equals(command)) {
        found = entry;
      }
    }
    if (found == null) {
      // A command told about in the description of another one
      String result = search(entries, command);
      if (TOOLS.contains(command)) {
        return "\"" + command + "\" is a tool of the MCP server, beside apply_commands, and not one of its commands: call the tool "
            + command + " itself, its parameters are in the list of tools."
            + (result.startsWith("Nothing") ? "" : " Among the commands: " + result);
      }
      return result.startsWith("Nothing") ? "No command \"" + command + "\". {\"action\":\"help\"} lists the commands."
          : "\"" + command + "\" has no entry of its own. " + result;
    }
    StringBuilder text = new StringBuilder(found.name + "\nSyntax: " + found.syntax);
    if (found.description.length() > 0) {
      text.append("\n").append(found.description);
    }
    if (found.name.equals("add_fence")) {
      text.append("\nStyles.\n").append(AssistantFenceBuilder.describeStyles(true));
      text.append("\nGate types: ").append(Arrays.toString(AssistantFenceBuilder.GATE_TYPES));
      text.append("\n{\"action\":\"help\",\"command\":\"add_fence\",\"style\":\"<style>\"} gives the options of a style.");
    }
    String group = getGroup(found.name);
    StringBuilder related = new StringBuilder();
    for (Entry entry : entries) {
      if (entry != found && getGroup(entry.name).equals(group)) {
        related.append(related.length() > 0 ? ", " : "").append(entry.name);
      }
    }
    if (related.length() > 0) {
      text.append("\nRelated (").append(group).append("): ").append(related);
    }
    text.append("\nUnits: centimeters and degrees; x to the right of the plan, y downward; colors written #RRGGBB.");
    return text.toString();
  }

  private static String search(List<Entry> entries, String word) {
    String lowerWord = word.toLowerCase(Locale.ENGLISH);
    StringBuilder text = new StringBuilder();
    int count = 0;
    for (Entry entry : entries) {
      String all = entry.syntax + " " + entry.description;
      int index = all.toLowerCase(Locale.ENGLISH).indexOf(lowerWord);
      if (index < 0 && !entry.name.contains(lowerWord)) {
        continue;
      }
      count++;
      text.append("\n  ").append(entry.name).append(": ");
      if (index < 0 || index < entry.syntax.length()) {
        text.append(summarize(entry));
      } else {
        int from = Math.max(0, index - 70);
        int to = Math.min(all.length(), index + lowerWord.length() + 90);
        text.append(from > 0 ? "..." : "").append(all.substring(from, to)).append(to < all.length() ? "..." : "");
      }
    }
    StringBuilder styles = new StringBuilder();
    for (AssistantFenceBuilder.Style style : AssistantFenceBuilder.getStyles()) {
      if (style.name.contains(lowerWord) || style.description.toLowerCase(Locale.ENGLISH).contains(lowerWord)
          || style.defaults.containsKey(lowerWord)) {
        styles.append(styles.length() > 0 ? ", " : "").append(style.name);
      }
    }
    if (count == 0 && styles.length() == 0) {
      return "Nothing in the commands mentions \"" + word + "\". {\"action\":\"help\"} lists the commands.";
    }
    return (count > 0 ? count + (count == 1 ? " command mentions \"" : " commands mention \"") + word + "\":" + text : "")
        + (styles.length() > 0 ? (count > 0 ? "\n" : "") + "Styles of fence with \"" + word + "\": " + styles : "");
  }

  /**
   * Returns what this version can do and its limits.
   */
  public static String getCapabilities() {
    String version = null;
    try {
      Package applicationPackage = AssistantHelp.class.getPackage();
      version = applicationPackage != null ? applicationPackage.getImplementationVersion() : null;
    } catch (RuntimeException ex) {
      // Unknown version
    }
    StringBuilder styles = new StringBuilder();
    for (AssistantFenceBuilder.Style style : AssistantFenceBuilder.getStyles()) {
      styles.append(styles.length() > 0 ? ", " : "").append(style.name);
    }
    return "Sweet Home 3D" + (version != null ? " " + version : "") + ", commands of apply_commands: " + getEntries().size()
        + " documented by {\"action\":\"help\"}."
        + "\nFences: " + AssistantFenceBuilder.getStyles().size() + " styles (" + styles + "); gates: "
        + Arrays.toString(AssistantFenceBuilder.GATE_TYPES) + "."
        + "\nTerrain: points of known height given to set_terrain, " + AssistantShapeCommands.getMaxTerrainPoints()
        + " at most, or a raster, an Esri ASCII grid or a text of x y z lines thinned to that count; graded by add_pad, "
        + "grade_path, add_berm, add_swale, flatten_area and smooth_area; measured by cut_fill_report, contours, spot_elevations and "
        + "slope_map. GeoTIFF (classic, not compressed or Deflate or LZW) and LAS files are read; BigTIFF and LAZ aren't."
        + "\nGeoreference: one point with a north angle; coordinate systems EPSG:4326, 4269, 3857, the UTM zones (326xx, 327xx, 269xx) "
        + "and the State Plane zones of Georgia (2240, 2239, 26967, 26966). GIS files read: GeoJSON, Esri JSON, KML, shapefiles, "
        + "text DXF and OpenStreetMap data. Web services, when the user allows them: USGS 3DEP heights, Overpass, ArcGIS parcel "
        + "layers, Esri, USGS and OpenStreetMap tiles. No point cloud, no Bing or Google pictures."
        + "\nPlants: " + HomeAssistantContext.getPlantSpecies(false).size() + " species of trees and "
        + HomeAssistantContext.getPlantSpecies(true).size() + " of shrubs built by add_tree and add_shrub (list_species), with seasons, "
        + "growth, and 3 levels of detail (2000, 10000 and more triangles at most); 3000 plants per call. Catalog pieces are placed by "
        + "add_trees with names. trees_from_lidar finds trees in a LAS point cloud."
        + "\nModels read: OBJ, DAE, 3DS, KMZ, ZIP. Written by export_home: PDF, SVG, OBJ, CSV, a 3D web page, a panorama."
        + "\nLimits: " + AssistantCommandExecutor.MAX_COMMANDS_PER_TURN + " commands per call (" + AssistantCommandExecutor.MAX_COMMANDS_PER_FILE
        + " from a file); coordinates up to " + (int)AssistantCommandExecutor.MAX_COORDINATE_CM + " cm and sizes up to "
        + (int)AssistantCommandExecutor.MAX_SIZE_CM + " cm; 500 points and 1500 spans per fence; "
        + AssistantFurnitureBuilder.MAX_PARTS + " parts per piece built by create_furniture; files up to 20 MB.";
  }
}
