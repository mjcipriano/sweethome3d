package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;

/**
 * Verifies the plain-text home brief given to the AI design assistant reports
 * the rooms (with area in m2), walls (length in m) and furniture of a home.
 */
public class HomeAssistantContextTest {
  @Test
  public void testDescribesRoomsWallsAndFurniture() throws Exception {
    Home home = new Home();
    // 400 x 300 cm room = 12 m2
    home.addRoom(new Room(new float[][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
    home.addWall(new Wall(0, 0, 400, 0, 7.5f, 250)); // 4 m long
    Content model = new URLContent(new File("sofa.obj").toURI().toURL());
    Content icon = new URLContent(new File("sofa.png").toURI().toURL());
    HomePieceOfFurniture sofa = new HomePieceOfFurniture(
        new CatalogPieceOfFurniture("Big sofa", icon, model, 200, 90, 80, true, false));
    sofa.setModelSize(Long.valueOf(5 * 1024 * 1024)); // 5 MB
    home.addPieceOfFurniture(sofa);

    String brief = HomeAssistantContext.describeHome(home);
    assertTrue(brief, brief.contains("Rooms (1)"));
    assertTrue(brief, brief.contains("12 m2"));
    assertTrue(brief, brief.contains("Walls (1)"));
    assertTrue(brief, brief.contains("4 m"));
    assertTrue(brief, brief.contains("Furniture: 1 pieces"));
    assertTrue(brief, brief.contains("1 visible"));
    assertTrue(brief, brief.contains("Big sofa"));
    assertTrue(brief, brief.contains("5 MB"));
    // Stable ids let the assistant target existing items in phase 3
    assertTrue(brief, brief.contains("R1"));
    assertTrue(brief, brief.contains("W1"));
    assertTrue(brief, brief.contains("F1"));
  }
}
