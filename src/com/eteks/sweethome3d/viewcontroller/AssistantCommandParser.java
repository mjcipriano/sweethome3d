/*
 * AssistantCommandParser.java 10 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses the AI assistant reply into a human-readable message and an optional
 * list of edit {@link AssistantCommand commands}. The model is asked to answer
 * with a JSON object <code>{"reply": "...", "commands": [ ... ]}</code>; this
 * parser is tolerant of replies that wrap that object in prose or Markdown code
 * fences, and falls back to treating the whole reply as plain text.
 * @author Sweet Home 3D
 */
public class AssistantCommandParser {
  private final String                 reply;
  private final List<AssistantCommand> commands;

  private AssistantCommandParser(String reply, List<AssistantCommand> commands) {
    this.reply = reply;
    this.commands = commands;
  }

  /**
   * Returns the message to show to the user.
   */
  public String getReply() {
    return this.reply;
  }

  /**
   * Returns the edit commands to run, possibly empty.
   */
  public List<AssistantCommand> getCommands() {
    return this.commands;
  }

  /**
   * Parses the given model <code>response</code>.
   */
  @SuppressWarnings("unchecked")
  public static AssistantCommandParser parse(String response) {
    if (response == null) {
      return new AssistantCommandParser("", Collections.<AssistantCommand>emptyList());
    }
    String json = extractJSONObject(response);
    if (json != null) {
      try {
        Object parsed = JSONParser.parse(json);
        if (parsed instanceof Map) {
          Map<String, Object> root = (Map<String, Object>)parsed;
          Object commandsValue = root.get("commands");
          if (commandsValue instanceof List) {
            List<AssistantCommand> commands = new ArrayList<AssistantCommand>();
            for (Object item : (List<?>)commandsValue) {
              if (item instanceof Map) {
                Map<String, Object> command = (Map<String, Object>)item;
                Object action = command.get("action");
                if (action != null) {
                  commands.add(new AssistantCommand(action.toString(), command));
                }
              }
            }
            Object replyValue = root.get("reply");
            String replyText = replyValue != null ? replyValue.toString()
                : (commands.isEmpty() ? response : "");
            return new AssistantCommandParser(replyText, commands);
          }
        }
      } catch (IllegalArgumentException ex) {
        // Not valid JSON: treat the whole response as plain text
      }
    }
    return new AssistantCommandParser(response, Collections.<AssistantCommand>emptyList());
  }

  /**
   * Returns the first balanced JSON object found in <code>text</code> that
   * contains a <code>"commands"</code> key, or <code>null</code>.
   */
  private static String extractJSONObject(String text) {
    int searchFrom = 0;
    while (true) {
      int start = text.indexOf('{', searchFrom);
      if (start < 0) {
        return null;
      }
      int end = matchingBrace(text, start);
      if (end > start) {
        String candidate = text.substring(start, end + 1);
        if (candidate.contains("\"commands\"")) {
          return candidate;
        }
        searchFrom = start + 1;
      } else {
        return null;
      }
    }
  }

  /**
   * Returns the index of the brace matching the '{' at <code>start</code>,
   * skipping over strings, or -1.
   */
  private static int matchingBrace(String text, int start) {
    int depth = 0;
    boolean inString = false;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (c == '\\') {
          i++;
        } else if (c == '"') {
          inString = false;
        }
      } else if (c == '"') {
        inString = true;
      } else if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }
}
