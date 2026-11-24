/*
 * AssistantPlantBuilder.java 23 nov. 2026
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Builds the models of trees and shrubs from a species and a few sizes: a trunk, branches and
 * masses of leaves, with few polygons and the silhouette of the species, at a season.
 */
final class AssistantPlantBuilder {
  /** The shapes of the crown. */
  enum Form {ROUND, OVAL, COLUMNAR, CONICAL, PINE, VASE, WEEPING, SPREADING, PYRAMIDAL, PALM, SNAG, STUMP, MOUND, GRASS}

  /**
   * A species of tree or of shrub.
   */
  static final class Species {
    final String   name;
    final String   group;
    final String   commonName;
    final Form     form;
    final boolean  evergreen;
    final boolean  shrub;
    /** Usual height in cm. */
    final float    height;
    /** Width of the crown / height. */
    final float    crownRatio;
    /** Height under the crown / height. */
    final float    baseRatio;
    /** Diameter of the trunk / height. */
    final float    trunkRatio;
    final int      bark;
    final int      leaf;
    final int      autumnLeaf;
    /** Color of the flowers in spring (or in summer if negative), or 0. */
    final int      flower;
    final boolean  summerFlowers;
    /** Growth in cm a year. */
    final float    growth;
    final int      trunks;

    private Species(String group, boolean shrub, String [] fields) {
      this.group = group;
      this.shrub = shrub;
      this.name = fields [0];
      this.commonName = fields [1];
      this.form = Form.valueOf(fields [2]);
      this.evergreen = fields [3].startsWith("E");
      this.height = Float.parseFloat(fields [4]);
      this.crownRatio = Float.parseFloat(fields [5]);
      this.baseRatio = Float.parseFloat(fields [6]);
      this.trunkRatio = Float.parseFloat(fields [7]);
      this.bark = Integer.parseInt(fields [8], 16);
      this.leaf = Integer.parseInt(fields [9], 16);
      this.autumnLeaf = fields [10].length() > 0 ? Integer.parseInt(fields [10], 16) : this.leaf;
      this.summerFlowers = fields [11].startsWith("s");
      this.flower = fields [11].length() > 0 ? Integer.parseInt(this.summerFlowers ? fields [11].substring(1) : fields [11], 16) : 0;
      this.growth = Float.parseFloat(fields [12]);
      this.trunks = fields.length > 13 ? Integer.parseInt(fields [13]) : 1;
    }
  }

  private static final Map<String, Species> SPECIES = new LinkedHashMap<String, Species>();
  private static final Map<String, String>  ALIASES = new LinkedHashMap<String, String>();

  /**
   * Adds species: name|aliases, common name, form, E or D, height, crown ratio, base ratio, trunk ratio,
   * bark, leaf, autumn leaf, flowers, growth, trunks.
   */
  private static void add(String group, boolean shrub, String... lines) {
    for (String line : lines) {
      String [] fields = line.split(",", -1);
      String [] names = fields [0].split("\\|");
      fields [0] = names [0];
      SPECIES.put(names [0], new Species(group, shrub, fields));
      for (int i = 1; i < names.length; i++) {
        ALIASES.put(names [i], names [0]);
      }
    }
  }

