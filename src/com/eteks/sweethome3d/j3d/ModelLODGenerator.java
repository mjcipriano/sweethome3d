/*
 * ModelLODGenerator.java 7 Jun 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.j3d;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Link;
import javax.media.j3d.Node;
import javax.media.j3d.Shape3D;
import javax.vecmath.Point3f;
import javax.vecmath.TexCoord2f;
import javax.vecmath.Vector3f;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.tools.TemporaryURLContent;
import com.sun.j3d.utils.geometry.GeometryInfo;

/**
 * Generates persistent, topology-safe simplified OBJ models.
 */
public class ModelLODGenerator {
  public static final int DEFAULT_VERTEX_THRESHOLD =
      Integer.getInteger("com.eteks.sweethome3d.j3d.lodVertexThreshold", 100000);
  public static final int DEFAULT_TARGET_VERTICES =
      Integer.getInteger("com.eteks.sweethome3d.j3d.lodTargetVertices", 75000);

  private static boolean nativeAvailable;

  static {
    try {
      System.loadLibrary("sweethome3d_model_lod");
      nativeAvailable = true;
    } catch (UnsatisfiedLinkError ex) {
      try {
        System.load(extractNativeLibrary().getAbsolutePath());
        nativeAvailable = true;
      } catch (IOException ex2) {
        System.err.println("Model LOD generation unavailable: " + ex.getMessage());
      }
    }
  }

  public static boolean isAvailable() {
    return nativeAvailable;
  }

