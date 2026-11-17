package com.eteks.sweethome3d.j3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Material;
import javax.media.j3d.QuadArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Color3f;
import javax.vecmath.Vector3f;

import org.junit.Assume;
import org.junit.Test;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;

/**
 * Tests the OpenGL renderer on a small scene drawn in an off-screen buffer.
 * This test needs an OpenGL context: it's skipped where none can be created.
 */
public class OpenGLSceneRendererTest {
  @Test
  public void testLitSquareIsDrawnAndPicked() {
    // A red square lit from the front, two units wide and five units in front of the camera
    QuadArray quad = new QuadArray(4, GeometryArray.COORDINATES | GeometryArray.NORMALS);
    quad.setCoordinates(0, new float [] {-1, -1, 0,  1, -1, 0,  1, 1, 0,  -1, 1, 0});
    quad.setNormals(0, new float [] {0, 0, 1,  0, 0, 1,  0, 0, 1,  0, 0, 1});
    Appearance appearance = new Appearance();
    Material material = new Material();
    material.setDiffuseColor(new Color3f(1, 0, 0));
    material.setAmbientColor(new Color3f(1, 0, 0));
    material.setSpecularColor(new Color3f(0, 0, 0));
    appearance.setMaterial(material);
    Transform3D translation = new Transform3D();
    translation.setTranslation(new Vector3f(0, 0, -5));
    TransformGroup transformGroup = new TransformGroup(translation);
    transformGroup.addChild(new Shape3D(quad, appearance));
    final BranchGroup object = new BranchGroup();
    object.addChild(transformGroup);

    final BranchGroup root = new BranchGroup();
    final Group objects = new Group();
    objects.addChild(object);
    root.addChild(objects);
    root.addChild(new Background(new Color3f(0, 0, 1)));
    root.addChild(new AmbientLight(new Color3f(0.2f, 0.2f, 0.2f)));
    root.addChild(new DirectionalLight(new Color3f(1, 1, 1), new Vector3f(0, 0, -1)));

    final OpenGLSceneRenderer renderer = new OpenGLSceneRenderer();
    renderer.setScene(root, objects);
    final BufferedImage [] image = {null};
    GLOffscreenAutoDrawable drawable = null;
    try {
      GLProfile profile = GLProfile.getMaxFixedFunc(true);
      GLCapabilities capabilities = new GLCapabilities(profile);
      capabilities.setDepthBits(24);
      capabilities.setOnscreen(false);
      drawable = GLDrawableFactory.getFactory(profile).createOffscreenAutoDrawable(null, capabilities, null, 200, 200);
    } catch (Throwable ex) {
      Assume.assumeNoException("No OpenGL context available", ex);
    }
    drawable.addGLEventListener(new GLEventListener() {
        public void init(GLAutoDrawable drawable) {
          renderer.init(drawable.getGL().getGL2());
        }

        public void dispose(GLAutoDrawable drawable) {
          renderer.dispose(drawable.getGL().getGL2());
        }

        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        }

        public void display(GLAutoDrawable drawable) {
          GL2 gl = drawable.getGL().getGL2();
          renderer.render(gl, 200, 200, new Transform3D(), Math.PI / 3, 0.1, 100);
          image [0] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
        }
      });
    try {
      drawable.display();
      // Objects are picked among the ones displayed in the last frame
      assertSame(object, renderer.pickObject(100, 100));
      assertSame(object, renderer.pickObject(120, 80));
      assertNull(renderer.pickObject(5, 5));
    } finally {
      drawable.destroy();
    }

    assertEquals(1, renderer.getDrawnItemCount());
    assertEquals(6, renderer.getDrawnVertexCount());
    int center = image [0].getRGB(100, 100);
    assertTrue("Lit red expected at center but was " + Integer.toHexString(center),
        (center >> 16 & 0xFF) > 200 && (center >> 8 & 0xFF) < 60 && (center & 0xFF) < 60);
    int corner = image [0].getRGB(5, 5);
    assertTrue("Blue background expected in the corner but was " + Integer.toHexString(corner),
        (corner >> 16 & 0xFF) < 30 && (corner >> 8 & 0xFF) < 30 && (corner & 0xFF) > 220);
    // The square covers about 70 pixels around the center
    int edge = image [0].getRGB(100 - 50, 100);
    assertTrue("Background expected beside the square", (edge & 0xFF) > 220);
  }
}