  static {
    add("Conifers", false,
        "loblolly_pine|pine,Loblolly pine,PINE,E,2700,0.33,0.6,0.022,5B4634,3E5F3A,,,75",
        "shortleaf_pine,Shortleaf pine,PINE,E,2400,0.3,0.6,0.02,6B4A34,446640,,,50",
        "longleaf_pine,Longleaf pine,PINE,E,2800,0.3,0.65,0.02,5B4634,4A6B3C,,,50",
        "slash_pine,Slash pine,PINE,E,2600,0.33,0.62,0.022,6B4A34,3F6238,,,75",
        "virginia_pine,Virginia pine,PINE,E,1400,0.5,0.4,0.022,5B4634,4C6A42,,,45",
        "white_pine|eastern_white_pine,Eastern white pine,CONICAL,E,2400,0.42,0.25,0.025,4E4034,3B6650,,,70",
        "eastern_red_cedar|red_cedar,Eastern red cedar,COLUMNAR,E,1200,0.38,0.08,0.03,6B4F3F,3A5A40,,,35",
        "leyland_cypress,Leyland cypress,COLUMNAR,E,1500,0.3,0.03,0.025,6B4F3F,3F6B45,,,100",
        "arizona_cypress,Arizona cypress,CONICAL,E,1200,0.4,0.05,0.025,7A5A48,7C9C96,,,60",
        "bald_cypress,Bald cypress,CONICAL,D,2200,0.38,0.25,0.035,7A5F4C,5E8A4A,B5651D,,60",
        "eastern_hemlock|hemlock,Eastern hemlock,CONICAL,E,2000,0.45,0.15,0.025,5B4634,2F5A3A,,,35",
        "fraser_fir|fir,Fraser fir,CONICAL,E,1200,0.45,0.08,0.02,6E6A62,2E5A44,,,30",
        "blue_spruce,Colorado blue spruce,CONICAL,E,1400,0.42,0.05,0.022,5E5248,6F8F9A,,,30",
        "norway_spruce|spruce,Norway spruce,CONICAL,E,1800,0.42,0.06,0.025,5E4A3C,2F5638,,,60",
        "deodar_cedar,Deodar cedar,CONICAL,E,1600,0.55,0.08,0.028,5E5248,6A8C7A,,,60",
        "cryptomeria,Japanese cryptomeria,CONICAL,E,1500,0.35,0.1,0.028,7A4A34,3C6444,,,60",
        "arborvitae_green_giant|green_giant|arborvitae,Arborvitae 'Green Giant',COLUMNAR,E,1200,0.3,0.02,0.02,6B4F3F,3D6B3D,,,90",
        "arborvitae_emerald|emerald_arborvitae,Arborvitae 'Emerald',COLUMNAR,E,400,0.25,0.02,0.02,6B4F3F,3F7A48,,,25");
    add("Broadleaf evergreens", false,
        "southern_magnolia|magnolia,Southern magnolia,PYRAMIDAL,E,1800,0.55,0.12,0.03,6E665E,2E4F2E,,F4F1E4,45",
        "magnolia_little_gem|little_gem,Magnolia 'Little Gem',PYRAMIDAL,E,600,0.42,0.1,0.03,6E665E,2E4F2E,,F4F1E4,30",
        "magnolia_dd_blanchard|dd_blanchard,Magnolia 'DD Blanchard',PYRAMIDAL,E,1500,0.45,0.12,0.03,6E665E,2A4A2C,,F4F1E4,45",
        "live_oak,Live oak,SPREADING,E,1500,1.5,0.22,0.06,4E443C,3F5A38,,,60",
        "american_holly|holly,American holly,PYRAMIDAL,E,1200,0.45,0.1,0.03,8A8A84,2F5230,,,30",
        "nellie_stevens_holly|nellie_r_stevens,Holly 'Nellie R. Stevens',PYRAMIDAL,E,600,0.5,0.03,0.03,8A8A84,2A4B2C,,,60",
        "yaupon,Yaupon holly,VASE,E,500,0.7,0.3,0.03,9A968C,3F5E3A,,,30,3",
        "wax_myrtle,Wax myrtle,ROUND,E,450,0.9,0.2,0.03,8A8478,5A7848,,,90,3",
        "cherry_laurel,Cherry laurel,OVAL,E,700,0.65,0.15,0.035,5A4A40,2F5A32,,,60",
        "tea_olive,Tea olive,OVAL,E,450,0.65,0.1,0.03,8A8478,355C34,,,30",
        "loquat,Loquat,ROUND,E,600,0.9,0.3,0.035,6E5E50,3A5A34,,,45",
        "camellia_tree,Camellia (tree form),OVAL,E,400,0.65,0.25,0.03,9A8E80,2C4F2E,,D84A6B,25");
    add("Oaks", false,
        "white_oak|oak,White oak,ROUND,D,2400,0.95,0.3,0.04,9A9488,4A7038,8E3B2A,,35",
        "northern_red_oak|red_oak,Northern red oak,ROUND,D,2200,0.9,0.32,0.04,5E5650,416A34,A0351F,,60",
        "southern_red_oak,Southern red oak,ROUND,D,2400,0.85,0.35,0.04,4E4640,3F6632,9A4A22,,45",
        "water_oak,Water oak,ROUND,D,2000,0.8,0.35,0.04,6E6A64,4A7038,B08A2E,,60",
        "willow_oak,Willow oak,OVAL,D,2100,0.75,0.3,0.04,6E6A64,4F7A3C,C9A22E,,60",
        "scarlet_oak,Scarlet oak,ROUND,D,2100,0.8,0.33,0.038,5A524C,416A34,C1281C,,50",
        "post_oak,Post oak,ROUND,D,1500,0.85,0.35,0.045,7A7268,4A6A38,9A6A2A,,25",
        "chestnut_oak,Chestnut oak,ROUND,D,2000,0.8,0.35,0.04,5A5048,4A7038,C98A2A,,35",
        "pin_oak,Pin oak,PYRAMIDAL,D,2000,0.6,0.25,0.035,6A625A,3F6A32,A8341F,,60",
        "shumard_oak,Shumard oak,ROUND,D,2200,0.8,0.33,0.04,6A625A,3F6A32,B5301C,,60",
        "overcup_oak,Overcup oak,ROUND,D,1500,0.9,0.3,0.04,8A8278,4A7038,B08A2E,,40");
    add("Shade trees", false,
        "sweetgum,Sweetgum,PYRAMIDAL,D,2200,0.6,0.3,0.035,7A7268,3F6E34,A8284A,,60",
        "tulip_poplar|tulip_tree,Tulip poplar,OVAL,D,2800,0.45,0.45,0.035,8A8478,4F8038,E0B62E,,75",
        "red_maple|maple,Red maple,OVAL,D,1700,0.7,0.3,0.035,8A8680,45733A,C8281E,B03A3A,60",
        "sugar_maple,Sugar maple,ROUND,D,2000,0.75,0.3,0.035,7A726A,45733A,E8792A,,35",
        "japanese_maple,Japanese maple,ROUND,D,500,1.1,0.3,0.035,6A6258,7A3030,C8281E,,25,3",
        "japanese_maple_laceleaf|laceleaf_maple,Laceleaf Japanese maple,WEEPING,D,220,1.4,0.25,0.05,6A6258,7A3030,D84A1E,,12",
        "river_birch|birch,River birch,VASE,D,1500,0.7,0.3,0.03,C9A58A,5E8A40,D8B82E,,75,3",
        "mockernut_hickory|hickory,Mockernut hickory,OVAL,D,2200,0.6,0.4,0.035,6E6A64,4A7038,D8A82A,,30",
        "pignut_hickory,Pignut hickory,OVAL,D,2200,0.55,0.4,0.035,6E6A64,4A7038,D8A82A,,30",
        "black_gum|tupelo,Black gum,PYRAMIDAL,D,1700,0.6,0.3,0.035,5A524C,3F6A34,C1281C,,35",
        "american_beech|beech,American beech,ROUND,D,2100,0.85,0.25,0.04,A8A8A2,4A7A3A,C99A3A,,30",
        "sycamore,American sycamore,ROUND,D,2700,0.8,0.35,0.045,D0CCC0,5A8440,B08A3A,,75",
        "black_walnut|walnut,Black walnut,ROUND,D,2200,0.85,0.4,0.04,4A423C,5A8440,D8B82E,,60",
        "pecan,Pecan,ROUND,D,2400,0.9,0.4,0.04,6A625A,5A8440,C9A22E,,45",
        "saucer_magnolia|tulip_magnolia,Saucer magnolia,ROUND,D,700,0.95,0.25,0.04,8A8680,4A7A3A,B08A3A,E8B4D0,35,3",
        "american_elm|elm,American elm,VASE,D,2400,0.8,0.4,0.04,5E5650,4A7A3A,D8B82E,,75",
        "lacebark_elm,Lacebark elm,VASE,D,1400,0.85,0.35,0.04,B08A6A,4A7A3A,C9A22E,,60",
        "zelkova,Japanese zelkova,VASE,D,1700,0.8,0.35,0.035,8A8278,4A7A3A,C8581E,,50",
        "ginkgo,Ginkgo,OVAL,D,1700,0.55,0.3,0.035,7A7268,5A8A40,F0C82A,,30",
        "honey_locust,Honey locust,VASE,D,1500,0.85,0.4,0.03,5A524C,6A9448,E0C02E,,60",
        "black_locust,Black locust,OVAL,D,1700,0.55,0.4,0.035,4A423C,5A8A44,D8B82E,F4F1E4,75",
        "black_cherry,Black cherry,OVAL,D,1800,0.6,0.4,0.035,3E3632,4A7A3A,D88A2A,F4F1E4,60",
        "persimmon,Persimmon,OVAL,D,1300,0.6,0.35,0.035,3E3632,4A7A3A,D8781E,,35",
        "sassafras,Sassafras,OVAL,D,1200,0.6,0.35,0.035,6A4A34,5A8A40,E8792A,,45",
        "hackberry,Hackberry,ROUND,D,1700,0.85,0.35,0.04,9A9A94,5A8440,D8C84A,,45",
        "mimosa|silk_tree,Mimosa,SPREADING,D,800,1.3,0.4,0.035,8A8680,6A9A4A,C9B84A,sF2A0C0,90",
        "bradford_pear|callery_pear,Bradford pear,OVAL,D,1200,0.7,0.2,0.04,5A524C,3F6A34,B03A2A,F8F6EE,75",
        "sourwood,Sourwood,OVAL,D,1000,0.5,0.35,0.03,6A625A,4A7A3A,C1281C,sF4F1E4,30");
    add("Ornamentals", false,
        "crape_myrtle|crepe_myrtle,Crape myrtle,VASE,D,600,0.75,0.35,0.035,C9A58A,4A7A3A,D8581E,sE0448A,60,4",
        "crape_myrtle_natchez|natchez,Crape myrtle 'Natchez',VASE,D,800,0.75,0.4,0.035,B07A5A,4A7A3A,D8781E,sFAF8F2,75,4",
        "crape_myrtle_dwarf,Dwarf crape myrtle,VASE,D,250,0.9,0.25,0.04,C9A58A,4A7A3A,D8581E,sD83A6A,30,5",
        "flowering_dogwood|dogwood,Flowering dogwood,SPREADING,D,700,1.1,0.3,0.035,5A524C,4A7A3A,A8282A,FAF8F2,30",
        "kousa_dogwood,Kousa dogwood,VASE,D,700,0.9,0.3,0.035,8A7A6A,4A7A3A,B03A2A,FAF8F2,30",
        "eastern_redbud|redbud,Eastern redbud,SPREADING,D,700,1.1,0.3,0.035,4A423C,4F7A3C,D8C84A,D060A8,45,2",
        "redbud_forest_pansy|forest_pansy,Redbud 'Forest Pansy',SPREADING,D,600,1.1,0.3,0.035,4A423C,6A3048,C8581E,D060A8,35,2",
        "yoshino_cherry|cherry,Yoshino cherry,SPREADING,D,900,1.1,0.3,0.04,5A4A44,4A7A3A,D8A82A,F8E4EC,60",
        "kwanzan_cherry,Kwanzan cherry,VASE,D,800,0.8,0.3,0.04,5A4A44,4A7A3A,D8781E,F0A0C0,45",
        "fringe_tree,Fringe tree,ROUND,D,500,1,0.3,0.035,6A625A,4A7A3A,D8C84A,FAF8F2,25,2",
        "serviceberry,Serviceberry,VASE,D,600,0.7,0.3,0.03,8A8680,4A7A3A,D8581E,FAF8F2,40,3",
        "vitex|chaste_tree,Vitex,VASE,D,450,1,0.3,0.035,7A7268,6A8A5A,B0A04A,s8A7AC8,60,4",
        "smoke_tree,Smoke tree,ROUND,D,400,1,0.25,0.035,6A625A,6A3848,C8481E,sD8A0B0,35,3",
        "windmill_palm|palm,Windmill palm,PALM,E,600,0.45,0.75,0.04,6A5A48,3F6A38,,,25",
        "sabal_palm|cabbage_palm,Sabal palm,PALM,E,1200,0.35,0.8,0.035,7A6A58,3F6A38,,,20",
        "needle_palm,Needle palm,PALM,E,180,1.3,0.15,0.08,6A5A48,345E34,,,10");
    add("Fruit trees", false,
        "apple,Apple,ROUND,D,500,1,0.3,0.04,6A5A50,4A7A3A,C9A22E,F8E4EC,35",
        "peach,Peach,VASE,D,400,1.1,0.25,0.04,6A4A44,5A8440,D8A82A,F0A0C0,45",
        "pear,Pear,OVAL,D,600,0.6,0.25,0.04,5A524C,4A7A3A,C8581E,FAF8F2,45",
        "fig,Fig,ROUND,D,400,1.2,0.15,0.04,B0ACA4,4A7A3A,C9B84A,,45,3",
        "citrus,Citrus,ROUND,E,350,0.9,0.25,0.04,7A7268,2F5A32,,FAF8F2,30");
    add("Generic", false,
        "generic_deciduous|deciduous|tree,Deciduous tree,ROUND,D,1500,0.8,0.3,0.035,6A625A,4A7A3A,C9862E,,45",
        "generic_conifer|conifer,Conifer,CONICAL,E,1500,0.4,0.1,0.025,5E4A3C,2F5638,,,45",
        "generic_broadleaf_evergreen|broadleaf_evergreen,Broadleaf evergreen,OVAL,E,1000,0.6,0.15,0.03,6E665E,2E4F2E,,,35",
        "dead_snag|snag,Dead tree,SNAG,D,1200,0.5,0.4,0.04,8A8478,8A8478,,,0",
        "stump,Stump,STUMP,D,40,1.5,0,1.2,6A5A48,6A5A48,,,0");
    add("Shrubs", true,
        "azalea,Evergreen azalea,MOUND,E,120,1.2,0.05,0.05,6A5A50,3A6238,,E0508A,15",
        "native_azalea,Native azalea,VASE,D,250,0.8,0.2,0.04,6A5A50,4F7A3C,D8A82A,F0A050,20,4",
        "boxwood,Boxwood,MOUND,E,100,1,0.02,0.05,6A5A50,345E34,,,8",
        "hydrangea|mophead_hydrangea,Mophead hydrangea,MOUND,D,130,1.2,0.05,0.04,8A7A6A,4A7A3A,B0A04A,s6A8AD8,40",
        "oakleaf_hydrangea,Oakleaf hydrangea,MOUND,D,180,1.2,0.08,0.04,B07A5A,4A7038,A8343A,sF4F1E4,35",
        "panicle_hydrangea,Panicle hydrangea,VASE,D,220,0.9,0.15,0.04,8A7A6A,4A7A3A,C9A22E,sF4EEDA,45,4",
        "gardenia,Gardenia,MOUND,E,130,1.1,0.05,0.05,6A5A50,2A4F2C,,sFAF8F2,20",
        "loropetalum,Loropetalum,MOUND,E,200,1.2,0.05,0.04,5A4A44,5A2A3E,,D0408A,45",
        "indian_hawthorn,Indian hawthorn,MOUND,E,110,1.3,0.03,0.05,5A4A44,345E34,,F0C0D0,15",
        "abelia,Abelia,MOUND,E,150,1.2,0.05,0.04,7A5A48,4A7038,,sF8EEF0,30",
        "nandina,Nandina,VASE,E,160,0.7,0.1,0.03,6A4A34,4A7038,B03A2A,,25,5",
        "dwarf_yaupon,Dwarf yaupon holly,MOUND,E,100,1.3,0.02,0.05,9A968C,3F5E3A,,,10",
        "japanese_holly,Japanese holly,MOUND,E,130,1.1,0.02,0.05,6A5A50,2F5230,,,15",
        "inkberry,Inkberry holly,MOUND,E,150,1,0.05,0.05,6A5A50,2F5230,,,20",
        "spreading_juniper|juniper,Spreading juniper,MOUND,E,45,4,0,0.1,6A4A34,4A6A58,,,20",
        "upright_juniper,Upright juniper,COLUMNAR,E,400,0.25,0.02,0.03,6A4A34,4A6A58,,,30",
        "yew,Yew,MOUND,E,150,1.2,0.03,0.05,6A4A34,2A4A30,,,15",
        "shrub_rose|rose,Shrub rose,MOUND,D,130,1.1,0.08,0.04,5A6A40,3F6A34,8A7A3A,sD8304A,45",
        "knockout_rose,Knock Out rose,MOUND,D,110,1.1,0.05,0.04,5A6A40,3F6A34,8A7A3A,sE0284A,45",
        "climbing_rose,Climbing rose,COLUMNAR,D,280,0.5,0.05,0.03,5A6A40,3F6A34,8A7A3A,sE0508A,90",
        "forsythia,Forsythia,VASE,D,220,1.1,0.05,0.03,8A7A4A,5A8A40,B0A04A,F0D020,60,5",
        "spirea,Spirea,MOUND,D,110,1.3,0.05,0.04,7A5A48,5A8A40,D8781E,FAF8F2,30",
        "viburnum,Viburnum,MOUND,D,250,1,0.08,0.04,6A5A50,3F6A34,A8343A,FAF8F2,45",
        "rhododendron,Rhododendron,MOUND,E,200,1.1,0.08,0.04,6A5A50,2E5230,,C860C0,20",
        "fothergilla,Fothergilla,MOUND,D,130,1,0.05,0.04,6A5A50,4A7A3A,E8792A,FAF8F2,20",
        "ninebark,Ninebark,VASE,D,200,1.1,0.08,0.04,8A6A4A,5A2A34,A8343A,sF8EEF0,45,5",
        "butterfly_bush,Butterfly bush,VASE,D,220,1,0.1,0.03,8A8478,6A8A5A,B0A04A,s8A4AC8,90,5",
        "distylium,Distylium,MOUND,E,110,1.4,0.03,0.05,6A5A50,3A5E3A,,,25",
        "sasanqua|camellia_sasanqua,Sasanqua camellia,OVAL,E,250,0.7,0.08,0.04,9A8E80,2C4F2E,,F0A0C0,30",
        "blueberry,Blueberry,VASE,D,160,0.9,0.1,0.03,8A5A48,4F7A44,C8281E,FAF8F2,25,5",
        "muhly_grass|muhly,Pink muhly grass,GRASS,D,90,1.2,0,0.3,8A9A5A,6A8A4A,D880B0,,40",
        "miscanthus,Miscanthus,GRASS,D,180,0.9,0,0.3,8A9A5A,6A9450,D8C890,,60",
        "fountain_grass,Fountain grass,GRASS,D,90,1.3,0,0.3,8A9A5A,6A9450,C9B880,,45",
        "liriope,Liriope,GRASS,E,35,1.3,0,0.3,3F6A38,3F6A38,,,10",
        "mondo_grass|mondo,Mondo grass,GRASS,E,18,1.5,0,0.3,2A4A2C,2A4A2C,,,5");
  }

