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

import java.awt.Component;
import java.awt.image.BufferedImage;

import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Transform3D;

import com.eteks.sweethome3d.j3d.OpenGLSceneRenderer;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLRunnable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;

/**
 * A canvas which displays with OpenGL a scene described by Java 3D nodes.
 * This class is the only one of its package which depends on JOGL classes,
 * and doesn't implement any of their interfaces, to be able to fall back on
 * Java 3D when they can't be loaded.
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
     * Called after a frame was drawn.
     */
    public void frameRendered(OpenGLView3D view);
  }

  private final GLCanvas            canvas;
  private final OpenGLSceneRenderer renderer = new OpenGLSceneRenderer();
  private final Client              client;
  private String   vendor;
  private String   rendererName;
  private String   version;
  private boolean  antialiasing;
  private long     framesWindowStartTime;
  private int      framesSinceWindowStart;
  private float    framesPerSecond;
  private long     lastFrameDuration;
  // Image drawn in the top left corner over the scene
  private BufferedImage overlayImage;
  private boolean  overlayDirty;
  private int      overlayTexture;

  public OpenGLView3D(Client client, boolean antialiasing) {
    this.client = client;
    GLProfile profile = GLProfile.getMaxFixedFunc(true);
    GLCapabilities capabilities = new GLCapabilities(profile);
    capabilities.setDepthBits(24);
    capabilities.setDoubleBuffered(true);
    if (antialiasing) {
      capabilities.setSampleBuffers(true);
      capabilities.setNumSamples(4);
    }
    this.antialiasing = antialiasing;
    this.canvas = new GLCanvas(capabilities);
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
    this.renderer.setScene(sceneRoot, objectsGroup);
    repaint();
  }

  public void markObjectDirty(Node object) {
    this.renderer.markObjectDirty(object);
  }

  public void markAllObjectsDirty() {
    this.renderer.markAllObjectsDirty();
  }

  /**
   * Sets the image drawn in the top left corner over the scene, or <code>null</code>
   * if there's none. This method should be called again when the image changes.
   */
  public void setOverlayImage(BufferedImage overlayImage) {
    this.overlayImage = overlayImage;
    this.overlayDirty = true;
    repaint();
  }

  /**
   * Draws a new frame later.
   */
  public void repaint() {
    this.canvas.repaint();
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
    return this.antialiasing;
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

  /**
   * Returns <code>true</code> if the last frame didn't display all the scene yet.
   */
  public boolean isFramePending() {
    return this.renderer.isFramePending();
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
    return this.renderer.pickObject(Math.round(x * scale), Math.round(y * scale));
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
   * Returns the image of a frame.
   */
  public BufferedImage createImage() {
    final BufferedImage [] image = {null};
    this.canvas.invoke(true, new GLRunnable() {
        public boolean run(GLAutoDrawable drawable) {
          display(drawable);
          GL gl = drawable.getGL();
          image [0] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
          return true;
        }
      });
    return image [0];
  }

  /**
   * Frees the resources of this view.
   */
  public void dispose() {
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
    this.antialiasing = drawable.getChosenGLCapabilities().getSampleBuffers();
    if (this.antialiasing) {
      gl.glEnable(GL.GL_MULTISAMPLE);
    }
    // Synchronize frames with the screen, except for benchmarks
    gl.setSwapInterval("false".equalsIgnoreCase(System.getProperty("com.eteks.sweethome3d.opengl.vsync")) ? 0 : 1);
    this.renderer.init(gl);
    // The texture of a previous context is lost
    this.overlayTexture = 0;
    this.overlayDirty = true;
  }

  /**
   * Draws the overlay image in the top left corner of the view.
   */
  private void drawOverlay(GL2 gl, int width, int height) {
    BufferedImage image = this.overlayImage;
    if (this.overlayDirty) {
      this.overlayDirty = false;
      if (this.overlayTexture != 0) {
        gl.glDeleteTextures(1, new int [] {this.overlayTexture}, 0);
        this.overlayTexture = 0;
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
        this.overlayTexture = id [0];
      }
    }
    if (this.overlayTexture != 0 && image != null) {
      float scale = getScale();
      float right = image.getWidth() * scale;
      float bottom = image.getHeight() * scale;
      gl.glMatrixMode(com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION);
      gl.glLoadIdentity();
      gl.glOrtho(0, width, height, 0, -1, 1);
      gl.glMatrixMode(com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW);
      gl.glLoadIdentity();
      gl.glDisable(GL.GL_DEPTH_TEST);
      gl.glDisable(GL.GL_CULL_FACE);
      gl.glDisable(com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHTING);
      gl.glEnable(GL.GL_BLEND);
      gl.glEnable(GL.GL_TEXTURE_2D);
      gl.glBindTexture(GL.GL_TEXTURE_2D, this.overlayTexture);
      gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
      gl.glBegin(GL2.GL_QUADS);
      gl.glTexCoord2f(0, 0);
      gl.glVertex2f(0, 0);
      gl.glTexCoord2f(0, 1);
      gl.glVertex2f(0, bottom);
      gl.glTexCoord2f(1, 1);
      gl.glVertex2f(right, bottom);
      gl.glTexCoord2f(1, 0);
      gl.glVertex2f(right, 0);
      gl.glEnd();
      gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
      gl.glDisable(GL.GL_TEXTURE_2D);
      gl.glDisable(GL.GL_BLEND);
    }
  }

  private void display(GLAutoDrawable drawable) {
    long start = System.nanoTime();
    GL2 gl = drawable.getGL().getGL2();
    int width = drawable.getSurfaceWidth();
    int height = drawable.getSurfaceHeight();
    double [] clipDistances = this.client.getClipDistances(width, height);
    this.renderer.render(gl, width, height, this.client.getCameraTransform(),
        this.client.getFieldOfView(), clipDistances [0], clipDistances [1]);
    drawOverlay(gl, width, height);
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
      repaint();
    }
  }
}
