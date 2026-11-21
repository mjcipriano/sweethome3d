/*
 * AssistantModelInspector.java 13 Nov 2026
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Matrix3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import com.eteks.sweethome3d.j3d.ModelManager;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.HomeMaterial;
import com.eteks.sweethome3d.model.Transformation;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;

/**
 * Reads in 3D models what the commands of the assistant need: the names of
 * their materials, and the leaves of doors and windows that turn on hinges.
 * @author Sweet Home 3D
 */
public class AssistantModelInspector implements AssistantCommandExecutor.ModelInspector {
  private static BranchGroup load(Content model) throws IOException {
    final BranchGroup [] modelRoot = new BranchGroup [1];
    final Exception [] error = new Exception [1];
    try {
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

  public List<String> getMaterialNames(Content model) throws IOException {
    List<String> names = new ArrayList<String>();
    for (HomeMaterial material : ModelManager.getInstance().getMaterials(load(model))) {
      names.add(material.getName());
    }
    return names;
  }

  private static void collect(Node node, String prefix, List<Node> nodes) {
    if (node.getUserData() instanceof String && ((String)node.getUserData()).startsWith(prefix)
        && !((String)node.getUserData()).endsWith(ModelManager.DEFORMABLE_TRANSFORM_GROUP_SUFFIX)) {
      nodes.add(node);
    } else if (node instanceof Group) {
      Group group = (Group)node;
      for (int i = 0; i < group.numChildren(); i++) {
        collect((Node)group.getChild(i), prefix, nodes);
      }
    }
  }

  private static TransformGroup findTransformGroup(Node node, String userData) {
    if (node instanceof TransformGroup && userData.equals(node.getUserData())) {
      return (TransformGroup)node;
    } else if (node instanceof Group) {
      Group group = (Group)node;
      for (int i = 0; i < group.numChildren(); i++) {
        TransformGroup found = findTransformGroup((Node)group.getChild(i), userData);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static double [] getBounds(List<Node> nodes) {
    double [] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
    for (Node node : nodes) {
      try {
        BoundingBox box = ModelManager.getInstance().getBounds(node);
        Point3d lower = new Point3d();
        Point3d upper = new Point3d();
        box.getLower(lower);
        box.getUpper(upper);
        bounds [0] = Math.min(bounds [0], lower.x);
        bounds [1] = Math.min(bounds [1], lower.y);
        bounds [2] = Math.min(bounds [2], lower.z);
        bounds [3] = Math.max(bounds [3], upper.x);
        bounds [4] = Math.max(bounds [4], upper.y);
        bounds [5] = Math.max(bounds [5], upper.z);
      } catch (IllegalArgumentException ex) {
        // A node without geometry
      }
    }
    return bounds [0] == Double.POSITIVE_INFINITY ? null : bounds;
  }

  private static float [] getModelBounds(BranchGroup root, float [][] modelRotation) {
    Transform3D rotation = new Transform3D();
    if (modelRotation != null) {
      rotation.setRotation(new Matrix3f(modelRotation [0][0], modelRotation [0][1], modelRotation [0][2],
          modelRotation [1][0], modelRotation [1][1], modelRotation [1][2],
          modelRotation [2][0], modelRotation [2][1], modelRotation [2][2]));
    }
    BoundingBox box = ModelManager.getInstance().getBounds(root, rotation);
    Point3d lower = new Point3d();
    Point3d upper = new Point3d();
    box.getLower(lower);
    box.getUpper(upper);
    return new float [] {(float)lower.x, (float)lower.y, (float)lower.z, (float)upper.x, (float)upper.y, (float)upper.z};
  }

  private static void apply(BranchGroup root, Transformation [] transformations) {
    if (transformations != null) {
      for (Transformation transformation : transformations) {
        TransformGroup group = findTransformGroup(root,
            transformation.getName() + ModelManager.DEFORMABLE_TRANSFORM_GROUP_SUFFIX);
        if (group != null) {
          float [][] matrix = transformation.getMatrix();
          group.setTransform(new Transform3D(new float [] {
              matrix [0][0], matrix [0][1], matrix [0][2], matrix [0][3],
              matrix [1][0], matrix [1][1], matrix [1][2], matrix [1][3],
              matrix [2][0], matrix [2][1], matrix [2][2], matrix [2][3], 0, 0, 0, 1}));
        }
      }
    }
  }

  public AssistantCommandExecutor.ModelOpening getOpening(Content model, float [][] modelRotation,
                                                           Transformation [] currentTransformations,
                                                           float angle) throws IOException {
    AssistantCommandExecutor.ModelOpening opening = new AssistantCommandExecutor.ModelOpening();
    // The size of the model as it is now
    BranchGroup currentRoot = load(model);
    apply(currentRoot, currentTransformations);
    opening.currentBounds = getModelBounds(currentRoot, modelRotation);

    BranchGroup root = load(model);
    Map<String, Transformation> transformations = new TreeMap<String, Transformation>();
    if (currentTransformations != null) {
      for (Transformation transformation : currentTransformations) {
        // What isn't a leaf on hinges keeps its place
        if (!transformation.getName().startsWith(ModelManager.HINGE_PREFIX)) {
          transformations.put(transformation.getName(), transformation);
        }
      }
    }
    for (int i = 1; i < 20; i++) {
      String name = ModelManager.HINGE_PREFIX + i;
      TransformGroup group = findTransformGroup(root, name + ModelManager.DEFORMABLE_TRANSFORM_GROUP_SUFFIX);
      if (group == null) {
        break;
      }
      List<Node> hinges = new ArrayList<Node>();
      // The hinges are named sweethome3d_hinge_1, or sweethome3d_hinge_1_2 when there are several
      collect(root, name, hinges);
      for (int j = hinges.size() - 1; j >= 0; j--) {
        String hingeName = (String)hinges.get(j).getUserData();
        if (hingeName.length() > name.length() && Character.isDigit(hingeName.charAt(name.length()))) {
          hinges.remove(j);
        }
      }
      double [] hingeBounds = getBounds(hinges);
      List<Node> leaves = new ArrayList<Node>();
      leaves.add(group);
      double [] leafBounds = getBounds(leaves);
      if (hingeBounds == null || leafBounds == null) {
        continue;
      }
      opening.hingeCount++;
      if (Math.abs(angle) < 0.01f) {
        continue;
      }
      double [] center = {(hingeBounds [0] + hingeBounds [3]) / 2, (hingeBounds [1] + hingeBounds [4]) / 2,
                          (hingeBounds [2] + hingeBounds [5]) / 2};
      double [] leafCenter = {(leafBounds [0] + leafBounds [3]) / 2, (leafBounds [1] + leafBounds [4]) / 2,
                              (leafBounds [2] + leafBounds [5]) / 2};
      double [] size = {hingeBounds [3] - hingeBounds [0], hingeBounds [4] - hingeBounds [1], hingeBounds [5] - hingeBounds [2]};
      // The leaf turns around the longest side of its hinges
      int axis = size [1] >= size [0] && size [1] >= size [2] ? 1 : size [0] >= size [2] ? 0 : 2;
      Transform3D rotation = new Transform3D();
      double radians = Math.toRadians(angle);
      if (axis == 1) {
        // Two leaves that face each other turn in opposite directions
        rotation.rotY(leafCenter [0] >= center [0] ? radians : -radians);
      } else if (axis == 0) {
        rotation.rotX(leafCenter [1] <= center [1] ? radians : -radians);
      } else {
        rotation.rotZ(leafCenter [0] >= center [0] ? radians : -radians);
      }
      Transform3D toOrigin = new Transform3D();
      toOrigin.setTranslation(new Vector3d(-center [0], -center [1], -center [2]));
      Transform3D fromOrigin = new Transform3D();
      fromOrigin.setTranslation(new Vector3d(center [0], center [1], center [2]));
      Transform3D transform = new Transform3D();
      transform.mul(fromOrigin, rotation);
      transform.mul(toOrigin);
      float [] matrix = new float [16];
      transform.get(matrix);
      transformations.put(name, new Transformation(name, new float [][] {
          {matrix [0], matrix [1], matrix [2], matrix [3]},
          {matrix [4], matrix [5], matrix [6], matrix [7]},
          {matrix [8], matrix [9], matrix [10], matrix [11]}}));
    }
    opening.railCount = findTransformGroup(root, ModelManager.RAIL_PREFIX + 1 + ModelManager.DEFORMABLE_TRANSFORM_GROUP_SUFFIX) != null ? 1 : 0;
    opening.transformations = transformations.isEmpty() ? null
        : transformations.values().toArray(new Transformation [transformations.size()]);
    apply(root, opening.transformations);
    opening.newBounds = getModelBounds(root, modelRotation);
    return opening;
  }
}