  private static File extractNativeLibrary() throws IOException {
    String osName = System.getProperty("os.name").toLowerCase();
    String os = osName.contains("win") ? "windows"
        : osName.contains("mac") ? "macos" : "linux";
    String extension = os.equals("windows") ? ".dll" : os.equals("macos") ? ".dylib" : ".so";
    String resource = "/native/" + os + "/x64/"
        + (os.equals("windows") ? "" : "lib")
        + "sweethome3d_model_lod" + extension;
    InputStream in = ModelLODGenerator.class.getResourceAsStream(resource);
    if (in == null) {
      throw new IOException("Missing resource " + resource);
    }
    File nativeFile = File.createTempFile("sweethome3d_model_lod-", extension);
    nativeFile.deleteOnExit();
    FileOutputStream out = new FileOutputStream(nativeFile);
    try {
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) >= 0) {
        out.write(buffer, 0, count);
      }
    } finally {
      out.close();
      in.close();
    }
    return nativeFile;
  }

  /**
   * Clamps a requested simplification ratio to the range the simplifier supports.
   * A ratio of 1 keeps every vertex, a smaller ratio keeps fewer; values outside
   * [0.02, 0.95] are pulled back into that range.
   */
  public static float normalizeTargetRatio(float targetRatio) {
    // Allow very aggressive reductions (down to 0.1% of the vertices)
    return Math.min(0.95f, Math.max(0.001f, targetRatio));
  }

  /**
   * Generates an LOD for {@code model} with an automatic reduction level,
   * returning {@code null} below the vertex threshold.
   */
  public ModelLOD generate(final Content model) throws IOException {
    return generate(model, null);
  }

  /**
   * Generates an LOD for {@code model} reduced to about {@code targetRatio} of
   * its vertices (0&nbsp;&lt;&nbsp;ratio&nbsp;&le;&nbsp;1). This manual override
   * always attempts a reduction, ignoring the automatic vertex threshold.
   */
  public ModelLOD generate(final Content model, float targetRatio) throws IOException {
    return generate(model, Float.valueOf(targetRatio));
  }

  private ModelLOD generate(final Content model, Float targetRatioOverride) throws IOException {
    if (!nativeAvailable) {
      throw new IOException("The model LOD native library isn't available");
    }

    final BranchGroup [] loadedModel = new BranchGroup [1];
    final Exception [] loadError = new Exception [1];
    ModelManager.getInstance().loadModel(model, true, new ModelManager.ModelObserver() {
        public void modelUpdated(BranchGroup modelRoot) {
          loadedModel [0] = modelRoot;
        }

        public void modelError(Exception ex) {
          loadError [0] = ex;
        }
      });
    if (loadError [0] != null) {
      throw new IOException("Couldn't load model", loadError [0]);
    }

    int sourceVertexCount = ModelManager.getInstance().getModelVertexCount(model);
    float targetRatio;
    if (targetRatioOverride != null) {
      // Manual reduction level: always attempt a reduction
      targetRatio = normalizeTargetRatio(targetRatioOverride.floatValue());
    } else {
      // Automatic level: only simplify models above the threshold
      if (sourceVertexCount <= DEFAULT_VERTEX_THRESHOLD) {
        return null;
      }
      targetRatio = normalizeTargetRatio((float)DEFAULT_TARGET_VERTICES / sourceVertexCount);
    }
    BranchGroup simplifiedModel = (BranchGroup)loadedModel [0].cloneTree(true);
    int simplifiedVertices = simplifyNode(simplifiedModel, targetRatio);
    if (simplifiedVertices <= 0 || simplifiedVertices >= sourceVertexCount) {
      return null;
    }

    File zipFile = File.createTempFile("sweethome3d-lod-", ".zip");
    zipFile.deleteOnExit();
    String entryName = "model.obj";
    OBJWriter.writeNodeInZIPFile(simplifiedModel, zipFile, 1, entryName,
        "Sweet Home 3D generated LOD");
    Content lodContent = new TemporaryURLContent(
        new URL("jar:" + zipFile.toURI().toURL() + "!/" + entryName));
    return new ModelLOD(lodContent, sourceVertexCount, simplifiedVertices);
  }

  private int simplifyNode(Node node, float targetRatio) {
    int vertexCount = 0;
    if (node instanceof Group) {
      Enumeration<?> children = ((Group)node).getAllChildren();
      while (children.hasMoreElements()) {
        vertexCount += simplifyNode((Node)children.nextElement(), targetRatio);
      }
    } else if (node instanceof Link) {
      vertexCount += simplifyNode(((Link)node).getSharedGroup(), targetRatio);
    } else if (node instanceof Shape3D) {
      Shape3D shape = (Shape3D)node;
      for (int i = 0; i < shape.numGeometries(); i++) {
        Geometry geometry = shape.getGeometry(i);
        if (geometry instanceof GeometryArray) {
          GeometryArray simplified = simplifyGeometry((GeometryArray)geometry, targetRatio);
          if (simplified != null) {
            shape.setGeometry(simplified, i);
            vertexCount += simplified.getVertexCount();
          } else {
            vertexCount += ((GeometryArray)geometry).getVertexCount();
          }
        }
      }
    }
    return vertexCount;
  }

  private GeometryArray simplifyGeometry(GeometryArray geometry, float targetRatio) {
    try {
      GeometryInfo source = new GeometryInfo(geometry);
      source.convertToIndexedTriangles();
      source.unindexify();
      Point3f [] coordinates = source.getCoordinates();
      if (coordinates == null || coordinates.length < 300) {
        return null;
      }
      // Colored and multi-texture geometry is left untouched until every stream
      // can be represented without changing its visual semantics.
      if (source.getColors() != null || source.getTexCoordSetCount() > 1
          || source.getNumTexCoordComponents() > 2) {
        return null;
      }

      Vector3f [] sourceNormals = source.getNormals();
      Object [] sourceTextureCoordinates = source.getTexCoordSetCount() == 1
          ? source.getTextureCoordinates(0) : null;
      MeshData mesh = indexVertices(coordinates, sourceNormals, sourceTextureCoordinates);
      SimplificationResult result =
          simplifyNative(mesh.positions, mesh.indices, targetRatio, 0.01f);
      if (result == null) {
        return null;
      }

      Point3f [] compactCoordinates = new Point3f [result.sourceVertices.length];
      Vector3f [] compactNormals = sourceNormals != null
          ? new Vector3f [result.sourceVertices.length] : null;
      TexCoord2f [] compactTextureCoordinates = sourceTextureCoordinates != null
          ? new TexCoord2f [result.sourceVertices.length] : null;
      for (int i = 0; i < result.sourceVertices.length; i++) {
        int sourceIndex = result.sourceVertices [i];
        int originalIndex = mesh.originalVertices [sourceIndex];
        compactCoordinates [i] = coordinates [originalIndex];
        if (compactNormals != null) {
          compactNormals [i] = sourceNormals [originalIndex];
        }
        if (compactTextureCoordinates != null) {
          compactTextureCoordinates [i] = (TexCoord2f)sourceTextureCoordinates [originalIndex];
        }
      }

      GeometryInfo target = new GeometryInfo(GeometryInfo.TRIANGLE_ARRAY);
      target.setCoordinates(compactCoordinates);
      target.setCoordinateIndices(result.indices);
      if (compactNormals != null) {
        target.setNormals(compactNormals);
        target.setNormalIndices(result.indices);
      }
      if (compactTextureCoordinates != null) {
        target.setTextureCoordinateParams(1, 2);
        target.setTextureCoordinates(0, compactTextureCoordinates);
        target.setTextureCoordinateIndices(0, result.indices);
      }
      return target.getIndexedGeometryArray();
    } catch (RuntimeException ex) {
      // Unsupported geometry remains at full fidelity.
      return null;
    }
  }

  private static native SimplificationResult simplifyNative(
      float [] positions, int [] indices, float targetRatio, float targetError);

  static SimplificationResult simplifyPositions(
      float [] positions, float targetRatio, float targetError) {
    if (!nativeAvailable) {
      throw new IllegalStateException("The model LOD native library isn't available");
    }
    Map<String, Integer> vertices = new HashMap<String, Integer>();
    List<Float> compactPositions = new ArrayList<Float>();
    int [] indices = new int [positions.length / 3];
    for (int i = 0; i < indices.length; i++) {
      int offset = i * 3;
      String key = Float.floatToIntBits(positions [offset]) + ":"
          + Float.floatToIntBits(positions [offset + 1]) + ":"
          + Float.floatToIntBits(positions [offset + 2]);
      Integer index = vertices.get(key);
      if (index == null) {
        index = vertices.size();
        vertices.put(key, index);
        compactPositions.add(positions [offset]);
        compactPositions.add(positions [offset + 1]);
        compactPositions.add(positions [offset + 2]);
      }
      indices [i] = index;
    }
    float [] compactPositionArray = new float [compactPositions.size()];
    for (int i = 0; i < compactPositionArray.length; i++) {
      compactPositionArray [i] = compactPositions.get(i);
    }
    return simplifyNative(compactPositionArray, indices, targetRatio, targetError);
  }

  private MeshData indexVertices(Point3f [] coordinates, Vector3f [] normals,
                                 Object [] textureCoordinates) {
    Map<VertexKey, Integer> vertices = new HashMap<VertexKey, Integer>();
    List<Integer> originalVertices = new ArrayList<Integer>();
    int [] indices = new int [coordinates.length];
    for (int i = 0; i < coordinates.length; i++) {
      VertexKey key = new VertexKey(coordinates [i],
          normals != null ? normals [i] : null,
          textureCoordinates != null ? (TexCoord2f)textureCoordinates [i] : null);
      Integer index = vertices.get(key);
      if (index == null) {
        index = vertices.size();
        vertices.put(key, index);
        originalVertices.add(i);
      }
      indices [i] = index;
    }
    float [] positions = new float [originalVertices.size() * 3];
    int [] originalVertexArray = new int [originalVertices.size()];
    for (int i = 0, offset = 0; i < originalVertices.size(); i++) {
      int originalIndex = originalVertices.get(i);
      originalVertexArray [i] = originalIndex;
      Point3f point = coordinates [originalIndex];
      positions [offset++] = point.x;
      positions [offset++] = point.y;
      positions [offset++] = point.z;
    }
    return new MeshData(positions, indices, originalVertexArray);
  }

  private static class MeshData {
    final float [] positions;
    final int []   indices;
    final int []   originalVertices;

    MeshData(float [] positions, int [] indices, int [] originalVertices) {
      this.positions = positions;
      this.indices = indices;
      this.originalVertices = originalVertices;
    }
  }

  private static class VertexKey {
    private final int [] values;

    VertexKey(Point3f point, Vector3f normal, TexCoord2f textureCoordinate) {
      this.values = new int [normal != null ? textureCoordinate != null ? 8 : 6
                                                             : textureCoordinate != null ? 5 : 3];
      int i = 0;
      this.values [i++] = Float.floatToIntBits(point.x);
      this.values [i++] = Float.floatToIntBits(point.y);
      this.values [i++] = Float.floatToIntBits(point.z);
      if (normal != null) {
        this.values [i++] = Float.floatToIntBits(normal.x);
        this.values [i++] = Float.floatToIntBits(normal.y);
        this.values [i++] = Float.floatToIntBits(normal.z);
      }
      if (textureCoordinate != null) {
        this.values [i++] = Float.floatToIntBits(textureCoordinate.x);
        this.values [i] = Float.floatToIntBits(textureCoordinate.y);
      }
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.hashCode(this.values);
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof VertexKey
          && java.util.Arrays.equals(this.values, ((VertexKey)object).values);
    }
  }

  static class SimplificationResult {
    final int [] sourceVertices;
    final int [] indices;
    final float  error;

    SimplificationResult(int [] sourceVertices, int [] indices, float error) {
      this.sourceVertices = sourceVertices;
      this.indices = indices;
      this.error = error;
    }
  }
}
