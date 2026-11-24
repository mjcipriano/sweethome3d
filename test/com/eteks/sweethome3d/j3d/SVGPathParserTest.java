/*
 * SVGPathParserTest.java
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
package com.eteks.sweethome3d.j3d;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.text.ParseException;

import junit.framework.TestCase;

/**
 * Tests the parser of SVG paths.
 */
public class SVGPathParserTest extends TestCase {
  /**
   * Returns the segments of a shape as a path written with absolute commands,
   * its numbers rounded to a thousandth.
   */
  private static String describe(Shape shape) {
    StringBuilder text = new StringBuilder();
    float [] points = new float [6];
    for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
      int type = it.currentSegment(points);
      int count = type == PathIterator.SEG_CLOSE
          ? 0
          : type == PathIterator.SEG_QUADTO
              ? 4
              : type == PathIterator.SEG_CUBICTO ? 6 : 2;
      if (text.length() > 0) {
        text.append(' ');
      }
      text.append("MLQCZ".charAt(type));
      for (int i = 0; i < count; i++) {
        float value = Math.round(points [i] * 1000) / 1000f;
        text.append(i == 0 ? "" : ",");
        if (value == Math.round(value)) {
          text.append(Math.round(value));
        } else {
          text.append(value);
        }
      }
    }
    return text.toString();
  }

  private static void assertPath(String expected, String path) throws ParseException {
    assertEquals(path, expected, describe(SVGPathParser.parse(path)));
  }

  private static void assertWrong(String path) {
    try {
      SVGPathParser.parse(path);
      fail(path + " should be refused");
    } catch (ParseException ex) {
      // Expected
    }
  }

  public void testLines() throws ParseException {
    assertPath("M0,0 L1,0 L1,1 L0,1 Z", "M0,0 L1,0 1,1 0,1 Z");
    assertPath("M0,0 L1,0 L1,1 L0,1 Z", "M 0 0 l 1 0 l 0 1 l -1 0 z");
    assertPath("M0,0 L1,0 L1,1 L0,1 Z", "M0 0L1 0L1 1L0 1Z");
    // The pairs that follow a move are lines, relative ones after m
    assertPath("M1,1 L3,3 L6,0 Z", "m1,1 2,2 3,-3z");
    assertPath("M1,1 L2,2 L3,-3 Z", "M1,1 2,2 3,-3z");
    assertPath("M0,0 L10,0 L20,0 L20,5 L20,6", "M0,0 H10 20 V5 6");
    assertPath("M0,0 L1,1 L2,1 L2,2 L0,2 L0,0", "M0,0 l1,1 h1 v1 H0 V0");
    assertPath("M0,0 L1,1", "  M 0,0\n\tL 1,1\r\n");
    assertEquals(PathIterator.WIND_EVEN_ODD, SVGPathParser.parse("M0,0 L1,0 1,1 Z").getPathIterator(null).getWindingRule());
  }

  public void testNumbers() throws ParseException {
    // A sign, a second point or a letter end a number
    assertPath("M10,-20 L30,-40", "M10-20L30-40");
    assertPath("M-1,-1 L-2,-2", "M-1-1-2-2");
    assertPath("M1,1 L2,2", "M+1+1L+2,+2");
    assertPath("M0.5,0.5 L1.5,0.5", "M.5.5L1.5.5");
    assertPath("M1.5,0.5 L0.5,0.5", "M1.5.5.5.5");
    assertPath("M10,0.2 L15,3", "M1e1,2E-1 L1.5e+1 3");
    assertPath("M1,1 L10,2", "M 1E0 1 L 1.E1 2");
    assertPath("M0,0 L1,1", "M0,0 L1.,1");
    assertPath("M1,2 L3,4", "M 1 , 2 L 3 , 4");
    assertPath("M0,0 L1,1 L2,2 L3,3", "M0,0L1,1,2,2,3,3");
    assertPath("M0,0 L1,1", "M0,0,1,1");
  }

  public void testSubpaths() throws ParseException {
    assertPath("M0,0 L1,1 M5,5 L6,6", "M0,0 L1,1 M5,5 L6,6");
    assertPath("M0,0 L1,1 Z M2,2 L3,3 Z", "M0 0L1 1ZM2 2L3 3Z");
    // A relative move after a closed subpath starts from the start of this subpath
    assertPath("M0,0 L1,1 Z M2,2 L5,5 Z", "M0 0L1 1zm2 2l3 3z");
    assertPath("M0,0 L1,1 Z L5,5", "M0,0 L1,1 z L5,5");
    assertPath("M4,6 L5,7", "M1,2 m3,4 l1,1");
    assertPath("M0,0 L1,1 M2,2 L3,2 L3,3", "M0,0 L1,1 m1,1 1,0 0,1");
    // A move without anything drawn from it isn't kept
    assertPath("", "M0,0");
    assertPath("", "M0,0 Z Z");
    assertPath("M0,0 L1,1 Z", "M0,0 z L1,1 z");
    assertPath("", "");
    assertPath("", "   ");
  }

  public void testCurves() throws ParseException {
    assertPath("M0,0 C1,2,3,4,5,6", "M0,0 C1,2 3,4 5,6");
    assertPath("M0,0 C1,2,3,4,5,6 C12,14,14,16,16,18", "M0,0 c1,2 3,4 5,6 7,8 9,10 11,12");
    // The first control point of a smooth curve mirrors the last one of the previous curve
    assertPath("M100,200 C100,100,250,100,250,200 C250,300,400,300,400,200",
        "M100,200 C100,100 250,100 250,200 S400,300 400,200");
    assertPath("M0,0 C1,2,3,4,5,6 C7,8,12,14,14,16 C16,18,15,17,16,18", "M0,0 c1,2 3,4 5,6 s7,8 9,10 s1,1 2,2");
    assertPath("M0,0 C0,0,7,8,9,10", "M0,0 S7,8 9,10");
    assertPath("M0,0 L5,5 C5,5,7,8,10,0", "M0,0 L5,5 S7,8 10,0");
    assertPath("M0,0 Q1,2,3,4", "M0,0 Q1,2 3,4");
    assertPath("M0,0 Q1,2,3,4 Q8,10,10,12", "M0,0 q1,2 3,4 5,6 7,8");
    assertPath("M0,0 Q1,2,3,4 Q5,6,5,6 Q5,6,8,9", "M0,0 Q1,2 3,4 T5,6 T8,9");
    assertPath("M0,0 Q1,2,3,4 Q5,6,8,10 Q11,14,15,18", "M0,0 q1,2 3,4 t5,6 7,8");
    assertPath("M0,0 Q0,0,5,6", "M0,0 T5,6");
    assertPath("M0,0 L5,0 Q5,0,10,0", "M0,0 h5 T10,0");
    assertPath("M0.2,0 Q0.2,0.5,0.5,0.5 Q0.8,0.5,0.8,0 Z", "M 0.2 0 Q 0.2 0.5 0.5 0.5 Q 0.8 0.5 0.8 0 Z");
  }

  public void testArcs() throws ParseException {
    // Half circles, by one side or the other
    assertPath("M10,10 C10,7.239,12.239,5,15,5 C17.761,5,20,7.239,20,10", "M10,10 A5,5 0 0 1 20,10");
    assertPath("M10,10 C10,12.761,12.239,15,15,15 C17.761,15,20,12.761,20,10", "M10,10 A5,5 0 1 0 20,10");
    assertPath("M10,10 C10,12.761,12.239,15,15,15 C17.761,15,20,12.761,20,10", "M10,10 a5,5 0 0 0 10,0");
    // Flags written without separators
    assertPath("M10,10 C10,7.239,12.239,5,15,5 C17.761,5,20,7.239,20,10", "M10,10 a5,5 0 01 10,0");
    assertPath("M10,10 C10,7.239,12.239,5,15,5 C17.761,5,20,7.239,20,10", "M10,10 a5 5 0 0110 0");
    // A circle drawn with two arcs
    assertPath("M10,10 C10,12.761,12.239,15,15,15 C17.761,15,20,12.761,20,10 C20,7.239,17.761,5,15,5 C12.239,5,10,7.239,10,10",
        "M10,10 a5,5,0,1,0,10,0 a5,5,0,1,0,-10,0");
    assertPath("M10,10 C10,7.239,12.239,5,15,5 C17.761,5,20,7.239,20,10 C20,7.239,22.239,5,25,5 C27.761,5,30,7.239,30,10",
        "M10,10 A5,5 0 0 1 20,10 5,5 0 0 1 30,10");
    // Turned ellipses
    assertPath("M10,10 C13.002,4.616,17.674,1.37,20.435,2.751 C23.197,4.132,23.002,9.616,20,15",
        "M10,10 A5,10 30 0 1 20,15");
    assertPath("M10,10 C7.187,6.881,5.8,3.359,6.531,1.195 C7.263,-0.968,9.956,-1.307,13.264,0.347 "
        + "C16.572,2.001,19.784,5.293,21.293,8.576 C22.803,11.86,22.285,14.43,20,15",
        "M10,10 A5,10 -45 1 1 20,15");
    assertPath("M0,0 C1.643,0.702,2.786,2.226,3,4", "M0,0 A5,5 720 0 1 3,4");
    // Radii too small are enlarged, negative ones are taken as positive
    assertPath("M10,10 C10,7.239,12.239,5,15,5 C17.761,5,20,7.239,20,10", "M10,10 A1,1 0 0 1 20,10");
    assertPath("M10,10 C10,7.239,12.239,5,15,5 C17.761,5,20,7.239,20,10", "M10,10 A-5,-5 0 0 1 20,10");
    // A line when a radius is null, and nothing between two equal points
    assertPath("M10,10 L20,10", "M10,10 A0,5 0 0 1 20,10");
    assertPath("M10,10", "M10,10 A5,5 0 0 1 10,10");
    // No control point to mirror after an arc
    assertPath("M0,0 C2.761,-5.523,9.477,-7.761,15,-5 C20.523,-2.239,22.761,4.477,20,10 Q20,10,30,30",
        "M0,0 A5,5 0 0 1 20,10 T30,30");
    // The whole circle stays in its bounds
    Rectangle2D bounds = SVGPathParser.parse("M0,0 a 25 25 0 1 0 50 0 a 25 25 0 1 0 -50 0").getBounds2D();
    assertEquals(0, bounds.getMinX(), 1E-3);
    assertEquals(50, bounds.getMaxX(), 1E-3);
    assertEquals(-25, bounds.getMinY(), 1E-3);
    assertEquals(25, bounds.getMaxY(), 1E-3);
  }

  public void testWrongPaths() {
    assertWrong("L1,1");
    assertWrong("0,0 L1,1");
    assertWrong("M0,0 L1");
    assertWrong("M0.0.1 1");
    assertWrong("M0,0 X1,1");
    assertWrong("M0,0 L1,1 foo");
    assertWrong("M0,0 L1,,1");
    assertWrong("M0,0 L,1,1");
    assertWrong("M,0,0");
    assertWrong("M0,0 L1,1,");
    assertWrong("M0,0,L1,1");
    assertWrong("M0 0 L 1 1 Z,");
    assertWrong("M0,0 L1,1 Z5");
    assertWrong("M0,0 A5,5 0 2 1 20,10");
    assertWrong("M0,0 A5,5 0 0 1");
    assertWrong("M0,0 L1,1 C1,2");
    assertWrong("M0,0 L1e,1");
    assertWrong("M0,0 L.,1");
    assertWrong("M0,0L1,1-");
    assertWrong("M 0x10,0");
    assertWrong("M0,0 L NaN,1");
    assertWrong("M 0,0 L 1,0   L 2,2");
  }

  /**
   * A wrong path is shown as a square.
   */
  public void testShapeTools() {
    assertEquals("M0,0 L1,1 L0,1 Z", describe(ShapeTools.getShape("M0,0 L1,1 0,1 Z")));
    assertEquals(new Rectangle2D.Float(0, 0, 1, 1), ShapeTools.getShape("M0,0 L1"));
    assertEquals(new Rectangle2D.Float(0, 0, 1, 1), ShapeTools.getShape("L1,1"));
    assertSame(ShapeTools.getShape("M0,0 L3,1 0,1 Z"), ShapeTools.getShape("M0,0 L3,1 0,1 Z"));
  }
}
