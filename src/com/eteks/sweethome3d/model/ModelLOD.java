/*
 * ModelLOD.java 7 Jun 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.model;

import java.io.Serializable;

/**
 * A persistent simplified representation of a model.
 */
public class ModelLOD implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Name of the piece of furniture property set to <code>"true"</code> when the
   * piece should be displayed with its reduced (low poly) model in the 3D view.
   * The original model is always kept and used for rendering and exports.
   */
  public static final String LOW_POLY_PROPERTY = "lowPolyInView";

  private final Content content;
  private final int     sourceVertexCount;
  private final int     vertexCount;

  public ModelLOD(Content content, int sourceVertexCount, int vertexCount) {
    if (content == null) {
      throw new IllegalArgumentException("LOD content is required");
    }
    this.content = content;
    this.sourceVertexCount = sourceVertexCount;
    this.vertexCount = vertexCount;
  }

  public Content getContent() {
    return this.content;
  }

  public int getSourceVertexCount() {
    return this.sourceVertexCount;
  }

  public int getVertexCount() {
    return this.vertexCount;
  }

  /**
   * Returns <code>true</code> if the given <code>piece</code> is set to be
   * displayed with reduced detail in the 3D view.
   */
  public static boolean isReducedDetailInView(HomePieceOfFurniture piece) {
    return "true".equalsIgnoreCase(piece.getProperty(LOW_POLY_PROPERTY));
  }

  /**
   * Returns the model content that should be displayed for the given
   * <code>piece</code>: the reduced LOD content when the piece is set to use
   * reduced detail, a matching LOD exists in <code>home</code> and
   * <code>useModelLODs</code> is <code>true</code>; otherwise the piece's
   * original model. The system property
   * <code>com.eteks.sweethome3d.j3d.useModelLODs=false</code> disables reduced
   * models globally.
   */
  public static Content getDisplayedModel(HomePieceOfFurniture piece, Home home, boolean useModelLODs) {
    if (useModelLODs
        && home != null
        && !"false".equalsIgnoreCase(System.getProperty("com.eteks.sweethome3d.j3d.useModelLODs"))
        && isReducedDetailInView(piece)) {
      ModelLOD modelLOD = home.getModelLOD(piece.getModel());
      if (modelLOD != null) {
        return modelLOD.getContent();
      }
    }
    return piece.getModel();
  }
}
