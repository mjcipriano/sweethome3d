/*
 * JSONParser.java 10 June 2026
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small dependency-free JSON parser, sufficient to read the responses of the
 * AI assistant providers. Parsing yields {@link Map}, {@link List},
 * {@link String}, {@link Double}, {@link Boolean} and <code>null</code> values.
 * A helper is provided to JSON-escape strings when building requests.
 * @author Sweet Home 3D
 */
public class JSONParser {
  private final String text;
  private int          index;

  private JSONParser(String text) {
    this.text = text;
  }

  /**
   * Parses the given JSON <code>text</code> and returns the corresponding value.
   * @throws IllegalArgumentException if the text isn't valid JSON
   */
  public static Object parse(String text) {
    JSONParser parser = new JSONParser(text);
    parser.skipWhitespace();
    Object value = parser.parseValue();
    parser.skipWhitespace();
    if (parser.index < parser.text.length()) {
      throw new IllegalArgumentException("Unexpected trailing characters at " + parser.index);
    }
    return value;
  }

  /**
   * Returns <code>value</code> escaped and surrounded by double quotes so it can
   * be inserted in a JSON document.
   */
  public static String toJSONString(String value) {
    StringBuilder builder = new StringBuilder(value.length() + 2);
    builder.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' :  builder.append("\\\"");  break;
        case '\\' : builder.append("\\\\");  break;
        case '\n' : builder.append("\\n");   break;
        case '\r' : builder.append("\\r");   break;
        case '\t' : builder.append("\\t");   break;
        case '\b' : builder.append("\\b");   break;
        case '\f' : builder.append("\\f");   break;
        default :
          if (c < 0x20) {
            builder.append(String.format("\\u%04x", (int)c));
          } else {
            builder.append(c);
          }
      }
    }
    builder.append('"');
    return builder.toString();
  }

  private Object parseValue() {
    skipWhitespace();
    if (this.index >= this.text.length()) {
      throw new IllegalArgumentException("Unexpected end of JSON");
    }
    char c = this.text.charAt(this.index);
    switch (c) {
      case '{' : return parseObject();
      case '[' : return parseArray();
      case '"' : return parseString();
      case 't' : case 'f' : return parseBoolean();
      case 'n' : expect("null"); return null;
      default :  return parseNumber();
    }
  }

  private Map<String, Object> parseObject() {
    Map<String, Object> object = new LinkedHashMap<String, Object>();
    this.index++; // {
    skipWhitespace();
    if (currentChar() == '}') {
      this.index++;
      return object;
    }
    while (true) {
      skipWhitespace();
      String key = parseString();
      skipWhitespace();
      if (currentChar() != ':') {
        throw new IllegalArgumentException("Expected ':' at " + this.index);
      }
      this.index++;
      object.put(key, parseValue());
      skipWhitespace();
      char c = currentChar();
      this.index++;
      if (c == '}') {
        return object;
      } else if (c != ',') {
        throw new IllegalArgumentException("Expected ',' or '}' at " + (this.index - 1));
      }
    }
  }

  private List<Object> parseArray() {
    List<Object> array = new ArrayList<Object>();
    this.index++; // [
    skipWhitespace();
    if (currentChar() == ']') {
      this.index++;
      return array;
    }
    while (true) {
      array.add(parseValue());
      skipWhitespace();
      char c = currentChar();
      this.index++;
      if (c == ']') {
        return array;
      } else if (c != ',') {
        throw new IllegalArgumentException("Expected ',' or ']' at " + (this.index - 1));
      }
    }
  }

  private String parseString() {
    if (currentChar() != '"') {
      throw new IllegalArgumentException("Expected '\"' at " + this.index);
    }
    this.index++;
    StringBuilder builder = new StringBuilder();
    while (true) {
      if (this.index >= this.text.length()) {
        throw new IllegalArgumentException("Unterminated string");
      }
      char c = this.text.charAt(this.index++);
      if (c == '"') {
        return builder.toString();
      } else if (c == '\\') {
        char escaped = this.text.charAt(this.index++);
        switch (escaped) {
          case '"' :  builder.append('"');  break;
          case '\\' : builder.append('\\'); break;
          case '/' :  builder.append('/');  break;
          case 'n' :  builder.append('\n'); break;
          case 'r' :  builder.append('\r'); break;
          case 't' :  builder.append('\t'); break;
          case 'b' :  builder.append('\b'); break;
          case 'f' :  builder.append('\f'); break;
          case 'u' :
            builder.append((char)Integer.parseInt(this.text.substring(this.index, this.index + 4), 16));
            this.index += 4;
            break;
          default :
            throw new IllegalArgumentException("Invalid escape \\" + escaped);
        }
      } else {
        builder.append(c);
      }
    }
  }

  private Boolean parseBoolean() {
    if (currentChar() == 't') {
      expect("true");
      return Boolean.TRUE;
    } else {
      expect("false");
      return Boolean.FALSE;
    }
  }

  private Double parseNumber() {
    int start = this.index;
    while (this.index < this.text.length()
        && "+-0123456789.eE".indexOf(this.text.charAt(this.index)) >= 0) {
      this.index++;
    }
    if (this.index == start) {
      throw new IllegalArgumentException("Invalid JSON value at " + start);
    }
    return Double.valueOf(this.text.substring(start, this.index));
  }

  private void expect(String literal) {
    if (!this.text.regionMatches(this.index, literal, 0, literal.length())) {
      throw new IllegalArgumentException("Expected '" + literal + "' at " + this.index);
    }
    this.index += literal.length();
  }

  private char currentChar() {
    if (this.index >= this.text.length()) {
      throw new IllegalArgumentException("Unexpected end of JSON");
    }
    return this.text.charAt(this.index);
  }

  private void skipWhitespace() {
    while (this.index < this.text.length()
        && Character.isWhitespace(this.text.charAt(this.index))) {
      this.index++;
    }
  }
}
