/*
 * HomeSaveOverOpenedFileTest.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.junit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * Checks that a home opened from a file can be modified and saved over that
 * same file repeatedly without its embedded contents being mixed up: saving
 * renumbers the ZIP entries, so the contents of the opened home must not
 * depend on the entries of the file being overwritten.
 */
public class HomeSaveOverOpenedFileTest extends TestCase {
  public void testSaveTwiceOverOpenedFileKeepsContents() throws Exception {
    File directory = File.createTempFile("saveover", "");
    directory.delete();
    assertTrue(directory.mkdirs());
    try {
      Home home = new Home();
      String [] names = {"first", "second", "third"};
      for (String name : names) {
        home.addPieceOfFurniture(new HomePieceOfFurniture(new CatalogPieceOfFurniture(name,
            createContent(directory, name + ".png", "icon of " + name),
            createContent(directory, name + ".obj", "model of " + name), 50, 50, 50, true, false)));
      }
      File homeFile = new File(directory, "home.sh3d");
      HomeFileRecorder recorder = new HomeFileRecorder(9);
      recorder.writeHome(home, homeFile.getPath());

      Home openedHome = recorder.readHome(homeFile.getPath());
      assertEquals(3, openedHome.getFurniture().size());
      // Deleting the first piece shifts the entry numbers of the others at save time
      openedHome.deletePieceOfFurniture(openedHome.getFurniture().get(0));
      recorder.writeHome(openedHome, homeFile.getPath());
      assertContents(openedHome, "second", "third");
      // A second save reads the contents again through the opened home
      recorder.writeHome(openedHome, homeFile.getPath());
      assertContents(openedHome, "second", "third");

      Home reopenedHome = recorder.readHome(homeFile.getPath());
      assertContents(reopenedHome, "second", "third");
    } finally {
      File [] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          file.delete();
        }
      }
      directory.delete();
    }
  }

  private static void assertContents(Home home, String... names) throws IOException {
    assertEquals(names.length, home.getFurniture().size());
    for (int i = 0; i < names.length; i++) {
      HomePieceOfFurniture piece = home.getFurniture().get(i);
      assertEquals(names [i], piece.getName());
      assertTrue("Model of " + names [i],
          Arrays.equals(("model of " + names [i]).getBytes("UTF-8"), read(piece.getModel())));
      assertTrue("Icon of " + names [i],
          Arrays.equals(("icon of " + names [i]).getBytes("UTF-8"), read(piece.getIcon())));
    }
  }

  private static Content createContent(File directory, String name, String text) throws IOException {
    File file = new File(directory, name);
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(text.getBytes("UTF-8"));
    } finally {
      out.close();
    }
    return new URLContent(file.toURI().toURL());
  }

  private static byte [] read(Content content) throws IOException {
    InputStream in = content.openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int size;
      while ((size = in.read(buffer)) != -1) {
        out.write(buffer, 0, size);
      }
      return out.toByteArray();
    } finally {
      in.close();
    }
  }
}
