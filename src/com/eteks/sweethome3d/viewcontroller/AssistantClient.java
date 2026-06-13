/*
 * AssistantClient.java 10 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A minimal language-model client for the AI design assistant that talks to
 * either an OpenAI-compatible endpoint (OpenRouter, DeepSeek, LM Studio, Ollama,
 * ...) or the Anthropic API. The provider, endpoint URL, optional API key and
 * model name are supplied by the caller (from the user preferences).
 * @author Sweet Home 3D
 */
public class AssistantClient {
  /**
   * Supported provider protocols.
   */
  public enum Provider {
    /** Chat-completions protocol used by OpenAI, OpenRouter, DeepSeek, LM Studio, Ollama. */
    OPENAI_COMPATIBLE,
    /** Anthropic messages protocol. */
    ANTHROPIC
  }

  /**
   * Receives incremental text as a streamed reply arrives.
   */
  public interface StreamHandler {
    /** Called for each chunk of assistant text as it streams in. */
    void onText(String chunk);
  }

  /**
   * A single message of a conversation (role is "user" or "assistant").
   */
  public static class ChatMessage {
    private final String role;
    private final String content;

    public ChatMessage(String role, String content) {
      this.role = role;
      this.content = content;
    }

    public String getRole() {
      return this.role;
    }

    public String getContent() {
      return this.content;
    }
  }

  /** Low sampling temperature: edit commands need precision, not creativity. */
  private static final String TEMPERATURE = "0.2";

  private final Provider provider;
  private final String   apiUrl;
  private final String   apiKey;
  private final String   model;
  private final int      maxTokens;

  public AssistantClient(Provider provider, String apiUrl, String apiKey, String model) {
    this.provider = provider;
    this.apiUrl = apiUrl != null ? apiUrl.trim() : "";
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.model = model != null ? model.trim() : "";
    // Large enough that a multi-command JSON reply is never truncated mid-object
    this.maxTokens = 4096;
  }

