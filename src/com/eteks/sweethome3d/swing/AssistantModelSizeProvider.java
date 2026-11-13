/*
 * AssistantModelSizeProvider.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.swing;

import java.io.IOException;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Vector3f;

import com.eteks.sweethome3d.j3d.ModelManager;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;

/**
 * Measures the 3D models the design assistant imports, with the model manager
 * of the 3D view.
 * @author Sweet Home 3D
 */
public class AssistantModelSizeProvider implements AssistantCommandExecutor.ModelSizeProvider {
  public float [] getModelSize(Content model) throws IOException {
    final BranchGroup [] modelRoot = new BranchGroup [1];
    final Exception [] error = new Exception [1];
    try {
      // Loaded at once, to report a file that can't be read to the assistant
      ModelManager.getInstance().loadModel(model, true, new ModelManager.ModelObserver() {
          public void modelUpdated(BranchGroup root) {
            modelRoot [0] = root;
          }

          public void modelError(Exception ex) {
            error [0] = ex;
          }
        });
    } catch (LinkageError ex) {
      throw new IOException("the 3D libraries aren't available");
    }
    if (error [0] != null || modelRoot [0] == null) {
      throw new IOException("the model can't be read"
          + (error [0] != null && error [0].getMessage() != null ? ": " + error [0].getMessage() : ""));
    }
    Vector3f size = ModelManager.getInstance().getSize(modelRoot [0]);
    // Width, depth and height: the vertical axis of a model is y
    return new float [] {size.x, size.z, size.y};
  }
}
