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

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.HomeMCPServer;
import com.eteks.sweethome3d.swing.SwingViewFactory;
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
      assertEquals(11, tools.size());
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
      assertEquals(text(result), Boolean.FALSE, result.get("isError"));
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
      result = callTool(handler, "search_textures", "{\"query\":\"parquet\"}");
      assertTrue(text(result), text(result).startsWith("Textures:"));
      assertEquals(Boolean.TRUE, callTool(handler, "search_textures", "{}").get("isError"));

      // A picture needs a provider, and comes back as an image content
      assertEquals(Boolean.TRUE, callTool(handler, "get_plan_image", "{}").get("isError"));
      handler.setImageProvider(new HomeMCPHandler.ImageProvider() {
          public byte [] getPlanImage(int maxSize) {
            return new byte [] {(byte)maxSize, 2, 3};
          }

          public byte [] get3DImage(int width, int height) {
            return new byte [] {(byte)(width / 10), (byte)(height / 10)};
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
      File [] files = folder.listFiles();
      if (files != null) {
        for (File file : files) {
          file.delete();
        }
      }
      folder.delete();
    }
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
}
