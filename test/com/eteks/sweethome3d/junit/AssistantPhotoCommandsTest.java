/*
 * AssistantPhotoCommandsTest.java 23 nov. 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the trees and the shrubs built from a species, and the commands that change them.
 */
public class AssistantPhotoCommandsTest {
  private String                   previousNo3D;
  private Locale                   previousLocale;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.UK);
    this.home = new Home(250);
    this.preferences = new DefaultUserPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
  }

  @After
  public void tearDown() {
    Locale.setDefault(this.previousLocale);
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private HomePieceOfFurniture getPiece(String name) {
    HomePieceOfFurniture found = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (name.equals(piece.getName())) {
        found = piece;
      }
    }
    return found;
  }

  private List<HomePieceOfFurniture> getPlants() {
    List<HomePieceOfFurniture> plants = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantPlant") != null) {
        plants.add(piece);
      }
    }
    return plants;
  }

  /**
   * Returns the bytes of a JPEG file with the place, the direction and the lens a camera writes.
   */
  private static byte [] createPhoto(double latitude, double longitude, double direction, int focalLength35) {
    java.nio.ByteBuffer tiff = java.nio.ByteBuffer.allocate(400).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte)'I').put((byte)'I').putShort((short)42).putInt(8);
    // The first directory: where the two others are
    tiff.putShort((short)2);
    tiff.putShort((short)0x8769).putShort((short)4).putInt(1).putInt(40);
    tiff.putShort((short)0x8825).putShort((short)4).putInt(1).putInt(60);
    tiff.putInt(0);
    // The lens
    tiff.position(40);
    tiff.putShort((short)1);
    tiff.putShort((short)0xA405).putShort((short)3).putInt(1).putShort((short)focalLength35).putShort((short)0);
    tiff.putInt(0);
    // The place
    tiff.position(60);
    tiff.putShort((short)6);
    tiff.putShort((short)1).putShort((short)2).putInt(2).put((byte)(latitude >= 0 ? 'N' : 'S')).put((byte)0).putShort((short)0);
    tiff.putShort((short)2).putShort((short)5).putInt(3).putInt(200);
    tiff.putShort((short)3).putShort((short)2).putInt(2).put((byte)(longitude >= 0 ? 'E' : 'W')).put((byte)0).putShort((short)0);
    tiff.putShort((short)4).putShort((short)5).putInt(3).putInt(224);
    tiff.putShort((short)17).putShort((short)5).putInt(1).putInt(248);
    tiff.putShort((short)31).putShort((short)5).putInt(1).putInt(256);
    tiff.putInt(0);
    double [] angles = {Math.abs(latitude), Math.abs(longitude)};
    for (int i = 0; i < 2; i++) {
      tiff.position(200 + i * 24);
      int degrees = (int)angles [i];
      int minutes = (int)((angles [i] - degrees) * 60);
      double seconds = ((angles [i] - degrees) * 60 - minutes) * 60;
      tiff.putInt(degrees).putInt(1).putInt(minutes).putInt(1).putInt((int)Math.round(seconds * 10000)).putInt(10000);
    }
    tiff.position(248);
    tiff.putInt((int)Math.round(direction * 100)).putInt(100);
    tiff.putInt(5).putInt(1);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    out.write(0xFF);
    out.write(0xD8);
    out.write(0xFF);
    out.write(0xE1);
    int length = 2 + 6 + 400;
    out.write(length >> 8);
    out.write(length & 0xFF);
    out.write(new byte [] {'E', 'x', 'i', 'f', 0, 0}, 0, 6);
    out.write(tiff.array(), 0, 400);
    out.write(0xFF);
    out.write(0xD9);
    return out.toByteArray();
  }

  private static java.io.File write(java.io.File folder, String name, byte [] content) throws Exception {
    java.io.File file = new java.io.File(folder, name);
    file.deleteOnExit();
    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
    out.write(content);
    out.close();
    return file;
  }

  @Test
  public void testPhotosPlacedFromTheirCamera() throws Exception {
    java.io.File folder = java.nio.file.Files.createTempDirectory("photos").toFile();
    folder.deleteOnExit();
    // 20 m to the south of the origin looking north, and 20 m to its west looking east
    write(folder, "a.jpg", createPhoto(33.7490 - 0.00018, -84.3880, 0, 26));
    write(folder, "b.jpg", createPhoto(33.7490, -84.3880 - 0.000216, 90, 26));
    write(folder, "c.jpg", new byte [] {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xD9});
    String folderPath = folder.getPath().replace("\\", "\\\\");
    String summary = run("{\"action\":\"import_photos\",\"folder\":\"" + folderPath + "\"}");
    assertTrue(summary, summary.contains("needs its georeference"));
    run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.7490,\"lon\":-84.3880}");
    summary = run("{\"action\":\"import_photos\",\"folder\":\"" + folderPath + "\"}");
    assertTrue(summary, summary.contains("Placed 2 photos") && summary.contains("PH1 \"a.jpg\"") && summary.contains("PH2 \"b.jpg\"")
        && summary.contains("Left out, without place: [c.jpg]"));
    // Their marks: a cone, a circle of 5 m and a label each
    assertEquals(4, this.home.getPolylines().size());
    assertEquals(2, this.home.getLabels().size());
    summary = run("{\"action\":\"list_photos\"}");
    assertTrue(summary, summary.contains("PH1 \"a.jpg\" at (0, 199") && summary.contains("looking 0 degrees")
        && summary.contains("69.4 degrees wide") && summary.contains("within 5 m") && summary.contains("PH2 \"b.jpg\" at (-200"));
    // The camera where the first photo was taken
    summary = run("{\"action\":\"photo_viewpoint\",\"photo\":\"PH1\"}");
    assertTrue(summary, summary.contains("Moved the camera where the photo PH1"));
    assertEquals(0, this.home.getCamera().getX(), 5);
    assertEquals(1997, this.home.getCamera().getY(), 30);
    // It looks up the plan
    assertEquals(180, Math.abs(Math.toDegrees(this.home.getCamera().getYaw()) % 360), 1);

    // Something seen in the middle of both photos is at the origin
    summary = run("{\"action\":\"locate_from_photos\",\"rays\":[{\"photo\":\"PH1\",\"u\":0.5},{\"photo\":\"PH2\",\"u\":0.5}]}");
    assertTrue(summary, summary.contains("cross at (") && summary.contains("They cross at 90 degrees"));
    String place = summary.substring(summary.indexOf("cross at (") + 10, summary.indexOf(")", summary.indexOf("cross at (")));
    assertEquals(0, Float.parseFloat(place.split(",") [0].trim()), 30);
    assertEquals(0, Float.parseFloat(place.split(",") [1].trim()), 30);
    summary = run("{\"action\":\"locate_from_photos\",\"rays\":[{\"photo\":\"PH1\",\"u\":0.5}]}");
    assertTrue(summary, summary.contains("give 2 \"rays\""));

    // A photo whose camera recorded a direction 10 degrees off: two things seen in it give it back
    run("{\"action\":\"add_photo\",\"path\":\"" + new java.io.File(folder, "a.jpg").getPath().replace("\\", "\\\\")
        + "\",\"x\":0,\"y\":2000,\"heading\":10,\"fov\":60}");
    // A post straight ahead, another one 30 degrees to the right, at the right edge of the photo
    double edge = 2000 * Math.tan(Math.toRadians(30));
    summary = run("{\"action\":\"refine_photo_pose\",\"photo\":\"PH3\",\"matches\":[{\"u\":0.5,\"plan\":[0,0]},{\"u\":1,\"plan\":["
        + edge + ",0]}]}");
    assertTrue(summary, summary.contains("its place kept") && summary.contains("turned by -10 degrees to 0 degrees"));
    // With a third one its place is found too, from 3 m away
    run("{\"action\":\"add_photo\",\"path\":\"" + new java.io.File(folder, "a.jpg").getPath().replace("\\", "\\\\")
        + "\",\"x\":250,\"y\":1800,\"heading\":5,\"fov\":60}");
    summary = run("{\"action\":\"refine_photo_pose\",\"photo\":\"PH4\",\"matches\":[{\"u\":0.5,\"plan\":[0,0]},{\"u\":1,\"plan\":["
        + edge + ",0]},{\"u\":0,\"plan\":[" + (-edge) + ",0]},{\"u\":0.5,\"plan\":[0,-1500]}]}");
    assertTrue(summary, summary.contains("its place moved by"));
    for (String line : run("{\"action\":\"list_photos\"}").split("\n")) {
      if (line.contains("PH4")) {
        String at = line.substring(line.indexOf("at (") + 4, line.indexOf(")"));
        assertEquals(line, 0, Float.parseFloat(at.split(",") [0].trim()), 40);
        assertEquals(line, 2000, Float.parseFloat(at.split(",") [1].trim()), 60);
      }
    }
    summary = run("{\"action\":\"remove_photos\",\"ids\":[\"PH3\",\"PH4\"]}");
    assertTrue(summary, summary.contains("Removed 2 photos"));
    assertEquals(4, this.home.getPolylines().size());
    summary = run("{\"action\":\"photo_viewpoint\",\"photo\":\"PH9\"}");
    assertTrue(summary, summary.contains("no photo PH9"));
  }
}
