/*
 * SVGVectorGraphics2D.java
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
package com.eteks.sweethome3d.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * A graphics context which writes a SVG 1.1 file: shapes are <code>path</code> elements
 * with their transformation, colors, gradients and textures (<code>linearGradient</code> and
 * <code>pattern</code> elements), images are PNG images kept in the file, and texts are
 * the outlines of their characters. The file is complete once {@link #close() close} is called.
 */
public class SVGVectorGraphics2D extends VectorGraphics2D {
  private static final MathContext PRECISION = new MathContext(7);

  /**
   * The file shared by a context and the ones created from it.
   */
  private static class Document {
    private final Writer                     writer;
    private final Map<BufferedImage, String> imageIds = new IdentityHashMap<BufferedImage, String>();
    private Shape                            lastClip;
    private String                           lastClipId;
    private int                              idCount;
    private IOException                      exception;

    public Document(Writer writer) {
      this.writer = writer;
    }

    public void write(String text) {
      if (this.exception == null) {
        try {
          this.writer.write(text);
        } catch (IOException ex) {
          // Reported when the file is closed
          this.exception = ex;
        }
      }
    }

    public String createId(String prefix) {
      return prefix + (++this.idCount);
    }
  }

  private final Document document;

  /**
   * Creates a context which writes a SVG image of the given size in <code>out</code>.
   * @param title  the title of the image or <code>null</code>
   * @param author the name of the person who creates it or <code>null</code>
   */
  public SVGVectorGraphics2D(OutputStream out, Dimension size, String title, String author) throws IOException {
    this.document = new Document(new BufferedWriter(new OutputStreamWriter(out, "UTF-8")));
    StringBuilder header = new StringBuilder();
    header.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
    header.append("<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
    header.append(" width=\"").append(size.width).append("px\" height=\"").append(size.height).append("px\"");
    header.append(" viewBox=\"0 0 ").append(size.width).append(' ').append(size.height).append("\">\n");
    if (title != null && title.length() > 0) {
      header.append("<title>").append(escape(title)).append("</title>\n");
    }
    // English date to avoid letters depending on the language of the user
    header.append("<desc>Creator: Sweet Home 3D");
    if (author != null && author.length() > 0) {
      header.append(" Source: ").append(escape(author));
    }
    header.append(" Date: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH).format(new Date()));
    header.append("</desc>\n");
    this.document.write(header.toString());
  }

  private SVGVectorGraphics2D(SVGVectorGraphics2D graphics) {
    super(graphics);
    this.document = graphics.document;
  }

  @Override
  protected VectorGraphics2D createCopy() {
    return new SVGVectorGraphics2D(this);
  }

  /**
   * Writes the end of the file, and throws the exception which prevented from writing it if any.
   * The stream isn't closed.
   */
  public void close() throws IOException {
    this.document.write("</svg>\n");
    if (this.document.exception != null) {
      throw this.document.exception;
    }
    this.document.writer.flush();
  }

  @Override
  protected void writeShape(Shape shape, boolean outlined) {
    Paint paint = getPaint();
    float opacity = getAlpha();
    String paintValue;
    if (paint instanceof Color) {
      Color color = (Color)paint;
      opacity *= color.getAlpha() / 255f;
      paintValue = getColorValue(color);
    } else if (paint instanceof GradientPaint) {
      paintValue = "url(#" + writeGradient((GradientPaint)paint) + ")";
    } else if (paint instanceof TexturePaint) {
      String patternId = writePattern((TexturePaint)paint);
      if (patternId == null) {
        return;
      }
      paintValue = "url(#" + patternId + ")";
    } else {
      writeShapeAsImage(shape, outlined);
      return;
    }
    if (opacity <= 0) {
      return;
    }

    PathIterator it = shape.getPathIterator(null);
    String path = getPath(it);
    if (path.length() == 0) {
      return;
    }
    StringBuilder element = new StringBuilder();
    String clipId = getClipId();
    if (clipId != null) {
      element.append("<g clip-path=\"url(#").append(clipId).append(")\">");
    }
    element.append("<path d=\"").append(path).append('"');
    appendTransform(element, getDeviceTransform());
    if (outlined) {
      BasicStroke stroke = (BasicStroke)getStroke();
      element.append(" fill=\"none\" stroke=\"").append(paintValue).append('"');
      if (opacity < 1) {
        element.append(" stroke-opacity=\"").append(format(opacity)).append('"');
      }
      element.append(" stroke-width=\"").append(format(stroke.getLineWidth())).append('"');
      switch (stroke.getEndCap()) {
        case BasicStroke.CAP_ROUND :
          element.append(" stroke-linecap=\"round\"");
          break;
        case BasicStroke.CAP_SQUARE :
          element.append(" stroke-linecap=\"square\"");
          break;
      }
      switch (stroke.getLineJoin()) {
        case BasicStroke.JOIN_ROUND :
          element.append(" stroke-linejoin=\"round\"");
          break;
        case BasicStroke.JOIN_BEVEL :
          element.append(" stroke-linejoin=\"bevel\"");
          break;
        default :
          element.append(" stroke-miterlimit=\"").append(format(Math.max(1, stroke.getMiterLimit()))).append('"');
          break;
      }
      float [] dash = stroke.getDashArray();
      if (dash != null && dash.length > 0) {
        element.append(" stroke-dasharray=\"");
        for (int i = 0; i < dash.length; i++) {
          element.append(i > 0 ? "," : "").append(format(dash [i]));
        }
        element.append('"');
        if (stroke.getDashPhase() != 0) {
          element.append(" stroke-dashoffset=\"").append(format(stroke.getDashPhase())).append('"');
        }
      }
    } else {
      element.append(" fill=\"").append(paintValue).append('"');
      if (opacity < 1) {
        element.append(" fill-opacity=\"").append(format(opacity)).append('"');
      }
      if (it.getWindingRule() == PathIterator.WIND_EVEN_ODD) {
        element.append(" fill-rule=\"evenodd\"");
      }
    }
    element.append("/>");
    if (clipId != null) {
      element.append("</g>");
    }
    element.append('\n');
    this.document.write(element.toString());
  }

  @Override
  protected void writeImage(BufferedImage image, AffineTransform imageTransform) {
    float opacity = getAlpha();
    if (opacity <= 0) {
      return;
    }
    String imageId = writeImageDefinition(image);
    if (imageId == null) {
      return;
    }
    StringBuilder element = new StringBuilder();
    String clipId = getClipId();
    if (clipId != null) {
      element.append("<g clip-path=\"url(#").append(clipId).append(")\">");
    }
    element.append("<use xlink:href=\"#").append(imageId).append('"');
    AffineTransform transform = new AffineTransform(getDeviceTransform());
    transform.concatenate(imageTransform);
    appendTransform(element, transform);
    if (opacity < 1) {
      element.append(" opacity=\"").append(format(opacity)).append('"');
    }
    element.append("/>");
    if (clipId != null) {
      element.append("</g>");
    }
    element.append('\n');
    this.document.write(element.toString());
  }

  /**
   * Writes the definition of an image if it wasn't written yet, and returns its id.
   */
  private String writeImageDefinition(BufferedImage image) {
    String imageId = this.document.imageIds.get(image);
    if (imageId == null) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        if (!ImageIO.write(image, "png", out)) {
          // Try with a copy of an usual type
          BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
          copy.createGraphics().drawImage(image, 0, 0, null);
          out.reset();
          if (!ImageIO.write(copy, "png", out)) {
            return null;
          }
        }
      } catch (IOException ex) {
        return null;
      }
      imageId = this.document.createId("image");
      this.document.imageIds.put(image, imageId);
      this.document.write("<defs><image id=\"" + imageId + "\" width=\"" + image.getWidth()
          + "\" height=\"" + image.getHeight() + "\" preserveAspectRatio=\"none\" xlink:href=\"data:image/png;base64,"
          + Base64.getEncoder().encodeToString(out.toByteArray()) + "\"/></defs>\n");
    }
    return imageId;
  }

