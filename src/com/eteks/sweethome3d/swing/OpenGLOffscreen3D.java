/*
 * OpenGLOffscreen3D.java
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

import java.awt.image.BufferedImage;

import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Transform3D;

import com.eteks.sweethome3d.j3d.OpenGLSceneRenderer;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLEventListener;

/**
 * Creates with OpenGL images of a scene described by Java 3D nodes, without displaying it.
 * @author Emmanuel Puybaret
 */
class OpenGLOffscreen3D {
  private final OpenGLSceneRenderer     renderer = new OpenGLSceneRenderer();
  private final GLOffscreenAutoDrawable drawable;
  // Drawing done by the next call to display
  private Runnable drawing;
  private GL2      gl;

  /**
   * Creates an OpenGL context able to draw the given scene.
   * @throws RuntimeException if this context can't be created
   */
  public OpenGLOffscreen3D(Node sceneRoot, Group objectsGroup) {
    GLProfile profile = GLProfile.getMaxFixedFunc(true);
    GLCapabilities capabilities = new GLCapabilities(profile);
    capabilities.setDepthBits(24);
    capabilities.setOnscreen(false);
    this.drawable = GLDrawableFactory.getFactory(profile).createOffscreenAutoDrawable(null, capabilities, null, 16, 16);
    this.drawable.addGLEventListener(new GLEventListener() {
        public void init(GLAutoDrawable drawable) {
          renderer.init(drawable.getGL().getGL2());
        }

        public void dispose(GLAutoDrawable drawable) {
          renderer.dispose(drawable.getGL().getGL2());
        }

        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        }

        public void display(GLAutoDrawable drawable) {
          if (drawing != null) {
            gl = drawable.getGL().getGL2();
            drawing.run();
          }
        }
      });
    this.renderer.setScene(sceneRoot, objectsGroup);
    // Sharper shadows than in a view updated at each frame
    this.renderer.setShadowMapMaxSize(8192);
  }

  /**
   * Sets the scene drawn by this object. The geometries of its previous
   * scene that the new one shares remain in the graphics card.
   */
  public void setScene(Node sceneRoot, Group objectsGroup) {
    this.renderer.setScene(sceneRoot, objectsGroup);
  }

  /**
   * Notes that any object of the scene may have changed.
   */
  public void markAllObjectsDirty() {
    this.renderer.markAllObjectsDirty();
  }

  /**
   * Returns an image of the scene at the given size.
   * @throws RuntimeException if the image can't be created
   */
  public BufferedImage createImage(Transform3D cameraTransform, double fieldOfView,
                                   double frontClipDistance, double backClipDistance,
                                   final int width, final int height,
                                   int antialiasingSamples, boolean shadows, boolean ambientOcclusion) {
    this.renderer.setAntialiasingSamples(antialiasingSamples);
    this.renderer.setShadows(shadows);
    this.renderer.setAmbientOcclusion(ambientOcclusion);
    final OpenGLSceneRenderer.Frame frame = this.renderer.prepareFrame(
        cameraTransform, fieldOfView, frontClipDistance, backClipDistance);
    final BufferedImage [] image = {null};
    final RuntimeException [] exception = {null};
    this.drawing = new Runnable() {
        public void run() {
          try {
            image [0] = renderer.renderImage(gl, frame, width, height);
          } catch (RuntimeException ex) {
            exception [0] = ex;
          }
        }
      };
    try {
      this.drawable.display();
    } finally {
      this.drawing = null;
    }
    if (exception [0] != null) {
      throw exception [0];
    } else if (image [0] == null) {
      throw new IllegalStateException("No image");
    }
    return image [0];
  }

  /**
   * Returns the shape drawn at the pixel (x, y) of the last image created, or <code>null</code>.
   */
  public OpenGLSceneRenderer.PickedShape pickShape(int x, int y) {
    return this.renderer.pickShape(x, y);
  }

  /**
   * Frees the resources of the OpenGL context.
   */
  public void dispose() {
    try {
      this.drawable.destroy();
    } catch (RuntimeException ex) {
      // The context is useless from now on anyway
    }
  }
}
