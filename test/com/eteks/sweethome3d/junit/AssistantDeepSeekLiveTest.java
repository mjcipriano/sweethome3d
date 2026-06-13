package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantClient;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.ChatMessage;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.Provider;
import com.eteks.sweethome3d.viewcontroller.AssistantCommand;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * End-to-end tests of the assistant protocol against the real DeepSeek API
 * (an OpenAI-compatible provider). They are opt-in so the regular suites stay
 * offline and deterministic:
 * <ul>
 * <li>run with <code>make test-assistant-live</code>, or set the system property
 *     <code>sweethome3d.liveAssistantTests=true</code>;</li>
 * <li>the API key is read from the <code>DEEPSEEK_API</code> environment
 *     variable or a <code>DEEPSEEK_API=...</code> line in a <code>.env</code>
 *     file at the repository root.</li>
 * </ul>
 * When disabled or without a key the tests are skipped through JUnit
 * assumptions, with the reason in the test report.
 */
public class AssistantDeepSeekLiveTest {
  private static final String API_URL = "https://api.deepseek.com";
  private static final String MODEL   = "deepseek-chat";

  /**
   * The model must ground furniture names in the supplied catalog vocabulary and
   * produce executable add commands for a multi-part request.
   */
  @Test
  public void testGroundedAddCommands() throws Exception {
    AssistantClient client = createClientOrSkip();
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      home.addRoom(new Room(new float[][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}})); // R1
      UserPreferences preferences = createCatalogPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);

      String systemPrompt = HomeAssistantContext.buildSystemPrompt(home, preferences, true);
      List<ChatMessage> conversation = AssistantClient.newConversation();
      conversation.add(new ChatMessage("user",
          "Add a desk and an office chair to the room R1. Place them anywhere sensible inside it."));
      String reply = client.sendMessage(systemPrompt, conversation);
      System.out.println("[live] DeepSeek add reply: " + reply);

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
      restoreNo3D(previousNo3D);
    }
  }

  /**
   * The model must target an existing item by its stable id and move it the
   * requested distance in the documented coordinate system.
   */
  @Test
  public void testTargetedMoveCommand() throws Exception {
    AssistantClient client = createClientOrSkip();
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = createCatalogPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);

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
      System.out.println("[live] DeepSeek move reply: " + reply);

      List<AssistantCommand> commands = AssistantCommandParser.parse(reply).getCommands();
      assertFalse("Reply contains commands: " + reply, commands.isEmpty());
      executor.execute(commands);
      assertEquals("Sofa moved 50 cm right: " + reply, 150f, sofa.getX(), 1f);
      assertEquals("Sofa kept its y position: " + reply, 200f, sofa.getY(), 1f);
    } finally {
      restoreNo3D(previousNo3D);
    }
  }

  private static UserPreferences createCatalogPreferences() throws Exception {
    // Same synthetic catalog as the canned (offline) variant of these tests
    return AssistantCannedProtocolTest.createCatalogPreferences();
  }

  private static AssistantClient createClientOrSkip() {
    Assume.assumeTrue("Live assistant tests are opt-in: run make test-assistant-live "
        + "or set -Dsweethome3d.liveAssistantTests=true",
        Boolean.getBoolean("sweethome3d.liveAssistantTests")
            || "1".equals(System.getenv("LIVE_ASSISTANT_TESTS")));
    String key = findApiKey();
    Assume.assumeTrue("No DeepSeek key found in the DEEPSEEK_API environment variable "
        + "or a .env file at the repository root", key != null && key.length() > 0);
    return new AssistantClient(Provider.OPENAI_COMPATIBLE, API_URL, key, MODEL);
  }

  private static String findApiKey() {
    String key = System.getenv("DEEPSEEK_API");
    if (key != null && key.trim().length() > 0) {
      return key.trim();
    }
    File envFile = new File(".env");
    if (!envFile.exists()) {
      return null;
    }
    try {
      BufferedReader reader = new BufferedReader(new FileReader(envFile));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.startsWith("DEEPSEEK_API=")) {
            return line.substring("DEEPSEEK_API=".length()).trim();
          }
        }
      } finally {
        reader.close();
      }
    } catch (IOException ex) {
      // Treated as no key below
    }
    return null;
  }

  private static void restoreNo3D(String previousNo3D) {
    if (previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
    }
  }
}
