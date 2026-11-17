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
  /**
   * Home property set to <code>false</code> when the reduced models should be
   * displayed only for the pieces opted in with {@link #LOW_POLY_PROPERTY}.
   */
  public static final String AUTOMATIC_PROPERTY = "automaticModelLODs";

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
   * Returns <code>true</code> if the given <code>piece</code> is set to be
   * always displayed with its original model in the 3D view, even when the
   * reduced models are chosen automatically.
   */
  public static boolean isFullDetailInView(HomePieceOfFurniture piece) {
    return "false".equalsIgnoreCase(piece.getProperty(LOW_POLY_PROPERTY));
  }

  /**
   * Returns <code>true</code> if the 3D view displays the reduced model of
   * the given <code>piece</code> of <code>home</code>.
   */
  public static boolean isReducedDetailDisplayed(HomePieceOfFurniture piece, Home home) {
    return piece.getModel() != null
        && getDisplayedModel(piece, home, true) != piece.getModel();
  }

  /**
   * Returns <code>true</code> if the pieces of <code>home</code> that have a
   * reduced model display it without being asked to, which is the default.
   * The home property {@link #AUTOMATIC_PROPERTY} or the system property
   * <code>com.eteks.sweethome3d.j3d.automaticModelLODs</code> set to
   * <code>false</code> restrict the reduced models to the pieces opted in.
   */
  public static boolean isAutomatic(Home home) {
    return home != null
        && !"false".equalsIgnoreCase(home.getProperty(AUTOMATIC_PROPERTY))
        && !"false".equalsIgnoreCase(System.getProperty("com.eteks.sweethome3d.j3d.automaticModelLODs"));
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
        && (isReducedDetailInView(piece)
            || isAutomatic(home) && !isFullDetailInView(piece))) {
      ModelLOD modelLOD = home.getModelLOD(piece.getModel());
      if (modelLOD != null) {
        return modelLOD.getContent();
      }
    }
    return piece.getModel();
  }
}
