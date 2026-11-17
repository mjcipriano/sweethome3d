package com.eteks.sweethome3d.j3d;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.FloatBuffer;

import javax.media.j3d.GeometryArray;
import javax.media.j3d.IndexedTriangleArray;
import javax.media.j3d.IndexedTriangleStripArray;
import javax.media.j3d.LineStripArray;
import javax.media.j3d.QuadArray;
import javax.media.j3d.TriangleFanArray;

import org.junit.Test;

/**
 * Tests how the OpenGL renderer turns Java 3D geometries into lists of
 * triangles and lines. These tests don't need an OpenGL context.
 */
public class OpenGLMeshTest {
  private static final float [] SQUARE = {0, 0, 0,  1, 0, 0,  1, 1, 0,  0, 1, 0};

  @Test
  public void testQuadsAreSplitInTriangles() {
    QuadArray quads = new QuadArray(4, GeometryArray.COORDINATES | GeometryArray.NORMALS);
    quads.setCoordinates(0, SQUARE);
    quads.setNormals(0, new float [] {0, 0, 1,  0, 0, 1,  0, 0, 1,  0, 0, 1});
    OpenGLSceneRenderer.Mesh mesh = new OpenGLSceneRenderer.Mesh(quads, 0);
    FloatBuffer vertices = mesh.createVertices();
    assertNotNull(vertices);
    assertEquals(6, mesh.getVertexCount());
    // Coordinates and normal of each vertex
    assertEquals(6 * 6, vertices.remaining());
    assertArrayEquals(new float [] {0, 0, 0,  1, 0, 0,  1, 1, 0,  0, 0, 0,  1, 1, 0,  0, 1, 0},
        mesh.getPositions(), 0);
    // Normal of the second vertex
    assertEquals(1f, vertices.get(6 + 5), 0);
  }

  @Test
  public void testIndexedStripKeepsWinding() {
    IndexedTriangleStripArray strip = new IndexedTriangleStripArray(4, GeometryArray.COORDINATES, 4, new int [] {4});
    strip.setCoordinates(0, new float [] {0, 0, 0,  1, 0, 0,  0, 1, 0,  1, 1, 0});
    strip.setCoordinateIndices(0, new int [] {0, 1, 2, 3});
    OpenGLSceneRenderer.Mesh mesh = new OpenGLSceneRenderer.Mesh(strip, 0);
    assertNotNull(mesh.createVertices());
    assertEquals(6, mesh.getVertexCount());
    // The second triangle is turned over to face the same side as the first one
    assertArrayEquals(new float [] {0, 0, 0,  1, 0, 0,  0, 1, 0,  0, 1, 0,  1, 0, 0,  1, 1, 0},
        mesh.getPositions(), 0);
  }

  @Test
  public void testFan() {
    TriangleFanArray fan = new TriangleFanArray(5, GeometryArray.COORDINATES, new int [] {5});
    fan.setCoordinates(0, new float [] {0, 0, 0,  1, 0, 0,  1, 1, 0,  0, 1, 0,  -1, 1, 0});
    OpenGLSceneRenderer.Mesh mesh = new OpenGLSceneRenderer.Mesh(fan, 0);
    assertNotNull(mesh.createVertices());
    assertEquals(9, mesh.getVertexCount());
    float [] positions = mesh.getPositions();
    // Each triangle starts from the first vertex of the fan
    for (int triangle = 0; triangle < 3; triangle++) {
      assertEquals(0f, positions [triangle * 9], 0);
      assertEquals(0f, positions [triangle * 9 + 1], 0);
    }
    assertEquals(-1f, positions [8 * 3], 0);
  }

  @Test
  public void testSeparateNormalIndices() {
    IndexedTriangleArray triangles = new IndexedTriangleArray(3,
        GeometryArray.COORDINATES | GeometryArray.NORMALS, 3);
    triangles.setCoordinates(0, new float [] {0, 0, 0,  1, 0, 0,  0, 1, 0});
    triangles.setNormals(0, new float [] {0, 0, 1,  0, 1, 0,  1, 0, 0});
    triangles.setCoordinateIndices(0, new int [] {2, 1, 0});
    triangles.setNormalIndices(0, new int [] {0, 0, 2});
    OpenGLSceneRenderer.Mesh mesh = new OpenGLSceneRenderer.Mesh(triangles, 0);
    FloatBuffer vertices = mesh.createVertices();
    assertEquals(3, mesh.getVertexCount());
    assertArrayEquals(new float [] {0, 1, 0,  1, 0, 0,  0, 0, 0}, mesh.getPositions(), 0);
    // Normal of the third vertex
    assertEquals(1f, vertices.get(2 * 6 + 3), 0);
    assertEquals(0f, vertices.get(2 * 6 + 5), 0);
  }

  @Test
  public void testLineStrip() {
    LineStripArray lines = new LineStripArray(3, GeometryArray.COORDINATES, new int [] {3});
    lines.setCoordinates(0, new float [] {0, 0, 0,  1, 0, 0,  1, 1, 0});
    OpenGLSceneRenderer.Mesh mesh = new OpenGLSceneRenderer.Mesh(lines, 0);
    assertNotNull(mesh.createVertices());
    // Two segments
    assertEquals(4, mesh.getVertexCount());
  }
}