  /**
   * Returns the species with the given name or an alias of it, or <code>null</code>.
   */
  static Species getSpecies(String name) {
    if (name == null) {
      return null;
    }
    String key = name.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_').replace('-', '_').replace("'", "");
    if (ALIASES.containsKey(key)) {
      key = ALIASES.get(key);
    }
    Species species = SPECIES.get(key);
    if (species == null) {
      // "Loblolly pine", "red maple tree"
      for (Species candidate : SPECIES.values()) {
        if (candidate.commonName.toLowerCase(Locale.ENGLISH).replace(' ', '_').replace("'", "").equals(key)) {
          return candidate;
        }
      }
    }
    return species;
  }

  static List<Species> getAllSpecies() {
    return new ArrayList<Species>(SPECIES.values());
  }

  /**
   * Returns the names of the species by group.
   */
  static String describeSpecies(boolean shrubs, boolean details) {
    StringBuilder text = new StringBuilder();
    String group = null;
    for (Species species : SPECIES.values()) {
      if (species.shrub != shrubs) {
        continue;
      }
      if (!species.group.equals(group)) {
        group = species.group;
        text.append(text.length() > 0 ? (details ? "\n" : "; ") : "").append(group).append(": ");
      } else {
        text.append(", ");
      }
      text.append(species.name);
      if (details) {
        text.append(" (").append(species.commonName).append(", ").append(species.evergreen ? "evergreen" : "deciduous").append(", ")
            .append(Math.round(species.height / 10) / 10f).append(" m)");
      }
    }
    return text.toString();
  }

