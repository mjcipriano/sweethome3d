/*
 * AssistantFenceBuilder.java 23 nov. 2026
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the model of a fence, of a garden wall or of a barrier from its description: a line of points,
 * a style and its options, and the gates that open it. The fence is cut in spans between posts,
 * each span following the ground.
 */
final class AssistantFenceBuilder {
  private static final int   MAX_POINTS = 500;
  private static final int   MAX_BOARDS = 4000;
  private static final int   MAX_SPANS = 1500;
  private static final float BAG_VOLUME = 0.0106f; // m3 of concrete given by a bag of 50 lb

  /**
   * What a fence is built on.
   */
  interface Site {
    /**
     * Returns the height of the ground at a point, above the floor of the level of the fence.
     */
    float getGroundHeight(float x, float y);

    /**
     * Returns <code>true</code> if the ground is a terrain with slopes.
     */
    boolean hasTerrain();

    /**
     * Returns the material showing the catalog texture with the given name, or <code>null</code>.
     */
    AssistantMeshBuilder.Material getTexture(AssistantMeshBuilder mesh, String texture, float scale, int color);
  }

  /** What fills the spans. */
  private enum Infill {MESH, STRANDS, PICKET, SOLID, DOUBLE_BOARDS, HSLAT, BASKET, LATTICE, LATTICE_TOP, RAILS, CROSSBUCK,
                       ZIGZAG, BARS, SHEET, GLASS, GABION, WALL, RETAINING, PIER_PANEL, BOLLARDS, ROLL, PALISADE, LEAFY,
                       HAHA, HEDGE}

  /**
   * A style of fence.
   */
  static final class Style {
    final String              name;
    final String              family;
    final String              description;
    private final Infill      infill;
    final Map<String, Object> defaults = new LinkedHashMap<String, Object>();

    private Style(String name, String family, Infill infill, String description, String defaults) {
      this.name = name;
      this.family = family;
      this.infill = infill;
      this.description = description;
      if (defaults.length() > 0) {
        for (String entry : defaults.split(",")) {
          int equal = entry.indexOf('=');
          String value = entry.substring(equal + 1);
          Object parsed;
          if (value.equals("true") || value.equals("false")) {
            parsed = Boolean.valueOf(value);
          } else {
            try {
              parsed = Double.valueOf(value);
            } catch (NumberFormatException ex) {
              parsed = value;
            }
          }
          this.defaults.put(entry.substring(0, equal), parsed);
        }
      }
    }

    /**
     * Returns <code>true</code> if a hedge is planted rather than a fence built.
     */
    boolean isHedge() {
      return this.infill == Infill.HEDGE;
    }
  }

  private static final Map<String, Style>  STYLES = new LinkedHashMap<String, Style>();
  private static final Map<String, String> ALIASES = new LinkedHashMap<String, String>();

  private static void add(String family, String names, Infill infill, String description, String defaults) {
    String [] allNames = names.split("\\|");
    STYLES.put(allNames [0], new Style(allNames [0], family, infill, description, defaults));
    for (int i = 1; i < allNames.length; i++) {
      ALIASES.put(allNames [i], allNames [0]);
    }
  }

  static {
    String wire = "Wire and mesh";
    add(wire, "chain_link|chainlink|wire|mesh", Infill.MESH, "diamond mesh on steel posts, with a top rail",
        "height=120,post_spacing=300,post_size=5,post_shape=round,pattern=diamond,mesh_size=5,top_rail=true,"
        + "color=#A9ADB0,terminal_scale=1.5,post_cap=ball,finish=galvanized");
    add(wire, "welded_wire", Infill.MESH, "rectangular welded grid, alone or in a wood or metal frame",
        "height=120,post_spacing=240,post_size=9,pattern=grid,cell_w=5,cell_h=10,color=#6E7275,post_color=#8B6B47,frame=none");
    add(wire, "hog_panel|cattle_panel", Infill.MESH, "heavy welded panels between wood posts or T-posts",
        "height=127,post_spacing=240,post_size=10,pattern=grid,cell_w=15,cell_h=15,color=#8E9398,post_color=#8B6B47,frame=wood,wire=0.12");
    add(wire, "field_fence|woven_wire", Infill.MESH, "woven farm wire on T-posts or wood posts",
        "height=120,post_spacing=300,post_size=4,post_shape=t_post,pattern=grid,cell_w=15,cell_h=12,color=#8E9398,post_color=#3F5B3A,wire=0.08");
    add(wire, "deer_fence", Infill.MESH, "tall black mesh that deer don't jump, on tall posts",
        "height=230,post_spacing=450,post_size=5,post_shape=round,pattern=grid,cell_w=5,cell_h=5,color=#2B2B2B,wire=0.08");
    add(wire, "garden_wire_tpost", Infill.MESH, "green steel T-posts with a wire mesh, around a vegetable garden",
        "height=120,post_spacing=240,post_size=4,post_shape=t_post,pattern=grid,cell_w=5,cell_h=10,color=#5E6B5E,post_color=#2F5D3A,wire=0.1");
    add(wire, "poultry_netting|hexagonal|chicken_wire", Infill.MESH, "chicken wire on posts or in a wood frame",
        "height=120,post_spacing=240,post_size=6,pattern=hex,cell_w=5,cell_h=5,color=#9AA0A0,post_color=#8B6B47,wire=0.1");
    add(wire, "electric", Infill.STRANDS, "1 to 6 strands of polywire or tape on thin insulated posts",
        "height=110,post_spacing=400,post_size=3,post_shape=round,strands=3,strand=1.2,color=#F2F2EA,post_color=#F0F0F0,post_extra=4");
    add(wire, "barbed_wire", Infill.STRANDS, "3 to 5 strands of barbed wire on wood posts or T-posts",
        "height=120,post_spacing=400,post_size=10,strands=4,strand=0.6,color=#6F6B62,post_color=#8B6B47");
    add(wire, "high_tensile", Infill.STRANDS, "smooth tight wires with braced corners",
        "height=130,post_spacing=600,post_size=12,strands=6,strand=0.5,color=#9AA0A0,post_color=#8B6B47,terminal_scale=1.4");
    add(wire, "cable_rail", Infill.STRANDS, "horizontal stainless cables between wood or metal posts, under a top rail",
        "height=100,post_spacing=150,post_size=9,strands=9,strand=0.6,color=#C9CDD0,post_color=#8B6B47,top_rail=true");
    add(wire, "security_mesh|358_anti_climb|anti_climb", Infill.MESH, "very fine welded mesh that can't be climbed, with an optional topping",
        "height=240,post_spacing=250,post_size=8,pattern=fine,cell_w=7.6,cell_h=2.6,color=#3C4043,wire=0.35,post_cap=flat");

    String wood = "Wood";
    add(wood, "picket", Infill.PICKET, "pickets with a gap on two rails",
        "height=120,post_spacing=240,post_size=9,color=#F2EFE6,picket_width=7,gap=5,top=dog_ear");
    add(wood, "privacy_solid|stockade|privacy|panel|solid", Infill.SOLID, "vertical boards without gap",
        "height=180,post_spacing=240,post_size=9,color=#9A7B55,top=flat");
    add(wood, "board_on_board", Infill.DOUBLE_BOARDS, "overlapping vertical boards, the same on both sides",
        "height=180,post_spacing=240,post_size=9,color=#9A7B55,picket_width=14,layers=same");
    add(wood, "shadowbox", Infill.DOUBLE_BOARDS, "boards that alternate on each side of the rails",
        "height=180,post_spacing=240,post_size=9,color=#9A7B55,picket_width=14,layers=both");
    add(wood, "horizontal_slat", Infill.HSLAT, "modern horizontal boards with a thin gap",
        "height=180,post_spacing=180,post_size=9,color=#8A6A45,slat_height=14,gap=1.5");
    add(wood, "louvered", Infill.HSLAT, "angled louvers that hide the view and let the air through",
        "height=180,post_spacing=180,post_size=9,color=#8A6A45,slat_height=9,gap=3,slat_thickness=6");
    add(wood, "basketweave", Infill.BASKET, "thin boards woven between the posts",
        "height=180,post_spacing=180,post_size=9,color=#A07F58");
    add(wood, "lattice_top", Infill.LATTICE_TOP, "a privacy fence with a band of lattice on top",
        "height=180,post_spacing=240,post_size=9,color=#9A7B55,lattice_height=30,lattice_pattern=square");
    add(wood, "lattice_panel|lattice", Infill.LATTICE, "a full lattice, as under a deck or for a screen",
        "height=150,post_spacing=240,post_size=9,color=#C9B48F,lattice_pattern=diagonal");
    add(wood, "split_rail", Infill.RAILS, "rough split rails mortised into the posts",
        "height=110,post_spacing=300,post_size=13,color=#8A7A66,rail_count=3,rail_height=10,rail_width=9,post_extra=8");
    add(wood, "post_and_rail|ranch_rail|rail|ranch", Infill.RAILS, "2 to 4 planed rails nailed on the posts",
        "height=120,post_spacing=240,post_size=10,color=#8B6B47,rail_count=3,rail_height=9,rail_width=4");
    add(wood, "post_and_board", Infill.RAILS, "wide boards on the posts, as around a horse farm",
        "height=140,post_spacing=240,post_size=12,color=#2B2B2B,rail_count=4,rail_height=14,rail_width=2.5,rail_offset=7");
    add(wood, "crossbuck|x_rail", Infill.CROSSBUCK, "a cross between the top and the bottom rails",
        "height=120,post_spacing=240,post_size=10,color=#F2EFE6,rail_height=9,rail_width=4");
    add(wood, "zigzag_rail|worm_fence", Infill.ZIGZAG, "rails stacked in a zigzag, without posts",
        "height=110,post_spacing=300,color=#8A7A66,posts=false,rail_count=5");
    add(wood, "wattle|woven_branch", Infill.ROLL, "woven saplings between stakes",
        "height=120,post_spacing=60,post_size=4,post_shape=round,color=#7A5E3F,pattern=wattle,thickness=5,post_extra=4");
    add(wood, "bamboo", Infill.ROLL, "bamboo canes, rolled or in panels",
        "height=180,post_spacing=240,post_size=8,color=#C8AD6A,pattern=bamboo,thickness=3");
    add(wood, "reed|brush|heather_screen", Infill.ROLL, "a rolled natural screen of reed, brush or heather",
        "height=180,post_spacing=200,post_size=6,color=#A88F5F,pattern=reed,thickness=2");
    add(wood, "log_palisade", Infill.PALISADE, "upright logs with pointed tops",
        "height=200,color=#7B6248,posts=false,picket_width=15,top=pointed");
    add(wood, "snow_fence", Infill.PICKET, "thin slats held by wires, unrolled for the winter",
        "height=120,post_spacing=250,post_size=4,post_shape=t_post,color=#A9502E,post_color=#3F5B3A,picket_width=4,gap=5,top=flat,rail_height=0.6,rail_width=0.6");

    String metal = "Metal and composite";
    add(metal, "ornamental_steel|wrought_iron", Infill.BARS, "steel pickets with finials between rails",
        "height=150,post_spacing=240,post_size=6,color=#1E1E1E,picket_spacing=11,finials=spear,rail_count=3,post_cap=ball");
    add(metal, "aluminum_ornamental", Infill.BARS, "lighter aluminum pickets, with pressed points or a flat top",
        "height=140,post_spacing=180,post_size=5,color=#2A2A2A,picket_spacing=10,finials=none,rail_count=3,post_cap=pyramid,picket_size=1.6");
    add(metal, "pool_code", Infill.BARS, "an ornamental fence that a child can't climb or squeeze through: "
        + "gaps under 10 cm, 122 cm high at least, a gate that closes by itself",
        "height=137,post_spacing=180,post_size=5,color=#1E1E1E,picket_spacing=9,finials=none,rail_count=2,post_cap=pyramid,picket_size=1.6,clearance=5");
    add(metal, "corrugated_metal", Infill.SHEET, "corrugated sheets in a wood or steel frame",
        "height=180,post_spacing=240,post_size=9,color=#9EA3A6,post_color=#6B4F34,pattern=corrugated,frame=wood");
    add(metal, "steel_panel|laser_cut", Infill.SHEET, "sheets of steel, plain or cut with a pattern",
        "height=180,post_spacing=180,post_size=6,color=#5C4033,post_color=#2A2A2A,pattern=none,post_cap=flat");
    add(metal, "perforated_metal", Infill.SHEET, "sheets pierced with holes",
        "height=150,post_spacing=180,post_size=6,color=#8E9398,post_color=#3C4043,pattern=holes,open_area=40,post_cap=flat");
    add(metal, "gabion", Infill.GABION, "wire baskets filled with stones",
        "height=100,post_spacing=200,color=#8C8478,posts=false,basket_size=50,clearance=0");
    add(metal, "vinyl_privacy", Infill.SOLID, "white PVC boards without gap, with routed posts and caps",
        "height=180,post_spacing=240,post_size=12,color=#F5F5F0,top=flat,post_cap=pyramid,cap_rail=true");
    add(metal, "vinyl_picket", Infill.PICKET, "white PVC pickets",
        "height=120,post_spacing=240,post_size=10,color=#F5F5F0,picket_width=7,gap=6,top=dog_ear,post_cap=pyramid");
    add(metal, "vinyl_rail|paddock_vinyl_rail", Infill.RAILS, "white PVC rails slid in the posts",
        "height=137,post_spacing=240,post_size=12,color=#F5F5F0,rail_count=3,rail_height=14,rail_width=4,post_cap=pyramid");
    add(metal, "vinyl_semi_privacy", Infill.PICKET, "white PVC boards with a thin gap",
        "height=180,post_spacing=240,post_size=12,color=#F5F5F0,picket_width=14,gap=2,top=flat,post_cap=pyramid");
    add(metal, "composite_privacy", Infill.SOLID, "composite boards between aluminum posts",
        "height=180,post_spacing=180,post_size=8,color=#6B5B4B,post_color=#3C4043,top=flat,boards=horizontal,post_cap=flat");
    add(metal, "composite_slat", Infill.HSLAT, "composite slats with gaps between aluminum posts",
        "height=180,post_spacing=180,post_size=8,color=#6B5B4B,post_color=#3C4043,slat_height=12,gap=2,post_cap=flat");
    add(metal, "glass_panel", Infill.GLASS, "panes of glass, framed or frameless, around a pool or a deck",
        "height=122,post_spacing=150,post_size=5,color=#BFD9E0,post_color=#C9CDD0,frame=frameless,post_cap=none,clearance=8");

    String masonry = "Masonry and solid walls";
    add(masonry, "brick_wall", Infill.WALL, "a brick wall with a coping, and piers if asked",
        "height=180,post_spacing=240,color=#9C4A35,posts=false,thickness=22,pattern=brick_running,coping=true,clearance=0");
    add(masonry, "stone_wall|dry_stack", Infill.WALL, "a wall of fieldstone, ashlar or flagstone",
        "height=120,post_spacing=240,color=#8C8478,posts=false,thickness=40,pattern=fieldstone,coping=false,clearance=0");
    add(masonry, "stucco_wall", Infill.WALL, "blocks covered with stucco, with a cap",
        "height=180,post_spacing=240,color=#E0D5C0,posts=false,thickness=20,pattern=stucco,coping=true,clearance=0");
    add(masonry, "cmu_wall|split_face_block", Infill.WALL, "a wall of concrete blocks",
        "height=180,post_spacing=240,color=#A9A49A,posts=false,thickness=20,pattern=block,coping=true,clearance=0");
    add(masonry, "concrete_panel|precast", Infill.WALL, "precast concrete panels between concrete posts",
        "height=180,post_spacing=240,post_size=15,color=#B4B1AA,thickness=8,pattern=stucco,coping=false,clearance=0,post_cap=flat");
    add(masonry, "pier_and_panel", Infill.PIER_PANEL, "masonry piers with another style between them, given by \"infill\"",
        "height=150,post_spacing=240,post_size=40,color=#9C4A35,post_pattern=brick_running,post_cap=flat,post_extra=15");
    add(masonry, "retaining_wall", Infill.RETAINING, "a wall that holds the ground of its higher side",
        "height=15,post_spacing=200,color=#8C8478,posts=false,thickness=30,pattern=ashlar,coping=true,clearance=0");

    String living = "Living barriers";
    add(living, "hedge", Infill.HEDGE, "a hedge, planted by add_hedge along the same line", "height=150");
    add(living, "espalier", Infill.LEAFY, "trees trained flat on wires between posts",
        "height=180,post_spacing=300,post_size=9,color=#3F6B35,post_color=#8B6B47,strands=4");
    add(living, "living_willow", Infill.LATTICE, "willow rods planted in a diamond pattern, which take root",
        "height=150,post_spacing=300,color=#6E7B3A,posts=false,lattice_pattern=diagonal,lattice_cell=25,frame=none");

    String specialty = "Specialty";
    add(specialty, "temporary_construction", Infill.MESH, "steel panels on block feet around a work site",
        "height=180,post_spacing=290,post_size=4,post_shape=round,pattern=grid,cell_w=5,cell_h=20,color=#A9ADB0,frame=metal,feet=true,wire=0.12");
    add(specialty, "silt_fence", Infill.SHEET, "black fabric on stakes that holds the mud of a work site",
        "height=60,post_spacing=180,post_size=4,color=#1F1F1F,post_color=#A58A63,pattern=none,thickness=0.5,post_extra=15,clearance=0");
    add(specialty, "orange_safety_mesh", Infill.MESH, "orange plastic mesh on T-posts",
        "height=120,post_spacing=250,post_size=4,post_shape=t_post,pattern=grid,cell_w=4,cell_h=8,color=#F26B1D,post_color=#3F5B3A,wire=0.3");
    add(specialty, "dog_run_panel", Infill.MESH, "modular welded panels in a steel frame",
        "height=180,post_spacing=150,post_size=4,post_shape=round,pattern=grid,cell_w=5,cell_h=10,color=#3C4043,frame=metal,wire=0.12");
    add(specialty, "pipe_fence", Infill.RAILS, "welded steel pipes, as around a corral",
        "height=140,post_spacing=240,post_size=7,post_shape=round,color=#5A5A5A,rail_count=4,rail_height=6,rail_width=6");
    add(specialty, "guardrail", Infill.RAILS, "the steel rail along a road",
        "height=75,post_spacing=190,post_size=10,post_shape=i_beam,color=#B0B4B7,rail_count=1,rail_height=31,rail_width=6,rail_offset=8,post_extra=0");
    add(specialty, "ha_ha", Infill.HAHA, "a wall sunk in the ground, which keeps animals out without hiding the view",
        "height=120,post_spacing=240,color=#8C8478,posts=false,thickness=30,pattern=ashlar");
    add(specialty, "bollards", Infill.BOLLARDS, "bollards in a row: steel, concrete or lighted",
        "height=90,post_spacing=150,post_size=15,post_shape=round,color=#5A5A5A,type=steel,post_extra=0,post_cap=flat");
  }

