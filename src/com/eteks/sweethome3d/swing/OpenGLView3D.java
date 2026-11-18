/*
 * OpenGLView3D.java
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
package com.eteks.sweethome3d.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Transform3D;

import com.eteks.sweethome3d.j3d.OpenGLSceneRenderer;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLRunnable;
import com.jogamp.opengl.Threading;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;

/**
 * A canvas which displays with OpenGL a scene described by Java 3D nodes.
 * The scene is described in the thread which modifies it (the event dispatch
 * thread of the application) and the frames are drawn by a thread of this
 * view, to keep the user interface reactive when a frame is long to draw.
 * This class is one of the two classes of its package which depend on JOGL classes,
 * to be able to fall back on Java 3D when they can't be loaded.
 * @author Emmanuel Puybaret
 */
class OpenGLView3D {
  /**
   * The provider of the point of view of each frame.
   */
  public static interface Client {
    /**
     * Returns the transformation of the camera in the scene.
     */
    public Transform3D getCameraTransform();

    /**
     * Returns the horizontal field of view in radians.
     */
    public double getFieldOfView();

    /**
     * Returns the front and back clip distances for a view of the given size.
     */
    public double [] getClipDistances(int width, int height);

    /**
     * Returns the lines of text displayed in the top left corner of the view
     * or <code>null</code>. This method is called in the rendering thread.
     */
    public String [] getStatistics();

    /**
     * Called in the rendering thread after a frame was drawn.
     */
    public void frameRendered(OpenGLView3D view);
  }

  private final GLCanvas            canvas;
  private final OpenGLSceneRenderer renderer = new OpenGLSceneRenderer();
  private final Client              client;
  private final Object              lock = new Object();
  private final Thread              renderingThread;
  // Fields protected by lock
  private OpenGLSceneRenderer.Frame pendingFrame;
  private boolean  frameRequested;
  private boolean  framePrepared;
  private boolean  disposed;
  private boolean  preparationScheduled;

  private volatile String  vendor;
  private volatile String  rendererName;
  private volatile String  version;
  private long     framesWindowStartTime;
  private int      framesSinceWindowStart;
  private volatile float framesPerSecond;
  private volatile long  lastFrameDuration;
  private boolean  renderingFailureLogged;
  // Image drawn in the top left corner over the scene
  private final Overlay imageOverlay = new Overlay();
  private final Overlay statisticsOverlay = new Overlay();
  private String [] displayedStatistics;