  /**
   * What a plant is built from, its sizes in cm.
   */
  static final class Plant {
    Species species;
    float   height;
    float   crownWidth;
    float   crownDepth;
    float   crownBase;
    float   trunkDiameter;
    float   leanAngle;
    float   leanDirection;
    int     trunks;
    String  season = "summer";
    String  lod = "medium";
    String  prunedShape = "natural";
    long    seed;
    /** Shows a tree that isn't there anymore: a pale mass one sees through. */
    boolean ghost;
    /** <code>natural</code> for branches and clusters of leaves, <code>simple</code> for plain masses. */
    String  style = "natural";
    /** Values that replace the ones of the profile of the species, or <code>null</code>. */
    Map<String, Object> profile;
    /** Shown by a model of the catalog rather than by a generated one. */
    boolean catalog;
    /** The name of the piece of the catalog to show, or <code>null</code> for the one of its species. */
    String  catalogName;
  }

  private final AssistantMeshBuilder mesh = new AssistantMeshBuilder();
  private final Plant                plant;
  private final Random               random;
  private final int                  detail;
  private int                        triangles;

  private AssistantPlantBuilder(Plant plant) {
    this.plant = plant;
    this.random = new Random(plant.seed * 7919 + plant.species.name.hashCode());
    this.detail = "low".equals(plant.lod) ? 0 : "high".equals(plant.lod) ? 2 : 1;
  }

  /**
   * Returns the model of a plant, its trunk standing at the origin.
   */
  static AssistantMeshBuilder build(Plant plant) {
    AssistantPlantBuilder builder = new AssistantPlantBuilder(plant);
    builder.build();
    if (isImpostor(plant)) {
      try {
        // Pictures of the plant at its medium detail on a few flat faces
        return builder.mesh.createImpostor(IMPOSTOR_TILE_SIZE);
      } catch (IOException ex) {
        // The plant itself
      }
    }
    return builder.mesh;
  }

  /** The size in pixels of each of the 9 pictures of an impostor. */
  static final int IMPOSTOR_TILE_SIZE = 256;

  /**
   * Returns <code>true</code> if the plant is shown by pictures of it on flat faces.
   */
  static boolean isImpostor(Plant plant) {
    return "impostor".equals(plant.lod) && !plant.ghost;
  }

  /**
   * Returns the count of triangles of the last model of a plant like the given one.
   */
  static int countTriangles(Plant plant) {
    if (isImpostor(plant)) {
      return AssistantMeshBuilder.IMPOSTOR_TRIANGLES;
    }
    AssistantPlantBuilder builder = new AssistantPlantBuilder(plant);
    builder.build();
    return builder.triangles;
  }

  static int shade(int color, float factor) {
    int red = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
    int green = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
    int blue = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
    return red << 16 | green << 8 | blue;
  }

