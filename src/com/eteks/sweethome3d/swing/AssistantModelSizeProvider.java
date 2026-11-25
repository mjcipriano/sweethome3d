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
public class AssistantModelSizeProvider implements AssistantCommandExecutor.ModelSizeProvider,
                                                   AssistantCommandExecutor.ModelImageFilter {
  /**
   * Returns <code>image</code> with the colors of its pixels multiplied by the given factors:
   * a glTF material may tint its image with a color, which the materials of the 3D view don't do.
   */
  public byte [] tint(byte [] image, float red, float green, float blue) throws IOException {
    java.awt.image.BufferedImage picture = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
    if (picture == null) {
      throw new IOException("the image can't be read");
    }
    boolean alpha = picture.getColorModel().hasAlpha();
    int width = picture.getWidth();
    int height = picture.getHeight();
    java.awt.image.BufferedImage tinted = new java.awt.image.BufferedImage(width, height,
        alpha ? java.awt.image.BufferedImage.TYPE_INT_ARGB : java.awt.image.BufferedImage.TYPE_INT_RGB);
    int [] factors = {Math.round(Math.max(0, Math.min(1, red)) * 256), Math.round(Math.max(0, Math.min(1, green)) * 256),
                      Math.round(Math.max(0, Math.min(1, blue)) * 256)};
    int [] row = new int [width];
    for (int y = 0; y < height; y++) {
      picture.getRGB(0, y, width, 1, row, 0, width);
      for (int x = 0; x < width; x++) {
        int pixel = row [x];
        row [x] = (pixel & 0xFF000000)
            | ((((pixel >> 16) & 0xFF) * factors [0]) >> 8) << 16
            | ((((pixel >> 8) & 0xFF) * factors [1]) >> 8) << 8
            | (((pixel & 0xFF) * factors [2]) >> 8);
      }
      tinted.setRGB(0, y, width, 1, row, 0, width);
    }
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    // Photos stay photos: a PNG image of a large JPEG one would be many times heavier
    boolean jpeg = !alpha && image.length > 2 && (image [0] & 0xFF) == 0xFF && (image [1] & 0xFF) == 0xD8;
    if (!javax.imageio.ImageIO.write(tinted, jpeg ? "jpg" : "png", out)) {
      throw new IOException("the image can't be written");
    }
    return out.toByteArray();
  }

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
