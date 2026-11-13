/*
 * WebXRSceneDescriptionTest.java
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.plugin.webxr.WebXRPreviewPlugin;
import com.eteks.sweethome3d.plugin.webxr.WebXRSceneDescription;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the description of the plan given to the WebXR viewer and the files
 * the viewer is made of.
 */
public class WebXRSceneDescriptionTest {
  @Test
  @SuppressWarnings("unchecked")
  public void testHomeWithoutLevels() throws Exception {
    Home home = new Home(250);
    home.addWall(new Wall(0, 200, 400, 200, 10, 250));
    home.addPieceOfFurniture(createDoor(200, 200));
    home.getObserverCamera().setX(100);
    home.getObserverCamera().setY(50);
    home.getObserverCamera().setZ(170);
    home.getObserverCamera().setYaw(1.5f);

    Map<String, Object> scene = (Map<String, Object>)JSONParser.parse(WebXRSceneDescription.toJSON(home));
    List<Object> levels = (List<Object>)scene.get("levels");
    assertEquals("One implicit level", 1, levels.size());
    assertEquals(2.5, number(levels.get(0), "height"), 1e-4);
    assertEquals(Boolean.TRUE, ((Map<String, Object>)levels.get(0)).get("floor"));

    // Lengths are in meters, the plan y axis is z
    List<Object> walls = (List<Object>)scene.get("walls");
    assertEquals(1, walls.size());
    assertEquals(0, number(walls.get(0), "x1"), 1e-4);
    assertEquals(2, number(walls.get(0), "z1"), 1e-4);
    assertEquals(4, number(walls.get(0), "x2"), 1e-4);
    assertEquals(0.1, number(walls.get(0), "thickness"), 1e-4);
    assertEquals(0, number(walls.get(0), "level"), 0);

    List<Object> openings = (List<Object>)scene.get("openings");
    assertEquals(1, openings.size());
    assertEquals(2, number(openings.get(0), "x"), 1e-4);
    assertEquals(2, number(openings.get(0), "z"), 1e-4);
    assertEquals(0.9, number(openings.get(0), "width"), 1e-4);
    assertEquals(2.1, number(openings.get(0), "height"), 1e-4);
    assertEquals(0, number(openings.get(0), "sill"), 1e-4);

    assertEquals(1, number(scene.get("start"), "x"), 1e-4);
    assertEquals(0.5, number(scene.get("start"), "z"), 1e-4);
    assertEquals(1.5, number(scene.get("start"), "yaw"), 1e-4);
    assertEquals(1.7, number(scene.get("start"), "eyeHeight"), 1e-4);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testLevels() throws Exception {
    Home home = new Home(250);
    Level ground = new Level("Ground \"floor\"", 0, 12, 250);
    Level first = new Level("First", 270, 12, 250);
    Level hidden = new Level("Hidden", 540, 12, 250);
    Level roof = new Level("Roof", 540, 12, 250);
    home.addLevel(ground);
    home.addLevel(first);
    home.addLevel(hidden);
    home.addLevel(roof);
    hidden.setViewable(false);

    home.setSelectedLevel(ground);
    home.addWall(new Wall(0, 0, 400, 0, 10, 250));
    home.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
    home.setSelectedLevel(first);
    home.addWall(new Wall(0, 0, 0, 300, 20, 250));
    home.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
    home.setSelectedLevel(hidden);
    home.addWall(new Wall(0, 0, 100, 100, 10, 250));
    home.setSelectedLevel(first);
    home.getObserverCamera().setZ(270 + 165);
    // As the export does, show every viewable level
    for (Level level : home.getLevels()) {
      if (level.isViewable()) {
        level.setVisible(true);
      }
    }

    Map<String, Object> scene = (Map<String, Object>)JSONParser.parse(WebXRSceneDescription.toJSON(home));
    List<Object> levels = (List<Object>)scene.get("levels");
    assertEquals("Hidden level not exported", 3, levels.size());
    assertEquals("Ground \"floor\"", ((Map<String, Object>)levels.get(0)).get("name"));
    assertEquals(2.7, number(levels.get(1), "elevation"), 1e-4);
    assertEquals(Boolean.TRUE, ((Map<String, Object>)levels.get(1)).get("floor"));
    assertEquals("A level without room has no floor", Boolean.FALSE, ((Map<String, Object>)levels.get(2)).get("floor"));

    List<Object> walls = (List<Object>)scene.get("walls");
    assertEquals("Wall of the hidden level not exported", 2, walls.size());
    assertEquals(0, number(walls.get(0), "level"), 0);
    assertEquals(1, number(walls.get(1), "level"), 0);
    assertEquals("Visitor starts at the selected level", 1, number(scene.get("start"), "level"), 0);
    assertEquals("Eye height is relative to the level", 1.65, number(scene.get("start"), "eyeHeight"), 1e-4);
  }

  @Test
  public void testViewerFilesAreWritten() throws Exception {
    File folder = Files.createTempDirectory("sh3d-webxr-test").toFile();
    try {
      File page = WebXRPreviewPlugin.writeViewer(folder, new Home());
      assertEquals("index.html", page.getName());
      String html = new String(Files.readAllBytes(page.toPath()), "UTF-8");
      assertTrue(html.contains("webxr-viewer.js"));
      String viewer = new String(Files.readAllBytes(new File(folder, "webxr-viewer.js").toPath()), "UTF-8");
      assertTrue(viewer.contains("./webxr-walk.js"));
      assertTrue(new File(folder, "webxr-walk.js").length() > 0);
      JSONParser.parse(new String(Files.readAllBytes(new File(folder, "scene.json").toPath()), "UTF-8"));
    } finally {
      File [] files = folder.listFiles();
      if (files != null) {
        for (File file : files) {
          file.delete();
        }
      }
      folder.delete();
    }
  }

  private static HomeDoorOrWindow createDoor(float x, float y) throws Exception {
    Content content = new URLContent(new File("dummy.obj").toURI().toURL());
    HomeDoorOrWindow door = new HomeDoorOrWindow(new CatalogDoorOrWindow(null, "Door", null, content, content,
        90, 10, 210, 0, true, 0.5f, 0.5f, new Sash [0], null, null, false, null, null));
    door.setX(x);
    door.setY(y);
    return door;
  }

  @SuppressWarnings("unchecked")
  private static double number(Object map, String key) {
    return ((Number)((Map<String, Object>)map).get(key)).doubleValue();
  }
}
