package com.eteks.sweethome3d.plugin.webxr;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.j3d.Ground3D;
import com.eteks.sweethome3d.j3d.OBJWriter;
import com.eteks.sweethome3d.j3d.Object3DBranchFactory;
import com.eteks.sweethome3d.viewcontroller.Object3DFactory;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.InterruptedRecorderException;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;
import com.eteks.sweethome3d.viewcontroller.View;
import com.eteks.sweethome3d.swing.HomeComponent3D;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javax.media.j3d.Node;

/**
 * WebXR preview plugin: exports the current home to OBJ/MTL, serves a local
 * WebXR page, and opens it in the default browser.
 */
public class WebXRPreviewPlugin extends Plugin {
  private static final String HTML_TEMPLATE =
      "<!DOCTYPE html>\n"
    + "<html lang=\"en\">\n"
    + "<head>\n"
    + "  <meta charset=\"utf-8\" />\n"
    + "  <title>Sweet Home 3D - WebXR Preview</title>\n"
    + "  <style>\n"
    + "    body { margin:0; overflow:hidden; background:#111; color:#e8f5ff; font-family:Arial,sans-serif; }\n"
    + "    #overlay { position:fixed; top:10px; left:10px; background:rgba(0,0,0,0.7); padding:10px 12px; border-radius:6px; max-width:420px; line-height:1.4; font-size:13px; }\n"
    + "    #log { margin-top:6px; white-space:pre-wrap; font-size:12px; color:#cde4ff; }\n"
    + "    canvas { display:block; }\n"
    + "    a { color:#9cf; }\n"
    + "  </style>\n"
    + "</head>\n"
    + "<body>\n"
    + "<div id=\"overlay\">\n"
    + "  <div><strong>WebXR Preview</strong></div>\n"
    + "  <div>Click \"Enter VR\" then use thumbsticks or WASD + mouse to move.</div>\n"
    + "  <div id=\"path\">Export dir: __EXPORT_PATH__</div>\n"
    + "  <div id=\"files\">OBJ: pending | MTL: pending</div>\n"
    + "  <div id=\"status\"></div>\n"
    + "  <div id=\"log\"></div>\n"
    + "</div>\n"
    + "<script type=\"importmap\">\n"
    + "{ \"imports\": { \"three\": \"https://unpkg.com/three@0.155.0/build/three.module.js\" } }\n"
    + "</script>\n"
    + "<script type=\"module\">\n"
    + "import * as THREE from 'https://unpkg.com/three@0.155.0/build/three.module.js';\n"
    + "import {MTLLoader} from 'https://unpkg.com/three@0.155.0/examples/jsm/loaders/MTLLoader.js';\n"
    + "import {OBJLoader} from 'https://unpkg.com/three@0.155.0/examples/jsm/loaders/OBJLoader.js';\n"
    + "import {OrbitControls} from 'https://unpkg.com/three@0.155.0/examples/jsm/controls/OrbitControls.js';\n"
    + "import {VRButton} from 'https://unpkg.com/three@0.155.0/examples/jsm/webxr/VRButton.js';\n"
    + "const statusEl=document.getElementById('status');\n"
    + "const filesEl=document.getElementById('files');\n"
    + "const logEl=document.getElementById('log');\n"
    + "function log(msg){console.log(msg); if(logEl){logEl.textContent+=msg+'\\n';}}\n"
    + "function setStatus(msg){statusEl.textContent=msg; log(msg);}\n"
    + "const scene=new THREE.Scene();scene.background=new THREE.Color(0x111111);\n"
    + "const camera=new THREE.PerspectiveCamera(70,window.innerWidth/window.innerHeight,0.1,2000);\n"
    + "camera.position.set(0,1.6,3);\n"
    + "const renderer=new THREE.WebGLRenderer({antialias:true});\n"
    + "renderer.outputEncoding=THREE.sRGBEncoding;\n"
    + "renderer.setSize(window.innerWidth,window.innerHeight);\n"
    + "renderer.xr.enabled=true;\n"
    + "document.body.appendChild(renderer.domElement);\n"
    + "document.body.appendChild(VRButton.createButton(renderer));\n"
    + "const controls=new OrbitControls(camera,renderer.domElement);\n"
    + "controls.target.set(0,1,0);controls.update();\n"
    + "scene.add(new THREE.HemisphereLight(0xffffff,0x444444,0.6));\n"
    + "const dirLight=new THREE.DirectionalLight(0xffffff,0.8);dirLight.position.set(3,10,10);scene.add(dirLight);\n"
    + "scene.add(new THREE.GridHelper(50,50,0x333333,0x222222));\n"
    + "scene.add(new THREE.AxesHelper(2));\n"
    + "const clock=new THREE.Clock();let obj=null;const keys={};\n"
    + "window.addEventListener('keydown',e=>{keys[e.code]=true;});\n"
    + "window.addEventListener('keyup',e=>{keys[e.code]=false;});\n"
    + "function centerModel(){if(!obj)return;const box=new THREE.Box3().setFromObject(obj);const size=box.getSize(new THREE.Vector3());const center=box.getCenter(new THREE.Vector3());obj.position.sub(center);camera.position.set(0,Math.max(1.6,size.y),Math.max(size.z*1.5,3));controls.target.set(0,size.y*0.5,0);controls.update();}\n"
    + "function fallbackCube(){if(obj)return;const geo=new THREE.BoxGeometry(1,1,1);const mat=new THREE.MeshStandardMaterial({color:0x44aa88});obj=new THREE.Mesh(geo,mat);scene.add(obj);centerModel();setStatus('Showing fallback cube');}\n"
    + "function handleXRMovement(delta){const session=renderer.xr.getSession();if(!session)return;const speed=2.2;const dir=new THREE.Vector3();const cameraObj=renderer.xr.getCamera(camera);if(!cameraObj)return;session.inputSources.forEach(src=>{const gp=src.gamepad;if(!gp||gp.axes.length<2)return;const ax=gp.axes;const moveX=ax[0];const moveZ=ax[1];dir.set(0,0,-1).applyQuaternion(cameraObj.quaternion);dir.y=0;dir.normalize();const right=new THREE.Vector3().crossVectors(dir,new THREE.Vector3(0,1,0)).normalize();cameraObj.position.addScaledVector(dir,moveZ*speed*delta);cameraObj.position.addScaledVector(right,moveX*speed*delta);});}\n"
    + "function handleDesktopMovement(delta){const speed=3.0;const moveZ=(keys['KeyW']?-1:0)+(keys['KeyS']?1:0);const moveX=(keys['KeyA']?-1:0)+(keys['KeyD']?1:0);if(moveX===0&&moveZ===0)return;const dir=new THREE.Vector3();dir.set(0,0,-1).applyQuaternion(camera.quaternion);dir.y=0;dir.normalize();const right=new THREE.Vector3().crossVectors(dir,new THREE.Vector3(0,1,0)).normalize();camera.position.addScaledVector(dir,moveZ*speed*delta);camera.position.addScaledVector(right,moveX*speed*delta);controls.target.addScaledVector(dir,moveZ*speed*delta);controls.target.addScaledVector(right,moveX*speed*delta);controls.update();}\n"
    + "const manager=new THREE.LoadingManager();\n"
    + "manager.onError=url=>{setStatus('Error loading '+url);fallbackCube();};\n"
    + "manager.onLoad=()=>{setStatus('Model loaded');};\n"
    + "function loadModel(){setStatus('Loading OBJ/MTL...');const mtlLoader=new MTLLoader(manager);mtlLoader.setPath('./');mtlLoader.load('scene.mtl',materials=>{materials.preload();const loader=new OBJLoader(manager);loader.setMaterials(materials);loader.setPath('./');loader.load('scene.obj',o=>{obj=o;o.traverse(c=>{if(c.isMesh){c.castShadow=true;c.receiveShadow=true;}});scene.add(o);centerModel();},undefined,err=>{setStatus('OBJ load error');log(err);fallbackCube();});},undefined,err=>{setStatus('MTL load error');log(err);fallbackCube();});}\n"
    + "function checkFiles(){return Promise.all([fetch('scene.obj',{method:'GET'}),fetch('scene.mtl',{method:'GET'})]).then(([o,m])=>{filesEl.textContent='OBJ: '+o.status+' | MTL: '+m.status;return o.ok&&m.ok;}).catch(e=>{setStatus('Fetch failed');log(e);return false;});}\n"
    + "function animate(){const delta=clock.getDelta();handleXRMovement(delta);handleDesktopMovement(delta);renderer.render(scene,camera);}renderer.setAnimationLoop(animate);\n"
    + "window.addEventListener('resize',()=>{camera.aspect=window.innerWidth/window.innerHeight;camera.updateProjectionMatrix();renderer.setSize(window.innerWidth,window.innerHeight);});\n"
    + "checkFiles().then(ok=>{if(ok){loadModel();}else{setStatus('Files missing - fallback cube');fallbackCube();}});\n"
    + "setTimeout(()=>{if(!obj){fallbackCube();}},2000);\n"
    + "</script>\n"
    + "</body>\n"
    + "</html>\n";

