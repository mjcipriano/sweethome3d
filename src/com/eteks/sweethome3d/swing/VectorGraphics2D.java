/*
 * VectorGraphics2D.java
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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

import javax.swing.ImageIcon;

/**
 * A graphics context which keeps the transformation, the clip, the paint, the stroke,
 * the transparency and the font it's given, and brings every drawing down to three operations
 * its subclasses write in a file of vectors: a shape to fill, a shape to stroke and an image to draw.
 * Texts are drawn with the outlines of their characters, which requires no font in the file.
 * Transparency is the only composition rule: the other rules of <code>AlphaComposite</code>
 * are handled as <code>SRC_OVER</code>.
 */
abstract class VectorGraphics2D extends Graphics2D {
  // Used for the measures of fonts and the description of the device
  private static final BufferedImage MEASURE_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
  private static final int           MAX_RASTER_SIZE = 2048;

  private AffineTransform transform = new AffineTransform();
  // Clip in the device space or null
  private Shape           clip;
  private Paint           paint = Color.BLACK;
  private Color           color = Color.BLACK;
  private Color           background = Color.WHITE;
  private Stroke          stroke = new BasicStroke();
  private Composite       composite = AlphaComposite.SrcOver;
  private Font            font = new Font("Dialog", Font.PLAIN, 12);
  private RenderingHints  hints = new RenderingHints(null);

  protected VectorGraphics2D() {
  }

  /**
   * Creates a context in the same state as <code>graphics</code>.
   */
  protected VectorGraphics2D(VectorGraphics2D graphics) {
    this.transform = new AffineTransform(graphics.transform);
    this.clip = graphics.clip;
    this.paint = graphics.paint;
    this.color = graphics.color;
    this.background = graphics.background;
    this.stroke = graphics.stroke;
    this.composite = graphics.composite;
    this.font = graphics.font;
    this.hints = (RenderingHints)graphics.hints.clone();
  }

  // ---- What subclasses write ----

  /**
   * Writes <code>shape</code>, given in the user space, filled with the current paint,
   * or its outline drawn with the current stroke if <code>outlined</code> is <code>true</code>.
   * The current stroke is then an instance of <code>BasicStroke</code>.
   */
  protected abstract void writeShape(Shape shape, boolean outlined);

  /**
   * Writes <code>image</code>, <code>imageTransform</code> bringing its pixels to the user space.
   */
  protected abstract void writeImage(BufferedImage image, AffineTransform imageTransform);

  /**
   * Returns a context in the same state which writes in the same file.
   */
  protected abstract VectorGraphics2D createCopy();

  // ---- State read by subclasses ----

  /**
   * Returns the clip in the device space, or <code>null</code>. It's never modified:
   * another clip is another object.
   */
  protected Shape getDeviceClip() {
    return this.clip;
  }

  /**
   * Returns the transformation from the user space to the device space, which mustn't be modified.
   */
  protected AffineTransform getDeviceTransform() {
    return this.transform;
  }

  /**
   * Returns the opacity set by the current composite, from 0 to 1.
   */
  protected float getAlpha() {
    return this.composite instanceof AlphaComposite
        ? ((AlphaComposite)this.composite).getAlpha()
        : 1;
  }

  /**
   * Returns an image of the same pixels as <code>image</code>, once they are all loaded.
   */
  protected static BufferedImage getBufferedImage(Image image) {
    if (image instanceof BufferedImage) {
      return (BufferedImage)image;
    }
    // Wait the pixels of the image
    image = new ImageIcon(image).getImage();
    int width = image.getWidth(null);
    int height = image.getHeight(null);
    if (width <= 0 || height <= 0) {
      return null;
    }
    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2D = bufferedImage.createGraphics();
    g2D.drawImage(image, 0, 0, null);
    g2D.dispose();
    return bufferedImage;
  }

  /**
   * Writes <code>shape</code> as an image of it, for the paints and the strokes a file can't describe.
   */
  protected void writeShapeAsImage(Shape shape, boolean outlined) {
    Shape filledShape = outlined ? this.stroke.createStrokedShape(shape) : shape;
    Rectangle2D bounds = this.transform.createTransformedShape(filledShape).getBounds2D();
    if (this.clip != null) {
      Rectangle2D.intersect(bounds, this.clip.getBounds2D(), bounds);
    }
    if (bounds.isEmpty()) {
      return;
    }
    // Twice more pixels than the units of the device, in the limit of a reasonable image
    double scale = Math.min(2, MAX_RASTER_SIZE / Math.max(bounds.getWidth(), bounds.getHeight()));
    int width = Math.max(1, (int)Math.ceil(bounds.getWidth() * scale));
    int height = Math.max(1, (int)Math.ceil(bounds.getHeight() * scale));
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2D = image.createGraphics();
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2D.scale(scale, scale);
    g2D.translate(-bounds.getX(), -bounds.getY());
    g2D.transform(this.transform);
    g2D.setPaint(this.paint);
    g2D.fill(filledShape);
    g2D.dispose();
    AffineTransform previousTransform = this.transform;
    this.transform = new AffineTransform();
    try {
      AffineTransform imageTransform = AffineTransform.getTranslateInstance(bounds.getX(), bounds.getY());
      imageTransform.scale(1 / scale, 1 / scale);
      writeImage(image, imageTransform);
    } finally {
      this.transform = previousTransform;
    }
  }

