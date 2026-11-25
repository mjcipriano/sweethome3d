/*
 * AssistantModelImportTest.java 25 nov. 2026
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the import of glTF, GLB and OBJ models: their size, their triangles, their materials,
 * the helper shapes left out, where the piece stands, and the preview of a model.
 * @author Sweet Home 3D contributors
 */
public class AssistantModelImportTest {
  private String                   previousNo3D;
  private Locale                   previousLocale;
  private File                     folder;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.UK);
    this.folder = File.createTempFile("model-import", "");
    this.folder.delete();
    assertTrue(this.folder.mkdir());
    this.home = new Home(250);
    this.preferences = new DefaultUserPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
  }

  @After
  public void tearDown() {
    File [] files = this.folder.listFiles();
    if (files != null) {
      for (File file : files) {
        file.delete();
      }
    }
    this.folder.delete();
    Locale.setDefault(this.previousLocale);
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private String path(File file) {
    return file.getPath().replace("\\", "\\\\");
  }

  private HomePieceOfFurniture getPiece(String name) {
    HomePieceOfFurniture found = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (name.equals(piece.getName())) {
        found = piece;
      }
    }
    return found;
  }

  // ---- The model of the tests ----

  private static void write(File file, byte [] data) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  private static byte [] createImage() throws IOException {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 4; x++) {
      for (int y = 0; y < 4; y++) {
        image.setRGB(x, y, (x + y) % 2 == 0 ? 0x2A5A6B : 0x3A7A8B);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  /**
   * The binary data of the model: a cube of 1 m with its texture coordinates, a square
   * of 1 m in the plane of the ground and a sphere of 1 m of radius, then an image.
   * The offsets and the lengths of its parts are stored in <code>views</code>.
   */
  private static byte [] createBuffer(List<int []> views, byte [] image) {
    ByteBuffer buffer = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
    // Cube: positions, texture coordinates, indices
    float [][] corners = {{-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
                          {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}};
    int start = buffer.position();
    for (float [] corner : corners) {
      buffer.putFloat(corner [0]).putFloat(corner [1]).putFloat(corner [2]);
    }
    views.add(new int [] {start, buffer.position() - start});
    start = buffer.position();
    for (float [] corner : corners) {
      buffer.putFloat(corner [0] + 0.5f).putFloat(corner [1] + 0.5f);
    }
    views.add(new int [] {start, buffer.position() - start});
    start = buffer.position();
    int [] cubeIndices = {0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2, 0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5};
    for (int index : cubeIndices) {
      buffer.putShort((short)index);
    }
    views.add(new int [] {start, buffer.position() - start});
    // Square, drawn as a strip without indices
    start = buffer.position();
    for (float [] corner : new float [][] {{-0.5f, 0, -0.5f}, {-0.5f, 0, 0.5f}, {0.5f, 0, -0.5f}, {0.5f, 0, 0.5f}}) {
      buffer.putFloat(corner [0]).putFloat(corner [1]).putFloat(corner [2]);
    }
    views.add(new int [] {start, buffer.position() - start});
    // Sphere of 8 meridians and 6 parallels
    start = buffer.position();
    int meridians = 8;
    int parallels = 6;
    for (int i = 0; i <= parallels; i++) {
      double latitude = Math.PI * i / parallels - Math.PI / 2;
      for (int j = 0; j < meridians; j++) {
        double longitude = 2 * Math.PI * j / meridians;
        buffer.putFloat((float)(Math.cos(latitude) * Math.cos(longitude))).putFloat((float)Math.sin(latitude))
            .putFloat((float)(Math.cos(latitude) * Math.sin(longitude)));
      }
    }
    views.add(new int [] {start, buffer.position() - start, (parallels + 1) * meridians});
    start = buffer.position();
    int count = 0;
    for (int i = 0; i < parallels; i++) {
      for (int j = 0; j < meridians; j++) {
        int a = i * meridians + j;
        int b = i * meridians + (j + 1) % meridians;
        int c = a + meridians;
        int d = b + meridians;
        buffer.putInt(a).putInt(c).putInt(b).putInt(b).putInt(c).putInt(d);
        count += 6;
      }
    }
    views.add(new int [] {start, buffer.position() - start, count});
    start = buffer.position();
    buffer.put(image);
    views.add(new int [] {start, image.length});
    while (buffer.position() % 4 != 0) {
      buffer.put((byte)0);
    }
    byte [] data = new byte [buffer.position()];
    buffer.flip();
    buffer.get(data);
    return data;
  }

  /**
   * Returns the description of a model of a vehicle 2 m wide, 4 m long and 1.2 m high: a body
   * with an image, a red trim turned and scaled on its roof, and two helpers, a ground of 10 m
   * without material under it and a sphere of 5 m of radius around it.
   * @param bufferUri the address of the binary data, or <code>null</code> in a GLB file
   * @param imageUri  the address of the image, or <code>null</code> if it's in the binary data
   */
  private static String createGltf(List<int []> views, int bufferLength, String bufferUri, String imageUri) {
    double sine = Math.sin(Math.PI / 4);
    return "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]}],"
        + "\"nodes\":["
        + "{\"name\":\"car\",\"translation\":[10,0,0],\"children\":[1,2,3,4]},"
        + "{\"name\":\"body\",\"mesh\":0,\"translation\":[0,0.5,0],\"scale\":[2,1,4]},"
        + "{\"name\":\"trim\",\"mesh\":1,\"translation\":[0,1.1,0],\"rotation\":[0," + sine + ",0," + sine + "],\"scale\":[1,0.2,0.5]},"
        + "{\"name\":\"ground\",\"mesh\":2,\"matrix\":[10,0,0,0, 0,1,0,0, 0,0,10,0, 0,-0.01,0,1]},"
        + "{\"name\":\"probe\",\"mesh\":3,\"translation\":[0,0.5,0],\"scale\":[5,5,5]}],"
        + "\"meshes\":["
        + "{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"TEXCOORD_0\":1},\"indices\":2,\"material\":0}]},"
        + "{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":2,\"material\":1}]},"
        + "{\"primitives\":[{\"attributes\":{\"POSITION\":3},\"mode\":5}]},"
        + "{\"primitives\":[{\"attributes\":{\"POSITION\":4},\"indices\":5,\"material\":2}]}],"
        + "\"materials\":["
        + "{\"name\":\"carpaint\",\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0},\"metallicFactor\":0.8,\"roughnessFactor\":0.2}},"
        + "{\"name\":\"Trim Red\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,0,0,1],\"metallicFactor\":0,\"roughnessFactor\":1}},"
        + "{\"name\":\"probe\",\"alphaMode\":\"BLEND\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,1,1,0.25]}}],"
        + "\"textures\":[{\"source\":0}],"
        + "\"images\":[" + (imageUri != null ? "{\"uri\":\"" + imageUri + "\"}" : "{\"bufferView\":6,\"mimeType\":\"image/png\"}") + "],"
        + "\"accessors\":["
        + "{\"bufferView\":0,\"componentType\":5126,\"count\":8,\"type\":\"VEC3\"},"
        + "{\"bufferView\":1,\"componentType\":5126,\"count\":8,\"type\":\"VEC2\"},"
        + "{\"bufferView\":2,\"componentType\":5123,\"count\":36,\"type\":\"SCALAR\"},"
        + "{\"bufferView\":3,\"componentType\":5126,\"count\":4,\"type\":\"VEC3\"},"
        + "{\"bufferView\":4,\"componentType\":5126,\"count\":" + views.get(4) [2] + ",\"type\":\"VEC3\"},"
        + "{\"bufferView\":5,\"componentType\":5125,\"count\":" + views.get(5) [2] + ",\"type\":\"SCALAR\"}],"
        + "\"bufferViews\":[" + createViews(views) + "],"
        + "\"buffers\":[{\"byteLength\":" + bufferLength + (bufferUri != null ? ",\"uri\":\"" + bufferUri + "\"" : "") + "}]}";
  }

  private static String createViews(List<int []> views) {
    StringBuilder text = new StringBuilder();
    for (int [] view : views) {
      text.append(text.length() > 0 ? "," : "").append("{\"buffer\":0,\"byteOffset\":").append(view [0])
          .append(",\"byteLength\":").append(view [1]).append("}");
    }
    return text.toString();
  }

  private File createGlbFile(String name) throws IOException {
    List<int []> views = new ArrayList<int []>();
    byte [] binary = createBuffer(views, createImage());
    byte [] json = createGltf(views, binary.length, null, null).getBytes("UTF-8");
    int jsonLength = (json.length + 3) / 4 * 4;
    ByteBuffer glb = ByteBuffer.allocate(12 + 8 + jsonLength + 8 + binary.length).order(ByteOrder.LITTLE_ENDIAN);
    glb.putInt(0x46546C67).putInt(2).putInt(glb.capacity());
    glb.putInt(jsonLength).putInt(0x4E4F534A).put(json);
    for (int i = json.length; i < jsonLength; i++) {
      glb.put((byte)' ');
    }
    glb.putInt(binary.length).putInt(0x004E4942).put(binary);
    File file = new File(this.folder, name);
    write(file, glb.array());
    return file;
  }

  /**
   * Returns the text of a file stored beside the model of <code>piece</code> in its archive.
   */
  private static String readModelFile(HomePieceOfFurniture piece, String name) throws IOException {
    URL url = ((URLContent)piece.getModel()).getURL();
    assertTrue(url.toString(), url.toString().endsWith("!/model.obj"));
    InputStream in = new URL(url.toString().replace("!/model.obj", "!/" + name)).openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      for (int count; (count = in.read(buffer)) > 0; ) {
        out.write(buffer, 0, count);
      }
      return out.toString("ISO-8859-1");
    } finally {
      in.close();
    }
  }

  private static int count(String text, String regex) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  /**
   * Returns the lower x, y, z and the upper x, y, z of the vertices of an OBJ text.
   */
  private static float [] getBounds(String obj) {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^v (\\S+) (\\S+) (\\S+)$").matcher(obj);
    while (matcher.find()) {
      for (int i = 0; i < 3; i++) {
        float value = Float.parseFloat(matcher.group(i + 1));
        bounds [i] = Math.min(bounds [i], value);
        bounds [i + 3] = Math.max(bounds [i + 3], value);
      }
    }
    return bounds;
  }

  // ---- Tests ----

  /**
   * A GLB file gives a piece at its real size, without its helpers, with its materials.
   */
  @Test
  public void testGlbImport() throws Exception {
    File file = createGlbFile("car.glb");
    String summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"x\":100,\"y\":200,"
        + "\"source\":\"https://example.org/models/car\",\"author\":\"A. Maker\",\"license\":\"CC BY 4.0\"}");
    HomePieceOfFurniture car = getPiece("car");
    assertNotNull(summary, car);
    // Meters of the file, as centimeters
    assertEquals(200, car.getWidth(), 0.1f);
    assertEquals(400, car.getDepth(), 0.1f);
    assertEquals(120, car.getHeight(), 0.1f);
    assertTrue(summary, summary.contains("200 x 400 x 120 cm"));
    assertTrue(summary, summary.contains("24 triangles"));
    // The ground and the sphere around the model are left out, and named
    assertTrue(summary, summary.contains("\"ground\" (a flat plane of 1000 cm x 1000 cm under the model, 2 triangles)"));
    assertTrue(summary, summary.contains("\"probe\" (a sphere of 1000 cm around the model"));
    assertTrue(summary, summary.contains("keep_helpers"));
    // Named as list_materials and set_material name them
    assertTrue(summary, summary.contains("materials [carpaint, Trim_Red]"));
    assertFalse(summary, summary.contains("WARNING"));

    String obj = readModelFile(car, "model.obj");
    assertEquals(24, count(obj, "(?m)^f "));
    assertEquals(16, count(obj, "(?m)^v "));
    // The image of the body goes with its texture coordinates, the trim has none
    assertEquals(8, count(obj, "(?m)^vt "));
    float [] bounds = getBounds(obj);
    assertEquals(200, bounds [3] - bounds [0], 0.1f);
    assertEquals(120, bounds [4] - bounds [1], 0.1f);
    assertEquals(400, bounds [5] - bounds [2], 0.1f);
    // The node of the trim is turned by a quarter of turn: 100 cm along z and 50 cm along x
    java.util.regex.Matcher trim = java.util.regex.Pattern.compile("(?s)g trim_2\\n(.*)$").matcher(obj);
    assertTrue(trim.find());
    float [] trimBounds = getBounds(trim.group(1));
    assertEquals(50, trimBounds [3] - trimBounds [0], 0.1f);
    assertEquals(20, trimBounds [4] - trimBounds [1], 0.1f);
    assertEquals(100, trimBounds [5] - trimBounds [2], 0.1f);
    assertEquals(100, trimBounds [1] - bounds [1], 0.1f);

    String mtl = readModelFile(car, "model.mtl");
    assertTrue(mtl, mtl.contains("newmtl carpaint"));
    assertTrue(mtl, mtl.contains("map_Kd texture_1.png"));
    assertTrue(mtl, mtl.contains("newmtl Trim_Red"));
    assertTrue(mtl, mtl.contains("Kd 1.0 0.0 0.0"));
    // A smooth metal shines, a rough material doesn't
    assertEquals(1, count(mtl, "(?m)^Ns "));
    assertTrue(readModelFile(car, "texture_1.png").startsWith("\u0089PNG"));

    // Where the model comes from stays with the piece
    assertEquals("https://example.org/models/car", car.getProperty("com.eteks.sweethome3d.modelSource"));
    assertEquals("A. Maker", car.getProperty("com.eteks.sweethome3d.modelAuthor"));
    assertEquals("CC BY 4.0", car.getProperty("com.eteks.sweethome3d.modelLicense"));
    String description = run("{\"action\":\"describe_item\",\"id\":\"F1\"}");
    assertTrue(description, description.contains("model source https://example.org/models/car, author A. Maker, license CC BY 4.0"));

    // One undoable step
    this.homeController.undo();
    assertTrue(this.home.getFurniture().isEmpty());
  }

  /**
   * The helpers are kept when asked, and the options scale, turn and recolor the model.
   */
  @Test
  public void testOptions() throws Exception {
    File file = createGlbFile("car.glb");
    String summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"All\",\"keep_helpers\":true}");
    HomePieceOfFurniture all = getPiece("All");
    assertNotNull(summary, all);
    assertEquals(1000, all.getWidth(), 0.5f);
    assertEquals(1000, all.getHeight(), 0.5f);
    assertFalse(summary, summary.contains("left out"));
    String mtl = readModelFile(all, "model.mtl");
    assertTrue(mtl, mtl.contains("newmtl no_material"));
    // The sphere is transparent
    assertTrue(mtl, mtl.contains("d 0.25"));

    // The longest side at 450 cm, the right side of the model as the front of the piece
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Turned\",\"length\":450,"
        + "\"front\":\"+x\",\"materials\":{\"carpaint\":\"#285A6B\",\"chrome\":\"#FFFFFF\",\"trim red\":\"#101010\"}}");
    HomePieceOfFurniture turned = getPiece("Turned");
    assertNotNull(summary, turned);
    assertEquals(450, turned.getWidth(), 0.5f);
    assertEquals(225, turned.getDepth(), 0.5f);
    assertEquals(135, turned.getHeight(), 0.5f);
    assertTrue(summary, summary.contains("its side +x was turned to the front"));
    assertTrue(summary, summary.contains("no material is named [chrome]"));
    mtl = readModelFile(turned, "model.mtl");
    // The color replaces the image of the material
    assertFalse(mtl, mtl.contains("map_Kd"));
    assertTrue(mtl, mtl.contains("Kd 0.1568"));
    assertTrue(mtl, mtl.contains("Kd 0.0627"));
    assertTrue(turned.getProperty("com.eteks.sweethome3d.modelImportMaterials").contains("#285A6B"));
    assertTrue(run("{\"action\":\"describe_item\",\"id\":\"F2\"}").contains("materials recolored when it was imported"));
    // The x of the model became the z of the piece
    String obj = readModelFile(turned, "model.obj");
    float [] bounds = getBounds(obj);
    assertEquals(400, bounds [3] - bounds [0], 0.5f);
    assertEquals(200, bounds [5] - bounds [2], 0.5f);
    // The model stood 10 m along x in its file
    assertEquals(2000, bounds [5] + bounds [2], 1);

    // Unknown keys and wrong values are told
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Other\",\"frnt\":\"+x\"}");
    assertTrue(summary, summary.contains("frnt"));
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Wrong\",\"front\":\"up\"}");
    assertNull(summary, getPiece("Wrong"));
    assertTrue(summary, summary.contains("\"front\" is +x, -x, +y or -y"));
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Wrong\",\"materials\":{\"carpaint\":\"blue-ish\"}}");
    assertNull(summary, getPiece("Wrong"));
    // A budget of triangles
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Heavy\",\"triangle_budget\":20}");
    assertTrue(summary, summary.contains("WARNING: that is more than 20 triangles"));
  }

  /**
   * A glTF file is read with the files beside it or with the data it embeds.
   */
  @Test
  public void testGltfFiles() throws Exception {
    List<int []> views = new ArrayList<int []>();
    byte [] image = createImage();
    byte [] binary = createBuffer(views, image);
    write(new File(this.folder, "car data.bin"), binary);
    write(new File(this.folder, "paint.png"), image);
    File file = new File(this.folder, "car.gltf");
    write(file, createGltf(views, binary.length, "car%20data.bin", "paint.png").getBytes("UTF-8"));
    String summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Beside\"}");
    HomePieceOfFurniture piece = getPiece("Beside");
    assertNotNull(summary, piece);
    assertEquals(400, piece.getDepth(), 0.1f);
    assertTrue(readModelFile(piece, "model.mtl").contains("map_Kd texture_1.png"));

    File embedded = new File(this.folder, "embedded.gltf");
    write(embedded, createGltf(views, binary.length,
        "data:application/octet-stream;base64," + java.util.Base64.getEncoder().encodeToString(binary),
        "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(image)).getBytes("UTF-8"));
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(embedded) + "\"}");
    piece = getPiece("embedded");
    assertNotNull(summary, piece);
    assertEquals(200, piece.getWidth(), 0.1f);
    assertEquals(24, count(readModelFile(piece, "model.obj"), "(?m)^f "));

    // A file out of the folder of the model isn't read, nor a compressed model
    File outside = new File(this.folder, "outside.gltf");
    write(outside, createGltf(views, binary.length, "../car%20data.bin", "paint.png").getBytes("UTF-8"));
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(outside) + "\"}");
    assertNull(summary, getPiece("outside"));
    assertTrue(summary, summary.contains("isn't beside the model"));
    File compressed = new File(this.folder, "compressed.gltf");
    write(compressed, createGltf(views, binary.length, "car%20data.bin", "paint.png")
        .replace("{\"asset\"", "{\"extensionsRequired\":[\"KHR_draco_mesh_compression\"],\"asset\"").getBytes("UTF-8"));
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(compressed) + "\"}");
    assertNull(summary, getPiece("compressed"));
    assertTrue(summary, summary.contains("KHR_draco_mesh_compression"));
    File broken = new File(this.folder, "broken.glb");
    write(broken, new byte [] {'g', 'l', 'T', 'F', 2, 0, 0, 0, 40, 0, 0, 0});
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(broken) + "\"}");
    assertNull(summary, getPiece("broken"));
  }

  /**
   * A model is simplified to a count of triangles, in a GLB file as in an OBJ file.
   */
  @Test
  public void testSimplification() throws Exception {
    // A sphere of 60 cm of radius with 80 meridians and 60 parallels
    StringBuilder obj = new StringBuilder("mtllib ball.mtl\nusemtl rubber\n");
    int meridians = 80;
    int parallels = 60;
    for (int i = 0; i <= parallels; i++) {
      double latitude = Math.PI * i / parallels - Math.PI / 2;
      for (int j = 0; j < meridians; j++) {
        double longitude = 2 * Math.PI * j / meridians;
        obj.append("v ").append((float)(60 * Math.cos(latitude) * Math.cos(longitude))).append(' ')
            .append((float)(60 * Math.sin(latitude))).append(' ')
            .append((float)(60 * Math.cos(latitude) * Math.sin(longitude))).append('\n');
      }
    }
    for (int i = 0; i < parallels; i++) {
      for (int j = 0; j < meridians; j++) {
        int a = i * meridians + j + 1;
        int b = i * meridians + (j + 1) % meridians + 1;
        obj.append("f ").append(a).append(' ').append(a + meridians).append(' ').append(b + meridians).append(' ').append(b).append('\n');
      }
    }
    File file = new File(this.folder, "ball.obj");
    write(file, obj.toString().getBytes("ISO-8859-1"));
    write(new File(this.folder, "ball.mtl"), "newmtl rubber\nKd 0.2 0.4 0.6\n".getBytes("ISO-8859-1"));
    String summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Ball\","
        + "\"target_triangles\":1000,\"width\":120}");
    HomePieceOfFurniture ball = getPiece("Ball");
    assertNotNull(summary, ball);
    assertTrue(summary, summary.contains("simplified from 9600 to "));
    String simplified = readModelFile(ball, "model.obj");
    int triangles = count(simplified, "(?m)^f ");
    assertTrue(triangles + " triangles", triangles <= 1000 && triangles >= 300);
    // The same outline, to a few centimeters
    float [] bounds = getBounds(simplified);
    for (int i = 0; i < 3; i++) {
      assertEquals(120, bounds [i + 3] - bounds [i], 8);
    }
    assertEquals(120, ball.getWidth(), 0.01f);
    assertTrue(readModelFile(ball, "model.mtl").contains("Kd 0.2 0.4 0.6"));

    // More triangles than asked: nothing changes
    File glb = createGlbFile("car.glb");
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(glb) + "\",\"target_triangles\":5000}");
    assertTrue(summary, summary.contains("24 triangles"));
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(glb) + "\",\"name\":\"Rough\",\"target_triangles\":14}");
    assertTrue(summary, summary.contains("simplified from"));
    assertTrue(count(readModelFile(getPiece("Rough"), "model.obj"), "(?m)^f ") <= 14);
  }

  /**
   * The piece goes on the level of the room it is in, or on the ground outdoors.
   */
  @Test
  public void testPlacement() throws Exception {
    File file = createGlbFile("car.glb");
    Level ground = new Level("Ground", 0, 12, 250);
    Level upper = new Level("Upper", 262, 12, 250);
    this.home.addLevel(ground);
    this.home.addLevel(upper);
    this.home.setSelectedLevel(ground);
    Room groundRoom = new Room(new float [][] {{0, 0}, {600, 0}, {600, 600}, {0, 600}});
    this.home.addRoom(groundRoom);
    groundRoom.setLevel(ground);
    Room upperRoom = new Room(new float [][] {{1000, 0}, {1600, 0}, {1600, 600}, {1000, 600}});
    this.home.addRoom(upperRoom);
    upperRoom.setLevel(upper);
    String summary = run("{\"action\":\"set_terrain\",\"points\":[[2000,0,-40],[4000,0,-40],[4000,2000,-40],[2000,2000,-40]]}");
    this.home.setSelectedLevel(upper);

    // In a room of the ground floor while the upper level is selected
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Indoors\",\"x\":300,\"y\":300}");
    HomePieceOfFurniture piece = getPiece("Indoors");
    assertNotNull(summary, piece);
    assertEquals(ground, piece.getLevel());
    assertEquals(0, piece.getElevation(), 0.01f);
    assertTrue(summary, summary.contains("it stands on the floor of the level \"Ground\""));
    assertEquals("The selected level doesn't change", upper, this.home.getSelectedLevel());
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Upstairs\",\"x\":1300,\"y\":300}");
    assertEquals(upper, getPiece("Upstairs").getLevel());

    // On the driveway: on the level the closest to the ground, at the height of the ground
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Outdoors\",\"x\":3000,\"y\":1000}");
    piece = getPiece("Outdoors");
    assertNotNull(summary, piece);
    assertEquals(summary, ground, piece.getLevel());
    assertEquals(-40, piece.getElevation(), 0.5f);
    assertTrue(summary, summary.contains("it stands on the ground"));
    // An elevation or a level given is kept
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Given\",\"x\":3000,\"y\":1000,"
        + "\"level\":\"Upper\",\"elevation\":15}");
    piece = getPiece("Given");
    assertEquals(upper, piece.getLevel());
    assertEquals(15, piece.getElevation(), 0.01f);
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + path(file) + "\",\"name\":\"Nowhere\",\"level\":\"Attic\"}");
    assertNull(summary, getPiece("Nowhere"));
    assertTrue(summary, summary.contains("there is no level \"Attic\""));
  }

  /**
   * A model is drawn alone, in a home which isn't the one of the user.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testPreviewModel() throws Exception {
    File file = createGlbFile("car.glb");
    HomeMCPHandler handler = new HomeMCPHandler(this.home, this.preferences, this.homeController, "test");
    final List<Home> drawnHomes = new ArrayList<Home>();
    final List<HomeMCPHandler.Image3DRequest> requests = new ArrayList<HomeMCPHandler.Image3DRequest>();
    handler.setImageProvider(new HomeMCPHandler.ImageProvider() {
        public byte [] getPlanImage(int maxSize, Level level, float [] area) {
          return null;
        }

        public HomeMCPHandler.Image3D get3DImage(HomeMCPHandler.Image3DRequest request) {
          drawnHomes.add(request.getDrawnHome());
          requests.add(request);
          return new HomeMCPHandler.Image3D(new byte [] {1, 2, 3}, null, "drawn");
        }

        public byte [] getCatalogImage(String name, boolean texture) {
          return null;
        }

        public String get3DInfo() {
          return null;
        }
      });
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"preview_model\",\"arguments\":"
        + "{\"path\":\"" + path(file) + "\",\"length\":450,\"materials\":{\"carpaint\":\"#285A6B\"}}}}"));
    Map<String, Object> result = (Map<String, Object>)response.get("result");
    assertEquals(String.valueOf(result), Boolean.FALSE, result.get("isError"));
    List<Object> contents = (List<Object>)result.get("content");
    String text = (String)((Map<String, Object>)contents.get(0)).get("text");
    assertTrue(text, text.contains("225 x 450 x 135 cm"));
    assertTrue(text, text.contains("24 triangles"));
    assertTrue(text, text.contains("front"));
    assertTrue(text, text.contains("Views: [front_left, front, side, top]"));
    // Four views, each a picture without a composer of pictures
    assertEquals(5, contents.size());
    assertEquals("image", ((Map<String, Object>)contents.get(1)).get("type"));
    assertEquals(4, drawnHomes.size());
    for (Home drawnHome : drawnHomes) {
      assertTrue(drawnHome != null && drawnHome != this.home);
      assertEquals(1, drawnHome.getFurniture().size());
      assertEquals(450, drawnHome.getFurniture().get(0).getDepth(), 0.5f);
      assertTrue(drawnHome.getWalls().isEmpty());
    }
    // The camera looks at the model from out of it, and from above in the last view
    assertTrue(requests.get(1).getPose().getY() > 300);
    assertTrue(requests.get(3).getPose().getZ() > 400);
    // Nothing was added to the home, nor to its undoable changes
    assertTrue(this.home.getFurniture().isEmpty());

    // A piece of the catalog, at another size
    String name = this.preferences.getFurnitureCatalog().getCategories().get(0).getFurniture().get(0).getName();
    response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"preview_model\",\"arguments\":"
        + "{\"name\":" + JSONParser.toJSONString(name) + ",\"views\":[\"top\"],\"width\":333}}}"));
    result = (Map<String, Object>)response.get("result");
    assertEquals(String.valueOf(result), Boolean.FALSE, result.get("isError"));
    assertEquals(2, ((List<Object>)result.get("content")).size());
    assertEquals(333, drawnHomes.get(4).getFurniture().get(0).getWidth(), 0.5f);

    // Errors
    for (String arguments : new String [] {"{}", "{\"path\":\"car.glb\"}", "{\"name\":\"No such piece 123\"}",
                                           "{\"path\":\"" + path(file) + "\",\"views\":[\"under\"]}"}) {
      response = (Map<String, Object>)JSONParser.parse(handler.handle(
          "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"preview_model\",\"arguments\":" + arguments + "}}"));
      assertEquals(arguments, Boolean.TRUE, ((Map<String, Object>)response.get("result")).get("isError"));
    }
  }
}