  /**
   * Writes the definition of a gradient in the user space and returns its id.
   */
  private String writeGradient(GradientPaint gradient) {
    String gradientId = this.document.createId("gradient");
    StringBuilder definition = new StringBuilder("<defs><linearGradient id=\"").append(gradientId)
        .append("\" gradientUnits=\"userSpaceOnUse\"")
        .append(" x1=\"").append(format(gradient.getPoint1().getX()))
        .append("\" y1=\"").append(format(gradient.getPoint1().getY()))
        .append("\" x2=\"").append(format(gradient.getPoint2().getX()))
        .append("\" y2=\"").append(format(gradient.getPoint2().getY())).append('"');
    if (gradient.isCyclic()) {
      definition.append(" spreadMethod=\"reflect\"");
    }
    definition.append('>');
    Color [] colors = {gradient.getColor1(), gradient.getColor2()};
    for (int i = 0; i < colors.length; i++) {
      definition.append("<stop offset=\"").append(i).append("\" stop-color=\"").append(getColorValue(colors [i])).append('"');
      if (colors [i].getAlpha() < 255) {
        definition.append(" stop-opacity=\"").append(format(colors [i].getAlpha() / 255f)).append('"');
      }
      definition.append("/>");
    }
    definition.append("</linearGradient></defs>\n");
    this.document.write(definition.toString());
    return gradientId;
  }