  /**
   * Sends a request to the configured provider and returns the assistant reply.
   * @param systemPrompt instructions and project context for the assistant
   * @param conversation the prior user/assistant messages, oldest first
   * @throws IOException if the request fails or the response can't be read
   */
  public String sendMessage(String systemPrompt, List<ChatMessage> conversation) throws IOException {
    String endpoint = getEndpoint();
    HttpURLConnection connection = (HttpURLConnection)new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(15000);
    // Local models can be slow, so allow a generous read timeout
    connection.setReadTimeout(600000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    if (this.provider == Provider.ANTHROPIC) {
      connection.setRequestProperty("x-api-key", this.apiKey);
      connection.setRequestProperty("anthropic-version", "2023-06-01");
    } else if (this.apiKey.length() > 0) {
      connection.setRequestProperty("Authorization", "Bearer " + this.apiKey);
    }

    byte [] body = buildRequestBody(systemPrompt, conversation).getBytes("UTF-8");
    OutputStream out = connection.getOutputStream();
    try {
      out.write(body);
    } finally {
      out.close();
    }

    int status = connection.getResponseCode();
    String response = readStream(status >= 400
        ? connection.getErrorStream() : connection.getInputStream());
    if (status >= 400) {
      throw new IOException("Assistant request failed (HTTP " + status + "): " + response);
    }
    return parseResponseText(response);
  }

  /**
   * Sends a request asking for a streamed reply, forwarding each text chunk to
   * <code>handler</code> as it arrives, and returns the full accumulated reply.
   * Falls back to the meaning of {@link #sendMessage} if <code>handler</code> is
   * <code>null</code>.
   * @throws IOException if the request fails or the response can't be read
   */
  public String sendMessageStreaming(String systemPrompt, List<ChatMessage> conversation,
                                     StreamHandler handler) throws IOException {
    if (handler == null) {
      return sendMessage(systemPrompt, conversation);
    }
    String endpoint = getEndpoint();
    HttpURLConnection connection = (HttpURLConnection)new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(600000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Accept", "text/event-stream");
    if (this.provider == Provider.ANTHROPIC) {
      connection.setRequestProperty("x-api-key", this.apiKey);
      connection.setRequestProperty("anthropic-version", "2023-06-01");
    } else if (this.apiKey.length() > 0) {
      connection.setRequestProperty("Authorization", "Bearer " + this.apiKey);
    }

    byte [] body = buildRequestBody(systemPrompt, conversation, true).getBytes("UTF-8");
    OutputStream out = connection.getOutputStream();
    try {
      out.write(body);
    } finally {
      out.close();
    }

    int status = connection.getResponseCode();
    if (status >= 400) {
      String error = readStream(connection.getErrorStream());
      throw new IOException("Assistant request failed (HTTP " + status + "): " + error);
    }

    StringBuilder full = new StringBuilder();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("data:")) {
          continue;
        }
        String data = line.substring("data:".length()).trim();
        if (data.length() == 0 || "[DONE]".equals(data)) {
          continue;
        }
        String chunk = parseStreamDelta(this.provider, data);
        if (chunk != null && chunk.length() > 0) {
          full.append(chunk);
          handler.onText(chunk);
        }
      }
    } finally {
      reader.close();
    }
    return full.toString();
  }

  /**
   * Extracts the incremental text from one server-sent-event <code>data:</code>
   * payload, for the given provider, or <code>null</code> if the event carries no
   * text (e.g. a start/stop or usage event).
   */
  public static String parseStreamDelta(Provider provider, String data) {
    Object parsed;
    try {
      parsed = JSONParser.parse(data);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    if (!(parsed instanceof Map)) {
      return null;
    }
    Map<?, ?> root = (Map<?, ?>)parsed;
    if (provider == Provider.ANTHROPIC) {
      // {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
      Object delta = root.get("delta");
      if (delta instanceof Map) {
        Object text = ((Map<?, ?>)delta).get("text");
        if (text != null) {
          return text.toString();
        }
      }
    } else {
      // {"choices":[{"delta":{"content":"..."}}]}
      Object choices = root.get("choices");
      if (choices instanceof List && !((List<?>)choices).isEmpty()) {
        Object first = ((List<?>)choices).get(0);
        if (first instanceof Map) {
          Object delta = ((Map<?, ?>)first).get("delta");
          if (delta instanceof Map) {
            Object content = ((Map<?, ?>)delta).get("content");
            if (content != null) {
              return content.toString();
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns the full endpoint URL of the configured provider.
   */
  public String getEndpoint() {
    String base = this.apiUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (this.provider == Provider.ANTHROPIC) {
      return base + "/messages";
    } else {
      return base + "/chat/completions";
    }
  }

  /**
   * Builds the JSON request body for the configured provider.
   */
  public String buildRequestBody(String systemPrompt, List<ChatMessage> conversation) {
    return buildRequestBody(systemPrompt, conversation, false);
  }

  /**
   * Builds the JSON request body, optionally requesting a streamed response.
   */
  public String buildRequestBody(String systemPrompt, List<ChatMessage> conversation, boolean stream) {
    StringBuilder body = new StringBuilder();
    body.append('{');
    body.append("\"model\":").append(JSONParser.toJSONString(this.model)).append(',');
    if (this.provider == Provider.ANTHROPIC) {
      body.append("\"max_tokens\":").append(this.maxTokens).append(',');
      body.append("\"temperature\":").append(TEMPERATURE).append(',');
      body.append("\"stream\":").append(stream).append(',');
      if (systemPrompt != null) {
        body.append("\"system\":").append(JSONParser.toJSONString(systemPrompt)).append(',');
      }
      body.append("\"messages\":");
      appendMessages(body, null, conversation);
    } else {
      body.append("\"max_tokens\":").append(this.maxTokens).append(',');
      body.append("\"temperature\":").append(TEMPERATURE).append(',');
      body.append("\"stream\":").append(stream).append(',');
      body.append("\"messages\":");
      appendMessages(body, systemPrompt, conversation);
    }
    body.append('}');
    return body.toString();
  }

  private void appendMessages(StringBuilder body, String systemPrompt, List<ChatMessage> conversation) {
    body.append('[');
    boolean first = true;
    if (systemPrompt != null) {
      appendMessage(body, "system", systemPrompt);
      first = false;
    }
    for (ChatMessage message : conversation) {
      if (!first) {
        body.append(',');
      }
      appendMessage(body, message.getRole(), message.getContent());
      first = false;
    }
    body.append(']');
  }

  private void appendMessage(StringBuilder body, String role, String content) {
    body.append("{\"role\":").append(JSONParser.toJSONString(role))
        .append(",\"content\":").append(JSONParser.toJSONString(content)).append('}');
  }

  /**
   * Extracts the assistant text from a provider response body.
   */
  public String parseResponseText(String response) throws IOException {
    try {
      Object parsed = JSONParser.parse(response);
      if (!(parsed instanceof Map)) {
        throw new IOException("Unexpected response: " + response);
      }
      Map<?, ?> root = (Map<?, ?>)parsed;
      if (this.provider == Provider.ANTHROPIC) {
        Object content = root.get("content");
        if (content instanceof List && !((List<?>)content).isEmpty()) {
          Object first = ((List<?>)content).get(0);
          if (first instanceof Map) {
            Object text = ((Map<?, ?>)first).get("text");
            if (text != null) {
              return text.toString();
            }
          }
        }
      } else {
        Object choices = root.get("choices");
        if (choices instanceof List && !((List<?>)choices).isEmpty()) {
          Object first = ((List<?>)choices).get(0);
          if (first instanceof Map) {
            Object message = ((Map<?, ?>)first).get("message");
            if (message instanceof Map) {
              Object content = ((Map<?, ?>)message).get("content");
              if (content != null) {
                return content.toString();
              }
            }
          }
        }
      }
      throw new IOException("No assistant text in response: " + response);
    } catch (IllegalArgumentException ex) {
      throw new IOException("Couldn't parse assistant response: " + response, ex);
    }
  }

  private static String readStream(InputStream in) throws IOException {
    if (in == null) {
      return "";
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) >= 0) {
        out.write(buffer, 0, count);
      }
    } finally {
      in.close();
    }
    return new String(out.toByteArray(), "UTF-8");
  }

  /**
   * Returns an empty modifiable conversation list.
   */
  public static List<ChatMessage> newConversation() {
    return new ArrayList<ChatMessage>();
  }
}
