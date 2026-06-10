package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.eteks.sweethome3d.j3d.ModelLODGenerator;
import com.eteks.sweethome3d.j3d.Object3DBranchFactory;

/**
 * Headless checks of the model LOD configuration: the manual reduction ratio is
 * clamped to the supported range, and the viewport factory allows reduced models
 * by default while it can be switched off (as the render/export factories do).
 */
public class ModelLODGeneratorTest {
  @Test
  public void testTargetRatioIsClamped() {
    assertEquals(0.5f, ModelLODGenerator.normalizeTargetRatio(0.5f), 1e-6);
    // Below the minimum and above the maximum are pulled into [0.001, 0.95].
    assertEquals(0.001f, ModelLODGenerator.normalizeTargetRatio(0f), 1e-6);
    assertEquals(0.001f, ModelLODGenerator.normalizeTargetRatio(-1f), 1e-6);
    assertEquals(0.001f, ModelLODGenerator.normalizeTargetRatio(0.0005f), 1e-6);
    assertEquals(0.95f, ModelLODGenerator.normalizeTargetRatio(1f), 1e-6);
    assertEquals(0.95f, ModelLODGenerator.normalizeTargetRatio(10f), 1e-6);
  }

  @Test
  public void testViewportFactoryAllowsReducedModelsByDefault() {
    Object3DBranchFactory factory = new Object3DBranchFactory();
    assertTrue("The viewport factory should allow reduced models by default",
        factory.isUseModelLODs());
    factory.setUseModelLODs(false);
    assertFalse("Render/export factories disable reduced models",
        factory.isUseModelLODs());
  }
}
