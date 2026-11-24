/*
 * SVGPathParser.java
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
import java.awt.geom.GeneralPath;
import java.text.ParseException;

/**
 * A parser of the data of a <a href="http://www.w3.org/TR/SVG11/paths.html">SVG path</a>,
 * as written in the <code>d</code> attribute of a <code>path</code> element:
 * the commands <code>M L H V C S Q T A Z</code> and their relative versions in lower case,
 * a command being repeated when more numbers follow its parameters.
 * Elliptical arcs are replaced by cubic curves.
 */
public class SVGPathParser {
  private final String      path;
  private int               index;
  private final GeneralPath shape = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
  // The current point
  private float             x;
  private float             y;
  // The start of the current subpath
  private float             startX;
  private float             startY;
  // The last control point of a curve, or the current point after another command
  private float             controlX;
  private float             controlY;
  // true as long as nothing was drawn from the start of the current subpath
  private boolean           moved;
  private boolean           started;

  private SVGPathParser(String path) {
    this.path = path;
  }

  /**
   * Returns the shape described by <code>path</code>. A shape is empty if the
   * path contains only white spaces.
   * @throws ParseException if the path isn't correctly written. The index of the
   *            first wrong character is given by {@link ParseException#getErrorOffset()}.
   */
  public static Shape parse(String path) throws ParseException {
    SVGPathParser parser = new SVGPathParser(path);
    parser.parse();
    return parser.shape;
  }

  private void parse() throws ParseException {
    skipWhiteSpaces();
    while (this.index < this.path.length()) {
      char command = this.path.charAt(this.index);
      if (!this.started && command != 'M' && command != 'm') {
        throw new ParseException("A path starts with a move", this.index);
      }
      this.index++;
      switch (command) {
        case 'M' :
        case 'm' :
          parseMove(command == 'm');
          break;
        case 'L' :
        case 'l' :
          parseLines(command == 'l');
          break;
        case 'H' :
        case 'h' :
        case 'V' :
        case 'v' :
          parseStraightLines(command == 'H' || command == 'h', command == 'h' || command == 'v');
          break;
        case 'C' :
        case 'c' :
        case 'S' :
        case 's' :
          parseCubicCurves(command == 'S' || command == 's', command == 'c' || command == 's');
          break;
        case 'Q' :
        case 'q' :
        case 'T' :
        case 't' :
          parseQuadraticCurves(command == 'T' || command == 't', command == 'q' || command == 't');
          break;
        case 'A' :
        case 'a' :
          parseArcs(command == 'a');
          break;
        case 'Z' :
        case 'z' :
          closePath();
          break;
        default :
          throw new ParseException("Unknown command " + command, this.index - 1);
      }
      skipWhiteSpaces();
    }
  }

  // ---- Commands ----

  private void parseMove(boolean relative) throws ParseException {
    skipWhiteSpaces();
    float moveX = parseNumber();
    skipCommaAndWhiteSpaces();
    float moveY = parseNumber();
    if (relative) {
      moveX += this.x;
      moveY += this.y;
    }
    this.x =
    this.startX =
    this.controlX = moveX;
    this.y =
    this.startY =
    this.controlY = moveY;
    this.moved = true;
    this.started = true;
    // The pairs of numbers that follow are lines
    if (skipToNextNumber()) {
      parseLines(relative);
    }
  }

  private void parseLines(boolean relative) throws ParseException {
    skipWhiteSpaces();
    do {
      float lineX = parseNumber();
      skipCommaAndWhiteSpaces();
      float lineY = parseNumber();
      if (relative) {
        lineX += this.x;
        lineY += this.y;
      }
      lineTo(lineX, lineY);
    } while (skipToNextNumber());
  }

  private void parseStraightLines(boolean horizontal, boolean relative) throws ParseException {
    skipWhiteSpaces();
    do {
      float value = parseNumber();
      if (horizontal) {
        lineTo(relative ? this.x + value : value, this.y);
      } else {
        lineTo(this.x, relative ? this.y + value : value);
      }
    } while (skipToNextNumber());
  }

  private void parseCubicCurves(boolean smooth, boolean relative) throws ParseException {
    skipWhiteSpaces();
    do {
      float originX = relative ? this.x : 0;
      float originY = relative ? this.y : 0;
      float x1;
      float y1;
      if (smooth) {
        // The first control point mirrors the last one
        x1 = 2 * this.x - this.controlX;
        y1 = 2 * this.y - this.controlY;
      } else {
        x1 = originX + parseNumber();
        skipCommaAndWhiteSpaces();
        y1 = originY + parseNumber();
        skipCommaAndWhiteSpaces();
      }
      float x2 = originX + parseNumber();
      skipCommaAndWhiteSpaces();
      float y2 = originY + parseNumber();
      skipCommaAndWhiteSpaces();
      float endX = originX + parseNumber();
      skipCommaAndWhiteSpaces();
      float endY = originY + parseNumber();
      startDrawing();
      this.shape.curveTo(x1, y1, x2, y2, endX, endY);
      this.controlX = x2;
      this.controlY = y2;
      this.x = endX;
      this.y = endY;
    } while (skipToNextNumber());
  }

