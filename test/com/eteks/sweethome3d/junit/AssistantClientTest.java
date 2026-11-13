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

  @Test
  public void testStreamRequestAndDeltaParsing() {
    AssistantClient openai = new AssistantClient(Provider.OPENAI_COMPATIBLE,
        "http://local/v1", "", "qwen");
    assertTrue(openai.buildRequestBody(null,
        AssistantClient.newConversation(), true).contains("\"stream\":true"));

    // OpenAI-compatible streaming delta
    assertEquals("Hel", AssistantClient.parseStreamDelta(Provider.OPENAI_COMPATIBLE,
        "{\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}"));
    // A role-only opening delta carries no text
    assertEquals(null, AssistantClient.parseStreamDelta(Provider.OPENAI_COMPATIBLE,
        "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"));

    AssistantClient anthropic = new AssistantClient(Provider.ANTHROPIC,
        "https://api.anthropic.com/v1", "sk-test", "claude-opus-4-8");
    assertTrue(anthropic.buildRequestBody("ctx",
        AssistantClient.newConversation(), true).contains("\"stream\":true"));

    // Anthropic streaming delta
    assertEquals("lo", AssistantClient.parseStreamDelta(Provider.ANTHROPIC,
        "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"lo\"}}"));
    // A message_start event carries no text
    assertEquals(null, AssistantClient.parseStreamDelta(Provider.ANTHROPIC,
        "{\"type\":\"message_start\",\"message\":{\"role\":\"assistant\"}}"));
  }

  /**
   * Truncated escapes and absurd nesting must fail with the documented
   * IllegalArgumentException rather than an index or stack overflow error.
   */
  @Test
  public void testJsonParserRejectsMalformedInputCleanly() {
    StringBuilder deep = new StringBuilder();
    for (int i = 0; i < 100000; i++) {
      deep.append('[');
    }
    String [] malformed = {"{\"reply\":\"x\\u12", "\"abc\\", "{\"a\":", deep.toString(), "1.2.3"};
    for (String text : malformed) {
      try {
        JSONParser.parse(text);
        org.junit.Assert.fail("Expected a failure for " + text.substring(0, Math.min(20, text.length())));
      } catch (IllegalArgumentException ex) {
        // Expected
      }
    }
  }

  private static com.sun.net.httpserver.HttpServer startServer(final String contentType,
                                                               final String body) throws Exception {
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", new com.sun.net.httpserver.HttpHandler() {
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
          java.io.InputStream in = exchange.getRequestBody();
          while (in.read() >= 0) {
          }
          byte [] data = body.getBytes("UTF-8");
          exchange.getResponseHeaders().set("Content-Type", contentType);
          exchange.sendResponseHeaders(200, data.length);
          exchange.getResponseBody().write(data);
          exchange.close();
        }
      });
    server.start();
    return server;
  }

  private static String stream(com.sun.net.httpserver.HttpServer server) throws java.io.IOException {
    AssistantClient client = new AssistantClient(Provider.OPENAI_COMPATIBLE,
        "http://127.0.0.1:" + server.getAddress().getPort(), "", "test-model");
    List<ChatMessage> conversation = AssistantClient.newConversation();
    conversation.add(new ChatMessage("user", "Hello"));
    final StringBuilder streamed = new StringBuilder();
    String reply = client.sendMessageStreaming("system", conversation, new AssistantClient.StreamHandler() {
        public void onText(String chunk) {
          streamed.append(chunk);
        }
      });
    assertEquals(reply, streamed.toString());
    return reply;
  }

  /**
   * A streamed request must cope with servers that answer with events, with a
   * plain JSON body, with an in-band error, or with nothing at all.
   */
  @Test
  public void testStreamingHandlesEventsPlainJsonErrorsAndEmptyReplies() throws Exception {
    com.sun.net.httpserver.HttpServer server = startServer("text/event-stream",
        "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
        + "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\ndata: [DONE]\n\n");
    try {
      assertEquals("Hello", stream(server));
    } finally {
      server.stop(0);
    }

    server = startServer("application/json",
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Plain\"}}]}");
    try {
      assertEquals("Plain", stream(server));
    } finally {
      server.stop(0);
    }

    String [] failures = {
        "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
            + "data: {\"error\":{\"message\":\"overloaded\"}}\n\n",
        "{\"error\":{\"message\":\"overloaded\"}}",
        "data: [DONE]\n\n"};
    for (String body : failures) {
      server = startServer("text/event-stream", body);
      try {
        stream(server);
        org.junit.Assert.fail("Expected an IOException for " + body);
      } catch (java.io.IOException ex) {
        if (body.contains("overloaded")) {
          assertTrue(ex.getMessage(), ex.getMessage().contains("overloaded"));
        }
      } finally {
        server.stop(0);
      }
    }
  }
}
