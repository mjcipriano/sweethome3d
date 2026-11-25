/*
 * AssistantOnlineModelsTest.java 25 nov. 2026
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.AssistantModelSizeProvider;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.AssistantOnlineModels;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the search of the libraries of 3D models of the web, the import of a model found
 * there and the credits of a home. No server is asked: the answers are the ones kept in
 * the resources of the tests, trimmed from answers of Sketchfab and Poly Haven, and the
 * models are built by the test.
 * @author Sweet Home 3D contributors
 */
public class AssistantOnlineModelsTest {
  private static final String TOKEN = "0123456789abcdef0123456789abcdef";
  private static final String SKETCHFAB_UID = "00000000000000000000000000000001";

  private String                   previousNo3D;
  private Locale                   previousLocale;
  private File                     folder;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private CannedFetcher            fetcher;
  private Map<String, String>      tokens;

  /**
   * Answers the requests it knows and keeps them all.
   */
  private static final class CannedFetcher implements AssistantOnlineModels.Fetcher {
    final Map<String, byte []> answers = new LinkedHashMap<String, byte []>();
    final List<String>         requests = new ArrayList<String>();
    final List<String>         authorizations = new ArrayList<String>();

    public byte [] fetch(String address, String authorization, long maxLength) throws IOException {
      this.requests.add(address);
      this.authorizations.add(authorization);
      byte [] answer = this.answers.get(address);
      if (answer == null) {
        // An address with parameters
        for (Map.Entry<String, byte []> entry : this.answers.entrySet()) {
          if (entry.getKey().endsWith("*") && address.startsWith(entry.getKey().substring(0, entry.getKey().length() - 1))) {
            answer = entry.getValue();
          }
        }
      }
      if (answer == null) {
        throw new IOException("the server knows no such model or file (404)");
      } else if (answer.length > maxLength) {
        throw new IOException("the file is too large");
      }
      return answer;
    }

