/*
 * VectorExportTest.java
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.FileContentManager;
import com.eteks.sweethome3d.swing.HomePDFPrinter;
import com.eteks.sweethome3d.swing.HomePrintableComponent;
import com.eteks.sweethome3d.swing.PDFDocumentWriter;
import com.eteks.sweethome3d.swing.PlanComponent;
import com.eteks.sweethome3d.swing.SVGVectorGraphics2D;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Tests the SVG and PDF files written by the program itself.
 */
public class VectorExportTest {
  /**
   * Draws what the plan and the printed pages use: colors with transparency, dashes,
   * a gradient, a texture, a paint that has to become an image, an image, a text and a clip.
   */
  private static void draw(Graphics2D g2D) {
    g2D.setColor(new Color(255, 0, 0, 128));
    g2D.fillRect(10, 10, 100, 50);
    g2D.setColor(Color.BLUE);
    g2D.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 10, new float [] {8, 4}, 2));
    g2D.draw(new Line2D.Float(10, 80, 200, 80));
    g2D.setStroke(new BasicStroke(1.5f));
    g2D.setPaint(new GradientPaint(0, 100, Color.YELLOW, 100, 100, Color.GREEN));
    g2D.fill(new Ellipse2D.Float(10, 100, 100, 40));

    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    for (int i = 0; i < 4; i++) {
      image.setRGB(i, i, 0xFF204060);
      image.setRGB(3 - i, i, 0x80FF8000);
    }
    g2D.setPaint(new TexturePaint(image, new Rectangle2D.Float(0, 0, 8, 8)));
    g2D.fillRect(120, 100, 60, 40);
    g2D.setPaint(new RadialGradientPaint(250, 120, 30, new float [] {0, 1}, new Color [] {Color.WHITE, Color.BLACK}));
    g2D.fillOval(220, 90, 60, 60);

    Graphics2D g2DCopy = (Graphics2D)g2D.create();
    g2DCopy.translate(20, 160);
    g2DCopy.rotate(0.2);
    g2DCopy.scale(2, 2);
    g2DCopy.clip(new Rectangle(0, 0, 60, 30));
    g2DCopy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
    g2DCopy.drawImage(image, 0, 0, 40, 40, null);
    g2DCopy.setComposite(AlphaComposite.SrcOver);
    g2DCopy.setColor(Color.BLACK);
    g2DCopy.setFont(new Font("SansSerif", Font.BOLD, 14));
    g2DCopy.drawString("Text \u00e9", 5, 20);
    g2DCopy.dispose();
    // The copy changed nothing in the first one
    assertTrue(g2D.getTransform().isIdentity());
    assertEquals(null, g2D.getClip());
    assertTrue(g2D.getFontMetrics().stringWidth("Text") > 0);
  }

  @Test
  public void testSVGGraphics() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SVGVectorGraphics2D g2D = new SVGVectorGraphics2D(out, new Dimension(300, 250), "A <title> & more", "me");
    draw(g2D);
    g2D.close();

    Document document = parseSVG(out.toByteArray());
    Element root = document.getDocumentElement();
    assertEquals("svg", root.getNodeName());
    assertEquals("300px", root.getAttribute("width"));
    assertEquals("0 0 300 250", root.getAttribute("viewBox"));
    assertEquals("A <title> & more", document.getElementsByTagName("title").item(0).getTextContent());
    assertEquals(1, document.getElementsByTagName("linearGradient").getLength());
    assertEquals(1, document.getElementsByTagName("pattern").getLength());
    assertEquals(1, document.getElementsByTagName("clipPath").getLength());
    // The image of the texture drawn once more, and the image of the radial gradient
    assertEquals(2, document.getElementsByTagName("image").getLength());
    // No text element: texts are the outlines of their characters
    assertEquals(0, document.getElementsByTagName("text").getLength());

    Element rectangle = (Element)document.getElementsByTagName("path").item(0);
    assertEquals("#ff0000", rectangle.getAttribute("fill"));
    assertEquals(0.5, Double.parseDouble(rectangle.getAttribute("fill-opacity")), 0.01);
    Element line = (Element)document.getElementsByTagName("path").item(1);
    assertEquals("M10 80L200 80", line.getAttribute("d"));
    assertEquals("none", line.getAttribute("fill"));
    assertEquals("#0000ff", line.getAttribute("stroke"));
    assertEquals("3", line.getAttribute("stroke-width"));
    assertEquals("8,4", line.getAttribute("stroke-dasharray"));
    assertEquals("2", line.getAttribute("stroke-dashoffset"));
    assertEquals("round", line.getAttribute("stroke-linecap"));
    assertEquals("bevel", line.getAttribute("stroke-linejoin"));
    Element ellipse = (Element)document.getElementsByTagName("path").item(2);
    assertTrue(ellipse.getAttribute("fill"), ellipse.getAttribute("fill").startsWith("url(#gradient"));
    Element text = (Element)document.getElementsByTagName("path").item(document.getElementsByTagName("path").getLength() - 1);
    assertTrue(text.getAttribute("transform"), text.getAttribute("transform").startsWith("matrix(1.96"));
    assertEquals("g", text.getParentNode().getNodeName());
    assertTrue(((Element)text.getParentNode()).getAttribute("clip-path").startsWith("url(#clip"));
    String svg = new String(out.toByteArray(), "UTF-8");
    assertTrue(svg.contains("data:image/png;base64,"));
    assertFalse("No exponent in numbers", Pattern.compile("[0-9]E-?[0-9]").matcher(svg.replaceAll("base64,[^\"]*", "")).find());
  }

  @Test
  public void testPDFDocument() throws Exception {
    Locale defaultLocale = Locale.getDefault();
    // Numbers are written the same way with a locale which uses a comma for decimals
    Locale.setDefault(Locale.FRANCE);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PDFDocumentWriter writer = new PDFDocumentWriter(out);
      writer.setTitle("Plan \u00e9t\u00e9 (1)");
      writer.setAuthor("me");
      writer.setCreator("Sweet Home 3D");
      writer.setCreationDate(new Date());
      Graphics2D g2D = writer.createPage(300, 250);
      draw(g2D);
      g2D.dispose();
      g2D = writer.createPage(595.5f, 842);
      g2D.setColor(Color.RED);
      g2D.fillRect(0, 0, 10, 10);
      writer.close();
      // Closing twice changes nothing
      int length = out.size();
      writer.close();
      assertEquals(length, out.size());
    } finally {
      Locale.setDefault(defaultLocale);
    }

    PDFFile pdf = new PDFFile(out.toByteArray());
    assertEquals(2, pdf.pageBoxes.size());
    assertEquals("0 0 300 250", pdf.pageBoxes.get(0));
    assertEquals("0 0 595.5 842", pdf.pageBoxes.get(1));
    assertTrue(pdf.text.substring(pdf.text.indexOf("/Title")), pdf.text.contains("/Title <FEFF0050006C0061006E002000E9007400E90020002800310029>"));
    assertTrue(pdf.text.contains("/Author (me)"));
    assertTrue(pdf.text.contains("/Creator (Sweet Home 3D)"));
    assertTrue(pdf.text.contains("/CreationDate (D:"));
    // No font: texts are the outlines of their characters
    assertFalse(pdf.text.contains("/Font"));
    // The image of the texture with its transparency, and the image of the radial gradient
    assertEquals(4, count(pdf.text, "/Subtype /Image"));
    assertEquals(2, count(pdf.text, "/SMask "));
    assertEquals(1, count(pdf.text, "/PatternType 1"));
    assertEquals(1, count(pdf.text, "/PatternType 2"));
    assertTrue(pdf.text.contains("/ca 0.502 /CA 0.502"));

    String page = pdf.pageContents.get(0);
    assertTrue(page, page.startsWith("1 0 0 -1 0 250 cm\n"));
    assertTrue(page, page.contains("1 0 0 rg\n10 10 m\n110 10 l\n110 60 l\n10 60 l\n10 10 l\nh\nf\n"));
    assertTrue(page, page.contains("0 0 1 RG\n3 w 1 J 2 j 10 M [8 4] 2 d\n10 80 m\n200 80 l\nS\n"));
    assertTrue(page, page.contains("/Pattern cs /P1 scn\n"));
    assertTrue(page, page.contains("/Pattern cs /P2 scn\n"));
    assertTrue(page, page.contains("W n\n"));
    assertTrue(page, page.contains(" Do\n"));
    assertTrue(page, page.contains(" c\n"));
    assertEquals(count(page, "q\n"), count(page, "Q\n"));
    assertFalse("No comma or exponent in numbers", Pattern.compile("[0-9][,E][0-9]").matcher(page).find());
    assertTrue(pdf.pageContents.get(1).contains("1 0 0 rg\n0 0 m\n10 0 l\n"));
  }

  /**
   * Exports the plan of a home to SVG and prints it to PDF.
   */
  @Test
  public void testHomeExports() throws Exception {
    Locale defaultLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      UserPreferences preferences = new DefaultUserPreferences();
      Home home = new Home(250);
      home.setName("Test <home>");
      Wall wall1 = new Wall(0, 0, 500, 0, 20, 250);
      Wall wall2 = new Wall(500, 0, 500, 400, 20, 250);
      wall1.setWallAtEnd(wall2);
      wall2.setWallAtStart(wall1);
      wall2.setPattern(preferences.getPatternsCatalog().getPatterns().get(2));
      home.addWall(wall1);
      home.addWall(wall2);
      Room room = new Room(new float [][] {{0, 0}, {500, 0}, {500, 400}, {0, 400}});
      room.setName("Living room");
      room.setAreaVisible(true);
      room.setFloorTexture(new HomeTexture(preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0)));
      home.addRoom(room);
      int count = 0;
      for (FurnitureCategory category : preferences.getFurnitureCatalog().getCategories()) {
        for (CatalogPieceOfFurniture catalogPiece : category.getFurniture()) {
          if (count < 3 && !catalogPiece.isDoorOrWindow()) {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
            piece.setX(100 + count * 150);
            piece.setY(200);
            piece.setAngle(count * 0.4f);
            piece.setNameVisible(true);
            home.addPieceOfFurniture(piece);
            count++;
            break;
          }
        }
      }
      home.addDimensionLine(new DimensionLine(0, -30, 500, -30, 20));
      Label label = new Label("Hello", 600, 150);
      label.setStyle(new TextStyle(30, true, true));
      label.setAngle(0.3f);
      home.addLabel(label);
      Polyline polyline = new Polyline(new float [][] {{550, 320}, {650, 380}, {780, 330}});
      polyline.setDashStyle(Polyline.DashStyle.DASH_DOT);
      polyline.setEndArrowStyle(Polyline.ArrowStyle.DELTA);
      home.addPolyline(polyline);

      HomeController controller = new HomeController(home, preferences, new SwingViewFactory(),
          new FileContentManager(preferences));
      controller.getView();
      PlanComponent plan = new PlanComponent(home, preferences, controller.getPlanController());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      plan.exportToSVG(out);
      Document document = parseSVG(out.toByteArray());
      assertEquals("Test <home>", document.getElementsByTagName("title").item(0).getTextContent());
      assertTrue(document.getElementsByTagName("path").getLength() + " paths", document.getElementsByTagName("path").getLength() > 20);
      // The icons of the furniture and the pattern of a wall
      assertTrue(document.getElementsByTagName("image").getLength() >= 3);
      String svg = new String(out.toByteArray(), "UTF-8");
      assertTrue(svg.contains("stroke-dasharray"));
      int width = Integer.parseInt(document.getDocumentElement().getAttribute("width").replace("px", ""));
      assertTrue("width " + width, width > 780 && width < 1100);

      out = new ByteArrayOutputStream();
      new HomePDFPrinter(home, preferences, controller, new Font("SansSerif", Font.PLAIN, 12)).write(out);
      PDFFile pdf = new PDFFile(out.toByteArray());
      PageFormat pageFormat = HomePrintableComponent.getPageFormat(home.getPrint());
      assertEquals(new HomePrintableComponent(home, controller, new Font("SansSerif", Font.PLAIN, 12)).getPageCount(),
          pdf.pageBoxes.size());
      assertTrue(pdf.pageBoxes.size() >= 1);
      String [] box = pdf.pageBoxes.get(0).split(" ");
      assertEquals(pageFormat.getWidth(), Double.parseDouble(box [2]), 0.01);
      assertEquals(pageFormat.getHeight(), Double.parseDouble(box [3]), 0.01);
      assertTrue(pdf.text.contains("/Title (Test <home>"));
      assertTrue(pdf.text.contains("/Creator "));
      assertTrue(count(pdf.text, "/Subtype /Image") >= 3);
      String page = pdf.pageContents.get(0);
      assertTrue(count(page, "f\n") + count(page, "f*\n") > 5);
      assertTrue(count(page, "S\n") > 3);
      assertTrue(page.contains(" d\n"));
    } finally {
      Locale.setDefault(defaultLocale);
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }

  private static Document parseSVG(byte [] svg) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(svg));
  }

  private static int count(String text, String part) {
    int count = 0;
    for (int index = text.indexOf(part); index != -1; index = text.indexOf(part, index + part.length())) {
      count++;
    }
    return count;
  }

  /**
   * A PDF file read back from its table of objects: each object is where the table tells,
   * each stream has the length its dictionary tells and can be inflated.
   */
  private static class PDFFile {
    private final String       text;
    private final List<String> pageBoxes = new ArrayList<String>();
    private final List<String> pageContents = new ArrayList<String>();

    public PDFFile(byte [] bytes) throws Exception {
      this.text = new String(bytes, "ISO-8859-1");
      assertTrue(this.text.startsWith("%PDF-1.4\n"));
      assertTrue(this.text.endsWith("%%EOF\n"));
      Matcher startMatcher = Pattern.compile("startxref\n(\\d+)\n%%EOF\n$").matcher(this.text);
      assertTrue(startMatcher.find());
      int tableOffset = Integer.parseInt(startMatcher.group(1));
      assertTrue(this.text.startsWith("xref\n0 ", tableOffset));
      int lineEnd = this.text.indexOf('\n', tableOffset + 5);
      int size = Integer.parseInt(this.text.substring(tableOffset + 7, lineEnd));
      assertEquals("0000000000 65535 f \n", this.text.substring(lineEnd + 1, lineEnd + 21));
      String [] objects = new String [size];
      for (int i = 1; i < size; i++) {
        String line = this.text.substring(lineEnd + 1 + i * 20, lineEnd + 21 + i * 20);
        assertTrue(line, line.matches("\\d{10} 00000 n \n"));
        int offset = Integer.parseInt(line.substring(0, 10));
        assertTrue("Object " + i + " at " + offset, this.text.startsWith(i + " 0 obj\n", offset));
        objects [i] = this.text.substring(offset, this.text.indexOf("endobj\n", offset));
        if (objects [i].contains("stream\n")) {
          // Stream of the length told by its dictionary
          Matcher lengthMatcher = Pattern.compile("/Length (\\d+) >>\nstream\n").matcher(objects [i]);
          assertTrue(lengthMatcher.find());
          int streamLength = Integer.parseInt(lengthMatcher.group(1));
          assertTrue(objects [i].startsWith("\nendstream\n", lengthMatcher.end() + streamLength));
          byte [] data = inflate(bytes, offset + lengthMatcher.end(), streamLength);
          Matcher imageMatcher = Pattern.compile("/Width (\\d+) /Height (\\d+) /ColorSpace /Device(RGB|Gray)").matcher(objects [i]);
          if (imageMatcher.find()) {
            assertEquals(Integer.parseInt(imageMatcher.group(1)) * Integer.parseInt(imageMatcher.group(2))
                * ("RGB".equals(imageMatcher.group(3)) ? 3 : 1), data.length);
          }
          objects [i] = objects [i].substring(0, lengthMatcher.end()) + new String(data, "ISO-8859-1");
        }
      }
      String trailer = this.text.substring(lineEnd + 1 + size * 20);
      Matcher trailerMatcher = Pattern.compile("^trailer\n<< /Size (\\d+) /Root (\\d+) 0 R /Info (\\d+) 0 R >>\n").matcher(trailer);
      assertTrue(trailer, trailerMatcher.find());
      assertEquals(size, Integer.parseInt(trailerMatcher.group(1)));
      String catalog = objects [Integer.parseInt(trailerMatcher.group(2))];
      assertTrue(catalog, catalog.contains("/Type /Catalog"));
      assertTrue(objects [Integer.parseInt(trailerMatcher.group(3))].contains("/Producer"));

      Matcher pagesMatcher = Pattern.compile("/Pages (\\d+) 0 R").matcher(catalog);
      assertTrue(pagesMatcher.find());
      String pages = objects [Integer.parseInt(pagesMatcher.group(1))];
      Matcher kidsMatcher = Pattern.compile("/Kids \\[([^\\]]*)\\] /Count (\\d+)").matcher(pages);
      assertTrue(pages, kidsMatcher.find());
      String [] kids = kidsMatcher.group(1).trim().split(" 0 R ?");
      assertEquals(Integer.parseInt(kidsMatcher.group(2)), kids.length);
      for (String kid : kids) {
        String page = objects [Integer.parseInt(kid.trim())];
        Matcher pageMatcher = Pattern.compile(
            "/Type /Page /Parent (\\d+) 0 R /MediaBox \\[([^\\]]*)\\] /Resources (\\d+) 0 R /Contents (\\d+) 0 R").matcher(page);
        assertTrue(page, pageMatcher.find());
        assertEquals(pagesMatcher.group(1), pageMatcher.group(1));
        this.pageBoxes.add(pageMatcher.group(2));
        String resources = objects [Integer.parseInt(pageMatcher.group(3))];
        String content = objects [Integer.parseInt(pageMatcher.group(4))];
        content = content.substring(content.indexOf("stream\n") + 7);
        this.pageContents.add(content);
        // Every resource used by the page is an object of its resources
        Matcher nameMatcher = Pattern.compile("/((GS|Im|P)\\d+) ").matcher(content);
        while (nameMatcher.find()) {
          Matcher resourceMatcher = Pattern.compile("/" + nameMatcher.group(1) + " (\\d+) 0 R").matcher(resources);
          assertTrue(nameMatcher.group(1), resourceMatcher.find());
          assertTrue(objects [Integer.parseInt(resourceMatcher.group(1))] != null);
        }
      }
    }

    private static byte [] inflate(byte [] bytes, int offset, int length) throws Exception {
      Inflater inflater = new Inflater();
      inflater.setInput(bytes, offset, length);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        assertTrue("Stream which can't be inflated", count > 0 || inflater.finished());
        out.write(buffer, 0, count);
      }
      inflater.end();
      return out.toByteArray();
    }
  }
}
