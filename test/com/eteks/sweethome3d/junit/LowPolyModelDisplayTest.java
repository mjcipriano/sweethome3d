package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * Verifies the per-piece reduced-detail (low poly) opt-in decision: a piece
 * displays its reduced LOD only when it is opted in, a LOD exists and reduced
 * models are allowed (i.e. not in a render/export scene), and the original model
 * is used in every other case.
 */
public class LowPolyModelDisplayTest {
  @After
  public void clearKillSwitch() {
    System.clearProperty("com.eteks.sweethome3d.j3d.useModelLODs");
  }

  private HomePieceOfFurniture newPiece(Content model) throws Exception {
    Content icon = new URLContent(new File("icon.png").toURI().toURL());
    return new HomePieceOfFurniture(
        new CatalogPieceOfFurniture("tree", icon, model, 100, 100, 200, true, false));
  }

  @Test
  public void testReducedModelOnlyShownWhenOptedIn() throws Exception {
    Content model = new URLContent(new File("tree.obj").toURI().toURL());
    Content reduced = new URLContent(new File("tree-lod.obj").toURI().toURL());
    Home home = new Home();
    home.setModelLOD(model, new ModelLOD(reduced, 500000, 50000));
    HomePieceOfFurniture piece = newPiece(model);
    home.addPieceOfFurniture(piece);

    // Not opted in: original model is displayed even though a LOD exists.
    assertFalse(ModelLOD.isReducedDetailInView(piece));
    assertSame("A piece that didn't opt in must show its original model",
        model, ModelLOD.getDisplayedModel(piece, home, true));

    // Opt in: the reduced model is displayed in the viewport.
    piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, "true");
    assertTrue(ModelLOD.isReducedDetailInView(piece));
    assertSame("An opted-in piece must show its reduced model in the view",
        reduced, ModelLOD.getDisplayedModel(piece, home, true));
  }

  @Test
  public void testOriginalModelUsedForRenderingAndExport() throws Exception {
    Content model = new URLContent(new File("tree.obj").toURI().toURL());
    Content reduced = new URLContent(new File("tree-lod.obj").toURI().toURL());
    Home home = new Home();
    home.setModelLOD(model, new ModelLOD(reduced, 500000, 50000));
    HomePieceOfFurniture piece = newPiece(model);
    piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, "true");

    // useModelLODs == false models the render / export / "show full detail" scene.
    assertSame("Rendering and exports must always use the original model",
        model, ModelLOD.getDisplayedModel(piece, home, false));
  }

  @Test
  public void testFurnitureSortsByCachedVertexCount() throws Exception {
    Content model = new URLContent(new File("tree.obj").toURI().toURL());
    HomePieceOfFurniture big = newPiece(model);
    big.setModelVertexCount(Integer.valueOf(300000));
    HomePieceOfFurniture small = newPiece(model);
    small.setModelVertexCount(Integer.valueOf(1000));
    HomePieceOfFurniture medium = newPiece(model);
    medium.setModelVertexCount(Integer.valueOf(50000));
    HomePieceOfFurniture unknown = newPiece(model); // no cached count yet

    java.util.List<HomePieceOfFurniture> pieces =
        new java.util.ArrayList<HomePieceOfFurniture>(java.util.Arrays.asList(big, small, medium, unknown));
    java.util.Collections.sort(pieces,
        HomePieceOfFurniture.getFurnitureComparator(HomePieceOfFurniture.SortableProperty.VERTICES));

    assertSame("Unknown vertex count sorts first", unknown, pieces.get(0));
    assertSame("Smallest vertex count next", small, pieces.get(1));
    assertSame(medium, pieces.get(2));
    assertSame("Largest vertex count sorts last", big, pieces.get(3));
  }

  @Test
  public void testOriginalModelUsedWhenNoLodOrGloballyDisabled() throws Exception {
    Content model = new URLContent(new File("tree.obj").toURI().toURL());
    Home home = new Home();
    HomePieceOfFurniture piece = newPiece(model);
    piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, "true");

    // Opted in but no LOD generated yet for this model.
    assertSame("Without a generated LOD the original model is shown",
        model, ModelLOD.getDisplayedModel(piece, home, true));

    // A generated LOD plus the global kill switch still shows the original.
    home.setModelLOD(model, new ModelLOD(
        new URLContent(new File("tree-lod.obj").toURI().toURL()), 500000, 50000));
    System.setProperty("com.eteks.sweethome3d.j3d.useModelLODs", "false");
    assertSame("The global kill switch must force the original model",
        model, ModelLOD.getDisplayedModel(piece, home, true));
  }
}