  private static int mix(int color1, int color2, float ratio) {
    int red = Math.round(((color1 >> 16) & 0xFF) * (1 - ratio) + ((color2 >> 16) & 0xFF) * ratio);
    int green = Math.round(((color1 >> 8) & 0xFF) * (1 - ratio) + ((color2 >> 8) & 0xFF) * ratio);
    int blue = Math.round((color1 & 0xFF) * (1 - ratio) + (color2 & 0xFF) * ratio);
    return red << 16 | green << 8 | blue;
  }

  private float vary(float amount) {
    return 1 + (this.random.nextFloat() * 2 - 1) * amount;
  }

  // ---- Shapes ----

  /**
   * Adds a limb from a point to another one, thinner at its end.
   */
  private void limb(float [] start, float [] end, float startRadius, float endRadius, int sides, AssistantMeshBuilder.Material material) {
    float dx = end [0] - start [0];
    float dy = end [1] - start [1];
    float dz = end [2] - start [2];
    float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 0.5f) {
      return;
    }
    // Two directions across the limb
    float [] u = Math.abs(dy) > 0.9f * length ? new float [] {1, 0, 0} : new float [] {-dz, 0, dx};
    float uLength = (float)Math.sqrt(u [0] * u [0] + u [1] * u [1] + u [2] * u [2]);
    u = new float [] {u [0] / uLength, u [1] / uLength, u [2] / uLength};
    float [] v = {(dy * u [2] - dz * u [1]) / length, (dz * u [0] - dx * u [2]) / length, (dx * u [1] - dy * u [0]) / length};
    int [] bottom = new int [sides];
    int [] top = new int [sides];
    for (int i = 0; i < sides; i++) {
      double angle = Math.PI * 2 * i / sides;
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      bottom [i] = this.mesh.addVertex(start [0] + (u [0] * cos + v [0] * sin) * startRadius,
          start [1] + (u [1] * cos + v [1] * sin) * startRadius, start [2] + (u [2] * cos + v [2] * sin) * startRadius);
      top [i] = this.mesh.addVertex(end [0] + (u [0] * cos + v [0] * sin) * endRadius,
          end [1] + (u [1] * cos + v [1] * sin) * endRadius, end [2] + (u [2] * cos + v [2] * sin) * endRadius);
    }
    float [] center = {(start [0] + end [0]) / 2, (start [1] + end [1]) / 2, (start [2] + end [2]) / 2};
    for (int i = 0; i < sides; i++) {
      int next = (i + 1) % sides;
      addFacing(new int [] {bottom [i], bottom [next], top [next], top [i]}, center, material, true);
      this.triangles += 2;
    }
    if (endRadius > 1.5f) {
      addFacing(top, new float [] {start [0], start [1], start [2]}, material, false);
      this.triangles += sides - 2;
    }
  }

  /**
   * Adds a polygon turned away from a point.
   */
  private void addFacing(int [] indices, float [] inside, AssistantMeshBuilder.Material material, boolean smooth) {
    float [] normal = this.mesh.getNormal(indices);
    if (normal == null) {
      return;
    }
    float [] center = this.mesh.getCenter(indices);
    if (normal [0] * (center [0] - inside [0]) + normal [1] * (center [1] - inside [1]) + normal [2] * (center [2] - inside [2]) < 0) {
      AssistantMeshBuilder.reverse(indices);
    }
    this.mesh.addFace(indices, material, smooth);
  }

  /**
   * Adds a mass of leaves: a ball with bumps, of radii <code>rx</code>, <code>ry</code> (up) and <code>rz</code>.
   * @param taper  from 0 for a ball to 1 for a cone that points up
   */
  private void blob(float x, float y, float z, float rx, float ry, float rz, float taper, float bumps,
                    AssistantMeshBuilder.Material material, int level) {
    int rings = level <= 0 ? 3 : level == 1 ? 5 : level == 2 ? 7 : 9;
    int sides = level <= 0 ? 6 : level == 1 ? 8 : level == 2 ? 12 : 16;
    int [][] vertices = new int [rings + 1][];
    for (int ring = 0; ring <= rings; ring++) {
      double latitude = Math.PI * ring / rings;
      float ringHeight = -(float)Math.cos(latitude);
      float ringRadius = (float)Math.sin(latitude);
      // A cone is wide at its bottom and thin at its top
      float t = (ringHeight + 1) / 2;
      ringRadius = ringRadius * (1 - taper) + taper * (1 - t) * (ring == 0 ? 0 : 1) * (0.25f + 0.75f * (float)Math.min(1, t * 6));
      boolean pole = ring == 0 || ring == rings;
      vertices [ring] = new int [pole ? 1 : sides];
      for (int i = 0; i < vertices [ring].length; i++) {
        double longitude = Math.PI * 2 * (i + (ring % 2) * 0.5) / sides;
        float bump = pole ? 1 : vary(bumps);
        vertices [ring][i] = this.mesh.addVertex(x + (float)Math.cos(longitude) * ringRadius * rx * bump,
            y + ringHeight * ry * (pole ? 1 : vary(bumps * 0.4f)), z + (float)Math.sin(longitude) * ringRadius * rz * bump);
      }
    }
    float [] center = {x, y, z};
    for (int ring = 0; ring < rings; ring++) {
      int [] low = vertices [ring];
      int [] high = vertices [ring + 1];
      for (int i = 0; i < sides; i++) {
        int next = (i + 1) % sides;
        if (low.length == 1) {
          addFacing(new int [] {low [0], high [i], high [next]}, center, material, true);
          this.triangles++;
        } else if (high.length == 1) {
          addFacing(new int [] {low [i], low [next], high [0]}, center, material, true);
          this.triangles++;
        } else {
          addFacing(new int [] {low [i], low [next], high [next]}, center, material, true);
          addFacing(new int [] {low [i], high [next], high [i]}, center, material, true);
          this.triangles += 2;
        }
      }
    }
  }

  /**
   * Adds a box of leaves with soft edges, for a pruned shrub.
   */
  private void box(float x, float y, float z, float rx, float ry, float rz, AssistantMeshBuilder.Material material) {
    float chamfer = Math.min(Math.min(rx, rz), ry) * 0.2f;
    float [][] ring = {{-rx, -rz + chamfer}, {-rx + chamfer, -rz}, {rx - chamfer, -rz}, {rx, -rz + chamfer},
                       {rx, rz - chamfer}, {rx - chamfer, rz}, {-rx + chamfer, rz}, {-rx, rz - chamfer}};
    float [] heights = {-ry, ry - chamfer, ry};
    float [] scales = {1, 1, 1 - chamfer / Math.min(rx, rz)};
    int [][] vertices = new int [3][ring.length];
    for (int level = 0; level < 3; level++) {
      for (int i = 0; i < ring.length; i++) {
        vertices [level][i] = this.mesh.addVertex(x + ring [i][0] * scales [level], y + heights [level], z + ring [i][1] * scales [level]);
      }
    }
    float [] center = {x, y, z};
    for (int level = 0; level < 2; level++) {
      for (int i = 0; i < ring.length; i++) {
        int next = (i + 1) % ring.length;
        addFacing(new int [] {vertices [level][i], vertices [level][next], vertices [level + 1][next], vertices [level + 1][i]},
            center, material, false);
        this.triangles += 2;
      }
    }
    addFacing(vertices [2].clone(), center, material, false);
    this.triangles += ring.length - 2;
  }

  // ---- Plants ----

  private void build() {
    Plant plant = this.plant;
    Species species = plant.species;
    String season = plant.season;
    boolean bare = !species.evergreen && "winter".equals(season);
    int leafColor = species.leaf;
    if (!species.evergreen && "autumn".equals(season)) {
      leafColor = species.autumnLeaf;
    } else if ("spring".equals(season) && !species.evergreen) {
      leafColor = shade(mix(species.leaf, 0xA8D060, 0.35f), 1.08f);
    }
    boolean flowering = species.flower != 0
        && (species.summerFlowers ? "summer".equals(season) : "spring".equals(season));
    if (species.form == Form.GRASS && "autumn".equals(season)) {
      leafColor = species.autumnLeaf;
    } else if (species.form == Form.GRASS && "winter".equals(season) && !species.evergreen) {
      leafColor = 0xC9B88A;
      bare = false;
    }
    AssistantMeshBuilder.Material bark;
    AssistantMeshBuilder.Material leaves;
    AssistantMeshBuilder.Material innerLeaves;
    AssistantMeshBuilder.Material flowers = null;
    if (plant.ghost) {
      bark = this.mesh.getMaterial(0xB0B0B0, 0.35f);
      leaves = innerLeaves = this.mesh.getMaterial(0xC8C8C8, 0.25f);
    } else {
      bark = this.detail == 0 ? this.mesh.getMaterial(species.bark)
          : this.mesh.getMaterial("bark", 40, 80, species.bark, shade(species.bark, 0.7f), 0);
      // Leaves that let the light through around a darker heart
      leaves = this.detail == 0 ? this.mesh.getMaterial(leafColor)
          : this.mesh.getMaterial("leaves", Math.max(60, plant.height * 0.12f), Math.max(60, plant.height * 0.12f),
              shade(leafColor, 1.12f), flowering ? species.flower : shade(leafColor, 1.4f), flowering ? 0.08f : 0.16f);
      innerLeaves = this.mesh.getMaterial(shade(leafColor, 0.72f));
      if (flowering) {
        flowers = this.mesh.getMaterial(species.flower);
        if (this.detail == 0) {
          leaves = this.mesh.getMaterial(mix(leafColor, species.flower, bare ? 0.9f : 0.55f));
        }
      }
    }
    float height = plant.height;
    float crownBase = Math.max(0, Math.min(height * 0.92f, plant.crownBase));
    float crownHeight = height - crownBase;
    float radiusX = plant.crownWidth / 2;
    float radiusZ = plant.crownDepth / 2;
    float trunkRadius = Math.max(0.6f, plant.trunkDiameter / 2);
    float leanX = (float)(Math.sin(Math.toRadians(plant.leanAngle)) * Math.cos(Math.toRadians(plant.leanDirection - 90)));
    float leanZ = (float)(Math.sin(Math.toRadians(plant.leanAngle)) * Math.sin(Math.toRadians(plant.leanDirection - 90)));
    int trunkSides = this.detail == 0 ? 5 : this.detail == 1 ? 7 : 10;
    Form form = species.form;
    String pruned = plant.prunedShape;

    if (form == Form.STUMP) {
      limb(new float [] {0, 0, 0}, new float [] {0, height, 0}, trunkRadius * 1.15f, trunkRadius, Math.max(8, trunkSides), bark);
      return;
    } else if (form == Form.GRASS) {
      addGrass(height, radiusX, radiusZ, leaves, flowers != null ? flowers
          : "autumn".equals(season) && species.autumnLeaf != species.leaf ? this.mesh.getMaterial(species.autumnLeaf) : null);
      return;
    } else if (!pruned.equals("natural")) {
      // A clipped shape on a short stem
      limb(new float [] {0, 0, 0}, new float [] {0, crownBase + crownHeight * 0.3f, 0}, trunkRadius, trunkRadius * 0.8f, trunkSides, bark);
      AssistantMeshBuilder.Material clipped = bare ? this.mesh.getMaterial(shade(species.bark, 1.1f)) : leaves;
      float middle = crownBase + crownHeight / 2;
      if (pruned.equals("box") || pruned.equals("hedge_section")) {
        box(0, middle, 0, radiusX, crownHeight / 2, radiusZ, clipped);
      } else if (pruned.equals("cone")) {
        blob(0, middle, 0, radiusX, crownHeight / 2, radiusZ, 1, 0.02f, clipped, Math.max(1, this.detail));
      } else {
        blob(0, middle, 0, radiusX, crownHeight / 2, radiusZ, 0, 0.03f, clipped, Math.max(1, this.detail));
      }
      return;
    }

    if (!plant.ghost && !"simple".equals(plant.style) && AssistantTreeBuilder.builds(form)) {
      // Branches that divide, and leaves one sees through
      this.triangles += AssistantTreeBuilder.build(this.mesh, plant, this.random, this.detail);
      return;
    }

    // Trunks
    int trunks = Math.max(1, plant.trunks);
    float trunkTop = form == Form.PALM ? crownBase + crownHeight * 0.15f
        : form == Form.CONICAL || form == Form.COLUMNAR || form == Form.PYRAMIDAL ? height * 0.9f
        : form == Form.SNAG ? height : crownBase + crownHeight * (form == Form.PINE ? 0.75f : 0.45f);
    List<float []> tops = new ArrayList<float []>();
    for (int i = 0; i < trunks; i++) {
      double angle = Math.PI * 2 * i / trunks + this.random.nextFloat();
      float spread = trunks == 1 ? 0 : radiusX * 0.35f;
      float baseOffset = trunks == 1 ? 0 : trunkRadius * 1.2f;
      float [] base = {(float)Math.cos(angle) * baseOffset, 0, (float)Math.sin(angle) * baseOffset};
      float [] top = {(float)Math.cos(angle) * spread + leanX * trunkTop, trunkTop * (float)Math.cos(Math.toRadians(plant.leanAngle)),
                      (float)Math.sin(angle) * spread + leanZ * trunkTop};
      float radius = trunks == 1 ? trunkRadius : trunkRadius / (float)Math.sqrt(trunks) * 1.2f;
      if (form == Form.PALM) {
        limb(base, top, radius, radius * 0.8f, trunkSides, bark);
      } else {
        // A root flare, then a slow taper
        float [] knee = {base [0] + (top [0] - base [0]) * 0.08f, top [1] * 0.08f, base [2] + (top [2] - base [2]) * 0.08f};
        limb(base, knee, radius * 1.35f, radius, trunkSides, bark);
        limb(knee, top, radius, radius * (form == Form.SNAG ? 0.25f : 0.35f), trunkSides, bark);
      }
      tops.add(top);
    }
    float centerX = leanX * (crownBase + crownHeight / 2);
    float centerZ = leanZ * (crownBase + crownHeight / 2);

    if (form == Form.PALM) {
      addFronds(tops.get(0), radiusX, crownHeight, leaves);
      return;
    }
    // Branches, seen in winter and under the leaves
    int branchCount = form == Form.SNAG ? 9 : bare ? (this.detail == 0 ? 10 : this.detail == 1 ? 18 : 30)
        : form == Form.CONICAL || form == Form.COLUMNAR ? 0 : this.detail == 0 ? 0 : 5;
    for (int i = 0; i < branchCount; i++) {
      float [] top = tops.get(i % tops.size());
      float t = form == Form.CONICAL || form == Form.PYRAMIDAL ? 0.15f + 0.8f * i / branchCount : 0.35f + 0.6f * this.random.nextFloat();
      float startHeight = crownBase + (trunkTop - crownBase) * t;
      float ratio = startHeight / Math.max(1, top [1]);
      float [] start = {top [0] * ratio, startHeight, top [2] * ratio};
      double angle = Math.PI * 2 * (i * 0.382 + this.random.nextFloat() * 0.2);
      float reach = (form == Form.CONICAL || form == Form.PYRAMIDAL ? (1 - t) : 0.6f + 0.4f * this.random.nextFloat())
          * (form == Form.SNAG ? 0.5f : 0.85f);
      float rise = form == Form.WEEPING ? -0.1f : form == Form.VASE ? 0.9f : form == Form.CONICAL ? 0.05f : 0.45f;
      float [] end = {centerX * 0.3f + (float)Math.cos(angle) * radiusX * reach,
                      Math.min(height * 0.97f, startHeight + crownHeight * 0.4f * rise * vary(0.3f) + radiusX * reach * rise * 0.5f),
                      centerZ * 0.3f + (float)Math.sin(angle) * radiusZ * reach};
      float radius = trunkRadius * 0.3f * (1 - t * 0.5f);
      limb(start, end, radius, radius * 0.25f, this.detail == 2 ? 5 : 3, bark);
      if ((bare || form == Form.SNAG) && this.detail > 0 && form != Form.SNAG) {
        // Twigs
        for (int k = 0; k < (this.detail == 2 ? 4 : 3); k++) {
          float along = 0.45f + 0.25f * k;
          float [] from = {start [0] + (end [0] - start [0]) * along, start [1] + (end [1] - start [1]) * along,
                           start [2] + (end [2] - start [2]) * along};
          double twigAngle = angle + (k % 2 == 0 ? 0.9 : -0.9);
          float twig = radiusX * 0.3f;
          float [] to = {from [0] + (float)Math.cos(twigAngle) * twig, Math.min(height, from [1] + twig * (form == Form.WEEPING ? -0.6f : 0.7f)),
                         from [2] + (float)Math.sin(twigAngle) * twig * radiusZ / Math.max(1, radiusX)};
          limb(from, to, radius * 0.35f, radius * 0.1f, 3, bark);
        }
      }
    }
    if (bare || form == Form.SNAG) {
      if (flowers != null && bare) {
        // The flowers of trees that bloom before their leaves
        addCrown(form, crownBase, crownHeight, radiusX, radiusZ, centerX, centerZ, flowers, null);
      }
      return;
    }
    addCrown(form, crownBase, crownHeight, radiusX, radiusZ, centerX, centerZ, leaves, plant.ghost ? null : innerLeaves);
    if (flowers != null && this.detail > 0 && !species.summerFlowers == "spring".equals(season)) {
      // Clusters of flowers on the crown
      int clusters = this.detail == 2 ? 14 : 8;
      for (int i = 0; i < clusters; i++) {
        double angle = Math.PI * 2 * i * 0.382;
        float t = 0.3f + 0.6f * this.random.nextFloat();
        float ring = form == Form.CONICAL || form == Form.PYRAMIDAL ? 1 - t * 0.8f : (float)Math.sin(Math.PI * Math.min(0.9f, t + 0.1f));
        blob(centerX + (float)Math.cos(angle) * radiusX * ring * 0.95f, crownBase + crownHeight * t,
            centerZ + (float)Math.sin(angle) * radiusZ * ring * 0.95f, radiusX * 0.16f, crownHeight * 0.07f, radiusZ * 0.16f, 0, 0.1f, flowers, 0);
      }
    }
  }

  private void addCrown(Form form, float crownBase, float crownHeight, float radiusX, float radiusZ, float centerX, float centerZ,
                        AssistantMeshBuilder.Material leaves, AssistantMeshBuilder.Material innerLeaves) {
    int level = this.detail;
    float middle = crownBase + crownHeight / 2;
    switch (form) {
      case CONICAL : {
        // Tiers of branches, wider at the bottom
        int tiers = level == 0 ? 4 : level == 1 ? 6 : 9;
        for (int i = 0; i < tiers; i++) {
          float t = (float)i / tiers;
          float tierHeight = crownHeight / tiers * 1.7f;
          float scale = 1 - t * 0.82f;
          blob(centerX * (0.5f + t), crownBase + crownHeight * t + tierHeight / 2, centerZ * (0.5f + t), radiusX * scale * vary(0.06f),
              tierHeight / 2, radiusZ * scale * vary(0.06f), 0.9f, 0.08f, leaves, level + 1);
        }
        if (innerLeaves != null) {
          blob(centerX, middle, centerZ, radiusX * 0.45f, crownHeight * 0.48f, radiusZ * 0.45f, 1, 0, innerLeaves, 0);
        }
        break;
      }
      case COLUMNAR :
        blob(centerX, middle, centerZ, radiusX, crownHeight / 2, radiusZ, 0.35f, 0.06f, leaves, level + 2);
        if (innerLeaves != null) {
          blob(centerX, middle, centerZ, radiusX * 0.75f, crownHeight * 0.47f, radiusZ * 0.75f, 0.35f, 0, innerLeaves, 1);
        }
        break;
      case PYRAMIDAL : {
        blob(centerX, middle, centerZ, radiusX, crownHeight / 2, radiusZ, 0.6f, 0.12f, leaves, level + 1);
        int clusters = level == 0 ? 4 : level == 1 ? 8 : 14;
        for (int i = 0; i < clusters; i++) {
          double angle = Math.PI * 2 * i * 0.382;
          float t = 0.12f + 0.6f * i / clusters;
          float ring = 1 - t * 0.75f;
          blob(centerX + (float)Math.cos(angle) * radiusX * ring * 0.7f, crownBase + crownHeight * t,
              centerZ + (float)Math.sin(angle) * radiusZ * ring * 0.7f, radiusX * 0.42f, crownHeight * 0.16f, radiusZ * 0.42f,
              0.2f, 0.15f, leaves, level);
        }
        if (innerLeaves != null) {
          blob(centerX, middle, centerZ, radiusX * 0.7f, crownHeight * 0.45f, radiusZ * 0.7f, 0.6f, 0, innerLeaves, 1);
        }
        break;
      }
      case PINE : {
        // A few flat masses at the top of a bare trunk
        int masses = level == 0 ? 4 : level == 1 ? 7 : 11;
        for (int i = 0; i < masses; i++) {
          double angle = Math.PI * 2 * i * 0.382;
          float t = 0.35f + 0.6f * i / masses;
          float reach = i == masses - 1 ? 0 : 0.45f * (1.1f - t * 0.5f);
          blob(centerX + (float)Math.cos(angle) * radiusX * reach, crownBase + crownHeight * t,
              centerZ + (float)Math.sin(angle) * radiusZ * reach, radiusX * 0.62f * vary(0.15f), crownHeight * 0.16f,
              radiusZ * 0.62f * vary(0.15f), 0.15f, 0.2f, leaves, level + 1);
        }
        break;
      }
      case MOUND :
        blob(centerX, crownBase + crownHeight * 0.48f, centerZ, radiusX, crownHeight * 0.52f, radiusZ, 0.12f, 0.1f, leaves, level + 2);
        if (innerLeaves != null) {
          blob(centerX, crownBase + crownHeight * 0.45f, centerZ, radiusX * 0.8f, crownHeight * 0.45f, radiusZ * 0.8f, 0.1f, 0, innerLeaves, 0);
        }
        break;
      default : {
        // Masses of leaves around a heart
        float flat = form == Form.SPREADING ? 0.75f : form == Form.WEEPING ? 0.85f : 1;
        float lift = form == Form.VASE ? 0.62f : form == Form.SPREADING ? 0.55f : form == Form.WEEPING ? 0.6f : 0.5f;
        float heartY = crownBase + crownHeight * lift;
        float heartScale = form == Form.OVAL ? 0.8f : 0.72f;
        blob(centerX, heartY, centerZ, radiusX * heartScale, crownHeight * (form == Form.OVAL ? 0.5f : 0.42f) * flat,
            radiusZ * heartScale, form == Form.VASE ? -0.25f : 0, 0.14f, leaves, level + 1);
        int masses = level == 0 ? 6 : level == 1 ? 11 : 18;
        for (int i = 0; i < masses; i++) {
          double angle = Math.PI * 2 * i * 0.382 + this.random.nextFloat() * 0.4;
          float t = form == Form.VASE ? 0.45f + 0.45f * this.random.nextFloat()
              : form == Form.WEEPING ? 0.15f + 0.5f * this.random.nextFloat() : 0.22f + 0.6f * this.random.nextFloat();
          float ring = form == Form.VASE ? 0.35f + 0.45f * t : (float)Math.sin(Math.PI * Math.min(0.92f, t + 0.12f)) * 0.62f;
          float size = 0.42f * vary(0.2f);
          blob(centerX + (float)Math.cos(angle) * radiusX * ring, crownBase + crownHeight * t,
              centerZ + (float)Math.sin(angle) * radiusZ * ring, radiusX * size, crownHeight * size * 0.55f * flat,
              radiusZ * size, form == Form.WEEPING ? -0.3f : 0, 0.18f, leaves, level + 1);
        }
        if (innerLeaves != null) {
          blob(centerX, heartY, centerZ, radiusX * 0.55f, crownHeight * 0.32f * flat, radiusZ * 0.55f, 0, 0, innerLeaves, 0);
        }
        break;
      }
    }
  }

  /**
   * Adds the fronds of a palm around the top of its trunk.
   */
  private void addFronds(float [] top, float radius, float crownHeight, AssistantMeshBuilder.Material leaves) {
    int fronds = this.detail == 0 ? 10 : this.detail == 1 ? 16 : 24;
    for (int i = 0; i < fronds; i++) {
      double angle = Math.PI * 2 * i / fronds + this.random.nextFloat() * 0.2;
      float lift = (i % 3 == 0 ? 0.75f : i % 3 == 1 ? 0.35f : -0.05f) * crownHeight;
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      float width = radius * 0.3f;
      float [][] spine = {{top [0], top [1], top [2]},
                          {top [0] + cos * radius * 0.55f, top [1] + lift * 0.9f, top [2] + sin * radius * 0.55f},
                          {top [0] + cos * radius, top [1] + lift * 0.45f - crownHeight * 0.1f, top [2] + sin * radius}};
      for (int k = 0; k < 2; k++) {
        float w1 = k == 0 ? width * 0.3f : width;
        float w2 = k == 0 ? width : width * 0.15f;
        float [][] quad = {{spine [k][0] - sin * w1, spine [k][1], spine [k][2] + cos * w1},
                           {spine [k][0] + sin * w1, spine [k][1], spine [k][2] - cos * w1},
                           {spine [k + 1][0] + sin * w2, spine [k + 1][1], spine [k + 1][2] - cos * w2},
                           {spine [k + 1][0] - sin * w2, spine [k + 1][1], spine [k + 1][2] + cos * w2}};
        this.mesh.addPolygon(quad, leaves, 0, 1, 0);
        this.mesh.addPolygon(quad, leaves, 0, -1, 0);
        this.triangles += 4;
      }
    }
  }

  /**
   * Adds a tuft of blades of grass, with plumes if it has some.
   */
  private void addGrass(float height, float radiusX, float radiusZ, AssistantMeshBuilder.Material leaves, AssistantMeshBuilder.Material plumes) {
    int blades = this.detail == 0 ? 8 : this.detail == 1 ? 14 : 24;
    for (int i = 0; i < blades; i++) {
      double angle = Math.PI * 2 * i * 0.382;
      float reach = 0.45f + 0.55f * this.random.nextFloat();
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      float width = Math.max(1.5f, radiusX * 0.16f);
      float tipHeight = height * (plumes != null ? 0.72f : 1) * (1.05f - reach * 0.5f);
      float [][] blade = {{-sin * width, 0, cos * width}, {sin * width, 0, -cos * width},
                          {cos * radiusX * reach * 0.6f + sin * width * 0.6f, tipHeight * 0.75f, sin * radiusZ * reach * 0.6f - cos * width * 0.6f},
                          {cos * radiusX * reach, tipHeight, sin * radiusZ * reach}};
      this.mesh.addPolygon(new float [][] {blade [0], blade [1], blade [2]}, leaves, cos, 0.3f, sin);
      this.mesh.addPolygon(new float [][] {blade [0], blade [1], blade [2]}, leaves, -cos, -0.3f, -sin);
      this.mesh.addPolygon(new float [][] {blade [0], blade [2], blade [3]}, leaves, cos, 0.3f, sin);
      this.mesh.addPolygon(new float [][] {blade [0], blade [2], blade [3]}, leaves, -cos, -0.3f, -sin);
      this.triangles += 4;
    }
    if (plumes != null) {
      blob(0, height * 0.8f, 0, radiusX * 0.75f, height * 0.2f, radiusZ * 0.75f, 0, 0.2f, plumes, 0);
    }
  }
}
