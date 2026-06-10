package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.eteks.sweethome3d.viewcontroller.AssistantCommand;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;

/**
 * Verifies the assistant reply parser extracts edit commands from a JSON
 * protocol reply (even when wrapped in prose or Markdown fences) and otherwise
 * treats the reply as plain text.
 */
public class AssistantCommandParserTest {
  @Test
  public void testPlainTextReplyHasNoCommands() {
    AssistantCommandParser parsed = AssistantCommandParser.parse("Your home is 120 m2 with 3 rooms.");
    assertTrue(parsed.getCommands().isEmpty());
    assertEquals("Your home is 120 m2 with 3 rooms.", parsed.getReply());
  }

  @Test
  public void testParsesCommandsAndReply() {
    String response = "{\"reply\":\"Added a tree and a room.\",\"commands\":["
        + "{\"action\":\"add_furniture\",\"name\":\"Tree\",\"x\":150,\"y\":-200,\"angle\":90},"
        + "{\"action\":\"add_room\",\"name\":\"Patio\",\"points\":[[0,0],[300,0],[300,300]]}]}";
    AssistantCommandParser parsed = AssistantCommandParser.parse(response);
    assertEquals("Added a tree and a room.", parsed.getReply());
    List<AssistantCommand> commands = parsed.getCommands();
    assertEquals(2, commands.size());

    AssistantCommand tree = commands.get(0);
    assertEquals("add_furniture", tree.getAction());
    assertEquals("Tree", tree.getString("name"));
    assertEquals(150.0, tree.getDouble("x", 0), 1e-6);
    assertEquals(-200.0, tree.getDouble("y", 0), 1e-6);
    assertEquals(90.0, tree.getDouble("angle", 0), 1e-6);

    AssistantCommand room = commands.get(1);
    assertEquals("add_room", room.getAction());
    assertEquals(3, room.getPoints("points").size());
    assertEquals(300f, room.getPoints("points").get(1)[0], 1e-6);
  }

  @Test
  public void testExtractsJsonWrappedInProseAndFences() {
    String response = "Sure! Here is the change:\n```json\n"
        + "{\"reply\":\"Done.\",\"commands\":[{\"action\":\"select\",\"names\":[\"Kitchen\"]}]}\n"
        + "```\nLet me know if you need more.";
    AssistantCommandParser parsed = AssistantCommandParser.parse(response);
    assertEquals("Done.", parsed.getReply());
    assertEquals(1, parsed.getCommands().size());
    assertEquals("select", parsed.getCommands().get(0).getAction());
    assertEquals("Kitchen", parsed.getCommands().get(0).getStringList("names").get(0));
  }
}