    int count(String part) {
      int count = 0;
      for (String request : this.requests) {
        if (request.contains(part)) {
          count++;
        }
      }
      return count;
    }
  }

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.UK);
    this.folder = File.createTempFile("online-models", "");
    this.folder.delete();
    assertTrue(this.folder.mkdir());
    this.home = new Home(250);
    this.preferences = new DefaultUserPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
    this.fetcher = new CannedFetcher();
    this.executor.setModelFetcher(this.fetcher);
    // The tokens stay in the test: the folder of the preferences of the user is never read nor written
    this.tokens = new HashMap<String, String>();
    this.executor.setModelTokenStore(new AssistantOnlineModels.TokenStore() {
        public String getToken(String library) {
          return tokens.get(library);
        }

        public void setToken(String library, String token) {
          if (token == null) {
            tokens.remove(library);
          } else {
            tokens.put(library, token);
          }
        }
      });
    this.executor.setWebAccessAllowed(true);
    this.fetcher.answers.put("https://api.sketchfab.com/v3/search*", resource("sketchfabSearch.json"));
    this.fetcher.answers.put("https://api.polyhaven.com/assets?t=models", resource("polyhavenAssets.json"));
    this.fetcher.answers.put("https://api.polyhaven.com/files/ArmChair_01", resource("polyhavenFiles.json"));
  }

  @After
  public void tearDown() {
    delete(this.folder);
    Locale.setDefault(this.previousLocale);
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private static void delete(File file) {
    File [] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        delete(child);
      }
    }
    file.delete();
  }

  private static byte [] resource(String name) throws IOException {
    InputStream in = AssistantOnlineModelsTest.class.getResourceAsStream("resources/" + name);
    assertNotNull(name, in);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      for (int size; (size = in.read(buffer)) != -1; ) {
        out.write(buffer, 0, size);
      }
      return out.toByteArray();
    } finally {
      in.close();
    }
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private HomePieceOfFurniture getPiece(String name) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getName().equals(name)) {
        return piece;
      }
    }
    return null;
  }

  // ---- A model built by the test ----

  /**
   * Returns an image of one color, at JPEG or PNG format.
   */
  private static byte [] createImage(Color color, String format) throws IOException {
    BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = image.createGraphics();
    g2D.setColor(color);
    g2D.fillRect(0, 0, 16, 16);
    g2D.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(image, format, out));
    return out.toByteArray();
  }

  /**
   * Returns the data of a box of 2 x 1 x 1 m: the places of its 8 corners, their coordinates in an image
   * and its 12 triangles.
   */
  private static byte [] createBoxBuffer() {
    ByteBuffer buffer = ByteBuffer.allocate(8 * 12 + 8 * 8 + 36 * 2).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 8; i++) {
      buffer.putFloat((i & 1) == 0 ? -1 : 1).putFloat((i & 2) == 0 ? 0 : 1).putFloat((i & 4) == 0 ? -0.5f : 0.5f);
    }
    for (int i = 0; i < 8; i++) {
      buffer.putFloat((i & 1) == 0 ? 0 : 1).putFloat((i & 2) == 0 ? 0 : 1);
    }
    int [] triangles = {0, 2, 1, 1, 2, 3, 4, 5, 6, 5, 7, 6, 0, 1, 4, 1, 5, 4, 2, 6, 3, 3, 6, 7, 0, 4, 2, 2, 4, 6, 1, 3, 5, 3, 7, 5};
    for (int index : triangles) {
      buffer.putShort((short)index);
    }
    return buffer.array();
  }

  /**
   * Returns the text of a glTF file of the box, with a material whose image is tinted and placed
   * by a transformation, as the models of the libraries often are.
   * @param bufferUri the file of its data, or <code>null</code> for a GLB file
   */
  private static String createGltf(String bufferUri, String imageUri, String otherImageUri, int imageView) {
    return "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0,\"name\":\"body\"}],"
        + "\"extensionsUsed\":[\"KHR_texture_transform\",\"KHR_materials_clearcoat\"],"
        + "\"extensionsRequired\":[\"KHR_materials_clearcoat\"],"
        + "\"materials\":[{\"name\":\"paint\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[0.2,1,1,1],"
        + "\"baseColorTexture\":{\"index\":0,\"extensions\":{\"KHR_texture_transform\":{\"offset\":[0.5,0],\"scale\":[2,3]}}},"
        + "\"metallicFactor\":0,\"roughnessFactor\":0.4},"
        + (otherImageUri != null ? "\"normalTexture\":{\"index\":1}," : "")
        + "\"extensions\":{\"KHR_materials_clearcoat\":{\"clearcoatFactor\":1}}}],"
        + "\"textures\":[{\"source\":0}" + (otherImageUri != null ? ",{\"source\":1}" : "") + "],"
        + "\"images\":[" + (imageUri != null ? "{\"uri\":\"" + imageUri + "\"}" : "{\"bufferView\":" + imageView + ",\"mimeType\":\"image/png\"}")
        + (otherImageUri != null ? ",{\"uri\":\"" + otherImageUri + "\"}" : "") + "],"
        + "\"meshes\":[{\"name\":\"body\",\"primitives\":[{\"attributes\":{\"POSITION\":0,\"TEXCOORD_0\":1},\"indices\":2,\"material\":0}]}],"
        + "\"buffers\":[{" + (bufferUri != null ? "\"uri\":\"" + bufferUri + "\"," : "") + "\"byteLength\":%LENGTH%}],"
        + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":96},{\"buffer\":0,\"byteOffset\":96,\"byteLength\":64},"
        + "{\"buffer\":0,\"byteOffset\":160,\"byteLength\":72}%IMAGE_VIEW%],"
        + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":8,\"type\":\"VEC3\",\"min\":[-1,0,-0.5],\"max\":[1,1,0.5]},"
        + "{\"bufferView\":1,\"componentType\":5126,\"count\":8,\"type\":\"VEC2\"},"
        + "{\"bufferView\":2,\"componentType\":5123,\"count\":36,\"type\":\"SCALAR\"}]}";
  }

  /**
   * Returns a GLB file of the box, with its image in it.
   */
  private static byte [] createGlb() throws IOException {
    byte [] box = createBoxBuffer();
    byte [] image = createImage(new Color(200, 100, 50), "png");
    int imageLength = (image.length + 3) / 4 * 4;
    byte [] binary = new byte [box.length + imageLength];
    System.arraycopy(box, 0, binary, 0, box.length);
    System.arraycopy(image, 0, binary, box.length, image.length);
    String json = createGltf(null, null, null, 3).replace("%LENGTH%", String.valueOf(binary.length))
        .replace("%IMAGE_VIEW%", ",{\"buffer\":0,\"byteOffset\":" + box.length + ",\"byteLength\":" + image.length + "}");
    while (json.length() % 4 != 0) {
      json += " ";
    }
    ByteBuffer glb = ByteBuffer.allocate(12 + 8 + json.length() + 8 + binary.length).order(ByteOrder.LITTLE_ENDIAN);
    glb.putInt(0x46546C67).putInt(2).putInt(glb.capacity());
    glb.putInt(json.length()).putInt(0x4E4F534A).put(json.getBytes("US-ASCII"));
    glb.putInt(binary.length).putInt(0x004E4942).put(binary);
    return glb.array();
  }

  private static byte [] createZip(Map<String, byte []> files) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream(out);
    for (Map.Entry<String, byte []> file : files.entrySet()) {
      zip.putNextEntry(new ZipEntry(file.getKey()));
      zip.write(file.getValue());
      zip.closeEntry();
    }
    zip.close();
    return out.toByteArray();
  }

  /**
   * Returns a file of the model of <code>piece</code>, which is an archive of OBJ files.
   */
  private static byte [] readModelFile(HomePieceOfFurniture piece, String name) throws IOException {
    URL url = ((URLContent)piece.getModel()).getJAREntryURL();
    ZipFile zip = new ZipFile(new File(java.net.URLDecoder.decode(url.getPath(), "UTF-8")));
    try {
      ZipEntry entry = zip.getEntry(name);
      if (entry == null) {
        return null;
      }
      InputStream in = zip.getInputStream(entry);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      for (int size; (size = in.read(buffer)) != -1; ) {
        out.write(buffer, 0, size);
      }
      return out.toByteArray();
    } finally {
      zip.close();
    }
  }

  // ---- Tests ----

  /**
   * The models of two libraries are listed with their source, their author, their license and
   * what it requires, and sorted out by license and by count of triangles.
   */
  @Test
  public void testFindModels() {
    String summary = run("{\"action\":\"find_model\",\"query\":\"sedan car\",\"library\":\"sketchfab\"}");
    assertTrue(summary, summary.contains("sketchfab:" + SKETCHFAB_UID));
    assertTrue(summary, summary.contains("\"Sample sedan of 1971\" by Example Maker One (https://sketchfab.com/example-maker-1)"));
    assertTrue(summary, summary.contains("license CC BY (attribution required)"));
    assertTrue(summary, summary.contains("70258 triangles"));
    assertTrue(summary, summary.contains("download glb 29.4 MB / gltf 27.5 MB"));
    assertTrue(summary, summary.contains("page https://sketchfab.com/3d-models/"));
    assertTrue(summary, summary.contains("preview https://media.sketchfab.com/models/" + SKETCHFAB_UID));
    assertTrue(summary, summary.contains("license CC BY-NC-SA (attribution required, no commercial use, "
        + "modified versions under the same license)"));
    assertFalse("A model that can't be downloaded isn't listed: " + summary, summary.contains("gray car cloth"));
    // Only the words of the search were sent, to the service of the library, without any token
    assertEquals(1, this.fetcher.requests.size());
    assertTrue(this.fetcher.requests.get(0),
        this.fetcher.requests.get(0).startsWith("https://api.sketchfab.com/v3/search?type=models&downloadable=true&q=sedan+car"));
    assertNull(this.fetcher.authorizations.get(0));

    // One license is asked to the service, several are sorted out here
    this.fetcher.requests.clear();
    summary = run("{\"action\":\"find_model\",\"query\":\"sedan\",\"library\":\"sketchfab\",\"license\":\"CC BY\",\"max_triangles\":100000,\"count\":5}");
    assertTrue(this.fetcher.requests.get(0), this.fetcher.requests.get(0).contains("&license=by&max_face_count=100000&count=5"));
    this.fetcher.requests.clear();
    summary = run("{\"action\":\"find_model\",\"query\":\"sedan\",\"library\":\"sketchfab\",\"license\":[\"cc0\",\"by-nc-sa\"]}");
    assertFalse(this.fetcher.requests.get(0), this.fetcher.requests.get(0).contains("license="));
    assertFalse(summary, summary.contains("Sample sedan of 1971"));
    assertTrue(summary, summary.contains("Sample passenger car pack"));
    summary = run("{\"action\":\"find_model\",\"query\":\"sedan\",\"library\":\"sketchfab\",\"max_triangles\":70000}");
    assertFalse(summary, summary.contains("Sample sedan of 1971"));
    assertTrue(summary, summary.contains("Sample passenger car pack"));
    summary = run("{\"action\":\"find_model\",\"query\":\"sedan\",\"license\":\"gpl\"}");
    assertTrue(summary, summary.contains("\"license\" is among"));

    // Poly Haven gives its list, searched here: the words in the names first, then the most downloaded
    this.fetcher.requests.clear();
    summary = run("{\"action\":\"find_model\",\"query\":\"green chairs\",\"library\":\"polyhaven\"}");
    assertEquals(1, this.fetcher.requests.size());
    assertEquals("https://api.polyhaven.com/assets?t=models", this.fetcher.requests.get(0));
    int green = summary.indexOf("polyhaven:GreenChair_01");
    int arm = summary.indexOf("polyhaven:ArmChair_01");
    int school = summary.indexOf("polyhaven:SchoolChair_01");
    assertTrue(summary, green > 0 && arm > green && school > arm);
    assertFalse(summary, summary.contains("Barrel"));
    assertTrue(summary, summary.contains("\"Arm Chair 01\" by Example Maker Four, license CC0 (nothing required), 5626 triangles"));
    assertTrue(summary, summary.contains("page https://polyhaven.com/a/ArmChair_01"));
    assertTrue(summary, summary.contains("preview https://cdn.polyhaven.com/asset_img/thumbs/ArmChair_01.png"));
    summary = run("{\"action\":\"find_model\",\"query\":\"chair\",\"library\":\"polyhaven\",\"license\":\"by\"}");
    assertTrue(summary, summary.contains("No model found"));
    summary = run("{\"action\":\"find_model\",\"query\":\"chair\",\"library\":\"polyhaven\",\"max_triangles\":5000,\"count\":1}");
    assertTrue(summary, summary.contains("GreenChair_01"));
    assertFalse(summary, summary.contains("ArmChair_01"));

    // Both libraries by default; one that fails doesn't hide the other
    this.fetcher.answers.remove("https://api.sketchfab.com/v3/search*");
    summary = run("{\"action\":\"find_model\",\"query\":\"chair\",\"colour\":\"red\"}");
    assertTrue(summary, summary.contains("polyhaven:ArmChair_01"));
    assertTrue(summary, summary.contains("sketchfab couldn't be searched"));
    assertTrue(summary, summary.contains("colour"));
    assertTrue(run("{\"action\":\"find_model\"}").contains("\"query\" is required"));
    assertTrue(run("{\"action\":\"find_model\",\"query\":\"chair\",\"library\":\"elsewhere\"}").contains("\"library\" is one of"));

    // Nothing is asked without the permission of the user
    this.executor.setWebAccessAllowed(false);
    this.fetcher.requests.clear();
    summary = run("{\"action\":\"find_model\",\"query\":\"chair\"}");
    assertTrue(summary, summary.contains("Allow web services"));
    summary = run("{\"action\":\"import_model\",\"source\":\"polyhaven:ArmChair_01\"}");
    assertTrue(summary, summary.contains("Allow web services"));
    assertTrue(this.fetcher.requests.toString(), this.fetcher.requests.isEmpty());
    assertTrue(this.home.getFurniture().isEmpty());
    assertTrue(run("{\"action\":\"web_sources\"}").contains("api.sketchfab.com only"));
  }

  /**
   * A model of Poly Haven is downloaded with the files it needs, imported at the length asked,
   * with its image tinted and placed as its material tells, and credited.
   */
  @Test
  public void testImportFromPolyHaven() throws Exception {
    // The object that tints images in the application
    this.executor.setModelSizeProvider(new AssistantModelSizeProvider());
    byte [] box = createBoxBuffer();
    String gltf = createGltf("ArmChair_01.bin", "textures/Armchair_01_diff_1k.jpg", "textures/Armchair_01_nor_gl_1k.jpg", 0)
        .replace("%LENGTH%", String.valueOf(box.length)).replace("%IMAGE_VIEW%", "");
    String root = "https://dl.polyhaven.org/file/ph-assets/Models/";
    this.fetcher.answers.put(root + "gltf/1k/ArmChair_01/ArmChair_01_1k.gltf", gltf.getBytes("UTF-8"));
    // The data of the model is the same for every size of images
    this.fetcher.answers.put(root + "gltf/4k/ArmChair_01/ArmChair_01.bin", box);
    this.fetcher.answers.put(root + "jpg/1k/ArmChair_01/Armchair_01_diff_1k.jpg", createImage(new Color(200, 100, 50), "jpg"));

    String summary = run("{\"action\":\"import_model\",\"source\":\"polyhaven:ArmChair_01\",\"length\":150,\"x\":300,\"y\":200}");
    HomePieceOfFurniture piece = getPiece("Arm Chair 01");
    assertNotNull(summary, piece);
    assertEquals(150, piece.getWidth(), 0.5f);
    assertEquals(75, piece.getDepth(), 0.5f);
    assertEquals(75, piece.getHeight(), 0.5f);
    assertEquals(300, piece.getX(), 0.01f);
    assertEquals("https://polyhaven.com/a/ArmChair_01", piece.getProperty("com.eteks.sweethome3d.modelSource"));
    assertEquals("Example Maker Four", piece.getProperty("com.eteks.sweethome3d.modelAuthor"));
    assertEquals("CC0", piece.getProperty("com.eteks.sweethome3d.modelLicense"));
    assertTrue(summary, summary.contains("Downloaded from polyhaven: license CC0 (nothing required)"));
    assertFalse("The folder of the download isn't told: " + summary, summary.contains(System.getProperty("java.io.tmpdir")));
    // The list, the files of the model, its text, its data and the image of its color: not its other images
    assertEquals(this.fetcher.requests.toString(), 5, this.fetcher.requests.size());
    assertEquals(0, this.fetcher.count("nor_gl"));
    assertEquals(0, this.fetcher.count("_4k."));
    for (String authorization : this.fetcher.authorizations) {
      assertNull(authorization);
    }

    // The image is multiplied by the color of the material, which stays white
    assertTrue(summary, summary.contains("the image of the material paint was tinted with its color"));
    String mtl = new String(readModelFile(piece, "model.mtl"), "US-ASCII");
    assertTrue(mtl, mtl.contains("Kd 1.0 1.0 1.0"));
    assertTrue(mtl, mtl.contains("map_Kd texture_1.jpg"));
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(readModelFile(piece, "texture_1.jpg")));
    Color pixel = new Color(image.getRGB(8, 8));
    // The factor 0.2 of a glTF file is a share of light: 48 % of the value of a pixel
    assertEquals(200 * Math.pow(0.2, 1 / 2.2), pixel.getRed(), 6);
    assertEquals(100, pixel.getGreen(), 6);
    assertEquals(50, pixel.getBlue(), 6);
    // Its coordinates are scaled by 2 and 3 and moved by a half: from 0.5 to 2.5, and 3 high
    String obj = new String(readModelFile(piece, "model.obj"), "US-ASCII");
    float [] range = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
    for (String line : obj.split("\n")) {
      if (line.startsWith("vt ")) {
        String [] values = line.split(" ");
        range [0] = Math.min(range [0], Float.parseFloat(values [1]));
        range [1] = Math.max(range [1], Float.parseFloat(values [1]));
        range [2] = Math.min(range [2], Float.parseFloat(values [2]));
        range [3] = Math.max(range [3], Float.parseFloat(values [2]));
      }
    }
    assertEquals(0.5f, range [0], 1E-4f);
    assertEquals(2.5f, range [1], 1E-4f);
    assertEquals(3f, range [3] - range [2], 1E-4f);
    assertFalse("The extensions of materials are left aside in silence: " + summary, summary.contains("clearcoat"));
    assertFalse(summary, summary.contains("KHR_texture_transform"));

    // An other size of images, a model that doesn't exist, and undo
    summary = run("{\"action\":\"import_model\",\"source\":\"polyhaven:ArmChair_01\",\"resolution\":\"8k\"}");
    assertTrue(summary, summary.contains("\"resolution\" is among"));
    summary = run("{\"action\":\"import_model\",\"source\":\"polyhaven:Nothing_01\"}");
    assertTrue(summary, summary.contains("Poly Haven has no model"));
    assertEquals(1, this.home.getFurniture().size());
    this.homeController.undo();
    assertEquals(0, this.home.getFurniture().size());

    // Without the object that changes images, the answer tells that the tint is missing
    this.executor.setModelImageFilter(null);
    summary = run("{\"action\":\"import_model\",\"source\":\"polyhaven:ArmChair_01\",\"name\":\"Chair\"}");
    assertNotNull(summary, getPiece("Chair"));
    assertTrue(summary, summary.contains("the color that tints the image of the material paint isn't applied"));
  }

  /**
   * A model of Sketchfab is downloaded with the token of the user, which goes to the service
   * of Sketchfab only and appears nowhere else.
   */
  @Test
  public void testImportFromSketchfab() throws Exception {
    String api = "https://api.sketchfab.com/v3/models/" + SKETCHFAB_UID;
    Map<String, Object> search = (Map<String, Object>)JSONParser.parse(new String(resource("sketchfabSearch.json"), "UTF-8"));
    Object model = ((List<?>)search.get("results")).get(0);
    this.fetcher.answers.put(api, JSONParser.toJSON(model).getBytes("UTF-8"));
    String archive = "https://sketchfab-prod-media.s3.amazonaws.com/archives/" + SKETCHFAB_UID + "/gltf/file.zip?X-Amz-Signature=abc";
    this.fetcher.answers.put(api + "/download",
        ("{\"gltf\":{\"url\":\"" + archive + "\",\"size\":5000,\"expires\":300},\"usdz\":{\"url\":\"https://example.org/x.usdz\",\"size\":1}}").getBytes("UTF-8"));
    byte [] box = createBoxBuffer();
    Map<String, byte []> files = new LinkedHashMap<String, byte []>();
    files.put("license.txt", "CC Attribution".getBytes("UTF-8"));
    files.put("scene.gltf", createGltf("scene.bin", "textures/paint_baseColor.png", null, 0)
        .replace("%LENGTH%", String.valueOf(box.length)).replace("%IMAGE_VIEW%", "").getBytes("UTF-8"));
    files.put("scene.bin", box);
    files.put("textures/paint_baseColor.png", createImage(new Color(200, 100, 50), "png"));
    this.fetcher.answers.put(archive, createZip(files));

    // Without a token, nothing is asked and the answer tells where the user finds it
    String summary = run("{\"action\":\"import_model\",\"source\":\"sketchfab:" + SKETCHFAB_UID + "\"}");
    assertTrue(summary, summary.contains("https://sketchfab.com/settings/password"));
    assertTrue(this.fetcher.requests.isEmpty());
    assertTrue(run("{\"action\":\"import_model\",\"source\":\"sketchfab:" + SKETCHFAB_UID + "\",\"token\":\"a b\"}")
        .contains("doesn't look like one"));
    assertTrue(this.tokens.isEmpty());

    summary = run("{\"action\":\"import_model\",\"source\":\"sketchfab:" + SKETCHFAB_UID + "\",\"token\":\"" + TOKEN
        + "\",\"length\":480,\"front\":\"+x\",\"materials\":{\"paint\":\"#285A6B\"}}");
    HomePieceOfFurniture piece = getPiece("Sample sedan of 1971");
    assertNotNull(summary, piece);
    assertEquals(480, piece.getDepth(), 0.5f);
    assertEquals(240, piece.getWidth(), 0.5f);
    assertTrue(new String(readModelFile(piece, "model.mtl"), "US-ASCII").contains("Kd 0.15686275 0.3529412 0.41960785"));
    assertEquals("https://sketchfab.com/3d-models/none-" + SKETCHFAB_UID, piece.getProperty("com.eteks.sweethome3d.modelSource"));
    assertEquals("Example Maker One (https://sketchfab.com/example-maker-1)", piece.getProperty("com.eteks.sweethome3d.modelAuthor"));
    assertEquals("CC BY", piece.getProperty("com.eteks.sweethome3d.modelLicense"));
    assertTrue(summary, summary.contains("license CC BY (attribution required)"));
    assertTrue(summary, summary.contains("The token is kept"));
    assertEquals(TOKEN, this.tokens.get("sketchfab"));
    // The token went to the service of Sketchfab only, with the request for the links of the download
    assertEquals(Arrays3.asList(api, api + "/download", archive), this.fetcher.requests);
    assertNull(this.fetcher.authorizations.get(0));
    assertEquals("Token " + TOKEN, this.fetcher.authorizations.get(1));
    assertNull("The address of the file is signed: no token for its host", this.fetcher.authorizations.get(2));
    // And appears nowhere: not in the answer, not in the piece, not in the address kept as its source
    assertFalse(summary, summary.contains(TOKEN));
    for (String name : piece.getPropertyNames()) {
      assertFalse(name, piece.getProperty(name).contains(TOKEN));
      assertFalse(name, piece.getProperty(name).contains("X-Amz-Signature"));
    }
    assertFalse(summary, summary.contains("X-Amz-Signature"));

    // The next download uses the token kept
    this.fetcher.requests.clear();
    this.fetcher.authorizations.clear();
    summary = run("{\"action\":\"import_model\",\"source\":\"sketchfab:" + SKETCHFAB_UID + "\",\"name\":\"Second\"}");
    assertNotNull(summary, getPiece("Second"));
    assertEquals("Token " + TOKEN, this.fetcher.authorizations.get(1));
    assertFalse(summary, summary.contains(TOKEN));
    assertFalse(summary, summary.contains("The token is kept"));

    // A license that forbids modified versions, with a model recolored
    Map<String, Object> noDerivatives = new LinkedHashMap<String, Object>((Map<String, Object>)model);
    Map<String, Object> license = new LinkedHashMap<String, Object>();
    license.put("label", "CC Attribution-NoDerivs");
    noDerivatives.put("license", license);
    this.fetcher.answers.put(api, JSONParser.toJSON(noDerivatives).getBytes("UTF-8"));
    summary = run("{\"action\":\"import_model\",\"source\":\"sketchfab:" + SKETCHFAB_UID + "\",\"name\":\"Third\",\"materials\":{\"paint\":\"#FF0000\"}}");
    assertTrue(summary, summary.contains("license CC BY-ND (attribution required, no modified version)"));
    assertTrue(summary, summary.contains("WARNING: this license doesn't allow modified versions"));

    // A download larger than what is accepted isn't started
    this.fetcher.answers.put(api + "/download",
        ("{\"gltf\":{\"url\":\"" + archive + "\",\"size\":900000000}}").getBytes("UTF-8"));
    this.fetcher.requests.clear();
    summary = run("{\"action\":\"import_model\",\"source\":\"sketchfab:" + SKETCHFAB_UID + "\",\"name\":\"Fourth\"}");
    assertTrue(summary, summary.contains("more than the 150.0 MB accepted"));
    assertEquals(0, this.fetcher.count("amazonaws"));
    assertNull(getPiece("Fourth"));

    // The token is forgotten when asked, and never repeated
    summary = run("{\"action\":\"set_model_token\",\"library\":\"sketchfab\",\"token\":\"" + TOKEN + "x\"}");
    assertTrue(summary, summary.contains("Kept the token of sketchfab"));
    assertFalse(summary, summary.contains(TOKEN));
    assertEquals(TOKEN + "x", this.tokens.get("sketchfab"));
    summary = run("{\"action\":\"set_model_token\",\"library\":\"sketchfab\",\"remove\":true}");
    assertTrue(summary, summary.contains("Forgot the token"));
    assertTrue(this.tokens.isEmpty());
    assertTrue(run("{\"action\":\"set_model_token\",\"library\":\"polyhaven\",\"token\":\"" + TOKEN + "\"}")
        .contains("libraries which need a token"));
    assertTrue(run("{\"action\":\"import_model\",\"source\":\"sketchfab:../etc\"}").contains("\"source\" is sketchfab:"));
  }

  /**
   * A file at an https address is imported, and an archive can't write files out of its folder.
   */
  @Test
  public void testImportFromAddress() throws Exception {
    this.fetcher.answers.put("https://example.org/models/box.glb?version=2", createGlb());
    String summary = run("{\"action\":\"import_model\",\"source\":\"https://example.org/models/box.glb?version=2\","
        + "\"author\":\"Someone\",\"license\":\"CC BY-NC\",\"height\":50}");
    HomePieceOfFurniture piece = getPiece("box");
    assertNotNull(summary, piece);
    assertEquals(50, piece.getHeight(), 0.5f);
    assertEquals("https://example.org/models/box.glb", piece.getProperty("com.eteks.sweethome3d.modelSource"));
    assertEquals("Someone", piece.getProperty("com.eteks.sweethome3d.modelAuthor"));
    assertTrue(summary, summary.contains("Downloaded from example.org"));

    assertTrue(run("{\"action\":\"import_model\",\"source\":\"http://example.org/box.glb\"}").contains("https address"));
    assertTrue(run("{\"action\":\"import_model\",\"source\":\"https://example.org/box.exe\"}").contains(".glb, .gltf, .obj or .zip"));
    assertTrue(run("{\"action\":\"import_model\",\"source\":\"https://example.org/missing.glb\"}").contains("404"));

    // An archive with a name that leaves its folder
    Map<String, byte []> files = new LinkedHashMap<String, byte []>();
    files.put("scene.gltf", "{}".getBytes("UTF-8"));
    files.put("../../outside.txt", "x".getBytes("UTF-8"));
    this.fetcher.answers.put("https://example.org/bad.zip", createZip(files));
    summary = run("{\"action\":\"import_model\",\"source\":\"https://example.org/bad.zip\"}");
    assertTrue(summary, summary.contains("a file out of its folder"));
    assertEquals(1, this.home.getFurniture().size());

    // Without "source" naming a library or an address, import_model is import_furniture
    File file = new File(this.folder, "local.glb");
    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
    out.write(createGlb());
    out.close();
    this.fetcher.requests.clear();
    summary = run("{\"action\":\"import_model\",\"path\":\"" + file.getPath().replace("\\", "\\\\")
        + "\",\"source\":\"https://example.org/page\",\"name\":\"Local\"}");
    assertNotNull(summary, getPiece("Local"));
    assertTrue(this.fetcher.requests.isEmpty());
  }

  /**
   * The credits list the models with an author or a license and what each license requires,
   * and export_home writes them beside what it exports.
   */
  @Test
  public void testCredits() throws Exception {
    String summary = run("{\"action\":\"model_credits\"}");
    assertTrue(summary, summary.contains("No piece of this home has a model with an author"));
    this.fetcher.answers.put("https://example.org/car.glb", createGlb());
    run("{\"action\":\"import_model\",\"source\":\"https://example.org/car.glb\",\"name\":\"Camry\",\"author\":\"Ann\",\"license\":\"CC BY-NC-SA\"}");
    run("{\"action\":\"import_model\",\"source\":\"https://example.org/car.glb\",\"name\":\"Camry\",\"author\":\"Ann\",\"license\":\"CC BY-NC-SA\"}");
    run("{\"action\":\"import_model\",\"source\":\"https://example.org/car.glb\",\"name\":\"Civic\",\"author\":\"Bob\",\"license\":\"CC0\"}");
    run("{\"action\":\"import_model\",\"source\":\"https://example.org/car.glb\",\"name\":\"Van\"}");
    run("{\"action\":\"add_furniture\",\"name\":\"" + this.preferences.getFurnitureCatalog().getCategories().get(0).getFurniture().get(0).getName()
        + "\",\"x\":0,\"y\":0}");
    summary = run("{\"action\":\"model_credits\"}");
    assertTrue(summary, summary.contains("\"Camry\" (2 pieces) by Ann, license CC BY-NC-SA (attribution required, no commercial use, "
        + "modified versions under the same license), https://example.org/car.glb"));
    assertTrue(summary, summary.contains("\"Civic\" by Bob, license CC0 (nothing required)"));
    assertTrue(summary, summary.contains("\"Van\", author unknown, license unknown, https://example.org/car.glb"));
    assertTrue(summary, summary.contains("can't be used for a commercial purpose"));
    assertTrue(summary, summary.contains("must be credited"));
    assertTrue(summary, summary.contains("without a known license"));
    assertEquals("One line per model with credits: " + summary, 3, summary.split("\n- ").length - 1);

    // Written beside an export
    HomeMCPHandler handler = new HomeMCPHandler(this.home, this.preferences, this.homeController, "test");
    handler.setEditingAllowed(true);
    handler.setHomeOperations(new HomeMCPHandler.HomeOperations() {
        public String export(String format, File target, Map<String, Object> options) {
          return "Wrote " + target.getName() + ".";
        }

        public String serve(Home otherHome) {
          return null;
        }

        public String startVisit(String mode) {
          return null;
        }
      });
    File target = new File(this.folder, "house.obj");
    String answer = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"export_home\","
        + "\"arguments\":{\"format\":\"obj\",\"path\":\"" + target.getPath().replace("\\", "\\\\") + "\"}}}");
    File credits = new File(this.folder, "house-CREDITS.txt");
    assertTrue(answer, credits.isFile());
    assertTrue(answer, answer.contains("house-CREDITS.txt"));
    String text = new String(java.nio.file.Files.readAllBytes(credits.toPath()), "UTF-8");
    assertTrue(text, text.contains("\"Civic\" by Bob, license CC0"));
    assertTrue(text, text.contains("\"Camry\" (2 pieces) by Ann"));
    // No file of credits for a home without credited models
    Home emptyHome = new Home();
    handler = new HomeMCPHandler(emptyHome, this.preferences, new HomeController(emptyHome, this.preferences, new SwingViewFactory()), "test");
    handler.setEditingAllowed(true);
    handler.setHomeOperations(new HomeMCPHandler.HomeOperations() {
        public String export(String format, File target, Map<String, Object> options) {
          return "Wrote " + target.getName() + ".";
        }

        public String serve(Home otherHome) {
          return null;
        }

        public String startVisit(String mode) {
          return null;
        }
      });
    answer = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"export_home\","
        + "\"arguments\":{\"format\":\"obj\",\"path\":\"" + new File(this.folder, "empty.obj").getPath().replace("\\", "\\\\") + "\"}}}");
    assertFalse(new File(this.folder, "empty-CREDITS.txt").exists());
    assertFalse(answer, answer.contains("CREDITS"));
  }

  /**
   * Lists compared whatever their class.
   */
  private static final class Arrays3 {
    static List<String> asList(String... items) {
      return new ArrayList<String>(java.util.Arrays.asList(items));
    }
  }
}
