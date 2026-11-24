/*
 * PackageDependenciesTest.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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
package com.eteks.sweethome3d.junit;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import junit.framework.TestCase;

/**
 * Tests if dependencies between Sweet Home 3D packages are met.
 * @author Emmanuel Puybaret
 */
public class PackageDependenciesTest extends TestCase {
  /**
   * Tests that the package dependencies constraint is met for the analyzed packages.
   */
  public void testPackageDependencies() throws IOException {
    List<String> packageFilter = new ArrayList<String>();
    // Ignore Java packages
    packageFilter.add("java.*");
    // JDK HTTP server used by the local MCP server
    packageFilter.add("com.sun.net.httpserver");
    // Ignore JUnit tests
    packageFilter.add("com.eteks.sweethome3d.junit");
    // The VR preview is packaged as a plugin and may use the public application API.
    packageFilter.add("com.eteks.sweethome3d.plugin.vr");
    // The WebXR preview is a plug-in built in the application jar: nothing depends on it
    // (PluginManager finds it by the name of its resource) and it uses the public application API.
    packageFilter.add("com.eteks.sweethome3d.plugin.webxr");

    File classesDirectory = new File(System.getProperty("sweethome3d.testClasses", "classes"));

    DependencyConstraint constraint = new DependencyConstraint();
    // Sweet Home 3D packages
    JavaPackage sweetHome3DModel = constraint.addPackage("com.eteks.sweethome3d.model");
    JavaPackage sweetHome3DTools = constraint.addPackage("com.eteks.sweethome3d.tools");
    JavaPackage sweetHome3DPlugin = constraint.addPackage("com.eteks.sweethome3d.plugin");
    JavaPackage sweetHome3DViewController = constraint.addPackage("com.eteks.sweethome3d.viewcontroller");
    JavaPackage sweetHome3DSwing = constraint.addPackage("com.eteks.sweethome3d.swing");
    JavaPackage sweetHome3DJava3D = constraint.addPackage("com.eteks.sweethome3d.j3d");
    JavaPackage sweetHome3DIO = constraint.addPackage("com.eteks.sweethome3d.io");
    JavaPackage sweetHome3DApplication = constraint.addPackage("com.eteks.sweethome3d");
    JavaPackage sweetHome3DApplet = constraint.addPackage("com.eteks.sweethome3d.applet");
    // Swing components packages
    JavaPackage swing = constraint.addPackage("javax.swing");
    JavaPackage swingEvent = constraint.addPackage("javax.swing.event");
    JavaPackage swingUndo = constraint.addPackage("javax.swing.undo");
    JavaPackage swingText = constraint.addPackage("javax.swing.text");
    JavaPackage swingTextHtml = constraint.addPackage("javax.swing.text.html");
    JavaPackage swingTable = constraint.addPackage("javax.swing.table");
    JavaPackage swingTree = constraint.addPackage("javax.swing.tree");
    JavaPackage swingBorder = constraint.addPackage("javax.swing.border");
    JavaPackage swingColorchooser = constraint.addPackage("javax.swing.colorchooser");
    JavaPackage swingFilechooser = constraint.addPackage("javax.swing.filechooser");
    JavaPackage swingPlaf = constraint.addPackage("javax.swing.plaf");
    JavaPackage swingPlafBasic = constraint.addPackage("javax.swing.plaf.basic");
    JavaPackage swingPlafSynth = constraint.addPackage("javax.swing.plaf.synth");
    JavaPackage imageio = constraint.addPackage("javax.imageio");
    JavaPackage imageioStream = constraint.addPackage("javax.imageio.stream");
    // Java 3D
    JavaPackage java3d = constraint.addPackage("javax.media.j3d");
    JavaPackage vecmath = constraint.addPackage("javax.vecmath");
    JavaPackage sun3dLoaders = constraint.addPackage("com.sun.j3d.loaders");
    JavaPackage sun3dLoadersLw3d = constraint.addPackage("com.sun.j3d.loaders.lw3d");
    JavaPackage sun3dUtilsGeometry = constraint.addPackage("com.sun.j3d.utils.geometry");
    JavaPackage sun3dUtilsPicking = constraint.addPackage("com.sun.j3d.utils.picking");
    JavaPackage sun3dUtilsImage = constraint.addPackage("com.sun.j3d.utils.image");
    JavaPackage sun3dUtilsUniverse = constraint.addPackage("com.sun.j3d.utils.universe");
    JavaPackage sun3dExpSwing = constraint.addPackage("com.sun.j3d.exp.swing");
    // JOGL used by the OpenGL renderer of the 3D view
    JavaPackage jogl = constraint.addPackage("com.jogamp.opengl");
    JavaPackage joglFixedFunc = constraint.addPackage("com.jogamp.opengl.fixedfunc");
    JavaPackage joglAwt = constraint.addPackage("com.jogamp.opengl.awt");
    JavaPackage joglUtilAwt = constraint.addPackage("com.jogamp.opengl.util.awt");
    // XML
    JavaPackage xmlParsers = constraint.addPackage("javax.xml.parsers");
    JavaPackage xmlSax = constraint.addPackage("org.xml.sax");
    JavaPackage xmlSaxHelpers = constraint.addPackage("org.xml.sax.helpers");
    // Accessibility
    JavaPackage accessibility = constraint.addPackage("javax.accessibility");
    // SunFlow
    JavaPackage sunflow = constraint.addPackage("org.sunflow");
    JavaPackage sunflowCore = constraint.addPackage("org.sunflow.core");
    JavaPackage sunflowCoreLight = constraint.addPackage("org.sunflow.core.light");
    JavaPackage sunflowCorePrimitive = constraint.addPackage("org.sunflow.core.primitive");
    JavaPackage sunflowImage = constraint.addPackage("org.sunflow.image");
    JavaPackage sunflowMath = constraint.addPackage("org.sunflow.math");
    JavaPackage sunflowSystem = constraint.addPackage("org.sunflow.system");
    JavaPackage sunflowSystemUI = constraint.addPackage("org.sunflow.system.ui");
    // Java JNLP
    JavaPackage jnlp = constraint.addPackage("javax.jnlp");
    // Mac OS X specific interfaces
    JavaPackage eawt = constraint.addPackage("com.apple.eawt");
    JavaPackage eio = constraint.addPackage("com.apple.eio");

    // Describe dependencies : com.eteks.sweethome3d.model don't have any dependency on
    // other packages, IO and View/Controller packages ignore each other
    // and Swing components and Java 3D use are isolated in sweetHome3DSwing
    sweetHome3DTools.dependsUpon(sweetHome3DModel);
    sweetHome3DTools.dependsUpon(eio);

    sweetHome3DViewController.dependsUpon(sweetHome3DModel);
    sweetHome3DViewController.dependsUpon(sweetHome3DTools);
    sweetHome3DViewController.dependsUpon(swingEvent);
    sweetHome3DViewController.dependsUpon(swingUndo);
    sweetHome3DViewController.dependsUpon(swingText);
    sweetHome3DViewController.dependsUpon(swingTextHtml);
    sweetHome3DViewController.dependsUpon(xmlParsers);
    sweetHome3DViewController.dependsUpon(xmlSax);
    sweetHome3DViewController.dependsUpon(xmlSaxHelpers);

    sweetHome3DPlugin.dependsUpon(sweetHome3DModel);
    sweetHome3DPlugin.dependsUpon(sweetHome3DTools);
    sweetHome3DPlugin.dependsUpon(sweetHome3DViewController);
    sweetHome3DPlugin.dependsUpon(swingUndo);

    sweetHome3DJava3D.dependsUpon(sweetHome3DModel);
    sweetHome3DJava3D.dependsUpon(sweetHome3DTools);
    sweetHome3DJava3D.dependsUpon(sweetHome3DViewController);
    sweetHome3DJava3D.dependsUpon(java3d);
    sweetHome3DJava3D.dependsUpon(vecmath);
    sweetHome3DJava3D.dependsUpon(sun3dLoaders);
    sweetHome3DJava3D.dependsUpon(sun3dLoadersLw3d);
    sweetHome3DJava3D.dependsUpon(sun3dUtilsGeometry);
    sweetHome3DJava3D.dependsUpon(sun3dUtilsImage);
    sweetHome3DJava3D.dependsUpon(sun3dUtilsUniverse);
    sweetHome3DJava3D.dependsUpon(imageio);
    sweetHome3DJava3D.dependsUpon(swing);
    sweetHome3DJava3D.dependsUpon(sunflow);
    sweetHome3DJava3D.dependsUpon(sunflowCore);
    sweetHome3DJava3D.dependsUpon(sunflowCoreLight);
    sweetHome3DJava3D.dependsUpon(sunflowCorePrimitive);
    sweetHome3DJava3D.dependsUpon(sunflowImage);
    sweetHome3DJava3D.dependsUpon(sunflowMath);
    sweetHome3DJava3D.dependsUpon(sunflowSystem);
    sweetHome3DJava3D.dependsUpon(sunflowSystemUI);
    sweetHome3DJava3D.dependsUpon(xmlParsers);
    sweetHome3DJava3D.dependsUpon(xmlSax);
    sweetHome3DJava3D.dependsUpon(xmlSaxHelpers);
    sweetHome3DJava3D.dependsUpon(jogl);
    sweetHome3DJava3D.dependsUpon(joglFixedFunc);

    sweetHome3DSwing.dependsUpon(sweetHome3DModel);
    sweetHome3DSwing.dependsUpon(sweetHome3DTools);
    sweetHome3DSwing.dependsUpon(sweetHome3DPlugin);
    sweetHome3DSwing.dependsUpon(sweetHome3DViewController);
    sweetHome3DSwing.dependsUpon(sweetHome3DJava3D);
    sweetHome3DSwing.dependsUpon(swing);
    sweetHome3DSwing.dependsUpon(swingEvent);
    sweetHome3DSwing.dependsUpon(swingText);
    sweetHome3DSwing.dependsUpon(swingTextHtml);
    sweetHome3DSwing.dependsUpon(swingTable);
    sweetHome3DSwing.dependsUpon(swingTree);
    sweetHome3DSwing.dependsUpon(swingBorder);
    sweetHome3DSwing.dependsUpon(swingColorchooser);
    sweetHome3DSwing.dependsUpon(swingFilechooser);
    sweetHome3DSwing.dependsUpon(swingPlaf);
    sweetHome3DSwing.dependsUpon(swingPlafBasic);
    sweetHome3DSwing.dependsUpon(swingPlafSynth);
    sweetHome3DSwing.dependsUpon(accessibility);
    sweetHome3DSwing.dependsUpon(imageio);
    sweetHome3DSwing.dependsUpon(imageioStream);
    sweetHome3DSwing.dependsUpon(java3d);
    sweetHome3DSwing.dependsUpon(vecmath);
    sweetHome3DSwing.dependsUpon(sun3dUtilsGeometry);
    sweetHome3DSwing.dependsUpon(sun3dUtilsPicking);
    sweetHome3DSwing.dependsUpon(sun3dUtilsUniverse);
    sweetHome3DSwing.dependsUpon(sun3dExpSwing);
    sweetHome3DSwing.dependsUpon(jogl);
    sweetHome3DSwing.dependsUpon(joglFixedFunc);
    sweetHome3DSwing.dependsUpon(joglAwt);
    sweetHome3DSwing.dependsUpon(joglUtilAwt);
    sweetHome3DSwing.dependsUpon(jnlp);

    sweetHome3DIO.dependsUpon(sweetHome3DModel);
    sweetHome3DIO.dependsUpon(sweetHome3DTools);
    sweetHome3DIO.dependsUpon(xmlParsers);
    sweetHome3DIO.dependsUpon(xmlSax);
    sweetHome3DIO.dependsUpon(xmlSaxHelpers);

    // Describe application and applet assembly packages
    sweetHome3DApplication.dependsUpon(sweetHome3DModel);
    sweetHome3DApplication.dependsUpon(sweetHome3DTools);
    sweetHome3DApplication.dependsUpon(sweetHome3DPlugin);
    sweetHome3DApplication.dependsUpon(sweetHome3DViewController);
    sweetHome3DApplication.dependsUpon(sweetHome3DJava3D);
    sweetHome3DApplication.dependsUpon(sweetHome3DSwing);
    sweetHome3DApplication.dependsUpon(sweetHome3DIO);
    sweetHome3DApplication.dependsUpon(swing);
    sweetHome3DApplication.dependsUpon(swingEvent);
    sweetHome3DApplication.dependsUpon(swingBorder);
    sweetHome3DApplication.dependsUpon(swingPlafBasic);
    sweetHome3DApplication.dependsUpon(imageio);
    sweetHome3DApplication.dependsUpon(java3d);
    sweetHome3DApplication.dependsUpon(sun3dExpSwing);
    sweetHome3DApplication.dependsUpon(eawt);
    sweetHome3DApplication.dependsUpon(jnlp);

    sweetHome3DApplet.dependsUpon(sweetHome3DModel);
    sweetHome3DApplet.dependsUpon(sweetHome3DTools);
    sweetHome3DApplet.dependsUpon(sweetHome3DPlugin);
    sweetHome3DApplet.dependsUpon(sweetHome3DViewController);
    sweetHome3DApplet.dependsUpon(sweetHome3DJava3D);
    sweetHome3DApplet.dependsUpon(sweetHome3DSwing);
    sweetHome3DApplet.dependsUpon(sweetHome3DIO);
    sweetHome3DApplet.dependsUpon(swing);
    sweetHome3DApplet.dependsUpon(swingEvent);
    sweetHome3DApplet.dependsUpon(swingTable);
    sweetHome3DApplet.dependsUpon(jnlp);

    Map<String, Set<String>> actualDependencies = getPackageDependencies(classesDirectory, packageFilter);
    assertFalse("No class in " + classesDirectory, actualDependencies.isEmpty());
    Map<String, Set<String>> expectedDependencies = constraint.getDependencies();

    assertTrue(getDependencyMismatchMessage(expectedDependencies, actualDependencies),
        expectedDependencies.equals(actualDependencies));
  }