  /**
   * Writes the definition of a pattern repeating the image of a texture and returns its id.
   */
  private String writePattern(TexturePaint texture) {
    BufferedImage image = texture.getImage();
    String imageId = writeImageDefinition(image);
    Rectangle2D anchor = texture.getAnchorRect();
    if (imageId == null || anchor.getWidth() == 0 || anchor.getHeight() == 0) {
      return null;
    }
    String patternId = this.document.createId("pattern");
    this.document.write("<defs><pattern id=\"" + patternId + "\" patternUnits=\"userSpaceOnUse\""
        + " x=\"" + format(anchor.getX()) + "\" y=\"" + format(anchor.getY())
        + "\" width=\"" + format(anchor.getWidth()) + "\" height=\"" + format(anchor.getHeight()) + "\">"
        + "<use xlink:href=\"#" + imageId + "\" transform=\"scale(" + format(anchor.getWidth() / image.getWidth())
        + "," + format(anchor.getHeight() / image.getHeight()) + ")\"/></pattern></defs>\n");
    return patternId;
  }

  /**
   * Returns the id of the definition of the current clip, written if it differs from the last one,
   * or <code>null</code> if nothing is clipped.
   */
  private String getClipId() {
    Shape clip = getDeviceClip();
    if (clip == null) {
      return null;
    }
    if (clip != this.document.lastClip) {
      PathIterator it = clip.getPathIterator(null);
      String path = getPath(it);
      String clipId = this.document.createId("clip");
      this.document.write("<defs><clipPath id=\"" + clipId + "\"><path d=\"" + (path.length() > 0 ? path : "M0 0")
          + (it.getWindingRule() == PathIterator.WIND_EVEN_ODD ? "\" clip-rule=\"evenodd" : "")
          + "\"/></clipPath></defs>\n");
      this.document.lastClip = clip;
      this.document.lastClipId = clipId;
    }
    return this.document.lastClipId;
  }

  private static void appendTransform(StringBuilder element, AffineTransform transform) {
    if (!transform.isIdentity()) {
      element.append(" transform=\"matrix(").append(format(transform.getScaleX())).append(',')
          .append(format(transform.getShearY())).append(',').append(format(transform.getShearX())).append(',')
          .append(format(transform.getScaleY())).append(',').append(format(transform.getTranslateX())).append(',')
          .append(format(transform.getTranslateY())).append(")\"");
    }
  }

  private static String getPath(PathIterator it) {
    StringBuilder path = new StringBuilder();
    double [] coords = new double [6];
    for ( ; !it.isDone(); it.next()) {
      int pointCount;
      switch (it.currentSegment(coords)) {
        case PathIterator.SEG_MOVETO :
          path.append('M');
          pointCount = 1;
          break;
        case PathIterator.SEG_LINETO :
          path.append('L');
          pointCount = 1;
          break;
        case PathIterator.SEG_QUADTO :
          path.append('Q');
          pointCount = 2;
          break;
        case PathIterator.SEG_CUBICTO :
          path.append('C');
          pointCount = 3;
          break;
        default :
          path.append('Z');
          pointCount = 0;
          break;
      }
      for (int i = 0; i < pointCount * 2; i++) {
        if (i > 0) {
          path.append(' ');
        }
        path.append(format(coords [i]));
      }
    }
    return path.toString();
  }

  private static String getColorValue(Color color) {
    return String.format("#%06x", color.getRGB() & 0xFFFFFF);
  }

  /**
   * Returns a number written with 7 significant digits at most and no exponent.
   */
  static String format(double number) {
    if (Double.isNaN(number) || Double.isInfinite(number)) {
      return "0";
    } else if (number == Math.rint(number) && Math.abs(number) < 1E9) {
      return String.valueOf((long)number);
    } else {
      BigDecimal decimal = new BigDecimal(number, PRECISION);
      if (decimal.signum() == 0) {
        return "0";
      }
      return decimal.stripTrailingZeros().toPlainString();
    }
  }

  private static String escape(String text) {
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '<' :
          escaped.append("&lt;");
          break;
        case '>' :
          escaped.append("&gt;");
          break;
        case '&' :
          escaped.append("&amp;");
          break;
        case '"' :
          escaped.append("&quot;");
          break;
        default :
          if (c >= ' ' || c == '\n' || c == '\t') {
            escaped.append(c);
          }
          break;
      }
    }
    return escaped.toString();
  }
}
