/*
 * HomeMCPServer.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.swing;

import java.awt.EventQueue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A Model Context Protocol server (Streamable HTTP transport) giving an MCP
 * client such as Claude access to one home. It only listens on the loopback
 * interface of this computer, requires a secret bearer token, and rejects
 * requests coming from web pages (<code>Origin</code> and <code>Host</code>
 * checks), so other computers and browser tabs can't reach the home.
 * @author Sweet Home 3D
 */
public class HomeMCPServer {
  /** The port tried first, so the address configured in the client stays valid. */
  public static final int    DEFAULT_PORT = 47391;
  /** System property overriding {@link #DEFAULT_PORT}. */
  public static final String PORT_PROPERTY = "com.eteks.sweethome3d.mcp.port";
  /** The path of the MCP endpoint. */
  public static final String ENDPOINT_PATH = "/mcp";

  private static final String TOKEN_FILE = "mcp-token";
  private static final int    MAX_REQUEST_LENGTH = 1024 * 1024;

  private final HomeMCPHandler handler;
  private final String         token;
  private final int            preferredPort;
  private HttpServer           server;
  private ExecutorService      executor;

  /**
   * Creates a server answering with <code>handler</code> to the clients that
   * know <code>token</code>.
   * @param preferredPort the port to listen to if it's free, or 0 for any free port
   */
  public HomeMCPServer(HomeMCPHandler handler, String token, int preferredPort) {
    if (token == null || token.length() < 16) {
      throw new IllegalArgumentException("A token of 16 characters at least is required");
    }
    this.handler = handler;
    this.token = token;
    this.preferredPort = preferredPort;
  }

  /**
   * Returns the port set by {@link #PORT_PROPERTY} or {@link #DEFAULT_PORT}.
   */
  public static int getConfiguredPort() {
    try {
      int port = Integer.getInteger(PORT_PROPERTY, DEFAULT_PORT).intValue();
      return port >= 0 && port <= 65535 ? port : DEFAULT_PORT;
    } catch (SecurityException ex) {
      return DEFAULT_PORT;
    }
  }

  /**
   * Returns the token stored in the given application <code>folder</code>, after
   * creating it the first time. The token is kept from one session to the next
   * so the client configuration stays valid, in a file only its owner can read.
   */
  public static String getOrCreateToken(File folder) throws IOException {
    File tokenFile = new File(folder, TOKEN_FILE);
    if (tokenFile.isFile()) {
      InputStream in = new FileInputStream(tokenFile);
      try {
        String token = new String(readFully(in, 4096), "UTF-8").trim();
        if (token.matches("[0-9a-f]{32,}")) {
          return token;
        }
      } finally {
        in.close();
      }
    }
    String token = createToken();
    folder.mkdirs();
    // Restrict the file to its owner before writing the secret into it
    tokenFile.delete();
    if (!tokenFile.createNewFile()) {
      throw new IOException("Can't create " + tokenFile);
    }
    tokenFile.setReadable(false, false);
    tokenFile.setWritable(false, false);
    tokenFile.setReadable(true, true);
    tokenFile.setWritable(true, true);
    OutputStream out = new FileOutputStream(tokenFile);
    try {
      out.write(token.getBytes("UTF-8"));
    } finally {
      out.close();
    }
    return token;
  }

