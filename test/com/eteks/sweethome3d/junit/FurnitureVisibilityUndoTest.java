/*
 * FurnitureVisibilityUndoTest.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Checks that showing or hiding the selected furniture (the Visible On / Off
 * popup actions) is one undoable operation.
 */
public class FurnitureVisibilityUndoTest {
  @Test
  public void testSelectedFurnitureVisibilityIsUndoable() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = new DefaultUserPreferences();
      HomeController homeController = new HomeController(home, preferences, new SwingViewFactory());
      Content icon = new URLContent(new File("dummy.png").toURI().toURL());
      Content model = new URLContent(new File("dummy.obj").toURI().toURL());
      HomePieceOfFurniture piece1 = new HomePieceOfFurniture(
          new CatalogPieceOfFurniture("Piece 1", icon, model, 50, 50, 50, true, false));
      HomePieceOfFurniture piece2 = new HomePieceOfFurniture(
          new CatalogPieceOfFurniture("Piece 2", icon, model, 50, 50, 50, true, false));
      HomePieceOfFurniture piece3 = new HomePieceOfFurniture(
          new CatalogPieceOfFurniture("Piece 3", icon, model, 50, 50, 50, true, false));
      home.addPieceOfFurniture(piece1);
      home.addPieceOfFurniture(piece2);
      home.addPieceOfFurniture(piece3);
      home.setSelectedItems(Arrays.asList(new Selectable [] {piece1, piece2}));

      homeController.getFurnitureController().setSelectedFurnitureVisible(false);
      assertFalse(piece1.isVisible());
      assertFalse(piece2.isVisible());
      assertTrue("Unselected piece unchanged", piece3.isVisible());

      homeController.undo();
      assertTrue(piece1.isVisible());
      assertTrue(piece2.isVisible());
      homeController.redo();
      assertFalse(piece1.isVisible());
      assertFalse(piece2.isVisible());
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }

  @Test
  public void testReducedDetailIsUndoable() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = new DefaultUserPreferences();
      HomeController homeController = new HomeController(home, preferences, new SwingViewFactory());
      Content icon = new URLContent(new File("dummy.png").toURI().toURL());
      Content model = new URLContent(new File("dummy.obj").toURI().toURL());
      HomePieceOfFurniture piece1 = new HomePieceOfFurniture(
          new CatalogPieceOfFurniture("Piece 1", icon, model, 50, 50, 50, true, false));
      HomePieceOfFurniture piece2 = new HomePieceOfFurniture(
          new CatalogPieceOfFurniture("Piece 2", icon, model, 50, 50, 50, true, false));
      home.addPieceOfFurniture(piece1);
      home.addPieceOfFurniture(piece2);
      piece2.setProperty(ModelLOD.LOW_POLY_PROPERTY, "true");
      // Undo support is bound to the home once its view exists
      homeController.getView();
      assertFalse(home.isModified());

      homeController.getFurnitureController().setFurnitureReducedDetail(
          Arrays.asList(new HomePieceOfFurniture [] {piece1, piece2}), true);
      assertTrue(ModelLOD.isReducedDetailInView(piece1));
      assertTrue(ModelLOD.isReducedDetailInView(piece2));
      assertTrue("Change marks home as modified", home.isModified());

      homeController.undo();
      assertFalse(ModelLOD.isReducedDetailInView(piece1));
      assertTrue("Piece already reduced keeps its state", ModelLOD.isReducedDetailInView(piece2));
      assertFalse(home.isModified());
      homeController.redo();
      assertTrue(ModelLOD.isReducedDetailInView(piece1));

      // Unused reduced models are dropped, used ones kept
      Content otherModel = new URLContent(new File("other.obj").toURI().toURL());
      home.setModelLOD(model, new ModelLOD(model, 100, 10));
      home.setModelLOD(otherModel, new ModelLOD(otherModel, 100, 10));
      assertEquals(1, home.removeUnusedModelLODs());
      assertTrue(home.getModelLOD(model) != null);
      assertTrue(home.getModelLOD(otherModel) == null);
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
