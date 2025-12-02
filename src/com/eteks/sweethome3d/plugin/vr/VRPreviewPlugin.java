/*
 * VRPreviewPlugin.java
 *
 * A lightweight plugin that opens the current home in a full-screen 3D-only
 * preview window, suitable for desktop streaming to a VR headset (e.g., Quest
 * via Steam desktop/Link). It reuses the standard Swing view to avoid touching
 * the existing Java3D pipeline.
 */
package com.eteks.sweethome3d.plugin.vr;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.swing.HomePane;
import com.eteks.sweethome3d.viewcontroller.HomeController;

public class VRPreviewPlugin extends Plugin {
  @Override
  public PluginAction[] getActions() {
    return new PluginAction [] {new OpenVRPreviewAction(getHome())};
  }

  private static final String HTML_TEMPLATE =
      "<!DOCTYPE html>\\n"
      + "<html lang=\\\"en\\\">\\n"
      + "<head>\\n"
      + "  <meta charset=\\\"utf-8\\\" />\\n"
      + "  <title>Sweet Home 3D - WebXR Preview</title>\\n"
      + "  <style>body{margin:0;overflow:hidden;background:#111;}#overlay{position:fixed;top:10px;left:10px;color:#fff;font-family:Arial,sans-serif;z-index:10;padding:8px 12px;background:rgba(0,0,0,0.4);border-radius:4px;}canvas{display:block;}</style>\\n"
      + "</head>\\n"
      + "<body>\\n"
      + "<div id=\\\"overlay\\\">Click \\\\u201cEnter VR\\\\u201d then use thumbsticks/WASD to move. Drag to orbit in desktop mode.</div>\\n"
      + "<script type=\\\"module\\\">\\n"
      + "import * as THREE from 'https://unpkg.com/three@0.155.0/build/three.module.js';\\n"
      + "import {MTLLoader} from 'https://unpkg.com/three@0.155.0/examples/jsm/loaders/MTLLoader.js';\\n"
      + "import {OBJLoader} from 'https://unpkg.com/three@0.155.0/examples/jsm/loaders/OBJLoader.js';\\n"
      + "import {OrbitControls} from 'https://unpkg.com/three@0.155.0/examples/jsm/controls/OrbitControls.js';\\n"
      + "import {VRButton} from 'https://unpkg.com/three@0.155.0/examples/jsm/webxr/VRButton.js';\\n"
      + "const scene=new THREE.Scene();scene.background=new THREE.Color(0x111111);\\n"
      + "const camera=new THREE.PerspectiveCamera(70,window.innerWidth/window.innerHeight,0.1,2000);\\n"
      + "camera.position.set(0,1.6,3);\\n"
      + "const renderer=new THREE.WebGLRenderer({antialias:true});renderer.setSize(window.innerWidth,window.innerHeight);renderer.xr.enabled=true;document.body.appendChild(renderer.domElement);\\n"
      + "document.body.appendChild(VRButton.createButton(renderer));\\n"
      + "const controls=new OrbitControls(camera,renderer.domElement);controls.target.set(0,1,0);controls.update();\\n"
      + "scene.add(new THREE.HemisphereLight(0xffffff,0x444444,0.6));const dirLight=new THREE.DirectionalLight(0xffffff,0.8);dirLight.position.set(3,10,10);scene.add(dirLight);\\n"
      + "const grid=new THREE.GridHelper(50,50,0x333333,0x222222);scene.add(grid);\\n"
      + "const clock=new THREE.Clock();let obj=null;let keys={};\\n"
      + "window.addEventListener('keydown',e=>{keys[e.code]=true;});window.addEventListener('keyup',e=>{keys[e.code]=false;});\\n"
      + "function loadModel(){const mtlLoader=new MTLLoader();mtlLoader.setResourcePath('./');mtlLoader.setPath('./');mtlLoader.load('scene.mtl',materials=>{materials.preload();const loader=new OBJLoader();loader.setMaterials(materials);loader.setPath('./');loader.load('scene.obj',o=>{obj=o;obj.traverse(c=>{if(c.isMesh){c.castShadow=true;c.receiveShadow=true;}});scene.add(obj);centerModel();},err=>{console.error('OBJ load error',err);});},err=>{console.error('MTL load error',err);});}\\n"
      + "function centerModel(){if(!obj)return;const box=new THREE.Box3().setFromObject(obj);const size=box.getSize(new THREE.Vector3());const center=box.getCenter(new THREE.Vector3());obj.position.sub(center);camera.position.set(0,Math.max(1.6,size.y),Math.max(size.z*1.5,3));controls.target.set(0,size.y*0.5,0);controls.update();}\\n"
      + "function handleXRMovement(delta){const session=renderer.xr.getSession();if(!session)return;const speed=2.0;const dir=new THREE.Vector3();const cameraObj=renderer.xr.getCamera(camera);if(!cameraObj)return;session.inputSources.forEach(src=>{const gp=src.gamepad;if(!gp||gp.axes.length<2)return;const ax=gp.axes;const moveX=ax[0];const moveZ=ax[1];dir.set(0,0,-1).applyQuaternion(cameraObj.quaternion);dir.y=0;dir.normalize();const right=new THREE.Vector3().crossVectors(dir,new THREE.Vector3(0,1,0)).normalize();cameraObj.position.addScaledVector(dir,moveZ*speed*delta);cameraObj.position.addScaledVector(right,moveX*speed*delta);});}\\n"
      + "function handleDesktopMovement(delta){const speed=3.0;const moveZ=(keys['KeyW']? -1:0)+(keys['KeyS']?1:0);const moveX=(keys['KeyA']? -1:0)+(keys['KeyD']?1:0);if(moveX===0&&moveZ===0)return;const dir=new THREE.Vector3();dir.set(0,0,-1).applyQuaternion(camera.quaternion);dir.y=0;dir.normalize();const right=new THREE.Vector3().crossVectors(dir,new THREE.Vector3(0,1,0)).normalize();camera.position.addScaledVector(dir,moveZ*speed*delta);camera.position.addScaledVector(right,moveX*speed*delta);controls.target.addScaledVector(dir,moveZ*speed*delta);controls.target.addScaledVector(right,moveX*speed*delta);controls.update();}\\n"
      + "function animate(){const delta=clock.getDelta();handleXRMovement(delta);handleDesktopMovement(delta);renderer.render(scene,camera);}renderer.setAnimationLoop(animate);\\n"
      + "window.addEventListener('resize',()=>{camera.aspect=window.innerWidth/window.innerHeight;camera.updateProjectionMatrix();renderer.setSize(window.innerWidth,window.innerHeight);});\\n"
      + "loadModel();\\n"
      + "</script>\\n"
      + "</body>\\n"
      + "</html>\\n";