  private final PluginAction[] actions;

  public WebXRPreviewPlugin() {
    this.actions = new PluginAction[] { new WebXRPreviewAction() };
  }

  @Override
  public PluginAction [] getActions() {
    return this.actions;
  }

  private class WebXRPreviewAction extends PluginAction {
    WebXRPreviewAction() {
      putPropertyValue(Property.NAME, "WebXR Preview...");
      putPropertyValue(Property.SHORT_DESCRIPTION, "Export current home to OBJ and open a WebXR viewer");
      putPropertyValue(Property.MENU, "Tools");
      setEnabled(true);
    }

    @Override
    public void execute() {
      final WebXRPreviewAction self = this;
      self.setEnabled(false);
      Thread worker = new Thread(new Runnable() {
        public void run() {
          try {
            launchPreview();
          } catch (Exception ex) {
            showError("WebXR Preview failed: " + ex.getMessage(), ex);
          } finally {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                self.setEnabled(true);
              }
            });
          }
        }
      }, "WebXRPreview");
      worker.setDaemon(true);
      worker.start();
    }
  }

  private void launchPreview() throws Exception {
    Home home = getHome();
    if (home == null) {
      throw new IllegalStateException("No home is loaded.");
    }

    File exportDir = createExportDir();
    exportHome(exportDir, home);
    File indexFile = writeWebXRHtml(exportDir);
    HttpServer server = startServer(exportDir);
    URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/index.html");
    openInBrowser(url, indexFile);
  }

  private File createExportDir() throws IOException {
    String baseName = "sh3d-webxr-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    File exportDir = new File(System.getProperty("java.io.tmpdir"), baseName);
    if (!exportDir.exists() && !exportDir.mkdirs()) {
      throw new IOException("Failed to create export dir " + exportDir);
    }
    return exportDir;
  }

  private void exportHome(File exportDir, Home home) throws RecorderException {
    File objFile = new File(exportDir, "scene.obj");
    String header = "Exported " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    View view3D = null;
    HomeController3D controller3D = getHomeController() != null ? getHomeController().getHomeController3D() : null;
    if (controller3D != null) {
      view3D = controller3D.getView();
    }
    Object3DFactory factory;
    if (view3D instanceof HomeComponent3D) {
      factory = ((HomeComponent3D)view3D).getObject3DFactory();
    } else {
      factory = new Object3DBranchFactory();
    }
    Home clonedHome = cloneHomeInEDT(home);
    OBJExportHelper.exportHomeToFile(clonedHome, objFile.getAbsolutePath(), header, true, factory);
  }

  private File writeWebXRHtml(File exportDir) throws IOException {
    String html = HTML_TEMPLATE.replace("__EXPORT_PATH__", exportDir.getAbsolutePath());
    File index = new File(exportDir, "index.html");
    OutputStream out = null;
    try {
      out = new java.io.FileOutputStream(index);
      out.write(html.getBytes(StandardCharsets.UTF_8));
    } finally {
      if (out != null) {
        out.close();
      }
    }
    return index;
  }

  private HttpServer startServer(final File root) throws IOException {
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 0);
    final HttpServer server = HttpServer.create(address, 0);
    final String rootPath = root.getCanonicalPath();
    server.createContext("/", new HttpHandler() {
      public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null || "/".equals(path)) {
          path = "/index.html";
        }
        path = path.replace('\\', '/');
        if (path.startsWith("/")) {
          path = path.substring(1);
        }
        File target = new File(root, path);
        String canonical = target.getCanonicalPath();
        if (!canonical.startsWith(rootPath)) {
          sendNotFound(exchange);
          return;
        }
        if (!target.exists() || target.isDirectory()) {
          sendNotFound(exchange);
          return;
        }
        byte[] data = readAllBytes(target);
        Headers headers = exchange.getResponseHeaders();
        String contentType = guessContentType(target.getName());
        if (contentType != null) {
          headers.set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(200, data.length);
        OutputStream body = exchange.getResponseBody();
        body.write(data);
        body.close();
      }
    });
    ExecutorService executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.start();
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        server.stop(0);
      }
    }));
    return server;
  }

  private void sendNotFound(HttpExchange exchange) throws IOException {
    byte[] data = "Not Found".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(404, data.length);
    OutputStream body = exchange.getResponseBody();
    body.write(data);
    body.close();
  }

  private String guessContentType(String name) {
    String lower = name.toLowerCase(Locale.US);
    if (lower.endsWith(".html") || lower.endsWith(".htm")) {
      return "text/html; charset=utf-8";
    } else if (lower.endsWith(".js")) {
      return "application/javascript; charset=utf-8";
    } else if (lower.endsWith(".css")) {
      return "text/css; charset=utf-8";
    } else if (lower.endsWith(".mtl") || lower.endsWith(".obj") || lower.endsWith(".txt")) {
      return "text/plain; charset=utf-8";
    } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (lower.endsWith(".png")) {
      return "image/png";
    }
    String probe = URLConnection.guessContentTypeFromName(name);
    return probe;
  }

  private byte[] readAllBytes(File file) throws IOException {
    FileInputStream in = null;
    try {
      in = new FileInputStream(file);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }

  private void openInBrowser(URL url, File indexFile) {
    boolean opened = false;
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        try {
          desktop.browse(URI.create(url.toString()));
          opened = true;
        } catch (Exception ex) {
          // Fallback below
        }
      }
    }
    if (!opened) {
      showInfo("Open this URL in a WebXR-capable browser:\n" + url.toString()
          + "\n\nFiles are in " + indexFile.getParent());
    }
  }

  private void showError(final String message, Exception ex) {
    ex.printStackTrace();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JOptionPane.showMessageDialog(null, message, "WebXR Preview", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private void showInfo(final String message) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JOptionPane.showMessageDialog(null, message, "WebXR Preview", JOptionPane.INFORMATION_MESSAGE);
      }
    });
  }

  private Home cloneHomeInEDT(final Home home) throws RecorderException {
    if (EventQueue.isDispatchThread()) {
      return home.clone();
    }
    try {
      final AtomicReference<Home> cloned = new AtomicReference<Home>();
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          cloned.set(home.clone());
        }
      });
      return cloned.get();
    } catch (InterruptedException ex) {
      throw new InterruptedRecorderException(ex.getMessage());
    } catch (InvocationTargetException ex) {
      throw new RecorderException("Couldn't clone home", ex.getCause());
    }
  }

  /**
   * Helper that mirrors HomePane OBJ export logic.
   */
  private static class OBJExportHelper {
    private static void exportHomeToFile(Home home, String objFile, String header,
                                         boolean exportAllToOBJ, Object3DFactory object3dFactory) throws RecorderException {
      OBJWriter writer = null;
      boolean exportInterrupted = false;
      try {
        writer = new OBJWriter(objFile, header, -1);

        List<Selectable> exportedItems = new ArrayList<Selectable>(exportAllToOBJ
            ? home.getSelectableViewableItems()
            : home.getSelectedItems());
        List<Selectable> furnitureInGroups = new ArrayList<Selectable>();
        for (java.util.Iterator<Selectable> it = exportedItems.iterator(); it.hasNext();) {
          Selectable selectable = it.next();
          if (selectable instanceof HomeFurnitureGroup) {
            it.remove();
            for (HomePieceOfFurniture piece : ((HomeFurnitureGroup)selectable).getAllFurniture()) {
              if (!(piece instanceof HomeFurnitureGroup)) {
                furnitureInGroups.add(piece);
              }
            }
          }
        }
        exportedItems.addAll(furnitureInGroups);

        List<Selectable> emptySelection = java.util.Collections.emptyList();
        home.setSelectedItems(emptySelection);
        if (exportAllToOBJ) {
          java.awt.geom.Rectangle2D homeBounds = getExportedHomeBounds(home);
          if (homeBounds != null) {
            Ground3D groundNode = new Ground3D(home,
                (float)homeBounds.getX(), (float)homeBounds.getY(),
                (float)homeBounds.getWidth(), (float)homeBounds.getHeight(), true);
            writer.writeNode(groundNode, "ground");
          }
        } else if (home.isAllLevelsSelection()) {
          for (Level level : home.getLevels()) {
            if (level.isViewable()) {
              level.setVisible(true);
            }
          }
        }

        int i = 0;
        for (Selectable item : exportedItems) {
          Node node = (Node)object3dFactory.createObject3D(home, item, true);
          if (node != null) {
            if (item instanceof HomePieceOfFurniture) {
              writer.writeNode(node);
            } else if (!(item instanceof DimensionLine)) {
              writer.writeNode(node, item.getClass().getSimpleName().toLowerCase(Locale.US) + "_" + ++i);
            }
          }
        }
      } catch (java.io.InterruptedIOException ex) {
        exportInterrupted = true;
        throw new InterruptedRecorderException("Export to " + objFile + " interrupted");
      } catch (IOException ex) {
        throw new RecorderException("Couldn't export to OBJ in " + objFile, ex);
      } finally {
        if (writer != null) {
          try {
            writer.close();
            if (exportInterrupted) {
              new File(objFile).delete();
            }
          } catch (IOException ex) {
            throw new RecorderException("Couldn't export to OBJ in " + objFile, ex);
          }
        }
      }
    }

    private static java.awt.geom.Rectangle2D getExportedHomeBounds(Home home) {
      java.awt.geom.Rectangle2D homeBounds = updateObjectsBounds(null, home.getWalls());
      for (HomePieceOfFurniture piece : getVisibleFurniture(home.getFurniture())) {
        for (float [] point : piece.getPoints()) {
          if (homeBounds == null) {
            homeBounds = new java.awt.geom.Rectangle2D.Float(point [0], point [1], 0, 0);
          } else {
            homeBounds.add(point [0], point [1]);
          }
        }
      }
      return updateObjectsBounds(homeBounds, home.getRooms());
    }

    private static List<HomePieceOfFurniture> getVisibleFurniture(List<HomePieceOfFurniture> furniture) {
      List<HomePieceOfFurniture> visibleFurniture = new ArrayList<HomePieceOfFurniture>(furniture.size());
      for (HomePieceOfFurniture piece : furniture) {
        if (piece.isVisible()
            && (piece.getLevel() == null
                || piece.getLevel().isViewable())) {
          if (piece instanceof HomeFurnitureGroup) {
            visibleFurniture.addAll(getVisibleFurniture(((HomeFurnitureGroup)piece).getFurniture()));
          } else {
            visibleFurniture.add(piece);
          }
        }
      }
      return visibleFurniture;
    }

    private static java.awt.geom.Rectangle2D updateObjectsBounds(java.awt.geom.Rectangle2D objectBounds,
                                                                 Collection<? extends Selectable> items) {
      for (Selectable item : items) {
        if (!(item instanceof Elevatable)
            || ((Elevatable)item).getLevel() == null
            || ((Elevatable)item).getLevel().isViewableAndVisible()) {
          for (float [] point : item.getPoints()) {
            if (objectBounds == null) {
              objectBounds = new java.awt.geom.Rectangle2D.Float(point [0], point [1], 0, 0);
            } else {
              objectBounds.add(point [0], point [1]);
            }
          }
        }
      }
      return objectBounds;
    }
  }
}
