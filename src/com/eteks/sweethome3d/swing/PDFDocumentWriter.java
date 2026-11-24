/*
 * PDFDocumentWriter.java
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
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Writes a PDF 1.4 file, page after page, from what is drawn in the graphics context of each page:
 * <pre>
 * PDFDocumentWriter writer = new PDFDocumentWriter(out);
 * writer.setTitle("My home");
 * Graphics2D g2D = writer.createPage(595, 842);
 * // Draw in g2D
 * writer.close();</pre>
 * Shapes are paths filled or stroked with colors, axial shadings or tiling patterns, images
 * are kept with their transparency, and texts are the outlines of their characters: the file
 * contains no font. The origin of a page is at its top left corner and its unit is 1/72 inch,
 * as with <code>java.awt.print.Printable</code>.
 */
public class PDFDocumentWriter {
  private final OutputStream   out;
  private long                 offset;
  // Offsets of the objects in the file, the object of number n being at index n - 1
  private final List<Long>     objectOffsets = new ArrayList<Long>();
  private final List<Integer>  pages = new ArrayList<Integer>();
  private final int            catalogObject;
  private final int            pagesObject;
  private final int            resourcesObject;
  private final Map<String, Integer>        graphicsStates = new LinkedHashMap<String, Integer>();
  private final Map<String, Integer>        images = new LinkedHashMap<String, Integer>();
  private final Map<String, Integer>        patterns = new LinkedHashMap<String, Integer>();
  private final Map<BufferedImage, String>  imageNames = new IdentityHashMap<BufferedImage, String>();
  private final Map<Integer, String>        alphaNames = new HashMap<Integer, String>();
  private final Map<String, String>         patternNames = new HashMap<String, String>();
  private String               title;
  private String               author;
  private String               creator;
  private Date                 creationDate;
  private StringBuilder        pageContent;
  private float                pageWidth;
  private float                pageHeight;
  private boolean              closed;
  private IOException          exception;