  private String getDependencyMismatchMessage(Map<String, Set<String>> expectedDependencies,
                                              Map<String, Set<String>> actualDependencies) {
    StringBuilder message = new StringBuilder("Dependency mismatch");
    if (!expectedDependencies.keySet().equals(actualDependencies.keySet())) {
      message.append("\nExpected packages: ").append(expectedDependencies.keySet());
      message.append("\nActual packages: ").append(actualDependencies.keySet());
    }
    for (Map.Entry<String, Set<String>> expected : expectedDependencies.entrySet()) {
      Set<String> actual = actualDependencies.get(expected.getKey());
      if (actual == null) {
        message.append("\nMissing package: ").append(expected.getKey());
      } else if (!expected.getValue().equals(actual)) {
        message.append("\n").append(expected.getKey()).append(" outgoing");
        message.append("\n  expected: ").append(expected.getValue());
        message.append("\n  actual:   ").append(actual);
      }
    }
    return message.toString();
  }

  /**
   * Returns the packages of the classes found in <code>directory</code> and the ones they use,
   * each one with the other packages its classes use. The packages which match
   * <code>packageFilter</code> are ignored, the ones used as well as the ones found.
   */
  private Map<String, Set<String>> getPackageDependencies(File directory,
                                                          List<String> packageFilter) throws IOException {
    Map<String, Set<String>> dependencies = new TreeMap<String, Set<String>>();
    List<File> classFiles = new ArrayList<File>();
    addClassFiles(directory, classFiles);
    for (File classFile : classFiles) {
      List<String> classNames = new ArrayList<String>();
      String className = readClassNames(classFile, classNames);
      String packageName = getPackageName(className);
      if (isFiltered(packageName, packageFilter)) {
        continue;
      }
      Set<String> packageDependencies = dependencies.get(packageName);
      if (packageDependencies == null) {
        packageDependencies = new TreeSet<String>();
        dependencies.put(packageName, packageDependencies);
      }
      for (String usedClassName : classNames) {
        String usedPackageName = getPackageName(usedClassName);
        if (!usedPackageName.equals(packageName)
            && !isFiltered(usedPackageName, packageFilter)) {
          packageDependencies.add(usedPackageName);
          if (!dependencies.containsKey(usedPackageName)) {
            dependencies.put(usedPackageName, new TreeSet<String>());
          }
        }
      }
    }
    return dependencies;
  }

