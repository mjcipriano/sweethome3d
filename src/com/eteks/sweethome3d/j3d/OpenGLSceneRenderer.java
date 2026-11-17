/*
 * OpenGLSceneRenderer.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.j3d;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.GeometryStripArray;
import javax.media.j3d.Group;
import javax.media.j3d.ImageComponent;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.IndexedGeometryArray;
import javax.media.j3d.IndexedGeometryStripArray;
import javax.media.j3d.IndexedLineArray;
import javax.media.j3d.IndexedLineStripArray;
import javax.media.j3d.IndexedQuadArray;
import javax.media.j3d.IndexedTriangleArray;
import javax.media.j3d.IndexedTriangleFanArray;
import javax.media.j3d.IndexedTriangleStripArray;
import javax.media.j3d.Light;
import javax.media.j3d.LineArray;
import javax.media.j3d.LineAttributes;
import javax.media.j3d.LineStripArray;
import javax.media.j3d.Link;
import javax.media.j3d.Material;
import javax.media.j3d.Node;
import javax.media.j3d.PolygonAttributes;
import javax.media.j3d.QuadArray;
import javax.media.j3d.RenderingAttributes;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Switch;
import javax.media.j3d.TexCoordGeneration;
import javax.media.j3d.Texture;
import javax.media.j3d.TextureAttributes;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TransparencyAttributes;
import javax.media.j3d.TriangleArray;
import javax.media.j3d.TriangleFanArray;
import javax.media.j3d.TriangleStripArray;
import javax.vecmath.Color3f;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import javax.vecmath.TexCoord2f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.fixedfunc.GLPointerFunc;

/**
 * A renderer which draws with OpenGL a scene described by Java 3D nodes
 * that are not attached to a Java 3D universe. The nodes are used only as
 * the description of the scene: their geometries are copied once in buffers
 * kept by the graphics card and shared by all the shapes which use them, and
 * each frame draws these buffers without going through Java 3D.
 * All the methods of this class must be called in the same thread, the ones
 * that receive a <code>GL2</code> parameter with its OpenGL context current.
 * @author Emmanuel Puybaret
 */
public class OpenGLSceneRenderer {
  private static final int MAX_LIGHTS = 8;
  // Maximum count of vertices copied to the graphics card during a frame,
  // to keep the frames short while the models of a home are being loaded
  private static final int VERTEX_UPLOAD_BUDGET =
      Integer.getInteger("com.eteks.sweethome3d.opengl.vertexUploadBudget", 1500000);

  private Node   sceneRoot;
  private Group  objectsGroup;

  private final Map<Node, ObjectEntry>  objects = new IdentityHashMap<Node, ObjectEntry>();
  private final Map<Geometry, Mesh>     meshes = new IdentityHashMap<Geometry, Mesh>();
  private final Map<Texture, GLTexture> textures = new IdentityHashMap<Texture, GLTexture>();
  private final Map<State, State>       states = new HashMap<State, State>();

  private boolean     allObjectsDirty = true;
  private boolean     listsDirty = true;
  private boolean     resourcesDirty;
  private long        frame;
  private List<Item>  opaqueItems = new ArrayList<Item>();
  private List<Item>  transparentItems = new ArrayList<Item>();
  private List<Item>  environmentItems = new ArrayList<Item>();
  private List<Item>  backgroundItems = new ArrayList<Item>();
  private List<LightState> lights = new ArrayList<LightState>();
  private List<LightState> backgroundLights = new ArrayList<LightState>();
  private float []    backgroundColor = {0.9f, 0.9f, 0.9f};

  // Camera of the last frame
  private final double [] viewMatrix = new double [16];
  private final double [] projectionMatrix = new double [16];
  private int      viewWidth;
  private int      viewHeight;
  private boolean  pendingUploads;

  // Statistics of the last frame
  private int      drawnItemCount;
  private long     drawnVertexCount;
  private int      culledItemCount;

  // Current OpenGL state
  private State    currentState;
  private Mesh     currentMesh;
  private int      currentTexture;
  private boolean  currentFrontFaceClockwise;
  private boolean  generateMipmapAvailable;
  private int      maxTextureSize;
  private int      vertexUploadBudget;

  /**
   * Sets the root of the scene displayed by this renderer and the group among
   * its descendants whose children are the objects updated one by one.
   */
  public void setScene(Node sceneRoot, Group objectsGroup) {
    this.sceneRoot = sceneRoot;
    this.objectsGroup = objectsGroup;
    this.objects.clear();
    this.allObjectsDirty = true;
    this.listsDirty = true;
    this.resourcesDirty = true;
  }

  /**
   * Notes that the given child of the objects group changed.
   */
  public void markObjectDirty(Node object) {
    ObjectEntry entry = this.objects.get(object);
    if (entry != null) {
      entry.dirty = true;
    }
  }

  /**
   * Notes that any object of the scene may have changed.
   */
  public void markAllObjectsDirty() {
    this.allObjectsDirty = true;
  }

  /**
   * Returns <code>true</code> if some geometries weren't copied to the graphics
   * card during the last frame, which should be followed by an other one.
   */
  public boolean isFramePending() {
    return this.pendingUploads;
  }

  /**
   * Returns the count of shapes drawn in the last frame.
   */
  public int getDrawnItemCount() {
    return this.drawnItemCount;
  }

  /**
   * Returns the count of vertices drawn in the last frame.
   */
  public long getDrawnVertexCount() {
    return this.drawnVertexCount;
  }

  /**
   * Returns the count of shapes out of sight which weren't drawn in the last frame.
   */
  public int getCulledItemCount() {
    return this.culledItemCount;
  }

