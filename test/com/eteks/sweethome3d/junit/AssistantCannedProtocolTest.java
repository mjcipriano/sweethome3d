package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantClient;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.ChatMessage;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.Provider;
import com.eteks.sweethome3d.viewcontroller.AssistantCommand;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.JSONParser;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end tests of the assistant pipeline (request building, HTTP
 * round-trip, reply parsing, command execution) against a local mock server
 * that returns canned replies captured from a real DeepSeek session. They
 * validate exactly what {@link AssistantDeepSeekLiveTest} validates, but run
 * offline with no API key, so they belong to the regular suites; the live test
 * stays an optional, opt-in re-validation that providers still behave this way.
 */
public class AssistantCannedProtocolTest {
  /** Reply captured from deepseek-chat on 2026-06-12 for the grounded-add scenario. */
  private static final String CANNED_ADD_REPLY =
      "{\"reply\": \"Adding a desk and an office chair to the room. I'll place them in a "
      + "typical office arrangement near the center of the room.\", \"commands\": "
      + "[{\"action\": \"add_furniture\", \"name\": \"Desk\", \"x\": 200, \"y\": 200, "
      + "\"angle\": 0, \"width\": 120, \"depth\": 60, \"elevation\": 0}, "
      + "{\"action\": \"add_furniture\", \"name\": \"Office chair\", \"x\": 200, \"y\": 280, "
      + "\"angle\": 0, \"width\": 60, \"depth\": 60, \"elevation\": 0}]}";

  /** Reply captured from deepseek-chat on 2026-06-12 for the targeted-move scenario. */
  private static final String CANNED_MOVE_REPLY =
      "{\"reply\": \"Moving the sofa 50 cm to the right.\", \"commands\": "
      + "[{\"action\": \"move\", \"id\": \"F1\", \"dx\": 50, \"dy\": 0}]}";

  /**
   * A minimal OpenAI-compatible chat-completions server returning a fixed
   * assistant message and recording the request bodies it receives.
   */
  private static final class MockChatServer {
    private final HttpServer   server;
    final List<String>         requestBodies = new ArrayList<String>();

    MockChatServer(final String assistantText) throws IOException {
      this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      this.server.createContext("/chat/completions", new HttpHandler() {
          public void handle(HttpExchange exchange) throws IOException {
            requestBodies.add(readBody(exchange.getRequestBody()));
            String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + JSONParser.toJSONString(assistantText) + "}}]}";
            byte [] data = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            OutputStream body = exchange.getResponseBody();
            body.write(data);
            body.close();
          }
        });
      this.server.start();
    }

    String getUrl() {
      return "http://127.0.0.1:" + this.server.getAddress().getPort();
    }

    void stop() {
      this.server.stop(0);
    }

