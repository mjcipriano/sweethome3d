package com.eteks.sweethome3d.performance;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.j3d.ModelLODGenerator;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ModelLOD;

/**
 * Generates, persists, and reloads every eligible model LOD in a real home.
 */
public class ModelLODGenerationBenchmark {
  public static void main(String [] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: ModelLODGenerationBenchmark <input.sh3d> <output.sh3d>");
      System.exit(2);
    }
    File input = new File(args [0]).getCanonicalFile();
    File output = new File(args [1]).getCanonicalFile();
    HomeFileRecorder recorder = new HomeFileRecorder(0, false, null, false, true);
    Home home = recorder.readHome(input.getPath());
    Set<Content> models = new HashSet<Content>();
    collectModels(home.getFurniture(), models);

    long start = System.nanoTime();
    int generated = 0;
    long sourceVertices = 0;
    long lodVertices = 0;
    ModelLODGenerator generator = new ModelLODGenerator();
    for (Content model : models) {
      ModelLOD modelLOD = generator.generate(model);
      if (modelLOD != null) {
        home.setModelLOD(model, modelLOD);
        generated++;
        sourceVertices += modelLOD.getSourceVertexCount();
        lodVertices += modelLOD.getVertexCount();
        System.out.println("lod=" + generated
            + " source_vertices=" + modelLOD.getSourceVertexCount()
            + " lod_vertices=" + modelLOD.getVertexCount());
      }
    }
    recorder.writeHome(home, output.getPath());
    Home reopened = recorder.readHome(output.getPath());
    if (reopened.getModelLODs().size() != generated) {
      throw new AssertionError("Expected " + generated + " persisted LODs, found "
          + reopened.getModelLODs().size());
    }
    System.out.println("models=" + models.size()
        + " generated=" + generated
        + " source_vertices=" + sourceVertices
        + " lod_vertices=" + lodVertices
        + " elapsed_ms=" + Math.round((System.nanoTime() - start) / 1000000d)
        + " output_bytes=" + output.length());
  }

  private static void collectModels(Iterable<HomePieceOfFurniture> furniture, Set<Content> models) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collectModels(((HomeFurnitureGroup)piece).getFurniture(), models);
      } else if (piece.getModel() != null) {
        models.add(piece.getModel());
      }
    }
  }
}
