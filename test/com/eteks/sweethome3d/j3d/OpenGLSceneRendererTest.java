package com.eteks.sweethome3d.j3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Material;
import javax.media.j3d.PointLight;
import javax.media.j3d.PolygonAttributes;
import javax.media.j3d.QuadArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import org.junit.Assume;
import org.junit.Test;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;

/**
 * Tests the OpenGL renderer on small scenes drawn in an off-screen buffer.
 * This test needs an OpenGL context: it's skipped where none can be created.
 */
public class OpenGLSceneRendererTest {
  /**
   * Some drawing done with an OpenGL context.
   */
  private static interface Drawing {
    public void draw(GL2 gl, GLAutoDrawable drawable) throws Exception;
  }

  /**
   * Runs the given drawing in a new off-screen OpenGL context of 200 x 200 pixels
   * where <code>renderer</code> is initialized.
   */
  private static void draw(final OpenGLSceneRenderer renderer, final Drawing drawing) throws Exception {
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
    final Exception [] exception = {null};
    final Error [] error = {null};
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
          try {
            drawing.draw(drawable.getGL().getGL2(), drawable);
          } catch (Exception ex) {
            exception [0] = ex;
          } catch (Error ex) {
            error [0] = ex;
          }
        }
      });
    try {
      drawable.display();
    } finally {
      drawable.destroy();
    }
    if (error [0] != null) {
      throw error [0];
    } else if (exception [0] != null) {
      throw exception [0];
    }
  }

  private static Shape3D createSquare(float halfSize, Color3f color, boolean horizontal) {
    QuadArray quad = new QuadArray(4, GeometryArray.COORDINATES | GeometryArray.NORMALS);
    if (horizontal) {
      // In the plane y = 0, its normal going up
      quad.setCoordinates(0, new float [] {-halfSize, 0, halfSize,  halfSize, 0, halfSize,
                                           halfSize, 0, -halfSize,  -halfSize, 0, -halfSize});
      quad.setNormals(0, new float [] {0, 1, 0,  0, 1, 0,  0, 1, 0,  0, 1, 0});
    } else {
      quad.setCoordinates(0, new float [] {-halfSize, -halfSize, 0,  halfSize, -halfSize, 0,
                                           halfSize, halfSize, 0,  -halfSize, halfSize, 0});
      quad.setNormals(0, new float [] {0, 0, 1,  0, 0, 1,  0, 0, 1,  0, 0, 1});
    }
    Appearance appearance = new Appearance();
    Material material = new Material();
    material.setDiffuseColor(color);
    material.setAmbientColor(color);
    material.setSpecularColor(new Color3f(0, 0, 0));
    appearance.setMaterial(material);
    appearance.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_FILL, PolygonAttributes.CULL_NONE, 0));
    return new Shape3D(quad, appearance);
  }

  private static BranchGroup createObject(Shape3D shape, Vector3f translation) {
    Transform3D transform = new Transform3D();
    transform.setTranslation(translation);
    TransformGroup transformGroup = new TransformGroup(transform);
    transformGroup.addChild(shape);
    BranchGroup object = new BranchGroup();
    object.addChild(transformGroup);
    return object;
  }

  private static int getBrightness(int rgb) {
    return ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3;
  }

  @Test
  public void testLitSquareIsDrawnAndPicked() throws Exception {
    // A red square lit from the front, two units wide and five units in front of the camera
    final BranchGroup object = createObject(createSquare(1, new Color3f(1, 0, 0), false), new Vector3f(0, 0, -5));
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
    draw(renderer, new Drawing() {
        public void draw(GL2 gl, GLAutoDrawable drawable) {
          renderer.render(gl, 200, 200, new Transform3D(), Math.PI / 3, 0.1, 100);
          image [0] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
          // Objects are picked among the ones displayed in the last frame
          assertSame(object, renderer.pickObject(100, 100));
          assertSame(object, renderer.pickObject(120, 80));
          assertNull(renderer.pickObject(5, 5));
        }
      });

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

  /**
   * Geometries prepared by worker threads are drawn in a following frame,
   * and images are created at any size with all the geometries.
   */
  @Test
  public void testFramesPreparedInBackgroundAndImage() throws Exception {
    final BranchGroup root = new BranchGroup();
    final Group objects = new Group();
    objects.addChild(createObject(createSquare(1, new Color3f(1, 0, 0), false), new Vector3f(0, 0, -5)));
    root.addChild(objects);
    root.addChild(new Background(new Color3f(0, 0, 1)));
    root.addChild(new DirectionalLight(new Color3f(1, 1, 1), new Vector3f(0, 0, -1)));

    final OpenGLSceneRenderer renderer = new OpenGLSceneRenderer();
    renderer.setScene(root, objects);
    final BufferedImage [] image = {null};
    final int [] frameCount = {0};
    draw(renderer, new Drawing() {
        public void draw(GL2 gl, GLAutoDrawable drawable) throws InterruptedException {
          OpenGLSceneRenderer.Frame frame = renderer.prepareFrame(new Transform3D(), Math.PI / 3, 0.1, 100);
          do {
            renderer.renderFrame(gl, frame, 200, 200);
            frameCount [0]++;
            Thread.sleep(5);
          } while (renderer.isLoading() && frameCount [0] < 1000);
          renderer.renderFrame(gl, frame, 200, 200);
          assertEquals(1, renderer.getDrawnItemCount());

          Assume.assumeTrue("No frame buffer object", renderer.isAdvancedRenderingAvailable());
          renderer.setAntialiasingSamples(4);
          renderer.setAmbientOcclusion(true);
          image [0] = renderer.renderImage(gl, frame, 300, 150);
        }
      });
    assertEquals(300, image [0].getWidth());
    assertEquals(150, image [0].getHeight());
    int center = image [0].getRGB(150, 75);
    assertTrue("Lit red expected at center but was " + Integer.toHexString(center),
        (center >> 16 & 0xFF) > 200 && (center >> 8 & 0xFF) < 60 && (center & 0xFF) < 60);
    int corner = image [0].getRGB(5, 5);
    assertTrue("Blue background expected in the corner but was " + Integer.toHexString(corner),
        (corner >> 16 & 0xFF) < 30 && (corner & 0xFF) > 220);
  }

  /**
   * A light source lights the pixels of a surface according to their distance,
   * whatever the count of its vertices.
   */
  @Test
  public void testPointLightIsComputedAtEachPixel() throws Exception {
    final BranchGroup object = createObject(createSquare(2, new Color3f(1, 1, 1), false), new Vector3f(0, 0, -5));
    PointLight light = new PointLight(new Color3f(1, 1, 1), new Point3f(0, 0, -4.5f), new Point3f(1, 0, 4));
    light.setInfluencingBounds(new BoundingSphere(new Point3d(), 1E7));
    object.addChild(light);
    final BranchGroup root = new BranchGroup();
    final Group objects = new Group();
    objects.addChild(object);
    root.addChild(objects);
    root.addChild(new Background(new Color3f(0, 0, 1)));

    final OpenGLSceneRenderer renderer = new OpenGLSceneRenderer();
    renderer.setScene(root, objects);
    final BufferedImage [] image = {null};
    draw(renderer, new Drawing() {
        public void draw(GL2 gl, GLAutoDrawable drawable) {
          Assume.assumeTrue("No shader", renderer.isAdvancedRenderingAvailable());
          renderer.render(gl, 200, 200, new Transform3D(), Math.PI / 3, 0.1, 100);
          image [0] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
        }
      });
    int center = getBrightness(image [0].getRGB(100, 100));
    int side = getBrightness(image [0].getRGB(100 + 55, 100));
    assertTrue("Center lit by the light source expected but was " + center, center > 120);
    assertTrue("Side " + side + " expected darker than center " + center, side < center - 40);
  }

  /**
   * An object casts a shadow on the surface under it.
   */
  @Test
  public void testShadow() throws Exception {
    final BranchGroup root = new BranchGroup();
    final Group objects = new Group();
    // A ground and a square one unit over it, viewed from above
    objects.addChild(createObject(createSquare(5, new Color3f(0.8f, 0.8f, 0.8f), true), new Vector3f()));
    objects.addChild(createObject(createSquare(1, new Color3f(1, 0, 0), true), new Vector3f(0, 1, 0)));
    root.addChild(objects);
    root.addChild(new Background(new Color3f(0, 0, 1)));
    root.addChild(new AmbientLight(new Color3f(0.2f, 0.2f, 0.2f)));
    // Light coming from the left: the shadow is at the right of the square
    root.addChild(new DirectionalLight(new Color3f(1, 1, 1), new Vector3f(1, -1, 0)));

    final Transform3D camera = new Transform3D();
    camera.rotX(-Math.PI / 2);
    camera.setTranslation(new Vector3d(0, 10, 0));
    final OpenGLSceneRenderer renderer = new OpenGLSceneRenderer();
    renderer.setScene(root, objects);
    final BufferedImage [] images = {null, null};
    draw(renderer, new Drawing() {
        public void draw(GL2 gl, GLAutoDrawable drawable) {
          Assume.assumeTrue("No shader", renderer.isAdvancedRenderingAvailable());
          renderer.render(gl, 200, 200, camera, Math.PI / 3, 0.1, 100);
          images [0] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
          renderer.setShadows(true);
          // The shadows are complete in the frame which follows the one where geometries are copied
          renderer.render(gl, 200, 200, camera, Math.PI / 3, 0.1, 100);
          renderer.render(gl, 200, 200, camera, Math.PI / 3, 0.1, 100);
          images [1] = new AWTGLReadBufferUtil(drawable.getGLProfile(), false).readPixelsToBufferedImage(gl, true);
        }
      });
    // The ground at the right of the square, and far at its left
    int rightWithoutShadows = getBrightness(images [0].getRGB(123, 100));
    int leftWithoutShadows = getBrightness(images [0].getRGB(40, 100));
    assertTrue("Same light expected without shadows but was " + rightWithoutShadows + " and " + leftWithoutShadows,
        Math.abs(rightWithoutShadows - leftWithoutShadows) < 10);
    int shadow = getBrightness(images [1].getRGB(123, 100));
    int lit = getBrightness(images [1].getRGB(40, 100));
    assertTrue("Shadow " + shadow + " expected darker than lit ground " + lit, shadow < lit - 30);
  }
}
