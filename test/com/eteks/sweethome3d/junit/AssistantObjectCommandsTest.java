/*
 * AssistantObjectCommandsTest.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertArrayEquals;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantClient;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.ChatMessage;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.Provider;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.AssistantGeneratedModel;
import com.eteks.sweethome3d.viewcontroller.BlenderSceneScript;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the commands of the assistant that bring objects into a home (stairs,
 * pictures, imported 3D models and textures), the pictures sent to the model
 * and the Blender scene script.
 */
public class AssistantObjectCommandsTest {
  private String                   previousNo3D;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private File                     folder;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
    this.folder = Files.createTempDirectory("sh3d-assistant-test").toFile();
  }

  @After
  public void tearDown() {
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
    deleteFolder(this.folder);
  }

  private static void deleteFolder(File folder) {
    File [] files = folder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          deleteFolder(file);
        } else {
          file.delete();
        }
      }
    }
    folder.delete();
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private File createImage(String name, int width, int height) throws IOException {
    File file = new File(this.folder, name);
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(image, name.endsWith(".png") ? "png" : "jpg", file);
    return file;
  }

  private static String json(File file) {
    return JSONParser.toJSONString(file.getAbsolutePath());
  }

  /**
   * Returns the names of the entries of the archive a model content is stored in.
   */
  private static List<String> getEntries(Content content) throws IOException {
    String url = ((URLContent)content).getURL().toString();
    InputStream in = new java.net.URL(url.substring("jar:".length(), url.indexOf("!/"))).openStream();
    ZipInputStream zip = new ZipInputStream(in);
    List<String> entries = new ArrayList<String>();
    for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
      entries.add(entry.getName());
    }
    zip.close();
    return entries;
  }

  @Test
  public void testStairs() throws Exception {
    run("{\"action\":\"add_rectangular_room\",\"x\":0,\"y\":0,\"width\":600,\"depth\":450}");
    // Without level above, stairs climb the height of a floor
    String summary = run("{\"action\":\"add_stairs\",\"x\":500,\"y\":250}");
    assertTrue(summary, summary.contains("there is no level above yet"));
    this.homeController.undo();
    assertTrue(this.home.getFurniture().isEmpty());

    run("{\"action\":\"add_level\",\"name\":\"First floor\",\"elevation\":280},{\"action\":\"select_level\",\"index\":1}");
    summary = run("{\"action\":\"add_stairs\",\"x\":500,\"y\":250,\"angle\":90,\"width\":100}");
    assertTrue(summary, summary.contains("16 steps") && summary.contains("to the level \"First floor\""));
    HomePieceOfFurniture stairs = this.home.getFurniture().get(0);
    assertEquals("Staircase", stairs.getName());
    assertEquals(100f, stairs.getWidth(), 0.01f);
    assertEquals(16 * 25f, stairs.getDepth(), 0.01f);
    assertEquals("Stairs reach the floor of the next level", 280f, stairs.getHeight(), 0.01f);
    assertEquals((float)(Math.PI / 2), stairs.getAngle(), 1e-4f);
    assertEquals(this.home.getLevels().get(0), stairs.getLevel());
    assertNotNull("The floor above is cut out", stairs.getStaircaseCutOutShape());
    assertEquals(16, AssistantGeneratedModel.getStepCount(280));
    assertNotNull(stairs.getModel().openStream());
    assertFalse("Stairs through the ceiling aren't a defect", this.executor.checkHome().contains("above_ceiling"));
    assertTrue(run("{\"action\":\"add_stairs\",\"x\":0,\"y\":0,\"width\":5}").contains("Ignored add_stairs"));
  }

  @Test
  public void testPictures() throws Exception {
    File picture = createImage("Sunset over the sea.png", 400, 200);
    // A panel keeps the proportions of its picture
    String summary = run("{\"action\":\"add_picture\",\"image\":" + json(picture)
        + ",\"x\":100,\"y\":20,\"width\":120,\"elevation\":140}");
    assertTrue(summary, summary.contains("on its front"));
    HomePieceOfFurniture panel = this.home.getFurniture().get(0);
    assertEquals("Sunset over the sea", panel.getName());
    assertEquals(120f, panel.getWidth(), 0.01f);
    assertEquals(60f, panel.getHeight(), 0.01f);
    assertEquals(3f, panel.getDepth(), 0.1f);
    assertEquals(140f, panel.getElevation(), 0);
    List<String> entries = getEntries(panel.getModel());
    assertEquals("Model, materials and picture: " + entries, 3, entries.size());
    assertTrue(entries.toString(), entries.get(0).endsWith(".obj") && entries.get(1).endsWith(".mtl")
        && entries.get(2).endsWith(".png"));

    // A rug shows the picture on its top
    File rugPicture = createImage("rug.jpg", 300, 200);
    summary = run("{\"action\":\"add_picture\",\"image\":" + json(rugPicture)
        + ",\"name\":\"Persian rug\",\"mode\":\"floor\",\"x\":300,\"y\":200,\"width\":240}");
    assertTrue(summary, summary.contains("on its top"));
    HomePieceOfFurniture rug = this.home.getFurniture().get(1);
    assertEquals("Persian rug", rug.getName());
    assertEquals(240f, rug.getWidth(), 0.01f);
    assertEquals(160f, rug.getDepth(), 0.01f);
    assertTrue(rug.getHeight() < 2);
    assertArrayEquals(new int [] {300, 200},
        AssistantGeneratedModel.getImageSize(AssistantGeneratedModel.readFile(rugPicture, 1000000)));

    // The picture attached to the conversation
    assertTrue(run("{\"action\":\"add_picture\",\"image\":\"attached\"}").contains("no picture is attached"));
    this.executor.setAttachedImage(picture);
    run("{\"action\":\"add_picture\",\"image\":\"attached\",\"mode\":\"box\",\"width\":80,\"height\":40,\"depth\":50}");
    assertEquals(3, this.home.getFurniture().size());
    assertEquals(50f, this.home.getFurniture().get(2).getDepth(), 0.1f);

    // Once saved and read again, each piece still has its model, materials and picture
    run("{\"action\":\"add_stairs\",\"x\":500,\"y\":250}");
    File homeFile = new File(this.folder, "pictures.sh3d");
    new com.eteks.sweethome3d.io.HomeFileRecorder().writeHome(this.home, homeFile.getAbsolutePath());
    Home readHome = new com.eteks.sweethome3d.io.HomeFileRecorder().readHome(homeFile.getAbsolutePath());
    assertEquals(4, readHome.getFurniture().size());
    for (HomePieceOfFurniture piece : readHome.getFurniture()) {
      java.net.URL modelUrl = ((URLContent)piece.getModel()).getURL();
      assertTrue(piece.getName() + " " + modelUrl, modelUrl.toString().endsWith(".obj"));
      java.io.BufferedReader reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(modelUrl.openStream(), "ISO-8859-1"));
      String library = null;
      for (String line; (line = reader.readLine()) != null; ) {
        if (line.startsWith("mtllib ")) {
          library = line.substring(7).trim();
        }
      }
      reader.close();
      assertNotNull(library);
      new java.net.URL(modelUrl, library).openStream().close();
      assertNotNull(piece.getIcon().openStream());
    }
    this.homeController.undo();

    // Only pictures are read
    File text = new File(this.folder, "notes.png");
    Files.write(text.toPath(), "secret".getBytes("UTF-8"));
    assertTrue(run("{\"action\":\"add_picture\",\"image\":" + json(text) + "}").contains("isn't a PNG, JPEG or GIF image"));
    assertTrue(run("{\"action\":\"add_picture\",\"image\":\"relative.png\"}").contains("must be absolute"));
    assertEquals(3, this.home.getFurniture().size());
  }

  @Test
  public void testImportTexture() throws Exception {
    run("{\"action\":\"add_rectangular_room\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300}");
    Room room = this.home.getRooms().get(0);
    File image = createImage("Blue tiles.jpg", 256, 128);
    String summary = run("{\"action\":\"import_texture\",\"image\":" + json(image)
        + ",\"width\":60,\"id\":\"R1\",\"part\":\"floor\"}");
    assertTrue(summary, summary.contains("Added the texture \"Blue tiles\""));
    assertNotNull(room.getFloorTexture());
    assertEquals("Blue tiles", room.getFloorTexture().getName());
    assertEquals(60f, room.getFloorTexture().getWidth(), 0.01f);
    assertEquals(30f, room.getFloorTexture().getHeight(), 0.01f);
    assertTrue("The texture can be found again: " + this.executor.searchTextures("blue tiles", 5), this.executor.searchTextures("blue tiles", 5).contains("Blue tiles"));
    this.homeController.undo();
    assertNull(room.getFloorTexture());
    // The same name twice
    assertTrue(run("{\"action\":\"import_texture\",\"image\":" + json(image) + "}").contains("already has a texture"));
  }

  @Test
  public void testImportFurniture() throws Exception {
    File model = new File(this.folder, "Arm chair.obj");
    Files.write(model.toPath(), ("mtllib chair.mtl\nv 0 0 0\nv 0.8 0 0\nv 0 1 0.7\nusemtl wood\nf 1 2 3\n").getBytes("ISO-8859-1"));
    Files.write(new File(this.folder, "chair.mtl").toPath(),
        ("newmtl wood\nmap_Kd wood.png\nmap_Bump ../outside.png\n").getBytes("ISO-8859-1"));
    createImage("wood.png", 8, 8);
    createImage("unrelated.png", 8, 8);

    assertTrue("No model can be read without a size provider",
        run("{\"action\":\"import_furniture\",\"path\":" + json(model) + "}").contains("can't be read here"));
    this.executor.setModelSizeProvider(new AssistantCommandExecutor.ModelSizeProvider() {
        public float [] getModelSize(Content content) {
          return new float [] {0.8f, 0.7f, 1f};
        }
      });
    String summary = run("{\"action\":\"import_furniture\",\"path\":" + json(model) + ",\"x\":150,\"y\":120}");
    assertTrue(summary, summary.contains("Imported \"Arm chair\" at 80 x 70 x 100 cm") && summary.contains("meter"));
    HomePieceOfFurniture chair = this.home.getFurniture().get(0);
    assertEquals(150f, chair.getX(), 0);
    // The model is stored with the files it names, found beside it only
    List<String> entries = getEntries(chair.getModel());
    assertEquals(entries.toString(), 3, entries.size());
    assertTrue(entries.toString(), entries.contains("Arm chair.obj") && entries.contains("chair.mtl")
        && entries.contains("wood.png"));
    assertNotNull(chair.getModel().openStream());

    // A given dimension scales the others
    run("{\"action\":\"import_furniture\",\"path\":" + json(model) + ",\"name\":\"Big chair\",\"height\":150}");
    HomePieceOfFurniture bigChair = this.home.getFurniture().get(1);
    assertEquals("Big chair", bigChair.getName());
    assertEquals(150f, bigChair.getHeight(), 0.01f);
    assertEquals(120f, bigChair.getWidth(), 0.01f);

    File document = new File(this.folder, "letter.txt");
    Files.write(document.toPath(), "private".getBytes("UTF-8"));
    assertTrue(run("{\"action\":\"import_furniture\",\"path\":" + json(document) + "}")
        .contains("only OBJ, DAE, 3DS, KMZ and ZIP files"));
    assertEquals(2, this.home.getFurniture().size());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPictureSentToTheModel() throws Exception {
    byte [] image = {1, 2, 3, 4};
    List<ChatMessage> conversation = AssistantClient.newConversation();
    conversation.add(new ChatMessage("user", "What is this \"chair\"?", image, "image/jpeg"));
    conversation.add(new ChatMessage("assistant", "A chair."));

    String openAI = new AssistantClient(Provider.OPENAI_COMPATIBLE, "https://example.com/v1", "", "model")
        .buildRequestBody("system", conversation);
    java.util.Map<String, Object> request = (java.util.Map<String, Object>)JSONParser.parse(openAI);
    List<Object> messages = (List<Object>)request.get("messages");
    List<Object> content = (List<Object>)((java.util.Map<String, Object>)messages.get(1)).get("content");
    assertEquals("image_url", ((java.util.Map<String, Object>)content.get(0)).get("type"));
    assertEquals("data:image/jpeg;base64,AQIDBA==",
        ((java.util.Map<String, Object>)((java.util.Map<String, Object>)content.get(0)).get("image_url")).get("url"));
    assertEquals("What is this \"chair\"?", ((java.util.Map<String, Object>)content.get(1)).get("text"));
    assertEquals("A chair.", ((java.util.Map<String, Object>)messages.get(2)).get("content"));

    String anthropic = new AssistantClient(Provider.ANTHROPIC, "https://example.com/v1", "", "model")
        .buildRequestBody("system", conversation);
    request = (java.util.Map<String, Object>)JSONParser.parse(anthropic);
    messages = (List<Object>)request.get("messages");
    content = (List<Object>)((java.util.Map<String, Object>)messages.get(0)).get("content");
    java.util.Map<String, Object> source =
        (java.util.Map<String, Object>)((java.util.Map<String, Object>)content.get(0)).get("source");
    assertEquals("base64", source.get("type"));
    assertEquals("image/jpeg", source.get("media_type"));
    assertEquals("AQIDBA==", source.get("data"));
  }

  @Test
  public void testBlenderSceneScript() throws Exception {
    Level level = new Level("Ground", 30, 12, 250);
    this.home.addLevel(level);
    Camera camera = this.home.getObserverCamera();
    camera.setX(200);
    camera.setY(300);
    camera.setZ(170);
    camera.setYaw(0);
    camera.setPitch(0);
    this.home.setCamera(camera);
    String script = BlenderSceneScript.create(this.home, "scene.obj");
    assertTrue(script.contains("MODEL_FILE = \"scene.obj\""));
    // Meters, with the y axis of the plan turned upside down
    assertTrue(script, script.contains("CAMERA_LOCATION = (2.0000, -3.0000, 1.7000)"));
    // A yaw of 0 looks toward +y in the plan, which is -y in Blender
    assertTrue(script, script.contains("CAMERA_DIRECTION = (-0.0000, -1.0000, -0.0000)")
        || script.contains("CAMERA_DIRECTION = (0.0000, -1.0000, 0.0000)")
        || script.contains("CAMERA_DIRECTION = (-0.0000, -1.0000, 0.0000)"));
    assertTrue(script, script.contains("GROUND_ELEVATION = 0.3000"));
    assertTrue(script, script.contains("LIGHTS = []"));
    assertFalse("Every value was filled", script.contains("@"));
    assertTrue(script.contains("bpy.ops.wm.obj_import") && script.contains("bpy.ops.render.render"));
  }
}
