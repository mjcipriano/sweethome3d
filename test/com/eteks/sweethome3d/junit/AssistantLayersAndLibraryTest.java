/*
 * AssistantLayersAndLibraryTest.java 16 Nov 2026
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ObjectProperty;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemTags;

/**
 * Tests the tags and layers of furniture, the furniture built from parts and
 * the furniture libraries written by the assistant.
 */
public class AssistantLayersAndLibraryTest {
  private String                   previousNo3D;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private File                     folder;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
    this.folder = Files.createTempDirectory("sh3d-library-test").toFile();
  }

  @After
  public void tearDown() {
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
    File [] files = this.folder.listFiles();
    if (files != null) {
      for (File file : files) {
        file.delete();
      }
    }
    this.folder.delete();
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private String path(String name) {
    return new File(this.folder, name).getPath().replace("\\", "\\\\");
  }

  @Test
  public void testTagsAndLayers() throws Exception {
    run("{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":300,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Office chair\",\"x\":300,\"y\":200}");
    assertEquals(3, this.home.getFurniture().size());
    HomePieceOfFurniture sofa = this.home.getFurniture().get(0);
    HomePieceOfFurniture desk = this.home.getFurniture().get(1);
    HomePieceOfFurniture chair = this.home.getFurniture().get(2);

    String summary = run("{\"action\":\"add_tags\",\"ids\":[\"F1\",\"F2\"],\"tags\":[\"Living\",\"To Buy\"]}");
    assertTrue(summary, summary.contains("Tags changed on 2 pieces"));
    assertEquals("Living, To Buy", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertEquals(Arrays.asList("Living", "To Buy"), HomeItemTags.getTags(desk));
    assertNull(chair.getProperty(HomeItemTags.TAGS_PROPERTY));
    // The furniture list shows the tags in a column
    assertTrue(this.home.getFurnitureAdditionalProperties().contains(new ObjectProperty(HomeItemTags.TAGS_PROPERTY)));
    assertTrue(this.home.getFurnitureVisiblePropertyNames().contains(HomeItemTags.TAGS_PROPERTY));

    // A tag keeps the way it was first written, and isn't added twice
    run("{\"action\":\"add_tag\",\"id\":\"F3\",\"tag\":\"to buy\"},"
        + "{\"action\":\"add_tags\",\"id\":\"F1\",\"tags\":[\"TO BUY\", \"a,b;c\"]}");
    assertEquals("To Buy", chair.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertEquals("Living, To Buy, abc", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertEquals(Arrays.asList("abc", "Living", "To Buy"), HomeItemTags.getAllTags(this.home));

    // Hiding a layer hides its pieces, in one step of undo
    summary = run("{\"action\":\"hide_layer\",\"tag\":\"living\"}");
    assertTrue(summary, summary.contains("Hidden 2 of the 2 pieces"));
    assertFalse(sofa.isVisible());
    assertFalse(desk.isVisible());
    assertTrue(chair.isVisible());
    String description = HomeAssistantContext.describeHome(this.home);
    assertTrue(description, description.contains("Living (2, hidden)"));
    assertTrue(description, description.contains("To Buy (3, 2 hidden)"));
    assertTrue(description, description.contains("tags: Living, To Buy"));
    this.homeController.undo();
    assertTrue(sofa.isVisible());
    assertTrue(desk.isVisible());
    this.homeController.redo();
    assertFalse(sofa.isVisible());

    summary = run("{\"action\":\"show_layer\",\"type\":\"furniture\"}");
    assertTrue(summary, summary.contains("Shown 2 of the 3 pieces"));
    assertTrue(sofa.isVisible());

    // The pieces without tag, and a layer by type and tag
    run("{\"action\":\"remove_tags\",\"id\":\"F3\",\"tags\":[\"to buy\"]}");
    assertNull(chair.getProperty(HomeItemTags.TAGS_PROPERTY));
    run("{\"action\":\"select_layer\",\"tag\":\"untagged\"}");
    assertEquals(1, this.home.getSelectedItems().size());
    assertTrue(this.home.getSelectedItems().contains(chair));
    run("{\"action\":\"set_tags\",\"type\":\"furniture\",\"with_tag\":\"abc\",\"tags\":[\"keep\"]}");
    assertEquals("keep", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertEquals("Living, To Buy", desk.getProperty(HomeItemTags.TAGS_PROPERTY));
    this.homeController.undo();
    assertEquals("Living, To Buy, abc", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));

    summary = run("{\"action\":\"hide_layer\",\"type\":\"spaceships\"}");
    assertTrue(summary, summary.contains("unknown type"));
    summary = run("{\"action\":\"hide_layer\"}");
    assertTrue(summary, summary.contains("without \"tag\" or \"type\""));
    assertEquals(HomeItemTags.TYPE_DOORS_AND_WINDOWS, HomeItemTags.parseType("Doors"));
    assertEquals(HomeItemTags.TYPE_FURNITURE, HomeItemTags.getType(sofa));
  }

  /**
   * Tests the Tags menu of the furniture list and of the plan.
   */
  @Test
  public void testTagsMenu() {
    run("{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":300,\"y\":100}");
    HomePieceOfFurniture sofa = this.home.getFurniture().get(0);
    HomePieceOfFurniture desk = this.home.getFurniture().get(1);
    this.homeController.changeFurnitureTags(Arrays.asList(sofa), Arrays.asList("kitchen", "old"), null, false);
    this.homeController.changeFurnitureTags(Arrays.asList(desk), Arrays.asList("old"), null, false);
    javax.swing.JMenu menu = com.eteks.sweethome3d.swing.FurnitureLayersView.createTagsMenu("Tags", "Layers...",
        (javax.swing.JComponent)this.homeController.getView(), this.home, this.homeController);

    // Both pieces selected: "old" is ticked, "kitchen" is on one of the two
    this.home.setSelectedItems(Arrays.<com.eteks.sweethome3d.model.Selectable>asList(sofa, desk));
    menu.getMenuListeners() [0].menuSelected(null);
    javax.swing.JCheckBoxMenuItem kitchenItem = (javax.swing.JCheckBoxMenuItem)menu.getMenuComponent(0);
    javax.swing.JCheckBoxMenuItem oldItem = (javax.swing.JCheckBoxMenuItem)menu.getMenuComponent(1);
    assertEquals("kitchen  (1 of 2)", kitchenItem.getText());
    assertFalse(kitchenItem.isSelected());
    assertEquals("old", oldItem.getText());
    assertTrue(oldItem.isSelected());
    assertEquals("New tag...", ((javax.swing.JMenuItem)menu.getMenuComponent(3)).getText());
    assertEquals("Layers...", ((javax.swing.JMenuItem)menu.getMenuComponent(6)).getText());

    // Choosing a tag some pieces miss gives it to all, choosing a ticked one removes it
    kitchenItem.doClick(0);
    assertEquals("old, kitchen", desk.getProperty(HomeItemTags.TAGS_PROPERTY));
    menu.getMenuListeners() [0].menuSelected(null);
    ((javax.swing.JMenuItem)menu.getMenuComponent(1)).doClick(0);
    assertEquals("kitchen", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertEquals("kitchen", desk.getProperty(HomeItemTags.TAGS_PROPERTY));
    this.homeController.undo();
    assertEquals("kitchen, old", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));

    // Renaming a tag, and a layer hidden from the controller
    assertEquals(2, this.homeController.renameFurnitureTag("OLD", "Vintage"));
    assertEquals("kitchen, Vintage", sofa.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertEquals(1, this.homeController.setFurnitureVisible(Arrays.asList(sofa), false));
    assertFalse(sofa.isVisible());

    // Without selection, tags can't be changed
    this.home.setSelectedItems(new ArrayList<com.eteks.sweethome3d.model.Selectable>());
    menu.getMenuListeners() [0].menuSelected(null);
    assertFalse(menu.getMenuComponent(0).isEnabled());
  }

  /**
   * Returns the volume enclosed by the faces of an OBJ model, positive if
   * they are turned outward.
   */
  private static double getVolume(Content model) throws Exception {
    List<double []> vertices = new ArrayList<double []>();
    double volume = 0;
    InputStream in = model.openStream();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        String [] parts = line.trim().split(" +");
        if (parts [0].equals("v")) {
          vertices.add(new double [] {Double.parseDouble(parts [1]), Double.parseDouble(parts [2]),
                                      Double.parseDouble(parts [3])});
        } else if (parts [0].equals("f")) {
          double [] a = vertices.get(Integer.parseInt(parts [1]) - 1);
          for (int i = 2; i < parts.length - 1; i++) {
            double [] b = vertices.get(Integer.parseInt(parts [i]) - 1);
            double [] c = vertices.get(Integer.parseInt(parts [i + 1]) - 1);
            volume += (a [0] * (b [1] * c [2] - b [2] * c [1])
                - a [1] * (b [0] * c [2] - b [2] * c [0])
                + a [2] * (b [0] * c [1] - b [1] * c [0])) / 6;
          }
        }
      }
    } finally {
      in.close();
    }
    return volume;
  }

  @Test
  public void testFurnitureBuiltFromParts() throws Exception {
    String summary = run("{\"action\":\"create_furniture\",\"name\":\"Oak table\",\"x\":200,\"y\":300,\"angle\":90,"
        + "\"tags\":[\"custom\"],\"parts\":["
        + "{\"shape\":\"box\",\"x\":0,\"y\":0,\"z\":71,\"width\":120,\"depth\":70,\"height\":4,\"color\":\"#B08050\"},"
        + "{\"shape\":\"box\",\"x\":-55,\"y\":-30,\"z\":0,\"width\":5,\"depth\":5,\"height\":71,\"color\":\"#604020\"},"
        + "{\"shape\":\"box\",\"x\":55,\"y\":-30,\"z\":0,\"width\":5,\"depth\":5,\"height\":71,\"color\":\"#604020\"},"
        + "{\"shape\":\"box\",\"x\":-55,\"y\":30,\"z\":0,\"width\":5,\"depth\":5,\"height\":71,\"color\":\"#604020\"},"
        + "{\"shape\":\"box\",\"x\":55,\"y\":30,\"z\":0,\"width\":5,\"depth\":5,\"height\":71,\"color\":\"#604020\"},"
        + "{\"shape\":\"wobble\",\"width\":-3}]}");
    assertTrue(summary, summary.contains("Created \"Oak table\", 120 x 70 x 75 cm"));
    assertTrue(summary, summary.contains("was ignored"));
    assertEquals(1, this.home.getFurniture().size());
    HomePieceOfFurniture table = this.home.getFurniture().get(0);
    assertEquals(120, table.getWidth(), 0.01f);
    assertEquals(70, table.getDepth(), 0.01f);
    assertEquals(75, table.getHeight(), 0.01f);
    assertEquals(200, table.getX(), 0.01f);
    assertEquals(0, table.getElevation(), 0.01f);
    assertEquals("custom", table.getProperty(HomeItemTags.TAGS_PROPERTY));
    // Faces are turned outward: the volume they enclose is the one of the parts
    assertEquals(120 * 70 * 4 + 4 * 5 * 5 * 71, getVolume(table.getModel()), 1);
    // Its icon is a picture of the table
    InputStream in = table.getIcon().openStream();
    BufferedImage icon = ImageIO.read(in);
    in.close();
    assertEquals(48, icon.getWidth());
    int drawn = 0;
    for (int y = 0; y < icon.getHeight(); y++) {
      for (int x = 0; x < icon.getWidth(); x++) {
        if ((icon.getRGB(x, y) >>> 24) != 0) {
          drawn++;
        }
      }
    }
    assertTrue("pixels drawn: " + drawn, drawn > 200 && drawn < 48 * 48);
    this.homeController.undo();
    assertEquals(0, this.home.getFurniture().size());

    // Round shapes, laid down or tapered, and a piece hung above the floor
    summary = run("{\"action\":\"create_furniture\",\"name\":\"Shapes\",\"parts\":["
        + "{\"shape\":\"cylinder\",\"x\":0,\"y\":0,\"z\":100,\"diameter\":20,\"height\":50},"
        + "{\"shape\":\"cylinder\",\"axis\":\"x\",\"x\":100,\"y\":0,\"z\":100,\"width\":50,\"diameter\":20},"
        + "{\"shape\":\"sphere\",\"x\":200,\"y\":0,\"z\":100,\"diameter\":30},"
        + "{\"shape\":\"cone\",\"x\":300,\"y\":0,\"z\":100,\"diameter\":20,\"height\":30,\"rotation\":40}]}");
    HomePieceOfFurniture shapes = this.home.getFurniture().get(0);
    assertEquals(100, shapes.getElevation(), 0.01f);
    assertEquals(50, shapes.getHeight(), 0.01f);
    assertEquals(320, shapes.getWidth(), 0.5f);
    double expected = 2 * Math.PI * 100 * 50 + 4. / 3 * Math.PI * 15 * 15 * 15 + Math.PI * 100 * 30 / 3;
    assertEquals(expected, getVolume(shapes.getModel()), expected * 0.04);

    // Tags, their column, the hidden pieces and the built model are saved with the home
    run("{\"action\":\"add_tags\",\"id\":\"F1\",\"tags\":[\"Living\",\"To Buy\"]},"
        + "{\"action\":\"hide_layer\",\"tag\":\"living\"}");
    File file = new File(this.folder, "tags.sh3d");
    new com.eteks.sweethome3d.io.HomeFileRecorder().writeHome(this.home, file.getPath());
    Home savedHome = new com.eteks.sweethome3d.io.HomeFileRecorder().readHome(file.getPath());
    HomePieceOfFurniture savedShapes = savedHome.getFurniture().get(0);
    assertEquals("Living, To Buy", savedShapes.getProperty(HomeItemTags.TAGS_PROPERTY));
    assertFalse(savedShapes.isVisible());
    assertTrue(savedHome.getFurnitureVisiblePropertyNames().contains(HomeItemTags.TAGS_PROPERTY));
    // The application gives the property of the tags to each home it opens, which keeps the column
    savedHome.setFurnitureAdditionalProperties(Arrays.asList(HomeItemTags.createTagsProperty()));
    assertTrue(savedHome.getFurnitureVisiblePropertyNames().contains(HomeItemTags.TAGS_PROPERTY));
    assertEquals(expected, getVolume(savedShapes.getModel()), expected * 0.04);

    summary = run("{\"action\":\"create_furniture\",\"name\":\"Nothing\",\"parts\":[]}");
    assertTrue(summary, summary.contains("at least one part"));
    assertEquals(1, this.home.getFurniture().size());
  }

  @Test
  public void testCatalogAndLibrary() throws Exception {
    // A piece saved in the catalog without being placed
    String summary = run("{\"action\":\"create_furniture\",\"name\":\"Cube stool\",\"catalog\":\"AI furniture\","
        + "\"place\":false,\"parts\":[{\"shape\":\"box\",\"width\":40,\"depth\":40,\"height\":45,\"color\":\"#3060A0\"}]}");
    assertTrue(summary, summary.contains("added to the catalog category \"AI furniture\""));
    assertEquals(0, this.home.getFurniture().size());
    CatalogPieceOfFurniture stool = this.executor.findCatalogPiece("Cube stool", false);
    assertNotNull(stool);
    assertEquals("AI furniture", stool.getCategory().getName());
    assertTrue(stool.isModifiable());

    // Placed from the catalog like any other piece, then saved again with another piece
    run("{\"action\":\"add_furniture\",\"name\":\"Cube stool\",\"x\":50,\"y\":50},"
        + "{\"action\":\"create_furniture\",\"name\":\"Low shelf\",\"x\":200,\"y\":50,\"tags\":[\"set A\"],\"parts\":["
        + "{\"shape\":\"box\",\"z\":0,\"width\":90,\"depth\":30,\"height\":3},"
        + "{\"shape\":\"box\",\"z\":40,\"width\":90,\"depth\":30,\"height\":3}]}");
    assertEquals(2, this.home.getFurniture().size());
    run("{\"action\":\"add_tags\",\"id\":\"F1\",\"tags\":[\"set A\"]}");
    summary = run("{\"action\":\"add_to_catalog\",\"tag\":\"set A\",\"category\":\"AI furniture\"}");
    assertTrue(summary, summary.contains("Added 1 piece"));
    assertTrue(summary, summary.contains("\"Cube stool\" (already in this category)"));
    assertNotNull(this.executor.findCatalogPiece("Low shelf", false));

    // A library written from the pieces of a tag
    File library = new File(this.folder, "set-a.sh3f");
    summary = run("{\"action\":\"export_furniture_library\",\"path\":\"" + path("set-a") + "\","
        + "\"name\":\"Set A\",\"tag\":\"set A\",\"library_category\":\"Set A\",\"creator\":\"Test\"}");
    assertTrue(summary, summary.contains("Wrote 2 pieces"));
    assertTrue(library.isFile());
    summary = run("{\"action\":\"export_furniture_library\",\"path\":\"" + path("set-a.sh3f") + "\",\"tag\":\"set A\"}");
    assertTrue(summary, summary.contains("\"replace\":true"));

    // The application reads it as a furniture library
    DefaultFurnitureCatalog catalog = new DefaultFurnitureCatalog(
        new URL [] {library.toURI().toURL()});
    assertEquals(1, catalog.getCategoriesCount());
    FurnitureCategory category = catalog.getCategory(0);
    assertEquals("Set A", category.getName());
    assertEquals(2, category.getFurnitureCount());
    CatalogPieceOfFurniture stoolInLibrary = category.getPieceOfFurniture(0);
    assertEquals("Cube stool", stoolInLibrary.getName());
    assertEquals(40, stoolInLibrary.getWidth(), 0.01f);
    assertEquals(45, stoolInLibrary.getHeight(), 0.01f);
    assertEquals("Test", stoolInLibrary.getCreator());
    assertEquals(Arrays.asList("set A"), Arrays.asList(stoolInLibrary.getTags()));
    assertEquals(40 * 40 * 45, getVolume(stoolInLibrary.getModel()), 1);
    InputStream in = stoolInLibrary.getIcon().openStream();
    assertNotNull(ImageIO.read(in));
    in.close();
    CatalogPieceOfFurniture shelfInLibrary = category.getPieceOfFurniture(1);
    assertEquals("Low shelf", shelfInLibrary.getName());
    assertEquals(43, shelfInLibrary.getHeight(), 0.01f);

    // A library of a category of the catalog
    summary = run("{\"action\":\"export_furniture_library\",\"path\":\"" + path("category.sh3f") + "\","
        + "\"category\":\"ai furniture\"}");
    assertTrue(summary, summary.contains("Wrote 2 pieces"));

    // Errors are reported to the model
    summary = run("{\"action\":\"export_furniture_library\",\"path\":\"relative.sh3f\",\"tag\":\"set A\"}");
    assertTrue(summary, summary.contains("must be absolute"));
    summary = run("{\"action\":\"export_furniture_library\",\"path\":\"" + path("none") + "\",\"tag\":\"nothing\"}");
    assertTrue(summary, summary.contains("no furniture is designated"));
    summary = run("{\"action\":\"import_furniture_library\",\"path\":\"" + path("missing.sh3f") + "\"}");
    assertTrue(summary, summary.contains("there is no file"));
    summary = run("{\"action\":\"import_furniture_library\",\"path\":\"" + path("set-a.txt") + "\"}");
    assertTrue(summary, summary.contains(".sh3f file"));
  }
}