  /**
   * Returns the style with the given name or an alias of it, or <code>null</code>.
   */
  static Style getStyle(String name) {
    if (name == null) {
      return STYLES.get("picket");
    }
    String key = name.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_').replace('-', '_');
    if (ALIASES.containsKey(key)) {
      key = ALIASES.get(key);
    }
    return STYLES.get(key);
  }

  /**
   * Returns the styles in the order of their families.
   */
  static List<Style> getStyles() {
    return new ArrayList<Style>(STYLES.values());
  }

  /**
   * Returns the list of the styles by family, with what each one is.
   */
  static String describeStyles(boolean descriptions) {
    StringBuilder text = new StringBuilder();
    String family = null;
    for (Style style : STYLES.values()) {
      if (!style.family.equals(family)) {
        family = style.family;
        text.append(text.length() > 0 ? (descriptions ? "\n" : "; ") : "").append(family).append(": ");
      } else {
        text.append(descriptions ? "; " : ", ");
      }
      text.append(style.name);
      if (descriptions) {
        text.append(" (").append(style.description).append(')');
      }
    }
    return text.toString();
  }

  /** The types of gates. */
  static final String [] GATE_TYPES = {"single_swing", "double_swing", "sliding", "rolling", "cantilever", "bifold",
      "vertical_lift", "arm_barrier", "pedestrian_turnstile", "arched_top", "drive_arch", "arbor_gate",
      "walk_through_kissing_gate", "cattle_guard", "gap"};

  /**
   * The options of a fence or of a gate: what the description gives, then what the style gives.
   */
  private static final class Options {
    private final List<Map<?, ?>> maps = new ArrayList<Map<?, ?>>();
    private final Set<String>     used;
    private final Style           style;

    Options(Map<?, ?> spec, Style style, Set<String> used) {
      this.maps.add(spec);
      this.maps.add(style.defaults);
      this.style = style;
      this.used = used;
    }

    /**
     * Options that read <code>map</code> first, then the ones of <code>parent</code> if they share its style.
     */
    Options(Map<?, ?> map, Style style, Options parent) {
      this.maps.add(map);
      if (parent.style == style) {
        this.maps.add(parent.maps.get(0));
      } else {
        // The colors asked for the fence are the ones of its gates
        Map<String, Object> colors = new LinkedHashMap<String, Object>();
        for (String key : new String [] {"post_color", "post_size"}) {
          if (parent.maps.get(0).get(key) != null) {
            colors.put(key, parent.maps.get(0).get(key));
          }
        }
        this.maps.add(colors);
      }
      this.maps.add(style.defaults);
      this.style = style;
      this.used = parent.used;
    }

    Object get(String key) {
      this.used.add(key);
      for (Map<?, ?> map : this.maps) {
        Object value = map.get(key);
        if (value != null) {
          return value;
        }
      }
      return null;
    }

    /**
     * Returns the value of <code>key</code> in the object <code>group</code>, or the one of <code>flatKey</code>.
     */
    Object get(String group, String key, String flatKey) {
      this.used.add(group);
      Object map = this.maps.get(0).get(group);
      if (map instanceof Map && ((Map<?, ?>)map).get(key) != null) {
        return ((Map<?, ?>)map).get(key);
      }
      return get(flatKey);
    }

    boolean isGiven(String key) {
      this.used.add(key);
      return this.maps.get(0).get(key) != null;
    }

    float num(String key, float defaultValue, float minimum, float maximum) throws IOException {
      return num(key, get(key), defaultValue, minimum, maximum);
    }

    float num(String group, String key, String flatKey, float defaultValue, float minimum, float maximum) throws IOException {
      return num(flatKey, get(group, key, flatKey), defaultValue, minimum, maximum);
    }

    private float num(String key, Object value, float defaultValue, float minimum, float maximum) throws IOException {
      float number = AssistantFurnitureBuilder.getNumber(Collections.singletonMap("v", value), "v", defaultValue);
      if (!(number >= minimum && number <= maximum)) {
        throw new IOException("\"" + key + "\" goes from " + format(minimum) + " to " + format(maximum));
      }
      return number;
    }

    String str(String key, String defaultValue) {
      Object value = get(key);
      return value != null ? value.toString().trim().toLowerCase(Locale.ENGLISH) : defaultValue;
    }

    String str(String group, String key, String flatKey, String defaultValue) {
      Object value = get(group, key, flatKey);
      return value != null ? value.toString().trim().toLowerCase(Locale.ENGLISH) : defaultValue;
    }

    boolean bool(String key, boolean defaultValue) {
      Object value = get(key);
      if (value instanceof Boolean) {
        return ((Boolean)value).booleanValue();
      } else if (value instanceof Number) {
        return ((Number)value).doubleValue() != 0;
      } else if (value != null) {
        String text = value.toString().trim().toLowerCase(Locale.ENGLISH);
        return !(text.equals("false") || text.equals("none") || text.equals("no") || text.length() == 0);
      }
      return defaultValue;
    }

    int color(String key, int defaultColor) throws IOException {
      Object value = get(key);
      if (value == null) {
        return defaultColor;
      }
      Integer color = AssistantCommandExecutor.parseColor(value.toString());
      if (color == null) {
        throw new IOException("\"" + key + "\" must be written #RRGGBB");
      }
      return color & 0xFFFFFF;
    }
  }

  /**
   * A part of a fence between two posts.
   */
  private static final class Span {
    float x1;
    float y1;
    float x2;
    float y2;
    float b1;
    float b2;
    float t1;
    float t2;
    float g1;
    float g2;
    float length;
    float dx;
    float dy;

    Span(float x1, float y1, float x2, float y2) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
      this.length = (float)Math.hypot(x2 - x1, y2 - y1);
      this.dx = this.length > 0 ? (x2 - x1) / this.length : 1;
      this.dy = this.length > 0 ? (y2 - y1) / this.length : 0;
    }

    float x(float ratio) {
      return this.x1 + (this.x2 - this.x1) * ratio;
    }

    float y(float ratio) {
      return this.y1 + (this.y2 - this.y1) * ratio;
    }

    float bottom(float ratio) {
      return this.b1 + (this.b2 - this.b1) * ratio;
    }

    float top(float ratio) {
      return this.t1 + (this.t2 - this.t1) * ratio;
    }

    /** Returns the height at a fraction of the height of the span, 0 at its bottom. */
    float at(float ratio, float fraction) {
      return bottom(ratio) + (top(ratio) - bottom(ratio)) * fraction;
    }

