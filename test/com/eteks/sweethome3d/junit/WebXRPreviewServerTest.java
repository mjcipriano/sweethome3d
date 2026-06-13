package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.Test;

import com.eteks.sweethome3d.plugin.webxr.WebXRPreviewServer;

/**
 * Verifies the WebXR preview server that lets a headset on the local network
 * (e.g. Meta Quest 2) view the exported home: it serves the export directory
 * over localhost HTTP and over HTTPS (WebXR needs a secure context away from
 * localhost), and refuses paths outside the export directory.
 */
public class WebXRPreviewServerTest {
  @Test
  public void testServesExportOverHttpAndHttps() throws Exception {
    File exportDir = createExportDir();
    WebXRPreviewServer server = new WebXRPreviewServer(exportDir);
    server.start();
    try {
      // Desktop preview over localhost HTTP
      String localUrl = server.getLocalUrl();
      assertTrue(localUrl, localUrl.startsWith("http://127.0.0.1:"));
      HttpURLConnection connection =
          (HttpURLConnection)new URL(localUrl).openConnection();
      assertEquals(200, connection.getResponseCode());
      String html = readBody(connection);
      assertTrue(html, html.contains("WEBXR-TEST-PAGE"));
      assertTrue(connection.getContentType(),
          connection.getContentType().startsWith("text/html"));

      connection = (HttpURLConnection)new URL(
          localUrl.replace("index.html", "scene.obj")).openConnection();
      assertEquals(200, connection.getResponseCode());
      assertTrue(readBody(connection).contains("v 0 0 0"));

      // Path traversal is refused
      File secret = new File(exportDir.getParentFile(), "secret.txt");
      writeFile(secret, "secret");
      try {
        connection = (HttpURLConnection)new URL(
            localUrl.replace("index.html", "..%2Fsecret.txt")).openConnection();
        assertEquals("Paths outside the export dir are not served",
            404, connection.getResponseCode());
      } finally {
        secret.delete();
      }

      // Headset path: HTTPS with the bundled self-signed certificate
      assertTrue("HTTPS server is running", server.getHttpsPort() > 0);
      HttpsURLConnection httpsConnection = (HttpsURLConnection)new URL(
          "https://127.0.0.1:" + server.getHttpsPort() + "/index.html").openConnection();
      trustSelfSigned(httpsConnection);
      assertEquals(200, httpsConnection.getResponseCode());
      assertTrue(readBody(httpsConnection).contains("WEBXR-TEST-PAGE"));
    } finally {
      server.stop();
      deleteRecursively(exportDir);
    }
  }

  @Test
  public void testLanUrlsUseHttpsAndTheHttpsPort() throws Exception {
    File exportDir = createExportDir();
    WebXRPreviewServer server = new WebXRPreviewServer(exportDir);
    server.start();
    try {
      List<String> lanUrls = server.getLanUrls();
      // A machine with no LAN interface legitimately returns no URLs;
      // any returned URL must target the HTTPS port
      for (String url : lanUrls) {
        assertTrue(url, url.startsWith("https://"));
        assertTrue(url, url.contains(":" + server.getHttpsPort() + "/"));
        assertFalse("Loopback is not a LAN url: " + url, url.contains("127.0.0.1"));
      }
    } finally {
      server.stop();
      deleteRecursively(exportDir);
    }
  }

  private static File createExportDir() throws IOException {
    File exportDir = File.createTempFile("webxr-test", "");
    exportDir.delete();
    exportDir.mkdirs();
    writeFile(new File(exportDir, "index.html"), "<html>WEBXR-TEST-PAGE</html>");
    writeFile(new File(exportDir, "scene.obj"), "v 0 0 0\n");
    writeFile(new File(exportDir, "scene.mtl"), "newmtl test\n");
    return exportDir;
  }

  private static void writeFile(File file, String content) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(content.getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }

  private static String readBody(HttpURLConnection connection) throws IOException {
    InputStream in = connection.getResponseCode() >= 400
        ? connection.getErrorStream() : connection.getInputStream();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte [] buffer = new byte [8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    in.close();
    return new String(out.toByteArray(), "UTF-8");
  }

  /**
   * Trusts the server's self-signed certificate for this connection, the same
   * decision a headset user takes on the browser warning page.
   */
  private static void trustSelfSigned(HttpsURLConnection connection) throws Exception {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, new TrustManager [] {
        new X509TrustManager() {
          public void checkClientTrusted(X509Certificate [] chain, String authType) {
          }

          public void checkServerTrusted(X509Certificate [] chain, String authType) {
          }

          public X509Certificate [] getAcceptedIssuers() {
            return new X509Certificate [0];
          }
        }
      }, null);
    connection.setSSLSocketFactory(sslContext.getSocketFactory());
    connection.setHostnameVerifier(new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      });
  }

  private static void deleteRecursively(File file) {
    File [] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    file.delete();
  }
}
