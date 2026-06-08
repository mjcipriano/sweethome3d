package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.junit.Test;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.tools.URLContent;

public class ModelLODTest {
  @Test
  public void testModelLODIsClonedWithHome() throws Exception {
    Content source = new URLContent(new File("source.obj").toURI().toURL());
    Content simplified = new URLContent(new File("simplified.obj").toURI().toURL());
    Home home = new Home();
    ModelLOD modelLOD = new ModelLOD(simplified, 5000000, 75000);
    home.setModelLOD(source, modelLOD);

    Home clone = home.clone();
    assertNotSame(home.getModelLODs(), clone.getModelLODs());
    assertSame(modelLOD, clone.getModelLOD(source));
    assertEquals(5000000, clone.getModelLOD(source).getSourceVertexCount());
    assertEquals(75000, clone.getModelLOD(source).getVertexCount());
  }

  @Test
  public void testModelLODContentIsEmbeddedInHomeFile() throws Exception {
    File sourceFile = writeTemporaryContent("source-model");
    File lodFile = writeTemporaryContent("simplified-model");
    Home home = new Home();
    home.setModelLOD(new URLContent(sourceFile.toURI().toURL()),
        new ModelLOD(new URLContent(lodFile.toURI().toURL()), 5000000, 75000));
    File homeFile = File.createTempFile("model-lod-", ".sh3d");
    homeFile.deleteOnExit();
    HomeFileRecorder recorder = new HomeFileRecorder();
    recorder.writeHome(home, homeFile.getPath());
    sourceFile.delete();
    lodFile.delete();

    Home reopened = recorder.readHome(homeFile.getPath());
    assertEquals(1, reopened.getModelLODs().size());
    Map.Entry<Content, ModelLOD> entry = reopened.getModelLODs().entrySet().iterator().next();
    assertEquals("source-model", readContent(entry.getKey()));
    assertEquals("simplified-model", readContent(entry.getValue().getContent()));
  }

  private File writeTemporaryContent(String value) throws Exception {
    File file = File.createTempFile("model-lod-content-", ".dat");
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(value.getBytes("US-ASCII"));
    } finally {
      out.close();
    }
    return file;
  }

  @Test
  public void testModelLODPersistsThroughXmlHomeEntry() throws Exception {
    // The application reads the preferred Home.xml entry, so model LODs must round
    // trip through the XML export/import path, not only the serialized Home entry.
    File sourceFile = writeTemporaryContent("xml-source-model");
    File lodFile = writeTemporaryContent("xml-simplified-model");
    Home home = new Home();
    home.setModelLOD(new URLContent(sourceFile.toURI().toURL()),
        new ModelLOD(new URLContent(lodFile.toURI().toURL()), 4000000, 60000));
    File homeFile = File.createTempFile("model-lod-xml-", ".sh3d");
    homeFile.deleteOnExit();
    HomeFileRecorder recorder = new HomeFileRecorder(0, false, null, false, true);
    recorder.writeHome(home, homeFile.getPath());
    sourceFile.delete();
    lodFile.delete();

    Home reopened = recorder.readHome(homeFile.getPath());
    assertEquals(1, reopened.getModelLODs().size());
    Map.Entry<Content, ModelLOD> entry = reopened.getModelLODs().entrySet().iterator().next();
    assertEquals("xml-source-model", readContent(entry.getKey()));
    assertEquals("xml-simplified-model", readContent(entry.getValue().getContent()));
    assertEquals(4000000, entry.getValue().getSourceVertexCount());
    assertEquals(60000, entry.getValue().getVertexCount());
  }

  private String readContent(Content content) throws Exception {
    InputStream in = content.openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] bytes = new byte [64];
      int count;
      while ((count = in.read(bytes)) >= 0) {
        out.write(bytes, 0, count);
      }
      return new String(out.toByteArray(), "US-ASCII");
    } finally {
      in.close();
    }
  }
}