  private void addClassFiles(File directory, List<File> classFiles) {
    File [] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          addClassFiles(file, classFiles);
        } else if (file.getName().endsWith(".class")) {
          classFiles.add(file);
        }
      }
    }
  }

  /**
   * Returns <code>true</code> if a package is ignored: a filter that ends with
   * <code>*</code> matches the packages whose name starts with what precedes it.
   */
  private boolean isFiltered(String packageName, List<String> packageFilter) {
    for (String filter : packageFilter) {
      if (filter.endsWith("*")
            ? packageName.startsWith(filter.substring(0, filter.length() - 1))
            : packageName.equals(filter)) {
        return true;
      }
    }
    return false;
  }

  private String getPackageName(String className) {
    int index = className.lastIndexOf('/');
    return index == -1 ? "" : className.substring(0, index).replace('/', '.');
  }

  /**
   * Reads the constant pool, the fields and the methods of a compiled class, as the Java
   * Virtual Machine Specification describes them, and adds to <code>classNames</code> the
   * names of the classes cited there: the classes it calls, extends, implements, throws,
   * creates or casts to, and the types of the fields and of the methods it declares.
   * The types that appear only in the descriptors of the members of other classes it uses
   * aren't counted: the class doesn't name them.
   * @return the name of the class with <code>/</code> between its packages
   */
  private String readClassNames(File classFile, List<String> classNames) throws IOException {
    DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(classFile)));
    try {
      if (in.readInt() != 0xCAFEBABE) {
        throw new IOException(classFile + " isn't a class file");
      }
      in.readUnsignedShort(); // Minor version
      in.readUnsignedShort(); // Major version
      int constantCount = in.readUnsignedShort();
      String [] texts = new String [constantCount];
      int [] classNameIndices = new int [constantCount];
      for (int i = 1; i < constantCount; i++) {
        int tag = in.readUnsignedByte();
        switch (tag) {
          case 1 : // Utf8
            texts [i] = in.readUTF();
            break;
          case 3 : // Integer
          case 4 : // Float
            in.readInt();
            break;
          case 5 : // Long
          case 6 : // Double
            in.readLong();
            // Takes two entries
            i++;
            break;
          case 7 : // Class
            classNameIndices [i] = in.readUnsignedShort();
            break;
          case 8 : // String
          case 19 : // Module
          case 20 : // Package
            in.readUnsignedShort();
            break;
          case 9 : // Fieldref
          case 10 : // Methodref
          case 11 : // InterfaceMethodref
          case 17 : // Dynamic
          case 18 : // InvokeDynamic
            in.readUnsignedShort();
            in.readUnsignedShort();
            break;
          case 12 : // NameAndType
            in.readUnsignedShort();
            in.readUnsignedShort();
            break;
          case 15 : // MethodHandle
            in.readUnsignedByte();
            in.readUnsignedShort();
            break;
          case 16 : // MethodType
            in.readUnsignedShort();
            break;
          default :
            throw new IOException("Unknown constant " + tag + " in " + classFile);
        }
      }
      in.readUnsignedShort(); // Access flags
      String className = texts [classNameIndices [in.readUnsignedShort()]];
      in.readUnsignedShort(); // Super class, cited among the constants
      int interfaceCount = in.readUnsignedShort();
      for (int i = 0; i < interfaceCount; i++) {
        in.readUnsignedShort();
      }
      // Fields then methods
      for (int members = 0; members < 2; members++) {
        int memberCount = in.readUnsignedShort();
        for (int i = 0; i < memberCount; i++) {
          in.readUnsignedShort(); // Access flags
          in.readUnsignedShort(); // Name
          addDescriptorClassNames(texts [in.readUnsignedShort()], classNames);
          int attributeCount = in.readUnsignedShort();
          for (int j = 0; j < attributeCount; j++) {
            in.readUnsignedShort(); // Name
            int length = in.readInt();
            while (length > 0) {
              int skipped = in.skipBytes(length);
              if (skipped <= 0) {
                throw new IOException(classFile + " is truncated");
              }
              length -= skipped;
            }
          }
        }
      }
      for (int i = 1; i < constantCount; i++) {
        if (classNameIndices [i] != 0) {
          String name = texts [classNameIndices [i]];
          if (name.startsWith("[")) {
            // The class of an array is named by the descriptor of its type
            addDescriptorClassNames(name, classNames);
          } else {
            classNames.add(name);
          }
        }
      }
      return className;
    } finally {
      in.close();
    }
  }

  /**
   * Adds to <code>classNames</code> the classes named between <code>L</code> and <code>;</code>
   * in the descriptor of a field or of a method.
   */
  private void addDescriptorClassNames(String descriptor, List<String> classNames) {
    for (int i = 0; i < descriptor.length(); i++) {
      if (descriptor.charAt(i) == 'L') {
        int end = descriptor.indexOf(';', i);
        classNames.add(descriptor.substring(i + 1, end));
        i = end;
      }
    }
  }

  /**
   * The packages expected and the ones each of them may use.
   */
  private static class DependencyConstraint {
    private final Map<String, Set<String>> dependencies = new TreeMap<String, Set<String>>();

    public JavaPackage addPackage(String name) {
      Set<String> packageDependencies = new TreeSet<String>();
      this.dependencies.put(name, packageDependencies);
      return new JavaPackage(name, packageDependencies);
    }

    public Map<String, Set<String>> getDependencies() {
      return this.dependencies;
    }
  }

  private static class JavaPackage {
    private final String      name;
    private final Set<String> dependencies;

    public JavaPackage(String name, Set<String> dependencies) {
      this.name = name;
      this.dependencies = dependencies;
    }

    public void dependsUpon(JavaPackage usedPackage) {
      this.dependencies.add(usedPackage.name);
    }
  }
}