  /**
   * Creates a writer of a PDF file in <code>out</code>.
   */
  public PDFDocumentWriter(OutputStream out) throws IOException {
    this.out = out;
    // The second line tells this file contains bytes which aren't characters
    write(new byte [] {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', '%', (byte)0xE2, (byte)0xE3, (byte)0xCF, (byte)0xD3, '\n'});
    this.catalogObject = createObject();
    this.pagesObject = createObject();
    this.resourcesObject = createObject();
  }

  /**
   * Sets the title of the document.
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Sets the name of the person who creates the document.
   */
  public void setAuthor(String author) {
    this.author = author;
  }

  /**
   * Sets the name of the program which creates the document.
   */
  public void setCreator(String creator) {
    this.creator = creator;
  }

  /**
   * Sets the date of the creation of the document.
   */
  public void setCreationDate(Date creationDate) {
    this.creationDate = creationDate;
  }

  /**
   * Ends the page in progress if there's one, and returns the graphics context
   * of a new page of the given size in points.
   */
  public Graphics2D createPage(float width, float height) throws IOException {
    endPage();
    this.pageWidth = width;
    this.pageHeight = height;
    this.pageContent = new StringBuilder();
    return new PDFVectorGraphics2D(this);
  }

  /**
   * Writes the page in progress.
   */
  private void endPage() throws IOException {
    if (this.exception != null) {
      throw this.exception;
    }
    if (this.pageContent != null) {
      int contentObject = createObject();
      writeStream(contentObject, "", getBytes(this.pageContent.toString()));
      int pageObject = createObject();
      writeObject(pageObject, "<< /Type /Page /Parent " + this.pagesObject + " 0 R /MediaBox [0 0 "
          + format(this.pageWidth) + " " + format(this.pageHeight) + "] /Resources " + this.resourcesObject
          + " 0 R /Contents " + contentObject + " 0 R /Group << /S /Transparency /CS /DeviceRGB >> >>");
      this.pages.add(pageObject);
      this.pageContent = null;
    }
  }

  /**
   * Writes the last page, the description of the document and the table of its objects.
   * The stream isn't closed.
   */
  public void close() throws IOException {
    if (this.closed) {
      return;
    }
    this.closed = true;
    endPage();

    StringBuilder kids = new StringBuilder();
    for (Integer page : this.pages) {
      kids.append(page).append(" 0 R ");
    }
    writeObject(this.pagesObject, "<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + this.pages.size() + " >>");
    writeObject(this.catalogObject, "<< /Type /Catalog /Pages " + this.pagesObject + " 0 R >>");
    writeObject(this.resourcesObject, "<< /ProcSet [/PDF /ImageB /ImageC]"
        + getDictionary("ExtGState", this.graphicsStates)
        + getDictionary("XObject", this.images)
        + getDictionary("Pattern", this.patterns) + " >>");

    int informationObject = createObject();
    StringBuilder information = new StringBuilder("<<");
    if (this.title != null) {
      information.append(" /Title ").append(getTextString(this.title));
    }
    if (this.author != null) {
      information.append(" /Author ").append(getTextString(this.author));
    }
    if (this.creator != null) {
      information.append(" /Creator ").append(getTextString(this.creator));
    }
    information.append(" /Producer ").append(getTextString("Sweet Home 3D"));
    if (this.creationDate != null) {
      String date = getDateString(this.creationDate);
      information.append(" /CreationDate ").append(date).append(" /ModDate ").append(date);
    }
    information.append(" >>");
    writeObject(informationObject, information.toString());

    // Table of the objects: lines of 20 bytes
    long tableOffset = this.offset;
    StringBuilder table = new StringBuilder("xref\n0 " + (this.objectOffsets.size() + 1) + "\n0000000000 65535 f \n");
    for (Long objectOffset : this.objectOffsets) {
      if (objectOffset == null) {
        throw new IOException("Missing PDF object");
      }
      table.append(String.format(Locale.US, "%010d 00000 n \n", objectOffset));
    }
    table.append("trailer\n<< /Size ").append(this.objectOffsets.size() + 1)
        .append(" /Root ").append(this.catalogObject).append(" 0 R /Info ").append(informationObject)
        .append(" 0 R >>\nstartxref\n").append(tableOffset).append("\n%%EOF\n");
    write(getBytes(table.toString()));
    this.out.flush();
  }

  private static String getDictionary(String name, Map<String, Integer> objects) {
    if (objects.isEmpty()) {
      return "";
    }
    StringBuilder dictionary = new StringBuilder(" /" + name + " <<");
    for (Map.Entry<String, Integer> entry : objects.entrySet()) {
      dictionary.append(" /").append(entry.getKey()).append(' ').append(entry.getValue()).append(" 0 R");
    }
    return dictionary.append(" >>").toString();
  }

  // ---- Objects ----

  private int createObject() {
    this.objectOffsets.add(null);
    return this.objectOffsets.size();
  }

  private void write(byte [] bytes) throws IOException {
    this.out.write(bytes);
    this.offset += bytes.length;
  }

  private void writeObject(int object, String content) throws IOException {
    this.objectOffsets.set(object - 1, this.offset);
    write(getBytes(object + " 0 obj\n" + content + "\nendobj\n"));
  }

  /**
   * Writes an object with a stream of compressed data.
   * @param entries the entries of its dictionary, except its length and its filter
   */
  private void writeStream(int object, String entries, byte [] data) throws IOException {
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
    deflater.setInput(data);
    deflater.finish();
    ByteArrayOutputStream compressedData = new ByteArrayOutputStream(data.length / 2 + 64);
    byte [] buffer = new byte [8192];
    while (!deflater.finished()) {
      compressedData.write(buffer, 0, deflater.deflate(buffer));
    }
    deflater.end();
    this.objectOffsets.set(object - 1, this.offset);
    write(getBytes(object + " 0 obj\n<< " + entries + (entries.length() > 0 ? " " : "")
        + "/Filter /FlateDecode /Length " + compressedData.size() + " >>\nstream\n"));
    write(compressedData.toByteArray());
    write(getBytes("\nendstream\nendobj\n"));
  }

  private static byte [] getBytes(String text) {
    // Only ASCII characters are written
    byte [] bytes = new byte [text.length()];
    for (int i = 0; i < bytes.length; i++) {
      bytes [i] = (byte)text.charAt(i);
    }
    return bytes;
  }

  /**
   * Returns a string of the description of a document.
   */
  private static String getTextString(String text) {
    boolean ascii = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < ' ' || c > '~') {
        ascii = false;
        break;
      }
    }
    StringBuilder string = new StringBuilder();
    if (ascii) {
      string.append('(');
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '(' || c == ')' || c == '\\') {
          string.append('\\');
        }
        string.append(c);
      }
      string.append(')');
    } else {
      // UTF-16 characters after their byte order mark
      string.append("<FEFF");
      for (int i = 0; i < text.length(); i++) {
        string.append(String.format("%04X", (int)text.charAt(i)));
      }
      string.append('>');
    }
    return string.toString();
  }

  private static String getDateString(Date date) {
    String zone = new SimpleDateFormat("Z", Locale.US).format(date);
    return "(D:" + new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(date)
        + zone.substring(0, 3) + "'" + zone.substring(3) + "')";
  }

  /**
   * Returns a number written with 5 decimals at most and no exponent.
   */
  static String format(double number) {
    if (Double.isNaN(number) || Double.isInfinite(number)) {
      return "0";
    }
    long value = Math.round(Math.abs(number) * 100000);
    if (value == 0) {
      return "0";
    }
    StringBuilder text = new StringBuilder();
    if (number < 0) {
      text.append('-');
    }
    text.append(value / 100000);
    long decimals = value % 100000;
    if (decimals != 0) {
      String digits = String.valueOf(100000 + decimals).substring(1);
      int end = digits.length();
      while (digits.charAt(end - 1) == '0') {
        end--;
      }
      text.append('.').append(digits, 0, end);
    }
    return text.toString();
  }

  private static String format(AffineTransform transform) {
    return format(transform.getScaleX()) + " " + format(transform.getShearY()) + " "
        + format(transform.getShearX()) + " " + format(transform.getScaleY()) + " "
        + format(transform.getTranslateX()) + " " + format(transform.getTranslateY());
  }

  // ---- Resources ----

  /**
   * Returns the name of the graphics state which gives the opacity <code>alpha</code> to what is drawn.
   */
  private String getAlphaStateName(float alpha) throws IOException {
    int key = Math.round(Math.max(0, Math.min(1, alpha)) * 1000);
    String name = this.alphaNames.get(key);
    if (name == null) {
      name = "GS" + (this.graphicsStates.size() + 1);
      int object = createObject();
      writeObject(object, "<< /Type /ExtGState /ca " + format(key / 1000.) + " /CA " + format(key / 1000.) + " >>");
      this.graphicsStates.put(name, object);
      this.alphaNames.put(key, name);
    }
    return name;
  }

  /**
   * Returns the name of the object of <code>image</code>, written the first time it's requested.
   */
  private String getImageName(BufferedImage image) throws IOException {
    String name = this.imageNames.get(image);
    if (name == null) {
      int width = image.getWidth();
      int height = image.getHeight();
      byte [] colors = new byte [width * height * 3];
      byte [] alphas = new byte [width * height];
      boolean transparent = false;
      int [] row = new int [width];
      for (int y = 0, i = 0; y < height; y++) {
        image.getRGB(0, y, width, 1, row, 0, width);
        for (int x = 0; x < width; x++, i++) {
          int argb = row [x];
          colors [i * 3] = (byte)(argb >> 16);
          colors [i * 3 + 1] = (byte)(argb >> 8);
          colors [i * 3 + 2] = (byte)argb;
          alphas [i] = (byte)(argb >>> 24);
          transparent |= alphas [i] != (byte)0xFF;
        }
      }
      String entries = "/Type /XObject /Subtype /Image /Width " + width + " /Height " + height
          + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Interpolate true";
      if (transparent) {
        int maskObject = createObject();
        writeStream(maskObject, "/Type /XObject /Subtype /Image /Width " + width + " /Height " + height
            + " /ColorSpace /DeviceGray /BitsPerComponent 8", alphas);
        entries += " /SMask " + maskObject + " 0 R";
      }
      int object = createObject();
      writeStream(object, entries, colors);
      name = "Im" + (this.images.size() + 1);
      this.images.put(name, object);
      this.imageNames.put(image, name);
    }
    return name;
  }

  /**
   * Returns the name of the pattern which repeats <code>image</code> in the unit square
   * that <code>matrix</code> brings to the page.
   */
  private String getTilingPatternName(BufferedImage image, AffineTransform matrix) throws IOException {
    String imageName = getImageName(image);
    String key = imageName + " " + format(matrix);
    String name = this.patternNames.get(key);
    if (name == null) {
      int object = createObject();
      writeStream(object, "/Type /Pattern /PatternType 1 /PaintType 1 /TilingType 1 /BBox [0 0 1 1] /XStep 1 /YStep 1"
          + " /Resources << /XObject << /" + imageName + " " + this.images.get(imageName) + " 0 R >> >>"
          + " /Matrix [" + format(matrix) + "]", getBytes("/" + imageName + " Do\n"));
      name = "P" + (this.patterns.size() + 1);
      this.patterns.put(name, object);
      this.patternNames.put(key, name);
    }
    return name;
  }

  /**
   * Returns the name of the pattern which shades colors as <code>gradient</code>
   * in the space that <code>matrix</code> brings to the page.
   */
  private String getShadingPatternName(GradientPaint gradient, AffineTransform matrix) throws IOException {
    Color color1 = gradient.getColor1();
    Color color2 = gradient.getColor2();
    String shading = "<< /ShadingType 2 /ColorSpace /DeviceRGB /Coords ["
        + format(gradient.getPoint1().getX()) + " " + format(gradient.getPoint1().getY()) + " "
        + format(gradient.getPoint2().getX()) + " " + format(gradient.getPoint2().getY())
        + "] /Function << /FunctionType 2 /Domain [0 1] /C0 [" + getColorComponents(color1)
        + "] /C1 [" + getColorComponents(color2) + "] /N 1 >> /Extend [true true] >>";
    String key = shading + format(matrix);
    String name = this.patternNames.get(key);
    if (name == null) {
      int object = createObject();
      writeObject(object, "<< /Type /Pattern /PatternType 2 /Shading " + shading + " /Matrix [" + format(matrix) + "] >>");
      name = "P" + (this.patterns.size() + 1);
      this.patterns.put(name, object);
      this.patternNames.put(key, name);
    }
    return name;
  }

  private static String getColorComponents(Color color) {
    return format(color.getRed() / 255.) + " " + format(color.getGreen() / 255.) + " " + format(color.getBlue() / 255.);
  }

  // ---- Drawing ----

  /**
   * The graphics context of a page. The space of the device has its origin at the top left
   * corner of the page, with an y axis which goes down.
   */
  private static class PDFVectorGraphics2D extends VectorGraphics2D {
    private final PDFDocumentWriter writer;
    private final StringBuilder     content;
    // Brings the space of the device to the default space of the page
    private final AffineTransform   pageTransform;

    public PDFVectorGraphics2D(PDFDocumentWriter writer) {
      this.writer = writer;
      this.content = writer.pageContent;
      this.pageTransform = new AffineTransform(1, 0, 0, -1, 0, writer.pageHeight);
      this.content.append(format(this.pageTransform)).append(" cm\n");
    }

    private PDFVectorGraphics2D(PDFVectorGraphics2D graphics) {
      super(graphics);
      this.writer = graphics.writer;
      this.content = graphics.content;
      this.pageTransform = graphics.pageTransform;
    }

    @Override
    protected VectorGraphics2D createCopy() {
      return new PDFVectorGraphics2D(this);
    }

    /**
     * Returns <code>true</code> if this context still draws in the page in progress.
     */
    private boolean isActive() {
      // Nothing can be drawn either with a transformation which flattens everything
      return this.writer.pageContent == this.content
          && !this.writer.closed
          && this.writer.exception == null
          && Math.abs(getDeviceTransform().getDeterminant()) > 1E-12;
    }

    @Override
    protected void writeShape(Shape shape, boolean outlined) {
      if (!isActive()) {
        return;
      }
      try {
        Paint paint = getPaint();
        float opacity = getAlpha();
        String paintOperation;
        if (paint instanceof Color) {
          Color color = (Color)paint;
          opacity *= color.getAlpha() / 255f;
          paintOperation = getColorComponents(color) + (outlined ? " RG\n" : " rg\n");
        } else if (paint instanceof GradientPaint || paint instanceof TexturePaint) {
          AffineTransform matrix = new AffineTransform(this.pageTransform);
          matrix.concatenate(getDeviceTransform());
          String patternName;
          if (paint instanceof GradientPaint) {
            patternName = this.writer.getShadingPatternName((GradientPaint)paint, matrix);
          } else {
            TexturePaint texture = (TexturePaint)paint;
            Rectangle2D anchor = texture.getAnchorRect();
            if (anchor.getWidth() == 0 || anchor.getHeight() == 0) {
              return;
            }
            // The unit square of the pattern, where y goes up, shown in the rectangle of the texture
            matrix.concatenate(new AffineTransform(anchor.getWidth(), 0, 0, -anchor.getHeight(),
                anchor.getX(), anchor.getY() + anchor.getHeight()));
            patternName = this.writer.getTilingPatternName(texture.getImage(), matrix);
          }
          paintOperation = outlined
              ? "/Pattern CS /" + patternName + " SCN\n"
              : "/Pattern cs /" + patternName + " scn\n";
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
        StringBuilder operations = new StringBuilder("q\n");
        appendClip(operations);
        if (!getDeviceTransform().isIdentity()) {
          operations.append(format(getDeviceTransform())).append(" cm\n");
        }
        if (opacity < 1) {
          operations.append('/').append(this.writer.getAlphaStateName(opacity)).append(" gs\n");
        }
        operations.append(paintOperation);
        if (outlined) {
          BasicStroke stroke = (BasicStroke)getStroke();
          operations.append(format(stroke.getLineWidth())).append(" w ");
          operations.append(stroke.getEndCap() == BasicStroke.CAP_ROUND
              ? 1
              : (stroke.getEndCap() == BasicStroke.CAP_SQUARE ? 2 : 0)).append(" J ");
          operations.append(stroke.getLineJoin() == BasicStroke.JOIN_ROUND
              ? 1
              : (stroke.getLineJoin() == BasicStroke.JOIN_BEVEL ? 2 : 0)).append(" j ");
          operations.append(format(Math.max(1, stroke.getMiterLimit()))).append(" M");
          float [] dash = stroke.getDashArray();
          boolean dashed = false;
          if (dash != null) {
            for (float length : dash) {
              dashed |= length > 0;
            }
          }
          if (dashed) {
            operations.append(" [");
            for (int i = 0; i < dash.length; i++) {
              operations.append(i > 0 ? " " : "").append(format(dash [i]));
            }
            operations.append("] ").append(format(stroke.getDashPhase())).append(" d");
          }
          operations.append('\n').append(path).append("S\n");
        } else {
          operations.append(path).append(it.getWindingRule() == PathIterator.WIND_EVEN_ODD ? "f*\n" : "f\n");
        }
        operations.append("Q\n");
        this.content.append(operations);
      } catch (IOException ex) {
        // Reported when the page ends
        this.writer.exception = ex;
      }
    }

    @Override
    protected void writeImage(BufferedImage image, AffineTransform imageTransform) {
      float opacity = getAlpha();
      if (!isActive() || opacity <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0) {
        return;
      }
      try {
        StringBuilder operations = new StringBuilder("q\n");
        appendClip(operations);
        // The unit square of an image, where y goes up, shown in the rectangle of its pixels
        AffineTransform transform = new AffineTransform(getDeviceTransform());
        transform.concatenate(imageTransform);
        transform.concatenate(new AffineTransform(image.getWidth(), 0, 0, -image.getHeight(), 0, image.getHeight()));
        operations.append(format(transform)).append(" cm\n");
        if (opacity < 1) {
          operations.append('/').append(this.writer.getAlphaStateName(opacity)).append(" gs\n");
        }
        operations.append('/').append(this.writer.getImageName(image)).append(" Do\nQ\n");
        this.content.append(operations);
      } catch (IOException ex) {
        // Reported when the page ends
        this.writer.exception = ex;
      }
    }

    private void appendClip(StringBuilder operations) {
      Shape clip = getDeviceClip();
      if (clip != null) {
        PathIterator it = clip.getPathIterator(null);
        String path = getPath(it);
        // An empty clip lets nothing appear
        operations.append(path.length() > 0 ? path : "0 0 m\n")
            .append(it.getWindingRule() == PathIterator.WIND_EVEN_ODD ? "W* n\n" : "W n\n");
      }
    }

    private static String getPath(PathIterator it) {
      StringBuilder path = new StringBuilder();
      double [] coords = new double [6];
      double x = 0;
      double y = 0;
      double startX = 0;
      double startY = 0;
      for ( ; !it.isDone(); it.next()) {
        switch (it.currentSegment(coords)) {
          case PathIterator.SEG_MOVETO :
            path.append(format(coords [0])).append(' ').append(format(coords [1])).append(" m\n");
            x = startX = coords [0];
            y = startY = coords [1];
            break;
          case PathIterator.SEG_LINETO :
            path.append(format(coords [0])).append(' ').append(format(coords [1])).append(" l\n");
            x = coords [0];
            y = coords [1];
            break;
          case PathIterator.SEG_QUADTO :
            // The same curve with two control points
            path.append(format(x + (coords [0] - x) * 2 / 3)).append(' ')
                .append(format(y + (coords [1] - y) * 2 / 3)).append(' ')
                .append(format(coords [2] + (coords [0] - coords [2]) * 2 / 3)).append(' ')
                .append(format(coords [3] + (coords [1] - coords [3]) * 2 / 3)).append(' ')
                .append(format(coords [2])).append(' ').append(format(coords [3])).append(" c\n");
            x = coords [2];
            y = coords [3];
            break;
          case PathIterator.SEG_CUBICTO :
            for (int i = 0; i < 6; i++) {
              path.append(format(coords [i])).append(' ');
            }
            path.append("c\n");
            x = coords [4];
            y = coords [5];
            break;
          default :
            path.append("h\n");
            x = startX;
            y = startY;
            break;
        }
      }
      return path.toString();
    }
  }
}
