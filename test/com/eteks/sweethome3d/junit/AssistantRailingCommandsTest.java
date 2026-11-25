/*
 * AssistantRailingCommandsTest.java 25 nov. 2026
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the railings built along a line of points and along stairs.
 */
public class AssistantRailingCommandsTest {
  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, preferences, this.homeController);
  }

  @After
  public void tearDown() {
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private static int number(String summary, String pattern) {
    Matcher matcher = Pattern.compile(pattern).matcher(summary);
    assertTrue(pattern + " in " + summary, matcher.find());
    return Integer.parseInt(matcher.group(1));
  }

  private HomePieceOfFurniture getLastPiece() {
    return this.home.getFurniture().get(this.home.getFurniture().size() - 1);
  }

  @Test
  public void testRailingStyles() {
    for (String style : new String [] {"square", "turned_balusters", "cable", "glass"}) {
      String summary = run("{\"action\":\"add_railing\",\"points\":[[100,200],[500,200]],\"style\":\"" + style + "\"}");
      assertTrue(summary, summary.contains("Added the railing"));
      assertTrue(summary, summary.contains("400 cm long, 91 cm high, style " + style));
      HomePieceOfFurniture railing = getLastPiece();
      assertEquals("Railing", railing.getName());
      assertEquals(300, railing.getX(), 0.5f);
      assertEquals(200, railing.getY(), 0.5f);
      assertEquals(0, railing.getElevation(), 0.01f);
      assertTrue(style + " " + railing.getWidth(), railing.getWidth() >= 400 && railing.getWidth() <= 410);
      assertTrue(railing.getDepth() <= 10);
      assertTrue(railing.getHeight() >= 91 && railing.getHeight() <= 93);
      int triangles = number(summary, "(\\d+) triangles");
      System.out.println("Railing of 4 m, style " + style + ": " + triangles + " triangles");
      assertTrue(style + ": " + triangles + " triangles", triangles > 20 && triangles < 6000);
      if ("square".equals(style) || "turned_balusters".equals(style)) {
        int balusters = number(summary, "(\\d+) balusters");
        assertTrue(balusters + " balusters", balusters >= 30 && balusters <= 36);
        assertTrue("No more than 10 cm between balusters: " + summary, !summary.contains("building codes"));
      } else if ("cable".equals(style)) {
        assertTrue(summary, number(summary, "(\\d+) cables") >= 9);
      } else {
        assertEquals(summary, 4, number(summary, "(\\d+) glass panels"));
      }
    }
    assertEquals(4, this.home.getFurniture().size());
    this.homeController.undo();
    assertEquals(3, this.home.getFurniture().size());
  }

  /**
   * Balusters are repeated along a longer railing, not stretched.
   */
  @Test
  public void testBalustersRepeat() {
    String summary = run("{\"action\":\"add_railing\",\"points\":[[0,0],[200,0]],\"spacing\":12}");
    int shortCount = number(summary, "(\\d+) balusters");
    summary = run("{\"action\":\"add_railing\",\"points\":[[0,100],[800,100]],\"spacing\":12}");
    int longCount = number(summary, "(\\d+) balusters");
    assertTrue(shortCount + " then " + longCount, longCount >= shortCount * 3.5 && longCount <= shortCount * 4.5);
    HomePieceOfFurniture railing = getLastPiece();
    String id = HomeItemIds.getId(railing);
    // A wider spacing is told to be too wide for children
    summary = run("{\"action\":\"modify_railing\",\"id\":\"" + id + "\",\"spacing\":30,\"colour\":\"red\"}");
    assertTrue(summary, summary.contains("Modified the railing " + id));
    assertTrue(summary, summary.contains("building codes"));
    assertTrue(summary, summary.contains("doesn't know [colour]"));
    assertTrue(number(summary, "(\\d+) balusters") < longCount / 2);
    // An L with a corner post, closed in a rectangle, then of glass
    summary = run("{\"action\":\"modify_railing\",\"id\":\"" + id + "\",\"points\":[[0,100],[300,100],[300,300],[0,300]],"
        + "\"closed\":true,\"spacing\":12,\"style\":\"glass\",\"top_rail\":\"none\",\"height\":107}");
    assertTrue(summary, summary.contains("1000 cm long, 107 cm high, style glass"));
    assertEquals(150, railing.getX(), 0.5f);
    assertEquals(200, railing.getY(), 0.5f);
    assertTrue(railing.getWidth() >= 300 && railing.getDepth() >= 200);
    this.homeController.undo();
    assertTrue(railing.getDepth() < 20);

    assertTrue(run("{\"action\":\"add_railing\",\"points\":[[0,0]]}").contains("must list the 2 points"));
    assertTrue(run("{\"action\":\"add_railing\",\"points\":[[0,0],[100,0]],\"style\":\"rope\"}").contains("\"style\" is one of"));
    assertTrue(run("{\"action\":\"add_railing\",\"points\":[[0,0],[100,0]],\"height\":5}").contains("\"height\" is between"));
    assertTrue(run("{\"action\":\"add_railing\",\"points\":[[0,0],[100,0]],\"top_rail\":\"none\"}").contains("only a glass railing"));
    assertTrue(run("{\"action\":\"modify_railing\",\"id\":\"F99\",\"height\":100}").contains("give the id of a railing"));
  }

  /**
   * A railing follows the heights of its points, and the flights of stairs.
   */
  @Test
  public void testRailingAlongStairs() {
    String summary = run("{\"action\":\"add_railing\",\"points\":[[0,0,0],[300,0,200],[500,0]],\"style\":\"square\"}");
    assertTrue(summary, summary.contains("following the heights of its points"));
    HomePieceOfFurniture railing = getLastPiece();
    assertEquals(0, railing.getElevation(), 0.01f);
    assertTrue("From the floor to the rail of the top: " + railing.getHeight(),
        railing.getHeight() >= 291 && railing.getHeight() <= 294);

    summary = run("{\"action\":\"add_stairs\",\"x\":1000,\"y\":1000,\"width\":100,\"shape\":\"L\",\"turn\":\"right\"}");
    assertNotNull(summary);
    HomePieceOfFurniture stairs = getLastPiece();
    String stairsId = HomeItemIds.getId(stairs);
    float rise = stairs.getHeight();
    for (String side : new String [] {"left", "right"}) {
      summary = run("{\"action\":\"add_railing\",\"stairs\":\"" + stairsId + "\",\"side\":\"" + side + "\",\"style\":\"turned_balusters\"}");
      assertTrue(summary, summary.contains("follows the " + side + " side of the stairs " + stairsId));
      assertTrue(summary, summary.contains("following the heights of its points"));
      railing = getLastPiece();
      assertEquals(stairs.getLevel(), railing.getLevel());
      // Inside the stairs, from their floor to above their top
      assertTrue(railing.getX() - railing.getWidth() / 2 >= stairs.getX() - stairs.getWidth() / 2 - 5);
      assertTrue(railing.getX() + railing.getWidth() / 2 <= stairs.getX() + stairs.getWidth() / 2 + 5);
      assertTrue(railing.getY() - railing.getDepth() / 2 >= stairs.getY() - stairs.getDepth() / 2 - 5);
      assertTrue(railing.getY() + railing.getDepth() / 2 <= stairs.getY() + stairs.getDepth() / 2 + 5);
      assertTrue(side + ": " + railing.getHeight() + " for a rise of " + rise,
          railing.getHeight() > rise * 0.8f + 91 && railing.getHeight() < rise + 91 + 5);
      // Both flights: longer than one of them
      int length = number(summary, "(\\d+)(?:\\.\\d+)? cm long");
      assertTrue(side + ": " + length + " cm", length > Math.max(stairs.getWidth(), stairs.getDepth()) * 0.8f);
    }
    // On along the overlook upstairs, as one piece
    summary = run("{\"action\":\"add_railing\",\"stairs\":\"" + stairsId + "\",\"side\":\"left\",\"points\":[[1400,700],[1700,700]]}");
    assertTrue(summary, summary.contains("then the points given"));
    railing = getLastPiece();
    assertTrue(railing.getX() + railing.getWidth() / 2 >= 1699);
    assertTrue(run("{\"action\":\"add_railing\",\"stairs\":\"F99\"}").contains("no piece has the id F99"));
    assertTrue(run("{\"action\":\"add_railing\",\"stairs\":\"" + stairsId + "\",\"side\":\"both\"}").contains("\"side\" is left or right"));
  }
}
