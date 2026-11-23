/*
 * HomeMCPServerTest.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.HomeMCPServer;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the MCP message handler and its local HTTP transport.
 */
public class HomeMCPServerTest {
  private static final String TOKEN = "0123456789abcdef0123456789abcdef";

  /**
   * A home with a room and two sofas (F1 at x=100, F2 at x=300), and its handler.
   */
  private static final class Fixture {
    final Home            home = new Home();
    final UserPreferences preferences;
    final HomeController  homeController;
    final HomeMCPHandler  handler;
    final HomePieceOfFurniture sofa1;
    final HomePieceOfFurniture sofa2;

    Fixture() throws Exception {
      this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
      this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
      this.home.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
      this.sofa1 = new HomePieceOfFurniture(executor.findCatalogPiece("Sofa", false));
      this.sofa1.setX(100);
      this.sofa2 = new HomePieceOfFurniture(executor.findCatalogPiece("Sofa", false));
      this.sofa2.setX(300);
      this.home.addPieceOfFurniture(this.sofa1);
      this.home.addPieceOfFurniture(this.sofa2);
      this.handler = new HomeMCPHandler(this.home, this.preferences, this.homeController, "test");
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> call(HomeMCPHandler handler, String request) {
    String response = handler.handle(request);
    assertNotNull("Response to " + request, response);
    Map<String, Object> parsed = (Map<String, Object>)JSONParser.parse(response);
    assertEquals("2.0", parsed.get("jsonrpc"));
    return parsed;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> callTool(HomeMCPHandler handler, String name, String arguments) {
    Map<String, Object> response = call(handler, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
        + "\"params\":{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}");
    assertNull("No protocol error: " + response, response.get("error"));
    return (Map<String, Object>)response.get("result");
  }

  @SuppressWarnings("unchecked")
  private static String text(Map<String, Object> toolResult) {
    return (String)((Map<String, Object>)((List<Object>)toolResult.get("content")).get(0)).get("text");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testLifecycleAndToolList() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      HomeMCPHandler handler = new Fixture().handler;
      Map<String, Object> response = call(handler, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
          + "\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
          + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
      assertEquals("Request id echoed as a number", Double.valueOf(1), response.get("id"));
      Map<String, Object> result = (Map<String, Object>)response.get("result");
      assertEquals("Supported version accepted", "2025-03-26", result.get("protocolVersion"));
      assertTrue(((Map<String, Object>)result.get("capabilities")).containsKey("tools"));
      assertEquals("sweethome3d", ((Map<String, Object>)result.get("serverInfo")).get("name"));

      // An unknown protocol version is answered with the one this server speaks
      response = call(handler, "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"initialize\","
          + "\"params\":{\"protocolVersion\":\"2099-01-01\"}}");
      assertEquals("a", response.get("id"));
      assertEquals(HomeMCPHandler.PROTOCOL_VERSION,
          ((Map<String, Object>)response.get("result")).get("protocolVersion"));

      assertNull("Notifications get no response",
          handler.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
      assertTrue(((Map<String, Object>)call(handler,
          "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}").get("result")).isEmpty());

      response = call(handler, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");
      List<Object> tools = (List<Object>)((Map<String, Object>)response.get("result")).get("tools");
      assertEquals(35, tools.size());
      for (Object tool : tools) {
        Map<String, Object> toolMap = (Map<String, Object>)tool;
        assertNotNull(toolMap.get("name"));
        assertEquals("object", ((Map<String, Object>)toolMap.get("inputSchema")).get("type"));
      }

      // Protocol errors
      Map<String, Object> error = (Map<String, Object>)call(handler, "{not json").get("error");
      assertEquals(Double.valueOf(-32700), error.get("code"));
      error = (Map<String, Object>)call(handler,
          "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/list\"}").get("error");
      assertEquals(Double.valueOf(-32601), error.get("code"));
      error = (Map<String, Object>)call(handler, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
          + "\"params\":{\"name\":\"format_disk\"}}").get("error");
      assertEquals(Double.valueOf(-32602), error.get("code"));
    } finally {
      restoreNo3D(previousNo3D);
    }
  }

  /**
   * Checks the items looked at are entirely in the field of view of the standard views.
   */
  @Test
  public void testViewpoints() throws Exception {
    Fixture fixture = new Fixture();
    com.eteks.sweethome3d.model.Wall wall = new com.eteks.sweethome3d.model.Wall(0, 0, 400, 0, 10, 250);
    fixture.home.addWall(wall);
    List<com.eteks.sweethome3d.model.Selectable> all = new java.util.ArrayList<com.eteks.sweethome3d.model.Selectable>();
    float aspectRatio = 1.6f;
    for (String view : com.eteks.sweethome3d.viewcontroller.HomeViewpoints.AERIAL_VIEWS) {
      com.eteks.sweethome3d.viewcontroller.HomeViewpoints.Pose pose =
          com.eteks.sweethome3d.viewcontroller.HomeViewpoints.getPose(fixture.home, view, all, null, aspectRatio, 0);
      double yaw = pose.getYaw();
      double pitch = pose.getPitch();
      double [] forward = {-Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), -Math.sin(pitch)};
      double [] right = {-Math.cos(yaw), -Math.sin(yaw), 0};
      double [] up = {-Math.sin(yaw) * Math.sin(pitch), Math.cos(yaw) * Math.sin(pitch), Math.cos(pitch)};
      double tangent = Math.tan(pose.getFieldOfView() / 2);
      double largestExtent = 0;
      // The corners of the room on the floor and the top of the wall
      float [][] points = {{0, 0, 0}, {400, 0, 0}, {400, 300, 0}, {0, 300, 0}, {0, -5, 250}, {400, -5, 250}};
      for (float [] point : points) {
        double dx = point [0] - pose.getX();
        double dy = point [1] - pose.getY();
        double dz = point [2] - pose.getZ();
        double depth = dx * forward [0] + dy * forward [1] + dz * forward [2];
        assertTrue(view + " in front of the camera", depth > 0);
        double horizontal = Math.abs(dx * right [0] + dy * right [1] + dz * right [2]) / depth / tangent;
        double vertical = Math.abs(dx * up [0] + dy * up [1] + dz * up [2]) / depth / (tangent / aspectRatio);
        assertTrue(view + " in sight: " + horizontal + " " + vertical, horizontal <= 1 && vertical <= 1);
        largestExtent = Math.max(largestExtent, Math.max(horizontal, vertical));
      }
      assertTrue(view + " fills the picture: " + largestExtent, largestExtent > 0.6);
    }
    try {
      com.eteks.sweethome3d.viewcontroller.HomeViewpoints.getPose(new Home(), "top", all, null, 1, 0);
      fail("An empty home has no view");
    } catch (IllegalArgumentException ex) {
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testToolsReadAndEditHome() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Fixture fixture = new Fixture();
      HomeMCPHandler handler = fixture.handler;
      Map<String, Object> result = callTool(handler, "get_home", "{}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      assertTrue(text(result), text(result).contains("F1") && text(result).contains("Sofa"));

      result = callTool(handler, "search_catalog", "{\"query\":\"office chair\"}");
      assertTrue(text(result), text(result).contains("\"Office chair\""));
      assertTrue(text(callTool(handler, "list_catalog", "{}")).contains("Desk"));
      assertEquals(Boolean.TRUE, callTool(handler, "search_catalog", "{}").get("isError"));

      // The user deletes the first sofa after the client read the home: F2 must
      // still designate the second sofa and F1 nothing
      fixture.home.deletePieceOfFurniture(fixture.sofa1);
      result = callTool(handler, "apply_commands", "{\"commands\":["
          + "{\"action\":\"move\",\"id\":\"F2\",\"dx\":50},"
          + "{\"action\":\"move\",\"id\":\"F1\",\"dx\":70},"
          + "{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":200,\"y\":150}]}");
      // The other commands are applied, and the id of the deleted sofa is reported as an error
      assertEquals(text(result), Boolean.TRUE, result.get("isError"));
      assertTrue(text(result), text(result).contains("No item has the id F1"));
      assertEquals(350f, fixture.sofa2.getX(), 1e-3f);
      assertEquals(100f, fixture.sofa1.getX(), 1e-3f);
      assertEquals(2, fixture.home.getFurniture().size());
      assertEquals("Desk", fixture.home.getFurniture().get(1).getName());

      // One call is one undo step
      assertEquals(Boolean.FALSE, callTool(handler, "undo", "{}").get("isError"));
      assertEquals(300f, fixture.sofa2.getX(), 1e-3f);
      assertEquals(1, fixture.home.getFurniture().size());
      assertEquals(Boolean.FALSE, callTool(handler, "redo", "{}").get("isError"));
      assertEquals(350f, fixture.sofa2.getX(), 1e-3f);
      assertEquals(Boolean.TRUE, callTool(handler, "redo", "{}").get("isError"));

      // Invalid commands change nothing and are reported as tool errors
      assertEquals(Boolean.TRUE, callTool(handler, "apply_commands", "{\"commands\":[]}").get("isError"));
      assertEquals(Boolean.TRUE,
          callTool(handler, "apply_commands", "{\"commands\":[{\"id\":\"F1\"}]}").get("isError"));
      assertEquals(Boolean.TRUE, callTool(handler, "apply_commands",
          "{\"commands\":[{\"action\":\"move\",\"id\":\"F99\",\"dx\":5}]}").get("isError"));

      // Building commands, checks and textures go through the same tools
      result = callTool(handler, "apply_commands", "{\"commands\":["
          + "{\"action\":\"add_rectangular_room\",\"name\":\"Garage\",\"x\":1000,\"y\":0,\"width\":500,\"depth\":400},"
          + "{\"action\":\"auto_roof\",\"type\":\"hip\"}]}");
      assertEquals(text(result), Boolean.FALSE, result.get("isError"));
      assertTrue(text(result), text(result).contains("hip roof"));
      assertEquals(4, fixture.home.getWalls().size());
      result = callTool(handler, "check_home", "{}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      assertTrue(text(result), text(result).startsWith("Home check:"));
      result = callTool(handler, "get_report", "{}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      assertTrue(text(result), text(result).contains("Garage: 20.0 m2") && text(result).contains("4 walls"));
      result = callTool(handler, "search_textures", "{\"query\":\"parquet\"}");
      assertTrue(text(result), text(result).startsWith("Textures:"));
      assertEquals(Boolean.TRUE, callTool(handler, "search_textures", "{}").get("isError"));

      // A picture needs a provider, and comes back as an image content
      assertEquals(Boolean.TRUE, callTool(handler, "get_plan_image", "{}").get("isError"));
      final List<HomeMCPHandler.Image3DRequest> requests = new java.util.ArrayList<HomeMCPHandler.Image3DRequest>();
      handler.setImageProvider(new HomeMCPHandler.ImageProvider() {
          public byte [] getPlanImage(int maxSize, com.eteks.sweethome3d.model.Level level, float [] area) {
            return new byte [] {(byte)maxSize, 2, 3};
          }

          public HomeMCPHandler.Image3D get3DImage(HomeMCPHandler.Image3DRequest request) {
            requests.add(request);
            return new HomeMCPHandler.Image3D(
                new byte [] {(byte)(request.getWidth() / 10), (byte)(request.getHeight() / 10)},
                request.getPreviewWidth() > 0 ? new byte [] {7} : null, "Drawn by a test");
          }

          public byte [] getCatalogImage(String name, boolean texture) {
            return name.startsWith("Missing") ? null : new byte [] {(byte)name.length()};
          }

          public String get3DInfo() {
            return "Renderers of the test";
          }
        });
      result = callTool(handler, "get_plan_image", "{\"max_size\":250}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      Map<String, Object> image = (Map<String, Object>)((List<Object>)result.get("content")).get(0);
      assertEquals("image", image.get("type"));
      assertEquals("image/png", image.get("mimeType"));
      assertEquals(java.util.Base64.getEncoder().encodeToString(new byte [] {(byte)250, 2, 3}), image.get("data"));

      assertEquals(Boolean.TRUE, callTool(handler, "export_blender", "{\"folder\":\"/tmp/x\"}").get("isError"));
      result = callTool(handler, "get_3d_image", "{\"width\":400,\"height\":300}");
      image = (Map<String, Object>)((List<Object>)result.get("content")).get(0);
      assertEquals(java.util.Base64.getEncoder().encodeToString(new byte [] {40, 30}), image.get("data"));
      // By default the camera of the home, every level, and the renderer of the 3D view
      HomeMCPHandler.Image3DRequest request = requests.get(requests.size() - 1);
      assertEquals(HomeMCPHandler.Image3DRequest.AUTO, request.getRenderer());
      assertNull(request.getPose());
      assertTrue(request.isAllLevels());
      assertTrue(request.isShadows() && request.isAmbientOcclusion());
      String imageText = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
      assertTrue(imageText, imageText.startsWith("Drawn by a test, 400 x 300 pixels")
          && imageText.contains("camera of the home") && imageText.contains("All levels"));

      // A standard view has the whole room in sight from the asked side, and doesn't move the camera of the home
      float cameraX = fixture.home.getCamera().getX();
      result = callTool(handler, "get_3d_image", "{\"view\":\"south_east\",\"renderer\":\"opengl\","
          + "\"shadows\":false,\"levels\":\"selected\"}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      request = requests.get(requests.size() - 1);
      assertEquals(HomeMCPHandler.Image3DRequest.OPENGL, request.getRenderer());
      assertFalse(request.isShadows());
      assertTrue(request.isAmbientOcclusion());
      assertFalse(request.isAllLevels());
      assertTrue("Aerial view", request.getPose().isAerial());
      assertTrue("South east of the room: " + request.getPose().describe(),
          request.getPose().getX() > 400 && request.getPose().getY() > 300 && request.getPose().getZ() > 100);
      assertEquals(cameraX, fixture.home.getCamera().getX(), 0);

      // A view from the inside of a room stands in it
      callTool(handler, "get_home", "{}");
      result = callTool(handler, "get_3d_image", "{\"view\":\"inside\",\"target\":\"R1\",\"renderer\":\"raytrace\"}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      request = requests.get(requests.size() - 1);
      assertEquals(HomeMCPHandler.Image3DRequest.RAYTRACE, request.getRenderer());
      assertFalse(request.getPose().isAerial());
      assertTrue(request.getPose().describe(), request.getPose().getX() > 0 && request.getPose().getX() < 400
          && request.getPose().getY() > 0 && request.getPose().getY() < 300
          && request.getPose().getZ() == 160);
      assertEquals(85, Math.toDegrees(request.getPose().getFieldOfView()), 0.01);
      // A piece is looked at from the asked side, closer than the whole home
      result = callTool(handler, "get_3d_image", "{\"view\":\"top\",\"target\":\"F1\"}");
      float pieceViewHeight = requests.get(requests.size() - 1).getPose().getZ();
      result = callTool(handler, "get_3d_image", "{\"view\":\"top\"}");
      assertTrue(requests.get(requests.size() - 1).getPose().getZ() > pieceViewHeight);
      assertEquals(Math.PI / 2, requests.get(requests.size() - 1).getPose().getPitch(), 1E-5);
      // A camera for one picture
      result = callTool(handler, "get_3d_image", "{\"camera\":{\"x\":0,\"y\":0,\"height\":150,"
          + "\"look_at_x\":0,\"look_at_y\":100,\"look_at_height\":150},\"fov\":70}");
      request = requests.get(requests.size() - 1);
      assertEquals(0, request.getPose().getYaw(), 1E-5);
      assertEquals(0, request.getPose().getPitch(), 1E-5);
      assertEquals(70, Math.toDegrees(request.getPose().getFieldOfView()), 0.01);
      // The best quality of the ray tracer is limited in size
      result = callTool(handler, "get_3d_image", "{\"quality\":\"high\",\"width\":1600,\"height\":1200}");
      request = requests.get(requests.size() - 1);
      assertTrue(request.isHighQuality());
      assertEquals(HomeMCPHandler.Image3DRequest.RAYTRACE, request.getRenderer());
      assertEquals(1000, request.getWidth());
      assertEquals(750, request.getHeight());
      // Wrong arguments are explained
      int requestCount = requests.size();
      for (String arguments : new String [] {"{\"view\":\"behind\"}", "{\"target\":\"R1\"}",
          "{\"view\":\"top\",\"target\":\"F99\"}", "{\"renderer\":\"magic\"}", "{\"view\":\"inside\"}",
          "{\"renderer\":\"opengl\",\"quality\":\"high\"}", "{\"fov\":5}", "{\"levels\":\"none\"}",
          "{\"level\":\"Attic\"}", "{\"point_of_view\":\"Nowhere\"}", "{\"camera\":{\"x\":0}}",
          "{\"save_to\":\"relative.png\"}", "{\"save_to\":\"/tmp/picture.jpg\"}"}) {
        result = callTool(handler, "get_3d_image", arguments);
        assertEquals(arguments + " " + text(result), Boolean.TRUE, result.get("isError"));
      }
      assertEquals("No picture rendered for wrong arguments", requestCount, requests.size());

      // A picture saved in a file may be larger, and comes back with a smaller copy
      java.io.File pictureFile = java.io.File.createTempFile("sh3d-mcp", ".png");
      try {
        String pictureArguments = "{\"width\":3000,\"height\":2000,\"save_to\":"
            + JSONParser.toJSONString(pictureFile.getAbsolutePath());
        result = callTool(handler, "get_3d_image", pictureArguments + "}");
        assertEquals("An existing file isn't overwritten", Boolean.TRUE, result.get("isError"));
        result = callTool(handler, "get_3d_image", pictureArguments + ",\"replace\":true}");
        assertEquals(text(result), Boolean.FALSE, result.get("isError"));
        request = requests.get(requests.size() - 1);
        assertEquals(3000, request.getWidth());
        assertEquals(1000, request.getPreviewWidth());
        assertEquals(2, pictureFile.length());
        image = (Map<String, Object>)((List<Object>)result.get("content")).get(0);
        assertEquals(java.util.Base64.getEncoder().encodeToString(new byte [] {7}), image.get("data"));
        imageText = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
        assertTrue(imageText, imageText.startsWith("Saved to " + pictureFile.getAbsolutePath()));
        handler.setEditingAllowed(false);
        result = callTool(handler, "get_3d_image", pictureArguments + ",\"replace\":true}");
        assertEquals("Read-only mode writes no file", Boolean.TRUE, result.get("isError"));
        assertEquals("Read-only mode still gives pictures", Boolean.FALSE,
            callTool(handler, "get_3d_image", "{}").get("isError"));
        handler.setEditingAllowed(true);
      } finally {
        pictureFile.delete();
      }

      // The camera can be moved to the same views, and kept as a point of view
      result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_camera\",\"view\":\"north\"},"
          + "{\"action\":\"store_point_of_view\",\"name\":\"From the north\"}]}");
      assertEquals(text(result), Boolean.FALSE, result.get("isError"));
      assertSame(fixture.home.getTopCamera(), fixture.home.getCamera());
      assertTrue("North of the room", fixture.home.getCamera().getY() < 0);
      assertEquals(1, fixture.home.getStoredCameras().size());
      result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_camera\",\"view\":\"inside\",\"id\":\"R1\"}]}");
      assertSame(fixture.home.getObserverCamera(), fixture.home.getCamera());
      result = callTool(handler, "get_3d_image", "{\"point_of_view\":\"from the north\"}");
      assertEquals(text(result), Boolean.FALSE, result.get("isError"));
      assertTrue(requests.get(requests.size() - 1).getPose().isAerial());
      assertTrue(requests.get(requests.size() - 1).getPose().getY() < 0);
      result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_camera\",\"point_of_view\":\"From the north\"}]}");
      assertSame(fixture.home.getTopCamera(), fixture.home.getCamera());

      result = callTool(handler, "get_3d_info", "{}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      assertTrue(text(result), text(result).startsWith("Camera of the home: aerial view")
          && text(result).contains("\"From the north\"") && text(result).contains("south_east")
          && text(result).endsWith("Renderers of the test"));

      // Pictures of catalog items, with a word for the ones which don't exist
      result = callTool(handler, "get_catalog_image", "{\"names\":[\"Sofa\",\"Missing one\"]}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      List<Object> catalogContents = (List<Object>)result.get("content");
      assertEquals(3, catalogContents.size());
      assertEquals("\"Sofa\":", ((Map<String, Object>)catalogContents.get(0)).get("text"));
      assertEquals("image", ((Map<String, Object>)catalogContents.get(1)).get("type"));
      assertTrue(((String)((Map<String, Object>)catalogContents.get(2)).get("text")).contains("Missing one"));
      assertEquals(Boolean.TRUE, callTool(handler, "get_catalog_image", "{\"names\":[\"Missing\"]}").get("isError"));
      assertEquals(Boolean.TRUE, callTool(handler, "get_catalog_image", "{}").get("isError"));

      // A part of the plan
      result = callTool(handler, "get_plan_image", "{\"area\":{\"x\":0,\"y\":0,\"width\":200,\"height\":100}}");
      assertEquals(Boolean.FALSE, result.get("isError"));
      assertTrue(((String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text"))
          .startsWith("Plan from x 0, y 0 (top left) to x 200, y 100 cm"));
      result = callTool(handler, "get_plan_image", "{\"target\":\"R1\"}");
      assertTrue(((String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text"))
          .startsWith("Plan from x -100, y -100 (top left) to x 500, y 400 cm"));
      assertEquals(Boolean.TRUE, callTool(handler, "get_plan_image", "{\"level\":\"Attic\"}").get("isError"));
      assertEquals(Boolean.TRUE, callTool(handler, "get_plan_image", "{\"area\":{\"x\":0}}").get("isError"));
      assertEquals(Boolean.TRUE, callTool(handler, "get_plan_image", "{\"target\":\"R9\"}").get("isError"));
      final java.io.File [] exportFolder = {null};
      handler.setSceneExporter(new HomeMCPHandler.SceneExporter() {
          public String exportForBlender(java.io.File folder) {
            exportFolder [0] = folder;
            return new java.io.File(folder, "render_blender.py").getPath();
          }
        });
      assertEquals("Relative folders are refused", Boolean.TRUE,
          callTool(handler, "export_blender", "{\"folder\":\"relative\"}").get("isError"));
      String blenderFolder = new java.io.File(System.getProperty("java.io.tmpdir"), "sh3d-blender").getAbsolutePath();
      result = callTool(handler, "export_blender", "{\"folder\":" + JSONParser.toJSONString(blenderFolder) + "}");
      assertEquals(text(result), Boolean.FALSE, result.get("isError"));
      assertTrue(text(result), text(result).contains("blender -b -P"));
      assertEquals(blenderFolder, exportFolder [0].getPath());

      // Read-only mode keeps reading tools but refuses any change
      handler.setEditingAllowed(false);
      result = callTool(handler, "apply_commands",
          "{\"commands\":[{\"action\":\"delete\",\"ids\":[\"F2\"]}]}");
      assertEquals(Boolean.TRUE, result.get("isError"));
      assertEquals(Boolean.TRUE, callTool(handler, "undo", "{}").get("isError"));
      exportFolder [0] = null;
      assertEquals("Exporting writes files", Boolean.TRUE, callTool(handler, "export_blender",
          "{\"folder\":" + JSONParser.toJSONString(blenderFolder) + "}").get("isError"));
      assertNull(exportFolder [0]);
      assertEquals(Boolean.FALSE, callTool(handler, "get_3d_image", "{}").get("isError"));
      assertEquals("Sofa, desk and roof", 3, fixture.home.getFurniture().size());
      assertEquals(350f, fixture.sofa2.getX(), 1e-3f);
      assertEquals(Boolean.FALSE, callTool(handler, "get_home", "{}").get("isError"));
    } finally {
      restoreNo3D(previousNo3D);
    }
  }

  /**
   * The pictures of the sun through a day and a year, and the control of its window.
   */
  @Test
  public void testHelpAndCapabilities() throws Exception {
    Fixture fixture = new Fixture();
    HomeMCPHandler handler = fixture.handler;
    // Every command by area
    String help = text(callTool(handler, "help", "{}"));
    assertTrue(help, help.contains("Fences, walls and gates:") && help.contains("\n  add_fence - ")
        && help.contains("Rooms and walls:") && help.contains("\n  add_rectangular_room - ") && help.contains("\n  help - "));
    assertFalse(help, help.contains("Other commands"));
    // One command, with what is related to it
    help = text(callTool(handler, "help", "{\"command\":\"add_gate\"}"));
    assertTrue(help, help.startsWith("add_gate\nSyntax: {\"action\":\"add_gate\"") && help.contains("single_swing")
        && help.contains("Related (Fences, walls and gates): add_fence") && !help.contains("Rules:"));
    help = text(callTool(handler, "help", "{\"command\":\"add_fence\"}"));
    assertTrue(help, help.contains("chain_link (diamond mesh") && help.contains("Gate types:"));
    help = text(callTool(handler, "help", "{\"command\":\"add_fence\",\"style\":\"wrought_iron\"}"));
    assertTrue(help, help.startsWith("Style of fence ornamental_steel") && help.contains("finials=spear"));
    help = text(callTool(handler, "help", "{\"command\":\"restore_detail\"}"));
    assertTrue(help, help.contains("back to full detail") && !help.contains("Rules:") && !help.contains("MUST be names"));
    // A command told about in the entry of another one, and one that doesn't exist
    help = text(callTool(handler, "help", "{\"command\":\"remove_gate\"}"));
    assertTrue(help, help.contains("has no entry of its own") && help.contains("add_gate"));
    help = text(callTool(handler, "help", "{\"command\":\"teleport\"}"));
    assertTrue(help, help.startsWith("No command \"teleport\""));
    // A word
    help = text(callTool(handler, "help", "{\"search\":\"lot_line\"}"));
    assertTrue(help, help.contains("1 command mentions \"lot_line\"") && help.contains("add_fence: "));
    help = text(callTool(handler, "help", "{\"search\":\"lattice\"}"));
    assertTrue(help, help.contains("Styles of fence with \"lattice\": lattice_top, lattice_panel"));
    // The same through apply_commands
    help = text(callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"help\",\"search\":\"dormer\"}]}"));
    assertTrue(help, help.contains("add_dormer"));
    String capabilities = text(callTool(handler, "get_capabilities", "{}"));
    assertTrue(capabilities, capabilities.contains("Fences: 63 styles") && capabilities.contains("100 commands per call")
        && capabilities.contains("3000 at most") && capabilities.contains("EPSG:4326"));
    assertTrue(text(callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"get_capabilities\"}]}")).contains("Fences: 63 styles"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSunStudy() throws Exception {
    Fixture fixture = new Fixture();
    HomeMCPHandler handler = fixture.handler;
    // Paris, the day of the summer solstice at 10:00
    callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_compass\",\"latitude\":48.85,\"longitude\":2.35,"
        + "\"time_zone\":\"Europe/Paris\"},{\"action\":\"set_sun\",\"date\":\"2026-06-21\",\"time\":\"10:00\"}]}");
    final long time = fixture.home.getCamera().getTime();
    final List<HomeMCPHandler.Image3DRequest> requests = new java.util.ArrayList<HomeMCPHandler.Image3DRequest>();
    final String [] drawnBy = {"Drawn by a test"};
    handler.setImageProvider(new HomeMCPHandler.ImageProvider() {
        public byte [] getPlanImage(int maxSize, com.eteks.sweethome3d.model.Level level, float [] area) {
          return null;
        }

        public HomeMCPHandler.Image3D get3DImage(HomeMCPHandler.Image3DRequest request) {
          requests.add(request);
          return new HomeMCPHandler.Image3D(new byte [] {(byte)requests.size()}, null, drawnBy [0]);
        }

        public byte [] getCatalogImage(String name, boolean texture) {
          return null;
        }

        public String get3DInfo() {
          return "";
        }
      });
    // Without the application, no picture and no window
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "sun_study", "{}").get("isError"));
    final List<String> calls = new java.util.ArrayList<String>();
    final String [][] sheetCaptions = new String [1][];
    handler.setSunStudyView(new HomeMCPHandler.SunStudyView() {
        public void showWindow(boolean visible) {
          calls.add("window " + visible);
        }

        public void play(String animation) {
          calls.add("play " + animation);
        }

        public void setDisplay(Boolean openGL, Boolean shadows) {
          calls.add("display " + openGL + " " + shadows);
        }

        public String describe() {
          return "State of the test.";
        }

        public byte [] createContactSheet(byte [][] images, String [] captions, int columns) {
          sheetCaptions [0] = captions;
          return new byte [] {(byte)images.length, (byte)columns};
        }

        public byte [] createSunHoursImage(byte [][] masks, byte [] shading, double stepHours, double maximumHours,
                                           String title, float [] shares) {
          shares [0] = 0.25f;
          shares [1] = 0.5f;
          shares [2] = 0.25f;
          sheetCaptions [0] = new String [] {title, String.valueOf(Math.round(stepHours * 60)), String.valueOf(maximumHours)};
          return new byte [] {(byte)masks.length, shading [0]};
        }
      });

    // A day: 6 pictures between sunrise and sunset, of a copy of the home at each time
    Map<String, Object> result = callTool(handler, "get_sun_study_image", "{\"view\":\"top\"}");
    assertEquals(Boolean.FALSE, result.get("isError"));
    Map<String, Object> image = (Map<String, Object>)((List<Object>)result.get("content")).get(0);
    assertEquals(java.util.Base64.getEncoder().encodeToString(new byte [] {6, 3}), image.get("data"));
    String text = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
    assertTrue(text, text.startsWith("Drawn by a test, 6 pictures of 400 x 260 pixels")
        && text.contains("The day 2026-06-21, sunrise at 05:47 and sunset at 21:58")
        && text.contains("\n6. 2026-06-21 20:35: sun "));
    assertEquals(6, requests.size());
    long previousTime = 0;
    for (HomeMCPHandler.Image3DRequest request : requests) {
      Home drawnHome = request.getDrawnHome();
      assertTrue(fixture.home != drawnHome);
      assertEquals("true", drawnHome.getProperty("sunFollowsTime"));
      assertNull(drawnHome.getProperty("sunFalseColorsMaximum"));
      assertTrue(drawnHome.getCamera().getTime() > previousTime);
      assertEquals(drawnHome.getCamera().getTime(), drawnHome.getTopCamera().getTime());
      previousTime = drawnHome.getCamera().getTime();
      assertEquals(400, request.getWidth());
      assertTrue(request.getPose().isAerial());
    }
    assertTrue(sheetCaptions [0][0], sheetCaptions [0][0].startsWith("2026-06-21 07:10   sun "));
    assertEquals("The sun of the home didn't move", time, fixture.home.getCamera().getTime());
    assertNull(fixture.home.getProperty("sunFollowsTime"));

    // Given hours include both ends
    requests.clear();
    result = callTool(handler, "get_sun_study_image", "{\"from\":\"08:00\",\"to\":\"18:00\",\"steps\":3,"
        + "\"date\":\"2026-12-21\",\"false_colors\":true,\"width\":2000}");
    text = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
    assertTrue(text, text.contains("3 pictures of 800 x 260") && text.contains("1. 2026-12-21 08:00: the sun is ")
        && text.contains("2. 2026-12-21 13:00: sun 17.") && text.contains("3. 2026-12-21 18:00")
        && text.contains("red 120000 lux"));
    assertEquals("120000", requests.get(0).getDrawnHome().getProperty("sunFalseColorsMaximum"));
    assertEquals("2026-12-21 08:00   sun down", sheetCaptions [0][0]);

    // A year: the equinoxes and the solstices at the time of the home, or the 21st of months
    requests.clear();
    result = callTool(handler, "get_sun_study_image", "{\"sweep\":\"year\",\"false_colors\":30000}");
    text = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
    assertTrue(text, text.contains("The year 2026 at 10:00") && text.contains("1. 2026-03-20 10:00")
        && text.contains("2. 2026-06-21 10:00") && text.contains("3. 2026-09-22 10:00") && text.contains("4. 2026-12-21 10:00")
        && text.contains("red 30000 lux"));
    assertEquals(4, requests.size());
    result = callTool(handler, "get_sun_study_image", "{\"sweep\":\"year\",\"steps\":12,\"time\":\"15:30\"}");
    text = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
    assertTrue(text, text.contains("1. 2026-01-21 15:30") && text.contains("12. 2026-12-21 15:30"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"sweep\":\"year\",\"steps\":5}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"steps\":20}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"from\":\"18:00\",\"to\":\"9:00\"}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"sweep\":\"month\"}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"date\":\"June 21\"}").get("isError"));

    // The hours of sun of a day: pictures of where the sun reaches, each 20 minutes, then the usual picture
    requests.clear();
    assertEquals("Not without OpenGL", Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"sweep\":\"hours\"}").get("isError"));
    requests.clear();
    drawnBy [0] = "Drawn by the OpenGL renderer (test)";
    result = callTool(handler, "get_sun_study_image", "{\"sweep\":\"hours\",\"view\":\"top\"}");
    assertEquals(Boolean.FALSE, result.get("isError"));
    image = (Map<String, Object>)((List<Object>)result.get("content")).get(0);
    assertEquals(java.util.Base64.getEncoder().encodeToString(new byte [] {48, 49}), image.get("data"));
    text = (String)((Map<String, Object>)((List<Object>)result.get("content")).get(1)).get("text");
    assertTrue(text, text.startsWith("Drawn by the OpenGL renderer, 800 x 520 pixels")
        && text.contains("Hours of direct sun on 2026-06-21") && text.contains("from 48 instants 20 minutes apart")
        && text.contains("red 17 h") && text.contains("25 % get less than 2 hours of sun, 50 % from 2 to 6 hours and 25 % more"));
    assertEquals(49, requests.size());
    Home maskHome = requests.get(0).getDrawnHome();
    assertEquals("true", maskHome.getProperty("sunMask"));
    assertEquals("true", maskHome.getProperty("sunFollowsTime"));
    assertEquals(0x00FF00, maskHome.getEnvironment().getSkyColor());
    assertTrue(requests.get(0).isShadows());
    assertNull(requests.get(48).getDrawnHome().getProperty("sunMask"));
    assertNull(requests.get(48).getDrawnHome().getProperty("sunFollowsTime"));
    assertEquals("[Hours of direct sun on 2026-06-21, 20, 17.0]", java.util.Arrays.toString(sheetCaptions [0]));
    result = callTool(handler, "get_sun_study_image", "{\"sweep\":\"hours\",\"from\":\"12:00\",\"to\":\"16:00\","
        + "\"steps\":8,\"max_hours\":4}");
    assertEquals("[Hours of direct sun on 2026-06-21, 30, 4.0]", java.util.Arrays.toString(sheetCaptions [0]));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"sweep\":\"hours\",\"steps\":2}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_sun_study_image", "{\"sweep\":\"hours\",\"from\":\"23:00\"}").get("isError"));
    drawnBy [0] = "Drawn by a test";

    // The sun warns when it isn't computed for the place of the home, or with a time zone of elsewhere
    result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_georeference\",\"lat\":48.85,\"lon\":2.35,"
        + "\"x\":0,\"y\":0,\"rotation\":30},{\"action\":\"set_sun\"}]}");
    assertFalse(text(result), text(result).contains("Warning"));
    result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_compass\",\"latitude\":40,\"north_direction\":10},"
        + "{\"action\":\"set_sun\"}]}");
    assertTrue(text(result), text(result).contains("Warning: The home is georeferenced at latitude 48.85000, longitude 2.35000, "
        + "north 30.0 degrees, but the sun is computed for a place 984 km away and a north turned by -20.0 degrees"));
    result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_compass\",\"georeference\":true,"
        + "\"time_zone\":\"Asia/Kolkata\"},{\"action\":\"set_sun\"}]}");
    assertTrue(text(result), !text(result).contains("georeferenced")
        && text(result).contains("Warning: The time zone Asia/Kolkata (UTC+5:30) is 5 hours away from the solar time of the longitude 2.35"));
    assertEquals(30f, (float)Math.toDegrees(fixture.home.getCompass().getNorthDirection()), 1E-3f);
    callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"set_compass\",\"time_zone\":\"Europe/Paris\"}]}");

    // The window, its animations and the renderer of the 3D view
    assertEquals("State of the test.", text(callTool(handler, "sun_study", "{}")));
    assertTrue(calls.toString(), calls.isEmpty());
    result = callTool(handler, "sun_study", "{\"window\":\"open\",\"renderer\":\"opengl\",\"shadows\":true}");
    assertEquals(Boolean.FALSE, result.get("isError"));
    assertEquals("[display true true, window true]", calls.toString());
    calls.clear();
    result = callTool(handler, "sun_study", "{\"play\":\"day\"}");
    assertTrue(text(result), text(result).startsWith("State of the test. It was switched to the light of the sun")
        && text(result).contains("changes the time"));
    assertEquals("[play day]", calls.toString());
    assertEquals("true", fixture.home.getProperty("sunFollowsTime"));
    calls.clear();
    callTool(handler, "sun_study", "{\"play\":\"stop\",\"window\":\"close\",\"renderer\":\"java3d\"}");
    assertEquals("[display false null, play null, window false]", calls.toString());
    assertEquals(Boolean.TRUE, callTool(handler, "sun_study", "{\"play\":\"month\"}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "sun_study", "{\"window\":\"close\",\"play\":\"year\"}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "sun_study", "{\"renderer\":\"vulkan\"}").get("isError"));

    // Without the permission to change things, only the window and the stop
    handler.setEditingAllowed(false);
    calls.clear();
    assertEquals(Boolean.TRUE, callTool(handler, "sun_study", "{\"play\":\"year\"}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "sun_study", "{\"shadows\":false}").get("isError"));
    assertEquals(Boolean.FALSE, callTool(handler, "sun_study", "{\"window\":\"open\",\"play\":\"stop\"}").get("isError"));
    assertEquals("[window true, play null]", calls.toString());
    assertEquals(Boolean.FALSE, callTool(handler, "get_sun_study_image", "{\"steps\":2}").get("isError"));
  }

  @Test
  public void testHttpTransportRequiresTokenAndLocalOrigin() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    HomeMCPServer server = new HomeMCPServer(new Fixture().handler, TOKEN, 0);
    try {
      server.start();
      assertTrue(server.isRunning());
      assertTrue(server.getUrl(), server.getUrl().startsWith("http://127.0.0.1:"));
      String ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

      assertEquals("No token", 401, post(server, ping, null, null) [0]);
      assertEquals("Wrong token", 401, post(server, ping, "Bearer " + TOKEN + "0", null) [0]);
      // HttpURLConnection doesn't send Origin and Host headers of its own, so
      // the requests relayed by a web page are simulated with a socket
      assertEquals("Cross-site request", 403,
          rawPost(server, ping, "127.0.0.1:" + server.getPort(), "https://example.com"));
      assertEquals("DNS rebinding", 403,
          rawPost(server, ping, "evil.example.com:" + server.getPort(), null));
      assertEquals(200, rawPost(server, ping, "localhost:" + server.getPort(), "http://localhost:3000"));

      Object [] response = post(server, ping, "Bearer " + TOKEN, null);
      assertEquals(200, response [0]);
      assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", response [1]);
      assertEquals("Notification accepted", 202, post(server,
          "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "Bearer " + TOKEN, null) [0]);

      HttpURLConnection connection = (HttpURLConnection)new URL(server.getUrl()).openConnection();
      connection.setRequestProperty("Authorization", "Bearer " + TOKEN);
      assertEquals("No GET stream", 405, connection.getResponseCode());
      connection.disconnect();
    } finally {
      server.stop();
      assertFalse(server.isRunning());
      restoreNo3D(previousNo3D);
    }
  }

  @Test
  public void testTokenIsCreatedOnceAndKept() throws Exception {
    File folder = Files.createTempDirectory("sh3d-mcp-test").toFile();
    try {
      String token = HomeMCPServer.getOrCreateToken(folder);
      assertTrue(token, token.matches("[0-9a-f]{64}"));
      assertEquals(token, HomeMCPServer.getOrCreateToken(folder));
      assertFalse(token.equals(HomeMCPServer.createToken()));
    } finally {
      java.util.Deque<File> pending = new java.util.ArrayDeque<File>();
      List<File> all = new java.util.ArrayList<File>();
      pending.add(folder);
      while (!pending.isEmpty()) {
        File file = pending.pop();
        all.add(file);
        File [] children = file.listFiles();
        if (children != null) {
          pending.addAll(java.util.Arrays.asList(children));
        }
      }
      for (int i = all.size() - 1; i >= 0; i--) {
        all.get(i).delete();
      }
    }
  }

  /**
   * Tests the review notes of the user: stored with the home, described with
   * the ids of the items they are pinned to, and updated by the client.
   */
  @Test
  public void testModifyRoomFiltersAndStaleIds() throws Exception {
    Fixture fixture = new Fixture();
    HomeMCPHandler handler = fixture.handler;
    Room room = fixture.home.getRooms().get(0);
    room.setName("Bonus Room");
    Room terrace = new Room(new float [][] {{500, 0}, {900, 0}, {900, 300}, {500, 300}});
    terrace.setName("Terrace");
    terrace.setAreaVisible(false);
    fixture.home.addRoom(terrace);
    String description = text(callTool(handler, "get_home", "{}"));
    assertFalse(description, description.contains("ceiling hidden"));

    // Hidden ceiling, reported by the command and by get_home, and undone in one step
    Map<String, Object> result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"modify_room\",\"id\":\"R1\",\"ceiling_visible\":false}]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertTrue(text(result), text(result).contains("modified 1 item"));
    assertTrue(text(result), text(result).contains("R1 \"Bonus Room\": ceiling hidden, floor visible"));
    assertFalse(room.isCeilingVisible());
    assertTrue(room.isFloorVisible());
    assertTrue(terrace.isCeilingVisible());
    description = text(callTool(handler, "get_home", "{}"));
    assertTrue(description, description.matches("(?s).*R1 Bonus Room: 12 m2[^\n]*, ceiling hidden\n.*"));
    assertEquals(Boolean.FALSE, callTool(handler, "undo", "{}").get("isError"));
    assertTrue(room.isCeilingVisible());
    assertEquals(Boolean.FALSE, callTool(handler, "redo", "{}").get("isError"));
    assertFalse(room.isCeilingVisible());

    // By name, several fields at once, the omitted ones unchanged
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"modify_room\",\"room\":\"terrace\",\"ceiling_visible\":false,\"floor_visible\":false,"
        + "\"area_visible\":true,\"name\":\"Patio\",\"points\":[[500,0],[1000,0],[1000,300],[500,300]]}]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertFalse(terrace.isCeilingVisible());
    assertFalse(terrace.isFloorVisible());
    assertTrue(terrace.isAreaVisible());
    assertEquals("Patio", terrace.getName());
    assertEquals(1000f, terrace.getPoints() [1][0], 1e-3f);
    assertFalse(room.isCeilingVisible());
    assertEquals("Bonus Room", room.getName());
    assertTrue(text(callTool(handler, "get_home", "{}")).contains(", ceiling hidden, floor hidden"));
    callTool(handler, "undo", "{}");
    assertTrue(terrace.isCeilingVisible());
    assertTrue(terrace.isFloorVisible());
    assertFalse(terrace.isAreaVisible());
    assertEquals("Terrace", terrace.getName());
    assertEquals(900f, terrace.getPoints() [1][0], 1e-3f);

    // Wrong requests change nothing and say why
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"modify_room\",\"ids\":[\"R1\",\"R2\"],\"points\":[[0,0],[1,0],[1,1]]},"
        + "{\"action\":\"modify_room\",\"id\":\"R1\"},"
        + "{\"action\":\"modify_room\",\"room\":\"Kitchen\",\"ceiling_visible\":false},"
        + "{\"action\":\"modify_room\",\"id\":\"R1\",\"points\":[[0,0],[1,0]]}]}");
    assertTrue(text(result), text(result).contains("reshapes one room"));
    assertTrue(text(result), text(result).contains("give at least one of"));
    assertTrue(text(result), text(result).contains("no room has that id or name"));
    assertTrue(text(result), text(result).contains("at least 3 points"));
    assertEquals(4, room.getPoints().length);
    assertEquals(400f, room.getPoints() [1][0], 1e-3f);

    // An id whose room was deleted is an error, not a change
    callTool(handler, "get_home", "{}");
    fixture.home.deleteRoom(terrace);
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"modify_room\",\"id\":\"R2\",\"ceiling_visible\":false}]}");
    assertEquals(text(result), Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("No item has the id R2"));
    assertFalse(text(result), text(result).contains("modified"));
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"set_color\",\"ids\":[\"R1\",\"R2\",\"R7\"],\"color\":\"#FF0000\"}]}");
    assertEquals(text(result), Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("modified 1 item"));
    assertTrue(text(result), text(result).contains("No item has the id R2, R7"));
    assertEquals(Integer.valueOf(0xFF0000), room.getFloorColor());

    // Filters of get_home keep the ids of the complete description
    Level ground = new Level("Ground", 0, 12, 250);
    Level attic = new Level("Attic", 262, 12, 250);
    fixture.home.addLevel(ground);
    fixture.home.addLevel(attic);
    room.setLevel(ground);
    fixture.sofa1.setLevel(ground);
    fixture.sofa2.setLevel(attic);
    fixture.sofa2.setName("Attic sofa");
    Wall wall = new Wall(0, 0, 400, 0, 10, 250);
    wall.setLevel(attic);
    fixture.home.addWall(wall);
    description = text(callTool(handler, "get_home", "{\"level\":\"attic\"}"));
    assertTrue(description, description.contains("F2 \"Attic sofa\"") && description.contains("W1 "));
    assertFalse(description, description.contains("F1 ") || description.contains("R1 "));
    assertTrue(description, description.contains("Only the items matching the filter"));
    description = text(callTool(handler, "get_home", "{\"type\":\"rooms\"}"));
    assertTrue(description, description.contains("R1 Bonus Room"));
    assertFalse(description, description.contains("F1 ") || description.contains("W1 "));
    description = text(callTool(handler, "get_home", "{\"type\":\"furniture, walls\",\"name\":\"ATTIC\"}"));
    assertTrue(description, description.contains("F2 \"Attic sofa\""));
    assertFalse(description, description.contains("F1 ") || description.contains("W1 ") || description.contains("R1 "));
    assertEquals(Boolean.TRUE, callTool(handler, "get_home", "{\"level\":\"Cellar\"}").get("isError"));
    assertEquals(Boolean.TRUE, callTool(handler, "get_home", "{\"type\":\"lamps\"}").get("isError"));
    // The ids of a filtered description are the ones commands take
    result = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"move\",\"id\":\"F2\",\"dx\":10}]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(310f, fixture.sofa2.getX(), 1e-3f);

    // A piece tilted on a slope is set upright again, and undo tilts it back
    fixture.sofa2.setPitch((float)Math.toRadians(20));
    assertTrue(text(callTool(handler, "get_home", "{}")).contains("pitch 20 deg"));
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"rotate\",\"id\":\"F2\",\"pitch\":0,\"roll\":0}]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(0f, fixture.sofa2.getPitch(), 1e-6f);
    callTool(handler, "undo", "{}");
    assertEquals(Math.toRadians(20), fixture.sofa2.getPitch(), 1e-5);
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"rotate\",\"id\":\"F2\",\"roll\":15,\"angle\":90}]}");
    assertEquals(Math.toRadians(15), fixture.sofa2.getRoll(), 1e-5);
    assertEquals(0f, fixture.sofa2.getPitch(), 1e-6f);
    assertEquals(Math.toRadians(90), fixture.sofa2.getAngle(), 1e-5);

    // Textures with the same name are chosen by category or catalog id, and the reply names the one applied
    Content image = new URLContent(new java.io.File("dummy.png").toURI().toURL());
    TexturesCategory nature = new TexturesCategory("Nature");
    TexturesCategory sport = new TexturesCategory("Sport");
    fixture.preferences.getTexturesCatalog().add(nature, new CatalogTexture("lawn#1", "Zzmoss", image, 100, 100, null));
    fixture.preferences.getTexturesCatalog().add(sport, new CatalogTexture("pitch#2", "Zzmoss", image, 200, 200, null));
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"set_texture\",\"id\":\"R1\",\"texture\":\"Zzmoss\"}]}");
    assertTrue(text(result), text(result).contains("Applied the texture \"Zzmoss\" (Nature) to 1 item"));
    assertTrue(text(result), text(result).contains("2 textures are named \"Zzmoss\"")
        && text(result).contains("(Sport)"));
    assertEquals(100f, room.getFloorTexture().getWidth(), 1e-3f);
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"set_texture\",\"id\":\"R1\",\"texture\":\"Zzmoss\",\"category\":\"sport\"}]}");
    assertTrue(text(result), text(result).contains("Applied the texture \"Zzmoss\" (Sport) to 1 item"));
    assertFalse(text(result), text(result).contains("textures are named"));
    assertEquals(200f, room.getFloorTexture().getWidth(), 1e-3f);
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"set_texture\",\"id\":\"R1\",\"catalog_id\":\"lawn#1\",\"part\":\"ceiling\"}]}");
    assertTrue(text(result), text(result).contains("Applied the texture \"Zzmoss\" (Nature)"));
    assertEquals(100f, room.getCeilingTexture().getWidth(), 1e-3f);
    result = callTool(handler, "apply_commands", "{\"commands\":["
        + "{\"action\":\"set_texture\",\"id\":\"R1\",\"texture\":\"Zzmoss\",\"category\":\"Kitchen\"},"
        + "{\"action\":\"set_texture\",\"id\":\"R1\",\"catalog_id\":\"none\"}]}");
    assertTrue(text(result), text(result).contains("no texture \"Zzmoss\" in the category \"Kitchen\""));
    assertTrue(text(result), text(result).contains("no texture has the catalog id \"none\""));
    assertEquals(200f, room.getFloorTexture().getWidth(), 1e-3f);
  }

