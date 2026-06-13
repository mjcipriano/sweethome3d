package com.eteks.sweethome3d.plugin.webxr;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    + "    #overlay { position:fixed; top:10px; left:10px; background:rgba(0,0,0,0.7); padding:10px 12px; border-radius:6px; max-width:460px; line-height:1.4; font-size:13px; }\n"
    + "    #scale { margin-top:6px; font-weight:bold; }\n"
    + "    #controls { margin-top:6px; color:#a7d7ff; }\n"
    + "    #controllers { margin-top:4px; font-size:12px; color:#b6e1ff; }\n"
    + "    #log { margin-top:6px; white-space:pre-wrap; font-size:12px; color:#cde4ff; }\n"
    + "    canvas { display:block; }\n"
    + "    a { color:#9cf; }\n"
    + "  </style>\n"
    + "</head>\n"
    + "<body>\n"
    + "<div id=\"overlay\">\n"
    + "  <div><strong>WebXR Preview</strong></div>\n"
    + "  <div>Enter VR, then move with left stick or WASD + mouse.</div>\n"
    + "  <div id=\"controls\">Right stick up/down or +/- to scale. R or left grip recenters to the floor.</div>\n"
    + "  <div id=\"path\">Export dir: __EXPORT_PATH__</div>\n"
    + "  <div id=\"files\">OBJ: pending | MTL: pending</div>\n"
    + "  <div id=\"scale\">Scale: pending</div>\n"
    + "  <div id=\"controllers\">Controllers: detecting...</div>\n"
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
    + "const scaleEl=document.getElementById('scale');\n"
    + "const controllersEl=document.getElementById('controllers');\n"
    + "function log(msg){console.log(msg); if(logEl){logEl.textContent+=msg+'\\n';}}\n"
    + "function setStatus(msg){statusEl.textContent=msg; log(msg);}\n"
    + "const scene=new THREE.Scene();scene.background=new THREE.Color(0x111111);\n"
    + "const camera=new THREE.PerspectiveCamera(70,window.innerWidth/window.innerHeight,0.05,4000);\n"
    + "camera.position.set(0,1.6,3);\n"
    + "const renderer=new THREE.WebGLRenderer({antialias:true});\n"
    + "renderer.outputEncoding=THREE.sRGBEncoding;\n"
    + "renderer.setSize(window.innerWidth,window.innerHeight);\n"
    + "renderer.xr.enabled=true;\n"
    + "renderer.xr.setReferenceSpaceType('local-floor');\n"
    + "document.body.appendChild(renderer.domElement);\n"
    + "document.body.appendChild(VRButton.createButton(renderer));\n"
    + "const player=new THREE.Group();\n"
    + "const modelGroup=new THREE.Group();\n"
    + "scene.add(modelGroup);\n"
    + "scene.add(player);\n"
    + "player.add(camera);\n"
    + "const controls=new OrbitControls(camera,renderer.domElement);\n"
    + "controls.target.set(0,1.4,0);controls.update();\n"
    + "scene.add(new THREE.HemisphereLight(0xffffff,0x444444,0.6));\n"
    + "const dirLight=new THREE.DirectionalLight(0xffffff,0.8);dirLight.position.set(3,10,10);scene.add(dirLight);\n"
    + "scene.add(new THREE.GridHelper(50,50,0x333333,0x222222));\n"
    + "scene.add(new THREE.AxesHelper(2));\n"
    + "const clock=new THREE.Clock();let obj=null;const keys={};\n"
    + "let modelBounds=null;let modelScale=0.01;const SCALE_MIN=0.001;const SCALE_MAX=10;const gripState={left:false,right:false};\n"
    + "const tmpVec=new THREE.Vector3();const tmpRight=new THREE.Vector3();const up=new THREE.Vector3(0,1,0);\n"
    + "const controllers=[renderer.xr.getController(0),renderer.xr.getController(1)];\n"
    + "controllers.forEach((c,i)=>{c.userData.index=i;c.addEventListener('connected',e=>{c.userData.gamepad=e.data.gamepad;c.userData.handedness=e.data.handedness||e.data.inputSource?.handedness||(i===0?'left':'right');updateControllerDisplay();log('Controller '+c.userData.handedness+' connected');});c.addEventListener('disconnected',()=>{delete c.userData.gamepad;updateControllerDisplay();log('Controller '+(c.userData.handedness||c.userData.index)+' disconnected');});player.add(c);});\n"
    + "function updateScaleDisplay(){if(!scaleEl)return;scaleEl.textContent='Scale: '+modelScale.toFixed(3)+'x (default 0.010 = cm->m)';}\n"
    + "function setScale(next,{recenter=false}={}){const clamped=Math.min(Math.max(next,SCALE_MIN),SCALE_MAX);modelScale=clamped;modelGroup.scale.setScalar(clamped);updateScaleDisplay();if(recenter){placePlayerOnFloor();}}\n"
    + "function nudgeScale(multiplier){setScale(modelScale*multiplier);}\n"
    + "function handleScaleAxis(value,delta){const dead=0.25;if(Math.abs(value)<dead)return;const change=1+value*delta*1.5;nudgeScale(change);}\n"
    + "function placePlayerOnFloor(){const depth=modelBounds?modelBounds.getSize(tmpVec).z*modelScale:0;const spawnZ=Math.max(depth*0.55,2.0);player.position.set(0,0,spawnZ);controls.target.set(0,1.4,0);controls.update();}\n"
    + "function prepareModel(){if(!obj)return;modelGroup.clear();modelGroup.add(obj);modelBounds=new THREE.Box3().setFromObject(obj);const center=modelBounds.getCenter(new THREE.Vector3());const minY=modelBounds.min.y;obj.position.set(-center.x,-minY,-center.z);modelBounds=new THREE.Box3().setFromObject(obj);setScale(modelScale,{recenter:true});setStatus('Model ready');}\n"
    + "function fallbackCube(){if(obj)return;const geo=new THREE.BoxGeometry(1,1,1);const mat=new THREE.MeshStandardMaterial({color:0x44aa88});obj=new THREE.Mesh(geo,mat);prepareModel();setScale(1,{recenter:true});setStatus('Showing fallback cube');}\n"
    + "function movePlayer(moveX,moveZ,quat,delta,speed){if(Math.abs(moveX)<0.05&&Math.abs(moveZ)<0.05)return;tmpVec.set(0,0,-1).applyQuaternion(quat);tmpVec.y=0;if(tmpVec.lengthSq()===0)return;tmpVec.normalize();tmpRight.crossVectors(tmpVec,up).normalize();player.position.addScaledVector(tmpVec,moveZ*speed*delta);player.position.addScaledVector(tmpRight,moveX*speed*delta);controls.target.addScaledVector(tmpVec,moveZ*speed*delta);controls.target.addScaledVector(tmpRight,moveX*speed*delta);controls.update();}\n"
    + "function updateControllerDisplay(){if(!controllersEl)return;const active=controllers.filter(c=>c.userData.gamepad).length;controllersEl.textContent='Controllers: '+active+'/2';}\n"
    + "function handleXRMovement(delta){const session=renderer.xr.getSession();if(!session)return;const xrCamera=renderer.xr.getCamera(camera);let used=false;controllers.forEach(ctrl=>{const gp=ctrl.userData.gamepad;if(!gp||gp.axes.length<2)return;const moveSource=(ctrl.userData.handedness==='left'||controllers.length===1);if(moveSource){movePlayer(gp.axes[0],-gp.axes[1],xrCamera.quaternion,delta,2.2);}else{handleScaleAxis(gp.axes[1],delta);}const gripPressed=gp.buttons&&gp.buttons[1]&&gp.buttons[1].pressed;if(ctrl.userData.handedness==='left'&&gripPressed&&!gripState.left){placePlayerOnFloor();}gripState[ctrl.userData.handedness||'left']=!!gripPressed;used=true;});if(!used){session.inputSources.forEach(src=>{const gp=src.gamepad;if(!gp||gp.axes.length<2)return;const ax=gp.axes;const moveSource=(src.handedness==='left'||session.inputSources.length===1);if(moveSource){movePlayer(ax[0],-ax[1],xrCamera.quaternion,delta,2.2);}else{handleScaleAxis(ax[1],delta);}const gripPressed=gp.buttons&&gp.buttons[1]&&gp.buttons[1].pressed;if(src.handedness==='left'&&gripPressed&&!gripState.left){placePlayerOnFloor();}gripState[src.handedness]=!!gripPressed;});}}\n"
    + "function handleDesktopMovement(delta){const speed=3.0;const moveZ=(keys['KeyW']?1:0)+(keys['KeyS']?-1:0);const moveX=(keys['KeyA']?-1:0)+(keys['KeyD']?1:0);if(moveX===0&&moveZ===0)return;movePlayer(moveX,moveZ,camera.quaternion,delta,speed);}\n"
    + "window.addEventListener('keydown',e=>{keys[e.code]=true;if(e.code==='Equal'||e.code==='NumpadAdd'){nudgeScale(1.05);}else if(e.code==='Minus'||e.code==='NumpadSubtract'){nudgeScale(0.95);}else if(e.code==='KeyR'){placePlayerOnFloor();}});\n"
    + "window.addEventListener('keyup',e=>{keys[e.code]=false;});\n"
    + "renderer.xr.addEventListener('sessionstart',()=>{const session=renderer.xr.getSession();if(session){session.addEventListener('inputsourceschange',()=>{updateControllerDisplay();});updateControllerDisplay();}placePlayerOnFloor();});\n"
    + "const manager=new THREE.LoadingManager();\n"
    + "manager.onError=url=>{setStatus('Error loading '+url);fallbackCube();};\n"
    + "manager.onLoad=()=>{setStatus('Model loaded');};\n"
    + "function loadModel(){setStatus('Loading OBJ/MTL...');const mtlLoader=new MTLLoader(manager);mtlLoader.setPath('./');mtlLoader.load('scene.mtl',materials=>{materials.preload();const loader=new OBJLoader(manager);loader.setMaterials(materials);loader.setPath('./');loader.load('scene.obj',o=>{obj=o;o.traverse(c=>{if(c.isMesh){c.castShadow=true;c.receiveShadow=true;}});prepareModel();},undefined,err=>{setStatus('OBJ load error');log(err);fallbackCube();});},undefined,err=>{setStatus('MTL load error');log(err);fallbackCube();});}\n"
    + "function checkFiles(){return Promise.all([fetch('scene.obj',{method:'GET'}),fetch('scene.mtl',{method:'GET'})]).then(([o,m])=>{filesEl.textContent='OBJ: '+o.status+' | MTL: '+m.status;return o.ok&&m.ok;}).catch(e=>{setStatus('Fetch failed');log(e);return false;});}\n"
    + "function animate(){const delta=clock.getDelta();handleXRMovement(delta);handleDesktopMovement(delta);renderer.render(scene,camera);}renderer.setAnimationLoop(animate);\n"
    + "window.addEventListener('resize',()=>{camera.aspect=window.innerWidth/window.innerHeight;camera.updateProjectionMatrix();renderer.setSize(window.innerWidth,window.innerHeight);});\n"
    + "checkFiles().then(ok=>{if(ok){loadModel();}else{setStatus('Files missing - fallback cube');fallbackCube();}});\n"
    + "setTimeout(()=>{if(!obj){fallbackCube();}},2000);\n"
    + "updateScaleDisplay();\n"
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
    WebXRPreviewServer server = new WebXRPreviewServer(exportDir);
    server.start();
    openInBrowser(new URL(server.getLocalUrl()), indexFile);
    showConnectInstructions(server);
  }

  /**
   * Shows how to reach the preview from a headset on the local network
   * (Meta Quest 2/3, or any WebXR browser).
   */
  private void showConnectInstructions(WebXRPreviewServer server) {
    StringBuilder message = new StringBuilder("WebXR preview is running.\n\n")
        .append("Desktop browser: ").append(server.getLocalUrl()).append('\n');
    List<String> lanUrls = server.getLanUrls();
    if (lanUrls.isEmpty()) {
      message.append("\nNo LAN address with https is available, so a headset can't connect.\n")
          .append("Check that this computer is on the same network as the headset.");
    } else {
      message.append("\nOn your headset (Quest 2/3: open the Browser app), go to:\n");
      for (String url : lanUrls) {
        message.append("    ").append(url).append('\n');
      }
      message.append("\nThe browser will warn about the self-signed certificate once -\n")
          .append("choose Advanced > Proceed. Then press \"Enter VR\".\n\n")
          .append("Move with the left stick, scale the model with the right stick,\n")
          .append("and press the left grip to recenter on the floor.\n\n")
          .append("SteamVR/PC headsets: open the desktop URL above in Chrome or Edge\n")
          .append("with the headset connected (WebXR uses the active OpenXR runtime).");
    }
    showInfo(message.toString());
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
