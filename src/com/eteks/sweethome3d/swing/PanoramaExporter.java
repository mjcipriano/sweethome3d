/*
 * PanoramaExporter.java 16 Nov 2026
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
package com.eteks.sweethome3d.swing;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.j3d.PhotoRenderer;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;

/**
 * Exports a 360 degree panorama of a home: a picture of everything around
 * the virtual visitor (an equirectangular image computed by the photo
 * renderer with its spherical lens), with a web page to look around in it,
 * also in a VR headset. A panorama is one picture, so it loads at once on
 * any device, whatever the size of the home.
 * @author Sweet Home 3D
 */
public final class PanoramaExporter {
  public static final String IMAGE_NAME = "panorama.jpg";
  public static final String PAGE_NAME = "index.html";

  private PanoramaExporter() {
  }

  /**
   * Returns the camera of the panorama: at the place of the virtual visitor
   * and looking the way he looks, horizontally.
   */
  static Camera createPanoramaCamera(Home home) {
    Camera visitor = home.getObserverCamera();
    Camera camera = new Camera(visitor.getX(), visitor.getY(), visitor.getZ(), visitor.getYaw(), 0,
        visitor.getFieldOfView());
    camera.setTime(home.getCamera().getTime());
    camera.setLens(Camera.Lens.SPHERICAL);
    return camera;
  }

  /**
   * Renders the panorama seen by the virtual visitor of <code>home</code>.
   * @param width the width of the image, twice its height
   */
  public static BufferedImage render(Home home, int width, boolean bestQuality) throws IOException {
    Home renderedHome = home.clone();
    BufferedImage image = new BufferedImage(width, width / 2, BufferedImage.TYPE_INT_RGB);
    PhotoRenderer renderer = new PhotoRenderer(renderedHome,
        bestQuality ? PhotoRenderer.Quality.HIGH : PhotoRenderer.Quality.LOW);
    try {
      renderer.render(image, createPanoramaCamera(renderedHome), null);
    } finally {
      renderer.dispose();
    }
    return image;
  }