  @Test
  public void testReviewNotes() throws Exception {
    Fixture fixture = new Fixture();
    Wall wall = new Wall(0, 0, 400, 0, 10, 250);
    fixture.home.addWall(wall);
    assertTrue(text(callTool(fixture.handler, "get_notes", "{}")).contains("no review note"));

    String sofaKey = com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.getKey(fixture.sofa2);
    String wallKey = com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.getKey(wall);
    String roomKey = com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.getKey(fixture.home.getRooms().get(0));
    // As the viewer posts them, with fields a note doesn't have
    assertEquals(4, com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.setJSON(fixture.home, "["
        + "{\"id\":\"n1\",\"text\":\"add an outlet here\",\"status\":\"open\",\"target\":\"wall\",\"key\":\"" + wallKey + "\",\"x\":120.26,\"y\":4,\"z\":30,\"evil\":{\"a\":1}},"
        + "{\"id\":\"n2\",\"text\":\"fix this model\",\"target\":\"furniture\",\"name\":\"Sofa\",\"key\":\"" + sofaKey + "\",\"x\":300,\"y\":10,\"z\":45},"
        + "{\"id\":\"n3\",\"text\":\"darker floor\",\"target\":\"room\",\"key\":\"" + roomKey + "\",\"x\":200,\"y\":150,\"z\":0},"
        + "{\"id\":\"bad id!\",\"text\":\"gone\",\"target\":\"furniture\",\"name\":\"Chair\",\"key\":\"missing\",\"x\":1,\"y\":2,\"z\":3},"
        + "{\"id\":\"n9\",\"text\":\"   \"}, 12]"));
    assertTrue(fixture.home.isModified());
    assertFalse(fixture.home.getProperty(com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.NOTES_PROPERTY).contains("evil"));

    String notes = text(callTool(fixture.handler, "get_notes", "{}"));
    assertTrue(notes, notes.contains("Review notes of the user (4)"));
    assertTrue(notes, notes.contains("- n1 [open] \"add an outlet here\" - on wall W1 (from 0,0 to 400,0), 120 cm from its start, "
        + "at x 120 y 4 cm, 30 cm above the floor"));
    assertTrue(notes, notes.contains("- n2 [open] \"fix this model\" - on furniture F2 \"Sofa\", at x 300 y 10 cm"));
    assertTrue(notes, notes.contains("- n3 [open] \"darker floor\" - on room R1"));
    assertTrue(notes, notes.contains("- n4 [open] \"gone\" - on furniture \"Chair\" (no longer in the home)"));
    // The ids of the notes are the ones commands take
    assertTrue(text(callTool(fixture.handler, "apply_commands",
        "{\"commands\":[{\"action\":\"move\",\"id\":\"F2\",\"dx\":50}]}")).contains("modified 1 item"));
    assertEquals(350, fixture.sofa2.getX(), 0.01f);
    // The description of the home tells the assistant about them too
    assertTrue(text(callTool(fixture.handler, "get_home", "{}")).contains("add an outlet here"));

    // Done with a reply, then left out of the open notes
    String updated = text(callTool(fixture.handler, "update_note",
        "{\"id\":\"n1\",\"status\":\"done\",\"reply\":\"Outlet added as a label\"}"));
    assertTrue(updated, updated.contains("Note n1 updated; 3 still open"));
    notes = text(callTool(fixture.handler, "get_notes", "{}"));
    assertFalse(notes, notes.contains("n1 "));
    notes = text(callTool(fixture.handler, "get_notes", "{\"all\":true}"));
    assertTrue(notes, notes.contains("- n1 [done] \"add an outlet here\""));
    assertTrue(notes, notes.contains("reply: Outlet added as a label"));
    assertTrue(text(callTool(fixture.handler, "update_note", "{\"id\":\"n4\",\"status\":\"deleted\"}")).contains("2 still open"));
    assertEquals(3, com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.getNotes(fixture.home).size());
    assertEquals(Boolean.TRUE, callTool(fixture.handler, "update_note", "{\"id\":\"n77\",\"status\":\"done\"}").get("isError"));

    // A read-only home lets the notes be read, not changed
    fixture.handler.setEditingAllowed(false);
    assertTrue(text(callTool(fixture.handler, "get_notes", "{}")).contains("fix this model"));
    assertEquals(Boolean.TRUE, callTool(fixture.handler, "update_note", "{\"id\":\"n2\",\"status\":\"done\"}").get("isError"));
    assertEquals(2, com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.getOpenCount(fixture.home));

    // What isn't a list of notes is refused
    try {
      com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.setJSON(fixture.home, "{\"text\":\"x\"}");
      org.junit.Assert.fail("Not an array");
    } catch (IllegalArgumentException ex) {
      // Expected
    }
    com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.setJSON(fixture.home, "[]");
    assertNull(fixture.home.getProperty(com.eteks.sweethome3d.viewcontroller.HomeReviewNotes.NOTES_PROPERTY));
  }

