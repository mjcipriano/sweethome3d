/*
 * HomeMCPBridge.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.swing;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.tools.OperatingSystem;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * A program that MCP clients such as Claude Desktop or Claude Code launch to
 * reach the {@linkplain HomeMCPServer MCP server} of the home open in Sweet Home 3D.
 * It speaks MCP on its standard input and output (one JSON-RPC message per line)
 * and forwards the messages to the server, whose port and token it reads in the
 * application folder: the client configuration contains no secret and stays valid
 * whatever port the server gets. While no server runs, it answers the handshake
 * and the list of tools itself, and tool calls explain how to start the server.
 * @author Sweet Home 3D
 */
public class HomeMCPBridge {
  /** The name of this server in the configuration of clients. */
  public static final String SERVER_NAME = "sweethome3d";
  /** The name of the launcher of this program in packaged applications. */
  public static final String LAUNCHER_NAME = "sweethome3d-mcp";

  private static final String FOLDER_OPTION = "--folder";
  private static final int    CONNECT_TIMEOUT = 3000;
  // Rendering a picture of a large home takes a while
  private static final int    READ_TIMEOUT = 15 * 60 * 1000;

  private HomeMCPBridge() {
    // Program and utility class
  }

  public static void main(String [] args) throws IOException {
    File folder = null;
    for (int i = 0; i < args.length - 1; i++) {
      if (FOLDER_OPTION.equals(args [i])) {
        folder = new File(args [i + 1]);
      }
    }
    if (folder == null) {
      folder = OperatingSystem.getDefaultApplicationFolder();
    }
    // The standard output carries the protocol only
    OutputStream out = new FileOutputStream(java.io.FileDescriptor.out);
    run(System.in, out, folder);
  }

