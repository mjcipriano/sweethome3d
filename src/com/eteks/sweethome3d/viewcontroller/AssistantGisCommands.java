/*
 * AssistantGisCommands.java 23 nov. 2026
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that draw in the plan what GIS files describe (GeoJSON, KML, DXF, shapefiles, OpenStreetMap),
 * and the ones that ask a web service for parcels, heights, map data and aerial pictures when the user allows it.
 */
public class AssistantGisCommands {
  private static final long MAX_FILE_LENGTH = 100L * 1024 * 1024;
  private static final int  MAX_FEATURES = 3000;
  private static final int  MAX_DOWNLOAD = 40 * 1024 * 1024;

  /**
   * Makes one picture of the tiles of a map.
   */
  public interface TileComposer {
    /**
     * Returns a PNG or JPEG picture of tiles put side by side, cut, then turned.
     * @param tiles    the images of the tiles, row after row; a missing one is <code>null</code>
     * @param crop     the part kept, in pixels: x, y, width, height
     * @param rotation the angle the picture is turned by, in degrees clockwise
     */
    byte [] compose(byte [][] tiles, int columns, int rows, int tileSize, int [] crop, double rotation) throws IOException;
  }

  /**
   * A point, a line or a polygon read from a file, with what it says about itself.
   */
  private static final class Feature {
    /** point, line or polygon. */
    String              type;
    List<double []>     points = new ArrayList<double []>();
    Map<String, Object> properties = new LinkedHashMap<String, Object>();
  }

  /**
   * Gives the point of the plan of a place of a file.
   */
  private interface Transform {
    float [] toPlan(double first, double second);
  }

  /** The tiles of the USGS imagery: their row comes before their column, and they stop at the zoom 16. */
  static final String USGS_IMAGERY_TEMPLATE =
      "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}";
  /** The image service of the NAIP aerial pictures of the USGS, asked a box at a time. */
  static final String USGS_NAIP_TEMPLATE =
      "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer/exportImage"
      + "?bbox={bbox}&bboxSR=3857&imageSR=3857&size=256,256&format=jpg&f=image";
  /** The widest background image, in meters. */
  private static final double MAX_BASEMAP_WIDTH = 990;

  private final Home                     home;
  private final AssistantCommandExecutor executor;
  private boolean                        webAccessAllowed;
  private int                            unlabelledParcels;
  private TileComposer                   tileComposer;

  AssistantGisCommands(Home home, AssistantCommandExecutor executor) {
    this.home = home;
    this.executor = executor;
  }

  void setWebAccessAllowed(boolean webAccessAllowed) {
    this.webAccessAllowed = webAccessAllowed;
  }

  boolean isWebAccessAllowed() {
    return this.webAccessAllowed;
  }