  private void parseQuadraticCurves(boolean smooth, boolean relative) throws ParseException {
    skipWhiteSpaces();
    do {
      float originX = relative ? this.x : 0;
      float originY = relative ? this.y : 0;
      float x1;
      float y1;
      if (smooth) {
        x1 = 2 * this.x - this.controlX;
        y1 = 2 * this.y - this.controlY;
      } else {
        x1 = originX + parseNumber();
        skipCommaAndWhiteSpaces();
        y1 = originY + parseNumber();
        skipCommaAndWhiteSpaces();
      }
      float endX = originX + parseNumber();
      skipCommaAndWhiteSpaces();
      float endY = originY + parseNumber();
      startDrawing();
      this.shape.quadTo(x1, y1, endX, endY);
      this.controlX = x1;
      this.controlY = y1;
      this.x = endX;
      this.y = endY;
    } while (skipToNextNumber());
  }

  private void parseArcs(boolean relative) throws ParseException {
    skipWhiteSpaces();
    do {
      float radiusX = parseNumber();
      skipCommaAndWhiteSpaces();
      float radiusY = parseNumber();
      skipCommaAndWhiteSpaces();
      float angle = parseNumber();
      skipCommaAndWhiteSpaces();
      boolean largeArc = parseFlag();
      skipCommaAndWhiteSpaces();
      boolean sweep = parseFlag();
      skipCommaAndWhiteSpaces();
      float endX = parseNumber();
      skipCommaAndWhiteSpaces();
      float endY = parseNumber();
      if (relative) {
        endX += this.x;
        endY += this.y;
      }
      arcTo(radiusX, radiusY, angle, largeArc, sweep, endX, endY);
    } while (skipToNextNumber());
  }

  private void closePath() {
    if (!this.moved) {
      this.shape.closePath();
    }
    this.x =
    this.controlX = this.startX;
    this.y =
    this.controlY = this.startY;
  }

  // ---- Drawing ----

  /**
   * Adds to the shape the start of the current subpath if nothing was drawn from it yet.
   */
  private void startDrawing() {
    if (this.moved) {
      this.shape.moveTo(this.startX, this.startY);
      this.moved = false;
    }
  }

  private void lineTo(float lineX, float lineY) {
    startDrawing();
    this.shape.lineTo(lineX, lineY);
    this.x =
    this.controlX = lineX;
    this.y =
    this.controlY = lineY;
  }

  /**
   * Adds the arc of an ellipse of the given radii, turned of <code>angle</code> degrees,
   * that goes from the current point to (<code>endX</code>, <code>endY</code>), as cubic
   * curves of a quarter of the ellipse at most. Computed as described in the
   * <a href="http://www.w3.org/TR/SVG11/implnote.html#ArcImplementationNotes">implementation notes of SVG</a>.
   */
  private void arcTo(float radiusX, float radiusY, float angle,
                     boolean largeArc, boolean sweep, float endX, float endY) {
    double rx = Math.abs(radiusX);
    double ry = Math.abs(radiusY);
    if (rx == 0 || ry == 0) {
      lineTo(endX, endY);
      return;
    }
    startDrawing();
    if (endX == this.x && endY == this.y) {
      return;
    }
    double phi = Math.toRadians(angle % 360.);
    double cosPhi = Math.cos(phi);
    double sinPhi = Math.sin(phi);
    // The start point in the frame of the ellipse, centered between the two points
    double dx = (this.x - endX) / 2.;
    double dy = (this.y - endY) / 2.;
    double x1 = cosPhi * dx + sinPhi * dy;
    double y1 = -sinPhi * dx + cosPhi * dy;
    // Radii too small to join the two points are enlarged
    double lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry);
    if (lambda > 1) {
      double scale = Math.sqrt(lambda);
      rx *= scale;
      ry *= scale;
    }
    // The center of the ellipse
    double denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
    double coefficient = denominator == 0
        ? 0
        : Math.sqrt(Math.max(0, (rx * rx * ry * ry - denominator) / denominator));
    if (largeArc == sweep) {
      coefficient = -coefficient;
    }
    double centerX1 = coefficient * rx * y1 / ry;
    double centerY1 = -coefficient * ry * x1 / rx;
    double centerX = cosPhi * centerX1 - sinPhi * centerY1 + (this.x + endX) / 2.;
    double centerY = sinPhi * centerX1 + cosPhi * centerY1 + (this.y + endY) / 2.;
    // The angles of the two points seen from the center, on a circle of radius 1
    double startAngle = Math.atan2((y1 - centerY1) / ry, (x1 - centerX1) / rx);
    double endAngle = Math.atan2((-y1 - centerY1) / ry, (-x1 - centerX1) / rx);
    double extent = endAngle - startAngle;
    if (sweep && extent < 0) {
      extent += 2 * Math.PI;
    } else if (!sweep && extent > 0) {
      extent -= 2 * Math.PI;
    }