  public OpenGLView3D(Client client) {
    this.client = client;
    // Let any thread use OpenGL, as Java 3D does
    Threading.disableSingleThreading();
    GLProfile profile = GLProfile.getMaxFixedFunc(true);
    GLCapabilities capabilities = new GLCapabilities(profile);
    capabilities.setDepthBits(24);
    capabilities.setDoubleBuffered(true);
    // Edges are smoothed in the frame buffers of the renderer
    this.canvas = new GLCanvas(capabilities) {
        @Override
        public void paint(Graphics g) {
          // Frames are drawn only in the rendering thread
          requestFrame();
        }

        @Override
        public void update(Graphics g) {
          requestFrame();
        }
      };
    this.canvas.addGLEventListener(new GLEventListener() {
        public void init(GLAutoDrawable drawable) {
          OpenGLView3D.this.init(drawable);
        }

        public void dispose(GLAutoDrawable drawable) {
          OpenGLView3D.this.renderer.dispose(drawable.getGL().getGL2());
        }

        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        }

        public void display(GLAutoDrawable drawable) {
          OpenGLView3D.this.display(drawable);
        }
      });
    this.canvas.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent ev) {
          repaint();
        }

        @Override
        public void componentShown(ComponentEvent ev) {
          repaint();
        }
      });
    this.renderer.setFrameRequester(new Runnable() {
        public void run() {
          requestFrame();
        }
      });
    this.renderingThread = new Thread("OpenGL 3D view rendering") {
        @Override
        public void run() {
          renderFrames();
        }
      };
    this.renderingThread.setDaemon(true);
    this.renderingThread.start();
  }

  /**
   * Returns the component which displays the scene.
   */
  public Component getComponent() {
    return this.canvas;
  }

  /**
   * Sets the displayed scene and the group whose children are the objects of the home.
   */
  public void setScene(Node sceneRoot, Group objectsGroup) {
    synchronized (this.renderer) {
      this.renderer.setScene(sceneRoot, objectsGroup);
    }
    repaint();
  }

  public void markObjectDirty(Node object) {
    synchronized (this.renderer) {
      this.renderer.markObjectDirty(object);
    }
  }

  public void markAllObjectsDirty() {
    synchronized (this.renderer) {
      this.renderer.markAllObjectsDirty();
    }
  }

  /**
   * Sets whether edges are smoothed, whether objects cast shadows and
   * whether corners are darkened.
   */
  public void setQuality(boolean antialiasing, boolean shadows, boolean ambientOcclusion) {
    this.renderer.setAntialiasingSamples(antialiasing ? 4 : 0);
    this.renderer.setShadows(shadows);
    this.renderer.setAmbientOcclusion(ambientOcclusion);
    requestFrame();
  }

  /**
   * Sets the image drawn in the top left corner over the scene, or <code>null</code>
   * if there's none. This method should be called again when the image changes.
   */
  public void setOverlayImage(BufferedImage overlayImage) {
    this.imageOverlay.setImage(overlayImage);
    requestFrame();
  }

  /**
   * Describes the scene as it is now and draws it later. This method may be
   * called from any thread, and the scene is described in the event dispatch thread.
   */
  public void repaint() {
    synchronized (this.lock) {
      if (this.preparationScheduled || this.disposed) {
        return;
      }
      this.preparationScheduled = true;
    }
    java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
          prepareFrame();
        }
      });
  }

  /**
   * Describes the scene as it is now and asks the rendering thread to draw it.
   */
  private void prepareFrame() {
    synchronized (this.lock) {
      this.preparationScheduled = false;
      if (this.disposed) {
        return;
      }
    }
    OpenGLSceneRenderer.Frame frame = createFrame(this.canvas.getSurfaceWidth(), this.canvas.getSurfaceHeight());
    synchronized (this.lock) {
      this.pendingFrame = frame;
      this.frameRequested = true;
      this.framePrepared = true;
      this.lock.notifyAll();
    }
  }

  private OpenGLSceneRenderer.Frame createFrame(int width, int height) {
    double [] clipDistances = this.client.getClipDistances(width, height);
    synchronized (this.renderer) {
      return this.renderer.prepareFrame(this.client.getCameraTransform(),
          this.client.getFieldOfView(), clipDistances [0], clipDistances [1]);
    }
  }

  /**
   * Asks the rendering thread to draw again the scene as it was last described.
   */
  private void requestFrame() {
    synchronized (this.lock) {
      this.frameRequested = true;
      this.lock.notifyAll();
    }
  }

  /**
   * Draws the frames which are requested until this view is disposed.
   */
  private void renderFrames() {
    while (true) {
      boolean prepared;
      synchronized (this.lock) {
        while (!this.frameRequested && !this.disposed) {
          try {
            this.lock.wait();
          } catch (InterruptedException ex) {
            return;
          }
        }
        if (this.disposed) {
          return;
        }
        this.frameRequested = false;
        prepared = this.framePrepared;
        this.framePrepared = false;
      }
      try {
        if (!prepared && OperatingSystem.isLinux()) {
          // A frame drawn again while the scene is loading: leave some time to the
          // event dispatch thread, which may wait for the lock taken on the window
          // system while a frame is drawn (the lock of AWT under X11)
          Thread.sleep(Math.max(8, Math.min(40, this.lastFrameDuration / 2000000)));
        }
        if (this.canvas.isDisplayable() && this.canvas.isShowing()) {
          this.canvas.display();
        }
        if (OperatingSystem.isLinux()) {
          Thread.sleep(1);
        }
      } catch (InterruptedException ex) {
        return;
      } catch (RuntimeException ex) {
        if (!this.renderingFailureLogged) {
          this.renderingFailureLogged = true;
          ex.printStackTrace();
        }
      }
    }
  }

  public String getVendor() {
    return this.vendor;
  }

  public String getRendererName() {
    return this.rendererName;
  }

  public String getVersion() {
    return this.version;
  }

  public boolean isAntialiasing() {
    return this.renderer.getAntialiasingSamples() > 1;
  }

  public float getFramesPerSecond() {
    return this.framesPerSecond;
  }

  /**
   * Returns the time spent to draw the last frame in milliseconds.
   */
  public float getLastFrameDuration() {
    return this.lastFrameDuration / 1000000f;
  }

  public int getDrawnItemCount() {
    return this.renderer.getDrawnItemCount();
  }

  public long getDrawnVertexCount() {
    return this.renderer.getDrawnVertexCount();
  }

  public int getCulledItemCount() {
    return this.renderer.getCulledItemCount();
  }

  public int getMissingItemCount() {
    return this.renderer.getMissingItemCount();
  }

  /**
   * Returns <code>true</code> if the last frame didn't display all the scene yet.
   */
  public boolean isFramePending() {
    return this.renderer.isLoading();
  }

  /**
   * Returns <code>true</code> if lights are computed for each pixel, and if
   * shadows and ambient occlusion may be displayed.
   */
  public boolean isAdvancedRenderingAvailable() {
    return this.renderer.isAdvancedRenderingAvailable();
  }

  private float getScale() {
    return this.canvas.getWidth() > 0
        ? (float)this.canvas.getSurfaceWidth() / this.canvas.getWidth()
        : 1;
  }

  /**
   * Returns the object of the home displayed at the given location of the component.
   */
  public Node pickObject(int x, int y) {
    float scale = getScale();
    synchronized (this.renderer) {
      return this.renderer.pickObject(Math.round(x * scale), Math.round(y * scale));
    }
  }

  /**
   * Returns the origin and the direction of the ray going through
   * the given location of the component.
   */
  public double [] getPixelRay(int x, int y) {
    float scale = getScale();
    return this.renderer.getPixelRay(Math.round(x * scale), Math.round(y * scale));
  }

  /**
   * Returns the image of the scene as it is now, at the size of this view.
   */
  public BufferedImage createImage() {
    final OpenGLSceneRenderer.Frame frame = createFrame(this.canvas.getSurfaceWidth(), this.canvas.getSurfaceHeight());
    synchronized (this.lock) {
      this.pendingFrame = frame;
    }
    final BufferedImage [] image = {null};
    this.canvas.invoke(true, new GLRunnable() {
        public boolean run(GLAutoDrawable drawable) {
          GL gl = drawable.getGL();
          image [0] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
          return true;
        }
      });
    return image [0];
  }

  /**
   * Returns the image of the scene as it is now at the given size, with all its
   * geometries and textures, or <code>null</code> if it can't be created.
   */
  public BufferedImage createImage(final int width, final int height,
                                   final int antialiasingSamples,
                                   final boolean shadows, final boolean ambientOcclusion) {
    final OpenGLSceneRenderer.Frame frame = createFrame(width, height);
    final BufferedImage [] image = {null};
    this.canvas.invoke(true, new GLRunnable() {
        public boolean run(GLAutoDrawable drawable) {
          int previousSamples = renderer.getAntialiasingSamples();
          boolean previousShadows = renderer.isShadows();
          boolean previousAmbientOcclusion = renderer.isAmbientOcclusion();
          renderer.setAntialiasingSamples(antialiasingSamples);
          renderer.setShadows(shadows);
          renderer.setAmbientOcclusion(ambientOcclusion);
          try {
            image [0] = renderer.renderImage(drawable.getGL().getGL2(), frame, width, height);
          } catch (RuntimeException ex) {
            image [0] = null;
          } finally {
            renderer.setAntialiasingSamples(previousSamples);
            renderer.setShadows(previousShadows);
            renderer.setAmbientOcclusion(previousAmbientOcclusion);
          }
          // Don't swap buffers
          return false;
        }
      });
    requestFrame();
    return image [0];
  }

  /**
   * Frees the resources of this view.
   */
  public void dispose() {
    synchronized (this.lock) {
      this.disposed = true;
      this.lock.notifyAll();
    }
    this.renderer.setFrameRequester(null);
    if (Thread.currentThread() != this.renderingThread) {
      try {
        this.renderingThread.join(2000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    try {
      this.canvas.destroy();
    } catch (RuntimeException ex) {
      // The canvas is useless from now on anyway
    }
  }

  private void init(GLAutoDrawable drawable) {
    GL2 gl = drawable.getGL().getGL2();
    this.vendor = gl.glGetString(GL.GL_VENDOR);
    this.rendererName = gl.glGetString(GL.GL_RENDERER);
    this.version = gl.glGetString(GL.GL_VERSION);
    // Synchronize frames with the screen, except for benchmarks
    gl.setSwapInterval("false".equalsIgnoreCase(System.getProperty("com.eteks.sweethome3d.opengl.vsync")) ? 0 : 1);
    this.renderer.init(gl);
    // The textures of a previous context are lost
    this.imageOverlay.texture = 0;
    this.imageOverlay.dirty = true;
    this.statisticsOverlay.texture = 0;
    this.statisticsOverlay.dirty = true;
  }

  private void display(GLAutoDrawable drawable) {
    long start = System.nanoTime();
    GL2 gl = drawable.getGL().getGL2();
    int width = drawable.getSurfaceWidth();
    int height = drawable.getSurfaceHeight();
    OpenGLSceneRenderer.Frame frame;
    synchronized (this.lock) {
      frame = this.pendingFrame;
    }
    if (frame == null) {
      gl.glClearColor(0.9f, 0.9f, 0.9f, 1);
      gl.glClear(GL.GL_COLOR_BUFFER_BIT);
      return;
    }
    this.renderer.renderFrame(gl, frame, width, height);

    String [] statistics = this.client.getStatistics();
    if (!Arrays.equals(statistics, this.displayedStatistics)) {
      this.displayedStatistics = statistics;
      this.statisticsOverlay.setImage(statistics != null
          ? createStatisticsImage(statistics, getScale())
          : null);
    }
    float scale = getScale();
    // In the top right corner, to leave the top left one to the navigation arrows
    BufferedImage statisticsImage = this.statisticsOverlay.image;
    if (statisticsImage != null) {
      this.statisticsOverlay.draw(gl, width, height, width - statisticsImage.getWidth() - 8 * scale, 8 * scale, 1);
    }
    this.imageOverlay.draw(gl, width, height, 0, 0, scale);

    long now = System.nanoTime();
    this.lastFrameDuration = now - start;
    if (this.framesWindowStartTime == 0) {
      this.framesWindowStartTime = now;
    }
    this.framesSinceWindowStart++;
    long elapsed = now - this.framesWindowStartTime;
    if (elapsed >= 500000000L) {
      this.framesPerSecond = (float)(this.framesSinceWindowStart * 1E9 / elapsed);
      this.framesSinceWindowStart = 0;
      this.framesWindowStartTime = now;
    }
    this.client.frameRendered(this);
    if (this.renderer.isFramePending()) {
      // Some geometries are still to be copied to the graphics card
      requestFrame();
    }
  }

  /**
   * Returns an image of the given lines of text on a dark box.
   */
  private static BufferedImage createStatisticsImage(String [] lines, float scale) {
    Font font = new Font("Dialog", Font.BOLD, Math.round(12 * scale));
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2D = image.createGraphics();
    FontMetrics metrics = g2D.getFontMetrics(font);
    int textWidth = 0;
    for (String line : lines) {
      textWidth = Math.max(textWidth, metrics.stringWidth(line));
    }
    g2D.dispose();
    int margin = Math.round(8 * scale);
    image = new BufferedImage(textWidth + 2 * margin,
        metrics.getHeight() * lines.length + Math.round(12 * scale), BufferedImage.TYPE_INT_ARGB);
    g2D = image.createGraphics();
    g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2D.setColor(new Color(0, 0, 0, 170));
    g2D.fillRect(0, 0, image.getWidth(), image.getHeight());
    g2D.setColor(Color.WHITE);
    g2D.setFont(font);
    int baseline = Math.round(6 * scale) + metrics.getAscent();
    for (String line : lines) {
      g2D.drawString(line, margin, baseline);
      baseline += metrics.getHeight();
    }
    g2D.dispose();
    return image;
  }

  /**
   * An image drawn over the scene.
   */
  private static class Overlay {
    private volatile BufferedImage image;
    private volatile boolean       dirty;
    private int                    texture;

    public void setImage(BufferedImage image) {
      this.image = image;
      this.dirty = true;
    }

    /**
     * Draws the image at the given location from the top left corner of the view.
     */
    public void draw(GL2 gl, int width, int height, float x, float y, float scale) {
      BufferedImage image = this.image;
      if (this.dirty) {
        this.dirty = false;
        if (this.texture != 0) {
          gl.glDeleteTextures(1, new int [] {this.texture}, 0);
          this.texture = 0;
        }
        if (image != null) {
          int imageWidth = image.getWidth();
          int imageHeight = image.getHeight();
          int [] argb = image.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
          java.nio.ByteBuffer pixels = java.nio.ByteBuffer.allocateDirect(argb.length * 4);
          for (int pixel : argb) {
            pixels.put((byte)(pixel >> 16));
            pixels.put((byte)(pixel >> 8));
            pixels.put((byte)pixel);
            pixels.put((byte)(pixel >>> 24));
          }
          // Cast required to run under Java 8 once compiled with a more recent JDK
          ((java.nio.Buffer)pixels).flip();
          int [] id = new int [1];
          gl.glGenTextures(1, id, 0);
          gl.glBindTexture(GL.GL_TEXTURE_2D, id [0]);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
          gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
          gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, imageWidth, imageHeight, 0,
              GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
          this.texture = id [0];
        }
      }
      if (this.texture != 0 && image != null) {
        float right = x + image.getWidth() * scale;
        float bottom = y + image.getHeight() * scale;
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrtho(0, width, height, 0, -1, 1);
        gl.glMatrixMode(GL.GL_TEXTURE);
        gl.glLoadIdentity();
        gl.glMatrixMode(com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, this.texture);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
        gl.glColor4f(1, 1, 1, 1);
        gl.glBegin(GL2.GL_QUADS);
        gl.glTexCoord2f(0, 0);
        gl.glVertex2f(x, y);
        gl.glTexCoord2f(0, 1);
        gl.glVertex2f(x, bottom);
        gl.glTexCoord2f(1, 1);
        gl.glVertex2f(right, bottom);
        gl.glTexCoord2f(1, 0);
        gl.glVertex2f(right, y);
        gl.glEnd();
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glDisable(GL.GL_BLEND);
      }
    }
  }
}
