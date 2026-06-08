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
}