  /**
   * Returns a new random token.
   */
  public static String createToken() {
    byte [] bytes = new byte [32];
    new SecureRandom().nextBytes(bytes);
    StringBuilder token = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      token.append(String.format("%02x", b & 0xFF));
    }
    return token.toString();
  }

  /**
   * Starts this server on the loopback interface, on its preferred port or any
   * free one if it's already used (by another home window for example).
   */
  public synchronized void start() throws IOException {
    if (this.server != null) {
      return;
    }
    InetAddress loopback = InetAddress.getByName("127.0.0.1");
    HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(loopback, this.preferredPort), 0);
    } catch (BindException ex) {
      server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    }
    server.createContext(ENDPOINT_PATH, new HttpHandler() {
        public void handle(HttpExchange exchange) throws IOException {
          try {
            handleExchange(exchange);
          } finally {
            exchange.close();
          }
        }
      });
    this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable runnable) {
          Thread thread = new Thread(runnable, "Sweet Home 3D MCP server");
          thread.setDaemon(true);
          return thread;
        }
      });
    server.setExecutor(this.executor);
    server.start();
    this.server = server;
  }

  /**
   * Stops this server.
   */
  public synchronized void stop() {
    if (this.server != null) {
      this.server.stop(0);
      this.server = null;
      this.executor.shutdownNow();
      this.executor = null;
    }
  }

  /**
   * Returns <code>true</code> if this server is started.
   */
  public synchronized boolean isRunning() {
    return this.server != null;
  }

  /**
   * Returns the port this server listens to, or -1 if it isn't started.
   */
  public synchronized int getPort() {
    return this.server != null ? this.server.getAddress().getPort() : -1;
  }

  /**
   * Returns the URL of the MCP endpoint, or <code>null</code> if this server isn't started.
   */
  public String getUrl() {
    int port = getPort();
    return port >= 0 ? "http://127.0.0.1:" + port + ENDPOINT_PATH : null;
  }

  /**
   * Returns the secret clients must send as a bearer token.
   */
  public String getToken() {
    return this.token;
  }

  private void handleExchange(HttpExchange exchange) throws IOException {
    if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()
        || !isLocalHost(exchange.getRequestHeaders().getFirst("Host"), false)
        || !isLocalHost(exchange.getRequestHeaders().getFirst("Origin"), true)) {
      // Requests relayed by a web page (DNS rebinding, cross-site requests)
      sendText(exchange, 403, "Forbidden");
      return;
    }
    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
    String prefix = "Bearer ";
    if (authorization == null
        || !authorization.regionMatches(true, 0, prefix, 0, prefix.length())
        || !MessageDigest.isEqual(authorization.substring(prefix.length()).trim().getBytes("UTF-8"),
                this.token.getBytes("UTF-8"))) {
      exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
      sendText(exchange, 401, "Unauthorized");
      return;
    }
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      // No server-initiated stream nor session to delete
      exchange.getResponseHeaders().set("Allow", "POST");
      sendText(exchange, 405, "Method not allowed");
      return;
    }
    byte [] body = readFully(exchange.getRequestBody(), MAX_REQUEST_LENGTH + 1);
    if (body.length > MAX_REQUEST_LENGTH) {
      sendText(exchange, 413, "Request too large");
      return;
    }
    final String request = new String(body, "UTF-8");
    final String [] response = new String [1];
    try {
      // The home is read and edited in the event dispatch thread
      EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            response [0] = handler.handle(request);
          }
        });
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      sendText(exchange, 503, "Interrupted");
      return;
    } catch (InvocationTargetException ex) {
      sendText(exchange, 500, "Internal error");
      return;
    }
    if (response [0] == null) {
      exchange.sendResponseHeaders(202, -1);
    } else {
      byte [] data = response [0].getBytes("UTF-8");
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, data.length);
      OutputStream out = exchange.getResponseBody();
      out.write(data);
      out.close();
    }
  }

  /**
   * Returns <code>true</code> if the given <code>Host</code> header or
   * <code>Origin</code> URL designates this computer. A missing origin is
   * accepted as clients which aren't browsers don't send one; a missing host isn't.
   */
  static boolean isLocalHost(String value, boolean origin) {
    if (value == null) {
      return origin;
    }
    String host;
    try {
      host = origin ? new URI(value.trim()).getHost() : new URI("http://" + value.trim()).getHost();
    } catch (Exception ex) {
      return false;
    }
    return host != null
        && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
            || host.equals("[::1]") || host.equals("::1"));
  }

  private static byte [] readFully(InputStream in, int maxLength) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte [] buffer = new byte [8192];
    int count;
    while (out.size() < maxLength && (count = in.read(buffer, 0, Math.min(buffer.length, maxLength - out.size()))) > 0) {
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }

  private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
    byte [] data = text.getBytes("UTF-8");
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
    exchange.sendResponseHeaders(status, data.length);
    OutputStream out = exchange.getResponseBody();
    out.write(data);
    out.close();
  }
}