    int curveCount = Math.max(1, (int)Math.ceil(Math.abs(extent) / (Math.PI / 2) - 1E-6));
    double curveExtent = extent / curveCount;
    // The distance of the control points to the ends of a curve that draws an arc of circle
    double controlDistance = 4. / 3. * Math.tan(curveExtent / 4);
    double cos1 = Math.cos(startAngle);
    double sin1 = Math.sin(startAngle);
    for (int i = 1; i <= curveCount; i++) {
      double curveEndAngle = startAngle + i * curveExtent;
      double cos2 = Math.cos(curveEndAngle);
      double sin2 = Math.sin(curveEndAngle);
      double controlX1 = rx * (cos1 - controlDistance * sin1);
      double controlY1 = ry * (sin1 + controlDistance * cos1);
      double controlX2 = rx * (cos2 + controlDistance * sin2);
      double controlY2 = ry * (sin2 - controlDistance * cos2);
      float curveEndX;
      float curveEndY;
      if (i == curveCount) {
        curveEndX = endX;
        curveEndY = endY;
      } else {
        curveEndX = (float)(centerX + cosPhi * rx * cos2 - sinPhi * ry * sin2);
        curveEndY = (float)(centerY + sinPhi * rx * cos2 + cosPhi * ry * sin2);
      }
      this.shape.curveTo(
          (float)(centerX + cosPhi * controlX1 - sinPhi * controlY1),
          (float)(centerY + sinPhi * controlX1 + cosPhi * controlY1),
          (float)(centerX + cosPhi * controlX2 - sinPhi * controlY2),
          (float)(centerY + sinPhi * controlX2 + cosPhi * controlY2),
          curveEndX, curveEndY);
      cos1 = cos2;
      sin1 = sin2;
    }
    this.x =
    this.controlX = endX;
    this.y =
    this.controlY = endY;
  }

  // ---- Characters ----

  private static boolean isWhiteSpace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  private void skipWhiteSpaces() {
    while (this.index < this.path.length()
           && isWhiteSpace(this.path.charAt(this.index))) {
      this.index++;
    }
  }

  /**
   * Skips the white spaces and the comma that may separate two numbers.
   */
  private void skipCommaAndWhiteSpaces() {
    skipWhiteSpaces();
    if (this.index < this.path.length()
        && this.path.charAt(this.index) == ',') {
      this.index++;
      skipWhiteSpaces();
    }
  }

  /**
   * Skips the separators after the parameters of a command, and returns <code>true</code>
   * if a number follows them, which repeats the command.
   * @throws ParseException if a comma isn't followed by a number
   */
  private boolean skipToNextNumber() throws ParseException {
    skipWhiteSpaces();
    boolean comma = this.index < this.path.length()
        && this.path.charAt(this.index) == ',';
    if (comma) {
      this.index++;
      skipWhiteSpaces();
    }
    if (this.index < this.path.length()) {
      char c = this.path.charAt(this.index);
      if (c >= '0' && c <= '9' || c == '-' || c == '+' || c == '.') {
        return true;
      }
    }
    if (comma) {
      throw new ParseException("Number expected", this.index);
    }
    return false;
  }

  /**
   * Returns the flag 0 or 1 at the current index.
   */
  private boolean parseFlag() throws ParseException {
    if (this.index < this.path.length()) {
      char c = this.path.charAt(this.index);
      if (c == '0' || c == '1') {
        this.index++;
        return c == '1';
      }
    }
    throw new ParseException("Flag 0 or 1 expected", this.index);
  }

  /**
   * Returns the number at the current index: a sign, digits with a point
   * among them, and an exponent, as <code>-12</code>, <code>.5</code>,
   * <code>3.</code> or <code>1.5e-3</code>. A number ends where a character can't
   * belong to it, as in <code>10-5</code> or <code>.5.5</code>, which are two numbers.
   */
  private float parseNumber() throws ParseException {
    int start = this.index;
    int length = this.path.length();
    if (this.index < length
        && (this.path.charAt(this.index) == '-' || this.path.charAt(this.index) == '+')) {
      this.index++;
    }
    int digits = skipDigits();
    if (this.index < length && this.path.charAt(this.index) == '.') {
      this.index++;
      digits += skipDigits();
    }
    if (digits == 0) {
      throw new ParseException("Number expected", start);
    }
    if (this.index < length
        && (this.path.charAt(this.index) == 'e' || this.path.charAt(this.index) == 'E')) {
      this.index++;
      if (this.index < length
          && (this.path.charAt(this.index) == '-' || this.path.charAt(this.index) == '+')) {
        this.index++;
      }
      if (skipDigits() == 0) {
        throw new ParseException("Exponent expected", this.index);
      }
    }
    try {
      return Float.parseFloat(this.path.substring(start, this.index));
    } catch (NumberFormatException ex) {
      throw new ParseException("Number expected", start);
    }
  }

  private int skipDigits() {
    int start = this.index;
    while (this.index < this.path.length()
           && this.path.charAt(this.index) >= '0'
           && this.path.charAt(this.index) <= '9') {
      this.index++;
    }
    return this.index - start;
  }
}
