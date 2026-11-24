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

  private final Home                     home;
  private final AssistantCommandExecutor executor;
  private boolean                        webAccessAllowed;
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
        + "Each rule chooses features by \"geometry\":\"point|line|polygon\" and / or \"where\":{\"<attribute>\":\"<value>\"} and tells "
        + "what they become with \"as\": for a polygon property_line, polyline, room, slab, patio, planting_bed, building, pond, "
        + "pool or deck; for a line polyline, fence, hedge, path, driveway, sidewalk or curb; for a point tree, shrub, label, "
        + "utility or furniture; skip leaves them out. The other keys of a rule are the ones of the command that builds it, and "
        + "\"name_from\", \"height_from\", \"width_from\", \"species_from\", \"tag_from\" name the attributes to read, the heights and "
        + "the widths in meters unless \"attribute_units\":\"ft|cm\". Without rule a "
        + "polygon and a line are drawn as lines of the plan and a named point as a label. Up to " + MAX_FEATURES + " features a call)\n"
        + "  {\"action\":\"import_buildings\",\"path\":\"<file>\",\"height\":600,\"height_from\":\"HEIGHT\",\"roof\":\"hip\"}  "
        + "(the polygons of a file as neighbor buildings; import_roads draws its lines as "
        + "roads \"width\" cm wide (600, or \"width_from\") of asphalt with their names as labels; import_parcels its polygons as "
        + "property lines with their owner, address or parcel id as labels: \"label_from\":[\"OWNER\",\"SITUS\"], and "
        + "\"subject\":{\"<attribute>\":\"<value>\"} names the lot of the home, the others being drawn as neighbors)\n"
        + "  {\"action\":\"import_osm\",\"path\":\"<a .osm or Overpass JSON file>\"}  "
        + "(OpenStreetMap data: buildings with their levels, roads and footways, trees, fences with their type, hedges, walls and "
        + "water. Without path it asks the Overpass service for what is \"buffer_m\" meters (150) around the home, which needs "
        + "the permission to use web services; \"only\":[\"buildings\",\"roads\",\"paths\",\"trees\",\"fences\",\"water\"] limits it)\n"
        + "  {\"action\":\"import_parcels\",\"url\":\"<ArcGIS REST FeatureServer layer URL>\",\"buffer_m\":150}  "
        + "(asks a county parcel service for the lots around the home; web permission needed)\n"
        + "  {\"action\":\"fetch_elevation\",\"source\":\"usgs_3dep\",\"buffer_m\":60,\"resolution_cm\":300}  "
        + "(asks the USGS 3DEP service for the heights of the ground around the home and builds the terrain with them, as "
        + "set_terrain with \"pin\", \"datum_offset_cm\" and \"max_points\"; \"area\":[[x,y],...] instead of buffer_m; web permission "
        + "needed. Only in the United States)\n"
        + "  {\"action\":\"set_basemap\",\"source\":\"esri_world_imagery|usgs_naip|osm|xyz:<template>\",\"buffer_m\":80,\"zoom\":19}  "
        + "(downloads the tiles of an aerial picture or of a map around the home, turns them to the plan and shows them behind "
        + "it as its background image; web permission needed. An \"xyz:\" template has {z}, {x} and {y}. remove_background_image "
        + "takes it away)\n"
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
            + "USGS (basemap.nationalmap.gov), OpenStreetMap (tile.openstreetmap.org) or the server of an xyz template. Nothing "
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
    Object value = rule != null && rule.get("name_from") != null ? find(properties, rule.get("name_from").toString()) : null;
    for (String key : new String [] {"name", "addr:housenumber", "label", "title"}) {
      if (value == null) {
        value = find(properties, key);
      }
    }
    return value != null && value.toString().trim().length() > 0 ? value.toString().trim() : null;
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
      rule.put("as", path ? "path" : "driveway");
      rule.put("width", Double.valueOf(path ? 150 : highway.equals("service") || highway.equals("track") ? 350
          : highway.equals("residential") || highway.equals("unclassified") || highway.equals("living_street") ? 600
          : highway.equals("tertiary") ? 700 : 900));
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
        String fenceType = tags.get("fence_type") != null ? tags.get("fence_type").toString() : "";
        rule.put("style", barrier.equals("wall") ? "brick_wall" : barrier.equals("retaining_wall") ? "retaining_wall"
            : barrier.equals("guard_rail") ? "guardrail" : barrier.equals("bollard") ? "bollards"
            : fenceType.equals("chain_link") ? "chain_link" : fenceType.equals("wood") ? "privacy_solid"
            : fenceType.equals("split_rail") ? "split_rail" : fenceType.equals("wire") ? "field_fence"
            : fenceType.equals("electric") ? "electric" : fenceType.equals("barbed_wire") ? "barbed_wire"
            : fenceType.equals("pole") ? "post_and_rail" : fenceType.equals("railing") || fenceType.equals("bars")
                || fenceType.equals("metal") ? "ornamental_steel" : fenceType.equals("panel") ? "privacy_solid" : "post_and_rail");
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
    if (rule.get("where") instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)rule.get("where")).entrySet()) {
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
    if (osm && file == null) {
      features = readOsm(fetchOsm(command));
      source = "the Overpass service of OpenStreetMap";
    } else if ("import_parcels".equals(action) && command.getString("url") != null) {
      features = readGeoJson(fetchParcels(command));
      source = "the parcel service";
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
              "label_from", "max_features", "attribute_units").contains(entry.getKey())) {
        defaultRule.put(entry.getKey(), entry.getValue());
      }
    }
    if ("import_buildings".equals(action)) {
      defaultRule.put("geometry", "polygon");
      defaultRule.put("as", "building");
    } else if ("import_roads".equals(action)) {
      defaultRule.put("geometry", "line");
      defaultRule.put("as", "driveway");
      if (defaultRule.get("width") == null) {
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
    List<String> only = command.getStringList("only");
    // The heights and the widths that attributes give are in meters unless told otherwise
    float heightScale = 100;
    if (command.getString("attribute_units") != null) {
      String units = command.getString("attribute_units").toLowerCase(Locale.ENGLISH);
      heightScale = units.startsWith("cm") ? 1 : units.startsWith("f") ? 30.48f : 100;
    }
    int limit = (int)Math.max(1, Math.min(MAX_FEATURES, command.getDouble("max_features", MAX_FEATURES)));
    Map<String, Integer> counts = new TreeMap<String, Integer>();
    List<String> failures = new ArrayList<String>();
    int skipped = 0;
    int built = 0;
    int far = 0;
    this.executor.beginTurnUpdate();
    for (Feature feature : features) {
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
      List<float []> points = new ArrayList<float []>();
      boolean farAway = false;
      for (double [] point : feature.points) {
        float [] plan = transform.toPlan(point [0], point [1]);
        farAway |= !AssistantCommandExecutor.coordinateSane(plan [0]) || !AssistantCommandExecutor.coordinateSane(plan [1]);
        if (points.isEmpty() || Math.hypot(plan [0] - points.get(points.size() - 1) [0], plan [1] - points.get(points.size() - 1) [1]) > 0.5) {
          points.add(plan);
        }
      }
      if (farAway) {
        far++;
        continue;
      }
      if (feature.type.equals("polygon") && points.size() > 3
          && Math.hypot(points.get(0) [0] - points.get(points.size() - 1) [0], points.get(0) [1] - points.get(points.size() - 1) [1]) < 0.5) {
        points.remove(points.size() - 1);
      }
      String as = rule != null && rule.get("as") != null ? rule.get("as").toString().toLowerCase(Locale.ENGLISH)
          : feature.type.equals("point") ? "label" : "polyline";
      if (as.equals("skip") || as.equals("none")) {
        skipped++;
        continue;
      }
      try {
        if (build(feature, as, rule, points, heightScale, command, tally)) {
          counts.put(as, Integer.valueOf(counts.containsKey(as) ? counts.get(as).intValue() + 1 : 1));
          built++;
        } else {
          skipped++;
        }
      } catch (IOException ex) {
        if (failures.size() < 5) {
          String name = getName(feature.properties, rule);
          failures.add((name != null ? "\"" + name + "\": " : "") + ex.getMessage());
        }
        skipped++;
      }
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
      first = false;
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
   * Builds what a feature becomes and returns <code>false</code> if it has nothing to draw.
   */
  private boolean build(Feature feature, String as, Map<?, ?> rule, List<float []> points, float heightScale,
                        AssistantCommand command, int [] tally) throws IOException {
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    if (rule != null) {
      for (Map.Entry<?, ?> entry : rule.entrySet()) {
        String key = String.valueOf(entry.getKey());
        if (!Arrays.asList("where", "geometry", "as", "name_from", "height_from", "width_from", "species_from", "tag_from", "label")
                .contains(key)) {
          parameters.put(key, entry.getValue());
        }
      }
    }
    String name = getName(feature.properties, rule);
    if (name != null && parameters.get("name") == null && !as.equals("building")) {
      parameters.put("name", name.length() > 60 ? name.substring(0, 60) : name);
    }
    List<Object> tags = new ArrayList<Object>();
    if (parameters.get("tags") instanceof List) {
      tags.addAll((List<?>)parameters.get("tags"));
    }
    tags.add("imported");
    if (rule != null && rule.get("tag_from") != null && find(feature.properties, rule.get("tag_from").toString()) != null) {
      tags.add(HomeItemTags.cleanTag(find(feature.properties, rule.get("tag_from").toString()).toString()));
    }
    for (String [] attribute : new String [][] {{"height_from", "height"}, {"width_from", "width"}}) {
      if (rule != null && rule.get(attribute [0]) != null) {
        Object value = find(feature.properties, rule.get(attribute [0]).toString());
        if (value != null) {
          try {
            parameters.put(attribute [1], Double.valueOf(Double.parseDouble(value.toString().replaceAll("[^0-9.\\-]", "")) * heightScale));
          } catch (NumberFormatException ex) {
            // The default one
          }
        }
      }
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
        if (rule != null && rule.get("species_from") != null && find(feature.properties, rule.get("species_from").toString()) != null) {
          String species = find(feature.properties, rule.get("species_from").toString()).toString();
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
          return false;
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
        return false;
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
        if (rule != null && Boolean.TRUE.equals(rule.get("label")) && name != null) {
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
        // What names a parcel: its owner, its address, its number
        List<String> labelNames = command.getStringList("label_from");
        if (labelNames.isEmpty()) {
          labelNames = Arrays.asList("OWNER", "OWNER_NAME", "OWNERNME1", "SITUS", "SITEADDRESS", "ADDRESS", "PARCEL_ID", "PARCELID",
              "PIN", "APN");
        }
        StringBuilder label = new StringBuilder();
        int parts = 0;
        for (String labelName : labelNames) {
          Object value = find(feature.properties, labelName);
          if (value != null && value.toString().trim().length() > 0 && parts < 2) {
            label.append(label.length() > 0 ? " - " : "").append(value.toString().trim());
            parts++;
          }
        }
        boolean subject = true;
        if (command.getParameters().get("subject") instanceof Map) {
          for (Map.Entry<?, ?> entry : ((Map<?, ?>)command.getParameters().get("subject")).entrySet()) {
            Object value = find(feature.properties, String.valueOf(entry.getKey()));
            subject &= value != null && value.toString().equalsIgnoreCase(String.valueOf(entry.getValue()));
          }
        }
        if (!subject) {
          // A neighbor: a line of the plan, lighter
          action = "add_polyline";
          parameters.put("closed", Boolean.TRUE);
          parameters.put("dash", "dashed");
          parameters.put("color", "#8A8A8A");
          parameters.put("thickness", Double.valueOf(2));
        }
        String acres = format(getPolygonArea(points) / 10000 / 4046.86);
        parameters.put("name", (label.length() > 0 ? label.toString() : subject ? "Property line" : "Neighbor lot") + " (" + acres + " ac)");
        if (label.length() > 0) {
          parameters.put("label", label + " (" + acres + " ac)");
        }
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
    List<String> notes = new ArrayList<String>();
    this.executor.applySubCommand(new AssistantCommand(action, parameters), tally, notes);
    for (String note : notes) {
      if (note.startsWith("Couldn't")) {
        throw new IOException(note.substring(note.indexOf('(') + 1, note.lastIndexOf(')')));
      }
    }
    return true;
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
   * Returns the box of the plan a command asks for: its area, or the home with a margin.
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
      for (Wall wall : this.home.getWalls()) {
        shapes.add(wall.getPoints());
      }
      for (Room room : this.home.getRooms()) {
        shapes.add(room.getPoints());
      }
      for (Polyline polyline : this.home.getPolylines()) {
        if ("property".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))) {
          shapes.add(polyline.getPoints());
        }
      }
      if (shapes.isEmpty()) {
        HomeGeoreference georeference = HomeGeoreference.get(this.home);
        shapes.add(new float [][] {{georeference != null ? (float)georeference.getX() : 0, georeference != null ? (float)georeference.getY() : 0}});
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
    if ((long)columns * rows > 12000) {
      double scale = Math.sqrt((double)columns * rows / 12000);
      resolution *= scale;
      columns = (int)Math.ceil((bounds [2] - bounds [0]) / resolution) + 1;
      rows = (int)Math.ceil((bounds [3] - bounds [1]) / resolution) + 1;
      notes.add("The heights are asked every " + format(resolution) + " cm, for 12000 points at most");
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
        + " cm. They are heights above the sea, of which the lowest one, " + format(lowest / 100) + " m, was taken for the height 0"
        + (command.getParameters().get("pin") != null ? " before the pin" : ": \"pin\" sets the ground at a height at a place")
        + (missing > 0 ? ". " + missing + " places had no height and were set to 0" : ""));
  }

  private void setBasemap(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String source = command.getString("source") != null ? command.getString("source").trim() : "esri_world_imagery";
    String lowerSource = source.toLowerCase(Locale.ENGLISH);
    String template;
    String credit;
    if (lowerSource.equals("esri_world_imagery") || lowerSource.equals("esri")) {
      template = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
      credit = "Esri World Imagery (Esri, Maxar, Earthstar Geographics)";
    } else if (lowerSource.equals("usgs_naip") || lowerSource.equals("usgs")) {
      template = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}";
      credit = "USGS The National Map";
    } else if (lowerSource.equals("osm") || lowerSource.equals("openstreetmap")) {
      template = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
      credit = "OpenStreetMap contributors";
    } else if (lowerSource.startsWith("xyz:")) {
      template = source.substring(4).trim();
      credit = "the server of the template";
      if (!template.contains("{z}") || !template.contains("{x}") || !template.contains("{y}") || !template.toLowerCase(Locale.ENGLISH).startsWith("http")) {
        throw new IOException("an \"xyz:\" source is the address of tiles with {z}, {x} and {y}");
      }
    } else {
      throw new IOException("\"source\" is esri_world_imagery, usgs_naip, osm, or xyz:<template with {z}, {x}, {y}>. Bing and Google "
          + "need a key and forbid this use; a WMS server isn't read");
    }
    checkWebAccess("set_basemap", new URL(template.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0")).getHost());
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
    double [] center = georeference.toLatitudeLongitude(centerX, centerY);
    int zoom = (int)Math.max(1, Math.min(21, command.getDouble("zoom", 19)));
    double metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(center [0])) / Math.pow(2, zoom);
    while (zoom > 1 && halfSize * 2 / metersPerPixel > 4096) {
      zoom--;
      metersPerPixel *= 2;
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
          tiles [row * columns + column] = download(template.replace("{z}", String.valueOf(zoom))
              .replace("{x}", String.valueOf(firstColumn + column)).replace("{y}", String.valueOf(firstRow + row)), null);
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
        throw new IOException(note);
      }
    }
    notes.add("Displayed behind the plan " + columns * rows + " tiles of " + credit + " at the zoom " + zoom + " ("
        + format(metersPerPixel * 100) + " cm a pixel), " + format(width / 100) + " m wide around (" + format(centerX) + ", "
        + format(centerY) + ")" + (Math.abs(georeference.getRotation()) > 0.01 ? ", turned by " + format(georeference.getRotation())
            + " degrees to the plan" : "") + (missing > 0 ? "; " + missing + " tiles were missing" : "")
        + ". The date of the picture isn't given by this source. remove_background_image takes it away");
  }
}
