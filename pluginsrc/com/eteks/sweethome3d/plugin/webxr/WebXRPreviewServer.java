package com.eteks.sweethome3d.plugin.webxr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * Serves an exported WebXR preview directory to the local browser and to
 * headsets on the local network.
 *
 * <p>Two servers run over the same files:
 * <ul>
 * <li>an HTTP server bound to 127.0.0.1, for the desktop browser (localhost is
 *     a WebXR secure context, so no certificate fuss is needed there);</li>
 * <li>an HTTPS server bound to all interfaces for headsets such as the Meta
 *     Quest: browsers only expose WebXR to secure contexts, and a LAN address
 *     is only secure over https. The TLS key pair is a bundled self-signed
 *     certificate - it provides no authentication and is not a secret; it
 *     exists solely so the headset browser can grant the page a secure
 *     context after the user accepts the certificate warning once.</li>
 * </ul>
 */
public class WebXRPreviewServer {
  private static final String KEYSTORE_RESOURCE = "webxr-keystore.p12";
  private static final char [] KEYSTORE_PASSWORD = "sweethome3d-webxr".toCharArray();

  private final File       root;
  private final String     rootPath;
  private HttpServer       httpServer;
  private HttpsServer      httpsServer;

  public WebXRPreviewServer(File root) throws IOException {
    this.root = root;
    this.rootPath = root.getCanonicalPath();
  }

  /**
   * Starts the local HTTP server and the LAN HTTPS server on ephemeral ports.
   */
  public void start() throws IOException {
    HttpHandler handler = new StaticFileHandler();
    ExecutorService executor = Executors.newCachedThreadPool();

    this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.httpServer.createContext("/", handler);
    this.httpServer.setExecutor(executor);
    this.httpServer.start();

    try {
      SSLContext sslContext = createSelfSignedContext();
      this.httpsServer = HttpsServer.create(new InetSocketAddress((InetAddress)null, 0), 0);
      this.httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
      this.httpsServer.createContext("/", handler);
      this.httpsServer.setExecutor(executor);
      this.httpsServer.start();
    } catch (GeneralSecurityException ex) {
      // The desktop preview still works over plain HTTP
      this.httpsServer = null;
    }

    final WebXRPreviewServer server = this;
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        public void run() {
          server.stop();
        }
      }));
  }

  public void stop() {
    if (this.httpServer != null) {
      this.httpServer.stop(0);
      this.httpServer = null;
    }
    if (this.httpsServer != null) {
      this.httpsServer.stop(0);
      this.httpsServer = null;
    }
  }

  /**
   * Returns the URL of the desktop (localhost) preview.
   */
  public String getLocalUrl() {
    return "http://127.0.0.1:" + this.httpServer.getAddress().getPort() + "/index.html";
  }

  /**
   * Returns the https port for headsets, or -1 if the HTTPS server isn't running.
   */
  public int getHttpsPort() {
    return this.httpsServer != null ? this.httpsServer.getAddress().getPort() : -1;
  }

  /**
   * Returns the https URLs a headset on the local network can open, one per
   * non-loopback IPv4 address of this machine.
   */
  public List<String> getLanUrls() {
    List<String> urls = new ArrayList<String>();
    if (this.httpsServer == null) {
      return urls;
    }
    int port = this.httpsServer.getAddress().getPort();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces != null && interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        if (!networkInterface.isUp() || networkInterface.isLoopback()) {
          continue;
        }
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address.isLoopbackAddress() || address.getHostAddress().contains(":")) {
            continue; // Skip loopback and IPv6: headset users type these by hand
          }
          urls.add("https://" + address.getHostAddress() + ":" + port + "/index.html");
        }
      }
    } catch (IOException ex) {
      // Return whatever was collected
    }
    return urls;
  }

  private SSLContext createSelfSignedContext() throws IOException, GeneralSecurityException {
    InputStream keyStoreStream = WebXRPreviewServer.class.getResourceAsStream(KEYSTORE_RESOURCE);
    if (keyStoreStream == null) {
      throw new IOException("Missing bundled keystore " + KEYSTORE_RESOURCE);
    }
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(keyStoreStream, KEYSTORE_PASSWORD);
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
      return sslContext;
    } finally {
      keyStoreStream.close();
    }
  }

  /**
   * Serves files from the export directory, refusing paths that escape it.
   */
  private class StaticFileHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      if (path == null || "/".equals(path)) {
        path = "/index.html";
      }
      path = path.replace('\\', '/');
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      File target = new File(root, path);
      String canonical = target.getCanonicalPath();
      if (!canonical.startsWith(rootPath) || !target.exists() || target.isDirectory()) {
        sendNotFound(exchange);
        return;
      }
      byte [] data = readAllBytes(target);
      Headers headers = exchange.getResponseHeaders();
      String contentType = guessContentType(target.getName());
      if (contentType != null) {
        headers.set("Content-Type", contentType);
      }
      exchange.sendResponseHeaders(200, data.length);
      OutputStream body = exchange.getResponseBody();
      body.write(data);
      body.close();
    }
  }

  private void sendNotFound(HttpExchange exchange) throws IOException {
    byte [] data = "Not Found".getBytes("UTF-8");
    exchange.sendResponseHeaders(404, data.length);
    OutputStream body = exchange.getResponseBody();
    body.write(data);
    body.close();
  }

  static String guessContentType(String name) {
    String lower = name.toLowerCase(Locale.US);
    if (lower.endsWith(".html") || lower.endsWith(".htm")) {
      return "text/html; charset=utf-8";
    } else if (lower.endsWith(".js")) {
      return "application/javascript; charset=utf-8";
    } else if (lower.endsWith(".css")) {
      return "text/css; charset=utf-8";
    } else if (lower.endsWith(".mtl") || lower.endsWith(".obj") || lower.endsWith(".txt")) {
      return "text/plain; charset=utf-8";
    } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (lower.endsWith(".png")) {
      return "image/png";
    }
    return URLConnection.guessContentTypeFromName(name);
  }

  private static byte[] readAllBytes(File file) throws IOException {
    FileInputStream in = null;
    try {
      in = new FileInputStream(file);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }
}
