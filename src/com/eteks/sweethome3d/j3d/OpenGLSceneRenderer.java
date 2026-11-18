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
import java.awt.image.DataBufferInt;
import java.awt.image.RenderedImage;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingBox;
import javax.media.j3d.BoundingLeaf;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.Bounds;
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
import javax.media.j3d.PointLight;
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
 * <p>Three kinds of threads may use an instance of this class:<ul>
 * <li>the <i>scene thread</i>, i.e. the only thread which modifies the nodes
 * of the scene, calls {@link #setScene(Node, Group) setScene}, the methods which
 * mark objects as modified, {@link #prepareFrame(Transform3D, double, double, double)
 * prepareFrame} which returns an immutable description of the scene, and the picking methods</li>
 * <li>the <i>rendering thread</i>, with an OpenGL context current, calls
 * {@link #init(GL2) init}, {@link #renderFrame(GL2, Frame, int, int) renderFrame},
 * {@link #renderImage(GL2, Frame, int, int) renderImage} and {@link #dispose(GL2) dispose}</li>
 * <li>worker threads, one per available processor, prepare the vertices of the
 * geometries and the pixels of the textures before the rendering thread copies
 * them to the graphics card.</li></ul>
 * The scene thread and the rendering thread may be the same one.
 * @author Emmanuel Puybaret
 */
public class OpenGLSceneRenderer {
  private static final int MAX_LIGHTS = 8;
  private static final int MAX_POINT_LIGHTS = 8;
  // Maximum count of vertices copied to the graphics card during a frame,
  // to keep the frames short while the models of a home are being loaded
  private static final int VERTEX_UPLOAD_BUDGET =
      Integer.getInteger("com.eteks.sweethome3d.opengl.vertexUploadBudget", 1500000);
  // Maximum time in nanoseconds spent to copy geometries and textures to the graphics card during
  // a frame under Linux, where the rendering thread takes the lock of AWT while it draws a frame
  // and blocks the event dispatch thread as soon as it draws too
  private static final long UPLOAD_TIME_BUDGET = com.eteks.sweethome3d.tools.OperatingSystem.isLinux()
      ? Long.getLong("com.eteks.sweethome3d.opengl.uploadTimeBudget", 40) * 1000000L
      : Long.MAX_VALUE / 2;
  // Maximum count of pixels of textures copied to the graphics card during a frame
  private static final int PIXEL_UPLOAD_BUDGET = 8 * 1024 * 1024;
  // Maximum size of the vertices prepared in advance by the worker threads
  private static final long MAX_PREPARED_BYTES = 192L * 1024 * 1024;
  private static final int SHADOW_MAP_MAX_SIZE = 4096;
  // Lights when shadows are displayed: a main light which casts shadows is added from
  // the side of the first directional light but higher, and the lights of the scene fill the shadows
  private static final float SHADOW_KEY_LIGHT_FACTOR = 0.8f;
  private static final float SHADOW_FILL_LIGHT_FACTOR = 0.45f;
  private static final double SHADOW_KEY_LIGHT_ELEVATION = Math.toRadians(58);

  private static final int MESH_NEW = 0;
  private static final int MESH_PREPARING = 1;
  private static final int MESH_READY = 2;
  private static final int MESH_UPLOADED = 3;
  private static final int MESH_FAILED = 4;

  private static ExecutorService preparationExecutor;

  // ---- Fields used by the scene thread
  private Node   sceneRoot;
  private Group  objectsGroup;
  private final Map<Node, ObjectEntry>  objects = new IdentityHashMap<Node, ObjectEntry>();
  private final Map<Geometry, Mesh>     meshes = new IdentityHashMap<Geometry, Mesh>();
  private final Map<State, State>       states = new HashMap<State, State>();
  private Map<Group, Integer>  lightScopes = new IdentityHashMap<Group, Integer>();
  private boolean     allObjectsDirty = true;
  private boolean     listsDirty = true;
  private long        preparedFrameCount;
  private long        generation;
  private List<Item>  opaqueItems = new ArrayList<Item>();
  private List<Item>  transparentItems = new ArrayList<Item>();
  private PointLightState [] pointLights = {};
  private int         meshOrder;

  // ---- Fields shared by threads
  private final ConcurrentLinkedQueue<ReleasedMesh> releasedMeshes = new ConcurrentLinkedQueue<ReleasedMesh>();
  private final AtomicLong    preparedBytes = new AtomicLong();
  private final AtomicInteger preparationCount = new AtomicInteger();
  private volatile ViewState  lastView;
  private volatile Runnable   frameRequester;
  private volatile boolean    synchronousLoading;
  private volatile int        antialiasingSamples;
  private volatile boolean    shadows;
  private volatile int        shadowMapMaxSize = SHADOW_MAP_MAX_SIZE;
  private volatile boolean    ambientOcclusion;
  private volatile boolean    disposed;
  private volatile int        maxTextureSize = 2048;

  // ---- Fields used by the rendering thread
  private final Map<Texture, GLTexture> textures = new IdentityHashMap<Texture, GLTexture>();
  private final Map<Mesh, Boolean>      uploadedMeshes = new IdentityHashMap<Mesh, Boolean>();
  private long     renderedGeneration = -1;
  private int      framesSinceTextureCheck;
  private boolean  pendingUploads;
  private boolean  uploadsDone;
  private boolean  shadowUpdateDeferred;
  private int      vertexUploadBudget;
  private int      pixelUploadBudget;
  // Time left in nanoseconds to copy geometries and textures during the frame
  private long     uploadTimeBudget;
  private long     lastDrawingDuration;
  // true while an image is created: all that is ready is copied at each frame
  private boolean  imageRendering;
  private boolean  imageLoading;
  // Statistics of the last frame
  private volatile int   drawnItemCount;
  private volatile long  drawnVertexCount;
  private volatile int   culledItemCount;
  private volatile int   missingItemCount;
  // Statistics of the frame being drawn
  private int      frameItemCount;
  private long     frameVertexCount;
  private int      frameMissingItemCount;
  // Time when the shadow map was drawn and how long it took
  private long     shadowMapTime;
  private long     shadowMapDuration;
  // Current OpenGL state
  private State    currentState;
  private Mesh     currentMesh;
  private int      currentTexture;
  private LightSet currentLightSet;
  private int []   currentPointLights;
  private boolean  depthOnlyPass;
  private final float [] modelView = new float [16];
  // Capabilities of the OpenGL context
  private boolean  generateMipmapAvailable;
  private boolean  framebufferAvailable;
  private boolean  shadersAvailable;
  private float    maxAnisotropy;
  private int      maxSamples;
  private int      defaultFramebuffer;
  // Lights of the frame being drawn
  private final Map<Integer, LightSet> lightSets = new HashMap<Integer, LightSet>();
  private LightSet  backgroundLightSet;
  private float []  pointLightEyePositions;
  private boolean   frameShadows;
  private boolean   frameShadowCasters;
  private float []  shadowMatrix;
  // Programs and their uniform variables
  private int  sceneProgram;
  private final Map<String, Integer> sceneUniforms = new HashMap<String, Integer>();
  private int  occlusionProgram;
  private int  blurProgram;
  private int  compositionProgram;
  // Frame buffers
  private Target  sceneTarget;
  private Target  resolvedTarget;
  private Target  occlusionTarget;
  private Target  blurTarget;
  private Target  imageTarget;
  private int     shadowFramebuffer;
  private int     shadowTexture;
  private int     shadowMapSize;
  private boolean shadowMapValid;
  private float [] shadowMapLightDirection;
  private double [] shadowWorldMatrix;

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
   * Sets the object called from any thread when a new frame should be drawn
   * because some geometries or textures are ready to be displayed.
   */
  public void setFrameRequester(Runnable frameRequester) {
    this.frameRequester = frameRequester;
  }

  /**
   * Sets whether geometries and textures should be prepared in the rendering thread
   * to draw complete frames, or by worker threads to draw the first frames sooner.
   */
  public void setSynchronousLoading(boolean synchronousLoading) {
    this.synchronousLoading = synchronousLoading;
  }

  /**
   * Sets the count of samples per pixel used to smooth edges, 0 or 1 for none.
   */
  public void setAntialiasingSamples(int antialiasingSamples) {
    this.antialiasingSamples = antialiasingSamples;
  }

  public int getAntialiasingSamples() {
    return this.antialiasingSamples;
  }

  /**
   * Sets whether the objects cast shadows from the first directional light of the scene.
   */
  public void setShadows(boolean shadows) {
    this.shadows = shadows;
  }

  public boolean isShadows() {
    return this.shadows;
  }

  /**
   * Sets the maximum width in pixels of the image of the depths viewed from
   * the light where shadows are searched: the larger, the sharper the shadows.
   */
  public void setShadowMapMaxSize(int shadowMapMaxSize) {
    this.shadowMapMaxSize = shadowMapMaxSize;
  }

  /**
   * Sets whether corners and the surfaces close to an other one are darkened.
   */
  public void setAmbientOcclusion(boolean ambientOcclusion) {
    this.ambientOcclusion = ambientOcclusion;
  }

  public boolean isAmbientOcclusion() {
    return this.ambientOcclusion;
  }

  /**
   * Returns <code>true</code> if lights are computed for each pixel, and if
   * shadows and ambient occlusion may be displayed.
   */
  public boolean isAdvancedRenderingAvailable() {
    return this.shadersAvailable && this.framebufferAvailable;
  }

  /**
   * Returns <code>true</code> if some geometries weren't copied to the graphics
   * card during the last frame, which should be followed by an other one.
   */
  public boolean isFramePending() {
    return this.pendingUploads;
  }

  /**
   * Returns <code>true</code> if some geometries or textures of the last frame
   * weren't displayed because they were still being prepared.
   */
  public boolean isLoading() {
    return this.pendingUploads || this.preparationCount.get() > 0;
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
   * Returns the count of shapes which weren't drawn in the last frame because
   * their geometry or their texture wasn't ready, or couldn't be read.
   */
  public int getMissingItemCount() {
    return this.missingItemCount;
  }

  /**
   * Returns the threads which prepare geometries and textures.
   */
  private static synchronized ExecutorService getPreparationExecutor() {
    if (preparationExecutor == null) {
      int threadCount = Math.max(1, Math.min(16, Runtime.getRuntime().availableProcessors() - 1));
      preparationExecutor = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
          private final AtomicInteger count = new AtomicInteger();

          public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "OpenGL scene preparation " + this.count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
          }
        });
    }
    return preparationExecutor;
  }

  // ------------------------------------------------------------------
  // Scene thread
  // ------------------------------------------------------------------

  /**
   * Returns the description of the scene at this moment viewed from the given camera.
   * This method must be called in the thread which modifies the nodes of the scene.
   * @param cameraTransform  the transformation of the camera in the scene
   * @param fieldOfView      the horizontal field of view in radians
   */
  public Frame prepareFrame(Transform3D cameraTransform, double fieldOfView,
                            double frontClipDistance, double backClipDistance) {
    Frame frame = new Frame();
    this.preparedFrameCount++;
    frame.fieldOfView = fieldOfView;
    frame.frontClipDistance = frontClipDistance;
    frame.backClipDistance = backClipDistance;
    cameraTransform.get(frame.cameraMatrix);
    Transform3D view = new Transform3D(cameraTransform);
    view.invert();
    view.get(frame.viewMatrix);

    List<Item> environmentItems = new ArrayList<Item>();
    List<Item> backgroundItems = new ArrayList<Item>();
    List<LightState> lights = new ArrayList<LightState>();
    List<LightState> backgroundLights = new ArrayList<LightState>();
    if (this.sceneRoot != null) {
      Transform3D identity = new Transform3D();
      if (this.sceneRoot instanceof Group) {
        Group root = (Group)this.sceneRoot;
        // Lights first, to know the groups they are limited to
        for (int i = 0, n = root.numChildren(); i < n; i++) {
          Node child = root.getChild(i);
          if (child != this.objectsGroup && !(child instanceof Background)) {
            extractLights(child, identity, lights);
          }
        }
        Map<Group, Integer> lightScopes = new IdentityHashMap<Group, Integer>();
        int scopedLightCount = 0;
        for (LightState light : lights) {
          if (light.scopes != null && scopedLightCount < 31) {
            light.scopeBit = 1 << scopedLightCount++;
            for (Group group : light.scopes) {
              Integer mask = lightScopes.get(group);
              lightScopes.put(group, mask != null ? mask | light.scopeBit : light.scopeBit);
            }
          }
          light.scopes = null;
        }
        if (!lightScopes.equals(this.lightScopes)) {
          this.lightScopes = lightScopes;
          this.allObjectsDirty = true;
        }
        // Environment: background and ground are described again at each frame
        for (int i = 0, n = root.numChildren(); i < n; i++) {
          Node child = root.getChild(i);
          if (child instanceof Background) {
            Background background = (Background)child;
            Color3f color = new Color3f();
            background.getColor(color);
            frame.backgroundColor = new float [] {color.x, color.y, color.z};
            if (background.getGeometry() != null) {
              extractLights(background.getGeometry(), identity, backgroundLights);
              extract(background.getGeometry(), identity, false, null, 0, backgroundItems, null);
            }
          } else if (child != this.objectsGroup) {
            extract(child, identity, false, null, 0, environmentItems, null);
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
          entry.frame = this.preparedFrameCount;
          if (entry.dirty || this.allObjectsDirty) {
            entry.items = new ArrayList<Item>();
            entry.pointLights = new ArrayList<PointLightState>(0);
            extract(child, identity, true, child, 0, entry.items, entry.pointLights);
            entry.dirty = false;
            changed = true;
          }
        }
        if (this.objects.size() != count) {
          // Forget removed objects
          for (Iterator<ObjectEntry> it = this.objects.values().iterator(); it.hasNext(); ) {
            if (it.next().frame != this.preparedFrameCount) {
              it.remove();
              changed = true;
            }
          }
        }
        this.allObjectsDirty = false;
        if (changed || this.listsDirty) {
          List<Item> opaqueItems = new ArrayList<Item>();
          List<Item> transparentItems = new ArrayList<Item>();
          List<PointLightState> pointLights = new ArrayList<PointLightState>();
          for (ObjectEntry entry : this.objects.values()) {
            for (Item item : entry.items) {
              if (item.state.isBlended()) {
                transparentItems.add(item);
              } else {
                opaqueItems.add(item);
              }
            }
            pointLights.addAll(entry.pointLights);
          }
          // Sort opaque shapes to change OpenGL state as rarely as possible
          Collections.sort(opaqueItems, new Comparator<Item>() {
              public int compare(Item item1, Item item2) {
                int order = item1.state.order - item2.state.order;
                return order != 0
                    ? order
                    : item1.mesh.order - item2.mesh.order;
              }
            });
          this.opaqueItems = opaqueItems;
          this.transparentItems = transparentItems;
          this.pointLights = pointLights.toArray(new PointLightState [pointLights.size()]);
          this.listsDirty = false;
          this.generation++;
          releaseUnusedMeshes(environmentItems, backgroundItems);
        }
      }
    }
    frame.generation = this.generation;
    frame.opaqueItems = this.opaqueItems;
    frame.transparentItems = this.transparentItems;
    frame.pointLights = this.pointLights;
    frame.environmentItems = environmentItems;
    frame.backgroundItems = backgroundItems;
    frame.lights = lights.toArray(new LightState [lights.size()]);
    frame.backgroundLights = backgroundLights.toArray(new LightState [backgroundLights.size()]);
    return frame;
  }

  /**
   * Forgets the geometries that no shape uses anymore.
   */
  private void releaseUnusedMeshes(List<Item> environmentItems, List<Item> backgroundItems) {
    IdentityHashMap<Mesh, Boolean> usedMeshes = new IdentityHashMap<Mesh, Boolean>();
    for (ObjectEntry entry : this.objects.values()) {
      for (Item item : entry.items) {
        usedMeshes.put(item.mesh, Boolean.TRUE);
      }
    }
    for (Item item : environmentItems) {
      usedMeshes.put(item.mesh, Boolean.TRUE);
    }
    for (Item item : backgroundItems) {
      usedMeshes.put(item.mesh, Boolean.TRUE);
    }
    for (Iterator<Mesh> it = this.meshes.values().iterator(); it.hasNext(); ) {
      Mesh mesh = it.next();
      if (!usedMeshes.containsKey(mesh)) {
        it.remove();
        this.releasedMeshes.add(new ReleasedMesh(mesh, this.generation));
      }
    }
  }

  /**
   * Adds to <code>lights</code> the ambient and directional lights among the descendants of <code>node</code>.
   */
  private void extractLights(Node node, Transform3D parentTransform, List<LightState> lights) {
    if (node instanceof Group) {
      Group group = (Group)node;
      Transform3D transform = parentTransform;
      if (node instanceof TransformGroup) {
        Transform3D groupTransform = new Transform3D();
        ((TransformGroup)node).getTransform(groupTransform);
        transform = new Transform3D(parentTransform);
        transform.mul(groupTransform);
      }
      for (int i = 0, n = group.numChildren(); i < n; i++) {
        extractLights(group.getChild(i), transform, lights);
      }
    } else if (node instanceof Light) {
      Light light = (Light)node;
      if (light.getEnable()) {
        Color3f color = new Color3f();
        light.getColor(color);
        LightState lightState = null;
        if (light instanceof AmbientLight) {
          lightState = new LightState(color, null);
        } else if (light instanceof DirectionalLight) {
          Vector3f direction = new Vector3f();
          ((DirectionalLight)light).getDirection(direction);
          parentTransform.transform(direction);
          if (direction.lengthSquared() > 0) {
            direction.normalize();
          }
          lightState = new LightState(color, direction);
        }
        if (lightState != null) {
          if (light.numScopes() > 0) {
            lightState.scopes = new ArrayList<Group>(light.numScopes());
            for (Enumeration<?> it = light.getAllScopes(); it.hasMoreElements(); ) {
              lightState.scopes.add((Group)it.nextElement());
            }
          }
          lights.add(lightState);
        }
      }
    }
  }

  /**
   * Adds to <code>items</code> the visible shapes among the descendants of <code>node</code>.
   */
  private void extract(Node node, Transform3D parentTransform, boolean pickable, Node owner, int lightScopeMask,
                       List<Item> items, List<PointLightState> pointLights) {
    if (node instanceof Group) {
      Group group = (Group)node;
      Transform3D transform = parentTransform;
      if (node instanceof TransformGroup) {
        Transform3D groupTransform = new Transform3D();
        ((TransformGroup)node).getTransform(groupTransform);
        transform = new Transform3D(parentTransform);
        transform.mul(groupTransform);
      }
      if (!this.lightScopes.isEmpty()) {
        Integer mask = this.lightScopes.get(group);
        if (mask != null) {
          lightScopeMask |= mask;
        }
      }
      boolean childrenPickable = pickable && group.getPickable();
      if (node instanceof Switch) {
        Switch switchNode = (Switch)node;
        int whichChild = switchNode.getWhichChild();
        if (whichChild == Switch.CHILD_ALL) {
          for (int i = 0, n = group.numChildren(); i < n; i++) {
            extract(group.getChild(i), transform, childrenPickable, owner, lightScopeMask, items, pointLights);
          }
        } else if (whichChild == Switch.CHILD_MASK) {
          BitSet mask = switchNode.getChildMask();
          for (int i = 0, n = group.numChildren(); i < n; i++) {
            if (mask.get(i)) {
              extract(group.getChild(i), transform, childrenPickable, owner, lightScopeMask, items, pointLights);
            }
          }
        } else if (whichChild >= 0 && whichChild < group.numChildren()) {
          extract(group.getChild(whichChild), transform, childrenPickable, owner, lightScopeMask, items, pointLights);
        }
      } else {
        for (int i = 0, n = group.numChildren(); i < n; i++) {
          extract(group.getChild(i), transform, childrenPickable, owner, lightScopeMask, items, pointLights);
        }
      }
    } else if (node instanceof Link) {
      extract(((Link)node).getSharedGroup(), parentTransform, pickable, owner, lightScopeMask, items, pointLights);
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
              mesh = new Mesh((GeometryArray)geometry, this.meshOrder++);
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
              item.pickable = pickable && shape.getPickable();
              item.owner = owner;
              item.lightScopeMask = lightScopeMask;
              items.add(item);
            }
          }
        }
      }
    } else if (node instanceof PointLight && pointLights != null) {
      PointLight light = (PointLight)node;
      if (light.getEnable()) {
        Color3f color = new Color3f();
        light.getColor(color);
        if (color.x > 0 || color.y > 0 || color.z > 0) {
          Point3f position = new Point3f();
          light.getPosition(position);
          parentTransform.transform(position);
          Point3f attenuation = new Point3f();
          light.getAttenuation(attenuation);
          PointLightState lightState = new PointLightState();
          lightState.position = new float [] {position.x, position.y, position.z};
          lightState.color = new float [] {color.x, color.y, color.z};
          lightState.attenuation = new float [] {attenuation.x, attenuation.y, attenuation.z};
          BoundingLeaf boundingLeaf = light.getInfluencingBoundingLeaf();
          Bounds bounds = boundingLeaf != null
              ? boundingLeaf.getRegion()
              : light.getInfluencingBounds();
          if (bounds instanceof BoundingBox) {
            Point3d lower = new Point3d();
            Point3d upper = new Point3d();
            ((BoundingBox)bounds).getLower(lower);
            ((BoundingBox)bounds).getUpper(upper);
            lightState.lower = new double [] {lower.x, lower.y, lower.z};
            lightState.upper = new double [] {upper.x, upper.y, upper.z};
          } else if (bounds instanceof BoundingSphere) {
            Point3d center = new Point3d();
            ((BoundingSphere)bounds).getCenter(center);
            double radius = ((BoundingSphere)bounds).getRadius();
            if (radius < 1E6 && !Double.isInfinite(radius)) {
              lightState.lower = new double [] {center.x - radius, center.y - radius, center.z - radius};
              lightState.upper = new double [] {center.x + radius, center.y + radius, center.z + radius};
            }
          }
          pointLights.add(lightState);
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
   * Returns the origin and the direction in the scene of the ray which goes
   * through the pixel (x, y) of the last frame, y going down.
   */
  public double [] getPixelRay(int x, int y) {
    ViewState lastView = this.lastView;
    if (lastView == null || lastView.width <= 0 || lastView.height <= 0) {
      return null;
    }
    Matrix4d projection = new Matrix4d(lastView.projectionMatrix);
    Matrix4d view = new Matrix4d(lastView.viewMatrix);
    Matrix4d inverse = new Matrix4d();
    inverse.mul(projection, view);
    try {
      inverse.invert();
    } catch (RuntimeException ex) {
      return null;
    }
    double deviceX = 2. * (x + 0.5) / lastView.width - 1;
    double deviceY = 1 - 2. * (y + 0.5) / lastView.height;
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
            && item.mesh.mode == GL.GL_TRIANGLES) {
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
    float [] positions = null;
    if (!mesh.boundsKnown) {
      positions = mesh.getPositions();
      if (positions == null) {
        return Double.POSITIVE_INFINITY;
      }
    }
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

    if (positions == null) {
      positions = mesh.getPositions();
      if (positions == null) {
        return Double.POSITIVE_INFINITY;
      }
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

  // ------------------------------------------------------------------
  // Rendering thread
  // ------------------------------------------------------------------

  /**
   * Initializes the OpenGL context.
   */
  public void init(GL2 gl) {
    // Buffers and textures of a previous context are lost
    for (Mesh mesh : this.uploadedMeshes.keySet()) {
      synchronized (mesh) {
        mesh.buffer = 0;
        if (mesh.loadState == MESH_UPLOADED) {
          mesh.loadState = MESH_NEW;
        }
      }
    }
    this.uploadedMeshes.clear();
    for (GLTexture texture : this.textures.values()) {
      texture.deleted = true;
    }
    this.textures.clear();
    this.sceneTarget = null;
    this.resolvedTarget = null;
    this.occlusionTarget = null;
    this.imageTarget = null;
    this.shadowFramebuffer = 0;
    this.shadowTexture = 0;
    this.shadowMapValid = false;
    this.disposed = false;

    this.generateMipmapAvailable = gl.isFunctionAvailable("glGenerateMipmap");
    int [] value = new int [1];
    gl.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, value, 0);
    this.maxTextureSize = Math.max(256, value [0]);
    this.maxAnisotropy = 0;
    if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) {
      float [] floatValue = new float [1];
      gl.glGetFloatv(GL.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, floatValue, 0);
      this.maxAnisotropy = Math.min(16, floatValue [0]);
    }
    this.framebufferAvailable = gl.isFunctionAvailable("glGenFramebuffers")
        && gl.isFunctionAvailable("glBlitFramebuffer")
        && gl.isFunctionAvailable("glRenderbufferStorageMultisample")
        && !"false".equalsIgnoreCase(System.getProperty("com.eteks.sweethome3d.opengl.framebuffers"));
    this.maxSamples = 0;
    if (this.framebufferAvailable) {
      gl.glGetIntegerv(GL2.GL_MAX_SAMPLES, value, 0);
      this.maxSamples = value [0];
    }
    this.shadersAvailable = false;
    this.sceneProgram = 0;
    this.occlusionProgram = 0;
    this.compositionProgram = 0;
    if (gl.isFunctionAvailable("glCreateShader")
        && gl.isFunctionAvailable("glUseProgram")
        && !"false".equalsIgnoreCase(System.getProperty("com.eteks.sweethome3d.opengl.shaders"))) {
      try {
        this.sceneProgram = createProgram(gl, SCENE_VERTEX_SHADER, SCENE_FRAGMENT_SHADER);
        this.sceneUniforms.clear();
        gl.glUseProgram(this.sceneProgram);
        gl.glUniform1i(getUniform(gl, "texture0"), 0);
        gl.glUniform1i(getUniform(gl, "shadowMap"), 1);
        gl.glUseProgram(0);
        this.shadersAvailable = true;
        if (this.framebufferAvailable) {
          this.occlusionProgram = createProgram(gl, QUAD_VERTEX_SHADER, OCCLUSION_FRAGMENT_SHADER);
          this.compositionProgram = createProgram(gl, QUAD_VERTEX_SHADER, COMPOSITION_FRAGMENT_SHADER);
        }
      } catch (RuntimeException ex) {
        // Use the lights computed by OpenGL at each vertex
        System.err.println("OpenGL renderer shaders unavailable: " + ex.getMessage());
        gl.glUseProgram(0);
        this.occlusionProgram = 0;
        this.compositionProgram = 0;
      }
    }
  }

  /**
   * Frees the resources kept by the OpenGL context.
   */
  public void dispose(GL2 gl) {
    this.disposed = true;
    for (Mesh mesh : this.uploadedMeshes.keySet()) {
      synchronized (mesh) {
        if (mesh.buffer != 0) {
          gl.glDeleteBuffers(1, new int [] {mesh.buffer}, 0);
          mesh.buffer = 0;
        }
        if (mesh.loadState == MESH_UPLOADED) {
          mesh.loadState = MESH_NEW;
        }
      }
    }
    this.uploadedMeshes.clear();
    for (GLTexture texture : this.textures.values()) {
      if (texture.id != 0) {
        gl.glDeleteTextures(1, new int [] {texture.id}, 0);
      }
      texture.deleted = true;
    }
    this.textures.clear();
    this.sceneTarget = deleteTarget(gl, this.sceneTarget);
    this.resolvedTarget = deleteTarget(gl, this.resolvedTarget);
    this.occlusionTarget = deleteTarget(gl, this.occlusionTarget);
    this.imageTarget = deleteTarget(gl, this.imageTarget);
    deleteShadowMap(gl);
    for (int program : new int [] {this.sceneProgram, this.occlusionProgram, this.compositionProgram}) {
      if (program != 0) {
        gl.glDeleteProgram(program);
      }
    }
    this.sceneProgram = 0;
    this.occlusionProgram = 0;
    this.compositionProgram = 0;
    this.shadersAvailable = false;
  }

  /**
   * Draws at once the scene viewed from the given camera in a viewport of the given size.
   * This method may be used when the nodes of the scene are modified in the rendering thread.
   * @param cameraTransform  the transformation of the camera in the scene
   * @param fieldOfView      the horizontal field of view in radians
   */
  public void render(GL2 gl, int width, int height,
                     Transform3D cameraTransform, double fieldOfView,
                     double frontClipDistance, double backClipDistance) {
    Frame frame = prepareFrame(cameraTransform, fieldOfView, frontClipDistance, backClipDistance);
    boolean synchronousLoading = this.synchronousLoading;
    this.synchronousLoading = true;
    try {
      renderFrame(gl, frame, width, height);
    } finally {
      this.synchronousLoading = synchronousLoading;
    }
  }

  /**
   * Draws a frame in the default frame buffer of the OpenGL context.
   */
  public void renderFrame(GL2 gl, Frame frame, int width, int height) {
    this.defaultFramebuffer = gl.getDefaultDrawFramebuffer();
    renderFrame(gl, frame, width, height, this.defaultFramebuffer, true);
  }

  /**
   * Returns the image of a frame at the given size. All the geometries and
   * the textures of the scene are displayed in the returned image.
   * @throws IllegalStateException if the image can't be created with this OpenGL context
   */
  public BufferedImage renderImage(GL2 gl, Frame frame, int width, int height) {
    if (!this.framebufferAvailable) {
      throw new IllegalStateException("No frame buffer");
    } else if (width > this.maxTextureSize || height > this.maxTextureSize) {
      throw new IllegalStateException("Image too large");
    }
    this.defaultFramebuffer = gl.getDefaultDrawFramebuffer();
    // Geometries and textures are prepared by the worker threads,
    // and the frame is drawn again until they are all displayed
    this.imageRendering = true;
    try {
      this.imageTarget = getTarget(gl, this.imageTarget, width, height, 0, true, false);
      if (this.imageTarget == null) {
        throw new IllegalStateException("No frame buffer");
      }
      long start = System.currentTimeMillis();
      // Shadows are drawn once all the geometries are copied to the graphics card
      this.imageLoading = this.shadows;
      while (true) {
        renderFrame(gl, frame, width, height, this.imageTarget.framebuffer, false);
        if (isLoading()) {
          if (System.currentTimeMillis() - start > 120000) {
            break;
          } else if (!this.pendingUploads) {
            // Wait for the geometries and textures that worker threads are preparing
            try {
              Thread.sleep(3);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        } else if (this.imageLoading) {
          this.imageLoading = false;
        } else {
          break;
        }
      }

      gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.imageTarget.framebuffer);
      gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
      IntBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
      gl.glReadPixels(0, 0, width, height, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE, pixels);
      gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.defaultFramebuffer);
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      int [] data = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < height; y++) {
        ((java.nio.Buffer)pixels).position((height - 1 - y) * width);
        pixels.get(data, y * width, width);
      }
      // Don't keep a large image in the memory of the graphics card
      this.imageTarget = deleteTarget(gl, this.imageTarget);
      return image;
    } finally {
      this.imageRendering = false;
    }
  }

  private void renderFrame(GL2 gl, Frame frame, int width, int height, int targetFramebuffer, boolean publishView) {
    boolean unlimitedUploads = this.synchronousLoading || this.imageRendering;
    this.vertexUploadBudget = unlimitedUploads ? Integer.MAX_VALUE : VERTEX_UPLOAD_BUDGET;
    this.pixelUploadBudget = unlimitedUploads ? Integer.MAX_VALUE : PIXEL_UPLOAD_BUDGET;
    // A frame long to draw may spend as much time to copy geometries
    this.uploadTimeBudget = unlimitedUploads
        ? Long.MAX_VALUE / 2
        : Math.max(UPLOAD_TIME_BUDGET, this.lastDrawingDuration);
    long frameStart = System.nanoTime();
    long frameUploadTimeBudget = this.uploadTimeBudget;
    this.pendingUploads = false;
    this.uploadsDone = false;
    this.shadowUpdateDeferred = false;
    this.frameItemCount = 0;
    this.frameVertexCount = 0;
    this.frameMissingItemCount = 0;
    releaseResources(gl, frame);

    double aspect = height > 0 ? (double)width / height : 1;
    double halfWidth = Math.tan(frame.fieldOfView / 2) * frame.frontClipDistance;
    double halfHeight = halfWidth / aspect;
    double [] projectionMatrix = new double [16];
    setFrustum(projectionMatrix, -halfWidth, halfWidth, -halfHeight, halfHeight,
        frame.frontClipDistance, frame.backClipDistance);
    if (publishView) {
      this.lastView = new ViewState(frame.viewMatrix, projectionMatrix, width, height);
    }

    gl.glEnable(GLLightingFunc.GL_NORMALIZE);
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_COLOR_CONTROL, GL2.GL_SEPARATE_SPECULAR_COLOR);
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_LOCAL_VIEWER, 0);
    gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
    gl.glDepthFunc(GL.GL_LEQUAL);
    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
    gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);

    boolean advanced = isAdvancedRenderingAvailable();
    this.frameShadows = false;
    this.frameShadowCasters = advanced && this.shadows;
    if (advanced && this.shadows
        && !(this.imageRendering && this.imageLoading)) {
      this.frameShadows = updateShadowMap(gl, frame);
    }
    boolean occlusion = advanced && this.ambientOcclusion
        && this.occlusionProgram != 0 && this.compositionProgram != 0;
    int samples = this.framebufferAvailable
        ? Math.min(this.antialiasingSamples, this.maxSamples)
        : 0;
    if (samples < 2) {
      samples = 0;
    }
    int sceneFramebuffer = targetFramebuffer;
    if (samples > 0 || occlusion) {
      this.sceneTarget = getTarget(gl, this.sceneTarget, width, height, samples, false, true);
      if (this.sceneTarget != null) {
        sceneFramebuffer = this.sceneTarget.framebuffer;
      } else {
        occlusion = false;
      }
    }
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, sceneFramebuffer);
    drawScene(gl, frame, width, height, projectionMatrix);

    if (sceneFramebuffer != targetFramebuffer) {
      gl.glDisable(GL.GL_SCISSOR_TEST);
      if (occlusion) {
        this.resolvedTarget = getTarget(gl, this.resolvedTarget, width, height, 0, true, true);
        this.occlusionTarget = getTarget(gl, this.occlusionTarget, width, height, 0, true, false);
      }
      if (occlusion && this.resolvedTarget != null && this.occlusionTarget != null) {
        gl.glBindFramebuffer(GL2.GL_READ_FRAMEBUFFER, sceneFramebuffer);
        gl.glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, this.resolvedTarget.framebuffer);
        gl.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        drawOcclusion(gl, frame, width, height, targetFramebuffer);
      } else {
        gl.glBindFramebuffer(GL2.GL_READ_FRAMEBUFFER, sceneFramebuffer);
        gl.glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
        gl.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
      }
      gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, targetFramebuffer);
    }
    if (this.shadowUpdateDeferred) {
      this.pendingUploads = true;
    }
    if (this.uploadsDone && this.shadows && advanced && !this.imageRendering) {
      // The shadows of the geometries which were just copied are still missing
      this.shadowMapValid = false;
      this.pendingUploads = true;
    }
    this.renderedGeneration = frame.generation;
    // Time spent to draw this frame, without the copy of its geometries and textures
    this.lastDrawingDuration = Math.max(0, System.nanoTime() - frameStart
        - (frameUploadTimeBudget - this.uploadTimeBudget));
    this.drawnItemCount = this.frameItemCount;
    this.drawnVertexCount = this.frameVertexCount;
    this.missingItemCount = this.frameMissingItemCount;
  }

  /**
   * Draws the shapes of a frame in the current frame buffer.
   */
  private void drawScene(GL2 gl, Frame frame, int width, int height, double [] projectionMatrix) {
    double aspect = height > 0 ? (double)width / height : 1;
    gl.glViewport(0, 0, width, height);
    gl.glDepthMask(true);
    gl.glColorMask(true, true, true, true);
    gl.glClearColor(frame.backgroundColor [0], frame.backgroundColor [1], frame.backgroundColor [2], 1);
    gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
    this.depthOnlyPass = false;
    resetState(gl);

    this.lightSets.clear();
    this.backgroundLightSet = null;
    if (this.shadersAvailable) {
      gl.glUseProgram(this.sceneProgram);
      // Location of the lights from the camera
      PointLightState [] pointLights = frame.pointLights;
      if (this.pointLightEyePositions == null || this.pointLightEyePositions.length < pointLights.length * 3) {
        this.pointLightEyePositions = new float [pointLights.length * 3];
      }
      double [] view = frame.viewMatrix;
      for (int i = 0; i < pointLights.length; i++) {
        float [] position = pointLights [i].position;
        for (int row = 0; row < 3; row++) {
          this.pointLightEyePositions [i * 3 + row] = (float)(view [row * 4] * position [0]
              + view [row * 4 + 1] * position [1] + view [row * 4 + 2] * position [2] + view [row * 4 + 3]);
        }
      }
      gl.glUniform1i(getUniform(gl, "pointLightCount"), 0);
      this.currentPointLights = NO_POINT_LIGHT;
      if (this.frameShadows) {
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, this.shadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        // Matrix from the coordinates of the camera to the ones of the shadow map
        double [] matrix = multiply(this.shadowWorldMatrix, frame.cameraMatrix);
        float [] columns = new float [16];
        for (int row = 0; row < 4; row++) {
          for (int column = 0; column < 4; column++) {
            double coefficient = matrix [row * 4 + column];
            if (row < 3) {
              // From [-1, 1] to [0, 1]
              coefficient = coefficient * 0.5 + matrix [12 + column] * 0.5;
            }
            columns [column * 4 + row] = (float)coefficient;
          }
        }
        gl.glUniformMatrix4fv(getUniform(gl, "shadowMatrix"), 1, false, columns, 0);
        gl.glUniform1f(getUniform(gl, "shadowTexelSize"), 1f / this.shadowMapSize);
      }
    }

    if (!frame.backgroundItems.isEmpty()) {
      // Draw the background around the camera, whatever its location
      double [] backgroundProjection = new double [16];
      double backgroundHalfWidth = Math.tan(frame.fieldOfView / 2) * 0.1;
      setFrustum(backgroundProjection, -backgroundHalfWidth, backgroundHalfWidth,
          -backgroundHalfWidth / aspect, backgroundHalfWidth / aspect, 0.1, 10);
      double [] backgroundView = frame.viewMatrix.clone();
      backgroundView [3] = 0;
      backgroundView [7] = 0;
      backgroundView [11] = 0;
      loadMatrix(gl, GLMatrixFunc.GL_PROJECTION, backgroundProjection);
      loadMatrix(gl, GLMatrixFunc.GL_MODELVIEW, backgroundView);
      if (!this.shadersAvailable) {
        setLights(gl, frame.backgroundLights);
      }
      gl.glDisable(GL.GL_DEPTH_TEST);
      gl.glDepthMask(false);
      for (Item item : frame.backgroundItems) {
        drawItem(gl, frame, item, backgroundView, true);
      }
      gl.glDepthMask(true);
      resetState(gl);
    }

    loadMatrix(gl, GLMatrixFunc.GL_PROJECTION, projectionMatrix);
    loadMatrix(gl, GLMatrixFunc.GL_MODELVIEW, frame.viewMatrix);
    if (!this.shadersAvailable) {
      setLights(gl, frame.lights);
    }
    gl.glEnable(GL.GL_DEPTH_TEST);

    double [][] frustumPlanes = getFrustumPlanes(projectionMatrix, frame.viewMatrix);
    List<Item> blendedItems = new ArrayList<Item>();
    for (Item item : frame.environmentItems) {
      if (item.isBlended()) {
        blendedItems.add(item);
      } else {
        drawItem(gl, frame, item, frame.viewMatrix, false);
      }
    }
    int culledItemCount = 0;
    // Shapes out of sight which aren't copied to the graphics card yet
    List<Item> unloadedItems = null;
    for (Item item : frame.opaqueItems) {
      if (isOutOfSight(item, frustumPlanes)) {
        culledItemCount++;
        if (item.mesh.loadState != MESH_UPLOADED) {
          if (unloadedItems == null) {
            unloadedItems = new ArrayList<Item>();
          }
          unloadedItems.add(item);
        }
      } else if (item.isBlended()) {
        // Its texture, loaded since the lists were built, is partly transparent
        blendedItems.add(item);
      } else {
        drawItem(gl, frame, item, frame.viewMatrix, false);
      }
    }
    for (Item item : frame.transparentItems) {
      if (isOutOfSight(item, frustumPlanes)) {
        culledItemCount++;
        if (item.mesh.loadState != MESH_UPLOADED) {
          if (unloadedItems == null) {
            unloadedItems = new ArrayList<Item>();
          }
          unloadedItems.add(item);
        }
      } else {
        blendedItems.add(item);
      }
    }
    this.culledItemCount = culledItemCount;
    if (!blendedItems.isEmpty()) {
      // Draw transparent shapes from the farthest to the closest one
      for (Item item : blendedItems) {
        item.viewDepth = getViewDepth(item, frame.viewMatrix);
      }
      Collections.sort(blendedItems, new Comparator<Item>() {
          public int compare(Item item1, Item item2) {
            return Double.compare(item1.viewDepth, item2.viewDepth);
          }
        });
      for (Item item : blendedItems) {
        drawItem(gl, frame, item, frame.viewMatrix, false);
      }
    }

    if (unloadedItems != null
        && (!this.imageRendering || this.frameShadowCasters)) {
      // Once the shapes in sight are displayed, copy the other ones to the graphics card
      // to be ready when they come in sight or to cast their shadow
      for (Item item : unloadedItems) {
        if (this.vertexUploadBudget <= 0 || this.uploadTimeBudget <= 0) {
          this.pendingUploads = true;
          break;
        } else if (item.mesh.loadState != MESH_UPLOADED && !upload(gl, item.mesh, true)) {
          continue;
        } else if (item.state.texture != null
                   && (item.state.glTexture == null || item.state.glTexture.deleted)) {
          item.state.glTexture = getTexture(gl, item.state.texture);
        }
      }
    }

    resetState(gl);
    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    if (this.shadersAvailable) {
      gl.glUseProgram(0);
      if (this.frameShadows) {
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
      }
    }
  }

  /**
   * Deletes the buffers and the textures that no shape uses anymore.
   */
  private void releaseResources(GL2 gl, Frame frame) {
    for (ReleasedMesh released; (released = this.releasedMeshes.peek()) != null
            && released.generation <= frame.generation; ) {
      this.releasedMeshes.poll();
      Mesh mesh = released.mesh;
      synchronized (mesh) {
        if (mesh.buffer != 0) {
          gl.glDeleteBuffers(1, new int [] {mesh.buffer}, 0);
          mesh.buffer = 0;
        }
        this.uploadedMeshes.remove(mesh);
        if (mesh.loadState == MESH_READY) {
          mesh.data = null;
          this.preparedBytes.addAndGet(-mesh.preparedSize);
        } else if (mesh.loadState == MESH_PREPARING) {
          mesh.released = true;
        }
        if (mesh.loadState != MESH_PREPARING) {
          mesh.loadState = MESH_NEW;
        }
      }
    }
    if (frame.generation != this.renderedGeneration || ++this.framesSinceTextureCheck > 600) {
      this.framesSinceTextureCheck = 0;
      this.shadowMapValid &= frame.generation == this.renderedGeneration;
      if (!this.textures.isEmpty()) {
        IdentityHashMap<Texture, Boolean> usedTextures = new IdentityHashMap<Texture, Boolean>();
        List<List<Item>> lists = new ArrayList<List<Item>>();
        lists.add(frame.opaqueItems);
        lists.add(frame.transparentItems);
        lists.add(frame.environmentItems);
        lists.add(frame.backgroundItems);
        for (List<Item> list : lists) {
          for (Item item : list) {
            if (item.state.texture != null) {
              usedTextures.put(item.state.texture, Boolean.TRUE);
            }
          }
        }
        for (Iterator<Map.Entry<Texture, GLTexture>> it = this.textures.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<Texture, GLTexture> entry = it.next();
          if (!usedTextures.containsKey(entry.getKey())) {
            GLTexture texture = entry.getValue();
            synchronized (texture) {
              if (texture.id != 0) {
                gl.glDeleteTextures(1, new int [] {texture.id}, 0);
                texture.id = 0;
              }
              texture.pixels = null;
              texture.deleted = true;
            }
            it.remove();
          }
        }
      }
    }
  }

  /**
   * Asks for a new frame.
   */
  private void requestFrame() {
    Runnable frameRequester = this.frameRequester;
    if (frameRequester != null && !this.disposed) {
      frameRequester.run();
    }
  }

  /**
   * Sets the lights of the scene computed by OpenGL at each vertex.
   */
  private void setLights(GL2 gl, LightState [] lights) {
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
   * Returns the ambient and directional lights which light the shapes
   * belonging to the given scopes, in the coordinates of the camera.
   */
  private LightSet getLightSet(Frame frame, int lightScopeMask, boolean background, double [] viewMatrix) {
    LightSet lightSet = background
        ? this.backgroundLightSet
        : this.lightSets.get(lightScopeMask);
    if (lightSet == null) {
      lightSet = new LightSet();
      LightState [] lights = background ? frame.backgroundLights : frame.lights;
      boolean keyLight = this.frameShadows && !background;
      for (LightState light : lights) {
        if (light.direction == null) {
          lightSet.ambient [0] += light.color [0];
          lightSet.ambient [1] += light.color [1];
          lightSet.ambient [2] += light.color [2];
        } else if (light.scopeBit == 0 || (lightScopeMask & light.scopeBit) != 0) {
          if (keyLight && lightSet.count < MAX_LIGHTS) {
            lightSet.shadowLight = lightSet.count;
            addLight(lightSet, this.shadowMapLightDirection, light.color, SHADOW_KEY_LIGHT_FACTOR, viewMatrix);
          }
          if (lightSet.count < MAX_LIGHTS) {
            addLight(lightSet, light.direction, light.color,
                this.frameShadows && !background ? SHADOW_FILL_LIGHT_FACTOR : 1, viewMatrix);
          }
          keyLight = false;
        } else {
          keyLight = false;
        }
      }
      if (background) {
        this.backgroundLightSet = lightSet;
      } else {
        this.lightSets.put(lightScopeMask, lightSet);
      }
    }
    return lightSet;
  }

  private void addLight(LightSet lightSet, float [] direction, float [] color, float factor, double [] viewMatrix) {
    int i = lightSet.count++;
    for (int row = 0; row < 3; row++) {
      lightSet.directions [i * 3 + row] = (float)(viewMatrix [row * 4] * direction [0]
          + viewMatrix [row * 4 + 1] * direction [1] + viewMatrix [row * 4 + 2] * direction [2]);
      lightSet.colors [i * 3 + row] = color [row] * factor;
    }
  }

  /**
   * Returns the direction towards the light which casts shadows: the one of
   * the given light, raised to light the floors of the rooms over their walls.
   */
  private static float [] getShadowLightDirection(float [] direction) {
    double horizontalLength = Math.sqrt(direction [0] * direction [0] + direction [2] * direction [2]);
    if (horizontalLength < 1E-4 || direction [1] <= 0) {
      return direction.clone();
    }
    double elevation = Math.max(Math.atan2(direction [1], horizontalLength), SHADOW_KEY_LIGHT_ELEVATION);
    double scale = Math.cos(elevation) / horizontalLength;
    return new float [] {(float)(direction [0] * scale), (float)Math.sin(elevation), (float)(direction [2] * scale), 0};
  }

  /**
   * Returns the indices of the point lights of the frame which light the given shape.
   */
  private int [] getPointLights(Frame frame, Item item) {
    PointLightState [] pointLights = frame.pointLights;
    if (pointLights.length == 0) {
      return NO_POINT_LIGHT;
    } else if (item.pointLightsSource == pointLights) {
      return item.pointLights;
    }
    updateBounds(item);
    int count = 0;
    int [] indices = new int [pointLights.length];
    final double [] distances = new double [pointLights.length];
    for (int i = 0; i < pointLights.length; i++) {
      PointLightState light = pointLights [i];
      double distanceToBounds = 0;
      if (light.lower != null) {
        // Distance between the center of the shape and the box lit by the light
        for (int axis = 0; axis < 3; axis++) {
          double delta = Math.max(light.lower [axis] - item.center [axis],
              Math.max(0, item.center [axis] - light.upper [axis]));
          distanceToBounds += delta * delta;
        }
      }
      if (distanceToBounds <= item.radius * item.radius) {
        double dx = light.position [0] - item.center [0];
        double dy = light.position [1] - item.center [1];
        double dz = light.position [2] - item.center [2];
        distances [i] = Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - item.radius);
        indices [count++] = i;
      }
    }
    boolean cacheable = true;
    if (count > MAX_POINT_LIGHTS) {
      // Keep the closest lights, and among the ones in a large shape the closest to the camera
      cacheable = false;
      final double [] camera = {frame.cameraMatrix [3], frame.cameraMatrix [7], frame.cameraMatrix [11]};
      Integer [] sortedIndices = new Integer [count];
      for (int i = 0; i < count; i++) {
        sortedIndices [i] = indices [i];
      }
      final PointLightState [] lights = pointLights;
      Arrays.sort(sortedIndices, new Comparator<Integer>() {
          public int compare(Integer index1, Integer index2) {
            int order = Double.compare(distances [index1], distances [index2]);
            if (order == 0) {
              order = Double.compare(getSquareDistance(lights [index1].position, camera),
                  getSquareDistance(lights [index2].position, camera));
            }
            return order;
          }
        });
      count = MAX_POINT_LIGHTS;
      for (int i = 0; i < count; i++) {
        indices [i] = sortedIndices [i];
      }
    }
    int [] itemLights = count == 0
        ? NO_POINT_LIGHT
        : Arrays.copyOf(indices, count);
    if (cacheable) {
      item.pointLightsSource = pointLights;
      item.pointLights = itemLights;
    }
    return itemLights;
  }

  private static double getSquareDistance(float [] point1, double [] point2) {
    double dx = point1 [0] - point2 [0];
    double dy = point1 [1] - point2 [1];
    double dz = point1 [2] - point2 [2];
    return dx * dx + dy * dy + dz * dz;
  }

  /**
   * Forgets the current OpenGL state and sets its default values.
   */
  private void resetState(GL2 gl) {
    this.currentState = null;
    this.currentMesh = null;
    this.currentTexture = 0;
    this.currentLightSet = null;
    gl.glFrontFace(GL.GL_CCW);
    gl.glDisable(GL.GL_BLEND);
    gl.glDisable(GL.GL_TEXTURE_2D);
    gl.glDisable(GL2.GL_ALPHA_TEST);
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
  private void drawItem(GL2 gl, Frame frame, Item item, double [] viewMatrix, boolean background) {
    Mesh mesh = item.mesh;
    if (mesh.loadState != MESH_UPLOADED) {
      if (this.depthOnlyPass) {
        // Geometries are copied to the graphics card when the scene is drawn, the ones in sight first
        return;
      } else if (!upload(gl, mesh, false)) {
        this.frameMissingItemCount++;
        return;
      }
    }
    if (mesh.vertexCount == 0) {
      return;
    }
    State state = item.state;
    GLTexture texture = null;
    if (state.texture != null) {
      texture = state.glTexture;
      if (texture == null || texture.deleted || texture.id == 0 && !texture.failed) {
        texture = getTexture(gl, state.texture);
        state.glTexture = texture;
        if (texture == null) {
          // Draw the shape once its texture is ready
          if (!this.depthOnlyPass) {
            this.frameMissingItemCount++;
          }
          return;
        }
      }
    }

    boolean shaders = this.shadersAvailable && !this.depthOnlyPass;
    if (state != this.currentState) {
      this.currentState = state;
      if (this.depthOnlyPass) {
        applyDepthState(gl, state, texture);
      } else if (shaders) {
        applyShaderState(gl, state, texture, background);
      } else {
        applyState(gl, state, texture, background);
      }
    }
    // As in Java 3D, the front faces of a mirrored shape are the ones of its other side,
    // and it's up to the appearance of the shape to choose the faces to display
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
      if (shaders) {
        gl.glUniform1i(getUniform(gl, "vertexColors"), mesh.colors ? 1 : 0);
      }
    }
    if (shaders) {
      if (state.lighting) {
        LightSet lightSet = getLightSet(frame, item.lightScopeMask, background, viewMatrix);
        if (lightSet != this.currentLightSet) {
          this.currentLightSet = lightSet;
          gl.glUniform3fv(getUniform(gl, "ambientLight"), 1, lightSet.ambient, 0);
          gl.glUniform1i(getUniform(gl, "directionalLightCount"), lightSet.count);
          if (lightSet.count > 0) {
            gl.glUniform3fv(getUniform(gl, "directionalLightDirection"), lightSet.count, lightSet.directions, 0);
            gl.glUniform3fv(getUniform(gl, "directionalLightColor"), lightSet.count, lightSet.colors, 0);
          }
          gl.glUniform1i(getUniform(gl, "shadowLight"), lightSet.shadowLight);
        }
        int [] pointLights = background
            ? NO_POINT_LIGHT
            : getPointLights(frame, item);
        if (pointLights != this.currentPointLights
            && !Arrays.equals(pointLights, this.currentPointLights)) {
          setPointLights(gl, frame, pointLights);
        }
        this.currentPointLights = pointLights;
      }
    } else if (!mesh.colors && !state.lighting && !this.depthOnlyPass) {
      gl.glColor4f(state.color [0], state.color [1], state.color [2], state.alpha);
    }

    // Model view matrix
    float [] m = item.matrix;
    float [] modelView = this.modelView;
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
    if (!this.depthOnlyPass) {
      this.frameItemCount++;
      this.frameVertexCount += mesh.vertexCount;
    }
  }

  private void setPointLights(GL2 gl, Frame frame, int [] pointLights) {
    int count = pointLights.length;
    gl.glUniform1i(getUniform(gl, "pointLightCount"), count);
    if (count > 0) {
      float [] positions = new float [count * 3];
      float [] colors = new float [count * 3];
      float [] attenuations = new float [count * 3];
      for (int i = 0; i < count; i++) {
        PointLightState light = frame.pointLights [pointLights [i]];
        System.arraycopy(this.pointLightEyePositions, pointLights [i] * 3, positions, i * 3, 3);
        System.arraycopy(light.color, 0, colors, i * 3, 3);
        System.arraycopy(light.attenuation, 0, attenuations, i * 3, 3);
      }
      gl.glUniform3fv(getUniform(gl, "pointLightPosition"), count, positions, 0);
      gl.glUniform3fv(getUniform(gl, "pointLightColor"), count, colors, 0);
      gl.glUniform3fv(getUniform(gl, "pointLightAttenuation"), count, attenuations, 0);
    }
  }

  /**
   * Returns the location of a uniform variable of the program which draws the scene.
   */
  private int getUniform(GL2 gl, String name) {
    Integer location = this.sceneUniforms.get(name);
    if (location == null) {
      location = gl.glGetUniformLocation(this.sceneProgram, name);
      this.sceneUniforms.put(name, location);
    }
    return location;
  }

  /**
   * Sets the state shared by the three ways to draw shapes.
   */
  private void applyCommonState(GL2 gl, State state, GLTexture texture) {
    if (texture != null && texture.id != 0) {
      if (texture.id != this.currentTexture) {
        this.currentTexture = texture.id;
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture.id);
      }
      gl.glMatrixMode(GL.GL_TEXTURE);
      if (state.textureTransform != null) {
        gl.glLoadMatrixf(state.textureTransform, 0);
      } else {
        gl.glLoadIdentity();
      }
      gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    }
    if (state.cullFace == PolygonAttributes.CULL_NONE) {
      gl.glDisable(GL.GL_CULL_FACE);
    } else {
      gl.glEnable(GL.GL_CULL_FACE);
      gl.glCullFace(state.cullFace == PolygonAttributes.CULL_FRONT ? GL.GL_FRONT : GL.GL_BACK);
    }
  }

  private void applyTextureGeneration(GL2 gl, State state, GLTexture texture) {
    if (texture != null && texture.id != 0) {
      gl.glEnable(GL.GL_TEXTURE_2D);
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
  }

  private void applyOutputState(GL2 gl, State state, GLTexture texture, boolean background) {
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
   * Sets the OpenGL state of the following shapes, lit by OpenGL at each vertex.
   */
  private void applyState(GL2 gl, State state, GLTexture texture, boolean background) {
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
    applyCommonState(gl, state, texture);
    applyTextureGeneration(gl, state, texture);
    if (texture != null && texture.id != 0) {
      gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, state.textureMode);
    }
    gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, state.backFaceNormalFlip ? 1 : 0);
    applyOutputState(gl, state, texture, background);
  }

  /**
   * Sets the OpenGL state of the following shapes, lit by the scene program at each pixel.
   */
  private void applyShaderState(GL2 gl, State state, GLTexture texture, boolean background) {
    gl.glUniform1i(getUniform(gl, "lighting"), state.lighting ? 1 : 0);
    if (state.lighting) {
      gl.glUniform4fv(getUniform(gl, "materialAmbient"), 1, state.ambient, 0);
      gl.glUniform4fv(getUniform(gl, "materialDiffuse"), 1, state.diffuse, 0);
      gl.glUniform4fv(getUniform(gl, "materialSpecular"), 1, state.specular, 0);
      gl.glUniform4fv(getUniform(gl, "materialEmissive"), 1, state.emissive, 0);
      gl.glUniform1f(getUniform(gl, "shininess"), Math.max(1, state.shininess));
      gl.glUniform1i(getUniform(gl, "twoSided"), state.backFaceNormalFlip ? 1 : 0);
    } else {
      gl.glUniform4f(getUniform(gl, "flatColor"), state.color [0], state.color [1], state.color [2], state.alpha);
    }
    applyCommonState(gl, state, texture);
    if (texture != null && texture.id != 0) {
      gl.glUniform1i(getUniform(gl, "textureMode"), state.textureMode == GL2.GL_REPLACE
          ? 1
          : (state.textureMode == GL2.GL_DECAL ? 3 : 2));
      gl.glUniform1i(getUniform(gl, "textureAlpha"), texture.alpha ? 1 : 0);
      if (state.texturePlaneS != null) {
        gl.glUniform1i(getUniform(gl, "textureGeneration"), 1);
        gl.glUniform4fv(getUniform(gl, "texturePlaneS"), 1, state.texturePlaneS, 0);
        gl.glUniform4fv(getUniform(gl, "texturePlaneT"), 1, state.texturePlaneT, 0);
      } else {
        gl.glUniform1i(getUniform(gl, "textureGeneration"), 0);
      }
    } else {
      gl.glUniform1i(getUniform(gl, "textureMode"), 0);
    }
    applyOutputState(gl, state, texture, background);
  }

  /**
   * Sets the OpenGL state of the following shapes drawn in a shadow map.
   */
  private void applyDepthState(GL2 gl, State state, GLTexture texture) {
    applyCommonState(gl, state, texture);
    if (texture != null && texture.transparent) {
      // Leaves and other cut out shapes cast the shadow of their opaque pixels
      applyTextureGeneration(gl, state, texture);
      gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
      gl.glEnable(GL2.GL_ALPHA_TEST);
      gl.glAlphaFunc(GL.GL_GREATER, 0.5f);
    } else {
      applyTextureGeneration(gl, state, null);
      gl.glDisable(GL2.GL_ALPHA_TEST);
    }
  }

  /**
   * Returns the OpenGL texture matching a Java 3D texture, or <code>null</code>
   * if it isn't ready yet.
   */
  private GLTexture getTexture(GL2 gl, final Texture texture) {
    GLTexture glTexture = this.textures.get(texture);
    if (glTexture == null) {
      final GLTexture newTexture = new GLTexture();
      glTexture = newTexture;
      this.textures.put(texture, glTexture);
      if (this.synchronousLoading) {
        prepareTexture(texture, newTexture);
      } else {
        this.preparationCount.incrementAndGet();
        getPreparationExecutor().execute(new Runnable() {
            public void run() {
              try {
                prepareTexture(texture, newTexture);
              } finally {
                preparationCount.decrementAndGet();
                requestFrame();
              }
            }
          });
        return null;
      }
    }
    synchronized (glTexture) {
      if (glTexture.failed || glTexture.id != 0) {
        return glTexture;
      } else if (glTexture.pixels == null) {
        // Still being prepared
        return null;
      } else if (this.pixelUploadBudget <= 0 || this.uploadTimeBudget <= 0) {
        this.pendingUploads = true;
        return null;
      }
      long uploadStart = System.nanoTime();
      try {
        int [] id = new int [1];
        gl.glGenTextures(1, id, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, id [0]);
        this.currentTexture = id [0];
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, glTexture.wrapS ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, glTexture.wrapT ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        if (this.maxAnisotropy > 1) {
          // Keep the textures of floors and walls sharp when they are viewed at a low angle
          gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, this.maxAnisotropy);
        }
        if (!this.generateMipmapAvailable) {
          gl.glTexParameteri(GL.GL_TEXTURE_2D, GL2.GL_GENERATE_MIPMAP, 1);
        }
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, glTexture.width, glTexture.height, 0,
            GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, glTexture.pixels);
        if (this.generateMipmapAvailable) {
          gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        }
        glTexture.id = id [0];
        this.pixelUploadBudget -= glTexture.width * glTexture.height;
      } catch (RuntimeException ex) {
        // Draw the shapes without their texture
        glTexture.failed = true;
      }
      glTexture.pixels = null;
      this.uploadTimeBudget -= System.nanoTime() - uploadStart;
      // The state which was bound is unknown now
      this.currentState = null;
      return glTexture;
    }
  }

  /**
   * Reads the pixels of a texture. This method may be called from any thread.
   */
  private void prepareTexture(Texture texture, GLTexture glTexture) {
    try {
      ImageComponent imageComponent = texture.getImage(0);
      if (!(imageComponent instanceof ImageComponent2D)) {
        glTexture.failed = true;
        return;
      }
      RenderedImage renderedImage = ((ImageComponent2D)imageComponent).getRenderedImage();
      BufferedImage image;
      if (renderedImage instanceof BufferedImage) {
        image = (BufferedImage)renderedImage;
      } else {
        image = ((ImageComponent2D)imageComponent).getImage();
      }
      int width = image.getWidth();
      int height = image.getHeight();
      int maxTextureSize = this.maxTextureSize;
      if (width > maxTextureSize || height > maxTextureSize) {
        float scale = Math.min((float)maxTextureSize / width, (float)maxTextureSize / height);
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
      byte [] rowBytes = new byte [width * 4];
      boolean transparent = false;
      boolean alpha = image.getColorModel().hasAlpha();
      for (int y = 0; y < height; y++) {
        image.getRGB(0, flip ? height - 1 - y : y, width, 1, row, 0, width);
        for (int x = 0, i = 0; x < width; x++) {
          int argb = row [x];
          rowBytes [i++] = (byte)(argb >> 16);
          rowBytes [i++] = (byte)(argb >> 8);
          rowBytes [i++] = (byte)argb;
          rowBytes [i++] = (byte)(argb >>> 24);
          if (alpha && (argb >>> 24) < 250) {
            transparent = true;
          }
        }
        pixels.put(rowBytes);
      }
      // Cast required to run under Java 8 once compiled with a more recent JDK
      ((java.nio.Buffer)pixels).flip();
      synchronized (glTexture) {
        if (!glTexture.deleted) {
          glTexture.width = width;
          glTexture.height = height;
          glTexture.wrapS = texture.getBoundaryModeS() == Texture.WRAP;
          glTexture.wrapT = texture.getBoundaryModeT() == Texture.WRAP;
          glTexture.transparent = transparent;
          glTexture.alpha = alpha;
          glTexture.pixels = pixels;
        }
      }
    } catch (RuntimeException ex) {
      glTexture.failed = true;
    } catch (OutOfMemoryError ex) {
      glTexture.failed = true;
    }
  }

  /**
   * Copies the vertices of a geometry to a buffer of the graphics card,
   * and returns <code>true</code> if it's ready to be drawn.
   * @param background  <code>true</code> if the geometry isn't displayed in the current frame
   */
  private boolean upload(GL2 gl, final Mesh mesh, boolean background) {
    synchronized (mesh) {
      switch (mesh.loadState) {
        case MESH_UPLOADED :
          return true;
        case MESH_FAILED :
        case MESH_PREPARING :
          return false;
        case MESH_NEW :
          if (this.synchronousLoading) {
            if (this.vertexUploadBudget <= 0) {
              this.pendingUploads = true;
              return false;
            }
            try {
              mesh.data = mesh.createVertices();
            } catch (RuntimeException ex) {
              mesh.data = null;
            } catch (OutOfMemoryError ex) {
              mesh.data = null;
            }
            if (mesh.data == null) {
              mesh.loadState = MESH_FAILED;
              return false;
            }
            mesh.preparedSize = 0;
            mesh.loadState = MESH_READY;
            break;
          } else {
            // Keep half of the vertices prepared in advance for the geometries in sight
            if (this.preparedBytes.get() < (background ? MAX_PREPARED_BYTES / 2 : MAX_PREPARED_BYTES)) {
              final long size = mesh.getEstimatedSize();
              mesh.loadState = MESH_PREPARING;
              mesh.released = false;
              mesh.preparedSize = size;
              this.preparedBytes.addAndGet(size);
              this.preparationCount.incrementAndGet();
              getPreparationExecutor().execute(new Runnable() {
                  public void run() {
                    FloatBuffer vertices = null;
                    try {
                      vertices = mesh.createVertices();
                    } catch (RuntimeException ex) {
                      // Shape ignored
                    } catch (OutOfMemoryError ex) {
                    } finally {
                      synchronized (mesh) {
                        if (mesh.released) {
                          mesh.loadState = MESH_NEW;
                          preparedBytes.addAndGet(-size);
                        } else if (vertices != null) {
                          mesh.data = vertices;
                          mesh.loadState = MESH_READY;
                        } else {
                          mesh.loadState = MESH_FAILED;
                          preparedBytes.addAndGet(-size);
                        }
                      }
                      preparationCount.decrementAndGet();
                      requestFrame();
                    }
                  }
                });
            } else if (!background) {
              // Try again once some of the prepared vertices are copied
              this.pendingUploads = true;
            }
            return false;
          }
        default :
          break;
      }
      // Vertices ready
      if (this.vertexUploadBudget <= 0 || this.uploadTimeBudget <= 0) {
        // Leave it to a next frame
        this.pendingUploads = true;
        return false;
      }
      long uploadStart = System.nanoTime();
      try {
        if (mesh.vertexCount > 0) {
          int [] buffer = new int [1];
          gl.glGenBuffers(1, buffer, 0);
          gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer [0]);
          gl.glBufferData(GL.GL_ARRAY_BUFFER, (long)mesh.data.capacity() * 4, mesh.data, GL.GL_STATIC_DRAW);
          mesh.buffer = buffer [0];
          this.currentMesh = null;
          this.vertexUploadBudget -= mesh.vertexCount;
        }
        mesh.loadState = MESH_UPLOADED;
        this.uploadedMeshes.put(mesh, Boolean.TRUE);
        this.uploadsDone = true;
      } catch (RuntimeException ex) {
        mesh.loadState = MESH_FAILED;
      }
      mesh.data = null;
      this.uploadTimeBudget -= System.nanoTime() - uploadStart;
      this.preparedBytes.addAndGet(-mesh.preparedSize);
      mesh.preparedSize = 0;
      return mesh.loadState == MESH_UPLOADED;
    }
  }

  /**
   * Computes the bounding sphere of a shape in the scene.
   */
  private void updateBounds(Item item) {
    if (item.radius < 0) {
      Mesh mesh = item.mesh;
      float [] m = item.matrix;
      float x = mesh.center [0];
      float y = mesh.center [1];
      float z = mesh.center [2];
      item.center = new double [] {
          m [0] * x + m [4] * y + m [8] * z + m [12],
          m [1] * x + m [5] * y + m [9] * z + m [13],
          m [2] * x + m [6] * y + m [10] * z + m [14]};
      // Radius of the sphere which contains the transformed box of the geometry
      // (the lengths of the columns of the matrix would underestimate it
      // when a model is rotated then scaled differently along each axis)
      float halfX = (mesh.upper [0] - mesh.lower [0]) / 2;
      float halfY = (mesh.upper [1] - mesh.lower [1]) / 2;
      float halfZ = (mesh.upper [2] - mesh.lower [2]) / 2;
      double squareRadius = 0;
      for (int i = 0; i < 4; i++) {
        float dx = halfX;
        float dy = (i & 1) == 0 ? halfY : -halfY;
        float dz = (i & 2) == 0 ? halfZ : -halfZ;
        double cornerX = m [0] * dx + m [4] * dy + m [8] * dz;
        double cornerY = m [1] * dx + m [5] * dy + m [9] * dz;
        double cornerZ = m [2] * dx + m [6] * dy + m [10] * dz;
        squareRadius = Math.max(squareRadius, cornerX * cornerX + cornerY * cornerY + cornerZ * cornerZ);
      }
      item.radius = Math.sqrt(squareRadius);
    }
  }

  /**
   * Returns <code>true</code> if the bounding sphere of the given shape is out of the frustum.
   */
  private boolean isOutOfSight(Item item, double [][] planes) {
    if (item.mesh.loadState != MESH_UPLOADED
        && !item.mesh.boundsKnown
        && !item.mesh.computeBounds()) {
      return false;
    }
    updateBounds(item);
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

  // ---- Shadows

  /**
   * Draws the depth of the objects viewed from the first directional light
   * if it changed, and returns <code>true</code> if shadows can be displayed.
   */
  private boolean updateShadowMap(GL2 gl, Frame frame) {
    LightState keyLight = null;
    for (LightState light : frame.lights) {
      if (light.direction != null) {
        keyLight = light;
        break;
      }
    }
    if (keyLight == null) {
      return false;
    }
    int shadowMapSize = Math.min(this.shadowMapMaxSize, this.maxTextureSize);
    if (this.shadowTexture != 0 && this.shadowMapSize != shadowMapSize) {
      deleteShadowMap(gl);
    }
    if (this.shadowTexture == 0) {
      this.shadowMapSize = shadowMapSize;
      int [] id = new int [1];
      gl.glGenTextures(1, id, 0);
      this.shadowTexture = id [0];
      gl.glBindTexture(GL.GL_TEXTURE_2D, this.shadowTexture);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL2.GL_TEXTURE_COMPARE_MODE, GL2.GL_COMPARE_R_TO_TEXTURE);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL2.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
      gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_DEPTH_COMPONENT24, this.shadowMapSize, this.shadowMapSize, 0,
          GL2.GL_DEPTH_COMPONENT, GL.GL_UNSIGNED_INT, null);
      gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
      gl.glGenFramebuffers(1, id, 0);
      this.shadowFramebuffer = id [0];
      gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.shadowFramebuffer);
      gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, this.shadowTexture, 0);
      gl.glDrawBuffer(GL.GL_NONE);
      gl.glReadBuffer(GL.GL_NONE);
      boolean complete = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) == GL.GL_FRAMEBUFFER_COMPLETE;
      gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.defaultFramebuffer);
      if (!complete) {
        deleteShadowMap(gl);
        this.shadows = false;
        return false;
      }
      this.shadowMapValid = false;
    }
    float [] lightDirection = getShadowLightDirection(keyLight.direction);
    if (this.shadowMapValid
        && Arrays.equals(this.shadowMapLightDirection, lightDirection)
        && frame.generation == this.renderedGeneration) {
      return true;
    }
    if (this.shadowWorldMatrix != null
        && !this.synchronousLoading
        && !this.imageRendering
        && System.nanoTime() - this.shadowMapTime < Math.min(1000000000L, 4 * this.shadowMapDuration)) {
      // Drawing all the objects from the light takes time: while the scene changes
      // at each frame, keep the previous shadows and update them a little later
      this.shadowMapValid = false;
      this.shadowUpdateDeferred = true;
      return true;
    }
    long shadowMapStart = System.nanoTime();

    // Orthographic view from the light which contains the objects casting a shadow
    Vector3d light = new Vector3d(lightDirection [0], lightDirection [1], lightDirection [2]);
    light.normalize();
    Vector3d up = Math.abs(light.y) > 0.99 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
    Vector3d xAxis = new Vector3d();
    xAxis.cross(up, light);
    xAxis.normalize();
    Vector3d yAxis = new Vector3d();
    yAxis.cross(light, xAxis);
    Vector3d [] axes = {xAxis, yAxis, light};
    double [] lower = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
    double [] upper = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
    double [] corner = new double [3];
    for (Item item : frame.opaqueItems) {
      if (item.mesh.loadState == MESH_UPLOADED && isShadowCaster(item)) {
        float [] m = item.matrix;
        float [] meshLower = item.mesh.lower;
        float [] meshUpper = item.mesh.upper;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double [] itemLower = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double [] itemUpper = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < 8; i++) {
          float x = (i & 1) == 0 ? meshLower [0] : meshUpper [0];
          float y = (i & 2) == 0 ? meshLower [1] : meshUpper [1];
          float z = (i & 4) == 0 ? meshLower [2] : meshUpper [2];
          corner [0] = m [0] * x + m [4] * y + m [8] * z + m [12];
          corner [1] = m [1] * x + m [5] * y + m [9] * z + m [13];
          corner [2] = m [2] * x + m [6] * y + m [10] * z + m [14];
          minY = Math.min(minY, corner [1]);
          maxY = Math.max(maxY, corner [1]);
          for (int axis = 0; axis < 3; axis++) {
            double coordinate = axes [axis].x * corner [0] + axes [axis].y * corner [1] + axes [axis].z * corner [2];
            itemLower [axis] = Math.min(itemLower [axis], coordinate);
            itemUpper [axis] = Math.max(itemUpper [axis], coordinate);
          }
        }
        // Lawns, paths and other flat shapes on the ground cast no visible shadow
        // and shouldn't spread the shadow map over a large area
        if (maxY - minY > 3 || maxY > 10) {
          for (int axis = 0; axis < 3; axis++) {
            lower [axis] = Math.min(lower [axis], itemLower [axis]);
            upper [axis] = Math.max(upper [axis], itemUpper [axis]);
          }
        }
      }
    }
    if (lower [0] > upper [0]) {
      lower = new double [] {-100, -100, -100};
      upper = new double [] {100, 100, 100};
    }
    for (int axis = 0; axis < 3; axis++) {
      double margin = Math.max(1, (upper [axis] - lower [axis]) * 0.01);
      lower [axis] -= margin;
      upper [axis] += margin;
    }
    double width = upper [0] - lower [0];
    double height = upper [1] - lower [1];
    double depth = upper [2] - lower [2];
    double [] matrix = {
        2 * xAxis.x / width, 2 * xAxis.y / width, 2 * xAxis.z / width, -(upper [0] + lower [0]) / width,
        2 * yAxis.x / height, 2 * yAxis.y / height, 2 * yAxis.z / height, -(upper [1] + lower [1]) / height,
        -2 * light.x / depth, -2 * light.y / depth, -2 * light.z / depth, (upper [2] + lower [2]) / depth,
        0, 0, 0, 1};
    this.shadowWorldMatrix = matrix;
    this.shadowMapLightDirection = lightDirection;

    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.shadowFramebuffer);
    gl.glViewport(0, 0, this.shadowMapSize, this.shadowMapSize);
    gl.glDepthMask(true);
    gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
    resetState(gl);
    gl.glColorMask(false, false, false, false);
    gl.glEnable(GL.GL_DEPTH_TEST);
    gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
    gl.glPolygonOffset(2f, 4f);
    loadMatrix(gl, GLMatrixFunc.GL_PROJECTION, matrix);
    double [] identity = {1, 0, 0, 0,  0, 1, 0, 0,  0, 0, 1, 0,  0, 0, 0, 1};
    this.depthOnlyPass = true;
    for (Item item : frame.opaqueItems) {
      if (isShadowCaster(item)) {
        drawItem(gl, frame, item, identity, false);
      }
    }
    this.depthOnlyPass = false;
    resetState(gl);
    gl.glColorMask(true, true, true, true);
    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.defaultFramebuffer);
    this.shadowMapValid = !this.pendingUploads;
    gl.glFinish();
    this.shadowMapTime = System.nanoTime();
    this.shadowMapDuration = this.shadowMapTime - shadowMapStart;
    return true;
  }

  private boolean isShadowCaster(Item item) {
    State state = item.state;
    return item.mesh.mode == GL.GL_TRIANGLES
        && state.depthWrite
        && state.depthTest
        && state.polygonMode == PolygonAttributes.POLYGON_FILL
        && !state.isBlended();
  }

  private void deleteShadowMap(GL2 gl) {
    if (this.shadowFramebuffer != 0) {
      gl.glDeleteFramebuffers(1, new int [] {this.shadowFramebuffer}, 0);
      this.shadowFramebuffer = 0;
    }
    if (this.shadowTexture != 0) {
      gl.glDeleteTextures(1, new int [] {this.shadowTexture}, 0);
      this.shadowTexture = 0;
    }
    this.shadowMapValid = false;
  }

  // ---- Ambient occlusion

  /**
   * Darkens in the target frame buffer the pixels of the resolved frame
   * which are close to an other surface.
   */
  private void drawOcclusion(GL2 gl, Frame frame, int width, int height, int targetFramebuffer) {
    gl.glDisable(GL.GL_DEPTH_TEST);
    gl.glDisable(GL.GL_CULL_FACE);
    gl.glDisable(GL.GL_BLEND);
    gl.glDepthMask(false);
    gl.glViewport(0, 0, width, height);
    float tanHalfWidth = (float)Math.tan(frame.fieldOfView / 2);
    float tanHalfHeight = tanHalfWidth * height / width;

    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.occlusionTarget.framebuffer);
    gl.glUseProgram(this.occlusionProgram);
    gl.glBindTexture(GL.GL_TEXTURE_2D, this.resolvedTarget.depthBuffer);
    gl.glUniform1i(gl.glGetUniformLocation(this.occlusionProgram, "depthTexture"), 0);
    gl.glUniform4f(gl.glGetUniformLocation(this.occlusionProgram, "projection"),
        tanHalfWidth, tanHalfHeight, (float)frame.frontClipDistance, (float)frame.backClipDistance);
    gl.glUniform2f(gl.glGetUniformLocation(this.occlusionProgram, "pixelSize"), 1f / width, 1f / height);
    drawQuad(gl);

    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, targetFramebuffer);
    gl.glUseProgram(this.compositionProgram);
    gl.glUniform1i(gl.glGetUniformLocation(this.compositionProgram, "colorTexture"), 0);
    gl.glUniform1i(gl.glGetUniformLocation(this.compositionProgram, "occlusionTexture"), 1);
    gl.glUniform1i(gl.glGetUniformLocation(this.compositionProgram, "depthTexture"), 2);
    gl.glUniform2f(gl.glGetUniformLocation(this.compositionProgram, "pixelSize"), 1f / width, 1f / height);
    gl.glUniform2f(gl.glGetUniformLocation(this.compositionProgram, "clipDistances"),
        (float)frame.frontClipDistance, (float)frame.backClipDistance);
    gl.glActiveTexture(GL.GL_TEXTURE2);
    gl.glBindTexture(GL.GL_TEXTURE_2D, this.resolvedTarget.depthBuffer);
    gl.glActiveTexture(GL.GL_TEXTURE1);
    gl.glBindTexture(GL.GL_TEXTURE_2D, this.occlusionTarget.colorBuffer);
    gl.glActiveTexture(GL.GL_TEXTURE0);
    gl.glBindTexture(GL.GL_TEXTURE_2D, this.resolvedTarget.colorBuffer);
    drawQuad(gl);
    gl.glActiveTexture(GL.GL_TEXTURE2);
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    gl.glActiveTexture(GL.GL_TEXTURE1);
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    gl.glActiveTexture(GL.GL_TEXTURE0);
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    gl.glUseProgram(0);
    gl.glDepthMask(true);
  }

  private void drawQuad(GL2 gl) {
    gl.glBegin(GL2.GL_QUADS);
    gl.glTexCoord2f(0, 0);
    gl.glVertex2f(-1, -1);
    gl.glTexCoord2f(1, 0);
    gl.glVertex2f(1, -1);
    gl.glTexCoord2f(1, 1);
    gl.glVertex2f(1, 1);
    gl.glTexCoord2f(0, 1);
    gl.glVertex2f(-1, 1);
    gl.glEnd();
  }

  // ---- Frame buffers and programs

  /**
   * Returns a frame buffer of the given size, which reuses <code>target</code> if it matches.
   * @param textures  if <code>true</code> the buffers are textures that programs may read
   * @return the frame buffer or <code>null</code> if it can't be created
   */
  private Target getTarget(GL2 gl, Target target, int width, int height, int samples, boolean textures, boolean depth) {
    if (target != null
        && target.width == width
        && target.height == height
        && target.samples == samples) {
      return target;
    }
    deleteTarget(gl, target);
    target = new Target();
    target.width = width;
    target.height = height;
    target.samples = samples;
    target.textures = textures;
    int [] id = new int [1];
    gl.glGenFramebuffers(1, id, 0);
    target.framebuffer = id [0];
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, target.framebuffer);
    if (textures) {
      gl.glGenTextures(1, id, 0);
      target.colorBuffer = id [0];
      gl.glBindTexture(GL.GL_TEXTURE_2D, target.colorBuffer);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
      gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
      gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, target.colorBuffer, 0);
      if (depth) {
        gl.glGenTextures(1, id, 0);
        target.depthBuffer = id [0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, target.depthBuffer);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_DEPTH_COMPONENT24, width, height, 0,
            GL2.GL_DEPTH_COMPONENT, GL.GL_UNSIGNED_INT, null);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, target.depthBuffer, 0);
      }
      gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    } else {
      gl.glGenRenderbuffers(1, id, 0);
      target.colorBuffer = id [0];
      gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, target.colorBuffer);
      gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, samples, GL.GL_RGBA8, width, height);
      gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_RENDERBUFFER, target.colorBuffer);
      if (depth) {
        gl.glGenRenderbuffers(1, id, 0);
        target.depthBuffer = id [0];
        gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, target.depthBuffer);
        gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, samples, GL.GL_DEPTH_COMPONENT24, width, height);
        gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, target.depthBuffer);
      }
      gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, 0);
    }
    boolean complete = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) == GL.GL_FRAMEBUFFER_COMPLETE
        && gl.glGetError() == GL.GL_NO_ERROR;
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, this.defaultFramebuffer);
    if (!complete) {
      deleteTarget(gl, target);
      return null;
    }
    return target;
  }

  private Target deleteTarget(GL2 gl, Target target) {
    if (target != null) {
      int [] id = new int [1];
      if (target.framebuffer != 0) {
        id [0] = target.framebuffer;
        gl.glDeleteFramebuffers(1, id, 0);
      }
      for (int buffer : new int [] {target.colorBuffer, target.depthBuffer}) {
        if (buffer != 0) {
          id [0] = buffer;
          if (target.textures) {
            gl.glDeleteTextures(1, id, 0);
          } else {
            gl.glDeleteRenderbuffers(1, id, 0);
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns a program built from the given shaders.
   */
  private static int createProgram(GL2 gl, String vertexShaderSource, String fragmentShaderSource) {
    int vertexShader = createShader(gl, GL2.GL_VERTEX_SHADER, vertexShaderSource);
    int fragmentShader = createShader(gl, GL2.GL_FRAGMENT_SHADER, fragmentShaderSource);
    int program = gl.glCreateProgram();
    gl.glAttachShader(program, vertexShader);
    gl.glAttachShader(program, fragmentShader);
    gl.glLinkProgram(program);
    gl.glDeleteShader(vertexShader);
    gl.glDeleteShader(fragmentShader);
    int [] status = new int [1];
    gl.glGetProgramiv(program, GL2.GL_LINK_STATUS, status, 0);
    if (status [0] == 0) {
      byte [] log = new byte [4096];
      int [] length = new int [1];
      gl.glGetProgramInfoLog(program, log.length, length, 0, log, 0);
      gl.glDeleteProgram(program);
      throw new IllegalStateException("Program not linked: " + new String(log, 0, Math.max(0, length [0])));
    }
    return program;
  }

  private static int createShader(GL2 gl, int type, String source) {
    int shader = gl.glCreateShader(type);
    gl.glShaderSource(shader, 1, new String [] {source}, new int [] {source.length()}, 0);
    gl.glCompileShader(shader);
    int [] status = new int [1];
    gl.glGetShaderiv(shader, GL2.GL_COMPILE_STATUS, status, 0);
    if (status [0] == 0) {
      byte [] log = new byte [4096];
      int [] length = new int [1];
      gl.glGetShaderInfoLog(shader, log.length, length, 0, log, 0);
      gl.glDeleteShader(shader);
      throw new IllegalStateException("Shader not compiled: " + new String(log, 0, Math.max(0, length [0])));
    }
    return shader;
  }

  // ---- Matrices

  /**
   * Returns the six planes of the frustum in scene coordinates, their normal pointing inside.
   */
  private static double [][] getFrustumPlanes(double [] projection, double [] view) {
    // Rows of projection * view (both stored row by row)
    double [] m = multiply(projection, view);
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
   * Returns the product of two matrices stored row by row.
   */
  private static double [] multiply(double [] matrix1, double [] matrix2) {
    double [] m = new double [16];
    for (int row = 0; row < 4; row++) {
      for (int column = 0; column < 4; column++) {
        double sum = 0;
        for (int k = 0; k < 4; k++) {
          sum += matrix1 [row * 4 + k] * matrix2 [k * 4 + column];
        }
        m [row * 4 + column] = sum;
      }
    }
    return m;
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

  // ---- Shaders

  private static final int [] NO_POINT_LIGHT = {};

  private static final String SCENE_VERTEX_SHADER =
        "#version 120\n"
      + "uniform bool textureGeneration;\n"
      + "uniform vec4 texturePlaneS;\n"
      + "uniform vec4 texturePlaneT;\n"
      + "uniform mat4 shadowMatrix;\n"
      + "varying vec3 eyePosition;\n"
      + "varying vec3 eyeNormal;\n"
      + "varying vec4 shadowCoordinates;\n"
      + "varying vec4 vertexColor;\n"
      + "void main() {\n"
      + "  vec4 position = gl_ModelViewMatrix * gl_Vertex;\n"
      + "  eyePosition = position.xyz;\n"
      + "  eyeNormal = gl_NormalMatrix * gl_Normal;\n"
      + "  vec4 coordinates = textureGeneration\n"
      + "      ? vec4(dot(gl_Vertex, texturePlaneS), dot(gl_Vertex, texturePlaneT), 0., 1.)\n"
      + "      : gl_MultiTexCoord0;\n"
      + "  gl_TexCoord[0] = gl_TextureMatrix[0] * coordinates;\n"
      + "  vertexColor = gl_Color;\n"
      + "  shadowCoordinates = shadowMatrix * position;\n"
      + "  gl_Position = gl_ProjectionMatrix * position;\n"
      + "}\n";

  private static final String SCENE_FRAGMENT_SHADER =
        "#version 120\n"
      + "uniform sampler2D texture0;\n"
      + "uniform sampler2DShadow shadowMap;\n"
      + "uniform int textureMode;\n"
      + "uniform bool textureAlpha;\n"
      + "uniform bool lighting;\n"
      + "uniform bool twoSided;\n"
      + "uniform bool vertexColors;\n"
      + "uniform vec4 materialAmbient;\n"
      + "uniform vec4 materialDiffuse;\n"
      + "uniform vec4 materialSpecular;\n"
      + "uniform vec4 materialEmissive;\n"
      + "uniform float shininess;\n"
      + "uniform vec4 flatColor;\n"
      + "uniform vec3 ambientLight;\n"
      + "uniform int directionalLightCount;\n"
      + "uniform vec3 directionalLightDirection[" + MAX_LIGHTS + "];\n"
      + "uniform vec3 directionalLightColor[" + MAX_LIGHTS + "];\n"
      + "uniform int pointLightCount;\n"
      + "uniform vec3 pointLightPosition[" + MAX_POINT_LIGHTS + "];\n"
      + "uniform vec3 pointLightColor[" + MAX_POINT_LIGHTS + "];\n"
      + "uniform vec3 pointLightAttenuation[" + MAX_POINT_LIGHTS + "];\n"
      + "uniform int shadowLight;\n"
      + "uniform float shadowTexelSize;\n"
      + "varying vec3 eyePosition;\n"
      + "varying vec3 eyeNormal;\n"
      + "varying vec4 shadowCoordinates;\n"
      + "varying vec4 vertexColor;\n"
      + "float getLitPart(float normalDotLight) {\n"
      + "  vec3 c = shadowCoordinates.xyz / shadowCoordinates.w;\n"
      + "  if (c.x <= 0. || c.x >= 1. || c.y <= 0. || c.y >= 1. || c.z >= 1.) {\n"
      + "    return 1.;\n"
      + "  }\n"
      + "  float slope = sqrt(max(0., 1. - normalDotLight * normalDotLight)) / max(normalDotLight, 0.05);\n"
      + "  float bias = clamp(0.0005 * slope, 0.0002, 0.005);\n"
      + "  float angle = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2832;\n"
      + "  float sum = 0.;\n"
      + "  for (int i = 0; i < 12; i++) {\n"
      + "    float sampleAngle = angle + float(i) * 2.39996;\n"
      + "    float radius = sqrt((float(i) + 0.5) / 12.) * 2.5 * shadowTexelSize;\n"
      + "    sum += shadow2D(shadowMap, vec3(c.xy + vec2(cos(sampleAngle), sin(sampleAngle)) * radius, c.z - bias)).r;\n"
      + "  }\n"
      + "  return sum / 12.;\n"
      + "}\n"
      + "void main() {\n"
      + "  vec4 color;\n"
      + "  vec3 specular = vec3(0.);\n"
      + "  if (lighting) {\n"
      + "    vec3 normal = normalize(eyeNormal);\n"
      + "    if (twoSided && !gl_FrontFacing) {\n"
      + "      normal = -normal;\n"
      + "    }\n"
      + "    vec3 viewer = normalize(-eyePosition);\n"
      + "    vec3 diffuse = vec3(0.);\n"
      + "    for (int i = 0; i < " + MAX_LIGHTS + "; i++) {\n"
      + "      if (i >= directionalLightCount) {\n"
      + "        break;\n"
      + "      }\n"
      + "      vec3 light = directionalLightDirection[i];\n"
      + "      float normalDotLight = dot(normal, light);\n"
      + "      if (normalDotLight > 0.) {\n"
      + "        float lit = i == shadowLight ? getLitPart(normalDotLight) : 1.;\n"
      + "        diffuse += lit * normalDotLight * directionalLightColor[i];\n"
      + "        specular += lit * pow(max(dot(normal, normalize(light + viewer)), 0.), shininess) * directionalLightColor[i];\n"
      + "      }\n"
      + "    }\n"
      + "    for (int i = 0; i < " + MAX_POINT_LIGHTS + "; i++) {\n"
      + "      if (i >= pointLightCount) {\n"
      + "        break;\n"
      + "      }\n"
      + "      vec3 light = pointLightPosition[i] - eyePosition;\n"
      + "      float range = length(light);\n"
      + "      light /= range;\n"
      + "      float normalDotLight = dot(normal, light);\n"
      + "      if (normalDotLight > 0.) {\n"
      + "        vec3 attenuation = pointLightAttenuation[i];\n"
      + "        vec3 lightColor = pointLightColor[i] / (attenuation.x + attenuation.y * range + attenuation.z * range * range);\n"
      + "        diffuse += normalDotLight * lightColor;\n"
      + "        specular += pow(max(dot(normal, normalize(light + viewer)), 0.), shininess) * lightColor;\n"
      + "      }\n"
      + "    }\n"
      + "    vec4 diffuseColor = vertexColors ? vertexColor : materialDiffuse;\n"
      + "    color = vec4(materialEmissive.rgb + ambientLight * materialAmbient.rgb + diffuse * diffuseColor.rgb, materialDiffuse.a);\n"
      + "    color = clamp(color, 0., 1.);\n"
      + "    specular *= materialSpecular.rgb;\n"
      + "  } else {\n"
      + "    color = vertexColors ? vertexColor : flatColor;\n"
      + "  }\n"
      + "  if (textureMode != 0) {\n"
      + "    vec4 texel = texture2D(texture0, gl_TexCoord[0].st);\n"
      + "    if (textureMode == 1) {\n"
      + "      color.rgb = texel.rgb;\n"
      + "      if (textureAlpha) {\n"
      + "        color.a = texel.a;\n"
      + "      }\n"
      + "    } else if (textureMode == 3) {\n"
      + "      color.rgb = mix(color.rgb, texel.rgb, texel.a);\n"
      + "    } else {\n"
      + "      color.rgb *= texel.rgb;\n"
      + "      if (textureAlpha) {\n"
      + "        color.a *= texel.a;\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "  gl_FragColor = vec4(color.rgb + specular, color.a);\n"
      + "}\n";

  private static final String QUAD_VERTEX_SHADER =
        "#version 120\n"
      + "void main() {\n"
      + "  gl_TexCoord[0] = gl_MultiTexCoord0;\n"
      + "  gl_Position = gl_Vertex;\n"
      + "}\n";

  // Scalable ambient obscurance computed from the depth of the frame
  private static final String OCCLUSION_FRAGMENT_SHADER =
        "#version 120\n"
      + "uniform sampler2D depthTexture;\n"
      + "uniform vec4 projection;\n"
      + "uniform vec2 pixelSize;\n"
      + "const float radius = 45.;\n"
      + "const float intensity = 0.3;\n"
      + "vec3 getPosition(vec2 uv) {\n"
      + "  float depth = texture2D(depthTexture, uv).r;\n"
      + "  float z = 2. * projection.z * projection.w\n"
      + "      / (projection.w + projection.z - (2. * depth - 1.) * (projection.w - projection.z));\n"
      + "  return vec3((2. * uv - 1.) * projection.xy * z, -z);\n"
      + "}\n"
      + "void main() {\n"
      + "  vec2 uv = gl_TexCoord[0].st;\n"
      + "  float depth = texture2D(depthTexture, uv).r;\n"
      + "  vec3 position = getPosition(uv);\n"
      + "  float range = -position.z;\n"
      + "  vec2 uvRadius = radius / range / (2. * projection.xy);\n"
      + "  if (depth >= 1. || range > 6000. || uvRadius.x < 2. * pixelSize.x) {\n"
      + "    gl_FragColor = vec4(1.);\n"
      + "    return;\n"
      + "  }\n"
      + "  vec3 right = getPosition(uv + vec2(pixelSize.x, 0.)) - position;\n"
      + "  vec3 left = position - getPosition(uv - vec2(pixelSize.x, 0.));\n"
      + "  vec3 top = getPosition(uv + vec2(0., pixelSize.y)) - position;\n"
      + "  vec3 bottom = position - getPosition(uv - vec2(0., pixelSize.y));\n"
      + "  vec3 normal = normalize(cross(abs(right.z) < abs(left.z) ? right : left,\n"
      + "      abs(top.z) < abs(bottom.z) ? top : bottom));\n"
      + "  float angle = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2832;\n"
      + "  float bias = 1. + 0.004 * range;\n"
      + "  float sum = 0.;\n"
      + "  for (int i = 0; i < 16; i++) {\n"
      + "    float ratio = (float(i) + 0.5) / 16.;\n"
      + "    float sampleAngle = angle + ratio * 6.2832 * 5.;\n"
      + "    vec2 sampleUv = uv + vec2(cos(sampleAngle), sin(sampleAngle)) * ratio * uvRadius;\n"
      + "    vec3 v = getPosition(sampleUv) - position;\n"
      + "    float vv = dot(v, v);\n"
      + "    float f = max(radius * radius - vv, 0.);\n"
      + "    sum += f * f * f * max((dot(v, normal) - bias) / (vv + 1.), 0.);\n"
      + "  }\n"
      + "  float occlusion = max(0., 1. - sum * radius * intensity * 5. / (pow(radius, 6.) * 16.));\n"
      + "  occlusion = mix(occlusion, 1., smoothstep(3000., 6000., range));\n"
      + "  gl_FragColor = vec4(occlusion, occlusion, occlusion, 1.);\n"
      + "}\n";

  // Multiplies the frame by its occlusion, smoothed among the pixels at a close depth
  private static final String COMPOSITION_FRAGMENT_SHADER =
        "#version 120\n"
      + "uniform sampler2D colorTexture;\n"
      + "uniform sampler2D occlusionTexture;\n"
      + "uniform sampler2D depthTexture;\n"
      + "uniform vec2 pixelSize;\n"
      + "uniform vec2 clipDistances;\n"
      + "float getDistance(vec2 uv) {\n"
      + "  float depth = texture2D(depthTexture, uv).r;\n"
      + "  return 2. * clipDistances.x * clipDistances.y\n"
      + "      / (clipDistances.y + clipDistances.x - (2. * depth - 1.) * (clipDistances.y - clipDistances.x));\n"
      + "}\n"
      + "void main() {\n"
      + "  vec2 uv = gl_TexCoord[0].st;\n"
      + "  float range = getDistance(uv);\n"
      + "  float sum = 0.;\n"
      + "  float weights = 0.;\n"
      + "  for (int x = -2; x <= 2; x++) {\n"
      + "    for (int y = -2; y <= 2; y++) {\n"
      + "      vec2 sampleUv = uv + vec2(float(x), float(y)) * pixelSize;\n"
      + "      float weight = max(0.001, 1. - abs(getDistance(sampleUv) - range) / (0.02 * range + 1.));\n"
      + "      sum += texture2D(occlusionTexture, sampleUv).r * weight;\n"
      + "      weights += weight;\n"
      + "    }\n"
      + "  }\n"
      + "  gl_FragColor = vec4(texture2D(colorTexture, uv).rgb * (sum / weights), 1.);\n"
      + "}\n";

  // ---- Data

  /**
   * The description of the scene at a given moment viewed from a camera.
   */
  public static final class Frame {
    private long        generation;
    private List<Item>  opaqueItems = Collections.emptyList();
    private List<Item>  transparentItems = Collections.emptyList();
    private List<Item>  environmentItems = Collections.emptyList();
    private List<Item>  backgroundItems = Collections.emptyList();
    private LightState []      lights = {};
    private LightState []      backgroundLights = {};
    private PointLightState [] pointLights = {};
    private float []    backgroundColor = {0.9f, 0.9f, 0.9f};
    // Matrices stored row by row
    private final double [] viewMatrix = new double [16];
    private final double [] cameraMatrix = new double [16];
    private double      fieldOfView;
    private double      frontClipDistance;
    private double      backClipDistance;

    private Frame() {
    }
  }

  /**
   * The point of view of the last frame drawn on screen.
   */
  private static class ViewState {
    private final double [] viewMatrix;
    private final double [] projectionMatrix;
    private final int       width;
    private final int       height;

    public ViewState(double [] viewMatrix, double [] projectionMatrix, int width, int height) {
      this.viewMatrix = viewMatrix;
      this.projectionMatrix = projectionMatrix;
      this.width = width;
      this.height = height;
    }
  }

  /**
   * The shapes of a child of the objects group.
   */
  private static class ObjectEntry {
    private List<Item> items = Collections.emptyList();
    private List<PointLightState> pointLights = Collections.emptyList();
    private boolean    dirty;
    private long       frame;
  }

  /**
   * A shape to draw. Its description is set by the scene thread
   * and the other fields are used only by the rendering thread.
   */
  private static class Item {
    private Mesh      mesh;
    private State     state;
    private float []  matrix;
    private boolean   pickable;
    private Node      owner;
    private int       lightScopeMask;

    private double [] center;
    private double    radius = -1;
    private double    viewDepth;
    private Object    pointLightsSource;
    private int []    pointLights;

    public boolean isBlended() {
      if (this.state.isBlended()) {
        return true;
      } else {
        GLTexture texture = this.state.glTexture;
        return texture != null && texture.transparent;
      }
    }
  }

  /**
   * An ambient light or a directional light of the scene.
   */
  private static class LightState {
    private final float [] color;
    private final float [] direction;
    private List<Group>    scopes;
    private int            scopeBit;

    public LightState(Color3f color, Vector3f direction) {
      this.color = new float [] {color.x, color.y, color.z, 1};
      // OpenGL expects the direction towards the light
      this.direction = direction != null
          ? new float [] {-direction.x, -direction.y, -direction.z, 0}
          : null;
    }
  }

  /**
   * A light source of the scene, which lights the shapes in a box if it's not <code>null</code>.
   */
  private static class PointLightState {
    private float []  position;
    private float []  color;
    private float []  attenuation;
    private double [] lower;
    private double [] upper;
  }

  /**
   * The lights applied to some shapes in the coordinates of the camera.
   */
  private static class LightSet {
    private final float [] ambient = new float [3];
    private final float [] directions = new float [MAX_LIGHTS * 3];
    private final float [] colors = new float [MAX_LIGHTS * 3];
    private int            count;
    private int            shadowLight = -1;
  }

  /**
   * A frame buffer.
   */
  private static class Target {
    private int     framebuffer;
    private int     colorBuffer;
    private int     depthBuffer;
    private boolean textures;
    private int     width;
    private int     height;
    private int     samples;
  }

  /**
   * A geometry that no shape uses since the given generation of frames.
   */
  private static class ReleasedMesh {
    private final Mesh mesh;
    private final long generation;

    public ReleasedMesh(Mesh mesh, long generation) {
      this.mesh = mesh;
      this.generation = generation;
    }
  }

  /**
   * An OpenGL texture.
   */
  private static class GLTexture {
    private int        id;
    private int        width;
    private int        height;
    private boolean    wrapS;
    private boolean    wrapT;
    private ByteBuffer pixels;
    private volatile boolean transparent;
    private volatile boolean alpha;
    private volatile boolean failed;
    private volatile boolean deleted;
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
    // Texture of the graphics card, managed by the rendering thread
    private GLTexture glTexture;

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
    private volatile int loadState = MESH_NEW;
    private FloatBuffer  data;
    private long         preparedSize;
    private boolean      released;
    private volatile boolean boundsKnown;
    private float []   center = {0, 0, 0};
    private float      radius;
    private float []   lower = {0, 0, 0};
    private float []   upper = {0, 0, 0};
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
     * Computes the bounds of this mesh from the coordinates of its geometry, without
     * preparing its vertices, and returns <code>false</code> if they can't be read.
     */
    public synchronized boolean computeBounds() {
      if (!this.boundsKnown) {
        try {
          GeometryArray geometry = this.geometry;
          int format = geometry.getVertexFormat();
          float [] coordinates;
          int offset = 0;
          int size = 3;
          if ((format & GeometryArray.BY_REFERENCE) != 0) {
            if ((format & GeometryArray.USE_NIO_BUFFER) != 0) {
              return false;
            } else if ((format & GeometryArray.INTERLEAVED) != 0) {
              coordinates = geometry.getInterleavedVertices();
              size = coordinates.length / Math.max(1, geometry.getVertexCount());
              offset = size - 3;
            } else {
              coordinates = geometry.getCoordRefFloat();
            }
          } else {
            coordinates = new float [geometry.getVertexCount() * 3];
            geometry.getCoordinates(0, coordinates);
          }
          if (coordinates == null || coordinates.length < size) {
            return false;
          }
          float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
          float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
          for (int i = offset; i + 2 < coordinates.length; i += size) {
            float x = coordinates [i];
            float y = coordinates [i + 1];
            float z = coordinates [i + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
          }
          this.center = new float [] {(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
          this.lower = new float [] {minX, minY, minZ};
          this.upper = new float [] {maxX, maxY, maxZ};
          float dx = maxX - minX;
          float dy = maxY - minY;
          float dz = maxZ - minZ;
          this.radius = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) / 2;
          this.boundsKnown = true;
        } catch (RuntimeException ex) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns the approximate size in bytes of the vertices of this mesh.
     */
    public long getEstimatedSize() {
      try {
        int count = this.geometry instanceof IndexedGeometryArray
            ? ((IndexedGeometryArray)this.geometry).getValidIndexCount()
            : this.geometry.getValidVertexCount();
        return count * 48L;
      } catch (RuntimeException ex) {
        return 0;
      }
    }

    /**
     * Returns the coordinates of the vertices of the triangles of this mesh.
     */
    public synchronized float [] getPositions() {
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
    public synchronized FloatBuffer createVertices() {
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
        this.lower = new float [] {minX, minY, minZ};
        this.upper = new float [] {maxX, maxY, maxZ};
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        this.radius = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) / 2;
      }
      this.boundsKnown = true;
      return vertices;
    }
  }
}
