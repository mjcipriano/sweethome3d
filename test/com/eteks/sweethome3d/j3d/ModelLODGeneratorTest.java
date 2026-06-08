package com.eteks.sweethome3d.j3d;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModelLODGeneratorTest {
  @Test
  public void testNativeSimplificationProducesCompactValidMesh() {
    assertTrue(ModelLODGenerator.isAvailable());

    int gridSize = 20;
    float [] positions = new float [(gridSize - 1) * (gridSize - 1) * 6 * 3];
    int offset = 0;
    for (int y = 0; y < gridSize - 1; y++) {
      for (int x = 0; x < gridSize - 1; x++) {
        offset = addTriangle(positions, offset, x, y, x + 1, y, x + 1, y + 1);
        offset = addTriangle(positions, offset, x, y, x + 1, y + 1, x, y + 1);
      }
    }

    ModelLODGenerator.SimplificationResult result =
        ModelLODGenerator.simplifyPositions(positions, 0.25f, 0.01f);
    assertNotNull(result);
    assertTrue(result.indices.length < positions.length / 3);
    assertTrue(result.indices.length % 3 == 0);
    for (int index : result.indices) {
      assertTrue(index >= 0 && index < result.sourceVertices.length);
    }
  }

  private int addTriangle(float [] positions, int offset,
                          float x1, float y1, float x2, float y2, float x3, float y3) {
    float [] triangle = {x1, 0, y1, x2, 0, y2, x3, 0, y3};
    System.arraycopy(triangle, 0, positions, offset, triangle.length);
    return offset + triangle.length;
  }
}