    float height() {
      return (this.t1 - this.b1 + this.t2 - this.b2) / 2;
    }
  }

  /**
   * What was built, and the quantities it needs.
   */
  static final class Result {
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    Style                style;
    double               length;
    double               gateLength;
    int                  linePosts;
    int                  terminalPosts;
    int                  gatePosts;
    int                  spans;
    int                  boards;
    int                  rails;
    float                concrete;
    float                lowestGround = Float.MAX_VALUE;
    float                highestGround = -Float.MAX_VALUE;
    float                retained;
    float                height;
    boolean              closed;
    /** The gates that slide and lack room along the fence. */
    final List<String>   slideProblems = new ArrayList<String>();
    final List<String>   gates = new ArrayList<String>();
    final List<String>   notes = new ArrayList<String>();
    final List<String>   violations = new ArrayList<String>();
    final Set<String>    usedKeys = new LinkedHashSet<String>();
    /** The posts: x, y, the height of the ground they were built on. */
    final List<float []> postPlaces = new ArrayList<float []>();

    int getPostCount() {
      return this.linePosts + this.terminalPosts + this.gatePosts;
    }

    int getConcreteBags() {
      return (int)Math.ceil(this.concrete / BAG_VOLUME - 1E-4);
    }

    /** Returns a rough count of the nails or screws. */
    int getFasteners() {
      return this.boards * 4 + this.rails * 4;
    }
  }

  private final Map<String, Object>          spec;
  private final Site                         site;
  private final Result                       result = new Result();
  private final AssistantMeshBuilder         mesh = this.result.mesh;
  private Options                            options;
  private float                              side = 1;
  private float                              sink;
  private float                              boardPitchScale = 1;
  private float                              elevationOffset;

  private AssistantFenceBuilder(Map<String, Object> spec, Site site) {
    this.spec = spec;
    this.site = site;
  }

  /**
   * Builds the fence described by <code>spec</code>.
   */
  static Result build(Map<String, Object> spec, Site site) throws IOException {
    AssistantFenceBuilder builder = new AssistantFenceBuilder(spec, site);
    builder.build();
    return builder.result;
  }

  static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  /**
   * Returns the points of a description.
   */
  static float [][] getPoints(Map<?, ?> spec) throws IOException {
    List<float []> points = new ArrayList<float []>();
    Object value = spec.get("points");
    if (value instanceof List) {
      for (Object item : (List<?>)value) {
        if (item instanceof List && ((List<?>)item).size() >= 2 && ((List<?>)item).get(0) instanceof Number
            && ((List<?>)item).get(1) instanceof Number) {
          float x = ((Number)((List<?>)item).get(0)).floatValue();
          float y = ((Number)((List<?>)item).get(1)).floatValue();
          if (!AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)) {
            throw new IOException("points out of range");
          }
          if (points.isEmpty() || Math.hypot(x - points.get(points.size() - 1) [0], y - points.get(points.size() - 1) [1]) > 0.5) {
            points.add(new float [] {x, y});
          }
        }
      }
    }
    if (points.size() >= 4 && Math.hypot(points.get(0) [0] - points.get(points.size() - 1) [0],
            points.get(0) [1] - points.get(points.size() - 1) [1]) <= 0.5) {
      points.remove(points.size() - 1);
    }
    if (points.size() < 2) {
      throw new IOException("\"points\" must give 2 different points [x,y] at least, like [[0,0],[600,0],[600,400]]");
    } else if (points.size() > MAX_POINTS) {
      throw new IOException("\"points\" has " + MAX_POINTS + " points at most");
    }
    return points.toArray(new float [points.size()][]);
  }

  /**
   * Returns <code>true</code> if the line of a description is closed.
   */
  static boolean isClosed(Map<?, ?> spec, float [][] points) {
    Object closed = spec.get("closed");
    boolean loop = false;
    if (spec.get("points") instanceof List) {
      List<?> given = (List<?>)spec.get("points");
      if (given.size() >= 4 && given.get(0) instanceof List && given.get(given.size() - 1) instanceof List) {
        loop = given.get(0).equals(given.get(given.size() - 1));
      }
    }
    return points.length >= 3 && (closed instanceof Boolean ? ((Boolean)closed).booleanValue()
        : closed != null ? "true".equalsIgnoreCase(closed.toString()) : loop);
  }

  /**
   * Returns the distances along a line of its points, the last one being its length.
   */
  static double [] getDistances(float [][] points, boolean closed) {
    int sides = closed ? points.length : points.length - 1;
    double [] distances = new double [sides + 1];
    for (int i = 0; i < sides; i++) {
      float [] next = points [(i + 1) % points.length];
      distances [i + 1] = distances [i] + Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
    }
    return distances;
  }

  /**
   * Returns the point at a distance along a line.
   */
  static float [] getPoint(float [][] points, double [] distances, double distance) {
    int side = getSide(distances, distance);
    float [] point = points [side];
    float [] next = points [(side + 1) % points.length];
    double length = distances [side + 1] - distances [side];
    double ratio = length > 0 ? Math.max(0, Math.min(1, (distance - distances [side]) / length)) : 0;
    return new float [] {(float)(point [0] + (next [0] - point [0]) * ratio), (float)(point [1] + (next [1] - point [1]) * ratio)};
  }

  static int getSide(double [] distances, double distance) {
    for (int i = 0; i < distances.length - 1; i++) {
      if (distance <= distances [i + 1]) {
        return i;
      }
    }
    return distances.length - 2;
  }

  /**
   * Returns the distance along a line of its point the closest to (<code>x</code>, <code>y</code>).
   */
  static double getClosestDistance(float [][] points, boolean closed, float x, float y) {
    double [] distances = getDistances(points, closed);
    double best = Double.MAX_VALUE;
    double bestDistance = 0;
    for (int i = 0; i < distances.length - 1; i++) {
      float [] point = points [i];
      float [] next = points [(i + 1) % points.length];
      double length = distances [i + 1] - distances [i];
      double ratio = length > 0
          ? Math.max(0, Math.min(1, ((x - point [0]) * (next [0] - point [0]) + (y - point [1]) * (next [1] - point [1])) / (length * length)))
          : 0;
      double gap = Math.hypot(point [0] + (next [0] - point [0]) * ratio - x, point [1] + (next [1] - point [1]) * ratio - y);
      if (gap < best) {
        best = gap;
        bestDistance = distances [i] + ratio * length;
      }
    }
    return bestDistance;
  }

  private float ground(float x, float y) {
    float height = this.site.getGroundHeight(x, y);
    this.result.lowestGround = Math.min(this.result.lowestGround, height);
    this.result.highestGround = Math.max(this.result.highestGround, height);
    return height + this.elevationOffset;
  }

  // ---- Shapes ----

  /**
   * Adds a solid between two ratios of a span, its bottom and its top given at both ends.
   * @param thickness its size across the span
   * @param offset    how far its middle is from the line of the span, towards the finished side
   */
  private void box(Span span, float ratio1, float ratio2, float low1, float high1, float low2, float high2,
                   float thickness, float offset, AssistantMeshBuilder.Material material) {
    if (Math.abs(ratio2 - ratio1) * span.length < 0.05f || (high1 - low1 < 0.05f && high2 - low2 < 0.05f)) {
      return;
    }
    float nx = -span.dy * this.side;
    float ny = span.dx * this.side;
    float ax = span.x(ratio1) + nx * offset;
    float ay = span.y(ratio1) + ny * offset;
    float bx = span.x(ratio2) + nx * offset;
    float by = span.y(ratio2) + ny * offset;
    float hx = nx * thickness / 2;
    float hy = ny * thickness / 2;
    float [][] start = {{ax - hx, low1, ay - hy}, {ax + hx, low1, ay + hy}, {ax + hx, high1, ay + hy}, {ax - hx, high1, ay - hy}};
    float [][] end = {{bx - hx, low2, by - hy}, {bx + hx, low2, by + hy}, {bx + hx, high2, by + hy}, {bx - hx, high2, by - hy}};
    float [] center = {(ax + bx) / 2, (low1 + high1 + low2 + high2) / 4, (ay + by) / 2};
    this.mesh.addOutward(start, center, material);
    this.mesh.addOutward(end, center, material);
    for (int i = 0; i < 4; i++) {
      int next = (i + 1) % 4;
      this.mesh.addOutward(new float [][] {start [i], start [next], end [next], end [i]}, center, material);
    }
  }

  /**
   * Adds a solid over a whole span, from <code>low</code> cm above its bottom to <code>inset</code> cm under its top.
   */
  private void slab(Span span, float low, float inset, float thickness, float offset, AssistantMeshBuilder.Material material) {
    box(span, 0, 1, span.b1 + low, span.t1 - inset, span.b2 + low, span.t2 - inset, thickness, offset, material);
  }

  /**
   * Adds a rail whose top is at a fraction of the height of a span.
   */
  private void rail(Span span, float fraction, float width, float height, float offset, AssistantMeshBuilder.Material material) {
    box(span, 0, 1, span.at(0, fraction) - height, span.at(0, fraction), span.at(1, fraction) - height, span.at(1, fraction),
        width, offset, material);
    this.result.rails++;
  }

  /**
   * Adds a rail whose top is <code>inset</code> cm under the top of a span.
   */
  private void railUnderTop(Span span, float inset, float width, float height, float offset, AssistantMeshBuilder.Material material) {
    box(span, 0, 1, span.t1 - inset - height, span.t1 - inset, span.t2 - inset - height, span.t2 - inset, width, offset, material);
    this.result.rails++;
  }

  /**
   * Adds a rail whose bottom is <code>low</code> cm above the bottom of a span.
   */
  private void railAboveBottom(Span span, float low, float width, float height, float offset, AssistantMeshBuilder.Material material) {
    box(span, 0, 1, span.b1 + low, span.b1 + low + height, span.b2 + low, span.b2 + low + height, width, offset, material);
    this.result.rails++;
  }

  /**
   * Adds a surface without thickness seen from both sides.
   */
  private void sheet(Span span, float ratio1, float ratio2, float low1, float high1, float low2, float high2,
                     float offset, AssistantMeshBuilder.Material material) {
    float nx = -span.dy * this.side;
    float ny = span.dx * this.side;
    float ax = span.x(ratio1) + nx * offset;
    float ay = span.y(ratio1) + ny * offset;
    float bx = span.x(ratio2) + nx * offset;
    float by = span.y(ratio2) + ny * offset;
    this.mesh.addPolygon(new float [][] {{ax, low1, ay}, {bx, low2, by}, {bx, high2, by}, {ax, high1, ay}}, material, nx, 0, ny);
    this.mesh.addPolygon(new float [][] {{ax, low1, ay}, {bx, low2, by}, {bx, high2, by}, {ax, high1, ay}}, material, -nx, 0, -ny);
  }

  /**
   * Adds an upright board at a ratio of a span.
   * @param top the shape of its top: flat, pointed, dog_ear, gothic, french_gothic or rounded
   */
  private void board(Span span, float ratio, float width, float thickness, float low, float high, float offset,
                     AssistantMeshBuilder.Material material, String top) {
    if (high - low < 0.5f) {
      return;
    }
    float nx = -span.dy * this.side;
    float ny = span.dx * this.side;
    float cx = span.x(ratio) + nx * (offset - thickness / 2);
    float cy = span.y(ratio) + ny * (offset - thickness / 2);
    float half = width / 2;
    float [][] outline;
    if ("pointed".equals(top) || "spear".equals(top)) {
      float shoulder = Math.max(low, high - Math.max(width * 0.6f, 2));
      outline = new float [][] {{-half, low}, {half, low}, {half, shoulder}, {0, high}, {-half, shoulder}};
    } else if ("dog_ear".equals(top)) {
      float cut = Math.min(width * 0.25f, high - low);
      outline = new float [][] {{-half, low}, {half, low}, {half, high - cut}, {half - cut, high}, {-half + cut, high}, {-half, high - cut}};
    } else if ("gothic".equals(top) || "french_gothic".equals(top)) {
      float shoulder = Math.max(low, high - width * 1.1f);
      float middle = Math.max(low, high - width * 0.45f);
      outline = new float [][] {{-half, low}, {half, low}, {half, shoulder}, {half * 0.75f, middle}, {0, high},
                                {-half * 0.75f, middle}, {-half, shoulder}};
    } else if ("rounded".equals(top)) {
      float shoulder = Math.max(low, high - half);
      outline = new float [][] {{-half, low}, {half, low}, {half, shoulder}, {half * 0.7f, shoulder + (high - shoulder) * 0.7f}, {0, high},
                                {-half * 0.7f, shoulder + (high - shoulder) * 0.7f}, {-half, shoulder}};
    } else {
      outline = new float [][] {{-half, low}, {half, low}, {half, high}, {-half, high}};
    }
    float [][] section = new float [outline.length][];
    for (int i = 0; i < outline.length; i++) {
      section [i] = new float [] {cx + span.dx * outline [i][0], outline [i][1], cy + span.dy * outline [i][0]};
    }
    this.mesh.addSolid(section, new float [] {nx * thickness, 0, ny * thickness}, material);
    this.result.boards++;
  }

  /**
   * Adds a post, with its cap.
   */
  private void post(float x, float y, float dx, float dy, float size, String shape, float low, float high,
                    AssistantMeshBuilder.Material material, String cap, AssistantMeshBuilder.Material capMaterial) {
    float width = size;
    float depth = size;
    if ("t_post".equals(shape) || "u_channel".equals(shape)) {
      depth = size * 0.6f;
    } else if ("i_beam".equals(shape)) {
      depth = size * 0.6f;
    }
    if ("round".equals(shape)) {
      float [][] section = new float [8][];
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI * 2 * i / 8;
        section [i] = new float [] {x + (float)Math.cos(angle) * size / 2, low, y + (float)Math.sin(angle) * size / 2};
      }
      this.mesh.addSolid(section, new float [] {0, high - low, 0}, material);
    } else {
      float ax = dx * width / 2;
      float ay = dy * width / 2;
      float bx = -dy * depth / 2;
      float by = dx * depth / 2;
      this.mesh.addSolid(new float [][] {{x - ax - bx, low, y - ay - by}, {x + ax - bx, low, y + ay - by},
                                         {x + ax + bx, low, y + ay + by}, {x - ax + bx, low, y - ay + by}},
          new float [] {0, high - low, 0}, material);
    }
    if (cap == null || cap.equals("none") || cap.equals("false")) {
      return;
    }
    float half = size / 2 + 1;
    if (cap.equals("pyramid") || cap.equals("copper")) {
      float [] apex = {x, high + size * 0.45f, y};
      float [][] base = {{x - half, high, y - half}, {x + half, high, y - half}, {x + half, high, y + half}, {x - half, high, y + half}};
      float [] center = {x, high, y};
      for (int i = 0; i < 4; i++) {
        this.mesh.addOutward(new float [][] {base [i], base [(i + 1) % 4], apex}, center, capMaterial);
      }
      this.mesh.addOutward(base, new float [] {x, high + 1, y}, capMaterial);
    } else if (cap.equals("ball")) {
      // A ball drawn as a diamond
      float radius = size * 0.55f;
      float [] center = {x, high + radius, y};
      float [][] ring = {{x - radius, high + radius, y}, {x, high + radius, y - radius}, {x + radius, high + radius, y}, {x, high + radius, y + radius}};
      for (int i = 0; i < 4; i++) {
        this.mesh.addOutward(new float [][] {ring [i], ring [(i + 1) % 4], {x, high + radius * 2, y}}, center, capMaterial);
        this.mesh.addOutward(new float [][] {ring [i], ring [(i + 1) % 4], {x, high, y}}, center, capMaterial);
      }
    } else {
      this.mesh.addBox(x - half, high, y - half, x + half, high + 2, y + half, capMaterial);
      if (cap.equals("solar_light") || cap.equals("light") || cap.equals("lighted")) {
        this.mesh.addBox(x - half * 0.6f, high + 2, y - half * 0.6f, x + half * 0.6f, high + 7, y + half * 0.6f,
            named("lights", this.mesh.getMaterial(0xFFF3C0)));
        this.mesh.addBox(x - half, high + 7, y - half, x + half, high + 8.5f, y + half, capMaterial);
      }
    }
  }

  // ---- Materials ----

  /**
   * The colors of the parts of a fence or of a gate.
   */
  private final class Colors {
    final int                           infillColor;
    final int                           postColor;
    final int                           railColor;
    final AssistantMeshBuilder.Material infill;
    final AssistantMeshBuilder.Material post;
    final AssistantMeshBuilder.Material rail;
    final AssistantMeshBuilder.Material cap;

    Colors(Options options, String prefix) throws IOException {
      int styleColor = options.color("color", 0x8B6B47);
      String finish = options.str("finish", null);
      boolean finished = false;
      if (finish != null && !options.isGiven("color") && options.isGiven("finish")) {
        finished = true;
        styleColor = finish.startsWith("black") ? 0x1E1E1E : finish.startsWith("green") ? 0x2F5D3A
            : finish.startsWith("brown") ? 0x4B3A2A : finish.startsWith("white") ? 0xF2F2EE : 0xA9ADB0;
      }
      this.infillColor = options.color("infill_color", styleColor);
      this.postColor = finished && !options.isGiven("post_color") ? styleColor : options.color("post_color", styleColor);
      this.railColor = options.color("rail_color", options.style.family.equals("Wood") ? this.infillColor : this.postColor);
      AssistantMeshBuilder.Material textured = null;
      Object texture = options.get("texture");
      if (texture != null) {
        textured = site.getTexture(mesh, texture.toString(), options.num("texture_scale", 1, 0.01f, 100), this.infillColor);
        if (textured == null) {
          result.notes.add("no texture \"" + texture + "\" in the catalog: the fence keeps its color");
        }
      }
      // Materials named after the parts they show, for list_materials and set_material
      this.infill = mesh.getNamedMaterial(prefix + getInfillName(options.style.infill),
          textured != null ? textured : mesh.getMaterial(this.infillColor));
      String postPattern = options.str("post_pattern", null);
      this.post = mesh.getNamedMaterial(prefix + "posts",
          postPattern != null ? getPattern(postPattern, this.postColor) : mesh.getMaterial(this.postColor));
      this.rail = mesh.getNamedMaterial(prefix + "rails", mesh.getMaterial(this.railColor));
      String cap = options.str("post", "cap", "post_cap", "flat");
      this.cap = mesh.getNamedMaterial(prefix + "caps", mesh.getMaterial(cap.equals("copper") ? 0xB87333 : shade(this.postColor, 0.9f)));
    }
  }

  /**
   * Returns a material named after the part it shows.
   */
  private AssistantMeshBuilder.Material named(String name, AssistantMeshBuilder.Material material) {
    return this.mesh.getNamedMaterial(name, material);
  }

  /**
   * Returns the name of the material of what fills the spans of a style.
   */
  private static String getInfillName(Infill infill) {
    switch (infill) {
      case PICKET :
      case PALISADE :
        return "pickets";
      case SOLID :
      case DOUBLE_BOARDS :
      case HSLAT :
      case BASKET :
        return "boards";
      case BARS :
        return "bars";
      case STRANDS :
        return "wires";
      case WALL :
      case HAHA :
      case RETAINING :
      case GABION :
        return "wall";
      case SHEET :
      case GLASS :
      case PIER_PANEL :
        return "panels";
      default :
        return "infill";
    }
  }

  private static int shade(int color, float factor) {
    int red = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
    int green = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
    int blue = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
    return red << 16 | green << 8 | blue;
  }

  /**
   * Returns the material showing a pattern of masonry, of boards or of plants at its real size.
   */
  private AssistantMeshBuilder.Material getPattern(String pattern, int color) {
    if (pattern.startsWith("brick")) {
      return this.mesh.getMaterial(pattern, 43, 30, color, 0xC9C2B4, 0);
    } else if (pattern.equals("block")) {
      return this.mesh.getMaterial(pattern, 41, 41, color, shade(color, 0.75f), 0);
    } else if (pattern.equals("fieldstone") || pattern.equals("dry_stack")) {
      return this.mesh.getMaterial("fieldstone", 90, 90, color, shade(color, 0.55f), 0);
    } else if (pattern.equals("flagstone")) {
      return this.mesh.getMaterial(pattern, 120, 120, color, shade(color, 0.55f), 0);
    } else if (pattern.equals("ashlar")) {
      return this.mesh.getMaterial(pattern, 120, 100, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("corrugated")) {
      return this.mesh.getMaterial(pattern, 60, 60, color, color, 0);
    } else if (pattern.equals("boards")) {
      return this.mesh.getMaterial(pattern, 14, 60, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("hboards")) {
      return this.mesh.getMaterial(pattern, 60, 14, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("wattle")) {
      return this.mesh.getMaterial(pattern, 60, 30, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("bamboo")) {
      return this.mesh.getMaterial(pattern, 20, 80, color, shade(color, 0.65f), 0);
    } else if (pattern.equals("reed")) {
      return this.mesh.getMaterial(pattern, 30, 60, color, shade(color, 0.7f), 0);
    } else if (pattern.equals("woven")) {
      return this.mesh.getMaterial(pattern, 60, 50, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("stucco")) {
      return this.mesh.getMaterial(pattern, 30, 30, color, color, 0);
    }
    return this.mesh.getMaterial(color);
  }

  // ---- Building ----

  /**
   * A gate, or a gap, in the line of the fence.
   */
  private static final class Opening {
    Map<?, ?> map;
    double    start;
    double    end;
    String    type;
    int       index;
  }

  private void build() throws IOException {
    Style style = getStyle(this.spec.get("style") != null ? this.spec.get("style").toString() : null);
    if (style == null) {
      throw new IOException("unknown \"style\" \"" + this.spec.get("style") + "\". The styles: " + describeStyles(false));
    }
    this.result.style = style;
    this.options = new Options(this.spec, style, this.result.usedKeys);
    Options options = this.options;
    for (String key : new String [] {"style", "points", "closed", "name", "level", "tags", "owner", "ownership_side", "report",
                                     "openings", "gate"}) {
      this.result.usedKeys.add(key);
    }
    float [][] points = getPoints(this.spec);
    boolean closed = isClosed(this.spec, points);
    this.result.closed = closed;
    String sideName = options.str("side", "left");
    this.side = sideName.startsWith("r") || sideName.equals("in") || sideName.equals("inside") ? -1 : 1;
    this.sink = this.site.hasTerrain() ? 5 : 0;
    this.elevationOffset = options.num("elevation", 0, -1000, 3000);
    float height = options.num("height", 120, 5, 800);
    this.result.height = height;
    // The height at each point
    float [] pointHeights = new float [points.length];
    java.util.Arrays.fill(pointHeights, height);
    Object profile = options.get("height_profile");
    if (profile instanceof List) {
      List<?> heights = (List<?>)profile;
      for (int i = 0; i < points.length && i < heights.size(); i++) {
        if (heights.get(i) instanceof Number) {
          pointHeights [i] = Math.max(5, Math.min(800, ((Number)heights.get(i)).floatValue()));
        }
      }
      if (heights.size() != points.length) {
        this.result.notes.add("\"height_profile\" gives " + heights.size() + " heights for " + points.length
            + " points: the points without height are " + format(height) + " cm high");
      }
    }
    // "spacing" is read as the spacing of the posts when nothing else gives it
    boolean spacingAlias = this.spec.get("spacing") instanceof Number && this.spec.get("post_spacing") == null
        && !(this.spec.get("post") instanceof Map && ((Map<?, ?>)this.spec.get("post")).get("spacing") != null);
    this.result.usedKeys.add("spacing");
    if (style.infill == Infill.ZIGZAG) {
      points = getZigzag(points, spacingAlias ? options.num("spacing", 300, 60, 1000)
          : options.num("post", "spacing", "post_spacing", 300, 60, 1000), options.num("zigzag", 45, 5, 200));
      closed = false;
      pointHeights = new float [points.length];
      java.util.Arrays.fill(pointHeights, height);
    }
    double [] distances = getDistances(points, closed);
    double length = distances [distances.length - 1];
    List<Opening> openings = getOpenings(length);
    boolean drawPosts = options.bool("posts", true);
    float spacing = spacingAlias ? options.num("spacing", 240, 20, 1500)
        : options.num("post", "spacing", "post_spacing", 240, 20, 1500);
    float piers = options.num("piers_every", 0, 0, 3000);
    if (piers > 0) {
      spacing = Math.max(60, piers);
      drawPosts = true;
    }
    if (length / spacing > MAX_SPANS) {
      spacing = (float)(length / MAX_SPANS);
      this.result.notes.add("the posts are " + format(spacing) + " cm apart, for " + MAX_SPANS + " spans at most");
    }
    float postSize = options.num("post", "size", "post_size", piers > 0 ? options.num("thickness", 20, 1, 300) + 14 : 9, 1, 200);
    String postShape = options.str("post", "shape", "post_shape", "square");
    String postCap = options.str("post", "cap", "post_cap", style.family.equals("Wire and mesh") ? "none" : "flat");
    if (style.infill == Infill.BOLLARDS && "lighted".equals(options.str("type", "steel"))) {
      postCap = "solar_light";
    }
    Object lighting = options.get("lighting");
    float lightSpacing = 0;
    if (lighting != null && !"none".equals(lighting.toString()) && !Boolean.FALSE.equals(lighting)) {
      lightSpacing = lighting instanceof Map ? AssistantFurnitureBuilder.getNumber((Map<?, ?>)lighting, "spacing", 1) : 1;
      this.result.notes.add("the lights of the posts are drawn, without lighting the 3D view");
    }
    float postExtra = options.num("post_extra", 6, -100, 300);
    float terminalScale = options.num("terminal_scale", 1, 0.5f, 4);
    float clearance = options.num("clearance", style.infill == Infill.WALL || style.infill == Infill.GABION ? 0 : 5, -50, 200);
    String follow = options.str("follow_ground", "rake");
    if (!follow.equals("rake") && !follow.equals("step") && !follow.equals("level")) {
      throw new IOException("\"follow_ground\" is rake, step or level");
    }
    float maxStep = options.num("max_step", 0, 0, 500);
    String fillGaps = options.str("fill_gaps", "none");
    float embedDepth = options.num("post", "embed_depth", "embed_depth", Math.max(45, height / 3), 0, 400);
    String setIn = options.str("post", "set_in", "set_in", style.family.equals("Wire and mesh") && !style.name.equals("chain_link") ? "driven" : "concrete");
    options.get("post", "material", "post_material");
    Colors colors = new Colors(options, "");

    // The parts of each side left by the openings, cut in spans
    List<Span> spans = new ArrayList<Span>();
    List<double []> spanDistances = new ArrayList<double []>();
    Map<String, double []> posts = new LinkedHashMap<String, double []>();
    int sides = distances.length - 1;
    for (int sideIndex = 0; sideIndex < sides; sideIndex++) {
      double start = distances [sideIndex];
      double end = distances [sideIndex + 1];
      List<double []> parts = new ArrayList<double []>();
      double cursor = start;
      for (Opening opening : openings) {
        if (opening.end <= start || opening.start >= end) {
          continue;
        }
        parts.add(new double [] {cursor, Math.max(cursor, opening.start), 0, 1});
        cursor = Math.min(end, opening.end);
      }
      parts.add(new double [] {cursor, end, cursor > start ? 1 : 0, 0});
      for (double [] part : parts) {
        boolean gateStart = part [2] != 0;
        boolean gateEnd = part [3] != 0;
        int count = part [1] - part [0] < 1 ? 0 : Math.max(1, (int)Math.ceil((part [1] - part [0]) / spacing - 1E-4));
        if (count == 0 && !gateStart && !gateEnd) {
          continue;
        }
        float [] previous = null;
        double previousDistance = 0;
        for (int i = 0; i <= count; i++) {
          double distance = count == 0 ? part [0] : part [0] + (part [1] - part [0]) * i / count;
          float [] point = Math.abs(distance - start) < 1E-3 ? points [sideIndex]
              : Math.abs(distance - end) < 1E-3 ? points [(sideIndex + 1) % points.length]
              : getPoint(points, distances, distance);
          boolean gatePost = (i == 0 && gateStart) || (i == count && gateEnd);
          boolean terminal = Math.abs(distance - start) < 1E-3 || Math.abs(distance - end) < 1E-3;
          String key = Math.round(point [0] * 10) + ":" + Math.round(point [1] * 10);
          double [] post = posts.get(key);
          int kind = gatePost ? 2 : terminal ? 1 : 0;
          if (post == null) {
            posts.put(key, new double [] {point [0], point [1], sideIndex, kind, distance, -Float.MAX_VALUE});
          } else if (kind > post [3]) {
            post [3] = kind;
          }
          if (previous != null && distance - previousDistance > 0.5) {
            spans.add(new Span(previous [0], previous [1], point [0], point [1]));
            spanDistances.add(new double [] {previousDistance, distance});
          }
          previous = point;
          previousDistance = distance;
        }
      }
    }
    // The heights of the ends of the spans
    float levelTop = -Float.MAX_VALUE;
    if (follow.equals("level")) {
      for (int i = 0; i < spans.size(); i++) {
        Span span = spans.get(i);
        levelTop = Math.max(levelTop, Math.max(ground(span.x1, span.y1) + getHeight(points, distances, pointHeights, spanDistances.get(i) [0]),
            ground(span.x2, span.y2) + getHeight(points, distances, pointHeights, spanDistances.get(i) [1])));
      }
    }
    float steepest = 0;
    for (int i = 0; i < spans.size(); i++) {
      Span span = spans.get(i);
      span.g1 = ground(span.x1, span.y1);
      span.g2 = ground(span.x2, span.y2);
      float height1 = getHeight(points, distances, pointHeights, spanDistances.get(i) [0]);
      float height2 = getHeight(points, distances, pointHeights, spanDistances.get(i) [1]);
      if (follow.equals("step")) {
        float base = Math.max(span.g1, span.g2);
        span.b1 = span.b2 = base + clearance;
        span.t1 = span.t2 = base + (height1 + height2) / 2;
        steepest = Math.max(steepest, Math.abs(span.g1 - span.g2));
      } else {
        span.b1 = span.g1 + clearance;
        span.b2 = span.g2 + clearance;
        span.t1 = follow.equals("level") ? levelTop : span.g1 + height1;
        span.t2 = follow.equals("level") ? levelTop : span.g2 + height2;
      }
      for (double [] post : posts.values()) {
        if (Math.abs(post [0] - span.x1) < 0.06 && Math.abs(post [1] - span.y1) < 0.06) {
          post [5] = Math.max(post [5], span.t1);
        } else if (Math.abs(post [0] - span.x2) < 0.06 && Math.abs(post [1] - span.y2) < 0.06) {
          post [5] = Math.max(post [5], span.t2);
        }
      }
    }
    if (follow.equals("step") && maxStep > 0 && steepest > maxStep) {
      this.result.notes.add("a panel steps down by " + format(steepest) + " cm, more than \"max_step\" " + format(maxStep)
          + ": closer posts (\"post_spacing\") make smaller steps");
    }
    // The boards are farther apart on a very long fence
    this.boardPitchScale = (float)Math.max(1, length / 12 / MAX_BOARDS);
    // Posts
    int postIndex = 0;
    for (double [] post : posts.values()) {
      float x = (float)post [0];
      float y = (float)post [1];
      int kind = (int)post [3];
      boolean terminal = kind > 0 || (!closed && (post [4] < 1E-3 || post [4] > length - 1E-3));
      if (!drawPosts && kind != 2) {
        continue;
      }
      float ground = ground(x, y);
      float top = (post [5] > -Float.MAX_VALUE / 2 ? (float)post [5] : ground + getHeight(points, distances, pointHeights, post [4]))
          + postExtra + (kind == 2 ? 10 : 0);
      float size = postSize * (terminal ? terminalScale : 1) * (kind == 2 && terminalScale == 1 ? 1.15f : 1);
      int sideIndex = (int)post [2];
      float [] next = points [(sideIndex + 1) % points.length];
      Span direction = new Span(points [sideIndex][0], points [sideIndex][1], next [0], next [1]);
      String cap = postCap;
      boolean strip = lighting instanceof Map && "strip".equals(String.valueOf(((Map<?, ?>)lighting).get("type")));
      if (lightSpacing > 0 && !strip && postIndex % Math.max(1, Math.round(lightSpacing)) == 0) {
        cap = "solar_light";
      }
      post(x, y, direction.dx, direction.dy, size, postShape, ground - this.sink, top, colors.post, cap, colors.cap);
      if (options.bool("feet", false)) {
        float nx = -direction.dy * 30;
        float ny = direction.dx * 30;
        this.mesh.addSolid(new float [][] {{x - nx - direction.dx * 11, ground, y - ny - direction.dy * 11},
                                           {x + nx - direction.dx * 11, ground, y + ny - direction.dy * 11},
                                           {x + nx + direction.dx * 11, ground, y + ny + direction.dy * 11},
                                           {x - nx + direction.dx * 11, ground, y - ny + direction.dy * 11}},
            new float [] {0, 12, 0}, named("concrete", this.mesh.getMaterial(0x8E8E88)));
      }
      this.result.postPlaces.add(new float [] {x, y, ground - this.elevationOffset});
      if (kind == 2) {
        this.result.gatePosts++;
      } else if (terminal) {
        this.result.terminalPosts++;
      } else {
        this.result.linePosts++;
      }
      if (setIn.equals("concrete")) {
        // A hole three times as wide as the post
        double radius = size * 1.5 / 100;
        this.result.concrete += (float)((Math.PI * radius * radius - size * size / 10000.) * embedDepth / 100);
      }
      postIndex++;
    }
    // Infill
    for (Span span : spans) {
      if (span.length < 0.5f) {
        continue;
      }
      infill(options, colors, span, false);
      if (follow.equals("step") && !fillGaps.equals("none") && Math.abs(span.g1 - span.g2) > 1) {
        box(span, 0, 1, span.g1 - this.sink, span.b1, span.g2 - this.sink, span.b2, fillGaps.equals("soil") ? 12 : 2.5f, 0,
            fillGaps.equals("soil") ? named("soil", this.mesh.getMaterial(0x5B4634)) : colors.rail);
      }
      Object kickboard = options.get("kickboard");
      if (kickboard == null) {
        kickboard = options.get("rot_board");
      }
      if (kickboard != null && !Boolean.FALSE.equals(kickboard)) {
        float boardHeight = kickboard instanceof Number ? ((Number)kickboard).floatValue()
            : kickboard instanceof Map ? AssistantFurnitureBuilder.getNumber((Map<?, ?>)kickboard, "height", 14) : 14;
        box(span, 0, 1, span.g1, span.g1 + boardHeight, span.g2, span.g2 + boardHeight, 2.5f, 2, colors.rail);
        this.result.boards++;
      }
      if (lighting instanceof Map && "strip".equals(String.valueOf(((Map<?, ?>)lighting).get("type")))) {
        railUnderTop(span, 12, 1.5f, 1.5f, 3, named("lights", this.mesh.getMaterial(0xFFF3C0)));
      }
    }
    this.result.spans = spans.size();
    this.result.length = length;
    // Gates
    for (Opening opening : openings) {
      this.result.gateLength += opening.end - opening.start;
      addGate(opening, points, distances, pointHeights, colors, postSize);
    }
    if (style.name.equals("pool_code")) {
      checkPoolCode(height, clearance, openings);
    }
    if (this.mesh.getFaceCount() == 0) {
      throw new IOException("nothing to build: the line is too short");
    }
  }

  private static float getHeight(float [][] points, double [] distances, float [] heights, double distance) {
    int side = getSide(distances, distance);
    double length = distances [side + 1] - distances [side];
    double ratio = length > 0 ? Math.max(0, Math.min(1, (distance - distances [side]) / length)) : 0;
    return (float)(heights [side] + (heights [(side + 1) % heights.length] - heights [side]) * ratio);
  }

  /**
   * Returns a line that goes from one side to the other of the given one.
   */
  private static float [][] getZigzag(float [][] points, float step, float amplitude) {
    List<float []> zigzag = new ArrayList<float []>();
    int index = 0;
    for (int i = 0; i < points.length - 1; i++) {
      Span side = new Span(points [i][0], points [i][1], points [i + 1][0], points [i + 1][1]);
      int count = Math.max(1, Math.round(side.length / step));
      for (int k = 0; k < count; k++) {
        float offset = index++ % 2 == 0 ? -amplitude / 2 : amplitude / 2;
        zigzag.add(new float [] {side.x((float)k / count) - side.dy * offset, side.y((float)k / count) + side.dx * offset});
      }
      if (i == points.length - 2) {
        float offset = index % 2 == 0 ? -amplitude / 2 : amplitude / 2;
        zigzag.add(new float [] {side.x2 - side.dy * offset, side.y2 + side.dx * offset});
      }
    }
    return zigzag.toArray(new float [zigzag.size()][]);
  }

  private List<Opening> getOpenings(double length) throws IOException {
    List<Object> items = new ArrayList<Object>();
    if (this.spec.get("openings") instanceof List) {
      items.addAll((List<?>)this.spec.get("openings"));
    }
    if (this.spec.get("gate") instanceof Map) {
      items.add(this.spec.get("gate"));
    } else if (this.spec.get("gate") != null) {
      throw new IOException("\"gate\" is written {\"at\":N,\"width\":N}");
    }
    List<Opening> openings = new ArrayList<Opening>();
    for (int i = 0; i < items.size(); i++) {
      if (!(items.get(i) instanceof Map)) {
        throw new IOException("each item of \"openings\" is written {\"at\":N,\"width\":N,\"type\":\"single_swing\"}");
      }
      Map<?, ?> map = (Map<?, ?>)items.get(i);
      float at = AssistantFurnitureBuilder.getNumber(map, "at", Float.NaN);
      float width = AssistantFurnitureBuilder.getNumber(map, "width", 100);
      if (Float.isNaN(at)) {
        throw new IOException("a gate must give \"at\": the distance in cm from the first point to the middle of the gate, along the fence");
      } else if (!(width >= 30 && width <= 1500)) {
        throw new IOException("the \"width\" of a gate goes from 30 to 1500 cm");
      } else if (at - width / 2 < -0.5f || at + width / 2 > length + 0.5f) {
        throw new IOException("the gate goes from " + format(at - width / 2) + " to " + format(at + width / 2)
            + " cm along a fence " + format(length) + " cm long");
      }
      Opening opening = new Opening();
      opening.map = map;
      opening.start = Math.max(0, at - width / 2);
      opening.end = Math.min(length, at + width / 2);
      opening.index = i;
      String type = map.get("type") != null ? map.get("type").toString().trim().toLowerCase(Locale.ENGLISH) : "single_swing";
      if (type.equals("swing") || type.equals("single")) {
        type = "single_swing";
      } else if (type.equals("double")) {
        type = "double_swing";
      } else if (type.equals("kissing_gate") || type.equals("kissing")) {
        type = "walk_through_kissing_gate";
      } else if (type.equals("turnstile")) {
        type = "pedestrian_turnstile";
      } else if (type.equals("arbor")) {
        type = "arbor_gate";
      } else if (type.equals("none") || type.equals("opening")) {
        type = "gap";
      }
      if (!java.util.Arrays.asList(GATE_TYPES).contains(type)) {
        throw new IOException("unknown gate \"type\" \"" + type + "\". The types: " + java.util.Arrays.toString(GATE_TYPES));
      }
      opening.type = type;
      openings.add(opening);
    }
    Collections.sort(openings, new Comparator<Opening>() {
        public int compare(Opening opening1, Opening opening2) {
          return Double.compare(opening1.start, opening2.start);
        }
      });
    for (int i = 1; i < openings.size(); i++) {
      if (openings.get(i).start < openings.get(i - 1).end - 0.5) {
        throw new IOException("two gates overlap, at " + format((openings.get(i - 1).start + openings.get(i - 1).end) / 2)
            + " and " + format((openings.get(i).start + openings.get(i).end) / 2) + " cm along the fence");
      }
    }
    return openings;
  }

  // ---- Infill ----

  private void infill(Options options, Colors colors, Span span, boolean leaf) throws IOException {
    Infill infill = options.style.infill;
    float height = span.height();
    float pitchScale = this.boardPitchScale;
    switch (infill) {
      case MESH : {
        String pattern = options.str("pattern", "diamond");
        float cellWidth = options.num("cell_w", options.num("mesh_size", 5, 0.5f, 100), 0.5f, 200);
        float cellHeight = options.num("cell_h", options.num("mesh_size", 5, 0.5f, 100), 0.5f, 200);
        float wire = options.num("wire", options.num("gauge", 0.1f, 0.01f, 1), 0.01f, 1);
        if (pattern.equals("diamond")) {
          cellWidth = cellHeight = options.num("mesh_size", 5, 0.5f, 100) * 1.41f;
        }
        // Wires thick enough to be seen from a few meters
        float ratio = Math.max(0.1f, Math.min(0.5f, wire * 2 / Math.min(cellWidth, cellHeight) + 0.06f));
        float tile = pattern.equals("fine") ? 4 : pattern.equals("hex") ? 2 : 1;
        AssistantMeshBuilder.Material meshMaterial = this.mesh.getNamedMaterial("mesh", this.mesh.getMaterial(pattern, cellWidth * tile,
            cellHeight * (pattern.equals("fine") || pattern.equals("hex") ? 2 : 1), colors.infillColor, colors.infillColor, ratio));
        String frame = options.str("frame", "none");
        float inset = 0;
        if (!frame.equals("none") && !frame.equals("false")) {
          float size = frame.equals("wood") ? 9 : 4;
          railUnderTop(span, 0, frame.equals("wood") ? 4 : size, size, 0, frame.equals("wood") ? colors.post : colors.rail);
          railAboveBottom(span, 0, frame.equals("wood") ? 4 : size, size, 0, frame.equals("wood") ? colors.post : colors.rail);
          inset = size / 2;
        } else {
          if (options.bool("top_rail", false)) {
            railUnderTop(span, 0, 4, 4, 0, colors.rail);
          }
          if (options.bool("bottom_rail", false)) {
            railAboveBottom(span, 0, 4, 4, 0, colors.rail);
          } else if (options.bool("tension_wire", false)) {
            railAboveBottom(span, 2, 0.5f, 0.5f, 0, colors.rail);
          }
        }
        sheet(span, 0, 1, span.b1 + inset, span.t1 - inset, span.b2 + inset, span.t2 - inset, 0, meshMaterial);
        String slats = options.str("privacy_slats", "none");
        if (!slats.equals("none") && !slats.equals("false")) {
          int slatColor = options.color("slat_color", slats.equals("hedge") ? 0x3F6B35 : 0x5E6B5E);
          AssistantMeshBuilder.Material slatMaterial = this.mesh.getNamedMaterial("slats", slats.equals("hedge")
              ? this.mesh.getMaterial("leaves", 40, 40, slatColor, shade(slatColor, 1.25f), 0.12f)
              : this.mesh.getMaterial("slats", cellWidth, 60, slatColor, slatColor, slats.equals("winged") ? 0.04f : 0.25f));
          sheet(span, 0, 1, span.b1 + inset, span.t1 - inset, span.b2 + inset, span.t2 - inset, -0.6f, slatMaterial);
        }
        float barbed = options.num("barbed_top", 0, 0, 3);
        for (int i = 0; i < Math.round(barbed); i++) {
          railUnderTop(span, -8 - 9 * i, 0.6f, 0.6f, -3 * i, colors.rail);
        }
        String topping = options.str("topping", "none");
        if (topping.equals("razor_wire")) {
          for (int i = 0; i < 3; i++) {
            railUnderTop(span, -6 - 12 * (i % 2), 0.8f, 0.8f, (i - 1) * 12, colors.rail);
          }
        } else if (topping.equals("spikes") || topping.equals("rotating_spike_bar")) {
          railUnderTop(span, -4, 3, 3, 0, colors.rail);
          int count = Math.max(1, Math.round(span.length / (12 * pitchScale)));
          for (int i = 0; i < count; i++) {
            float ratio2 = (i + 0.5f) / count;
            board(span, ratio2, 2, 2, span.top(ratio2) + 4, span.top(ratio2) + 14, 1, colors.rail, "pointed");
          }
        } else if (!topping.equals("none")) {
          throw new IOException("\"topping\" is razor_wire, spikes or rotating_spike_bar");
        }
        break;
      }
      case STRANDS : {
        float strand = options.num("strand", 0.6f, 0.1f, 10);
        Object heights = options.get("strand_heights");
        boolean topRail = options.bool("top_rail", false);
        if (topRail) {
          railUnderTop(span, 0, 9, 4, 0, colors.post);
        }
        if (heights instanceof List) {
          for (Object item : (List<?>)heights) {
            if (item instanceof Number) {
              float strandHeight = ((Number)item).floatValue();
              box(span, 0, 1, span.g1 + strandHeight - strand, span.g1 + strandHeight, span.g2 + strandHeight - strand,
                  span.g2 + strandHeight, strand, 0, colors.infill);
            }
          }
        } else {
          int count = Math.round(options.num("strands", 3, 1, 40));
          for (int i = 0; i < count; i++) {
            float fraction = count == 1 ? 0.8f : 0.12f + (topRail ? 0.78f : 0.85f) * i / (count - 1);
            rail(span, fraction, strand, strand, 0, colors.infill);
          }
        }
        break;
      }
      case PICKET : {
        float width = options.num("picket_width", 7, 1, 60);
        float gap = options.num("gap", 5, 0, 100);
        String top = options.str("top", "dog_ear");
        String scallop = options.str("scallop", "none");
        float railHeight = options.num("rail_height", 9, 0.2f, 40);
        float railWidth = options.num("rail_width", 4, 0.2f, 40);
        int rails = Math.round(options.num("rails", "count", "rail_count", height > 150 ? 3 : 2, 1, 6));
        for (int i = 0; i < rails; i++) {
          rail(span, rails == 1 ? 0.6f : 0.2f + 0.6f * i / (rails - 1), railWidth, railHeight, -railWidth / 2, colors.rail);
        }
        float pitch = (width + gap) * pitchScale;
        int count = Math.max(1, Math.round(span.length / pitch));
        float depth = Math.min(25, height * 0.18f);
        for (int i = 0; i < count; i++) {
          float ratio = (i + 0.5f) / count;
          float drop = scallop.equals("concave") ? depth * (float)Math.sin(Math.PI * ratio)
              : scallop.equals("convex") ? depth * (1 - (float)Math.sin(Math.PI * ratio)) : 0;
          board(span, ratio, Math.min(width, span.length / count), 2, span.bottom(ratio), span.top(ratio) - drop, 2, colors.infill, top);
        }
        break;
      }
      case SOLID : {
        String top = options.str("top", "flat");
        boolean horizontal = "horizontal".equals(options.str("boards", "vertical"));
        int rails = Math.round(options.num("rails", "count", "rail_count", height > 150 ? 3 : 2, 1, 6));
        for (int i = 0; i < rails; i++) {
          rail(span, rails == 1 ? 0.6f : 0.15f + 0.7f * i / (rails - 1), 4, 9, -3, colors.rail);
        }
        if (!top.equals("flat") && !horizontal) {
          float width = options.num("picket_width", 14, 1, 60) * pitchScale;
          int count = Math.max(1, Math.round(span.length / width));
          for (int i = 0; i < count; i++) {
            float ratio = (i + 0.5f) / count;
            board(span, ratio, span.length / count, 2, span.bottom(ratio), span.top(ratio), 1, colors.infill, top);
          }
        } else {
          AssistantMeshBuilder.Material boards = colors.infill.isTextured() ? colors.infill
              : named("boards", getPattern(horizontal ? "hboards" : "boards", colors.infillColor));
          slab(span, 0, 0, 2, 0, boards);
          this.result.boards += Math.max(1, Math.round(horizontal ? height / 14 : span.length / 14));
        }
        if (options.bool("cap_rail", false)) {
          railUnderTop(span, -3, 9, 3, 0, colors.rail);
        }
        if (options.bool("trim_board", false)) {
          railUnderTop(span, 2, 2, 9, 2, colors.rail);
        }
        break;
      }
      case DOUBLE_BOARDS : {
        float width = options.num("picket_width", 14, 1, 60);
        boolean both = "both".equals(options.str("layers", "same"));
        int rails = height > 150 ? 3 : 2;
        for (int i = 0; i < rails; i++) {
          rail(span, 0.15f + 0.7f * i / (rails - 1), 4, 9, 0, colors.rail);
        }
        float pitch = width * (both ? 1.7f : 1.5f) * pitchScale;
        int count = Math.max(1, Math.round(span.length / pitch));
        for (int i = 0; i < count; i++) {
          float ratio = (i + 0.25f) / count;
          float ratio2 = (i + 0.75f) / count;
          board(span, ratio, width, 2, span.bottom(ratio), span.top(ratio), 4, colors.infill, "flat");
          board(span, ratio2, width, 2, span.bottom(ratio2), span.top(ratio2), both ? -2 : 6, colors.infill, "flat");
        }
        break;
      }
      case HSLAT : {
        float slat = options.num("slat_height", 14, 1, 80);
        float gap = options.num("gap", 1.5f, 0, 60);
        float thickness = options.num("slat_thickness", 2, 0.5f, 20);
        if ("vertical".equals(options.str("orientation", "horizontal"))) {
          int count = Math.max(1, Math.round(span.length / ((slat + gap) * pitchScale)));
          for (int i = 0; i < count; i++) {
            float ratio = (i + 0.5f) / count;
            board(span, ratio, slat, thickness, span.bottom(ratio), span.top(ratio), thickness / 2, colors.infill, "flat");
          }
        } else {
          int count = Math.max(1, Math.min(120, (int)Math.floor((height + gap) / (slat + gap))));
          for (int i = 0; i < count; i++) {
            float low = i * (slat + gap);
            box(span, 0, 1, span.b1 + low, span.b1 + low + slat, span.b2 + low, span.b2 + low + slat, thickness, 0, colors.infill);
            this.result.boards++;
          }
        }
        String frame = options.str("frame", "none");
        if (frame.equals("aluminum") || frame.equals("steel") || frame.equals("wood")) {
          railUnderTop(span, -2, 5, 3, 0, colors.post);
        }
        break;
      }
      case BASKET : {
        int count = Math.max(2, Math.min(60, Math.round(height / 12)));
        float slat = height / count;
        for (int i = 0; i < count; i++) {
          float low = i * slat;
          float offset = i % 2 == 0 ? 1.5f : -1.5f;
          box(span, 0, 0.5f, span.b1 + low, span.b1 + low + slat - 0.4f, span.bottom(0.5f) + low, span.bottom(0.5f) + low + slat - 0.4f,
              0.8f, offset, colors.infill);
          box(span, 0.5f, 1, span.bottom(0.5f) + low, span.bottom(0.5f) + low + slat - 0.4f, span.b2 + low, span.b2 + low + slat - 0.4f,
              0.8f, -offset, colors.infill);
          this.result.boards++;
        }
        board(span, 0.5f, 3, 3, span.bottom(0.5f), span.top(0.5f), 1.5f, colors.rail, "flat");
        railUnderTop(span, -2, 6, 2, 0, colors.rail);
        break;
      }
      case LATTICE : {
        float cell = options.num("lattice_cell", 12, 2, 100);
        String pattern = "square".equals(options.str("lattice_pattern", "diagonal")) ? "lattice_square" : "lattice_diagonal";
        AssistantMeshBuilder.Material lattice = named("lattice",
            this.mesh.getMaterial(pattern, cell, cell, colors.infillColor, colors.infillColor, 0.3f));
        boolean framed = !"none".equals(options.str("frame", "wood"));
        if (framed) {
          railUnderTop(span, 0, 4, 6, 0, colors.rail);
          railAboveBottom(span, 0, 4, 6, 0, colors.rail);
        }
        float inset = framed ? 3 : 0;
        sheet(span, 0, 1, span.b1 + inset, span.t1 - inset, span.b2 + inset, span.t2 - inset, 0, lattice);
        break;
      }
      case LATTICE_TOP : {
        float latticeHeight = Math.min(height * 0.6f, options.num("lattice_height", 30, 5, 300));
        String pattern = "square".equals(options.str("lattice_pattern", "square")) ? "lattice_square" : "lattice_diagonal";
        AssistantMeshBuilder.Material lattice = named("lattice",
            this.mesh.getMaterial(pattern, 10, 10, colors.infillColor, colors.infillColor, 0.3f));
        slab(span, 0, latticeHeight, 2, 0, colors.infill.isTextured() ? colors.infill : named("boards", getPattern("boards", colors.infillColor)));
        this.result.boards += Math.max(1, Math.round(span.length / 14));
        railUnderTop(span, latticeHeight - 3, 4, 6, 0, colors.rail);
        railUnderTop(span, 0, 4, 5, 0, colors.rail);
        rail(span, 0.2f, 4, 9, -3, colors.rail);
        sheet(span, 0, 1, span.t1 - latticeHeight + 3, span.t1 - 5, span.t2 - latticeHeight + 3, span.t2 - 5, 0, lattice);
        break;
      }
      case RAILS : {
        int count = Math.round(options.num("rails", "count", "rail_count", 3, 1, 8));
        float railHeight = options.num("rail_height", 9, 0.5f, 60);
        float railWidth = options.num("rail_width", 4, 0.5f, 40);
        float offset = options.num("rail_offset", 0, -50, 50);
        for (int i = 0; i < count; i++) {
          rail(span, count == 1 ? 1 : 0.3f + 0.65f * i / (count - 1), railWidth, railHeight, offset, colors.infill);
        }
        break;
      }
      case CROSSBUCK : {
        float railHeight = options.num("rail_height", 9, 0.5f, 60);
        float railWidth = options.num("rail_width", 4, 0.5f, 40);
        rail(span, 0.95f, railWidth, railHeight, 0, colors.infill);
        rail(span, 0.3f, railWidth, railHeight, 0, colors.infill);
        float low1 = span.at(0, 0.3f) - railHeight;
        float low2 = span.at(1, 0.3f) - railHeight;
        float high1 = span.at(0, 0.95f);
        float high2 = span.at(1, 0.95f);
        box(span, 0, 1, low1, low1 + railHeight, high2 - railHeight, high2, railWidth, railWidth, colors.infill);
        box(span, 0, 1, high1 - railHeight, high1, low2, low2 + railHeight, railWidth, -railWidth, colors.infill);
        this.result.rails += 2;
        break;
      }
      case ZIGZAG : {
        int count = Math.round(options.num("rails", "count", "rail_count", 5, 2, 12));
        float railHeight = height / count;
        for (int i = 0; i < count; i++) {
          float low = i * railHeight;
          box(span, -0.04f, 1.04f, span.g1 + low, span.g1 + low + railHeight * 0.85f, span.g2 + low, span.g2 + low + railHeight * 0.85f,
              railHeight * 0.8f, i % 2 == 0 ? 2 : -2, colors.infill);
          this.result.rails++;
        }
        break;
      }
      case BARS : {
        float spacing = options.num("picket_spacing", 11, 2, 60) * pitchScale;
        float size = options.num("picket_size", 2, 0.5f, 10);
        String finials = options.str("finials", "spear");
        if (options.bool("flat_top", false)) {
          finials = "none";
        } else if (options.bool("press_point", false)) {
          finials = "spear";
        }
        int rails = Math.round(options.num("rails", "count", "rail_count", 3, 2, 4));
        boolean flat = finials.equals("none") || finials.equals("flat");
        float topInset = flat ? 0 : Math.min(15, height * 0.12f);
        railUnderTop(span, topInset, 3, 4, 0, colors.rail);
        railAboveBottom(span, 8, 3, 4, 0, colors.rail);
        float secondInset = topInset + Math.min(18, height * 0.14f);
        if (rails >= 3) {
          railUnderTop(span, secondInset, 3, 4, 0, colors.rail);
        }
        if (rails >= 4) {
          railAboveBottom(span, 8 + Math.min(18, height * 0.14f), 3, 4, 0, colors.rail);
        }
        int count = Math.max(1, Math.round(span.length / spacing));
        boolean rings = options.bool("rings", false) || options.bool("scrolls", false);
        boolean puppy = options.bool("puppy_pickets", false) || "puppy_pickets".equals(options.str("bottom_rail", ""));
        for (int i = 0; i < count; i++) {
          float ratio = (i + 0.5f) / count;
          board(span, ratio, size, size, span.bottom(ratio), span.top(ratio), size / 2, colors.infill, flat ? "flat" : "pointed");
          if (!flat && !finials.equals("spear")) {
            // A ball, or the wings of a flair or of a fleur-de-lis, under the point
            float finialSize = finials.equals("ball") ? size * 2 : size * 2.6f;
            board(span, ratio, finialSize, finials.equals("ball") ? finialSize : size, span.top(ratio) - topInset * 0.75f,
                span.top(ratio) - topInset * 0.75f + (finials.equals("ball") ? finialSize : size * 1.6f),
                finials.equals("ball") ? finialSize / 2 : size / 2, colors.infill, finials.equals("ball") ? "flat" : "pointed");
          }
          if (rings && rails >= 3 && i % 2 == 0 && i + 1 < count) {
            float ringRatio = (i + 1f) / count;
            float ringSize = Math.min(span.length / count - size, secondInset - topInset - 5);
            float center = span.top(ringRatio) - (topInset + secondInset) / 2 - 2;
            board(span, ringRatio, ringSize, 0.8f, center - ringSize / 2, center + ringSize / 2, 0.4f, colors.infill, "rounded");
          }
          if (puppy && i + 1 < count) {
            float puppyRatio = (i + 1f) / count;
            board(span, puppyRatio, size, size, span.bottom(puppyRatio), span.bottom(puppyRatio) + Math.min(45, height * 0.4f),
                size / 2, colors.infill, "flat");
          }
        }
        break;
      }
      case SHEET : {
        String pattern = options.str("pattern", "none");
        float thickness = options.num("thickness", 1.5f, 0.2f, 30);
        if (pattern.equals("custom_svg")) {
          this.result.notes.add("a custom SVG pattern can't be cut: the panels are plain");
          pattern = "none";
        }
        String frame = options.str("frame", "none");
        if (frame.equals("wood") || frame.equals("steel") || frame.equals("metal")) {
          railUnderTop(span, 0, 4, 9, -3, colors.post);
          railAboveBottom(span, 0, 4, 9, -3, colors.post);
        }
        if (pattern.equals("holes") || pattern.equals("geometric") || pattern.equals("foliage")) {
          float open = options.num("open_area", pattern.equals("holes") ? 40 : 35, 2, 70) / 100;
          AssistantMeshBuilder.Material pierced = named("panels", pattern.equals("geometric")
              ? this.mesh.getMaterial("lattice_diagonal", 30, 30, colors.infillColor, colors.infillColor, 1 - open)
              : pattern.equals("foliage")
                  ? this.mesh.getMaterial("leaves", 60, 60, colors.infillColor, colors.infillColor, open)
                  : this.mesh.getMaterial("holes", options.num("hole_size", 2.5f, 0.3f, 30), options.num("hole_size", 2.5f, 0.3f, 30),
                        colors.infillColor, colors.infillColor, open));
          options.get("hole_pattern");
          sheet(span, 0, 1, span.b1, span.t1, span.b2, span.t2, 0, pierced);
        } else {
          slab(span, 0, 0, thickness, 0, colors.infill.isTextured() ? colors.infill
              : pattern.equals("corrugated") ? named("panels", getPattern("corrugated", colors.infillColor)) : colors.infill);
        }
        break;
      }
      case GLASS : {
        boolean framed = "framed".equals(options.str("frame", "frameless"));
        float gap = framed ? 0 : 0.02f;
        box(span, gap, 1 - gap, span.bottom(gap), span.top(gap), span.bottom(1 - gap), span.top(1 - gap), 1.2f, 0,
            named("glass", this.mesh.getMaterial(colors.infillColor, 0.35f)));
        if (framed) {
          railAboveBottom(span, -3, 3, 3, 0, colors.rail);
        }
        if (framed || options.bool("cap_rail", false)) {
          railUnderTop(span, -4, 5, 4, 0, colors.rail);
        }
        break;
      }
      case GABION : {
        float size = options.num("basket_size", 50, 10, 300);
        int stone = options.color("stone_color", colors.infillColor);
        slab(span, -this.sink, 0, size, 0, named("stones", getPattern("fieldstone", stone)));
        // The wires of the baskets
        AssistantMeshBuilder.Material wire = named("steel", this.mesh.getMaterial(0x6E7275));
        for (float offset = -size / 2; offset <= size / 2; offset += size) {
          railUnderTop(span, -0.5f, 1, 1, offset, wire);
          board(span, 0.002f, 1, 1, span.b1, span.t1, offset + 0.5f, wire, "flat");
          this.result.boards--;
        }
        this.result.rails -= 2;
        break;
      }
      case WALL :
      case HAHA :
      case RETAINING : {
        float thickness = options.num("thickness", 20, 1, 300);
        String pattern = options.str("pattern", "stucco");
        String stone = options.str("stone", null);
        if (stone != null) {
          pattern = stone;
        }
        String bond = options.str("bond", null);
        if (bond != null && pattern.startsWith("brick")) {
          pattern = "brick_" + bond;
        }
        AssistantMeshBuilder.Material wall = colors.infill.isTextured() ? colors.infill : named("wall", getPattern(pattern, colors.infillColor));
        float low1 = span.g1 - this.sink;
        float low2 = span.g2 - this.sink;
        float high1 = span.t1;
        float high2 = span.t2;
        if (infill == Infill.HAHA) {
          // From the ground down
          high1 = span.g1;
          high2 = span.g2;
          low1 = span.g1 - height;
          low2 = span.g2 - height;
        } else if (infill == Infill.RETAINING) {
          // The ground of both sides
          float reach = thickness / 2 + 40;
          String retains = options.str("retains", "auto");
          float [] grounds = new float [4];
          for (int end = 0; end < 2; end++) {
            float x = end == 0 ? span.x1 : span.x2;
            float y = end == 0 ? span.y1 : span.y2;
            grounds [end * 2] = ground(x - span.dy * reach, y + span.dx * reach);
            grounds [end * 2 + 1] = ground(x + span.dy * reach, y - span.dx * reach);
          }
          boolean leftHigh = retains.startsWith("l") ? true : retains.startsWith("r") ? false
              : grounds [0] + grounds [2] >= grounds [1] + grounds [3];
          float reveal = span.t1 - span.g1;
          high1 = (leftHigh ? grounds [0] : grounds [1]) + reveal;
          high2 = (leftHigh ? grounds [2] : grounds [3]) + reveal;
          low1 = Math.min(grounds [0], grounds [1]) - 15;
          low2 = Math.min(grounds [2], grounds [3]) - 15;
          this.result.retained = Math.max(this.result.retained,
              Math.max(Math.abs(grounds [0] - grounds [1]), Math.abs(grounds [2] - grounds [3])));
        }
        box(span, 0, 1, low1, high1, low2, high2, thickness, 0, wall);
        Object coping = options.get("coping");
        if (coping == null) {
          coping = options.get("cap");
        }
        if (infill != Infill.HAHA && coping != null && !Boolean.FALSE.equals(coping) && !"none".equals(coping.toString())
            && !"false".equals(coping.toString())) {
          box(span, 0, 1, high1, high1 + 6, high2, high2 + 6, thickness + 6, 0,
              named("coping", this.mesh.getMaterial(options.color("coping_color", shade(colors.infillColor, 1.15f)))));
        }
        break;
      }
      case PIER_PANEL : {
        Object nested = options.get("infill");
        Map<?, ?> nestedMap = nested instanceof Map ? (Map<?, ?>)nested
            : Collections.singletonMap("style", nested != null ? nested.toString() : "ornamental_steel");
        Style nestedStyle = getStyle(nestedMap.get("style") != null ? nestedMap.get("style").toString() : "ornamental_steel");
        if (nestedStyle == null || nestedStyle.infill == Infill.PIER_PANEL || nestedStyle.infill == Infill.HEDGE) {
          throw new IOException("\"infill\" gives the style between the piers, like {\"style\":\"ornamental_steel\"}");
        }
        Options nestedOptions = new Options(nestedMap, nestedStyle, options);
        float pier = options.num("post", "size", "post_size", 40, 1, 200) / 2;
        Span inner = inset(span, pier, pier);
        infill(nestedOptions, new Colors(nestedOptions, "panel_"), inner, leaf);
        break;
      }
      case ROLL : {
        float thickness = options.num("thickness", 3, 0.5f, 30);
        slab(span, 0, 0, thickness, 0, colors.infill.isTextured() ? colors.infill
            : named("screen", getPattern(options.str("pattern", "reed"), colors.infillColor)));
        break;
      }
      case PALISADE : {
        float width = options.num("picket_width", 15, 3, 60) * pitchScale;
        int count = Math.max(1, Math.round(span.length / width));
        for (int i = 0; i < count; i++) {
          float ratio = (i + 0.5f) / count;
          board(span, ratio, span.length / count, width / pitchScale, span.bottom(ratio) - this.sink - 5, span.top(ratio),
              width / pitchScale / 2, colors.infill, options.str("top", "pointed"));
        }
        break;
      }
      case LEAFY : {
        int count = Math.round(options.num("strands", 4, 1, 12));
        for (int i = 0; i < count; i++) {
          rail(span, 0.25f + 0.7f * i / Math.max(1, count - 1), 0.5f, 0.5f, 0, named("wires", this.mesh.getMaterial(0x8E9398)));
        }
        sheet(span, 0, 1, span.b1 + height * 0.15f, span.t1, span.b2 + height * 0.15f, span.t2, 1,
            named("leaves", this.mesh.getMaterial("leaves", 50, 50, colors.infillColor, shade(colors.infillColor, 1.3f), 0.3f)));
        board(span, 0.5f, 5, 5, span.bottom(0.5f) - 5, span.at(0.5f, 0.4f), 0, named("soil", this.mesh.getMaterial(0x5B4634)), "flat");
        this.result.boards--;
        break;
      }
      default :
        break;
    }
  }

  /**
   * Returns a span shorter at its ends.
   */
  private static Span inset(Span span, float start, float end) {
    float ratio1 = Math.min(0.45f, start / span.length);
    float ratio2 = 1 - Math.min(0.45f, end / span.length);
    Span inner = new Span(span.x(ratio1), span.y(ratio1), span.x(ratio2), span.y(ratio2));
    inner.b1 = span.bottom(ratio1);
    inner.b2 = span.bottom(ratio2);
    inner.t1 = span.top(ratio1);
    inner.t2 = span.top(ratio2);
    inner.g1 = span.g1 + (span.g2 - span.g1) * ratio1;
    inner.g2 = span.g1 + (span.g2 - span.g1) * ratio2;
    return inner;
  }

  // ---- Gates ----

  private static String text(Map<?, ?> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value != null ? value.toString().trim().toLowerCase(Locale.ENGLISH) : defaultValue;
  }

  /**
   * Returns the leaf of a gate: a span from a hinge, turned by an angle around it.
   */
  private Span getLeaf(float hingeX, float hingeY, float dx, float dy, float length, float angle, float low, float high) {
    double cos = Math.cos(Math.toRadians(angle));
    double sin = Math.sin(Math.toRadians(angle));
    float leafX = (float)(dx * cos - dy * sin);
    float leafY = (float)(dx * sin + dy * cos);
    Span leaf = new Span(hingeX, hingeY, hingeX + leafX * length, hingeY + leafY * length);
    leaf.b1 = leaf.b2 = low;
    leaf.t1 = leaf.t2 = high;
    leaf.g1 = leaf.g2 = low;
    return leaf;
  }

  /**
   * Adds a leaf with its frame and what fills it.
   * @param arch the height added in the middle of the whole gate (negative: taken away), the leaf
   *             covering the ratios <code>archStart</code> to <code>archEnd</code> of its width
   */
  private void addLeaf(Options options, Colors colors, Span leaf, float arch, float archStart, float archEnd) throws IOException {
    float frame = options.style.family.equals("Wood") ? 4 : 3;
    AssistantMeshBuilder.Material frameMaterial = colors.rail;
    int parts = arch != 0 ? 6 : 1;
    for (int i = 0; i < parts; i++) {
      float ratio1 = (float)i / parts;
      float ratio2 = (float)(i + 1) / parts;
      Span part = new Span(leaf.x(ratio1), leaf.y(ratio1), leaf.x(ratio2), leaf.y(ratio2));
      part.b1 = part.b2 = leaf.b1;
      part.g1 = part.g2 = leaf.g1;
      part.t1 = leaf.t1 + arch * (float)Math.sin(Math.PI * (archStart + (archEnd - archStart) * ratio1));
      part.t2 = leaf.t1 + arch * (float)Math.sin(Math.PI * (archStart + (archEnd - archStart) * ratio2));
      if (part.t1 - part.b1 < 10 || part.t2 - part.b2 < 10) {
        part.t1 = Math.max(part.t1, part.b1 + 10);
        part.t2 = Math.max(part.t2, part.b2 + 10);
      }
      infill(options, colors, part, true);
      // Its frame
      railUnderTop(part, 0, frame, frame, 0, frameMaterial);
      railAboveBottom(part, 0, frame, frame, 0, frameMaterial);
      this.result.rails -= 2;
      if (i == 0) {
        board(part, frame / 2 / part.length, frame, frame, part.b1, part.t1, frame / 2, frameMaterial, "flat");
        this.result.boards--;
      }
      if (i == parts - 1) {
        board(part, 1 - frame / 2 / part.length, frame, frame, part.b2, part.t2, frame / 2, frameMaterial, "flat");
        this.result.boards--;
      }
    }
    if (options.style.family.equals("Wood") && arch == 0 && leaf.length > 60) {
      // A brace from the bottom of the hinge side
      box(leaf, 0.03f, 0.97f, leaf.b1 + frame, leaf.b1 + frame * 2, leaf.t2 - frame * 2, leaf.t2 - frame, frame, -frame, frameMaterial);
    }
  }

  private void addGate(Opening opening, float [][] points, double [] distances, float [] pointHeights,
                       Colors fenceColors, float postSize) throws IOException {
    Map<?, ?> map = opening.map;
    for (Object key : map.keySet()) {
      this.result.usedKeys.add("openings." + key);
    }
    float [] start = getPoint(points, distances, opening.start);
    float [] end = getPoint(points, distances, opening.end);
    Span line = new Span(start [0], start [1], end [0], end [1]);
    float width = line.length;
    float ground = Math.max(ground(start [0], start [1]), ground(end [0], end [1]));
    float fenceHeight = getHeight(points, distances, pointHeights, (opening.start + opening.end) / 2);
    float height = AssistantFurnitureBuilder.getNumber(map, "height", fenceHeight);
    String type = opening.type;
    String name = map.get("name") != null ? map.get("name").toString() : null;
    String description = (name != null ? "\"" + name + "\" " : "") + type + " " + format(width) + " cm wide at "
        + format((opening.start + opening.end) / 2) + " cm";
    if (type.equals("gap")) {
      this.result.gates.add(description + ", left open");
      return;
    }
    // What fills the leaves
    Style style = this.options.style;
    if (map.get("style") != null) {
      style = getStyle(map.get("style").toString());
      if (style == null) {
        throw new IOException("unknown gate \"style\" \"" + map.get("style") + "\"");
      }
    }
    if (style.infill == Infill.WALL || style.infill == Infill.GABION || style.infill == Infill.RETAINING
        || style.infill == Infill.HAHA || style.infill == Infill.BOLLARDS || style.infill == Infill.ZIGZAG
        || style.infill == Infill.PIER_PANEL || style.infill == Infill.HEDGE || style.infill == Infill.PALISADE
        || style.infill == Infill.STRANDS || style.infill == Infill.LEAFY) {
      // A wall, or wires, have a gate of bars or of boards
      style = style.family.equals("Masonry and solid walls") ? getStyle("ornamental_steel")
          : style.family.equals("Wire and mesh") ? getStyle("pipe_fence") : getStyle("picket");
    }
    Options options = new Options(map, style, this.options);
    Colors colors = new Colors(options, "gate_");
    float clearance = type.equals("sliding") || type.equals("rolling") ? 6 : 5;
    float low = ground + clearance;
    float high = ground + height;
    String swing = text(map, "swing", "in");
    String hinge = text(map, "hinge", text(map, "hinge_side", "start"));
    float openAngle = Math.max(0, Math.min(180, AssistantFurnitureBuilder.getNumber(map, "open_angle", 0)));
    float open = Math.max(0, Math.min(1, AssistantFurnitureBuilder.getNumber(map, "open", openAngle / 90)));
    // A gate opens to the inside, the side opposite to the finished one
    float turn = (swing.startsWith("out") ? 1 : -1) * this.side;
    boolean hingeAtEnd = hinge.equals("end") || hinge.equals("right");
    float margin = 2;
    String operator = text(map, "operator", "none");
    String latch = text(map, "latch", type.contains("swing") || type.contains("arch") || type.equals("arbor_gate") ? "gravity" : "none");
    boolean leafGate = false;
    if (type.equals("single_swing") || type.equals("arbor_gate") || type.equals("walk_through_kissing_gate")
        || (type.equals("arched_top") && width <= 200)) {
      float arch = type.equals("arched_top") ? getArch(map, height) : 0;
      Span leaf = hingeAtEnd
          ? getLeaf(end [0] - line.dx * margin, end [1] - line.dy * margin, -line.dx, -line.dy, width - 2 * margin, -turn * openAngle, low, high)
          : getLeaf(start [0] + line.dx * margin, start [1] + line.dy * margin, line.dx, line.dy, width - 2 * margin, turn * openAngle, low, high);
      addLeaf(options, colors, leaf, arch, 0, 1);
      addLatch(leaf, latch, ground, colors);
      leafGate = true;
    } else if (type.equals("double_swing") || type.equals("drive_arch") || type.equals("arched_top") || type.equals("bifold")) {
      float arch = type.equals("drive_arch") || type.equals("arched_top") ? getArch(map, height) : 0;
      float half = width / 2 - margin - 0.5f;
      if (type.equals("bifold")) {
        // Each side folds in two panels
        float fold = open * 80;
        for (int end2 = 0; end2 < 2; end2++) {
          float sign = end2 == 0 ? 1 : -1;
          float hx = end2 == 0 ? start [0] + line.dx * margin : end [0] - line.dx * margin;
          float hy = end2 == 0 ? start [1] + line.dy * margin : end [1] - line.dy * margin;
          Span first = getLeaf(hx, hy, sign * line.dx, sign * line.dy, half / 2, sign * turn * fold, low, high);
          Span second = getLeaf(first.x2, first.y2, sign * line.dx, sign * line.dy, half / 2, -sign * turn * fold, low, high);
          addLeaf(options, colors, first, 0, 0, 1);
          addLeaf(options, colors, second, 0, 0, 1);
        }
      } else {
        Span left = getLeaf(start [0] + line.dx * margin, start [1] + line.dy * margin, line.dx, line.dy, half, turn * openAngle, low, high);
        Span right = getLeaf(end [0] - line.dx * margin, end [1] - line.dy * margin, -line.dx, -line.dy, half, -turn * openAngle, low, high);
        addLeaf(options, colors, left, arch, 0, 0.5f);
        addLeaf(options, colors, right, arch, 1, 0.5f);
        addLatch(left, latch.equals("gravity") ? "drop_rod" : latch, ground, colors);
      }
      leafGate = true;
    } else if (type.equals("sliding") || type.equals("rolling") || type.equals("cantilever")) {
      String direction = text(map, "slide_direction", "start");
      float sign = direction.equals("end") || direction.equals("right") ? 1 : -1;
      float tail = type.equals("cantilever") ? width * 0.4f : 0;
      float shift = sign * open * width;
      float offset = -this.side * (postSize / 2 + 5);
      float fromX = start [0] + line.dx * (shift - (sign < 0 ? tail : 0)) - line.dy * offset;
      float fromY = start [1] + line.dy * (shift - (sign < 0 ? tail : 0)) + line.dx * offset;
      Span leaf = getLeaf(fromX, fromY, line.dx, line.dy, width + tail, 0, type.equals("cantilever") ? low + 4 : low, high);
      addLeaf(options, colors, leaf, 0, 0, 1);
      if (!type.equals("cantilever")) {
        // The track, across the opening and where the gate goes when it opens
        Span track = getLeaf(start [0] + line.dx * (sign < 0 ? -width : 0) - line.dy * offset,
            start [1] + line.dy * (sign < 0 ? -width : 0) + line.dx * offset, line.dx, line.dy, width * 2, 0, ground, ground + 3);
        box(track, 0, 1, ground, ground + 3, ground, ground + 3, 5, 0, named("steel", this.mesh.getMaterial(0x6E7275)));
      } else {
        // Two guide posts beyond the opening
        float guide = sign < 0 ? -width * 0.35f : width * 1.35f;
        post(start [0] + line.dx * guide, start [1] + line.dy * guide, line.dx, line.dy, postSize, "square", ground - this.sink,
            high + 10, colors.post, "flat", colors.cap);
        this.result.gatePosts++;
      }
      description += ", slides to the " + (sign < 0 ? "start" : "end") + " of the fence and needs " + format(width + tail)
          + " cm free along it";
      // The run of fence the gate slides along
      double total = distances [distances.length - 1];
      double gateWidth = opening.end - opening.start;
      double run = this.result.closed ? total - gateWidth : sign < 0 ? opening.start : total - opening.end;
      double otherRun = this.result.closed ? run : sign < 0 ? total - opening.end : opening.start;
      if (run < width + tail - 1) {
        String problem = "the " + type + " gate at " + format((opening.start + opening.end) / 2) + " cm is " + format(width)
            + " cm wide and needs " + format(width + tail) + " cm of fence on the side of its " + (sign < 0 ? "start" : "end")
            + " to slide: " + format(run) + " cm are there"
            + (otherRun >= width + tail - 1 ? ". \"slide_direction\":\"" + (sign < 0 ? "end" : "start") + "\" slides it the other way, where "
                + format(otherRun) + " cm are free" : ". A narrower gate, a longer fence or a gate that swings fits");
        this.result.slideProblems.add(problem);
        description += " (only " + format(run) + " cm there)";
      }
    } else if (type.equals("vertical_lift")) {
      Span leaf = getLeaf(start [0] + line.dx * margin, start [1] + line.dy * margin, line.dx, line.dy, width - 2 * margin, 0,
          low + open * height, high + open * height);
      addLeaf(options, colors, leaf, 0, 0, 1);
      for (int end2 = 0; end2 < 2; end2++) {
        float [] place = end2 == 0 ? start : end;
        post(place [0] - line.dy * 8 * this.side, place [1] + line.dx * 8 * this.side, line.dx, line.dy, postSize, "square",
            ground - this.sink, ground + height * 2 + 20, colors.post, "flat", colors.cap);
      }
      description += ", lifts between two guides " + format(height * 2 + 20) + " cm high";
    } else if (type.equals("arm_barrier")) {
      float [] place = hingeAtEnd ? end : start;
      float sign = hingeAtEnd ? -1 : 1;
      this.mesh.addBox(place [0] - 15, ground, place [1] - 15, place [0] + 15, ground + 105, place [1] + 15, named("gate_barrier", this.mesh.getMaterial(0xE0E0DC)));
      double angle = Math.toRadians(Math.min(90, openAngle));
      float reach = (float)(Math.cos(angle) * (width - 10));
      float rise = (float)(Math.sin(angle) * (width - 10));
      Span arm = getLeaf(place [0], place [1], sign * line.dx, sign * line.dy, Math.max(1, reach), 0, ground, ground + 95);
      box(arm, 0, 1, ground + 87, ground + 95, ground + 87 + rise, ground + 95 + rise, 6, 0, named("gate_arm", this.mesh.getMaterial(0xD8402A)));
    } else if (type.equals("pedestrian_turnstile")) {
      float centerX = (start [0] + end [0]) / 2;
      float centerY = (start [1] + end [1]) / 2;
      post(centerX, centerY, line.dx, line.dy, 12, "round", ground - this.sink, ground + 105, colors.post, "flat", colors.cap);
      for (int i = 0; i < 3; i++) {
        Span arm = getLeaf(centerX, centerY, line.dx, line.dy, Math.min(45, width / 2 - 5), i * 120 + 30, ground, ground + 95);
        box(arm, 0, 1, ground + 91, ground + 95, ground + 91, ground + 95, 4, 0, colors.rail);
      }
    } else if (type.equals("cattle_guard")) {
      // Bars over a pit, that cattle doesn't cross
      float depth = Math.max(150, AssistantFurnitureBuilder.getNumber(map, "depth", 240));
      int bars = Math.max(4, Math.round(depth / 20));
      for (int i = 0; i < bars; i++) {
        float offset = -depth / 2 + depth * (i + 0.5f) / bars;
        box(line, 0, 1, ground + 2, ground + 9, ground + 2, ground + 9, 8, offset, named("steel", this.mesh.getMaterial(0x6E7275)));
      }
      description += ", " + format(depth) + " cm deep";
    }
    if (type.equals("arbor_gate")) {
      // A pergola over the gate
      float top = ground + Math.max(height + 60, 220);
      float depth = 60;
      AssistantMeshBuilder.Material wood = colors.post;
      for (int end2 = 0; end2 < 2; end2++) {
        float [] place = end2 == 0 ? start : end;
        for (int sideIndex = -1; sideIndex <= 1; sideIndex += 2) {
          post(place [0] - line.dy * depth / 2 * sideIndex, place [1] + line.dx * depth / 2 * sideIndex, line.dx, line.dy, 9, "square",
              ground - this.sink, top, wood, "none", wood);
        }
      }
      for (int sideIndex = -1; sideIndex <= 1; sideIndex += 2) {
        box(line, -0.15f, 1.15f, top, top + 12, top, top + 12, 4, depth / 2 * sideIndex, wood);
      }
      int rafters = Math.max(3, Math.round(width / 30));
      for (int i = 0; i < rafters; i++) {
        float ratio = (i + 0.5f) / rafters;
        Span rafter = getLeaf(line.x(ratio) + line.dy * (depth / 2 + 15), line.y(ratio) - line.dx * (depth / 2 + 15),
            -line.dy, line.dx, depth + 30, 0, top + 12, top + 20);
        box(rafter, 0, 1, top + 12, top + 20, top + 12, top + 20, 4, 0, wood);
      }
    } else if (type.equals("walk_through_kissing_gate")) {
      // A small pen on the inside where the gate swings
      float reach = width + 20;
      float sign = -this.side;
      float [][] pen = {{start [0], start [1]},
                        {start [0] - line.dy * reach * sign - line.dx * 10, start [1] + line.dx * reach * sign - line.dy * 10},
                        {end [0] - line.dy * reach * sign + line.dx * 10, end [1] + line.dx * reach * sign + line.dy * 10}};
      for (int i = 0; i < 2; i++) {
        Span rail = new Span(pen [i][0], pen [i][1], pen [i + 1][0], pen [i + 1][1]);
        rail.b1 = rail.b2 = low;
        rail.t1 = rail.t2 = high;
        for (int k = 0; k < 3; k++) {
          rail(rail, 0.3f + 0.3f * k, 4, 9, 0, colors.rail);
        }
        post(pen [i + 1][0], pen [i + 1][1], rail.dx, rail.dy, postSize, "square", ground - this.sink, high + 6, colors.post, "flat", colors.cap);
        this.result.gatePosts++;
      }
    }
    if (leafGate) {
      float leafWidth = type.equals("double_swing") || type.equals("drive_arch") || (type.equals("arched_top") && width > 200)
          ? width / 2 : width;
      description += ", swings " + (swing.startsWith("out") ? "out" : swing.startsWith("both") ? "both ways" : "in")
          + (type.equals("bifold") ? ", folding" : ", hinged at its " + (hingeAtEnd ? "end" : "start"))
          + ", needs " + format(leafWidth) + " cm free to open";
    }
    if (!operator.equals("none")) {
      float [] place = hingeAtEnd ? end : start;
      this.mesh.addBox(place [0] - line.dy * 25 * turn - 12, ground, place [1] + line.dx * 25 * turn - 12,
          place [0] - line.dy * 25 * turn + 12, ground + 40, place [1] + line.dx * 25 * turn + 12, named("gate_operator", this.mesh.getMaterial(0x4A4F54)));
      description += ", " + operator + " operator";
    }
    if (!latch.equals("none")) {
      description += ", " + latch + " latch";
    }
    if (Boolean.TRUE.equals(map.get("self_closing")) || "true".equals(String.valueOf(map.get("self_closing")))) {
      description += ", self-closing";
    }
    this.result.gates.add(description);
  }

  private static float getArch(Map<?, ?> map, float height) {
    float rise = AssistantFurnitureBuilder.getNumber(map, "arch_height", Math.min(40, height * 0.25f));
    return "concave".equals(text(map, "arch", "convex")) ? -Math.abs(rise) : Math.abs(rise);
  }

  /**
   * Adds the latch at the free end of a leaf.
   */
  private void addLatch(Span leaf, String latch, float ground, Colors colors) {
    if (latch.equals("none")) {
      return;
    }
    // The latch of a pool is out of the reach of a child
    float height = latch.equals("magna_latch_pool") ? 140 : latch.equals("drop_rod") ? 20 : 100;
    float low = Math.min(leaf.t2 - 8, ground + height);
    float size = latch.equals("keypad") || latch.equals("electric_strike") ? 9 : 5;
    board(leaf, 1 - 3 / leaf.length, 5, 4, low, low + (latch.equals("drop_rod") ? 45 : size), -this.side * 3,
        named("gate_hardware", this.mesh.getMaterial(0x2B2B2B)), "flat");
    this.result.boards--;
  }

  private void checkPoolCode(float height, float clearance, List<Opening> openings) throws IOException {
    float gap = this.options.num("picket_spacing", 9, 2, 60) - this.options.num("picket_size", 2, 0.5f, 10);
    if (gap >= 10) {
      this.result.violations.add("the gaps between its pickets are " + format(gap) + " cm wide: under 10 cm for a pool");
    }
    if (height < 122) {
      this.result.violations.add("it is " + format(height) + " cm high: 122 cm at least for a pool");
    }
    if (clearance > 5) {
      this.result.violations.add("it leaves " + format(clearance) + " cm under it: 5 cm at most for a pool");
    }
    for (Opening opening : openings) {
      if (opening.type.equals("gap")) {
        this.result.violations.add("the opening at " + format((opening.start + opening.end) / 2) + " cm has no gate");
        continue;
      }
      boolean selfClosing = Boolean.TRUE.equals(opening.map.get("self_closing")) || "true".equals(String.valueOf(opening.map.get("self_closing")));
      if (!selfClosing) {
        this.result.violations.add("the gate at " + format((opening.start + opening.end) / 2)
            + " cm doesn't close by itself: add \"self_closing\":true");
      }
      if (!text(opening.map, "swing", "in").startsWith("out")) {
        this.result.violations.add("the gate at " + format((opening.start + opening.end) / 2)
            + " cm opens to the pool: \"swing\":\"out\" opens it away");
      }
      if (!text(opening.map, "latch", "gravity").equals("magna_latch_pool")) {
        this.result.violations.add("the latch of the gate at " + format((opening.start + opening.end) / 2)
            + " cm is in reach of a child: \"latch\":\"magna_latch_pool\" puts it 140 cm high");
      }
    }
  }
}