  // ---- Shapes ----

  @Override
  public void draw(Shape shape) {
    if (shape == null) {
      return;
    }
    if (this.stroke instanceof BasicStroke) {
      writeShape(shape, true);
    } else {
      writeShape(this.stroke.createStrokedShape(shape), false);
    }
  }

  @Override
  public void fill(Shape shape) {
    if (shape != null) {
      writeShape(shape, false);
    }
  }

  @Override
  public void drawLine(int x1, int y1, int x2, int y2) {
    draw(new Line2D.Float(x1, y1, x2, y2));
  }

  @Override
  public void drawRect(int x, int y, int width, int height) {
    draw(new Rectangle(x, y, width, height));
  }

  @Override
  public void fillRect(int x, int y, int width, int height) {
    fill(new Rectangle(x, y, width, height));
  }

  @Override
  public void clearRect(int x, int y, int width, int height) {
    Paint previousPaint = this.paint;
    Composite previousComposite = this.composite;
    this.paint = this.background;
    this.composite = AlphaComposite.SrcOver;
    fill(new Rectangle(x, y, width, height));
    this.paint = previousPaint;
    this.composite = previousComposite;
  }

  @Override
  public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    draw(new RoundRectangle2D.Float(x, y, width, height, arcWidth, arcHeight));
  }

  @Override
  public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    fill(new RoundRectangle2D.Float(x, y, width, height, arcWidth, arcHeight));
  }

  @Override
  public void drawOval(int x, int y, int width, int height) {
    draw(new Ellipse2D.Float(x, y, width, height));
  }

  @Override
  public void fillOval(int x, int y, int width, int height) {
    fill(new Ellipse2D.Float(x, y, width, height));
  }

  @Override
  public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    draw(new Arc2D.Float(x, y, width, height, startAngle, arcAngle, Arc2D.OPEN));
  }

  @Override
  public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    fill(new Arc2D.Float(x, y, width, height, startAngle, arcAngle, Arc2D.PIE));
  }

  @Override
  public void drawPolyline(int [] xPoints, int [] yPoints, int pointCount) {
    if (pointCount > 1) {
      GeneralPath path = new GeneralPath();
      path.moveTo(xPoints [0], yPoints [0]);
      for (int i = 1; i < pointCount; i++) {
        path.lineTo(xPoints [i], yPoints [i]);
      }
      draw(path);
    }
  }

  @Override
  public void drawPolygon(int [] xPoints, int [] yPoints, int pointCount) {
    draw(new Polygon(xPoints, yPoints, pointCount));
  }

  @Override
  public void fillPolygon(int [] xPoints, int [] yPoints, int pointCount) {
    fill(new Polygon(xPoints, yPoints, pointCount));
  }

  @Override
  public void copyArea(int x, int y, int width, int height, int dx, int dy) {
    // What is written can't be read back
  }

  // ---- Texts ----

  @Override
  public void drawString(String text, int x, int y) {
    drawString(text, (float)x, (float)y);
  }

  @Override
  public void drawString(String text, float x, float y) {
    if (text != null && text.length() > 0) {
      fill(this.font.createGlyphVector(getFontRenderContext(), text).getOutline(x, y));
    }
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    drawString(iterator, (float)x, (float)y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    if (iterator != null && iterator.getBeginIndex() != iterator.getEndIndex()) {
      new TextLayout(iterator, getFontRenderContext()).draw(this, x, y);
    }
  }

  @Override
  public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
    fill(glyphVector.getOutline(x, y));
  }

  @Override
  public Font getFont() {
    return this.font;
  }

  @Override
  public void setFont(Font font) {
    if (font != null) {
      this.font = font;
    }
  }

  @Override
  public FontMetrics getFontMetrics(Font font) {
    Graphics2D g2D = MEASURE_IMAGE.createGraphics();
    try {
      g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2D.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      return g2D.getFontMetrics(font);
    } finally {
      g2D.dispose();
    }
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    return new FontRenderContext(null, true, true);
  }

  // ---- Images ----

  @Override
  public boolean drawImage(Image image, int x, int y, ImageObserver observer) {
    return drawImage(image, x, y, null, observer);
  }

  @Override
  public boolean drawImage(Image image, int x, int y, Color backgroundColor, ImageObserver observer) {
    BufferedImage bufferedImage = getBufferedImage(image);
    if (bufferedImage != null) {
      drawImage(bufferedImage, x, y, bufferedImage.getWidth(), bufferedImage.getHeight(), backgroundColor);
    }
    return true;
  }

  @Override
  public boolean drawImage(Image image, int x, int y, int width, int height, ImageObserver observer) {
    return drawImage(image, x, y, width, height, null, observer);
  }

  @Override
  public boolean drawImage(Image image, int x, int y, int width, int height, Color backgroundColor, ImageObserver observer) {
    BufferedImage bufferedImage = getBufferedImage(image);
    if (bufferedImage != null) {
      drawImage(bufferedImage, x, y, width, height, backgroundColor);
    }
    return true;
  }

  @Override
  public boolean drawImage(Image image, int dx1, int dy1, int dx2, int dy2,
                           int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
    return drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null, observer);
  }

  @Override
  public boolean drawImage(Image image, int dx1, int dy1, int dx2, int dy2,
                           int sx1, int sy1, int sx2, int sy2, Color backgroundColor, ImageObserver observer) {
    BufferedImage bufferedImage = getBufferedImage(image);
    if (bufferedImage == null || sx1 == sx2 || sy1 == sy2 || dx1 == dx2 || dy1 == dy2) {
      return true;
    }
    // The part of the image which is drawn
    Rectangle part = new Rectangle(Math.min(sx1, sx2), Math.min(sy1, sy2), Math.abs(sx2 - sx1), Math.abs(sy2 - sy1))
        .intersection(new Rectangle(bufferedImage.getWidth(), bufferedImage.getHeight()));
    if (part.isEmpty()) {
      return true;
    }
    double scaleX = (double)(dx2 - dx1) / (sx2 - sx1);
    double scaleY = (double)(dy2 - dy1) / (sy2 - sy1);
    // The pixel (sx1, sy1) goes to (dx1, dy1)
    AffineTransform imageTransform = new AffineTransform(scaleX, 0, 0, scaleY,
        dx1 + (part.x - sx1) * scaleX, dy1 + (part.y - sy1) * scaleY);
    if (backgroundColor != null) {
      Paint previousPaint = this.paint;
      this.paint = backgroundColor;
      fill(imageTransform.createTransformedShape(new Rectangle(part.width, part.height)));
      this.paint = previousPaint;
    }
    writeImage(bufferedImage.getSubimage(part.x, part.y, part.width, part.height), imageTransform);
    return true;
  }

  private void drawImage(BufferedImage image, int x, int y, int width, int height, Color backgroundColor) {
    if (width == 0 || height == 0) {
      return;
    }
    if (backgroundColor != null) {
      Paint previousPaint = this.paint;
      this.paint = backgroundColor;
      fill(new Rectangle(x, y, width, height));
      this.paint = previousPaint;
    }
    writeImage(image, new AffineTransform((double)width / image.getWidth(), 0, 0,
        (double)height / image.getHeight(), x, y));
  }

  @Override
  public boolean drawImage(Image image, AffineTransform imageTransform, ImageObserver observer) {
    BufferedImage bufferedImage = getBufferedImage(image);
    if (bufferedImage != null) {
      writeImage(bufferedImage, imageTransform != null ? imageTransform : new AffineTransform());
    }
    return true;
  }

  @Override
  public void drawImage(BufferedImage image, BufferedImageOp operation, int x, int y) {
    if (operation != null) {
      image = operation.filter(image, null);
    }
    writeImage(image, AffineTransform.getTranslateInstance(x, y));
  }

  @Override
  public void drawRenderedImage(RenderedImage image, AffineTransform imageTransform) {
    BufferedImage bufferedImage;
    if (image instanceof BufferedImage) {
      bufferedImage = (BufferedImage)image;
    } else {
      bufferedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2D = bufferedImage.createGraphics();
      g2D.drawRenderedImage(image, AffineTransform.getTranslateInstance(-image.getMinX(), -image.getMinY()));
      g2D.dispose();
      AffineTransform origin = AffineTransform.getTranslateInstance(image.getMinX(), image.getMinY());
      if (imageTransform != null) {
        origin.preConcatenate(imageTransform);
      }
      imageTransform = origin;
    }
    writeImage(bufferedImage, imageTransform != null ? imageTransform : new AffineTransform());
  }

  @Override
  public void drawRenderableImage(RenderableImage image, AffineTransform imageTransform) {
    drawRenderedImage(image.createDefaultRendering(), imageTransform);
  }

  // ---- Paint, stroke and composite ----

  @Override
  public Color getColor() {
    return this.color;
  }

  @Override
  public void setColor(Color color) {
    if (color != null) {
      this.color = color;
      this.paint = color;
    }
  }

  @Override
  public Paint getPaint() {
    return this.paint;
  }

  @Override
  public void setPaint(Paint paint) {
    if (paint != null) {
      this.paint = paint;
      if (paint instanceof Color) {
        this.color = (Color)paint;
      }
    }
  }

  @Override
  public Color getBackground() {
    return this.background;
  }

  @Override
  public void setBackground(Color color) {
    this.background = color;
  }

  @Override
  public Stroke getStroke() {
    return this.stroke;
  }

  @Override
  public void setStroke(Stroke stroke) {
    if (stroke != null) {
      this.stroke = stroke;
    }
  }

  @Override
  public Composite getComposite() {
    return this.composite;
  }

  @Override
  public void setComposite(Composite composite) {
    if (composite != null) {
      this.composite = composite;
    }
  }

  @Override
  public void setPaintMode() {
    this.composite = AlphaComposite.SrcOver;
  }

  @Override
  public void setXORMode(Color color) {
    // Nothing to mix with in a file
  }

  // ---- Rendering hints ----

  @Override
  public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    if (hintValue != null) {
      this.hints.put(hintKey, hintValue);
    } else {
      this.hints.remove(hintKey);
    }
  }

  @Override
  public Object getRenderingHint(RenderingHints.Key hintKey) {
    return this.hints.get(hintKey);
  }

  @Override
  public void setRenderingHints(Map<?, ?> hints) {
    this.hints = new RenderingHints(null);
    this.hints.putAll(hints);
  }

  @Override
  public void addRenderingHints(Map<?, ?> hints) {
    this.hints.putAll(hints);
  }

  @Override
  public RenderingHints getRenderingHints() {
    return (RenderingHints)this.hints.clone();
  }

  // ---- Transformation ----

  @Override
  public void translate(int x, int y) {
    this.transform.translate(x, y);
  }

  @Override
  public void translate(double tx, double ty) {
    this.transform.translate(tx, ty);
  }

  @Override
  public void rotate(double theta) {
    this.transform.rotate(theta);
  }

  @Override
  public void rotate(double theta, double x, double y) {
    this.transform.rotate(theta, x, y);
  }

  @Override
  public void scale(double sx, double sy) {
    this.transform.scale(sx, sy);
  }

  @Override
  public void shear(double shx, double shy) {
    this.transform.shear(shx, shy);
  }

  @Override
  public void transform(AffineTransform transform) {
    this.transform.concatenate(transform);
  }

  @Override
  public void setTransform(AffineTransform transform) {
    this.transform = transform != null ? new AffineTransform(transform) : new AffineTransform();
  }

  @Override
  public AffineTransform getTransform() {
    return new AffineTransform(this.transform);
  }

  // ---- Clip ----

  @Override
  public Shape getClip() {
    if (this.clip == null) {
      return null;
    }
    try {
      return this.transform.createInverse().createTransformedShape(this.clip);
    } catch (NoninvertibleTransformException ex) {
      return null;
    }
  }

  @Override
  public void setClip(Shape clip) {
    this.clip = clip != null
        ? this.transform.createTransformedShape(clip)
        : null;
  }

  @Override
  public void setClip(int x, int y, int width, int height) {
    setClip(new Rectangle(x, y, width, height));
  }

  @Override
  public Rectangle getClipBounds() {
    Shape clip = getClip();
    return clip != null ? clip.getBounds() : null;
  }

  @Override
  public void clip(Shape clip) {
    if (clip == null) {
      this.clip = null;
    } else if (this.clip == null) {
      setClip(clip);
    } else {
      Area area = new Area(this.clip);
      area.intersect(new Area(this.transform.createTransformedShape(clip)));
      this.clip = area;
    }
  }

  @Override
  public void clipRect(int x, int y, int width, int height) {
    clip(new Rectangle(x, y, width, height));
  }

  @Override
  public boolean hit(Rectangle rectangle, Shape shape, boolean onStroke) {
    if (onStroke) {
      shape = this.stroke.createStrokedShape(shape);
    }
    Shape deviceShape = this.transform.createTransformedShape(shape);
    if (this.clip != null) {
      Area area = new Area(deviceShape);
      area.intersect(new Area(this.clip));
      deviceShape = area;
    }
    return deviceShape.intersects(rectangle);
  }

  // ---- Device ----

  @Override
  public GraphicsConfiguration getDeviceConfiguration() {
    Graphics2D g2D = MEASURE_IMAGE.createGraphics();
    try {
      return g2D.getDeviceConfiguration();
    } finally {
      g2D.dispose();
    }
  }

  @Override
  public Graphics create() {
    return createCopy();
  }

  @Override
  public void dispose() {
  }
}