  /**
   * Posts <code>body</code> and returns the status code and the response body.
   */
  private static Object [] post(HomeMCPServer server, String body, String authorization,
                                String origin) throws Exception {
    HttpURLConnection connection = (HttpURLConnection)new URL(server.getUrl()).openConnection();
    try {
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept", "application/json, text/event-stream");
      if (authorization != null) {
        connection.setRequestProperty("Authorization", authorization);
      }
      if (origin != null) {
        connection.setRequestProperty("Origin", origin);
      }
      OutputStream out = connection.getOutputStream();
      out.write(body.getBytes("UTF-8"));
      out.close();
      int status = connection.getResponseCode();
      InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      if (in != null) {
        byte [] buffer = new byte [4096];
        int count;
        while ((count = in.read(buffer)) > 0) {
          bytes.write(buffer, 0, count);
        }
        in.close();
      }
      return new Object [] {Integer.valueOf(status), new String(bytes.toByteArray(), "UTF-8")};
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Posts <code>body</code> with the valid token and the given <code>Host</code>
   * and <code>Origin</code> headers, and returns the status code.
   */
  private static int rawPost(HomeMCPServer server, String body, String host, String origin) throws Exception {
    Socket socket = new Socket("127.0.0.1", server.getPort());
    try {
      byte [] data = body.getBytes("UTF-8");
      String request = "POST /mcp HTTP/1.1\r\nHost: " + host + "\r\n"
          + (origin != null ? "Origin: " + origin + "\r\n" : "")
          + "Authorization: Bearer " + TOKEN + "\r\nContent-Type: application/json\r\n"
          + "Content-Length: " + data.length + "\r\nConnection: close\r\n\r\n";
      OutputStream out = socket.getOutputStream();
      out.write(request.getBytes("ISO-8859-1"));
      out.write(data);
      out.flush();
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
      String statusLine = reader.readLine();
      return Integer.parseInt(statusLine.split(" ") [1]);
    } finally {
      socket.close();
    }
  }

  private static void restoreNo3D(String previousNo3D) {
    if (previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
    }
  }

  /**
   * Tests the program MCP clients launch to reach the server: forwarding,
   * answers given while no server runs, and its registration in Claude Desktop.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testBridge() throws Exception {
    File folder = java.nio.file.Files.createTempDirectory("sh3d-mcp-bridge").toFile();
    try {
      String messages = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}\n"
          + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n\n"
          + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n"
          + "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_home\",\"arguments\":{}}}\n";
      // The programs started before a request to stop see it, the ones started later don't
      long startTime = System.currentTimeMillis();
      assertFalse(com.eteks.sweethome3d.swing.HomeMCPBridge.isStopRequested(folder, startTime));
      com.eteks.sweethome3d.swing.HomeMCPBridge.requestStop(folder);
      assertTrue(com.eteks.sweethome3d.swing.HomeMCPBridge.isStopRequested(folder, startTime));
      assertFalse(com.eteks.sweethome3d.swing.HomeMCPBridge.isStopRequested(folder, System.currentTimeMillis() + 60000));
      assertTrue(new File(folder, "mcp-bridge-stop").delete());
      // No server and no token yet: the bridge answers the handshake itself
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      com.eteks.sweethome3d.swing.HomeMCPBridge.run(new java.io.ByteArrayInputStream(messages.getBytes("UTF-8")), out, folder);
      String [] lines = out.toString("UTF-8").split("\n");
      assertEquals("One line per request, none for the notification", 3, lines.length);
      Map<String, Object> result = (Map<String, Object>)((Map<String, Object>)JSONParser.parse(lines [0])).get("result");
      assertEquals("2025-06-18", result.get("protocolVersion"));
      result = (Map<String, Object>)((Map<String, Object>)JSONParser.parse(lines [1])).get("result");
      assertEquals(35, ((List<Object>)result.get("tools")).size());
      result = (Map<String, Object>)((Map<String, Object>)JSONParser.parse(lines [2])).get("result");
      assertEquals(Boolean.TRUE, result.get("isError"));
      assertTrue(lines [2], lines [2].contains("Start server"));
      assertFalse(new File(folder, "mcp-token").exists());

      // With a server recorded in the folder, on whatever port it got
      final Home home = new Home();
      home.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
      HomeMCPHandler handler = new HomeMCPHandler(home, AssistantCannedProtocolTest.createCatalogPreferences(), null, "1.2.3");
      HomeMCPServer server = new HomeMCPServer(handler, HomeMCPServer.getOrCreateToken(folder), 0);
      server.start();
      try {
        assertEquals(-1, HomeMCPServer.readPort(folder));
        HomeMCPServer.writePort(folder, server.getPort());
        assertEquals(server.getPort(), HomeMCPServer.readPort(folder));
        out = new java.io.ByteArrayOutputStream();
        com.eteks.sweethome3d.swing.HomeMCPBridge.run(new java.io.ByteArrayInputStream(messages.getBytes("UTF-8")), out, folder);
        lines = out.toString("UTF-8").split("\n");
        assertEquals(3, lines.length);
        Map<String, Object> serverInfo = (Map<String, Object>)((Map<String, Object>)
            ((Map<String, Object>)JSONParser.parse(lines [0])).get("result")).get("serverInfo");
        assertEquals("Answered by the server", "1.2.3", serverInfo.get("version"));
        result = (Map<String, Object>)((Map<String, Object>)JSONParser.parse(lines [2])).get("result");
        assertEquals(lines [2], Boolean.FALSE, result.get("isError"));
        assertTrue(lines [2], lines [2].contains("12 m2") || lines [2].contains("12.0 m2") || lines [2].contains("Rooms"));
        HomeMCPServer.clearPort(folder, server.getPort() + 1);
        assertEquals("Only the server that recorded a port clears it", server.getPort(), HomeMCPServer.readPort(folder));
        HomeMCPServer.clearPort(folder, server.getPort());
        assertEquals(-1, HomeMCPServer.readPort(folder));
      } finally {
        server.stop();
      }

      assertFalse(HomeMCPServer.isAutoStart(folder));
      HomeMCPServer.setAutoStart(folder, true);
      assertTrue(HomeMCPServer.isAutoStart(folder));
      HomeMCPServer.setAutoStart(folder, false);
      assertFalse(HomeMCPServer.isAutoStart(folder));

      // The command holds neither the token nor a port
      List<String> command = com.eteks.sweethome3d.swing.HomeMCPBridge.getCommand(folder);
      assertTrue(command.toString(), command.contains("--folder") && command.contains(folder.toPath().toRealPath().toString()));
      assertFalse(command.toString().contains(HomeMCPServer.readToken(folder)));
      assertTrue(com.eteks.sweethome3d.swing.HomeMCPBridge.getClaudeCodeCommandLine(folder)
          .startsWith("claude mcp add --scope user sweethome3d -- "));

      // Registration in Claude Desktop keeps the other settings
      String config = com.eteks.sweethome3d.swing.HomeMCPBridge.mergeClaudeDesktopConfig(
          "\uFEFF{\"theme\":\"dark\",\"scale\":1.5,\"mcpServers\":{\"other\":{\"command\":\"npx\",\"args\":[\"x\"]},"
          + "\"sweethome3d\":{\"command\":\"old\"}}}", command);
      Map<String, Object> root = (Map<String, Object>)JSONParser.parse(config);
      assertEquals("dark", root.get("theme"));
      assertEquals(1.5, ((Number)root.get("scale")).doubleValue(), 0);
      Map<String, Object> servers = (Map<String, Object>)root.get("mcpServers");
      assertEquals("npx", ((Map<String, Object>)servers.get("other")).get("command"));
      Map<String, Object> entry = (Map<String, Object>)servers.get("sweethome3d");
      assertEquals(command.get(0), entry.get("command"));
      assertEquals(command.subList(1, command.size()), entry.get("args"));
      root = (Map<String, Object>)JSONParser.parse(
          com.eteks.sweethome3d.swing.HomeMCPBridge.mergeClaudeDesktopConfig(null, command));
      assertNotNull(((Map<String, Object>)root.get("mcpServers")).get("sweethome3d"));
      // Under Windows, Claude Desktop keeps its settings in one of several folders
      File local = new File(folder, "Local");
      File roaming = new File(folder, "Roaming");
      File dataFolder = new File(local, "Claude-Data" + File.separator + "claude_pzs8sxrjxfjjc");
      assertTrue(dataFolder.mkdirs() && new File(roaming, "Claude").mkdirs());
      File dataConfig = new File(dataFolder, "claude_desktop_config.json");
      Files.write(dataConfig.toPath(), "{\"preferences\":{\"sidebarMode\":\"chat\"}}".getBytes("UTF-8"));
      List<File> configFiles = com.eteks.sweethome3d.swing.HomeMCPBridge.getClaudeDesktopConfigFiles(
          folder.getPath(), local.getPath(), roaming.getPath(), null, true, false);
      assertEquals(java.util.Arrays.asList(dataConfig, new File(new File(roaming, "Claude"), "claude_desktop_config.json")),
          configFiles);
      assertEquals(configFiles, com.eteks.sweethome3d.swing.HomeMCPBridge.installInClaudeDesktop(folder, configFiles));
      for (File configFile : configFiles) {
        root = (Map<String, Object>)JSONParser.parse(new String(Files.readAllBytes(configFile.toPath()), "UTF-8"));
        assertNotNull(configFile.getPath(), ((Map<String, Object>)root.get("mcpServers")).get("sweethome3d"));
      }
      root = (Map<String, Object>)JSONParser.parse(new String(Files.readAllBytes(dataConfig.toPath()), "UTF-8"));
      assertEquals("chat", ((Map<String, Object>)root.get("preferences")).get("sidebarMode"));
      assertTrue(new File(dataConfig.getPath() + ".before-sweethome3d").isFile());
      assertTrue(com.eteks.sweethome3d.swing.HomeMCPBridge.getClaudeDesktopConfigFiles(
          folder.getPath(), new File(folder, "none").getPath(), new File(folder, "none").getPath(), null, true, false).isEmpty());

      try {
        com.eteks.sweethome3d.swing.HomeMCPBridge.mergeClaudeDesktopConfig("{broken", command);
        org.junit.Assert.fail("A file that isn't JSON must be left alone");
      } catch (java.io.IOException ex) {
        assertTrue(ex.getMessage().contains("left unchanged"));
      }
    } finally {
      java.util.Deque<File> pending = new java.util.ArrayDeque<File>();
      List<File> all = new java.util.ArrayList<File>();
      pending.add(folder);
      while (!pending.isEmpty()) {
        File file = pending.pop();
        all.add(file);
        File [] children = file.listFiles();
        if (children != null) {
          pending.addAll(java.util.Arrays.asList(children));
        }
      }
      for (int i = all.size() - 1; i >= 0; i--) {
        all.get(i).delete();
      }
    }
  }
}
