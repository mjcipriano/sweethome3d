/*
 * WebFolderLaunchersTest.java 16 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

import com.eteks.sweethome3d.swing.PanoramaExporter;
import com.eteks.sweethome3d.tools.WebFolderLaunchers;

/**
 * Tests the launchers written in exported web folders, and the panorama
 * page that needs none.
 */
public class WebFolderLaunchersTest {
  private static String read(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()), "UTF-8");
  }

  private static void delete(File folder) {
    File [] files = folder.listFiles();
    if (files != null) {
      for (File file : files) {
        file.delete();
      }
    }
    folder.delete();
  }

  @Test
  public void testLaunchers() throws Exception {
    File folder = Files.createTempDirectory("sh3d-launchers-test").toFile();
    try {
      WebFolderLaunchers.write(folder, "index.html");
      // Windows: a batch file that runs a PowerShell server limited to this computer and this folder
      String batch = read(new File(folder, WebFolderLaunchers.WINDOWS_LAUNCHER));
      assertTrue(batch, batch.contains("powershell -NoProfile -ExecutionPolicy Bypass -File \"%~dp0view-in-browser.ps1\""));
      assertTrue(batch.contains("\r\n"));
      String server = read(new File(folder, WebFolderLaunchers.WINDOWS_SERVER));
      assertTrue(server, server.contains("$candidate.Prefixes.Add(\"http://localhost:$tried/\")"));
      assertFalse("Only this computer is served", server.contains("http://+:") || server.contains("http://*:"));
      assertTrue(server, server.contains("$file.StartsWith($root + [IO.Path]::DirectorySeparatorChar)"));
      assertTrue(server, server.contains("$response.StatusCode = 405"));
      assertTrue(server, server.contains("http://localhost:$Port/index.html"));
      // macOS and Linux: the same script, served to this computer only, with unix line ends
      File script = new File(folder, WebFolderLaunchers.UNIX_LAUNCHER);
      String unix = read(script);
      assertEquals(unix, read(new File(folder, WebFolderLaunchers.MAC_LAUNCHER)));
      assertTrue(unix.startsWith("#!/bin/sh\n"));
      assertFalse(unix.contains("\r"));
      assertTrue(unix, unix.contains("python3 -m http.server \"$PORT\" --bind 127.0.0.1"));
      assertTrue(unix, unix.contains("ruby -run -e httpd . -p \"$PORT\" -b 127.0.0.1"));
      assertTrue(unix, unix.contains("php -S \"127.0.0.1:$PORT\""));
      assertTrue(unix, unix.contains("http://localhost:$PORT/index.html"));
      if (!System.getProperty("os.name").startsWith("Windows")) {
        assertTrue(script.canExecute());
        assertTrue(new File(folder, WebFolderLaunchers.MAC_LAUNCHER).canExecute());
      }
      assertTrue(WebFolderLaunchers.getNotice().contains(WebFolderLaunchers.WINDOWS_LAUNCHER));
      // The name of the page goes in scripts: nothing else than a file name
      try {
        WebFolderLaunchers.write(folder, "index.html\"; rm -rf ~");
        fail("Unexpected page name accepted");
      } catch (IOException ex) {
        // Expected
      }
    } finally {
      delete(folder);
    }
  }

  @Test
  public void testPanoramaPageContainsItsPicture() throws Exception {
    File folder = Files.createTempDirectory("sh3d-panorama-test").toFile();
    try {
      PanoramaExporter.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB), folder, "My <home>");
      assertTrue(new File(folder, PanoramaExporter.IMAGE_NAME).length() > 100);
      String page = read(new File(folder, PanoramaExporter.PAGE_NAME));
      // Opened from the disk with a double click: the picture is in the page
      assertTrue(page.contains("new THREE.TextureLoader().load('data:image/jpeg;base64,/9j/"));
      assertFalse(page.contains(".load('" + PanoramaExporter.IMAGE_NAME + "')"));
      assertTrue(page.contains("<title>My &lt;home></title>"));
    } finally {
      delete(folder);
    }
  }
}