  private class OpenVRPreviewAction extends PluginAction {
    private final Home home;
    private ExecutorService serverExecutor;
    private HttpServer server;

    OpenVRPreviewAction(Home home) {
      this.home = home;
      putPropertyValue(Property.NAME, "VR Preview (Full Screen)");
      putPropertyValue(Property.MENU, "Tools");
      setEnabled(true);
    }

    @Override
    public void execute() {
      // Export to OBJ silently, generate WebXR page, and launch browser.
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          SwingViewFactory viewFactory = new SwingViewFactory();
          HomeController controller = new HomeController(home, getUserPreferences(), viewFactory);
          try {
            File exportDir = createExportDir();
            File objFile = new File(exportDir, "scene.obj");
            ((HomePane)controller.getView()).exportToOBJ(objFile.getAbsolutePath());
            writeWebXRHtml(exportDir);
            startServerAndOpen(exportDir);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      });
    }

    private File createExportDir() throws IOException {
      String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
      File dir = Files.createTempDirectory("sh3d-webxr-" + stamp).toFile();
      dir.deleteOnExit();
      return dir;
    }

    private void writeWebXRHtml(File dir) throws IOException {
      Files.write(new File(dir, "index.html").toPath(),
          HTML_TEMPLATE.getBytes(StandardCharsets.UTF_8));
    }

    private void startServerAndOpen(File dir) throws IOException {
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/", new StaticFileHandler(dir));
      serverExecutor = Executors.newSingleThreadExecutor();
      server.setExecutor(serverExecutor);
      server.start();
      int port = server.getAddress().getPort();
      URI uri = URI.create("http://localhost:" + port + "/index.html");
      openInBrowser(uri);
    }
  }

  private static class StaticFileHandler implements HttpHandler {
    private final File root;
    StaticFileHandler(File root) {
      this.root = root;
    }
    public void handle(HttpExchange exchange) throws IOException {
      try {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
          path = "/index.html";
        }
        File target = new File(root, path.replaceFirst("^/", ""));
        if (!target.getCanonicalPath().startsWith(root.getCanonicalPath()) || !target.exists()) {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
        byte[] data = Files.readAllBytes(target.toPath());
        String contentType = guessContentType(target.getName());
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
      } finally {
        exchange.close();
      }
    }

    private String guessContentType(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
      if (lower.endsWith(".js")) return "application/javascript";
      if (lower.endsWith(".mtl")) return "text/plain";
      if (lower.endsWith(".obj")) return "text/plain";
      if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
      if (lower.endsWith(".png")) return "image/png";
      return "application/octet-stream";
    }
  }

  private void openInBrowser(URI uri) {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(uri);
        return;
      }
    } catch (Exception ignore) {
      // fallback below
    }
    for (String cmd : Arrays.asList("xdg-open", "gio open")) {
      try {
        String[] parts = cmd.split(" ");
        String[] full = new String[parts.length + 1];
        System.arraycopy(parts, 0, full, 0, parts.length);
        full[parts.length] = uri.toString();
        new ProcessBuilder(full).inheritIO().start();
        return;
      } catch (IOException ignore) {
      }
    }
    System.out.println("Open in browser: " + uri);
  }
}
