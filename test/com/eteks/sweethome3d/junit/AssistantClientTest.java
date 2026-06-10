package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.eteks.sweethome3d.viewcontroller.AssistantClient;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.ChatMessage;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.Provider;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Verifies the JSON parser and the request/response handling of the AI assistant
 * client for both the OpenAI-compatible and Anthropic protocols, without any
 * network access.
 */
public class AssistantClientTest {
  @Test
  public void testJsonParserRoundTripsAndUnescapes() {
    Object parsed = JSONParser.parse(
        "{\"a\": 1, \"b\": [true, null, \"line1\\nline2\"], \"c\": {\"d\": \"x\\\"y\"}}");
    Map<?, ?> root = (Map<?, ?>)parsed;
    assertEquals(Double.valueOf(1), root.get("a"));
    List<?> b = (List<?>)root.get("b");
    assertEquals(Boolean.TRUE, b.get(0));
    assertEquals(null, b.get(1));
    assertEquals("line1\nline2", b.get(2));
    assertEquals("x\"y", ((Map<?, ?>)root.get("c")).get("d"));
    // Escaping round-trips
    assertEquals("\"a\\\"b\\nc\"", JSONParser.toJSONString("a\"b\nc"));
  }

  @Test
  public void testOpenAiRequestAndResponse() throws Exception {
    AssistantClient client = new AssistantClient(Provider.OPENAI_COMPATIBLE,
        "http://aliendev.local:1234/v1/", "", "qwen");
    assertEquals("http://aliendev.local:1234/v1/chat/completions", client.getEndpoint());

    List<ChatMessage> conversation = AssistantClient.newConversation();
    conversation.add(new ChatMessage("user", "How big is the house?"));
    String body = client.buildRequestBody("You are a design assistant.", conversation);
    assertTrue(body, body.contains("\"model\":\"qwen\""));
    assertTrue(body, body.contains("\"stream\":false"));
    assertTrue(body, body.contains("\"role\":\"system\""));
    assertTrue(body, body.contains("\"role\":\"user\""));
    assertTrue(body, body.contains("How big is the house?"));

    String reply = client.parseResponseText(
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"It is 120 m2.\"}}]}");
    assertEquals("It is 120 m2.", reply);
  }

  @Test
  public void testAnthropicRequestAndResponse() throws Exception {
    AssistantClient client = new AssistantClient(Provider.ANTHROPIC,
        "https://api.anthropic.com/v1", "sk-test", "claude-opus-4-8");
    assertEquals("https://api.anthropic.com/v1/messages", client.getEndpoint());

    List<ChatMessage> conversation = AssistantClient.newConversation();
    conversation.add(new ChatMessage("user", "Hi"));
    String body = client.buildRequestBody("Project context here", conversation);
    // Anthropic carries the system prompt as a top-level field, not a message
    assertTrue(body, body.contains("\"system\":\"Project context here\""));
    assertTrue(body, body.contains("\"max_tokens\":"));
    assertTrue(body, !body.contains("\"role\":\"system\""));

    String reply = client.parseResponseText(
        "{\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}]}");
    assertEquals("Hello!", reply);
  }
}