    private static String readBody(InputStream in) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) >= 0) {
        out.write(buffer, 0, count);
      }
      in.close();
      return new String(out.toByteArray(), "UTF-8");
    }
  }

  /**
   * The canned add reply must ground both pieces to the exact catalog names and
   * place them in the home, and the request sent to the provider must carry the
   * catalog vocabulary the grounding relies on.
   */
  @Test
  public void testGroundedAddCommands() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    MockChatServer mockServer = new MockChatServer(CANNED_ADD_REPLY);
    try {
      Home home = new Home();
      home.addRoom(new Room(new float[][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}})); // R1
      UserPreferences preferences = createCatalogPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);
      AssistantClient client = new AssistantClient(
          Provider.OPENAI_COMPATIBLE, mockServer.getUrl(), "", "deepseek-chat");

      String systemPrompt = HomeAssistantContext.buildSystemPrompt(home, preferences, true);
      List<ChatMessage> conversation = AssistantClient.newConversation();
      conversation.add(new ChatMessage("user",
          "Add a desk and an office chair to the room R1. Place them anywhere sensible inside it."));
      String reply = client.sendMessage(systemPrompt, conversation);

      // The request the provider received contains the role, the protocol and
      // the catalog vocabulary
      assertEquals(1, mockServer.requestBodies.size());
      String request = mockServer.requestBodies.get(0);
      assertTrue(request, request.contains("interior design assistant"));
      assertTrue(request, request.contains("search_catalog"));
      assertTrue(request, request.contains("Office chair"));

      List<AssistantCommand> commands = AssistantCommandParser.parse(reply).getCommands();
      assertFalse("Reply contains commands: " + reply, commands.isEmpty());
      String summary = executor.execute(commands);
      assertNotNull("Commands were applied: " + reply, summary);
      assertTrue("No unmatched furniture names: " + summary,
          !summary.contains("Couldn't find furniture named"));

      boolean deskAdded = false;
      boolean chairAdded = false;
      for (HomePieceOfFurniture piece : home.getFurniture()) {
        if ("Desk".equals(piece.getName())) {
          deskAdded = true;
        } else if ("Office chair".equals(piece.getName())) {
          chairAdded = true;
        }
      }
      assertTrue("Desk added from the catalog: " + summary, deskAdded);
      assertTrue("Office chair added from the catalog: " + summary, chairAdded);
    } finally {
      mockServer.stop();
      restoreNo3D(previousNo3D);
    }
  }

  /**
   * The canned move reply must resolve the F1 id and apply the relative move.
   */
  @Test
  public void testTargetedMoveCommand() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    MockChatServer mockServer = new MockChatServer(CANNED_MOVE_REPLY);
    try {
      Home home = new Home();
      UserPreferences preferences = createCatalogPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);
      AssistantClient client = new AssistantClient(
          Provider.OPENAI_COMPATIBLE, mockServer.getUrl(), "", "deepseek-chat");

      // F1: a sofa at (100, 200)
      HomePieceOfFurniture sofa = new HomePieceOfFurniture(
          executor.findCatalogPiece("Sofa", false));
      sofa.setX(100);
      sofa.setY(200);
      home.addPieceOfFurniture(sofa);

      String systemPrompt = HomeAssistantContext.buildSystemPrompt(home, preferences, true);
      List<ChatMessage> conversation = AssistantClient.newConversation();
      conversation.add(new ChatMessage("user", "Move the sofa 50 cm to the right."));
      String reply = client.sendMessage(systemPrompt, conversation);

      List<AssistantCommand> commands = AssistantCommandParser.parse(reply).getCommands();
      assertFalse("Reply contains commands: " + reply, commands.isEmpty());
      executor.execute(commands);
      assertEquals("Sofa moved 50 cm right", 150f, sofa.getX(), 1e-3f);
      assertEquals("Sofa kept its y position", 200f, sofa.getY(), 1e-3f);
    } finally {
      mockServer.stop();
      restoreNo3D(previousNo3D);
    }
  }

  static UserPreferences createCatalogPreferences() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    Content icon = new URLContent(new File("dummy.png").toURI().toURL());
    Content model = new URLContent(new File("dummy.obj").toURI().toURL());
    FurnitureCategory living = new FurnitureCategory("Living room");
    preferences.getFurnitureCatalog().add(living,
        new CatalogPieceOfFurniture("Sofa", icon, model, 200, 90, 80, true, false));
    preferences.getFurnitureCatalog().add(living,
        new CatalogPieceOfFurniture("Coffee table", icon, model, 110, 60, 45, true, false));
    FurnitureCategory office = new FurnitureCategory("Office");
    preferences.getFurnitureCatalog().add(office,
        new CatalogPieceOfFurniture("Desk", icon, model, 140, 70, 75, true, false));
    preferences.getFurnitureCatalog().add(office,
        new CatalogPieceOfFurniture("Office chair", icon, model, 60, 60, 100, true, false));
    return preferences;
  }

  private static void restoreNo3D(String previousNo3D) {
    if (previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
    }
  }
}