  void setTileComposer(TileComposer tileComposer) {
    this.tileComposer = tileComposer;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    String rules = "\"rules\":[{\"where\":{\"layer\":\"FENCE\"},\"as\":\"fence\",\"style\":\"chain_link\"},{\"geometry\":\"polygon\",\"as\":\"building\","
        + "\"height_from\":\"HEIGHT\"}]";
    return "  {\"action\":\"import_geojson\",\"path\":\"<absolute path>\"," + rules + "}  "
        + "(draws in the plan what a GIS file describes, placed with the georeference of the home. import_kml reads KML, "
        + "import_shapefile a .shp with its .dbf, import_dxf a DXF drawing; \"geojson\":{...} gives GeoJSON in the command. "
        + "Coordinates are longitude and latitude, or in \"srs\":\"EPSG:2240\"; a DXF or a shapefile without srs is placed by "
        + "\"x\",\"y\" (the plan place of its origin) and \"units\":\"m|ft|cm\", its north up the plan. "
        + "Each rule chooses features by \"geometry\":\"point|line|polygon\" and / or \"where\":{\"<attribute>\":\"<value>\"} "
        + "(\"if\" is the same; a feature takes the first rule it matches, and the ones that match none are left out) and tells "
        + "what they become with \"as\": for a polygon property_line, polyline, room, slab, patio, planting_bed, building, pond, "
        + "pool or deck; for a line polyline, fence, hedge, path, road, driveway, sidewalk or curb; for a point tree, shrub, label, "
        + "utility or furniture; skip leaves them out. The other keys of a rule are the ones of the command that builds it, and "
        + "\"<key>_from\" (or \"<key>_attribute\") names the attribute that gives a key: \"name_from\", \"height_from\", "
        + "\"width_from\", \"species_from\", \"tag_from\", \"style_attribute\" for the style of a fence... The heights and "
        + "the widths are in meters unless \"attribute_units\":\"ft|cm\". Without rule a "
        + "polygon and a line are drawn as lines of the plan and a named point as a label. \"buffer_m\":N or \"area\":[[x,y],...] "
        + "keeps what is that near the walls of the home or in that area, lines being cut at its edge. Up to " + MAX_FEATURES
        + " features a call)\n"
        + "  {\"action\":\"import_buildings\",\"path\":\"<file>\",\"height\":600,\"height_attribute\":\"HEIGHT\","
        + "\"height_units\":\"m|cm|ft\",\"roof\":\"hip\"}  "
        + "(the polygons of a file as neighbor buildings. Their height is the attribute \"height_attribute\" names, or an "
        + "attribute named height, in \"height_units\" (without it a value over 100 is taken in cm, another in m), else "
        + "their levels x 280 cm (\"levels_attribute\", or levels, stories...), else \"height\"; the answer tells which. "
        + "Buildings where the home or a neighbor building stands already are left out unless \"keep_existing\":true. "
        + "import_roads draws its lines as roads \"width\" cm wide (600, or \"width_from\") of asphalt, tagged road, in pieces of "
        + "\"max_segment_m\" (40) at most that each follow the ground, with their names as labels. import_parcels draws the "
        + "parcel that contains the home as its property line and the others as neighbor lots, dash-dot gray lines that no "
        + "check takes for the lot of the home; a parcel isn't drawn over a property line that exists. Each is named and "
        + "labelled by its number and its address when its attributes have them, plus its owner with \"label_owner\":true, or by "
        + "\"label_from\":[\"PIN\",\"SITUS\"]; \"subject\":{\"<attribute>\":\"<value>\"} names the lot of the home itself)\n"
        + "  {\"action\":\"import_osm\",\"path\":\"<a .osm or Overpass JSON file>\"}  "
        + "(OpenStreetMap data: buildings with their levels, roads with their width or lanes, footways, trees, fences with "
        + "their type, hedges, walls and water. Without path it asks the Overpass service for what is \"buffer_m\" meters (150) "
        + "around the walls of the home, which needs the permission to use web services, and keeps only what is in that area: "
        + "roads are cut at its edge. \"only\" (or \"layers\"):[\"buildings\",\"roads\",\"paths\",\"trees\",\"fences\",\"water\"] "
        + "limits it)\n"
        + "  {\"action\":\"import_parcels\",\"url\":\"<ArcGIS REST FeatureServer layer URL>\",\"buffer_m\":150}  "
        + "(asks a county parcel service for the lots that touch the area around the home; web permission needed)\n"
        + "  {\"action\":\"fetch_elevation\",\"source\":\"usgs_3dep\",\"buffer_m\":60,\"resolution_cm\":300}  "
        + "(asks the USGS 3DEP service for the heights of the ground around the home and builds the terrain with them, as "
        + "set_terrain with \"pin\", \"datum_offset_cm\" and \"max_points\"; \"area\":[[x,y],...] instead of buffer_m; web permission "
        + "needed. Only in the United States. \"Around the home\" is around its walls, whatever was imported or drawn farther; "
        + "without wall, around its lot, then around the point of its georeference. A call gives 12000 heights at most: an "
        + "area too large for \"resolution_cm\" is refused with the resolution that covers it)\n"
        + "  {\"action\":\"set_basemap\",\"source\":\"esri_world_imagery|usgs_naip|usgs_imagery|osm|xyz:<template>\",\"buffer_m\":80,"
        + "\"zoom\":19}  "
        + "(downloads the tiles of an aerial picture or of a map around the home, turns them to the plan and shows them behind "
        + "it as its background image; web permission needed. usgs_naip is the NAIP aerial picture of the United States, "
        + "usgs_imagery the tiles of The National Map, which stop at the zoom 16. An \"xyz:\" template has {z}, {x} and {y}, "
        + "or {bbox}. The picture is 990 m and 4096 pixels wide at most: a larger area or a finer zoom is reduced, which the "
        + "answer tells. remove_background_image takes it away)\n"
        + "  {\"action\":\"web_sources\"}  (tells whether web services may be used, and what each command sends: the place of the "
        + "home goes to the service asked)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("import_geojson".equals(action) || "import_kml".equals(action) || "import_dxf".equals(action)
          || "import_shapefile".equals(action) || "import_buildings".equals(action) || "import_roads".equals(action)
          || "import_parcels".equals(action) || "import_osm".equals(action)) {
        importFeatures(action, command, tally, notes);
      } else if ("fetch_elevation".equals(action)) {
        fetchElevation(command, tally, notes);
      } else if ("set_basemap".equals(action)) {
        setBasemap(command, tally, notes);
      } else if ("web_sources".equals(action)) {
        notes.add((this.webAccessAllowed ? "Web services may be used." : "Web services aren't allowed: the user ticks \"Allow web "
            + "services\" in Sweet Home 3D (Tools > Claude MCP server...) to allow them.")
            + " What each command sends: fetch_elevation sends the corners of the area asked to the USGS (elevation.nationalmap.gov); "
            + "import_osm without path sends them to the Overpass service of OpenStreetMap (overpass-api.de); import_parcels with "
            + "\"url\" sends them to that server; set_basemap asks the tiles around the home to Esri (server.arcgisonline.com), the "
            + "USGS (imagery.nationalmap.gov for usgs_naip, basemap.nationalmap.gov for usgs_imagery), OpenStreetMap (tile.openstreetmap.org) or the server of an xyz template. Nothing "
            + "else of the home is sent. Point clouds aren't downloaded, and Bing and Google sources aren't available.");
      } else if ("fetch_point_cloud".equals(action)) {
        throw new IOException("point clouds aren't downloaded: the USGS serves them compressed (LAZ, Entwine), which this program "
            + "doesn't decode. Download the tile from apps.nationalmap.gov/lidar-explorer, decompress it with "
            + "\"laszip -i in.laz -o out.las\", then set_terrain with \"file\" reads its ground and trees_from_lidar its trees");
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    } catch (RuntimeException ex) {
      notes.add("Couldn't run " + action + " (" + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()) + ")");
    }
    return true;
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- Files ----

  private static File getFile(AssistantCommand command) throws IOException {
    String path = command.getString("path") != null ? command.getString("path") : command.getString("file");
    if (path == null) {
      return null;
    }
    path = path.trim();
    if (path.startsWith("~/")) {
      path = System.getProperty("user.home") + path.substring(1);
    }
    File file = new File(path);
    if (!file.isAbsolute() || !file.isFile()) {
      throw new IOException("\"path\" must be the absolute path of a file that exists, not \"" + path + "\"");
    }
    return file;
  }

  /**
   * Returns what gives the points of the plan of the places of a file.
   * @param geographic <code>true</code> if the file is in longitude and latitude unless it says otherwise
   */
  private Transform getTransform(AssistantCommand command, boolean geographic) throws IOException {
    final HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (command.getString("srs") != null) {
      final HomeGeoreference.Projection projection = HomeGeoreference.Projection.get(command.getString("srs"));
      if (georeference == null) {
        throw new IOException("\"srs\" needs the georeference of the home: set_georeference gives it");
      }
      return new Transform() {
          public float [] toPlan(double first, double second) {
            double [] place = projection.toLatitudeLongitude(first, second);
            double [] plan = georeference.toPlan(place [0], place [1]);
            return new float [] {(float)plan [0], (float)plan [1]};
          }
        };
    } else if (geographic) {
      if (georeference == null) {
        throw new IOException("this file is in longitude and latitude: it needs the georeference of the home. "
            + "{\"action\":\"set_georeference\",\"lat\":N,\"lon\":N,\"x\":N,\"y\":N} gives it");
      }
      return new Transform() {
          public float [] toPlan(double first, double second) {
            double [] plan = georeference.toPlan(second, first);
            return new float [] {(float)plan [0], (float)plan [1]};
          }
        };
    }
    String units = command.getString("units") != null ? command.getString("units").trim().toLowerCase(Locale.ENGLISH) : "m";
    final double scale = units.startsWith("cm") ? 1 : units.startsWith("mm") ? 0.1 : units.startsWith("f") || units.startsWith("us") ? 30.48
        : units.startsWith("in") ? 2.54 : 100;
    final double originX = command.getDouble("x", 0);
    final double originY = command.getDouble("y", 0);
    return new Transform() {
        public float [] toPlan(double first, double second) {
          // The north of a drawing is up the plan
          return new float [] {(float)(originX + first * scale), (float)(originY - second * scale)};
        }
      };
  }

  // ---- Readers ----

  private static void addGeoJsonGeometry(Object geometry, Map<String, Object> properties, List<Feature> features) {
    if (!(geometry instanceof Map)) {
      return;
    }
    Map<?, ?> map = (Map<?, ?>)geometry;
    String type = String.valueOf(map.get("type"));
    Object coordinates = map.get("coordinates");
    if (type.equals("GeometryCollection") && map.get("geometries") instanceof List) {
      for (Object item : (List<?>)map.get("geometries")) {
        addGeoJsonGeometry(item, properties, features);
      }
    } else if (!(coordinates instanceof List)) {
      return;
    } else if (type.equals("Point")) {
      addFeature("point", Collections.singletonList(coordinates), properties, features);
    } else if (type.equals("MultiPoint")) {
      for (Object point : (List<?>)coordinates) {
        addFeature("point", Collections.singletonList(point), properties, features);
      }
    } else if (type.equals("LineString")) {
      addFeature("line", (List<?>)coordinates, properties, features);
    } else if (type.equals("MultiLineString")) {
      for (Object line : (List<?>)coordinates) {
        addFeature("line", (List<?>)line, properties, features);
      }
    } else if (type.equals("Polygon")) {
      // Its outer ring
      if (!((List<?>)coordinates).isEmpty()) {
        addFeature("polygon", (List<?>)((List<?>)coordinates).get(0), properties, features);
      }
    } else if (type.equals("MultiPolygon")) {
      for (Object polygon : (List<?>)coordinates) {
        if (polygon instanceof List && !((List<?>)polygon).isEmpty()) {
          addFeature("polygon", (List<?>)((List<?>)polygon).get(0), properties, features);
        }
      }
    }
  }

  private static void addFeature(String type, List<?> coordinates, Map<String, Object> properties, List<Feature> features) {
    Feature feature = new Feature();
    feature.type = type;
    feature.properties = properties;
    for (Object coordinate : coordinates) {
      if (coordinate instanceof List && ((List<?>)coordinate).size() >= 2 && ((List<?>)coordinate).get(0) instanceof Number
          && ((List<?>)coordinate).get(1) instanceof Number) {
        feature.points.add(new double [] {((Number)((List<?>)coordinate).get(0)).doubleValue(),
                                          ((Number)((List<?>)coordinate).get(1)).doubleValue()});
      }
    }
    if (!feature.points.isEmpty()) {
      features.add(feature);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Feature> readGeoJson(Object data) throws IOException {
    if (data instanceof String) {
      try {
        data = JSONParser.parse((String)data);
      } catch (IllegalArgumentException ex) {
        throw new IOException("the file isn't written in JSON (" + ex.getMessage() + ")");
      }
    }
    List<Feature> features = new ArrayList<Feature>();
    List<Object> items = new ArrayList<Object>();
    if (data instanceof Map && ((Map<?, ?>)data).get("features") instanceof List) {
      items.addAll((List<?>)((Map<?, ?>)data).get("features"));
    } else if (data instanceof List) {
      items.addAll((List<?>)data);
    } else {
      items.add(data);
    }
    for (Object item : items) {
      if (!(item instanceof Map)) {
        continue;
      }
      Map<?, ?> map = (Map<?, ?>)item;
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      Object source = map.get("properties") != null ? map.get("properties") : map.get("attributes");
      if (source instanceof Map) {
        properties.putAll((Map<String, Object>)source);
      }
      if (map.get("geometry") instanceof Map && ((Map<?, ?>)map.get("geometry")).get("rings") instanceof List) {
        // Esri JSON
        for (Object ring : (List<?>)((Map<?, ?>)map.get("geometry")).get("rings")) {
          addFeature("polygon", (List<?>)ring, properties, features);
        }
      } else if (map.get("geometry") instanceof Map && ((Map<?, ?>)map.get("geometry")).get("paths") instanceof List) {
        for (Object path : (List<?>)((Map<?, ?>)map.get("geometry")).get("paths")) {
          addFeature("line", (List<?>)path, properties, features);
        }
      } else {
        addGeoJsonGeometry(map.get("geometry") != null ? map.get("geometry") : map, properties, features);
      }
    }
    return features;
  }

  private static String unescape(String text) {
    return text.replace("<![CDATA[", "").replace("]]>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&").trim();
  }

  private static List<Feature> readKml(String text) {
    List<Feature> features = new ArrayList<Feature>();
    Matcher placemarks = Pattern.compile("<Placemark[\\s>](.*?)</Placemark>", Pattern.DOTALL).matcher(text);
    Pattern coordinates = Pattern.compile("<(Point|LineString|LinearRing)[\\s>].*?<coordinates>(.*?)</coordinates>", Pattern.DOTALL);
    while (placemarks.find()) {
      String placemark = placemarks.group(1);
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      Matcher name = Pattern.compile("<name>(.*?)</name>", Pattern.DOTALL).matcher(placemark);
      if (name.find()) {
        properties.put("name", unescape(name.group(1)));
      }
      Matcher description = Pattern.compile("<description>(.*?)</description>", Pattern.DOTALL).matcher(placemark);
      if (description.find()) {
        properties.put("description", unescape(description.group(1)));
      }
      Matcher data = Pattern.compile("<(?:Data|SimpleData) name=\"([^\"]+)\"[^>]*>(.*?)</(?:Data|SimpleData)>", Pattern.DOTALL).matcher(placemark);
      while (data.find()) {
        properties.put(data.group(1), unescape(data.group(2).replaceAll("<[^>]+>", "")));
      }
      boolean polygon = placemark.contains("<Polygon");
      Matcher geometries = coordinates.matcher(placemark);
      boolean first = true;
      while (geometries.find()) {
        if (geometries.group(1).equals("LinearRing") && !first && polygon && placemark.substring(0, geometries.start()).contains("<innerBoundaryIs")) {
          // A hole
          continue;
        }
        Feature feature = new Feature();
        feature.type = geometries.group(1).equals("Point") ? "point" : geometries.group(1).equals("LinearRing") && polygon ? "polygon" : "line";
        feature.properties = properties;
        for (String tuple : geometries.group(2).trim().split("\\s+")) {
          String [] values = tuple.split(",");
          if (values.length >= 2) {
            try {
              feature.points.add(new double [] {Double.parseDouble(values [0]), Double.parseDouble(values [1])});
            } catch (NumberFormatException ex) {
              // Left out
            }
          }
        }
        if (!feature.points.isEmpty()) {
          features.add(feature);
        }
        first = false;
      }
    }
    return features;
  }

  private static List<Feature> readDxf(String text) throws IOException {
    String [] lines = text.split("\\r?\\n");
    List<String []> pairs = new ArrayList<String []>();
    for (int i = 0; i + 1 < lines.length; i += 2) {
      pairs.add(new String [] {lines [i].trim(), lines [i + 1].trim()});
    }
    List<Feature> features = new ArrayList<Feature>();
    boolean entities = false;
    Feature polyline = null;
    int index = 0;
    while (index < pairs.size()) {
      String [] pair = pairs.get(index);
      if (pair [0].equals("2") && pair [1].equals("ENTITIES")) {
        entities = true;
      } else if (pair [0].equals("0") && pair [1].equals("ENDSEC")) {
        entities = false;
      }
      if (!entities || !pair [0].equals("0")) {
        index++;
        continue;
      }
      String entity = pair [1];
      // The codes of the entity, until the next one
      Map<String, List<String>> codes = new HashMap<String, List<String>>();
      int next = index + 1;
      while (next < pairs.size() && !pairs.get(next) [0].equals("0")) {
        List<String> values = codes.get(pairs.get(next) [0]);
        if (values == null) {
          codes.put(pairs.get(next) [0], values = new ArrayList<String>());
        }
        values.add(pairs.get(next) [1]);
        next++;
      }
      index = next;
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      if (codes.get("8") != null) {
        properties.put("layer", codes.get("8").get(0));
      }
      properties.put("entity", entity);
      try {
        if (entity.equals("LINE")) {
          Feature feature = new Feature();
          feature.type = "line";
          feature.properties = properties;
          feature.points.add(new double [] {number(codes, "10", 0), number(codes, "20", 0)});
          feature.points.add(new double [] {number(codes, "11", 0), number(codes, "21", 0)});
          features.add(feature);
        } else if (entity.equals("LWPOLYLINE")) {
          Feature feature = new Feature();
          boolean closed = codes.get("70") != null && (Integer.parseInt(codes.get("70").get(0)) & 1) != 0;
          feature.type = closed ? "polygon" : "line";
          feature.properties = properties;
          List<String> xs = codes.get("10");
          List<String> ys = codes.get("20");
          for (int i = 0; xs != null && ys != null && i < xs.size() && i < ys.size(); i++) {
            feature.points.add(new double [] {Double.parseDouble(xs.get(i)), Double.parseDouble(ys.get(i))});
          }
          if (feature.points.size() >= 2) {
            features.add(feature);
          }
        } else if (entity.equals("POLYLINE")) {
          polyline = new Feature();
          boolean closed = codes.get("70") != null && (Integer.parseInt(codes.get("70").get(0)) & 1) != 0;
          polyline.type = closed ? "polygon" : "line";
          polyline.properties = properties;
        } else if (entity.equals("VERTEX") && polyline != null) {
          polyline.points.add(new double [] {number(codes, "10", 0), number(codes, "20", 0)});
        } else if (entity.equals("SEQEND") && polyline != null) {
          if (polyline.points.size() >= 2) {
            features.add(polyline);
          }
          polyline = null;
        } else if (entity.equals("CIRCLE")) {
          Feature feature = new Feature();
          feature.type = "polygon";
          feature.properties = properties;
          double radius = number(codes, "40", 1);
          for (int i = 0; i < 24; i++) {
            feature.points.add(new double [] {number(codes, "10", 0) + Math.cos(Math.PI * 2 * i / 24) * radius,
                                              number(codes, "20", 0) + Math.sin(Math.PI * 2 * i / 24) * radius});
          }
          properties.put("radius", Double.valueOf(radius));
          features.add(feature);
        } else if (entity.equals("POINT") || entity.equals("TEXT") || entity.equals("MTEXT") || entity.equals("INSERT")) {
          Feature feature = new Feature();
          feature.type = "point";
          feature.properties = properties;
          feature.points.add(new double [] {number(codes, "10", 0), number(codes, "20", 0)});
          if (codes.get("1") != null) {
            properties.put("name", codes.get("1").get(0).replaceAll("\\\\[A-Za-z][^;]*;", "").replace("\\P", " "));
          } else if (codes.get("2") != null) {
            properties.put("name", codes.get("2").get(0));
            properties.put("block", codes.get("2").get(0));
          }
          features.add(feature);
        }
      } catch (NumberFormatException ex) {
        // An entity that can't be read is left out
      }
    }
    if (features.isEmpty() && !text.contains("ENTITIES")) {
      throw new IOException("the file isn't a text DXF drawing: no ENTITIES section. Save it as an ASCII DXF");
    }
    return features;
  }

  private static double number(Map<String, List<String>> codes, String code, double defaultValue) {
    return codes.get(code) != null ? Double.parseDouble(codes.get(code).get(0)) : defaultValue;
  }

  /**
   * Returns the features of a shapefile, with the attributes of its .dbf file if it stands beside it.
   */
  private static List<Feature> readShapefile(File file) throws IOException {
    byte [] data = AssistantGeneratedModel.readFile(file, MAX_FILE_LENGTH);
    if (data.length < 100 || ByteBuffer.wrap(data).getInt(0) != 9994) {
      throw new IOException(file.getName() + " isn't a .shp shapefile");
    }
    // Its attributes
    List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
    File dbf = new File(file.getPath().substring(0, file.getPath().length() - 4) + ".dbf");
    if (!dbf.isFile()) {
      dbf = new File(file.getPath().substring(0, file.getPath().length() - 4) + ".DBF");
    }
    if (dbf.isFile()) {
      byte [] table = AssistantGeneratedModel.readFile(dbf, MAX_FILE_LENGTH);
      ByteBuffer buffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);
      int count = buffer.getInt(4);
      int headerLength = buffer.getShort(8) & 0xFFFF;
      int recordLength = buffer.getShort(10) & 0xFFFF;
      List<Object []> fields = new ArrayList<Object []>();
      for (int offset = 32; offset + 32 <= headerLength && table [offset] != 0x0D; offset += 32) {
        int end = offset;
        while (end < offset + 11 && table [end] != 0) {
          end++;
        }
        fields.add(new Object [] {new String(table, offset, end - offset, "ISO-8859-1"), Character.valueOf((char)table [offset + 11]),
                                  Integer.valueOf(table [offset + 16] & 0xFF)});
      }
      for (int i = 0; i < count && headerLength + (i + 1) * recordLength <= table.length; i++) {
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        int offset = headerLength + i * recordLength + 1;
        for (Object [] field : fields) {
          int length = ((Integer)field [2]).intValue();
          String value = new String(table, offset, length, "ISO-8859-1").trim();
          offset += length;
          if (value.length() == 0) {
            continue;
          }
          char type = ((Character)field [1]).charValue();
          if (type == 'N' || type == 'F') {
            try {
              record.put((String)field [0], Double.valueOf(value));
              continue;
            } catch (NumberFormatException ex) {
              // Kept as a text
            }
          }
          record.put((String)field [0], value);
        }
        records.add(record);
      }
    }
    List<Feature> features = new ArrayList<Feature>();
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int offset = 100;
    int recordIndex = 0;
    while (offset + 12 <= data.length) {
      buffer.order(ByteOrder.BIG_ENDIAN);
      int contentLength = buffer.getInt(offset + 4) * 2;
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      int start = offset + 8;
      if (start + contentLength > data.length) {
        break;
      }
      int shapeType = buffer.getInt(start) % 10;
      Map<String, Object> properties = recordIndex < records.size() ? records.get(recordIndex) : new LinkedHashMap<String, Object>();
      if (shapeType == 1) {
        Feature feature = new Feature();
        feature.type = "point";
        feature.properties = properties;
        feature.points.add(new double [] {buffer.getDouble(start + 4), buffer.getDouble(start + 12)});
        features.add(feature);
      } else if (shapeType == 3 || shapeType == 5) {
        int parts = buffer.getInt(start + 36);
        int points = buffer.getInt(start + 40);
        int pointsStart = start + 44 + parts * 4;
        for (int part = 0; part < parts; part++) {
          int from = buffer.getInt(start + 44 + part * 4);
          int to = part + 1 < parts ? buffer.getInt(start + 44 + (part + 1) * 4) : points;
          Feature feature = new Feature();
          feature.type = shapeType == 5 ? "polygon" : "line";
          feature.properties = properties;
          for (int i = from; i < to && pointsStart + (i + 1) * 16 <= data.length; i++) {
            feature.points.add(new double [] {buffer.getDouble(pointsStart + i * 16), buffer.getDouble(pointsStart + i * 16 + 8)});
          }
          if (feature.points.size() >= 2) {
            features.add(feature);
          }
        }
      } else if (shapeType == 8) {
        int points = buffer.getInt(start + 36);
        for (int i = 0; i < points; i++) {
          Feature feature = new Feature();
          feature.type = "point";
          feature.properties = properties;
          feature.points.add(new double [] {buffer.getDouble(start + 40 + i * 16), buffer.getDouble(start + 48 + i * 16)});
          features.add(feature);
        }
      }
      offset = start + contentLength;
      recordIndex++;
    }
    return features;
  }

  /**
   * Returns the features of OpenStreetMap data, written in XML or in the JSON of the Overpass service.
   */
  @SuppressWarnings("unchecked")
  private static List<Feature> readOsm(String text) throws IOException {
    List<Feature> features = new ArrayList<Feature>();
    Map<String, double []> nodes = new HashMap<String, double []>();
    if (text.trim().startsWith("{")) {
      Object data;
      try {
        data = JSONParser.parse(text);
      } catch (IllegalArgumentException ex) {
        throw new IOException("the OpenStreetMap data isn't written in JSON (" + ex.getMessage() + ")");
      }
      if (!(data instanceof Map) || !(((Map<?, ?>)data).get("elements") instanceof List)) {
        throw new IOException("no \"elements\" in the OpenStreetMap data");
      }
      List<?> elements = (List<?>)((Map<?, ?>)data).get("elements");
      for (Object element : elements) {
        if (element instanceof Map && "node".equals(((Map<?, ?>)element).get("type")) && ((Map<?, ?>)element).get("lat") instanceof Number) {
          nodes.put(String.valueOf(((Number)((Map<?, ?>)element).get("id")).longValue()),
              new double [] {((Number)((Map<?, ?>)element).get("lon")).doubleValue(), ((Number)((Map<?, ?>)element).get("lat")).doubleValue()});
        }
      }
      for (Object element : elements) {
        if (!(element instanceof Map)) {
          continue;
        }
        Map<?, ?> map = (Map<?, ?>)element;
        Map<String, Object> tags = map.get("tags") instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>)map.get("tags"))
            : new LinkedHashMap<String, Object>();
        Feature feature = new Feature();
        feature.properties = tags;
        if ("node".equals(map.get("type")) && !tags.isEmpty() && map.get("lat") instanceof Number) {
          feature.type = "point";
          feature.points.add(new double [] {((Number)map.get("lon")).doubleValue(), ((Number)map.get("lat")).doubleValue()});
        } else if ("way".equals(map.get("type"))) {
          if (map.get("geometry") instanceof List) {
            for (Object point : (List<?>)map.get("geometry")) {
              if (point instanceof Map && ((Map<?, ?>)point).get("lat") instanceof Number) {
                feature.points.add(new double [] {((Number)((Map<?, ?>)point).get("lon")).doubleValue(),
                                                  ((Number)((Map<?, ?>)point).get("lat")).doubleValue()});
              }
            }
          } else if (map.get("nodes") instanceof List) {
            for (Object id : (List<?>)map.get("nodes")) {
              double [] node = id instanceof Number ? nodes.get(String.valueOf(((Number)id).longValue())) : null;
              if (node != null) {
                feature.points.add(node);
              }
            }
          }
          feature.type = isClosed(feature.points) ? "polygon" : "line";
        } else {
          continue;
        }
        if (!feature.points.isEmpty()) {
          features.add(feature);
        }
      }
      return features;
    }
    // XML
    Matcher nodeMatcher = Pattern.compile("<node\\b([^>]*?)(/>|>(.*?)</node>)", Pattern.DOTALL).matcher(text);
    Pattern attribute = Pattern.compile("(\\w+)=[\"']([^\"']*)[\"']");
    Pattern tag = Pattern.compile("<tag\\s+k=[\"']([^\"']*)[\"']\\s+v=[\"']([^\"']*)[\"']");
    while (nodeMatcher.find()) {
      Map<String, String> attributes = new HashMap<String, String>();
      Matcher matcher = attribute.matcher(nodeMatcher.group(1));
      while (matcher.find()) {
        attributes.put(matcher.group(1), matcher.group(2));
      }
      if (attributes.get("id") == null || attributes.get("lat") == null || attributes.get("lon") == null) {
        continue;
      }
      double [] place = {Double.parseDouble(attributes.get("lon")), Double.parseDouble(attributes.get("lat"))};
      nodes.put(attributes.get("id"), place);
      if (nodeMatcher.group(3) != null) {
        Map<String, Object> tags = new LinkedHashMap<String, Object>();
        Matcher tagMatcher = tag.matcher(nodeMatcher.group(3));
        while (tagMatcher.find()) {
          tags.put(tagMatcher.group(1), unescape(tagMatcher.group(2)));
        }
        if (!tags.isEmpty()) {
          Feature feature = new Feature();
          feature.type = "point";
          feature.properties = tags;
          feature.points.add(place);
          features.add(feature);
        }
      }
    }
    Matcher wayMatcher = Pattern.compile("<way\\b[^>]*>(.*?)</way>", Pattern.DOTALL).matcher(text);
    Pattern reference = Pattern.compile("<nd\\s+ref=[\"']([^\"']*)[\"']");
    while (wayMatcher.find()) {
      Feature feature = new Feature();
      Matcher referenceMatcher = reference.matcher(wayMatcher.group(1));
      while (referenceMatcher.find()) {
        double [] node = nodes.get(referenceMatcher.group(1));
        if (node != null) {
          feature.points.add(node);
        }
      }
      Matcher tagMatcher = tag.matcher(wayMatcher.group(1));
      while (tagMatcher.find()) {
        feature.properties.put(tagMatcher.group(1), unescape(tagMatcher.group(2)));
      }
      feature.type = isClosed(feature.points) ? "polygon" : "line";
      if (feature.points.size() >= 2) {
        features.add(feature);
      }
    }
    if (features.isEmpty() && !text.contains("<osm")) {
      throw new IOException("the file isn't OpenStreetMap data: an .osm XML file starts with <osm, the JSON of Overpass with {\"elements\"");
    }
    return features;
  }

  private static boolean isClosed(List<double []> points) {
    return points.size() >= 4 && Math.abs(points.get(0) [0] - points.get(points.size() - 1) [0]) < 1E-9
        && Math.abs(points.get(0) [1] - points.get(points.size() - 1) [1]) < 1E-9;
  }

  // ---- What features become ----

  private static Object find(Map<String, Object> properties, String name) {
    if (name == null) {
      return null;
    }
    Object value = properties.get(name);
    if (value == null) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
    }
    return value;
  }

  private static String getName(Map<String, Object> properties, Map<?, ?> rule) {
    String nameAttribute = getAttributeName(rule, "name");
    Object value = nameAttribute != null ? find(properties, nameAttribute) : null;
    for (String key : new String [] {"name", "addr:housenumber", "label", "title"}) {
      if (value == null) {
        value = find(properties, key);
      }
    }
    return value != null && value.toString().trim().length() > 0 ? value.toString().trim() : null;
  }

  /**
   * Returns the attribute a rule reads a value from, given by <code>&lt;key&gt;_from</code> or
   * <code>&lt;key&gt;_attribute</code>, or <code>null</code>.
   */
  private static String getAttributeName(Map<?, ?> rule, String key) {
    if (rule == null) {
      return null;
    }
    Object name = rule.get(key + "_from") != null ? rule.get(key + "_from") : rule.get(key + "_attribute");
    return name != null && name.toString().trim().length() > 0 ? name.toString().trim() : null;
  }

  /**
   * Returns the style of fence that a type of fence of OpenStreetMap names, or <code>null</code>.
   */
  private static String getFenceStyle(String fenceType) {
    String type = fenceType.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_').replace('-', '_');
    if (AssistantFenceBuilder.getStyle(type) != null) {
      return type;
    }
    return type.equals("chain_link") || type.equals("chainlink") ? "chain_link" : type.equals("wood") || type.equals("panel") ? "privacy_solid"
        : type.equals("split_rail") ? "split_rail" : type.equals("wire") ? "field_fence"
        : type.equals("electric") ? "electric" : type.equals("barbed_wire") ? "barbed_wire"
        : type.equals("pole") ? "post_and_rail" : type.equals("railing") || type.equals("bars")
            || type.equals("metal") ? "ornamental_steel" : null;
  }

  private static double parseNumber(Object value) {
    try {
      return Double.parseDouble(value.toString().replaceAll("[^0-9.\\-]", ""));
    } catch (NumberFormatException ex) {
      return Double.NaN;
    }
  }

  /**
   * Returns the number of centimeters of a unit of length, or 0 if it isn't known.
   */
  private static float getUnitScale(String units) {
    if (units == null) {
      return 0;
    }
    String text = units.trim().toLowerCase(Locale.ENGLISH);
    return text.startsWith("cm") || text.startsWith("centi") ? 1 : text.startsWith("f") || text.startsWith("us") ? 30.48f
        : text.equals("m") || text.startsWith("met") ? 100 : 0;
  }

  /**
   * Returns the rule that OpenStreetMap tags give, or <code>null</code> for what isn't drawn.
   */
  private static Map<String, Object> getOsmRule(Feature feature, List<String> only) {
    Map<String, Object> tags = feature.properties;
    Map<String, Object> rule = new LinkedHashMap<String, Object>();
    String highway = tags.get("highway") != null ? tags.get("highway").toString() : null;
    String barrier = tags.get("barrier") != null ? tags.get("barrier").toString() : null;
    String natural = tags.get("natural") != null ? tags.get("natural").toString() : null;
    if (tags.get("building") != null && feature.type.equals("polygon")) {
      if (!only.isEmpty() && !only.contains("buildings")) {
        return null;
      }
      rule.put("as", "building");
      double height = Double.NaN;
      try {
        if (tags.get("height") != null) {
          height = Double.parseDouble(tags.get("height").toString().replaceAll("[^0-9.]", "")) * 100;
        } else if (tags.get("building:levels") != null) {
          rule.put("stories", Double.valueOf(Double.parseDouble(tags.get("building:levels").toString())));
        }
      } catch (NumberFormatException ex) {
        // The usual height
      }
      if (!Double.isNaN(height)) {
        rule.put("height", Double.valueOf(height));
      }
      String roof = tags.get("roof:shape") != null ? tags.get("roof:shape").toString() : "";
      rule.put("roof", roof.equals("flat") ? "flat" : roof.startsWith("gable") ? "gable" : "hip");
    } else if (highway != null && feature.type.equals("line")) {
      boolean path = Arrays.asList("footway", "path", "cycleway", "pedestrian", "steps", "bridleway").contains(highway);
      if (!only.isEmpty() && !only.contains(path ? "paths" : "roads")) {
        return null;
      }
      rule.put("as", path ? "path" : "road");
      double width = path ? 150 : highway.equals("service") || highway.equals("track") ? 350
          : highway.equals("residential") || highway.equals("unclassified") || highway.equals("living_street") ? 600
          : highway.equals("tertiary") ? 700 : 900;
      // The width or the lanes that the road tells
      double told = tags.get("width") != null ? parseNumber(tags.get("width")) * 100
          : tags.get("lanes") != null ? parseNumber(tags.get("lanes")) * 300 : Double.NaN;
      if (told >= 100 && told <= 3000) {
        width = told;
      }
      rule.put("width", Double.valueOf(width));
      if (!path) {
        rule.put("material", highway.equals("track") ? "gravel" : "asphalt");
        rule.put("label", Boolean.TRUE);
      }
    } else if (barrier != null && feature.type.equals("line") || barrier != null && feature.type.equals("polygon")) {
      if (!only.isEmpty() && !only.contains("fences")) {
        return null;
      }
      if (barrier.equals("hedge")) {
        rule.put("as", "hedge");
      } else {
        rule.put("as", "fence");
        String fenceStyle = tags.get("fence_type") != null ? getFenceStyle(tags.get("fence_type").toString()) : null;
        rule.put("style", barrier.equals("wall") ? "brick_wall" : barrier.equals("retaining_wall") ? "retaining_wall"
            : barrier.equals("guard_rail") ? "guardrail" : barrier.equals("bollard") ? "bollards"
            : fenceStyle != null ? fenceStyle : "post_and_rail");
        if (feature.type.equals("polygon")) {
          rule.put("closed", Boolean.TRUE);
        }
      }
    } else if ("tree".equals(natural) && feature.type.equals("point")) {
      if (!only.isEmpty() && !only.contains("trees")) {
        return null;
      }
      rule.put("as", "tree");
      String species = tags.get("species:en") != null ? tags.get("species:en").toString()
          : tags.get("genus:en") != null ? tags.get("genus:en").toString() : tags.get("taxon:en") != null ? tags.get("taxon:en").toString() : null;
      if (species != null && AssistantPlantBuilder.getSpecies(species) != null) {
        rule.put("species", species);
      } else {
        rule.put("species", "needleleaved".equals(tags.get("leaf_type")) ? "generic_conifer" : "generic_deciduous");
      }
      try {
        if (tags.get("height") != null) {
          rule.put("height", Double.valueOf(Double.parseDouble(tags.get("height").toString().replaceAll("[^0-9.]", "")) * 100));
        }
      } catch (NumberFormatException ex) {
        // The usual height
      }
    } else if (("water".equals(natural) || "swimming_pool".equals(tags.get("leisure"))) && feature.type.equals("polygon")) {
      if (!only.isEmpty() && !only.contains("water")) {
        return null;
      }
      rule.put("as", "swimming_pool".equals(tags.get("leisure")) ? "pool" : "pond");
    } else {
      return null;
    }
    return rule;
  }

  private static boolean matches(Feature feature, Map<?, ?> rule) {
    Object geometry = rule.get("geometry");
    if (geometry != null) {
      String wanted = geometry.toString().toLowerCase(Locale.ENGLISH);
      if (!(wanted.startsWith(feature.type) || feature.type.startsWith(wanted) || wanted.equals("all"))) {
        return false;
      }
    }
    Object where = rule.get("where") != null ? rule.get("where") : rule.get("if");
    if (where instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)where).entrySet()) {
        Object value = find(feature.properties, String.valueOf(entry.getKey()));
        String wanted = String.valueOf(entry.getValue());
        if (value == null && !wanted.equals("*") || value != null && !wanted.equals("*")
            && !value.toString().equalsIgnoreCase(wanted)
            && !(value instanceof Number && wanted.equals(format(((Number)value).doubleValue())))) {
          return false;
        }
      }
    }
    return true;
  }

  private static double getPolygonArea(List<float []> points) {
    double area = 0;
    for (int i = 0; i < points.size(); i++) {
      float [] next = points.get((i + 1) % points.size());
      area += points.get(i) [0] * next [1] - next [0] * points.get(i) [1];
    }
    return Math.abs(area) / 2;
  }

  private void importFeatures(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    File file = getFile(command);
    List<Feature> features;
    boolean geographic = true;
    String source;
    boolean osm = "import_osm".equals(action);
    boolean fetched = false;
    if (osm && file == null) {
      features = readOsm(fetchOsm(command));
      source = "the Overpass service of OpenStreetMap";
      fetched = true;
    } else if ("import_parcels".equals(action) && command.getString("url") != null) {
      features = readGeoJson(fetchParcels(command));
      source = "the parcel service";
      fetched = true;
    } else if (file == null) {
      Object data = command.getParameters().get("geojson");
      if (data == null) {
        throw new IOException("give the absolute \"path\" of the file" + ("import_geojson".equals(action) || "import_buildings".equals(action)
            || "import_roads".equals(action) || "import_parcels".equals(action) ? ", or \"geojson\":{...}" : ""));
      }
      features = readGeoJson(data);
      source = "the GeoJSON given";
    } else {
      String name = file.getName().toLowerCase(Locale.ENGLISH);
      source = file.getName();
      if (name.endsWith(".shp")) {
        features = readShapefile(file);
        geographic = false;
      } else {
        String text = new String(AssistantGeneratedModel.readFile(file, MAX_FILE_LENGTH), "UTF-8");
        if (name.endsWith(".kmz")) {
          throw new IOException("a .kmz file is a ZIP: extract its doc.kml and give that file");
        } else if (osm || name.endsWith(".osm")) {
          features = readOsm(text);
          osm = true;
        } else if (name.endsWith(".kml") || "import_kml".equals(action)) {
          features = readKml(text);
        } else if (name.endsWith(".dxf") || "import_dxf".equals(action)) {
          features = readDxf(text);
          geographic = false;
        } else {
          features = readGeoJson(text);
        }
      }
    }
    if (features.isEmpty()) {
      throw new IOException("nothing was read from " + source + ": no point, line or polygon");
    }
    Transform transform = getTransform(command, geographic && command.getString("x") == null);
    this.unlabelledParcels = 0;
    // The rules of the command, or the ones of its kind
    List<Map<?, ?>> rules = new ArrayList<Map<?, ?>>();
    if (command.getParameters().get("rules") instanceof List) {
      for (Object rule : (List<?>)command.getParameters().get("rules")) {
        if (rule instanceof Map) {
          rules.add((Map<?, ?>)rule);
        }
      }
    }
    Map<String, Object> defaultRule = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : command.getParameters().entrySet()) {
      if (!Arrays.asList("action", "path", "file", "geojson", "srs", "units", "x", "y", "rules", "url", "buffer_m", "only", "subject",
              "label_from", "max_features", "attribute_units", "layers", "area", "height_units", "max_segment_m", "label_owner",
              "keep_existing").contains(entry.getKey())) {
        defaultRule.put(entry.getKey(), entry.getValue());
      }
    }
    if ("import_buildings".equals(action)) {
      defaultRule.put("geometry", "polygon");
      defaultRule.put("as", "building");
    } else if ("import_roads".equals(action)) {
      defaultRule.put("geometry", "line");
      defaultRule.put("as", "road");
      if (defaultRule.get("width") == null && getAttributeName(defaultRule, "width") == null) {
        defaultRule.put("width", Double.valueOf(600));
      }
      if (defaultRule.get("material") == null) {
        defaultRule.put("material", "asphalt");
      }
      defaultRule.put("label", Boolean.TRUE);
    } else if ("import_parcels".equals(action)) {
      defaultRule.put("geometry", "polygon");
      defaultRule.put("as", "property_line");
    }
    List<String> only = new ArrayList<String>();
    for (String layer : command.getStringList("only")) {
      only.add(layer.trim().toLowerCase(Locale.ENGLISH));
    }
    for (String layer : command.getStringList("layers")) {
      only.add(layer.trim().toLowerCase(Locale.ENGLISH));
    }
    // The heights and the widths that attributes give are in meters unless told otherwise
    float heightScale = 100;
    if (command.getString("attribute_units") != null) {
      String units = command.getString("attribute_units").toLowerCase(Locale.ENGLISH);
      heightScale = units.startsWith("cm") ? 1 : units.startsWith("f") ? 30.48f : 100;
    }
    float heightUnitScale = getUnitScale(command.getString("height_units"));
    if (command.getString("height_units") != null && heightUnitScale == 0) {
      throw new IOException("\"height_units\" is m, cm or ft");
    }
    if (heightUnitScale == 0 && command.getString("attribute_units") != null) {
      heightUnitScale = heightScale;
    }
    // What a service gives around the home, or what is asked with an area, is cut to that area
    float [] clip = fetched || command.has("buffer_m") || command.has("area") ? getPlanBounds(command, 150) : null;
    float maxSegment = (float)Math.max(0, Math.min(100000, command.getDouble("max_segment_m", 40))) * 100;
    boolean parcels = "import_parcels".equals(action);
    int limit = (int)Math.max(1, Math.min(MAX_FEATURES, command.getDouble("max_features", MAX_FEATURES)));
    // The points of the plan of each feature
    List<List<float []>> planPoints = new ArrayList<List<float []>>();
    int far = 0;
    for (Feature feature : features) {
      List<float []> points = new ArrayList<float []>();
      boolean farAway = false;
      for (double [] point : feature.points) {
        float [] plan = transform.toPlan(point [0], point [1]);
        farAway |= !AssistantCommandExecutor.coordinateSane(plan [0]) || !AssistantCommandExecutor.coordinateSane(plan [1]);
        if (points.isEmpty() || Math.hypot(plan [0] - points.get(points.size() - 1) [0], plan [1] - points.get(points.size() - 1) [1]) > 0.5) {
          points.add(plan);
        }
      }
      if (feature.type.equals("polygon") && points.size() > 3
          && Math.hypot(points.get(0) [0] - points.get(points.size() - 1) [0], points.get(0) [1] - points.get(points.size() - 1) [1]) < 0.5) {
        points.remove(points.size() - 1);
      }
      if (farAway) {
        far++;
      }
      planPoints.add(farAway ? null : points);
    }
    // The lot of the home among parcels: the one that contains it, unless "subject" names it
    boolean subjectGiven = command.getParameters().get("subject") instanceof Map;
    float [] homePoint = getHomePoint();
    Feature subjectParcel = null;
    Polyline existingLot = null;
    int parcelCount = 0;
    if (parcels) {
      double smallest = Double.MAX_VALUE;
      for (int i = 0; i < features.size(); i++) {
        List<float []> points = planPoints.get(i);
        if (points != null && features.get(i).type.equals("polygon") && points.size() >= 3) {
          parcelCount++;
          if (!subjectGiven && homePoint != null && contains(points, homePoint [0], homePoint [1]) && getPolygonArea(points) < smallest) {
            smallest = getPolygonArea(points);
            subjectParcel = features.get(i);
          }
        }
      }
      if (!subjectGiven && homePoint == null && parcelCount == 1) {
        for (int i = 0; i < features.size(); i++) {
          if (planPoints.get(i) != null && features.get(i).type.equals("polygon") && planPoints.get(i).size() >= 3) {
            subjectParcel = features.get(i);
          }
        }
      }
      smallest = Double.MAX_VALUE;
      for (Polyline polyline : this.home.getPolylines()) {
        if ("property".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY)) && polyline.isClosedPath()
            && polyline.getPointCount() >= 3) {
          List<float []> lot = Arrays.asList(polyline.getPoints());
          if ((homePoint == null || contains(lot, homePoint [0], homePoint [1])) && getPolygonArea(lot) < smallest) {
            smallest = getPolygonArea(lot);
            existingLot = polyline;
          }
        }
      }
    }
    Map<String, Integer> counts = new TreeMap<String, Integer>();
    Map<String, Integer> pieceCounts = new TreeMap<String, Integer>();
    Map<String, Integer> heightUses = new LinkedHashMap<String, Integer>();
    Set<String> warnings = new LinkedHashSet<String>();
    List<String> failures = new ArrayList<String>();
    String subjectName = null;
    String parcelAttributes = null;
    int skipped = 0;
    int built = 0;
    int outside = 0;
    int existing = 0;
    int alreadyDrawn = 0;
    int sameLots = 0;
    this.executor.beginTurnUpdate();
    for (int index = 0; index < features.size(); index++) {
      Feature feature = features.get(index);
      if (built >= limit) {
        skipped++;
        continue;
      }
      Map<?, ?> rule = null;
      for (Map<?, ?> candidate : rules) {
        if (matches(feature, candidate)) {
          rule = candidate;
          break;
        }
      }
      if (rule == null && osm) {
        rule = getOsmRule(feature, only);
        if (rule == null) {
          skipped++;
          continue;
        }
      } else if (rule == null && defaultRule.get("as") != null && matches(feature, defaultRule)) {
        rule = defaultRule;
      } else if (rule == null && (!rules.isEmpty() || defaultRule.get("as") != null)) {
        // Features that no rule chooses are left out when rules are given
        skipped++;
        continue;
      }
      List<float []> points = planPoints.get(index);
      if (points == null) {
        continue;
      }
      String as = rule != null && rule.get("as") != null ? rule.get("as").toString().toLowerCase(Locale.ENGLISH)
          : feature.type.equals("point") ? "label" : "polyline";
      if (as.equals("skip") || as.equals("none")) {
        skipped++;
        continue;
      }
      boolean line = feature.type.equals("line");
      boolean building = as.equals("building") || as.equals("context_building");
      List<List<float []>> parts = new ArrayList<List<float []>>();
      if (clip == null || points.isEmpty()) {
        parts.add(points);
      } else if (line) {
        parts.addAll(clipLine(points, clip));
      } else if (feature.type.equals("point")) {
        if (points.get(0) [0] >= clip [0] && points.get(0) [0] <= clip [2] && points.get(0) [1] >= clip [1] && points.get(0) [1] <= clip [3]) {
          parts.add(points);
        }
      } else {
        // A lot is kept if it touches the area, anything else if its middle is in it
        float [] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, 0, 0};
        for (float [] point : points) {
          box [0] = Math.min(box [0], point [0]);
          box [1] = Math.min(box [1], point [1]);
          box [2] = Math.max(box [2], point [0]);
          box [3] = Math.max(box [3], point [1]);
          box [4] += point [0] / points.size();
          box [5] += point [1] / points.size();
        }
        if (as.equals("property_line") ? box [0] <= clip [2] && box [2] >= clip [0] && box [1] <= clip [3] && box [3] >= clip [1]
            : box [4] >= clip [0] && box [4] <= clip [2] && box [5] >= clip [1] && box [5] <= clip [3]) {
          parts.add(points);
        }
      }
      if (parts.isEmpty()) {
        outside++;
        continue;
      }
      if (building && !command.getBoolean("keep_existing", false) && isBuilt(points)) {
        existing++;
        continue;
      }
      boolean subject = false;
      if (as.equals("property_line") && feature.type.equals("polygon")) {
        subject = feature == subjectParcel;
        if (subjectGiven) {
          subject = true;
          for (Map.Entry<?, ?> entry : ((Map<?, ?>)command.getParameters().get("subject")).entrySet()) {
            Object value = find(feature.properties, String.valueOf(entry.getKey()));
            subject &= value != null && value.toString().equalsIgnoreCase(String.valueOf(entry.getValue()));
          }
        } else if (!parcels) {
          // A property line asked by a rule
          subject = true;
        }
        if (subject && parcels && existingLot != null) {
          // The lot of the home is drawn already
          alreadyDrawn++;
          continue;
        }
        if (!subject && parcels && isNeighborLotDrawn(points)) {
          sameLots++;
          continue;
        }
        if (parcelAttributes == null) {
          parcelAttributes = feature.properties.keySet().toString();
        }
      }
      if (line && maxSegment > 0 && (osm || "import_roads".equals(action)) && (as.equals("road") || as.equals("driveway"))) {
        List<List<float []>> pieces = new ArrayList<List<float []>>();
        for (List<float []> part : parts) {
          pieces.addAll(splitLine(part, maxSegment));
        }
        parts = pieces;
      }
      String key = null;
      int drawn = 0;
      for (int i = 0; i < parts.size(); i++) {
        try {
          String [] name = new String [1];
          String partKey = build(feature, as, rule, parts.get(i), heightScale, heightUnitScale, command, tally,
              i == parts.size() / 2, subject, heightUses, warnings, name);
          if (partKey != null) {
            key = partKey;
            drawn++;
            if (subject && parcels) {
              subjectName = name [0];
            }
          }
        } catch (IOException ex) {
          if (failures.size() < 5) {
            String name = getName(feature.properties, rule);
            failures.add((name != null ? "\"" + name + "\": " : "") + ex.getMessage());
          }
        }
      }
      if (drawn > 0) {
        counts.put(key, Integer.valueOf(counts.containsKey(key) ? counts.get(key).intValue() + 1 : 1));
        pieceCounts.put(key, Integer.valueOf(pieceCounts.containsKey(key) ? pieceCounts.get(key).intValue() + drawn : drawn));
        built++;
      } else {
        skipped++;
      }
    }
    if (built == 0 && (alreadyDrawn > 0 || existing > 0 || sameLots > 0)) {
      notes.add("Nothing new in " + source + ": " + (alreadyDrawn > 0 ? "the lot of the home is drawn already by "
          + this.executor.getId(existingLot) + (sameLots > 0 ? ", and its " + sameLots + " neighbor lots too" : "")
          : sameLots > 0 ? "its " + sameLots + " neighbor lots are drawn already"
          : existing + " buildings stand where the home or a neighbor building is already")
          + (outside > 0 ? "; " + outside + " features are outside the area asked" : ""));
      return;
    }
    if (built == 0 && outside > 0 && far == 0 && failures.isEmpty()) {
      throw new IOException("the " + outside + " features of " + source + " that could be drawn are outside the area asked, "
          + format((clip [2] - clip [0]) / 100) + " x " + format((clip [3] - clip [1]) / 100) + " m around ("
          + format((clip [0] + clip [2]) / 2) + ", " + format((clip [1] + clip [3]) / 2) + "): a larger \"buffer_m\" or an \"area\" takes them");
    }
    if (built == 0) {
      throw new IOException("nothing was drawn from the " + features.size() + " features of " + source
          + (far > 0 ? ": " + far + " are more than 10 km from the plan, so its georeference, \"srs\" or \"units\" is wrong" : "")
          + (failures.isEmpty() ? "" : ". " + failures));
    }
    StringBuilder text = new StringBuilder("Imported " + built + " of the " + features.size() + " features of " + source + ": ");
    boolean first = true;
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      text.append(first ? "" : ", ").append(entry.getValue()).append(' ').append(entry.getKey());
      if (pieceCounts.get(entry.getKey()).intValue() > entry.getValue().intValue()) {
        text.append(" (in ").append(pieceCounts.get(entry.getKey())).append(" pieces of ").append(format(maxSegment / 100))
            .append(" m at most, which each follow the ground)");
      }
      first = false;
    }
    if (clip != null) {
      text.append(", in the area of ").append(format((clip [2] - clip [0]) / 100)).append(" x ").append(format((clip [3] - clip [1]) / 100))
          .append(" m around (").append(format((clip [0] + clip [2]) / 2)).append(", ").append(format((clip [1] + clip [3]) / 2)).append(")");
      if (outside > 0) {
        text.append("; ").append(outside).append(" features outside it were left out, and the lines were cut at its edge");
      }
    }
    if (existing > 0) {
      text.append(". ").append(existing).append(existing == 1 ? " building was" : " buildings were")
          .append(" left out, where the home or a neighbor building stands already (\"keep_existing\":true draws them anyway)");
    }
    if (!heightUses.isEmpty()) {
      text.append(". Heights of the buildings: ");
      boolean firstUse = true;
      for (Map.Entry<String, Integer> entry : heightUses.entrySet()) {
        text.append(firstUse ? "" : ", ").append(entry.getValue()).append(" from ").append(entry.getKey());
        firstUse = false;
      }
    }
    if (parcels) {
      int neighbors = counts.containsKey("neighbor_lot") ? counts.get("neighbor_lot").intValue() : 0;
      if (alreadyDrawn > 0 || existingLot != null && subjectParcel != null) {
        text.append(". The lot of the home is drawn already by ").append(this.executor.getId(existingLot))
            .append(": its parcel wasn't drawn a second time");
      } else if (subjectParcel != null && subjectName == null) {
        text.append(". The lot of the home wasn't reached");
      } else if (subjectName != null) {
        text.append(". The lot of the home is \"").append(subjectName).append("\"")
            .append(subjectGiven ? "" : homePoint != null ? ", the parcel that contains the home" : "");
      } else {
        text.append(". None of the parcels ").append(subjectGiven ? "matches \"subject\"" : "contains the home")
            .append(": \"subject\":{\"<attribute>\":\"<value>\"} names its lot");
      }
      if (sameLots > 0) {
        text.append(". ").append(sameLots).append(" neighbor lots drawn already were left out");
      }
      if (neighbors > 0) {
        text.append(". The ").append(neighbors).append(neighbors == 1 ? " other is a neighbor lot" : " others are neighbor lots")
            .append(", drawn dash-dot in gray: the checks of the walls and of the fences don't use them");
      }
      if (warnings.isEmpty() && parcelAttributes != null && this.unlabelledParcels > 0) {
        text.append(". ").append(this.unlabelledParcels).append(" have no number or address among their attributes ")
            .append(parcelAttributes).append(": \"label_from\":[\"<attribute>\",...] names the ones to write");
      }
    }
    for (String warning : warnings) {
      text.append(". ").append(warning);
    }
    if (skipped > 0) {
      text.append(". ").append(skipped).append(" were left out (no rule for them, nothing to draw, or more than ").append(limit).append(")");
    }
    if (far > 0) {
      text.append(". ").append(far).append(" are too far from the plan");
    }
    if (!failures.isEmpty()) {
      text.append(". Couldn't draw some: ").append(failures);
    }
    notes.add(text.toString());
  }

  /**
   * Builds what a feature becomes and returns what it is counted as, or <code>null</code> if it has nothing to draw.
   */
  private String build(Feature feature, String as, Map<?, ?> rule, List<float []> points, float heightScale, float heightUnitScale,
                       AssistantCommand command, int [] tally, boolean labelled, boolean subject,
                       Map<String, Integer> heightUses, Set<String> warnings, String [] builtName) throws IOException {
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    boolean building = as.equals("building") || as.equals("context_building");
    if (rule != null) {
      for (Map.Entry<?, ?> entry : rule.entrySet()) {
        String key = String.valueOf(entry.getKey());
        if (!Arrays.asList("where", "if", "geometry", "as", "label", "height_units").contains(key)
            && !key.endsWith("_from") && !key.endsWith("_attribute")) {
          parameters.put(key, entry.getValue());
        }
      }
    }
    String name = getName(feature.properties, rule);
    if (name != null && parameters.get("name") == null && !building) {
      parameters.put("name", name.length() > 60 ? name.substring(0, 60) : name);
    }
    List<Object> tags = new ArrayList<Object>();
    if (parameters.get("tags") instanceof List) {
      tags.addAll((List<?>)parameters.get("tags"));
    }
    tags.add("imported");
    String tagAttribute = getAttributeName(rule, "tag");
    if (tagAttribute != null && find(feature.properties, tagAttribute) != null) {
      tags.add(HomeItemTags.cleanTag(find(feature.properties, tagAttribute).toString()));
    }
    // The values that attributes give: <key>_from or <key>_attribute names the attribute of the key
    if (rule != null) {
      float ruleHeightScale = getUnitScale(rule.get("height_units") != null ? rule.get("height_units").toString() : null);
      if (ruleHeightScale > 0) {
        heightUnitScale = ruleHeightScale;
      }
      for (Object ruleKey : rule.keySet()) {
        String key = String.valueOf(ruleKey);
        String base = key.endsWith("_from") ? key.substring(0, key.length() - 5)
            : key.endsWith("_attribute") ? key.substring(0, key.length() - 10) : null;
        if (base == null || base.length() == 0 || Arrays.asList("name", "species", "tag", "label").contains(base)
            || building && Arrays.asList("height", "stories", "levels").contains(base)) {
          continue;
        }
        Object value = find(feature.properties, String.valueOf(rule.get(key)));
        if (value == null || value.toString().trim().length() == 0) {
          continue;
        }
        if (Arrays.asList("height", "width", "thickness", "depth", "length", "diameter", "elevation").contains(base)) {
          double number = parseNumber(value);
          if (!Double.isNaN(number)) {
            parameters.put(base, Double.valueOf(number * (base.equals("height") && heightUnitScale > 0 ? heightUnitScale : heightScale)));
          }
        } else if (base.equals("style") && as.equals("fence")) {
          String style = getFenceStyle(value.toString());
          if (style != null) {
            parameters.put("style", style);
          } else if (warnings.size() < 4) {
            warnings.add("Warning: \"" + value + "\" of the attribute " + rule.get(key) + " isn't a style of fence, so the "
                + (parameters.get("style") != null ? "style " + parameters.get("style") : "usual style") + " was used");
          }
        } else {
          parameters.put(base, value instanceof Number || value instanceof Boolean ? value : value.toString().trim());
        }
      }
    }
    if (building) {
      // The height of a building: an attribute, its levels, or the one of the command
      String heightAttribute = getAttributeName(rule, "height");
      String levelsAttribute = getAttributeName(rule, "stories") != null ? getAttributeName(rule, "stories") : getAttributeName(rule, "levels");
      boolean explicit = parameters.get("height") != null || parameters.get("stories") != null;
      parameters.remove("levels");
      String use = null;
      List<String> heightNames = heightAttribute != null ? Arrays.asList(heightAttribute)
          : explicit ? Collections.<String>emptyList()
          : Arrays.asList("height", "building:height", "bldg_height", "bldgheight", "building_height", "height_m", "height_ft", "hgt");
      for (String heightName : heightNames) {
        Object value = find(feature.properties, heightName);
        double number = value != null ? parseNumber(value) : Double.NaN;
        if (number > 0) {
          String lowerName = heightName.toLowerCase(Locale.ENGLISH);
          float scale = heightUnitScale > 0 ? heightUnitScale : lowerName.endsWith("_ft") ? 30.48f : lowerName.endsWith("_m") ? 100
              : number > 100 ? 1 : 100;
          parameters.put("height", Double.valueOf(number * scale));
          parameters.remove("stories");
          use = "the attribute \"" + heightName + "\" in " + (scale == 1 ? "cm" : scale == 100 ? "m" : "ft")
              + (heightUnitScale > 0 ? "" : scale == 1 ? " (more than 100: \"height_units\":\"m|ft\" says otherwise)"
                  : " (\"height_units\":\"cm|ft\" says otherwise)");
          break;
        }
      }
      if (use == null) {
        List<String> levelNames = levelsAttribute != null ? Arrays.asList(levelsAttribute)
            : explicit ? Collections.<String>emptyList()
            : Arrays.asList("levels", "building:levels", "stories", "num_floors", "numfloors", "floors", "num_stories", "numstories");
        for (String levelName : levelNames) {
          Object value = find(feature.properties, levelName);
          double number = value != null ? parseNumber(value) : Double.NaN;
          if (number >= 0.5 && number <= 60) {
            parameters.put("stories", Double.valueOf(number));
            parameters.remove("height");
            use = "the levels of \"" + levelName + "\" x 280 cm";
            break;
          }
        }
      }
      if (use == null) {
        use = parameters.get("height") != null ? "the height given" : parameters.get("stories") != null ? "the levels given x 280 cm"
            : "the usual height of one level, without attribute of height or of levels"
              + (heightAttribute != null ? " (no \"" + heightAttribute + "\")" : "");
      }
      heightUses.put(use, Integer.valueOf(heightUses.containsKey(use) ? heightUses.get(use).intValue() + 1 : 1));
    }
    List<Object> list = new ArrayList<Object>();
    for (float [] point : points) {
      list.add(Arrays.asList((Object)Double.valueOf(Math.round(point [0] * 10) / 10.), Double.valueOf(Math.round(point [1] * 10) / 10.)));
    }
    String action;
    boolean taggable = true;
    if (feature.type.equals("point")) {
      parameters.put("x", Double.valueOf(points.get(0) [0]));
      parameters.put("y", Double.valueOf(points.get(0) [1]));
      if (as.equals("tree") || as.equals("shrub")) {
        action = as.equals("tree") ? "add_tree" : "add_shrub";
        parameters.remove("name");
        String speciesAttribute = getAttributeName(rule, "species");
        if (speciesAttribute != null && find(feature.properties, speciesAttribute) != null) {
          String species = find(feature.properties, speciesAttribute).toString();
          if (AssistantPlantBuilder.getSpecies(species) != null) {
            parameters.put("species", species);
          } else {
            tags.add(HomeItemTags.cleanTag(species));
          }
        }
      } else if (as.equals("utility")) {
        action = "add_utility";
      } else if (as.equals("furniture")) {
        action = "add_furniture";
        if (rule == null || rule.get("name") == null) {
          throw new IOException("a rule \"as\":\"furniture\" gives the \"name\" of the catalog piece");
        }
        parameters.put("name", rule.get("name"));
      } else if (as.equals("label")) {
        if (name == null) {
          return null;
        }
        action = "add_label";
        parameters.clear();
        parameters.put("text", name);
        parameters.put("x", Double.valueOf(points.get(0) [0]));
        parameters.put("y", Double.valueOf(points.get(0) [1]));
        taggable = false;
      } else {
        throw new IOException("a point becomes a tree, a shrub, a label, a utility or furniture, not \"" + as + "\"");
      }
    } else {
      if (points.size() < (feature.type.equals("polygon") ? 3 : 2)) {
        return null;
      }
      parameters.put("points", list);
      boolean polygon = feature.type.equals("polygon");
      if (as.equals("polyline") || as.equals("line")) {
        action = "add_polyline";
        if (polygon) {
          parameters.put("closed", Boolean.TRUE);
        }
        taggable = false;
      } else if (as.equals("fence")) {
        action = "add_fence";
        if (polygon) {
          parameters.put("closed", Boolean.TRUE);
        }
      } else if (as.equals("hedge")) {
        action = "add_hedge";
        if (polygon) {
          parameters.put("closed", Boolean.TRUE);
        }
        taggable = false;
      } else if (!polygon && (as.equals("path") || as.equals("driveway") || as.equals("road") || as.equals("sidewalk") || as.equals("curb"))) {
        action = as.equals("path") ? "add_path" : as.equals("sidewalk") ? "add_sidewalk" : as.equals("curb") ? "add_curb" : "add_driveway";
        taggable = !as.equals("path");
        if (as.equals("road")) {
          if (parameters.get("name") == null) {
            parameters.put("name", "Road");
          }
          if (parameters.get("material") == null) {
            parameters.put("material", "asphalt");
          }
          tags.add("road");
        }
        if (labelled && rule != null && (Boolean.TRUE.equals(rule.get("label")) || as.equals("road") && rule.get("label") == null)
            && name != null) {
          // The name of a road, in its middle
          float [] middle = points.get(points.size() / 2);
          float [] before = points.get(Math.max(0, points.size() / 2 - 1));
          Map<String, Object> label = new LinkedHashMap<String, Object>();
          label.put("text", name);
          label.put("x", Double.valueOf((middle [0] + before [0]) / 2));
          label.put("y", Double.valueOf((middle [1] + before [1]) / 2));
          this.executor.applySubCommand(new AssistantCommand("add_label", label), tally, new ArrayList<String>());
        }
      } else if (polygon && as.equals("property_line")) {
        action = "add_property_line";
        taggable = false;
        // What names a parcel: its number and its address
        String label = getParcelLabel(feature.properties, command.getStringList("label_from"), command.getBoolean("label_owner", false));
        if (!subject) {
          // A neighbor: a line of the plan that no check takes for the lot of the home
          action = "add_polyline";
          parameters.put("kind", "neighbor_lot");
          parameters.put("closed", Boolean.TRUE);
        }
        String acres = format(getPolygonArea(points) / 10000 / 4046.86);
        parameters.put("name", (subject ? "Property line" : "Neighbor lot") + (label != null ? " " + label : "") + " (" + acres + " ac)");
        if (label != null) {
          parameters.put("label", label + (subject ? "" : " (neighbor)"));
        } else {
          this.unlabelledParcels++;
        }
        as = subject ? "property_line" : "neighbor_lot";
      } else if (polygon && as.equals("room")) {
        action = "add_room";
        taggable = false;
      } else if (polygon && as.equals("slab")) {
        action = "add_slab";
      } else if (polygon && (as.equals("patio") || as.equals("planting_bed") || as.equals("pond") || as.equals("pool") || as.equals("deck"))) {
        action = "add_" + as;
        taggable = !as.equals("planting_bed");
      } else if (polygon && (as.equals("building") || as.equals("context_building"))) {
        action = "add_context_building";
        if (name != null) {
          parameters.put("name", name.length() > 60 ? name.substring(0, 60) : name);
        }
      } else if (polygon && (as.equals("driveway") || as.equals("road"))) {
        action = "add_driveway";
        parameters.put("polygon", list);
        parameters.remove("points");
      } else {
        throw new IOException("a " + feature.type + " doesn't become \"" + as + "\"");
      }
    }
    if (taggable) {
      parameters.put("tags", tags);
    }
    Set<HomePieceOfFurniture> before = as.equals("road") ? new HashSet<HomePieceOfFurniture>(this.home.getFurniture()) : null;
    List<String> notes = new ArrayList<String>();
    this.executor.applySubCommand(new AssistantCommand(action, parameters), tally, notes);
    for (String note : notes) {
      if (note.startsWith("Couldn't")) {
        throw new IOException(note.substring(note.indexOf('(') + 1, note.lastIndexOf(')')));
      } else if (note.startsWith("Warning:") && warnings.size() < 4) {
        warnings.add(note);
      }
    }
    if (before != null) {
      // A road isn't a driveway
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (!before.contains(piece)) {
          List<String> pieceTags = new ArrayList<String>(HomeItemTags.getTags(piece));
          pieceTags.remove("driveway");
          if (!pieceTags.contains("road")) {
            pieceTags.add("road");
          }
          piece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(pieceTags));
        }
      }
    }
    builtName [0] = parameters.get("name") != null ? parameters.get("name").toString() : null;
    return as;
  }

  /**
   * Returns the number and the address of a parcel among its attributes, or the values of the
   * attributes named, or <code>null</code> if it has none.
   */
  private static String getParcelLabel(Map<String, Object> properties, List<String> labelNames, boolean owner) {
    List<String> parts = new ArrayList<String>();
    if (!labelNames.isEmpty()) {
      for (String labelName : labelNames) {
        Object value = find(properties, labelName);
        if (value != null && isMeaningful(value) && parts.size() < 3) {
          parts.add(value.toString().trim());
        }
      }
    } else {
      String [][] exactNames = {
          {"parcelid", "parcelno", "parcelnum", "parcelnumber", "parcelidentifier", "pin", "apn", "parcel", "taxparcel", "taxid",
           "mapparcel", "parcelpin", "gpin", "pid", "realkey"},
          {"situs", "siteaddress", "siteaddr", "situsaddress", "situsaddr", "propertyaddress", "propaddr", "propertyaddr", "address",
           "fulladdress", "fulladdr", "physicaladdress", "streetaddress", "location", "locaddr"},
          {"owner", "ownername", "owner1", "ownernme1", "ownname", "ownername1"}};
      for (int kind = 0; kind < (owner ? 3 : 2); kind++) {
        String found = null;
        // A usual name first, then a name that looks like one
        for (int pass = 0; pass < 2 && found == null; pass++) {
          for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
            boolean named = pass == 0 ? Arrays.asList(exactNames [kind]).contains(key)
                : kind == 0 ? key.contains("parcel") && (key.contains("id") || key.contains("no") || key.contains("num") || key.contains("pin"))
                : kind == 1 ? (key.contains("situs") || key.contains("addr")) && !key.contains("mail") && !key.contains("own")
                    && !key.contains("city") && !key.contains("zip") && !key.contains("state")
                : key.contains("owner") && !key.contains("addr") && !key.contains("city") && !key.contains("zip")
                    && !key.contains("state") && !key.contains("mail");
            if (named && entry.getValue() != null && isMeaningful(entry.getValue())) {
              found = entry.getValue().toString().trim();
              break;
            }
          }
        }
        if (found != null) {
          parts.add(found.length() > 50 ? found.substring(0, 50) : found);
        }
      }
    }
    if (parts.isEmpty()) {
      return null;
    }
    StringBuilder label = new StringBuilder();
    for (String part : parts) {
      label.append(label.length() > 0 ? " - " : "").append(part);
    }
    return label.toString();
  }

  private static boolean isMeaningful(Object value) {
    String text = value.toString().trim();
    return text.length() > 0 && !text.equalsIgnoreCase("null") && !text.equals("0") && !text.equalsIgnoreCase("none");
  }

  /**
   * Returns the place the home stands at: the middle of its walls, or the point of its georeference,
   * or <code>null</code> if it has neither.
   */
  private float [] getHomePoint() {
    double x = 0;
    double y = 0;
    int count = 0;
    for (Wall wall : this.home.getWalls()) {
      x += (wall.getXStart() + wall.getXEnd()) / 2;
      y += (wall.getYStart() + wall.getYEnd()) / 2;
      count++;
    }
    if (count > 0) {
      return new float [] {(float)(x / count), (float)(y / count)};
    }
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    return georeference != null ? new float [] {(float)georeference.getX(), (float)georeference.getY()} : null;
  }

  private static boolean contains(List<float []> polygon, float x, float y) {
    boolean inside = false;
    for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
      float [] a = polygon.get(i);
      float [] b = polygon.get(j);
      if ((a [1] > y) != (b [1] > y) && x < (b [0] - a [0]) * (y - a [1]) / (b [1] - a [1]) + a [0]) {
        inside = !inside;
      }
    }
    return inside;
  }

  /**
   * Returns <code>true</code> if the line of a neighbor lot with the same points exists.
   */
  private boolean isNeighborLotDrawn(List<float []> polygon) {
    for (Polyline polyline : this.home.getPolylines()) {
      if (AssistantSiteCommands.NEIGHBOR_LOT_KIND.equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))
          && polyline.getPointCount() == polygon.size()) {
        float [][] points = polyline.getPoints();
        boolean same = true;
        for (int i = 0; i < points.length && same; i++) {
          same = Math.hypot(points [i][0] - polygon.get(i) [0], points [i][1] - polygon.get(i) [1]) < 5;
        }
        if (same) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns <code>true</code> if the home or a neighbor building stands already where a building would be drawn.
   */
  private boolean isBuilt(List<float []> polygon) {
    float x = 0;
    float y = 0;
    for (float [] point : polygon) {
      x += point [0] / polygon.size();
      y += point [1] / polygon.size();
    }
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if ("context_building".equals(piece.getProperty(AssistantGardenCommands.GARDEN_PROPERTY))
          && (piece.containsPoint(x, y, 0) || contains(polygon, piece.getX(), piece.getY()))) {
        return true;
      }
    }
    // The home itself: most of its walls are in the building
    int inside = 0;
    for (Wall wall : this.home.getWalls()) {
      if (contains(polygon, (wall.getXStart() + wall.getXEnd()) / 2, (wall.getYStart() + wall.getYEnd()) / 2)) {
        inside++;
      }
    }
    if (inside > 0 && inside * 2 >= this.home.getWalls().size()) {
      return true;
    }
    for (Room room : this.home.getRooms()) {
      if (room.isCeilingVisible() && room.containsPoint(x, y, 0) && inside > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the parts of a line that are in a box: x min, y min, x max, y max.
   */
  public static List<List<float []>> clipLine(List<float []> points, float [] box) {
    List<List<float []>> parts = new ArrayList<List<float []>>();
    List<float []> part = null;
    for (int i = 0; i + 1 < points.size(); i++) {
      float [] a = points.get(i);
      float [] b = points.get(i + 1);
      // The part of the segment in the box, by Liang and Barsky
      double start = 0;
      double end = 1;
      double dx = b [0] - a [0];
      double dy = b [1] - a [1];
      double [] p = {-dx, dx, -dy, dy};
      double [] q = {a [0] - box [0], box [2] - a [0], a [1] - box [1], box [3] - a [1]};
      boolean visible = true;
      for (int k = 0; k < 4 && visible; k++) {
        if (p [k] == 0) {
          visible = q [k] >= 0;
        } else if (p [k] < 0) {
          start = Math.max(start, q [k] / p [k]);
        } else {
          end = Math.min(end, q [k] / p [k]);
        }
      }
      if (!visible || start > end) {
        part = null;
        continue;
      }
      float [] first = {(float)(a [0] + dx * start), (float)(a [1] + dy * start)};
      float [] last = {(float)(a [0] + dx * end), (float)(a [1] + dy * end)};
      if (part == null || start > 0) {
        part = new ArrayList<float []>();
        parts.add(part);
        part.add(first);
      }
      if (Math.hypot(last [0] - first [0], last [1] - first [1]) > 0.5) {
        part.add(last);
      }
      if (end < 1) {
        part = null;
      }
    }
    for (int i = parts.size() - 1; i >= 0; i--) {
      if (parts.get(i).size() < 2) {
        parts.remove(i);
      }
    }
    return parts;
  }

  /**
   * Returns a line cut in pieces of a length at most, which share their ends.
   */
  static List<List<float []>> splitLine(List<float []> points, float maxLength) {
    List<List<float []>> parts = new ArrayList<List<float []>>();
    double total = 0;
    for (int i = 0; i + 1 < points.size(); i++) {
      total += Math.hypot(points.get(i + 1) [0] - points.get(i) [0], points.get(i + 1) [1] - points.get(i) [1]);
    }
    int count = (int)Math.ceil(total / maxLength);
    if (count <= 1) {
      parts.add(points);
      return parts;
    }
    double pieceLength = total / count;
    List<float []> part = new ArrayList<float []>();
    part.add(points.get(0));
    double length = 0;
    for (int i = 0; i + 1 < points.size(); i++) {
      float [] a = points.get(i);
      float [] b = points.get(i + 1);
      double segment = Math.hypot(b [0] - a [0], b [1] - a [1]);
      double done = 0;
      while (parts.size() < count - 1 && length + (segment - done) > pieceLength + 0.01) {
        done += pieceLength - length;
        float [] cut = {(float)(a [0] + (b [0] - a [0]) * done / segment), (float)(a [1] + (b [1] - a [1]) * done / segment)};
        part.add(cut);
        parts.add(part);
        part = new ArrayList<float []>();
        part.add(cut);
        length = 0;
      }
      length += segment - done;
      if (Math.hypot(b [0] - part.get(part.size() - 1) [0], b [1] - part.get(part.size() - 1) [1]) > 0.5) {
        part.add(b);
      }
    }
    if (part.size() >= 2) {
      parts.add(part);
    }
    return parts;
  }

  // ---- Web services ----

  private void checkWebAccess(String action, String host) throws IOException {
    if (!this.webAccessAllowed) {
      throw new IOException(action + " asks " + host + " for data about the place of the home, which the user didn't allow. "
          + "They tick \"Allow web services\" in Sweet Home 3D (Tools > Claude MCP server...). Without it, give a file");
    }
  }

  private static byte [] download(String address, String postBody) throws IOException {
    HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(60000);
    connection.setRequestProperty("User-Agent", "SweetHome3D");
    if (postBody != null) {
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      connection.getOutputStream().write(postBody.getBytes("UTF-8"));
    }
    int code = connection.getResponseCode();
    if (code != 200) {
      throw new IOException("the server " + new URL(address).getHost() + " answered " + code);
    }
    InputStream in = connection.getInputStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [16384];
      int size;
      while ((size = in.read(buffer)) != -1) {
        out.write(buffer, 0, size);
        if (out.size() > MAX_DOWNLOAD) {
          throw new IOException("the answer of " + new URL(address).getHost() + " is larger than 40 MB: ask for a smaller area");
        }
      }
      return out.toByteArray();
    } finally {
      in.close();
    }
  }

  /**
   * Returns the box around the home, or around an area of the plan, in degrees: south, west, north, east.
   */
  private double [] getGeographicBounds(AssistantCommand command, double defaultBuffer) throws IOException {
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (georeference == null) {
      throw new IOException("this home has no georeference: {\"action\":\"set_georeference\",\"lat\":N,\"lon\":N,\"x\":N,\"y\":N} "
          + "tells where it is");
    }
    float [] bounds = getPlanBounds(command, defaultBuffer);
    double [] box = {90, 180, -90, -180};
    for (float [] corner : new float [][] {{bounds [0], bounds [1]}, {bounds [2], bounds [1]}, {bounds [2], bounds [3]}, {bounds [0], bounds [3]}}) {
      double [] place = georeference.toLatitudeLongitude(corner [0], corner [1]);
      box [0] = Math.min(box [0], place [0]);
      box [1] = Math.min(box [1], place [1]);
      box [2] = Math.max(box [2], place [0]);
      box [3] = Math.max(box [3], place [1]);
    }
    return box;
  }

  /**
   * Returns the box of the plan a command asks for: its area, or the home with a margin. The home
   * is its walls; without wall, its lot; without lot, the point of its georeference.
   */
  private float [] getPlanBounds(AssistantCommand command, double defaultBuffer) throws IOException {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    List<float []> area = command.getPoints("area");
    List<float [][]> shapes = new ArrayList<float [][]>();
    float buffer = 0;
    if (area.size() >= 2) {
      shapes.add(area.toArray(new float [area.size()][]));
    } else {
      buffer = (float)(Math.max(1, Math.min(2000, command.getDouble("buffer_m", defaultBuffer))) * 100);
      // The home is its walls; what was imported or drawn around it doesn't count
      for (Wall wall : this.home.getWalls()) {
        shapes.add(wall.getPoints());
      }
      float [] homePoint = getHomePoint();
      if (shapes.isEmpty()) {
        // Without wall, the lot of the home, then the point of its georeference
        Polyline lot = null;
        for (Polyline polyline : this.home.getPolylines()) {
          if ("property".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY)) && polyline.isClosedPath()
              && (homePoint == null || contains(Arrays.asList(polyline.getPoints()), homePoint [0], homePoint [1]))
              && (lot == null || getPolygonArea(Arrays.asList(polyline.getPoints())) < getPolygonArea(Arrays.asList(lot.getPoints())))) {
            lot = polyline;
          }
        }
        if (lot != null) {
          shapes.add(lot.getPoints());
        }
      }
      if (shapes.isEmpty()) {
        shapes.add(new float [][] {{homePoint != null ? homePoint [0] : 0, homePoint != null ? homePoint [1] : 0}});
      }
    }
    for (float [][] shape : shapes) {
      for (float [] point : shape) {
        bounds [0] = Math.min(bounds [0], point [0] - buffer);
        bounds [1] = Math.min(bounds [1], point [1] - buffer);
        bounds [2] = Math.max(bounds [2], point [0] + buffer);
        bounds [3] = Math.max(bounds [3], point [1] + buffer);
      }
    }
    if (bounds [2] - bounds [0] > 500000 || bounds [3] - bounds [1] > 500000) {
      throw new IOException("the area is more than 5 km wide: give a smaller \"buffer_m\" or \"area\"");
    }
    return bounds;
  }

  private String fetchOsm(AssistantCommand command) throws IOException {
    checkWebAccess("import_osm", "overpass-api.de (OpenStreetMap)");
    double [] box = getGeographicBounds(command, 150);
    String bbox = String.format(Locale.US, "(%.7f,%.7f,%.7f,%.7f)", box [0], box [1], box [2], box [3]);
    String query = "[out:json][timeout:50];(way[building]" + bbox + ";way[highway]" + bbox + ";way[barrier]" + bbox
        + ";node[natural=tree]" + bbox + ";way[natural=water]" + bbox + ";way[leisure=swimming_pool]" + bbox + ";);out geom;";
    return new String(download("https://overpass-api.de/api/interpreter", "data=" + URLEncoder.encode(query, "UTF-8")), "UTF-8");
  }

  private String fetchParcels(AssistantCommand command) throws IOException {
    String address = command.getString("url").trim();
    if (!address.toLowerCase(Locale.ENGLISH).startsWith("https://") && !address.toLowerCase(Locale.ENGLISH).startsWith("http://")) {
      throw new IOException("\"url\" is the address of a layer of an ArcGIS REST service, like https://.../FeatureServer/0");
    }
    checkWebAccess("import_parcels", new URL(address).getHost());
    double [] box = getGeographicBounds(command, 150);
    if (address.endsWith("/")) {
      address = address.substring(0, address.length() - 1);
    }
    if (!address.toLowerCase(Locale.ENGLISH).endsWith("/query")) {
      address += "/query";
    }
    String query = "?where=1%3D1&geometry=" + String.format(Locale.US, "%.7f,%.7f,%.7f,%.7f", box [1], box [0], box [3], box [2])
        + "&geometryType=esriGeometryEnvelope&inSR=4326&spatialRel=esriSpatialRelIntersects&outFields=*&returnGeometry=true"
        + "&outSR=4326&f=geojson";
    String answer = new String(download(address + query, null), "UTF-8");
    if (answer.contains("\"error\"") && !answer.contains("\"features\"")) {
      throw new IOException("the parcel service refused the request: " + answer.substring(0, Math.min(200, answer.length())));
    }
    return answer;
  }

  private void fetchElevation(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String source = command.getString("source") != null ? command.getString("source").trim().toLowerCase(Locale.ENGLISH) : "usgs_3dep";
    if (!source.equals("usgs_3dep") && !source.equals("usgs_epqs") && !source.equals("usgs")) {
      throw new IOException("\"source\" is usgs_3dep (the United States). opentopography and mapzen need a key or are closed: "
          + "download their raster, convert it with gdal_translate -of AAIGrid and give it to set_terrain \"file\"");
    }
    checkWebAccess("fetch_elevation", "elevation.nationalmap.gov (USGS)");
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (georeference == null) {
      throw new IOException("this home has no georeference: set_georeference tells where it is");
    }
    float [] bounds = getPlanBounds(command, 60);
    float resolution = (float)Math.max(100, Math.min(3000, command.getDouble("resolution_cm", 300)));
    int columns = (int)Math.ceil((bounds [2] - bounds [0]) / resolution) + 1;
    int rows = (int)Math.ceil((bounds [3] - bounds [1]) / resolution) + 1;
    String coarser = "";
    if ((long)columns * rows > 12000) {
      double scale = Math.sqrt((double)columns * rows / 12000);
      if (command.has("resolution_cm")) {
        // The resolution asked isn't changed silently
        throw new IOException("the area of " + format((bounds [2] - bounds [0]) / 100) + " x " + format((bounds [3] - bounds [1]) / 100)
            + " m needs " + (long)columns * rows + " heights every " + format(resolution) + " cm, more than the 12000 of a call: "
            + "\"resolution_cm\":" + (int)Math.ceil(resolution * scale / 10) * 10 + " covers it, or a smaller \"buffer_m\" or \"area\"");
      }
      resolution *= scale;
      columns = (int)Math.ceil((bounds [2] - bounds [0]) / resolution) + 1;
      rows = (int)Math.ceil((bounds [3] - bounds [1]) / resolution) + 1;
      coarser = " (coarser than the usual 300 cm, for 12000 heights at most a call: a smaller \"buffer_m\" keeps 300 cm)";
    }
    float [] heights = new float [columns * rows];
    Arrays.fill(heights, Float.NaN);
    // Asked by groups of points
    int group = 500;
    int missing = 0;
    for (int start = 0; start < heights.length; start += group) {
      StringBuilder points = new StringBuilder();
      int end = Math.min(heights.length, start + group);
      for (int i = start; i < end; i++) {
        double [] place = georeference.toLatitudeLongitude(bounds [0] + (i % columns) * resolution, bounds [1] + (i / columns) * resolution);
        points.append(i > start ? "," : "").append(String.format(Locale.US, "[%.7f,%.7f]", place [1], place [0]));
      }
      String body = "geometry=" + URLEncoder.encode("{\"points\":[" + points + "],\"spatialReference\":{\"wkid\":4326}}", "UTF-8")
          + "&geometryType=esriGeometryMultipoint&returnFirstValueOnly=true&f=json";
      String answer = new String(download(
          "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/getSamples", body), "UTF-8");
      Object parsed;
      try {
        parsed = JSONParser.parse(answer);
      } catch (IllegalArgumentException ex) {
        throw new IOException("the answer of the USGS can't be read");
      }
      if (!(parsed instanceof Map) || !(((Map<?, ?>)parsed).get("samples") instanceof List)) {
        throw new IOException("the USGS gave no height: " + answer.substring(0, Math.min(200, answer.length())));
      }
      for (Object sample : (List<?>)((Map<?, ?>)parsed).get("samples")) {
        if (sample instanceof Map && ((Map<?, ?>)sample).get("locationId") instanceof Number && ((Map<?, ?>)sample).get("value") != null) {
          int index = start + ((Number)((Map<?, ?>)sample).get("locationId")).intValue();
          try {
            // Meters above the sea
            float value = Float.parseFloat(((Map<?, ?>)sample).get("value").toString());
            if (index >= 0 && index < heights.length && value > -1000) {
              heights [index] = value * 100;
            }
          } catch (NumberFormatException ex) {
            // No data there
          }
        }
      }
    }
    float lowest = Float.MAX_VALUE;
    for (float height : heights) {
      if (Float.isNaN(height)) {
        missing++;
      } else {
        lowest = Math.min(lowest, height);
      }
    }
    if (missing > heights.length / 2) {
      throw new IOException("the USGS has no height for most of this area: 3DEP covers the United States");
    }
    // The heights are above the sea: the lowest one is the height 0 unless a pin or an offset says otherwise
    List<Object> values = new ArrayList<Object>(heights.length);
    for (float height : heights) {
      values.add(Float.isNaN(height) ? (Object)Double.valueOf(0) : Double.valueOf(Math.round((height - lowest) * 10) / 10.));
    }
    Map<String, Object> raster = new LinkedHashMap<String, Object>();
    raster.put("x0", Double.valueOf(bounds [0]));
    raster.put("y0", Double.valueOf(bounds [1]));
    raster.put("cell", Double.valueOf(resolution));
    raster.put("cols", Double.valueOf(columns));
    raster.put("rows", Double.valueOf(rows));
    raster.put("heights", values);
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    parameters.put("raster", raster);
    for (String key : new String [] {"pin", "datum_offset_cm", "max_points", "max_error_cm", "name", "level"}) {
      if (command.getParameters().get(key) != null) {
        parameters.put(key, command.getParameters().get(key));
      }
    }
    List<String> terrainNotes = new ArrayList<String>();
    this.executor.applySubCommand(new AssistantCommand("set_terrain", parameters), tally, terrainNotes);
    for (String note : terrainNotes) {
      if (note.startsWith("Couldn't")) {
        throw new IOException(note.substring(note.indexOf('(') + 1, note.lastIndexOf(')')));
      }
      notes.add(note);
    }
    notes.add("The heights come from the USGS 3DEP service: " + columns + " x " + rows + " of them every " + format(resolution)
        + " cm" + coarser + ", over " + format((bounds [2] - bounds [0]) / 100) + " x " + format((bounds [3] - bounds [1]) / 100)
        + " m around (" + format((bounds [0] + bounds [2]) / 2) + ", " + format((bounds [1] + bounds [3]) / 2) + "), "
        + (command.getPoints("area").size() >= 2 ? "the area given" : "the walls of the home and " + format(Math.max(1, Math.min(2000,
            command.getDouble("buffer_m", 60)))) + " m around them") + ". They are heights above the sea, of which the lowest one, " + format(lowest / 100) + " m, was taken for the height 0"
        + (command.getParameters().get("pin") != null ? " before the pin" : ": \"pin\" sets the ground at a height at a place")
        + (missing > 0 ? ". " + missing + " places had no height and were set to 0" : ""));
  }

  /**
   * Returns the address of a tile: {z}, {x} and {y} are its zoom, its column and its row, {bbox} its box
   * in Web Mercator meters (west, south, east, north).
   */
  public static String getTileAddress(String template, int zoom, int column, int row) {
    double half = 20037508.342789244;
    double size = 2 * half / Math.pow(2, zoom);
    return template.replace("{z}", String.valueOf(zoom)).replace("{x}", String.valueOf(column)).replace("{y}", String.valueOf(row))
        .replace("{bbox}", String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", -half + column * size, half - (row + 1) * size,
            -half + (column + 1) * size, half - row * size));
  }

  private void setBasemap(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String source = command.getString("source") != null ? command.getString("source").trim() : "esri_world_imagery";
    String lowerSource = source.toLowerCase(Locale.ENGLISH);
    String template;
    String credit;
    int maxZoom = 21;
    if (lowerSource.equals("esri_world_imagery") || lowerSource.equals("esri")) {
      template = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
      credit = "Esri World Imagery (Esri, Maxar, Earthstar Geographics)";
    } else if (lowerSource.equals("usgs_naip") || lowerSource.equals("naip")) {
      // The image service of the NAIP pictures draws any box; the tiles of USGSImageryOnly stop at the zoom 16
      template = USGS_NAIP_TEMPLATE;
      credit = "USGS National Agriculture Imagery Program (NAIP)";
    } else if (lowerSource.equals("usgs") || lowerSource.equals("usgs_imagery")) {
      template = USGS_IMAGERY_TEMPLATE;
      credit = "USGS The National Map";
      maxZoom = 16;
    } else if (lowerSource.equals("osm") || lowerSource.equals("openstreetmap")) {
      template = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
      credit = "OpenStreetMap contributors";
    } else if (lowerSource.startsWith("xyz:")) {
      template = source.substring(4).trim();
      credit = "the server of the template";
      if (!(template.contains("{z}") && template.contains("{x}") && template.contains("{y}") || template.contains("{bbox}"))
          || !template.toLowerCase(Locale.ENGLISH).startsWith("http")) {
        throw new IOException("an \"xyz:\" source is the address of tiles with {z}, {x} and {y}, or with {bbox} for the box of "
            + "a tile in Web Mercator meters");
      }
    } else {
      throw new IOException("\"source\" is esri_world_imagery, usgs_naip, usgs_imagery, osm, or xyz:<template with {z}, {x}, {y}>. Bing and Google "
          + "need a key and forbid this use; a WMS server isn't read");
    }
    checkWebAccess("set_basemap", new URL(template.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0").replace("{bbox}", "0")).getHost());
    if (this.tileComposer == null) {
      throw new IOException("pictures can't be put together here");
    }
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (georeference == null) {
      throw new IOException("this home has no georeference: set_georeference tells where it is");
    }
    float [] bounds = getPlanBounds(command, 80);
    float centerX = (bounds [0] + bounds [2]) / 2;
    float centerY = (bounds [1] + bounds [3]) / 2;
    // A square around the middle, wide enough once turned
    double halfSize = Math.hypot(bounds [2] - bounds [0], bounds [3] - bounds [1]) / 2 / 100;
    String limits = "";
    if (halfSize * 2 > MAX_BASEMAP_WIDTH) {
      // A background image is 1 km wide at most
      limits = ". The area asked is " + format(halfSize * 2) + " m across: the picture covers the " + format(MAX_BASEMAP_WIDTH)
          + " m around its middle, the widest a background image may be";
      halfSize = MAX_BASEMAP_WIDTH / 2;
    }
    double [] center = georeference.toLatitudeLongitude(centerX, centerY);
    int askedZoom = (int)Math.max(1, Math.min(21, command.getDouble("zoom", 19)));
    int zoom = Math.min(askedZoom, maxZoom);
    double metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(center [0])) / Math.pow(2, zoom);
    while (zoom > 1 && halfSize * 2 / metersPerPixel > 4096) {
      zoom--;
      metersPerPixel *= 2;
    }
    if (zoom < askedZoom) {
      limits += ". The zoom " + zoom + " was used instead of " + askedZoom + (zoom == maxZoom ? ": this source has no tile beyond it"
          : ": a picture is 4096 pixels wide at most, and a smaller \"buffer_m\" allows a finer one");
    }
    double scale = Math.pow(2, zoom) * 256;
    double pixelX = (center [1] + 180) / 360 * scale;
    double sinLatitude = Math.sin(Math.toRadians(center [0]));
    double pixelY = (0.5 - Math.log((1 + sinLatitude) / (1 - sinLatitude)) / (4 * Math.PI)) * scale;
    double halfPixels = halfSize / metersPerPixel;
    int firstColumn = (int)Math.floor((pixelX - halfPixels) / 256);
    int lastColumn = (int)Math.floor((pixelX + halfPixels) / 256);
    int firstRow = (int)Math.floor((pixelY - halfPixels) / 256);
    int lastRow = (int)Math.floor((pixelY + halfPixels) / 256);
    int columns = lastColumn - firstColumn + 1;
    int rows = lastRow - firstRow + 1;
    if (columns * rows > 400) {
      throw new IOException("this area needs " + columns * rows + " tiles: a smaller \"buffer_m\" or \"zoom\"");
    }
    byte [][] tiles = new byte [columns * rows][];
    int missing = 0;
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        try {
          tiles [row * columns + column] = download(getTileAddress(template, zoom, firstColumn + column, firstRow + row), null);
        } catch (IOException ex) {
          missing++;
          if (missing > columns * rows / 2) {
            throw new IOException("the tiles can't be downloaded: " + ex.getMessage());
          }
        }
      }
    }
    int [] crop = {(int)Math.round(pixelX - halfPixels - firstColumn * 256), (int)Math.round(pixelY - halfPixels - firstRow * 256),
                   (int)Math.round(halfPixels * 2), (int)Math.round(halfPixels * 2)};
    byte [] picture = this.tileComposer.compose(tiles, columns, rows, 256, crop, georeference.getRotation());
    int [] size = picture != null ? AssistantGeneratedModel.getImageSize(picture) : null;
    if (size == null) {
      throw new IOException("the tiles downloaded aren't pictures that can be read");
    }
    File file = File.createTempFile("basemap", "." + AssistantGeneratedModel.getImageExtension(picture));
    file.deleteOnExit();
    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
    try {
      out.write(picture);
    } finally {
      out.close();
    }
    // The middle of the picture is the middle of the area
    double width = size [0] * metersPerPixel * 100;
    double height = size [1] * metersPerPixel * 100;
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    parameters.put("image", file.getPath());
    parameters.put("width", Double.valueOf(width));
    parameters.put("x", Double.valueOf(centerX - width / 2));
    parameters.put("y", Double.valueOf(centerY - height / 2));
    List<String> imageNotes = new ArrayList<String>();
    this.executor.applySubCommand(new AssistantCommand("set_background_image", parameters), tally, imageNotes);
    for (String note : imageNotes) {
      if (note.startsWith("Couldn't") || note.startsWith("Ignored")) {
        throw new IOException("the picture of " + format(width / 100) + " m couldn't be set as the background image: " + note);
      }
    }
    notes.add("Displayed behind the plan " + columns * rows + " tiles of " + credit + " at the zoom " + zoom + " ("
        + format(metersPerPixel * 100) + " cm a pixel), " + format(width / 100) + " m wide around (" + format(centerX) + ", "
        + format(centerY) + ")" + (Math.abs(georeference.getRotation()) > 0.01 ? ", turned by " + format(georeference.getRotation())
            + " degrees to the plan" : "") + (missing > 0 ? "; " + missing + " tiles were missing" : "") + limits
        + ". The date of the picture isn't given by this source. remove_background_image takes it away");
  }
}
