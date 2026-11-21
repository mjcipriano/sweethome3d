/*
 * HomeFrameBoundsTest.java
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
package com.eteks.sweethome3d.junit;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import com.eteks.sweethome3d.HomeFramePane;

import junit.framework.TestCase;

/**
 * Tests the bounds given to the frame of a home from the ones stored in its file.
 */
public class HomeFrameBoundsTest extends TestCase {
  private static final Dimension MINIMUM_SIZE = new Dimension(640, 400);

  public void testBoundsInScreenAreKept() {
    List<Rectangle> screens = Arrays.asList(new Rectangle(62, 0, 2498, 1440));
    assertEquals(new Rectangle(100, 80, 1000, 700),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(100, 80, 1000, 700), MINIMUM_SIZE, screens));
  }

  /**
   * A window snapped to a side of the screen under Windows goes a few pixels out of it.
   */
  public void testBoundsOutOfScreenAreMovedBack() {
    List<Rectangle> screens = Arrays.asList(new Rectangle(0, 0, 2560, 1440));
    assertEquals(new Rectangle(0, 0, 1294, 1440),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(-7, 0, 1294, 1447), MINIMUM_SIZE, screens));
    assertEquals(new Rectangle(1560, 740, 1000, 700),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(5000, 3000, 1000, 700), MINIMUM_SIZE, screens));
  }

  public void testTooSmallBoundsAreEnlarged() {
    List<Rectangle> screens = Arrays.asList(new Rectangle(0, 0, 1920, 1080));
    assertEquals(new Rectangle(62, 0, 640, 400),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(62, 0, 136, 39), MINIMUM_SIZE, screens));
    // A screen smaller than the minimum size limits the frame
    assertEquals(new Rectangle(0, 0, 500, 300),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(10, 10, 136, 39), MINIMUM_SIZE,
            Arrays.asList(new Rectangle(0, 0, 500, 300))));
  }

  public void testBoundsStayInTheScreenThatShowsThem() {
    List<Rectangle> screens = Arrays.asList(new Rectangle(0, 0, 1920, 1080), new Rectangle(1920, 0, 2560, 1400));
    assertEquals(new Rectangle(2100, 100, 1200, 800),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(2100, 100, 1200, 800), MINIMUM_SIZE, screens));
    // Mostly in the second screen
    assertEquals(new Rectangle(1920, 100, 1200, 800),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(1800, 100, 1200, 800), MINIMUM_SIZE, screens));
    // A screen which was unplugged
    assertEquals(new Rectangle(720, 100, 1200, 800),
        HomeFramePane.getBoundsFittedInScreens(new Rectangle(2100, 100, 1200, 800), MINIMUM_SIZE,
            Arrays.asList(new Rectangle(0, 0, 1920, 1080))));
  }
}