  /**
   * Forwards the messages read on <code>in</code> to the server recorded in
   * <code>folder</code> and writes its answers on <code>out</code>, until <code>in</code> ends.
   */
  public static void run(InputStream in, OutputStream out, File folder) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    for (String line; (line = reader.readLine()) != null; ) {
      if (line.trim().length() == 0) {
        continue;
      }
      String response = forward(line, folder);
      if (response != null) {
        // One message per line
        out.write(response.replace('\n', ' ').replace('\r', ' ').getBytes("UTF-8"));
        out.write('\n');
        out.flush();
      }
    }
  }

  /**
   * Returns the answer of the server to <code>message</code>, or the answer
   * given in its place if it can't be reached.
   */
  static String forward(String message, File folder) {
    String token = HomeMCPServer.readToken(folder);
    int port = HomeMCPServer.readPort(folder);
    String reason = "Sweet Home 3D isn't sharing a home: in Sweet Home 3D, open the home and choose "
        + "Help > Claude MCP server..., then Start server. Then try again.";
    if (token != null) {
      try {
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:"
            + (port > 0 ? port : HomeMCPServer.getConfiguredPort()) + HomeMCPServer.ENDPOINT_PATH).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        OutputStream body = connection.getOutputStream();
        try {
          body.write(message.getBytes("UTF-8"));
        } finally {
          body.close();
        }
        int status = connection.getResponseCode();
        if (status == 200) {
          InputStream response = connection.getInputStream();
          try {
            return new String(readFully(response), "UTF-8");
          } finally {
            response.close();
          }
        } else if (status == 202 || status == 204) {
          return null;
        }
        reason = "The server of Sweet Home 3D refused the request (HTTP " + status + "). "
            + "Stop and start it again from Help > Claude MCP server... in Sweet Home 3D.";
      } catch (IOException ex) {
        // No server listens: answered below
      }
    }
    return HomeMCPHandler.handleOffline(message,
        System.getProperty("com.eteks.sweethome3d.applicationVersion", ""), reason);
  }

  private static byte [] readFully(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte [] buffer = new byte [8192];
    for (int count; (count = in.read(buffer)) > 0; ) {
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }

  // ---- Client configuration ----

  /**
   * Returns the command that launches this program for the given application
   * <code>folder</code>: the launcher installed with the application if there's one,
   * otherwise the Java runtime and the classes of this application.
   */
  public static List<String> getCommand(File folder) {
    List<String> command = new ArrayList<String>();
    String applicationPath = System.getProperty("jpackage.app-path");
    File launcher = null;
    if (applicationPath != null) {
      launcher = new File(new File(applicationPath).getParentFile(),
          LAUNCHER_NAME + (OperatingSystem.isWindows() ? ".exe" : ""));
    }
    if (launcher != null && launcher.isFile()) {
      command.add(launcher.getAbsolutePath());
    } else {
      command.add(new File(new File(System.getProperty("java.home"), "bin"),
          OperatingSystem.isWindows() ? "java.exe" : "java").getAbsolutePath());
      command.add("-Djava.awt.headless=true");
      command.add("-cp");
      command.add(getClassPath());
      command.add(HomeMCPBridge.class.getName());
    }
    command.add(FOLDER_OPTION);
    command.add(folder.getAbsolutePath());
    return command;
  }

  private static String getClassPath() {
    try {
      java.security.CodeSource source = HomeMCPBridge.class.getProtectionDomain().getCodeSource();
      if (source != null && source.getLocation() != null && "file".equals(source.getLocation().getProtocol())) {
        return new File(source.getLocation().toURI()).getAbsolutePath();
      }
    } catch (Exception ex) {
      // Use the class path below
    }
    // The classes of the application are in the first entry of the class path
    String classPath = System.getProperty("java.class.path", "");
    int separator = classPath.indexOf(File.pathSeparatorChar);
    return new File(separator > 0 ? classPath.substring(0, separator) : classPath).getAbsolutePath();
  }

  /**
   * Returns the command line that registers this program in Claude Code.
   */
  public static String getClaudeCodeCommandLine(File folder) {
    StringBuilder line = new StringBuilder("claude mcp add --scope user " + SERVER_NAME + " --");
    for (String argument : getCommand(folder)) {
      line.append(' ').append(argument.indexOf(' ') >= 0 ? '"' + argument + '"' : argument);
    }
    return line.toString();
  }

  /**
   * Registers this program in Claude Code with its <code>claude</code> command.
   * @throws IOException if the command isn't installed or fails
   */
  public static void installInClaudeCode(File folder) throws IOException {
    // An entry with the same name would make the registration fail
    runClaude(Arrays.asList("mcp", "remove", "--scope", "user", SERVER_NAME));
    List<String> arguments = new ArrayList<String>(Arrays.asList("mcp", "add", "--scope", "user", SERVER_NAME, "--"));
    arguments.addAll(getCommand(folder));
    int status = runClaude(arguments);
    if (status != 0) {
      throw new IOException("the claude command failed (exit code " + status + ")");
    }
  }

  private static int runClaude(List<String> arguments) throws IOException {
    List<String> command = new ArrayList<String>();
    if (OperatingSystem.isWindows()) {
      // claude is a command script under Windows
      command.addAll(Arrays.asList("cmd.exe", "/c", "claude"));
    } else {
      command.add("claude");
    }
    command.addAll(arguments);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    process.getOutputStream().close();
    readFully(process.getInputStream());
    try {
      return process.waitFor();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted");
    }
  }

  /**
   * Returns the configuration file of Claude Desktop, existing or not, or
   * <code>null</code> if Claude Desktop doesn't seem to be installed.
   */
  public static File getClaudeDesktopConfigFile() {
    String home = System.getProperty("user.home");
    List<File> folders = new ArrayList<File>();
    if (OperatingSystem.isWindows()) {
      // Installed from the store, Claude Desktop keeps its settings in its package folder
      String localAppData = System.getenv("LOCALAPPDATA");
      File [] packages = localAppData != null ? new File(localAppData, "Packages").listFiles() : null;
      if (packages != null) {
        for (File packageFolder : packages) {
          if (packageFolder.getName().startsWith("Claude_")) {
            folders.add(new File(packageFolder, "LocalCache" + File.separator + "Roaming" + File.separator + "Claude"));
          }
        }
      }
      String appData = System.getenv("APPDATA");
      folders.add(new File(appData != null ? appData : home + File.separator + "AppData" + File.separator + "Roaming", "Claude"));
    } else if (OperatingSystem.isMacOSX()) {
      folders.add(new File(home, "Library/Application Support/Claude"));
    } else {
      String configHome = System.getenv("XDG_CONFIG_HOME");
      folders.add(new File(configHome != null && configHome.length() > 0 ? configHome : home + "/.config", "Claude"));
    }
    for (File folder : folders) {
      if (folder.isDirectory()) {
        return new File(folder, "claude_desktop_config.json");
      }
    }
    return null;
  }

  /**
   * Registers this program in the configuration file of Claude Desktop, keeping
   * its other settings and a copy of the previous file.
   * @return the modified file
   * @throws IOException if Claude Desktop isn't installed or its configuration can't be read
   */
  public static File installInClaudeDesktop(File folder) throws IOException {
    File configFile = getClaudeDesktopConfigFile();
    if (configFile == null) {
      throw new IOException("Claude Desktop doesn't seem to be installed for this user");
    }
    String config = null;
    if (configFile.isFile()) {
      InputStream in = new FileInputStream(configFile);
      try {
        config = new String(readFully(in), "UTF-8");
      } finally {
        in.close();
      }
      write(new File(configFile.getPath() + ".before-sweethome3d"), config);
    }
    write(configFile, mergeClaudeDesktopConfig(config, getCommand(folder)));
    return configFile;
  }

  private static void write(File file, String text) throws IOException {
    OutputStream out = new FileOutputStream(file);
    try {
      out.write(text.getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }

  /**
   * Returns the configuration of Claude Desktop <code>config</code> (which may be
   * <code>null</code> or empty) with the entry that launches <code>command</code>.
   * @throws IOException if <code>config</code> isn't a JSON object
   */
  @SuppressWarnings("unchecked")
  public static String mergeClaudeDesktopConfig(String config, List<String> command) throws IOException {
    Map<String, Object> root;
    if (config == null || config.trim().length() == 0) {
      root = new LinkedHashMap<String, Object>();
    } else {
      Object parsed;
      try {
        // Without the byte order mark some editors add
        parsed = JSONParser.parse(config.charAt(0) == (char)0xFEFF ? config.substring(1) : config);
      } catch (RuntimeException ex) {
        parsed = null;
      }
      if (!(parsed instanceof Map)) {
        throw new IOException("the configuration file of Claude Desktop isn't valid JSON; it was left unchanged");
      }
      root = (Map<String, Object>)parsed;
    }
    Map<String, Object> servers = root.get("mcpServers") instanceof Map
        ? (Map<String, Object>)root.get("mcpServers")
        : new LinkedHashMap<String, Object>();
    Map<String, Object> server = new LinkedHashMap<String, Object>();
    server.put("command", command.get(0));
    server.put("args", new ArrayList<Object>(command.subList(1, command.size())));
    servers.put(SERVER_NAME, server);
    root.put("mcpServers", servers);
    StringBuilder json = new StringBuilder();
    appendIndented(json, root, "");
    return json.append('\n').toString();
  }

  private static void appendIndented(StringBuilder json, Object value, String indent) {
    if (value instanceof Map && !((Map<?, ?>)value).isEmpty()) {
      json.append("{\n");
      int i = 0;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)value).entrySet()) {
        json.append(indent).append("  ").append(JSONParser.toJSONString(String.valueOf(entry.getKey()))).append(": ");
        appendIndented(json, entry.getValue(), indent + "  ");
        json.append(++i < ((Map<?, ?>)value).size() ? ",\n" : "\n");
      }
      json.append(indent).append('}');
    } else if (value instanceof List && !((List<?>)value).isEmpty()) {
      json.append("[\n");
      int i = 0;
      for (Object item : (List<?>)value) {
        json.append(indent).append("  ");
        appendIndented(json, item, indent + "  ");
        json.append(++i < ((List<?>)value).size() ? ",\n" : "\n");
      }
      json.append(indent).append(']');
    } else {
      json.append(JSONParser.toJSON(value));
    }
  }
}
