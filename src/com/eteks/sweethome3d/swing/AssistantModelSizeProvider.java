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
                                                   AssistantCommandExecutor.ModelImageFilter,
                                                   AssistantCommandExecutor.ModelShapeProvider {
  private static final int MAX_TRIANGLES = 500000;

  /**
   * Returns the triangles of <code>model</code>, read from what the writer of OBJ files
   * writes of it: every kind of geometry and the transformations of its parts are handled there.
   */
  public float [][] getTriangles(Content model) throws IOException {
    BranchGroup modelRoot = loadModel(model);
    java.io.StringWriter text = new java.io.StringWriter();
    com.eteks.sweethome3d.j3d.OBJWriter writer = new com.eteks.sweethome3d.j3d.OBJWriter(text);
    try {
      writer.writeNode(modelRoot);
    } finally {
      writer.close();
    }
    java.util.List<float []> vertices = new java.util.ArrayList<float []>();
    java.util.List<float []> triangles = new java.util.ArrayList<float []>();
    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(text.toString()));
    for (String line; (line = reader.readLine()) != null; ) {
      if (line.startsWith("v ")) {
        String [] values = line.trim().split("\\s+");
        if (values.length >= 4) {
          try {
            vertices.add(new float [] {Float.parseFloat(values [1]), Float.parseFloat(values [2]), Float.parseFloat(values [3])});
          } catch (NumberFormatException ex) {
            vertices.add(new float [3]);
          }
        }
      } else if (line.startsWith("f ") && triangles.size() < MAX_TRIANGLES) {
        String [] values = line.trim().split("\\s+");
        int [] indices = new int [values.length - 1];
        boolean valid = indices.length >= 3;
        for (int i = 0; i < indices.length && valid; i++) {
          String index = values [i + 1];
          int slash = index.indexOf('/');
          try {
            indices [i] = Integer.parseInt(slash >= 0 ? index.substring(0, slash) : index);
            // An index counted from the end, or from 1
            indices [i] = indices [i] < 0 ? vertices.size() + indices [i] : indices [i] - 1;
            valid = indices [i] >= 0 && indices [i] < vertices.size();
          } catch (NumberFormatException ex) {
            valid = false;
          }
        }
        // A face of more than 3 points as a fan of triangles
        for (int i = 1; valid && i < indices.length - 1; i++) {
          float [] triangle = new float [9];
          System.arraycopy(vertices.get(indices [0]), 0, triangle, 0, 3);
          System.arraycopy(vertices.get(indices [i]), 0, triangle, 3, 3);
          System.arraycopy(vertices.get(indices [i + 1]), 0, triangle, 6, 3);
          triangles.add(triangle);
        }
      }
    }
    return triangles.toArray(new float [triangles.size()][]);
  }

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
    Vector3f size = ModelManager.getInstance().getSize(loadModel(model));
    // Width, depth and height: the vertical axis of a model is y
    return new float [] {size.x, size.z, size.y};
  }

  private static BranchGroup loadModel(Content model) throws IOException {
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
    return modelRoot [0];
  }
}