  /**
   * Initializes the OpenGL context.
   */
  public void init(GL2 gl) {
    // Buffers and textures of a previous context are lost
    for (Mesh mesh : this.meshes.values()) {
      mesh.buffer = 0;
      mesh.uploaded = false;
    }
    this.textures.clear();
    this.generateMipmapAvailable = gl.isFunctionAvailable("glGenerateMipmap");
    int [] value = new int [1];
    gl.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, value, 0);
    this.maxTextureSize = Math.max(256, value [0]);
  }

  /**
   * Frees the resources kept by the OpenGL context.
   */
  public void dispose(GL2 gl) {
    for (Mesh mesh : this.meshes.values()) {
      if (mesh.buffer != 0) {
        gl.glDeleteBuffers(1, new int [] {mesh.buffer}, 0);
        mesh.buffer = 0;
        mesh.uploaded = false;
      }
    }
    for (GLTexture texture : this.textures.values()) {
      if (texture.id != 0) {
        gl.glDeleteTextures(1, new int [] {texture.id}, 0);
      }
    }
    this.textures.clear();
  }

  /**
   * Draws the scene viewed from the given camera in a viewport of the given size.
   * @param cameraTransform  the transformation of the camera in the scene
   * @param fieldOfView      the horizontal field of view in radians
   */
  public void render(GL2 gl, int width, int height,
                     Transform3D cameraTransform, double fieldOfView,
                     double frontClipDistance, double backClipDistance) {
    this.frame++;
    this.viewWidth = width;
    this.viewHeight = height;
    this.vertexUploadBudget = VERTEX_UPLOAD_BUDGET;
    this.pendingUploads = false;
    this.drawnItemCount = 0;
    this.drawnVertexCount = 0;
    this.culledItemCount = 0;

    updateScene();

    // Camera matrices
    Transform3D view = new Transform3D(cameraTransform);
    view.invert();
    view.get(this.viewMatrix);
    double aspect = height > 0 ? (double)width / height : 1;
    double halfWidth = Math.tan(fieldOfView / 2) * frontClipDistance;
    double halfHeight = halfWidth / aspect;
    setFrustum(this.projectionMatrix, -halfWidth, halfWidth, -halfHeight, halfHeight, frontClipDistance, backClipDistance);

    gl.glViewport(0, 0, width, height);
    gl.glDepthMask(true);
    gl.glClearColor(this.backgroundColor [0], this.backgroundColor [1], this.backgroundColor [2], 1);
    gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

    gl.glEnable(GLLightingFunc.GL_NORMALIZE);
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_COLOR_CONTROL, GL2.GL_SEPARATE_SPECULAR_COLOR);
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_LOCAL_VIEWER, 0);
    gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
    gl.glDepthFunc(GL.GL_LEQUAL);
    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
    gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
    resetState(gl);

    if (!this.backgroundItems.isEmpty()) {
      // Draw the background around the camera, whatever its location
      double [] backgroundProjection = new double [16];
      double backgroundHalfWidth = Math.tan(fieldOfView / 2) * 0.1;
      setFrustum(backgroundProjection, -backgroundHalfWidth, backgroundHalfWidth,
          -backgroundHalfWidth / aspect, backgroundHalfWidth / aspect, 0.1, 10);
      double [] backgroundView = this.viewMatrix.clone();
      backgroundView [3] = 0;
      backgroundView [7] = 0;
      backgroundView [11] = 0;
      loadMatrix(gl, GLMatrixFunc.GL_PROJECTION, backgroundProjection);
      loadMatrix(gl, GLMatrixFunc.GL_MODELVIEW, backgroundView);
      setLights(gl, this.backgroundLights);
      gl.glDisable(GL.GL_DEPTH_TEST);
      gl.glDepthMask(false);
      for (Item item : this.backgroundItems) {
        drawItem(gl, item, backgroundView, true);
      }
      gl.glDepthMask(true);
      resetState(gl);
    }

    loadMatrix(gl, GLMatrixFunc.GL_PROJECTION, this.projectionMatrix);
    loadMatrix(gl, GLMatrixFunc.GL_MODELVIEW, this.viewMatrix);
    setLights(gl, this.lights);
    gl.glEnable(GL.GL_DEPTH_TEST);

    double [][] frustumPlanes = getFrustumPlanes(this.projectionMatrix, this.viewMatrix);
    List<Item> blendedItems = new ArrayList<Item>();
    for (Item item : this.environmentItems) {
      if (item.isBlended(this)) {
        blendedItems.add(item);
      } else {
        drawItem(gl, item, this.viewMatrix, false);
      }
    }
    for (Item item : this.opaqueItems) {
      if (isOutOfSight(item, frustumPlanes)) {
        this.culledItemCount++;
      } else if (item.isBlended(this)) {
        // Its texture, loaded since the lists were built, is partly transparent
        blendedItems.add(item);
      } else {
        drawItem(gl, item, this.viewMatrix, false);
      }
    }
    for (Item item : this.transparentItems) {
      if (isOutOfSight(item, frustumPlanes)) {
        this.culledItemCount++;
      } else {
        blendedItems.add(item);
      }
    }
    if (!blendedItems.isEmpty()) {
      // Draw transparent shapes from the farthest to the closest one
      for (Item item : blendedItems) {
        item.viewDepth = getViewDepth(item, this.viewMatrix);
      }
      Collections.sort(blendedItems, new Comparator<Item>() {
          public int compare(Item item1, Item item2) {
            return Double.compare(item1.viewDepth, item2.viewDepth);
          }
        });
      for (Item item : blendedItems) {
        drawItem(gl, item, this.viewMatrix, false);
      }
    }

    resetState(gl);
    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    deleteUnusedResources(gl);
  }

  /**
   * Updates the description of the scene from its nodes.
   */
  private void updateScene() {
    if (this.sceneRoot == null) {
      return;
    }
    // Environment: background, ground and lights are described again at each frame
    this.environmentItems = new ArrayList<Item>();
    this.backgroundItems = new ArrayList<Item>();
    this.lights = new ArrayList<LightState>();
    this.backgroundLights = new ArrayList<LightState>();
    Transform3D identity = new Transform3D();
    if (this.sceneRoot instanceof Group) {
      Group root = (Group)this.sceneRoot;
      for (int i = 0, n = root.numChildren(); i < n; i++) {
        Node child = root.getChild(i);
        if (child != this.objectsGroup) {
          extractEnvironment(child, identity);
        }
      }
    }

    // Objects
    if (this.objectsGroup != null) {
      boolean changed = false;
      int count = this.objectsGroup.numChildren();
      for (int i = 0; i < count; i++) {
        Node child = this.objectsGroup.getChild(i);
        ObjectEntry entry = this.objects.get(child);
        if (entry == null) {
          entry = new ObjectEntry();
          this.objects.put(child, entry);
          entry.dirty = true;
        }
        entry.frame = this.frame;
        if (entry.dirty || this.allObjectsDirty) {
          entry.items = new ArrayList<Item>();
          extract(child, identity, true, child, entry.items, null);
          entry.dirty = false;
          changed = true;
        }
      }
      if (this.objects.size() != count) {
        // Forget removed objects
        for (Iterator<ObjectEntry> it = this.objects.values().iterator(); it.hasNext(); ) {
          if (it.next().frame != this.frame) {
            it.remove();
            changed = true;
          }
        }
      }
      this.allObjectsDirty = false;
      if (changed || this.listsDirty) {
        this.opaqueItems = new ArrayList<Item>();
        this.transparentItems = new ArrayList<Item>();
        for (ObjectEntry entry : this.objects.values()) {
          for (Item item : entry.items) {
            if (item.state.isBlended()) {
              this.transparentItems.add(item);
            } else {
              this.opaqueItems.add(item);
            }
          }
        }
        // Sort opaque shapes to change OpenGL state as rarely as possible
        Collections.sort(this.opaqueItems, new Comparator<Item>() {
            public int compare(Item item1, Item item2) {
              int order = item1.state.order - item2.state.order;
              return order != 0
                  ? order
                  : item1.mesh.order - item2.mesh.order;
            }
          });
        this.listsDirty = false;
        this.resourcesDirty = true;
      }
    }
  }

  /**
   * Describes a node which isn't an object of the home.
   */
  private void extractEnvironment(Node node, Transform3D transform) {
    if (node instanceof Background) {
      Background background = (Background)node;
      Color3f color = new Color3f();
      background.getColor(color);
      this.backgroundColor = new float [] {color.x, color.y, color.z};
      if (background.getGeometry() != null) {
        extract(background.getGeometry(), transform, false, null, this.backgroundItems, this.backgroundLights);
      }
    } else {
      extract(node, transform, false, null, this.environmentItems, this.lights);
    }
  }

  /**
   * Adds to <code>items</code> the visible shapes among the descendants of <code>node</code>.
   */
  private void extract(Node node, Transform3D parentTransform, boolean pickable, Node owner,
                       List<Item> items, List<LightState> lights) {
    if (node instanceof Group) {
      Group group = (Group)node;
      Transform3D transform = parentTransform;
      if (node instanceof TransformGroup) {
        Transform3D groupTransform = new Transform3D();
        ((TransformGroup)node).getTransform(groupTransform);
        transform = new Transform3D(parentTransform);
        transform.mul(groupTransform);
      }
      boolean childrenPickable = pickable && group.getPickable();
      if (node instanceof Switch) {
        Switch switchNode = (Switch)node;
        int whichChild = switchNode.getWhichChild();
        if (whichChild == Switch.CHILD_ALL) {
          for (int i = 0, n = group.numChildren(); i < n; i++) {
            extract(group.getChild(i), transform, childrenPickable, owner, items, lights);
          }
        } else if (whichChild == Switch.CHILD_MASK) {
          BitSet mask = switchNode.getChildMask();
          for (int i = 0, n = group.numChildren(); i < n; i++) {
            if (mask.get(i)) {
              extract(group.getChild(i), transform, childrenPickable, owner, items, lights);
            }
          }
        } else if (whichChild >= 0 && whichChild < group.numChildren()) {
          extract(group.getChild(whichChild), transform, childrenPickable, owner, items, lights);
        }
      } else {
        for (int i = 0, n = group.numChildren(); i < n; i++) {
          extract(group.getChild(i), transform, childrenPickable, owner, items, lights);
        }
      }
    } else if (node instanceof Link) {
      extract(((Link)node).getSharedGroup(), parentTransform, pickable, owner, items, lights);
    } else if (node instanceof Shape3D) {
      Shape3D shape = (Shape3D)node;
      Appearance appearance = shape.getAppearance();
      RenderingAttributes renderingAttributes = appearance != null
          ? appearance.getRenderingAttributes()
          : null;
      if (renderingAttributes == null || renderingAttributes.getVisible()) {
        State state = null;
        float [] matrix = null;
        for (int i = 0, n = shape.numGeometries(); i < n; i++) {
          Geometry geometry = shape.getGeometry(i);
          if (geometry instanceof GeometryArray) {
            Mesh mesh = this.meshes.get(geometry);
            if (mesh == null) {
              mesh = new Mesh((GeometryArray)geometry, this.meshes.size());
              this.meshes.put(geometry, mesh);
            }
            if (mesh.mode != -1) {
              if (state == null) {
                state = getState(appearance);
                matrix = toMatrix(parentTransform);
              }
              Item item = new Item();
              item.mesh = mesh;
              item.state = state;
              item.matrix = matrix;
              item.mirrored = getDeterminant(matrix) < 0;
              item.pickable = pickable && shape.getPickable();
              item.owner = owner;
              items.add(item);
            }
          }
        }
      }
    } else if (node instanceof Light && lights != null) {
      Light light = (Light)node;
      if (light.getEnable()) {
        Color3f color = new Color3f();
        light.getColor(color);
        if (light instanceof AmbientLight) {
          lights.add(new LightState(color, null));
        } else if (light instanceof DirectionalLight) {
          Vector3f direction = new Vector3f();
          ((DirectionalLight)light).getDirection(direction);
          parentTransform.transform(direction);
          lights.add(new LightState(color, direction));
        }
      }
    }
  }

  /**
   * Returns the shared state matching the given appearance.
   */
  private State getState(Appearance appearance) {
    State state = new State(appearance);
    State sharedState = this.states.get(state);
    if (sharedState == null) {
      if (this.states.size() > 20000) {
        // Don't keep the states of the colors and textures tried during a long session
        this.states.clear();
      }
      state.order = this.states.size();
      this.states.put(state, state);
      sharedState = state;
    }
    return sharedState;
  }

  /**
   * Deletes the buffers and the textures that no shape uses anymore.
   */
  private void deleteUnusedResources(GL2 gl) {
    if (this.resourcesDirty && !this.pendingUploads) {
      this.resourcesDirty = false;
      IdentityHashMap<Mesh, Boolean> usedMeshes = new IdentityHashMap<Mesh, Boolean>();
      IdentityHashMap<Texture, Boolean> usedTextures = new IdentityHashMap<Texture, Boolean>();
      List<List<Item>> lists = new ArrayList<List<Item>>();
      lists.add(this.opaqueItems);
      lists.add(this.transparentItems);
      lists.add(this.environmentItems);
      lists.add(this.backgroundItems);
      for (List<Item> list : lists) {
        for (Item item : list) {
          usedMeshes.put(item.mesh, Boolean.TRUE);
          if (item.state.texture != null) {
            usedTextures.put(item.state.texture, Boolean.TRUE);
          }
        }
      }
      for (Iterator<Mesh> it = this.meshes.values().iterator(); it.hasNext(); ) {
        Mesh mesh = it.next();
        if (!usedMeshes.containsKey(mesh)) {
          if (mesh.buffer != 0) {
            gl.glDeleteBuffers(1, new int [] {mesh.buffer}, 0);
          }
          it.remove();
        }
      }
      for (Iterator<Map.Entry<Texture, GLTexture>> it = this.textures.entrySet().iterator(); it.hasNext(); ) {
        Map.Entry<Texture, GLTexture> entry = it.next();
        if (!usedTextures.containsKey(entry.getKey())) {
          if (entry.getValue().id != 0) {
            gl.glDeleteTextures(1, new int [] {entry.getValue().id}, 0);
          }
          it.remove();
        }
      }
    }
  }

  /**
   * Sets the lights of the scene.
   */
  private void setLights(GL2 gl, List<LightState> lights) {
    float [] ambient = {0, 0, 0, 1};
    int lightIndex = 0;
    for (LightState light : lights) {
      if (light.direction == null) {
        ambient [0] += light.color [0];
        ambient [1] += light.color [1];
        ambient [2] += light.color [2];
      } else if (lightIndex < MAX_LIGHTS) {
        int glLight = GLLightingFunc.GL_LIGHT0 + lightIndex++;
        gl.glEnable(glLight);
        gl.glLightfv(glLight, GLLightingFunc.GL_POSITION, light.direction, 0);
        gl.glLightfv(glLight, GLLightingFunc.GL_DIFFUSE, light.color, 0);
        gl.glLightfv(glLight, GLLightingFunc.GL_SPECULAR, light.color, 0);
        gl.glLightfv(glLight, GLLightingFunc.GL_AMBIENT, new float [] {0, 0, 0, 1}, 0);
      }
    }
    for (int i = lightIndex; i < MAX_LIGHTS; i++) {
      gl.glDisable(GLLightingFunc.GL_LIGHT0 + i);
    }
    gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, ambient, 0);
  }

  /**
   * Forgets the current OpenGL state and sets its default values.
   */
  private void resetState(GL2 gl) {
    this.currentState = null;
    this.currentMesh = null;
    this.currentTexture = 0;
    this.currentFrontFaceClockwise = false;
    gl.glFrontFace(GL.GL_CCW);
    gl.glDisable(GL.GL_BLEND);
    gl.glDisable(GL.GL_TEXTURE_2D);
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    gl.glDisable(GLLightingFunc.GL_LIGHTING);
    gl.glDisable(GL2.GL_TEXTURE_GEN_S);
    gl.glDisable(GL2.GL_TEXTURE_GEN_T);
    gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
    gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL2.GL_FILL);
    gl.glEnable(GL.GL_CULL_FACE);
    gl.glCullFace(GL.GL_BACK);
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, 0);
    gl.glLineWidth(1);
    gl.glDepthMask(true);
    gl.glMatrixMode(GL.GL_TEXTURE);
    gl.glLoadIdentity();
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    gl.glDisableClientState(GLPointerFunc.GL_NORMAL_ARRAY);
    gl.glDisableClientState(GLPointerFunc.GL_TEXTURE_COORD_ARRAY);
    gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
    gl.glColor4f(1, 1, 1, 1);
  }

  /**
   * Draws a shape.
   */
  private void drawItem(GL2 gl, Item item, double [] viewMatrix, boolean background) {
    Mesh mesh = item.mesh;
    if (!mesh.uploaded) {
      if (mesh.failed) {
        return;
      } else if (this.vertexUploadBudget <= 0) {
        // Leave it to a next frame
        this.pendingUploads = true;
        return;
      }
      upload(gl, mesh);
      if (!mesh.uploaded) {
        return;
      }
    }
    if (mesh.vertexCount == 0) {
      return;
    }

    State state = item.state;
    if (state != this.currentState) {
      applyState(gl, state, background);
    }
    if (item.mirrored != this.currentFrontFaceClockwise) {
      this.currentFrontFaceClockwise = item.mirrored;
      gl.glFrontFace(item.mirrored ? GL.GL_CW : GL.GL_CCW);
    }
    if (mesh != this.currentMesh) {
      this.currentMesh = mesh;
      gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mesh.buffer);
      int stride = mesh.stride * 4;
      gl.glVertexPointer(3, GL.GL_FLOAT, stride, 0);
      int offset = 12;
      if (mesh.normals) {
        gl.glEnableClientState(GLPointerFunc.GL_NORMAL_ARRAY);
        gl.glNormalPointer(GL.GL_FLOAT, stride, offset);
        offset += 12;
      } else {
        gl.glDisableClientState(GLPointerFunc.GL_NORMAL_ARRAY);
        gl.glNormal3f(0, 1, 0);
      }
      if (mesh.textureCoordinates) {
        gl.glEnableClientState(GLPointerFunc.GL_TEXTURE_COORD_ARRAY);
        gl.glTexCoordPointer(2, GL.GL_FLOAT, stride, offset);
        offset += 8;
      } else {
        gl.glDisableClientState(GLPointerFunc.GL_TEXTURE_COORD_ARRAY);
      }
      if (mesh.colors) {
        gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        gl.glColorPointer(4, GL.GL_FLOAT, stride, offset);
      } else {
        gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
      }
    }
    if (!mesh.colors && !state.lighting) {
      gl.glColor4f(state.color [0], state.color [1], state.color [2], state.alpha);
    }

    // Model view matrix
    float [] m = item.matrix;
    float [] modelView = new float [16];
    for (int column = 0; column < 4; column++) {
      for (int row = 0; row < 4; row++) {
        // viewMatrix is stored row by row, m and modelView column by column
        modelView [column * 4 + row] = (float)(viewMatrix [row * 4] * m [column * 4]
            + viewMatrix [row * 4 + 1] * m [column * 4 + 1]
            + viewMatrix [row * 4 + 2] * m [column * 4 + 2]
            + viewMatrix [row * 4 + 3] * m [column * 4 + 3]);
      }
    }
    gl.glLoadMatrixf(modelView, 0);
    gl.glDrawArrays(mesh.mode, 0, mesh.vertexCount);
    this.drawnItemCount++;
    this.drawnVertexCount += mesh.vertexCount;
  }

  /**
   * Sets the OpenGL state of the following shapes.
   */
  private void applyState(GL2 gl, State state, boolean background) {
    this.currentState = state;
    if (state.lighting) {
      gl.glEnable(GLLightingFunc.GL_LIGHTING);
      gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GLLightingFunc.GL_AMBIENT, state.ambient, 0);
      gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GLLightingFunc.GL_DIFFUSE, state.diffuse, 0);
      gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GLLightingFunc.GL_SPECULAR, state.specular, 0);
      gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GLLightingFunc.GL_EMISSION, state.emissive, 0);
      gl.glMaterialf(GL.GL_FRONT_AND_BACK, GLLightingFunc.GL_SHININESS, state.shininess);
    } else {
      gl.glDisable(GLLightingFunc.GL_LIGHTING);
      gl.glColor4f(state.color [0], state.color [1], state.color [2], state.alpha);
    }

    GLTexture texture = state.texture != null
        ? getTexture(gl, state.texture)
        : null;
    if (texture != null && texture.id != 0) {
      gl.glEnable(GL.GL_TEXTURE_2D);
      if (texture.id != this.currentTexture) {
        this.currentTexture = texture.id;
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture.id);
      }
      gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, state.textureMode);
      gl.glMatrixMode(GL.GL_TEXTURE);
      if (state.textureTransform != null) {
        gl.glLoadMatrixf(state.textureTransform, 0);
      } else {
        gl.glLoadIdentity();
      }
      gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
      if (state.texturePlaneS != null) {
        gl.glEnable(GL2.GL_TEXTURE_GEN_S);
        gl.glEnable(GL2.GL_TEXTURE_GEN_T);
        gl.glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
        gl.glTexGeni(GL2.GL_T, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
        gl.glTexGenfv(GL2.GL_S, GL2.GL_OBJECT_PLANE, state.texturePlaneS, 0);
        gl.glTexGenfv(GL2.GL_T, GL2.GL_OBJECT_PLANE, state.texturePlaneT, 0);
      } else {
        gl.glDisable(GL2.GL_TEXTURE_GEN_S);
        gl.glDisable(GL2.GL_TEXTURE_GEN_T);
      }
    } else {
      gl.glDisable(GL.GL_TEXTURE_2D);
      gl.glDisable(GL2.GL_TEXTURE_GEN_S);
      gl.glDisable(GL2.GL_TEXTURE_GEN_T);
    }

    boolean blended = state.isBlended() || texture != null && texture.transparent;
    if (blended) {
      gl.glEnable(GL.GL_BLEND);
    } else {
      gl.glDisable(GL.GL_BLEND);
    }
    if (!background) {
      if (state.depthTest) {
        gl.glEnable(GL.GL_DEPTH_TEST);
      } else {
        gl.glDisable(GL.GL_DEPTH_TEST);
      }
      gl.glDepthMask(state.depthWrite && !blended);
    }

    if (state.cullFace == PolygonAttributes.CULL_NONE) {
      gl.glDisable(GL.GL_CULL_FACE);
    } else {
      gl.glEnable(GL.GL_CULL_FACE);
      gl.glCullFace(state.cullFace == PolygonAttributes.CULL_FRONT ? GL.GL_FRONT : GL.GL_BACK);
    }
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, state.backFaceNormalFlip ? 1 : 0);
    gl.glPolygonMode(GL.GL_FRONT_AND_BACK, state.polygonMode == PolygonAttributes.POLYGON_LINE
        ? GL2.GL_LINE
        : (state.polygonMode == PolygonAttributes.POLYGON_POINT ? GL2.GL_POINT : GL2.GL_FILL));
    if (state.polygonOffset != 0 || state.polygonOffsetFactor != 0) {
      gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
      gl.glPolygonOffset(state.polygonOffsetFactor, state.polygonOffset);
    } else {
      gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
    }
    gl.glLineWidth(state.lineWidth);
  }

  /**
   * Returns the OpenGL texture matching a Java 3D texture.
   */
  private GLTexture getTexture(GL2 gl, Texture texture) {
    GLTexture glTexture = this.textures.get(texture);
    if (glTexture == null) {
      glTexture = new GLTexture();
      this.textures.put(texture, glTexture);
      try {
        ImageComponent imageComponent = texture.getImage(0);
        if (imageComponent instanceof ImageComponent2D) {
          RenderedImage renderedImage = ((ImageComponent2D)imageComponent).getRenderedImage();
          BufferedImage image;
          if (renderedImage instanceof BufferedImage) {
            image = (BufferedImage)renderedImage;
          } else {
            image = ((ImageComponent2D)imageComponent).getImage();
          }
          int width = image.getWidth();
          int height = image.getHeight();
          if (width > this.maxTextureSize || height > this.maxTextureSize) {
            float scale = Math.min((float)this.maxTextureSize / width, (float)this.maxTextureSize / height);
            int scaledWidth = Math.max(1, (int)(width * scale));
            int scaledHeight = Math.max(1, (int)(height * scale));
            BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2D = scaledImage.createGraphics();
            g2D.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2D.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
            g2D.dispose();
            image = scaledImage;
            width = scaledWidth;
            height = scaledHeight;
          }
          boolean flip = !imageComponent.isYUp();
          int [] row = new int [width];
          ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
          boolean transparent = false;
          boolean alpha = image.getColorModel().hasAlpha();
          for (int y = 0; y < height; y++) {
            image.getRGB(0, flip ? height - 1 - y : y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
              int argb = row [x];
              pixels.put((byte)(argb >> 16));
              pixels.put((byte)(argb >> 8));
              pixels.put((byte)argb);
              pixels.put((byte)(argb >>> 24));
              if (alpha && (argb >>> 24) < 250) {
                transparent = true;
              }
            }
          }
          // Cast required to run under Java 8 once compiled with a more recent JDK
          ((java.nio.Buffer)pixels).flip();
          int [] id = new int [1];
          gl.glGenTextures(1, id, 0);
          gl.glBindTexture(GL.GL_TEXTURE_2D, id [0]);
          this.currentTexture = id [0];
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S,
              texture.getBoundaryModeS() == Texture.WRAP ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T,
              texture.getBoundaryModeT() == Texture.WRAP ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
          if (!this.generateMipmapAvailable) {
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL2.GL_GENERATE_MIPMAP, 1);
          }
          gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0,
              GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
          if (this.generateMipmapAvailable) {
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
          }
          glTexture.id = id [0];
          glTexture.transparent = transparent;
        }
      } catch (RuntimeException ex) {
        // Draw the shapes without their texture
        glTexture.id = 0;
      } catch (OutOfMemoryError ex) {
        glTexture.id = 0;
      }
    }
    return glTexture;
  }

  /**
   * Copies the vertices of a geometry to a buffer of the graphics card.
   */
  private void upload(GL2 gl, Mesh mesh) {
    try {
      FloatBuffer vertices = mesh.createVertices();
      if (vertices != null) {
        if (mesh.vertexCount > 0) {
          int [] buffer = new int [1];
          gl.glGenBuffers(1, buffer, 0);
          gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer [0]);
          gl.glBufferData(GL.GL_ARRAY_BUFFER, (long)vertices.capacity() * 4, vertices, GL.GL_STATIC_DRAW);
          mesh.buffer = buffer [0];
          this.currentMesh = null;
          this.vertexUploadBudget -= mesh.vertexCount;
        }
        mesh.uploaded = true;
      } else {
        mesh.failed = true;
      }
    } catch (RuntimeException ex) {
      mesh.failed = true;
    } catch (OutOfMemoryError ex) {
      mesh.failed = true;
    }
  }

  /**
   * Returns <code>true</code> if the bounding sphere of the given shape is out of the frustum.
   */
  private boolean isOutOfSight(Item item, double [][] planes) {
    Mesh mesh = item.mesh;
    if (!mesh.uploaded) {
      return false;
    }
    if (item.radius < 0) {
      // Compute the bounding sphere in the scene
      float [] m = item.matrix;
      float x = mesh.center [0];
      float y = mesh.center [1];
      float z = mesh.center [2];
      item.center = new double [] {
          m [0] * x + m [4] * y + m [8] * z + m [12],
          m [1] * x + m [5] * y + m [9] * z + m [13],
          m [2] * x + m [6] * y + m [10] * z + m [14]};
      double scaleX = Math.sqrt(m [0] * m [0] + m [1] * m [1] + m [2] * m [2]);
      double scaleY = Math.sqrt(m [4] * m [4] + m [5] * m [5] + m [6] * m [6]);
      double scaleZ = Math.sqrt(m [8] * m [8] + m [9] * m [9] + m [10] * m [10]);
      item.radius = mesh.radius * Math.max(scaleX, Math.max(scaleY, scaleZ));
    }
    for (double [] plane : planes) {
      if (plane [0] * item.center [0] + plane [1] * item.center [1] + plane [2] * item.center [2] + plane [3]
          < -item.radius) {
        return true;
      }
    }
    return false;
  }

  private double getViewDepth(Item item, double [] viewMatrix) {
    float [] m = item.matrix;
    float [] center = item.mesh.center;
    double x = m [0] * center [0] + m [4] * center [1] + m [8] * center [2] + m [12];
    double y = m [1] * center [0] + m [5] * center [1] + m [9] * center [2] + m [13];
    double z = m [2] * center [0] + m [6] * center [1] + m [10] * center [2] + m [14];
    return viewMatrix [8] * x + viewMatrix [9] * y + viewMatrix [10] * z + viewMatrix [11];
  }

  /**
   * Returns the six planes of the frustum in scene coordinates, their normal pointing inside.
   */
  private static double [][] getFrustumPlanes(double [] projection, double [] view) {
    // Rows of projection * view (both stored row by row)
    double [] m = new double [16];
    for (int row = 0; row < 4; row++) {
      for (int column = 0; column < 4; column++) {
        double sum = 0;
        for (int k = 0; k < 4; k++) {
          sum += projection [row * 4 + k] * view [k * 4 + column];
        }
        m [row * 4 + column] = sum;
      }
    }
    double [][] planes = new double [6][4];
    for (int i = 0; i < 4; i++) {
      planes [0][i] = m [12 + i] + m [i];       // Left
      planes [1][i] = m [12 + i] - m [i];       // Right
      planes [2][i] = m [12 + i] + m [4 + i];   // Bottom
      planes [3][i] = m [12 + i] - m [4 + i];   // Top
      planes [4][i] = m [12 + i] + m [8 + i];   // Near
      planes [5][i] = m [12 + i] - m [8 + i];   // Far
    }
    for (double [] plane : planes) {
      double length = Math.sqrt(plane [0] * plane [0] + plane [1] * plane [1] + plane [2] * plane [2]);
      if (length > 0) {
        for (int i = 0; i < 4; i++) {
          plane [i] /= length;
        }
      }
    }
    return planes;
  }

  /**
   * Sets a perspective projection matrix stored row by row.
   */
  private static void setFrustum(double [] m, double left, double right, double bottom, double top,
                                 double near, double far) {
    Arrays.fill(m, 0);
    m [0] = 2 * near / (right - left);
    m [2] = (right + left) / (right - left);
    m [5] = 2 * near / (top - bottom);
    m [6] = (top + bottom) / (top - bottom);
    m [10] = -(far + near) / (far - near);
    m [11] = -2 * far * near / (far - near);
    m [14] = -1;
  }

  /**
   * Loads a matrix stored row by row.
   */
  private static void loadMatrix(GL2 gl, int mode, double [] matrix) {
    double [] columns = new double [16];
    for (int row = 0; row < 4; row++) {
      for (int column = 0; column < 4; column++) {
        columns [column * 4 + row] = matrix [row * 4 + column];
      }
    }
    gl.glMatrixMode(mode);
    gl.glLoadMatrixd(columns, 0);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
  }

  /**
   * Returns the matrix of a transformation stored column by column.
   */
  private static float [] toMatrix(Transform3D transform) {
    double [] rows = new double [16];
    transform.get(rows);
    float [] columns = new float [16];
    for (int row = 0; row < 4; row++) {
      for (int column = 0; column < 4; column++) {
        columns [column * 4 + row] = (float)rows [row * 4 + column];
      }
    }
    return columns;
  }

  private static float getDeterminant(float [] m) {
    return m [0] * (m [5] * m [10] - m [9] * m [6])
        - m [4] * (m [1] * m [10] - m [9] * m [2])
        + m [8] * (m [1] * m [6] - m [5] * m [2]);
  }

  /**
   * Returns the origin and the direction in the scene of the ray which goes
   * through the pixel (x, y) of the last frame, y going down.
   */
  public double [] getPixelRay(int x, int y) {
    if (this.viewWidth <= 0 || this.viewHeight <= 0) {
      return null;
    }
    Matrix4d projection = new Matrix4d(this.projectionMatrix);
    Matrix4d view = new Matrix4d(this.viewMatrix);
    Matrix4d inverse = new Matrix4d();
    inverse.mul(projection, view);
    try {
      inverse.invert();
    } catch (RuntimeException ex) {
      return null;
    }
    double deviceX = 2. * (x + 0.5) / this.viewWidth - 1;
    double deviceY = 1 - 2. * (y + 0.5) / this.viewHeight;
    double [] near = unproject(inverse, deviceX, deviceY, -1);
    double [] far = unproject(inverse, deviceX, deviceY, 1);
    Vector3d direction = new Vector3d(far [0] - near [0], far [1] - near [1], far [2] - near [2]);
    direction.normalize();
    return new double [] {near [0], near [1], near [2], direction.x, direction.y, direction.z};
  }

  private static double [] unproject(Matrix4d inverse, double x, double y, double z) {
    double [] point = {
        inverse.m00 * x + inverse.m01 * y + inverse.m02 * z + inverse.m03,
        inverse.m10 * x + inverse.m11 * y + inverse.m12 * z + inverse.m13,
        inverse.m20 * x + inverse.m21 * y + inverse.m22 * z + inverse.m23};
    double w = inverse.m30 * x + inverse.m31 * y + inverse.m32 * z + inverse.m33;
    point [0] /= w;
    point [1] /= w;
    point [2] /= w;
    return point;
  }

  /**
   * Returns the child of the objects group displayed at the pixel (x, y) of
   * the last frame, or <code>null</code> if there's none.
   */
  public Node pickObject(int x, int y) {
    double [] ray = getPixelRay(x, y);
    if (ray == null) {
      return null;
    }
    Node closestObject = null;
    double closestDistance = Double.POSITIVE_INFINITY;
    for (ObjectEntry entry : this.objects.values()) {
      for (Item item : entry.items) {
        if (item.pickable && item.owner != null
            && item.mesh.uploaded && item.mesh.mode == GL.GL_TRIANGLES) {
          double distance = getIntersectionDistance(item, ray, closestDistance);
          if (distance < closestDistance) {
            closestDistance = distance;
            closestObject = item.owner;
          }
        }
      }
    }
    return closestObject;
  }

  /**
   * Returns the distance from the origin of <code>ray</code> to the closest
   * triangle of a shape it goes through, or infinity.
   */
  private double getIntersectionDistance(Item item, double [] ray, double maxDistance) {
    // Express the ray in the coordinates of the shape
    Matrix4d matrix = new Matrix4d();
    float [] m = item.matrix;
    matrix.setRow(0, m [0], m [4], m [8], m [12]);
    matrix.setRow(1, m [1], m [5], m [9], m [13]);
    matrix.setRow(2, m [2], m [6], m [10], m [14]);
    matrix.setRow(3, 0, 0, 0, 1);
    Matrix4d inverse = new Matrix4d();
    try {
      inverse.invert(matrix);
    } catch (RuntimeException ex) {
      return Double.POSITIVE_INFINITY;
    }
    Point3d origin = new Point3d(ray [0], ray [1], ray [2]);
    Vector3d direction = new Vector3d(ray [3], ray [4], ray [5]);
    inverse.transform(origin);
    inverse.transform(direction);
    // The direction isn't normalized, to keep distances comparable in the scene

    // Check the bounding sphere first
    Mesh mesh = item.mesh;
    double cx = mesh.center [0] - origin.x;
    double cy = mesh.center [1] - origin.y;
    double cz = mesh.center [2] - origin.z;
    double directionLengthSquared = direction.lengthSquared();
    if (directionLengthSquared == 0) {
      return Double.POSITIVE_INFINITY;
    }
    double t = (cx * direction.x + cy * direction.y + cz * direction.z) / directionLengthSquared;
    double dx = cx - t * direction.x;
    double dy = cy - t * direction.y;
    double dz = cz - t * direction.z;
    if (dx * dx + dy * dy + dz * dz > (double)mesh.radius * mesh.radius) {
      return Double.POSITIVE_INFINITY;
    }

    float [] positions = mesh.getPositions();
    if (positions == null) {
      return Double.POSITIVE_INFINITY;
    }
    double closest = maxDistance;
    double ox = origin.x, oy = origin.y, oz = origin.z;
    double rx = direction.x, ry = direction.y, rz = direction.z;
    for (int i = 0; i + 8 < positions.length; i += 9) {
      double e1x = positions [i + 3] - positions [i];
      double e1y = positions [i + 4] - positions [i + 1];
      double e1z = positions [i + 5] - positions [i + 2];
      double e2x = positions [i + 6] - positions [i];
      double e2y = positions [i + 7] - positions [i + 1];
      double e2z = positions [i + 8] - positions [i + 2];
      double px = ry * e2z - rz * e2y;
      double py = rz * e2x - rx * e2z;
      double pz = rx * e2y - ry * e2x;
      double determinant = e1x * px + e1y * py + e1z * pz;
      if (determinant > -1E-12 && determinant < 1E-12) {
        continue;
      }
      double inverseDeterminant = 1 / determinant;
      double tx = ox - positions [i];
      double ty = oy - positions [i + 1];
      double tz = oz - positions [i + 2];
      double u = (tx * px + ty * py + tz * pz) * inverseDeterminant;
      if (u < 0 || u > 1) {
        continue;
      }
      double qx = ty * e1z - tz * e1y;
      double qy = tz * e1x - tx * e1z;
      double qz = tx * e1y - ty * e1x;
      double v = (rx * qx + ry * qy + rz * qz) * inverseDeterminant;
      if (v < 0 || u + v > 1) {
        continue;
      }
      double distance = (e2x * qx + e2y * qy + e2z * qz) * inverseDeterminant;
      if (distance > 0 && distance < closest) {
        closest = distance;
      }
    }
    return closest < maxDistance ? closest : Double.POSITIVE_INFINITY;
  }

  /**
   * The shapes of a child of the objects group.
   */
  private static class ObjectEntry {
    private List<Item> items = Collections.emptyList();
    private boolean    dirty;
    private long       frame;
  }

  /**
   * A shape to draw.
   */
  private static class Item {
    private Mesh      mesh;
    private State     state;
    private float []  matrix;
    private boolean   mirrored;
    private boolean   pickable;
    private Node      owner;
    private double [] center;
    private double    radius = -1;
    private double    viewDepth;

    public boolean isBlended(OpenGLSceneRenderer renderer) {
      if (this.state.isBlended()) {
        return true;
      } else if (this.state.texture != null) {
        GLTexture texture = renderer.textures.get(this.state.texture);
        return texture != null && texture.transparent;
      } else {
        return false;
      }
    }
  }

  /**
   * A light of the scene.
   */
  private static class LightState {
    private final float [] color;
    private final float [] direction;

    public LightState(Color3f color, Vector3f direction) {
      this.color = new float [] {color.x, color.y, color.z, 1};
      // OpenGL expects the direction towards the light
      this.direction = direction != null
          ? new float [] {-direction.x, -direction.y, -direction.z, 0}
          : null;
    }
  }

  /**
   * An OpenGL texture.
   */
  private static class GLTexture {
    private int     id;
    private boolean transparent;
  }

  /**
   * The values of an appearance.
   */
  private static class State {
    private boolean   lighting;
    private float []  ambient;
    private float []  diffuse;
    private float []  specular;
    private float []  emissive;
    private float     shininess;
    private float []  color = {1, 1, 1};
    private float     alpha = 1;
    private boolean   transparency;
    private int       cullFace = PolygonAttributes.CULL_BACK;
    private boolean   backFaceNormalFlip;
    private int       polygonMode = PolygonAttributes.POLYGON_FILL;
    private float     polygonOffset;
    private float     polygonOffsetFactor;
    private boolean   depthTest = true;
    private boolean   depthWrite = true;
    private float     lineWidth = 1;
    private Texture   texture;
    private int       textureMode = GL2.GL_REPLACE;
    private float []  textureTransform;
    private float []  texturePlaneS;
    private float []  texturePlaneT;
    private int       order;

    public State(Appearance appearance) {
      if (appearance != null) {
        TransparencyAttributes transparencyAttributes = appearance.getTransparencyAttributes();
        if (transparencyAttributes != null
            && transparencyAttributes.getTransparencyMode() != TransparencyAttributes.NONE) {
          this.transparency = true;
          this.alpha = 1 - transparencyAttributes.getTransparency();
        }
        ColoringAttributes coloringAttributes = appearance.getColoringAttributes();
        if (coloringAttributes != null) {
          Color3f color = new Color3f();
          coloringAttributes.getColor(color);
          this.color = new float [] {color.x, color.y, color.z};
        }
        Material material = appearance.getMaterial();
        if (material != null && material.getLightingEnable()) {
          this.lighting = true;
          Color3f color = new Color3f();
          material.getAmbientColor(color);
          this.ambient = new float [] {color.x, color.y, color.z, 1};
          material.getDiffuseColor(color);
          this.diffuse = new float [] {color.x, color.y, color.z, this.alpha};
          material.getSpecularColor(color);
          this.specular = new float [] {color.x, color.y, color.z, 1};
          material.getEmissiveColor(color);
          this.emissive = new float [] {color.x, color.y, color.z, 1};
          this.shininess = Math.max(0, Math.min(128, material.getShininess()));
        }
        PolygonAttributes polygonAttributes = appearance.getPolygonAttributes();
        if (polygonAttributes != null) {
          this.cullFace = polygonAttributes.getCullFace();
          this.backFaceNormalFlip = polygonAttributes.getBackFaceNormalFlip();
          this.polygonMode = polygonAttributes.getPolygonMode();
          this.polygonOffset = polygonAttributes.getPolygonOffset();
          this.polygonOffsetFactor = polygonAttributes.getPolygonOffsetFactor();
        }
        RenderingAttributes renderingAttributes = appearance.getRenderingAttributes();
        if (renderingAttributes != null) {
          this.depthTest = renderingAttributes.getDepthBufferEnable();
          this.depthWrite = renderingAttributes.getDepthBufferWriteEnable();
        }
        LineAttributes lineAttributes = appearance.getLineAttributes();
        if (lineAttributes != null) {
          this.lineWidth = Math.max(0.5f, lineAttributes.getLineWidth());
        }
        this.texture = appearance.getTexture();
        if (this.texture != null) {
          TextureAttributes textureAttributes = appearance.getTextureAttributes();
          if (textureAttributes != null) {
            switch (textureAttributes.getTextureMode()) {
              case TextureAttributes.MODULATE :
                this.textureMode = GL2.GL_MODULATE;
                break;
              case TextureAttributes.DECAL :
                this.textureMode = GL2.GL_DECAL;
                break;
              case TextureAttributes.BLEND :
                this.textureMode = GL.GL_BLEND;
                break;
              default :
                this.textureMode = GL2.GL_REPLACE;
                break;
            }
            Transform3D transform = new Transform3D();
            textureAttributes.getTextureTransform(transform);
            if ((transform.getBestType() & Transform3D.IDENTITY) == 0) {
              this.textureTransform = toMatrix(transform);
            }
          }
          TexCoordGeneration texCoordGeneration = appearance.getTexCoordGeneration();
          if (texCoordGeneration != null
              && texCoordGeneration.getEnable()
              && texCoordGeneration.getGenMode() == TexCoordGeneration.OBJECT_LINEAR) {
            Vector4f plane = new Vector4f();
            texCoordGeneration.getPlaneS(plane);
            this.texturePlaneS = new float [] {plane.x, plane.y, plane.z, plane.w};
            texCoordGeneration.getPlaneT(plane);
            this.texturePlaneT = new float [] {plane.x, plane.y, plane.z, plane.w};
          }
        }
      }
    }

    public boolean isBlended() {
      return this.transparency && this.alpha < 1;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof State)) {
        return false;
      }
      State state = (State)object;
      return this.lighting == state.lighting
          && this.texture == state.texture
          && this.alpha == state.alpha
          && this.transparency == state.transparency
          && this.cullFace == state.cullFace
          && this.backFaceNormalFlip == state.backFaceNormalFlip
          && this.polygonMode == state.polygonMode
          && this.polygonOffset == state.polygonOffset
          && this.polygonOffsetFactor == state.polygonOffsetFactor
          && this.depthTest == state.depthTest
          && this.depthWrite == state.depthWrite
          && this.lineWidth == state.lineWidth
          && this.shininess == state.shininess
          && this.textureMode == state.textureMode
          && Arrays.equals(this.color, state.color)
          && Arrays.equals(this.ambient, state.ambient)
          && Arrays.equals(this.diffuse, state.diffuse)
          && Arrays.equals(this.specular, state.specular)
          && Arrays.equals(this.emissive, state.emissive)
          && Arrays.equals(this.textureTransform, state.textureTransform)
          && Arrays.equals(this.texturePlaneS, state.texturePlaneS)
          && Arrays.equals(this.texturePlaneT, state.texturePlaneT);
    }

    @Override
    public int hashCode() {
      int hashCode = Arrays.hashCode(this.color);
      hashCode = 31 * hashCode + Arrays.hashCode(this.diffuse);
      hashCode = 31 * hashCode + Arrays.hashCode(this.ambient);
      hashCode = 31 * hashCode + Arrays.hashCode(this.specular);
      hashCode = 31 * hashCode + Arrays.hashCode(this.textureTransform);
      hashCode = 31 * hashCode + Arrays.hashCode(this.texturePlaneS);
      hashCode = 31 * hashCode + System.identityHashCode(this.texture);
      hashCode = 31 * hashCode + Float.floatToIntBits(this.alpha);
      hashCode = 31 * hashCode + Float.floatToIntBits(this.shininess);
      hashCode = 31 * hashCode + this.cullFace * 7 + this.polygonMode;
      return hashCode;
    }
  }

  /**
   * The vertices of a geometry in the graphics card.
   */
  static class Mesh {
    private final GeometryArray geometry;
    private final int           order;
    private int        mode;
    private int        vertexCount;
    private int        stride;
    private boolean    normals;
    private boolean    textureCoordinates;
    private boolean    colors;
    private int        buffer;
    private boolean    uploaded;
    private boolean    failed;
    private float []   center = {0, 0, 0};
    private float      radius;
    private SoftReference<float []> positions;

    public Mesh(GeometryArray geometry, int order) {
      this.geometry = geometry;
      this.order = order;
      if (geometry instanceof TriangleArray
          || geometry instanceof QuadArray
          || geometry instanceof TriangleStripArray
          || geometry instanceof TriangleFanArray
          || geometry instanceof IndexedTriangleArray
          || geometry instanceof IndexedQuadArray
          || geometry instanceof IndexedTriangleStripArray
          || geometry instanceof IndexedTriangleFanArray) {
        this.mode = GL.GL_TRIANGLES;
      } else if (geometry instanceof LineArray
                 || geometry instanceof LineStripArray
                 || geometry instanceof IndexedLineArray
                 || geometry instanceof IndexedLineStripArray) {
        this.mode = GL.GL_LINES;
      } else {
        // Points and other geometries aren't drawn
        this.mode = -1;
      }
    }

    public int getVertexCount() {
      return this.vertexCount;
    }

    /**
     * Returns the coordinates of the vertices of the triangles of this mesh.
     */
    public float [] getPositions() {
      float [] positions = this.positions != null
          ? this.positions.get()
          : null;
      if (positions == null) {
        try {
          FloatBuffer vertices = createVertices();
          if (vertices == null) {
            return null;
          }
          positions = new float [this.vertexCount * 3];
          for (int i = 0; i < this.vertexCount; i++) {
            positions [i * 3] = vertices.get(i * this.stride);
            positions [i * 3 + 1] = vertices.get(i * this.stride + 1);
            positions [i * 3 + 2] = vertices.get(i * this.stride + 2);
          }
          this.positions = new SoftReference<float []>(positions);
        } catch (RuntimeException ex) {
          return null;
        } catch (OutOfMemoryError ex) {
          return null;
        }
      }
      return positions;
    }

    /**
     * Returns the vertices of the triangles or the lines of the geometry, with
     * for each vertex its coordinates, then its normal, its texture coordinates
     * and its color if they exist.
     */
    public FloatBuffer createVertices() {
      GeometryArray geometry = this.geometry;
      int format = geometry.getVertexFormat();
      int vertexCount = geometry.getVertexCount();
      boolean normals = (format & GeometryArray.NORMALS) != 0;
      boolean textureCoordinates = (format & (GeometryArray.TEXTURE_COORDINATE_2
          | GeometryArray.TEXTURE_COORDINATE_3 | GeometryArray.TEXTURE_COORDINATE_4)) != 0
          && geometry.getTexCoordSetCount() > 0;
      int colorSize = (format & GeometryArray.COLOR_4) == GeometryArray.COLOR_4
          ? 4
          : ((format & GeometryArray.COLOR_3) != 0 ? 3 : 0);
      int textureCoordinatesSize = (format & GeometryArray.TEXTURE_COORDINATE_2) != 0
          ? 2
          : ((format & GeometryArray.TEXTURE_COORDINATE_3) != 0 ? 3 : 4);

      float [] coordinates;
      float [] normalsArray = null;
      float [] textureCoordinatesArray = null;
      float [] colorsArray = null;
      if ((format & GeometryArray.BY_REFERENCE) != 0) {
        if ((format & GeometryArray.USE_NIO_BUFFER) != 0) {
          // Not used by the loaders of Sweet Home 3D
          return null;
        } else if ((format & GeometryArray.INTERLEAVED) != 0) {
          float [] data = geometry.getInterleavedVertices();
          int size = data.length / vertexCount;
          coordinates = new float [vertexCount * 3];
          int coordinatesOffset = size - 3;
          int normalsOffset = coordinatesOffset - 3;
          int colorsOffset = (normals ? normalsOffset : coordinatesOffset) - colorSize;
          if (normals) {
            normalsArray = new float [vertexCount * 3];
          }
          if (colorSize > 0) {
            colorsArray = new float [vertexCount * colorSize];
          }
          if (textureCoordinates) {
            textureCoordinatesArray = new float [vertexCount * textureCoordinatesSize];
          }
          for (int i = 0; i < vertexCount; i++) {
            System.arraycopy(data, i * size + coordinatesOffset, coordinates, i * 3, 3);
            if (normals) {
              System.arraycopy(data, i * size + normalsOffset, normalsArray, i * 3, 3);
            }
            if (colorSize > 0) {
              System.arraycopy(data, i * size + colorsOffset, colorsArray, i * colorSize, colorSize);
            }
            if (textureCoordinates) {
              System.arraycopy(data, i * size, textureCoordinatesArray, i * textureCoordinatesSize, textureCoordinatesSize);
            }
          }
        } else {
          coordinates = geometry.getCoordRefFloat();
          if (coordinates == null) {
            double [] doubleCoordinates = geometry.getCoordRefDouble();
            if (doubleCoordinates != null) {
              coordinates = new float [doubleCoordinates.length];
              for (int i = 0; i < coordinates.length; i++) {
                coordinates [i] = (float)doubleCoordinates [i];
              }
            } else {
              Point3f [] points = geometry.getCoordRef3f();
              if (points == null) {
                return null;
              }
              coordinates = new float [points.length * 3];
              for (int i = 0; i < points.length; i++) {
                coordinates [i * 3] = points [i].x;
                coordinates [i * 3 + 1] = points [i].y;
                coordinates [i * 3 + 2] = points [i].z;
              }
            }
          }
          if (normals) {
            normalsArray = geometry.getNormalRefFloat();
            if (normalsArray == null) {
              Vector3f [] vectors = geometry.getNormalRef3f();
              if (vectors != null) {
                normalsArray = new float [vectors.length * 3];
                for (int i = 0; i < vectors.length; i++) {
                  normalsArray [i * 3] = vectors [i].x;
                  normalsArray [i * 3 + 1] = vectors [i].y;
                  normalsArray [i * 3 + 2] = vectors [i].z;
                }
              } else {
                normals = false;
              }
            }
          }
          if (textureCoordinates) {
            textureCoordinatesArray = geometry.getTexCoordRefFloat(0);
            if (textureCoordinatesArray == null) {
              TexCoord2f [] coords = geometry.getTexCoordRef2f(0);
              if (coords != null) {
                textureCoordinatesSize = 2;
                textureCoordinatesArray = new float [coords.length * 2];
                for (int i = 0; i < coords.length; i++) {
                  textureCoordinatesArray [i * 2] = coords [i].x;
                  textureCoordinatesArray [i * 2 + 1] = coords [i].y;
                }
              } else {
                textureCoordinates = false;
              }
            }
          }
          if (colorSize > 0) {
            colorsArray = geometry.getColorRefFloat();
            if (colorsArray == null) {
              colorSize = 0;
            }
          }
        }
      } else {
        coordinates = new float [vertexCount * 3];
        geometry.getCoordinates(0, coordinates);
        if (normals) {
          normalsArray = new float [vertexCount * 3];
          geometry.getNormals(0, normalsArray);
        }
        if (textureCoordinates) {
          textureCoordinatesArray = new float [vertexCount * textureCoordinatesSize];
          geometry.getTextureCoordinates(0, 0, textureCoordinatesArray);
        }
        if (colorSize > 0) {
          colorsArray = new float [vertexCount * colorSize];
          geometry.getColors(0, colorsArray);
        }
      }

      // Indices of the vertices used by each corner
      int [] coordinatesIndices = null;
      int [] normalsIndices = null;
      int [] textureCoordinatesIndices = null;
      int [] colorsIndices = null;
      int elementCount;
      int firstElement = 0;
      if (geometry instanceof IndexedGeometryArray) {
        IndexedGeometryArray indexedGeometry = (IndexedGeometryArray)geometry;
        int indexCount = indexedGeometry.getIndexCount();
        if ((format & GeometryArray.BY_REFERENCE_INDICES) != 0) {
          coordinatesIndices = indexedGeometry.getCoordIndicesRef();
        } else {
          coordinatesIndices = new int [indexCount];
          indexedGeometry.getCoordinateIndices(0, coordinatesIndices);
        }
        if ((format & GeometryArray.USE_COORD_INDEX_ONLY) != 0) {
          normalsIndices = coordinatesIndices;
          textureCoordinatesIndices = coordinatesIndices;
          colorsIndices = coordinatesIndices;
        } else {
          if (normals) {
            normalsIndices = new int [indexCount];
            indexedGeometry.getNormalIndices(0, normalsIndices);
          }
          if (textureCoordinates) {
            textureCoordinatesIndices = new int [indexCount];
            indexedGeometry.getTextureCoordinateIndices(0, 0, textureCoordinatesIndices);
          }
          if (colorSize > 0) {
            colorsIndices = new int [indexCount];
            indexedGeometry.getColorIndices(0, colorsIndices);
          }
        }
        elementCount = indexedGeometry.getValidIndexCount();
        firstElement = indexedGeometry.getInitialIndexIndex();
      } else {
        elementCount = geometry.getValidVertexCount();
        firstElement = geometry.getInitialVertexIndex();
      }

      // Elements used by the corners of the triangles or the ends of the lines
      int [] corners;
      if (geometry instanceof TriangleArray || geometry instanceof IndexedTriangleArray
          || geometry instanceof LineArray || geometry instanceof IndexedLineArray) {
        int unit = this.mode == GL.GL_TRIANGLES ? 3 : 2;
        corners = new int [elementCount / unit * unit];
        for (int i = 0; i < corners.length; i++) {
          corners [i] = firstElement + i;
        }
      } else if (geometry instanceof QuadArray || geometry instanceof IndexedQuadArray) {
        corners = new int [elementCount / 4 * 6];
        for (int i = 0, j = 0; i + 3 < elementCount; i += 4) {
          corners [j++] = firstElement + i;
          corners [j++] = firstElement + i + 1;
          corners [j++] = firstElement + i + 2;
          corners [j++] = firstElement + i;
          corners [j++] = firstElement + i + 2;
          corners [j++] = firstElement + i + 3;
        }
      } else {
        int [] stripCounts;
        if (geometry instanceof IndexedGeometryStripArray) {
          stripCounts = new int [((IndexedGeometryStripArray)geometry).getNumStrips()];
          ((IndexedGeometryStripArray)geometry).getStripIndexCounts(stripCounts);
        } else {
          stripCounts = new int [((GeometryStripArray)geometry).getNumStrips()];
          ((GeometryStripArray)geometry).getStripVertexCounts(stripCounts);
        }
        boolean fan = geometry instanceof TriangleFanArray || geometry instanceof IndexedTriangleFanArray;
        boolean lines = this.mode == GL.GL_LINES;
        int cornerCount = 0;
        for (int stripCount : stripCounts) {
          cornerCount += lines
              ? Math.max(0, stripCount - 1) * 2
              : Math.max(0, stripCount - 2) * 3;
        }
        corners = new int [cornerCount];
        int start = firstElement;
        int j = 0;
        for (int stripCount : stripCounts) {
          if (lines) {
            for (int i = 0; i + 1 < stripCount; i++) {
              corners [j++] = start + i;
              corners [j++] = start + i + 1;
            }
          } else if (fan) {
            for (int i = 1; i + 1 < stripCount; i++) {
              corners [j++] = start;
              corners [j++] = start + i;
              corners [j++] = start + i + 1;
            }
          } else {
            for (int i = 0; i + 2 < stripCount; i++) {
              if (i % 2 == 0) {
                corners [j++] = start + i;
                corners [j++] = start + i + 1;
              } else {
                corners [j++] = start + i + 1;
                corners [j++] = start + i;
              }
              corners [j++] = start + i + 2;
            }
          }
          start += stripCount;
        }
      }

      int stride = 3 + (normals ? 3 : 0) + (textureCoordinates ? 2 : 0) + (colorSize > 0 ? 4 : 0);
      FloatBuffer vertices = ByteBuffer.allocateDirect(Math.max(1, corners.length) * stride * 4).
          order(ByteOrder.nativeOrder()).asFloatBuffer();
      float [] vertex = new float [stride];
      float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
      float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
      for (int corner : corners) {
        int index = coordinatesIndices != null ? coordinatesIndices [corner] : corner;
        float x = coordinates [index * 3];
        float y = coordinates [index * 3 + 1];
        float z = coordinates [index * 3 + 2];
        vertex [0] = x;
        vertex [1] = y;
        vertex [2] = z;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
        int offset = 3;
        if (normals) {
          index = normalsIndices != null ? normalsIndices [corner] : corner;
          vertex [offset++] = normalsArray [index * 3];
          vertex [offset++] = normalsArray [index * 3 + 1];
          vertex [offset++] = normalsArray [index * 3 + 2];
        }
        if (textureCoordinates) {
          index = textureCoordinatesIndices != null ? textureCoordinatesIndices [corner] : corner;
          vertex [offset++] = textureCoordinatesArray [index * textureCoordinatesSize];
          vertex [offset++] = textureCoordinatesArray [index * textureCoordinatesSize + 1];
        }
        if (colorSize > 0) {
          index = colorsIndices != null ? colorsIndices [corner] : corner;
          vertex [offset++] = colorsArray [index * colorSize];
          vertex [offset++] = colorsArray [index * colorSize + 1];
          vertex [offset++] = colorsArray [index * colorSize + 2];
          vertex [offset++] = colorSize == 4 ? colorsArray [index * colorSize + 3] : 1;
        }
        vertices.put(vertex);
      }
      ((java.nio.Buffer)vertices).flip();

      this.vertexCount = corners.length;
      this.stride = stride;
      this.normals = normals;
      this.textureCoordinates = textureCoordinates;
      this.colors = colorSize > 0;
      if (corners.length > 0) {
        this.center = new float [] {(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        this.radius = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) / 2;
      }
      return vertices;
    }
  }
}