  /**
   * Writes the picture and its web page in <code>folder</code>.
   */
  public static void write(BufferedImage image, File folder, String title) throws IOException {
    if (!folder.isDirectory() && !folder.mkdirs()) {
      throw new IOException("Can't create " + folder);
    }
    java.io.ByteArrayOutputStream jpeg = new java.io.ByteArrayOutputStream();
    if (!ImageIO.write(image, "jpg", jpeg)) {
      throw new IOException("Can't write a JPEG image");
    }
    OutputStream out = new FileOutputStream(new File(folder, IMAGE_NAME));
    try {
      out.write(jpeg.toByteArray());
    } finally {
      out.close();
    }
    // The page carries the picture itself, so it opens from the disk with a double click:
    // a browser refuses to draw in 3D a picture read from another file of the disk
    out = new FileOutputStream(new File(folder, PAGE_NAME));
    try {
      out.write(getPage(title, "data:image/jpeg;base64,"
          + java.util.Base64.getEncoder().encodeToString(jpeg.toByteArray())).getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }

  /**
   * Returns the web page that shows the panorama: drag to look around,
   * wheel to zoom, and Enter VR in a headset when the page is served with https.
   */
  static String getPage(String title) {
    return getPage(title, IMAGE_NAME);
  }

  /**
   * Returns the web page that shows the panorama at the given address,
   * which may be the picture itself as a <code>data:</code> address.
   */
  static String getPage(String title, String imageAddress) {
    String escapedTitle = title == null ? "Panorama" : title.replace("&", "&amp;").replace("<", "&lt;");
    return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
        + "<title>" + escapedTitle + "</title>\n"
        + "<style>html,body{margin:0;height:100%;overflow:hidden;background:#111;font-family:sans-serif}"
        + "#hint{position:absolute;left:12px;top:10px;color:#fff;background:rgba(0,0,0,.55);padding:6px 10px;"
        + "border-radius:6px;font-size:14px}</style>\n</head>\n<body>\n"
        + "<div id=\"hint\">" + escapedTitle + " - drag to look around, wheel to zoom</div>\n"
        + "<script type=\"importmap\">\n{ \"imports\": {\n"
        + "    \"three\": \"https://unpkg.com/three@0.155.0/build/three.module.js\",\n"
        + "    \"three/addons/\": \"https://unpkg.com/three@0.155.0/examples/jsm/\"\n} }\n</script>\n"
        + "<script type=\"module\">\n"
        + "import * as THREE from 'three';\n"
        + "import { VRButton } from 'three/addons/webxr/VRButton.js';\n"
        + "const renderer = new THREE.WebGLRenderer({antialias: true});\n"
        + "renderer.setPixelRatio(window.devicePixelRatio);\n"
        + "renderer.setSize(window.innerWidth, window.innerHeight);\n"
        + "renderer.xr.enabled = true;\n"
        + "document.body.appendChild(renderer.domElement);\n"
        + "document.body.appendChild(VRButton.createButton(renderer));\n"
        + "const scene = new THREE.Scene();\n"
        + "const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 100);\n"
        + "const geometry = new THREE.SphereGeometry(50, 64, 32);\n"
        + "// Seen from inside\n"
        + "geometry.scale(-1, 1, 1);\n"
        + "const texture = new THREE.TextureLoader().load('" + imageAddress + "');\n"
        + "texture.colorSpace = THREE.SRGBColorSpace;\n"
        + "const sphere = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial({map: texture}));\n"
        + "// The middle of the picture, where the visitor looks, is straight ahead\n"
        + "sphere.rotation.y = -Math.PI / 2;\n"
        + "scene.add(sphere);\n"
        + "let yaw = 0, pitch = 0, dragging = false, lastX = 0, lastY = 0;\n"
        + "const element = renderer.domElement;\n"
        + "element.addEventListener('pointerdown', ev => { dragging = true; lastX = ev.clientX; lastY = ev.clientY; });\n"
        + "window.addEventListener('pointerup', () => { dragging = false; });\n"
        + "window.addEventListener('pointermove', ev => {\n"
        + "  if (dragging) {\n"
        + "    yaw += (ev.clientX - lastX) * 0.005 * camera.fov / 75;\n"
        + "    pitch = Math.max(-1.5, Math.min(1.5, pitch + (ev.clientY - lastY) * 0.005 * camera.fov / 75));\n"
        + "    lastX = ev.clientX; lastY = ev.clientY;\n"
        + "  }\n"
        + "});\n"
        + "element.addEventListener('wheel', ev => {\n"
        + "  camera.fov = Math.max(25, Math.min(100, camera.fov + ev.deltaY * 0.05));\n"
        + "  camera.updateProjectionMatrix();\n"
        + "});\n"
        + "window.addEventListener('resize', () => {\n"
        + "  camera.aspect = window.innerWidth / window.innerHeight;\n"
        + "  camera.updateProjectionMatrix();\n"
        + "  renderer.setSize(window.innerWidth, window.innerHeight);\n"
        + "});\n"
        + "window.panorama = {ready: false};\n"
        + "renderer.setAnimationLoop(() => {\n"
        + "  if (!renderer.xr.isPresenting) {\n"
        + "    camera.rotation.set(pitch, yaw, 0, 'YXZ');\n"
        + "  }\n"
        + "  renderer.render(scene, camera);\n"
        + "  window.panorama.ready = !!texture.image;\n"
        + "});\n"
        + "</script>\n</body>\n</html>\n";
  }

  /**
   * Asks for a folder, renders the panorama in a thread and writes it there.
   */
  public static void showExportDialog(final JComponent parent, final Home home) {
    if (Boolean.getBoolean("com.eteks.sweethome3d.no3D")) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "The 3D libraries aren't available.");
      return;
    }
    Object [] sizes = {"2048 x 1024 - fast", "4096 x 2048 - sharper, about 4 times longer",
                       "4096 x 2048, best quality - much longer"};
    final Object size = JOptionPane.showInputDialog(SwingUtilities.getRootPane(parent),
        "<html>A panorama is a picture of everything around the <b>virtual visitor</b>:<br>"
        + "place him in the plan first (he is the figure of the virtual visit).<br>"
        + "The result is a folder with the picture and a web page to look around,<br>"
        + "also in a VR headset.<br><br>Size of the picture:</html>",
        "Export 360° panorama", JOptionPane.PLAIN_MESSAGE, null, sizes, sizes [0]);
    if (size == null) {
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Folder that will receive the panorama");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showSaveDialog(SwingUtilities.getRootPane(parent)) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File parentFolder = chooser.getSelectedFile();
    File chosenFolder = new File(parentFolder, "panorama");
    for (int i = 2; chosenFolder.exists(); i++) {
      chosenFolder = new File(parentFolder, "panorama-" + i);
    }
    final File folder = chosenFolder;
    final JDialog waitDialog = new JOptionPane("Computing the panorama... This takes from seconds to minutes.",
        JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object [0])
        .createDialog(SwingUtilities.getRootPane(parent), "Export 360° panorama");
    waitDialog.setModal(false);
    waitDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    waitDialog.setVisible(true);
    final int width = size == sizes [0] ? 2048 : 4096;
    final boolean bestQuality = size == sizes [2];
    final String title = home.getName() != null
        ? new File(home.getName()).getName().replaceFirst("\\.[^.]*$", "") : "Panorama";
    Thread thread = new Thread("Sweet Home 3D panorama") {
        @Override
        public void run() {
          String error = null;
          try {
            write(render(home, width, bestQuality), folder, title);
          } catch (IOException ex) {
            error = ex.getMessage();
          } catch (RuntimeException ex) {
            error = String.valueOf(ex);
          } catch (LinkageError ex) {
            error = "the 3D libraries aren't available";
          } catch (OutOfMemoryError ex) {
            error = "not enough memory for this home at this size";
          }
          final String message = error;
          SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                waitDialog.dispose();
                if (message != null) {
                  JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
                      "The panorama couldn't be computed: " + message);
                } else {
                  try {
                    java.awt.Desktop.getDesktop().browse(new File(folder, PAGE_NAME).toURI());
                  } catch (Exception ex) {
                    // The message below tells where it is
                  }
                  JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
                      "<html>The panorama is in<br>" + folder + "<br><br>Double-click its index.html to look around, "
                      + "on any computer: the page<br>contains the picture and needs no web server. "
                      + "Put it on a web site (https)<br>to share it as a link and to see it in a VR headset.</html>");
                }
              }
            });
        }
      };
    thread.setDaemon(true);
    thread.setPriority(Thread.MIN_PRIORITY);
    thread.start();
  }
}
