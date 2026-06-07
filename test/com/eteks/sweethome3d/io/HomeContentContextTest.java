/*
 * HomeContentContextTest.java 7 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.eteks.sweethome3d.model.Content;

import junit.framework.TestCase;

/**
 * Tests content validation and sharing while reading a home archive.
 */
public class HomeContentContextTest extends TestCase {
  public void testDamagedHomesAreReported() throws Exception {
    assertDamagedContentCount("damagedHomeWithContentDigests.sh3d", 5);
    assertDamagedContentCount(
        "damagedHomeInValidZipWithContentDigestsAndNoContent.sh3d", 9);
  }

  public void testDuplicateContentsAreShared() throws Exception {
    Map<String, byte []> contents = new LinkedHashMap<String, byte []>();
    contents.put("1", "shared model".getBytes("UTF-8"));
    contents.put("2", "shared model".getBytes("UTF-8"));
    contents.put("3", "different model".getBytes("UTF-8"));
    File homeFile = createHomeArchive(contents, null);
    try {
      HomeContentContext context = new HomeContentContext(
          homeFile.toURI().toURL(), null, false);
      Content firstContent = context.lookupContent("1");
      Content duplicateContent = context.lookupContent("2");
      Content differentContent = context.lookupContent("3");

      assertSame("Identical contents should share one instance",
          firstContent, duplicateContent);
      assertNotSame("Different contents shouldn't share one instance",
          firstContent, differentContent);
      assertTrue("All contents should be checked", context.containsCheckedContents());
      assertFalse("Valid contents shouldn't be reported as invalid",
          context.containsInvalidContents());
    } finally {
      homeFile.delete();
    }
  }

  public void testDigestMismatchIsReported() throws Exception {
    Map<String, byte []> contents = new LinkedHashMap<String, byte []>();
    contents.put("1", "actual content".getBytes("UTF-8"));
    Map<String, byte []> manifestContents =
        new LinkedHashMap<String, byte []>(contents);
    manifestContents.put("1", "expected content".getBytes("UTF-8"));
    File homeFile = createHomeArchive(contents, manifestContents);
    try {
      HomeContentContext context = new HomeContentContext(
          homeFile.toURI().toURL(), null, false);
      Content content = context.lookupContent("1");

      assertTrue("Digest mismatch should be reported",
          context.containsInvalidContents());
      assertEquals("Mismatched content should be retained for repair reporting",
          1, context.getInvalidContents().size());
      assertSame(content, context.getInvalidContents().get(0));
      assertFalse("A damaged archive isn't fully checked",
          context.containsCheckedContents());
    } finally {
      homeFile.delete();
    }
  }

  private File createHomeArchive(Map<String, byte []> contents,
                                 Map<String, byte []> manifestContents)
      throws IOException, NoSuchAlgorithmException {
    if (manifestContents == null) {
      manifestContents = contents;
    }
    File homeFile = File.createTempFile("home-content-context", ".sh3d");
    ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(homeFile));
    try {
      zipOut.putNextEntry(new ZipEntry("ContentDigests"));
      OutputStreamWriter writer = new OutputStreamWriter(zipOut, "UTF-8");
      writer.write("ContentDigests-Version: 1.0\n\n");
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      for (Map.Entry<String, byte []> entry : manifestContents.entrySet()) {
        writer.write("Name: " + entry.getKey() + "\n");
        writer.write("SHA-1-Digest: "
            + Base64.encodeBytes(digest.digest(entry.getValue())) + "\n\n");
      }
      writer.flush();
      zipOut.closeEntry();

      for (Map.Entry<String, byte []> entry : contents.entrySet()) {
        zipOut.putNextEntry(new ZipEntry(entry.getKey()));
        zipOut.write(entry.getValue());
        zipOut.closeEntry();
      }
    } finally {
      zipOut.close();
    }
    return homeFile;
  }

  private void assertDamagedContentCount(String resourceName,
                                         int expectedInvalidContentCount)
      throws Exception {
    URL resource = HomeContentContextTest.class.getResource(
        "../junit/resources/" + resourceName);
    assertNotNull("Missing test resource " + resourceName, resource);
    DefaultHomeInputStream in = new DefaultHomeInputStream(
        new File(resource.toURI()), ContentRecording.INCLUDE_ALL_CONTENT,
        null, null, false);
    try {
      in.readHome();
      fail("Damaged home should not load without reporting invalid content");
    } catch (DamagedHomeIOException ex) {
      assertEquals("Wrong invalid content count for " + resourceName,
          expectedInvalidContentCount, ex.getInvalidContent().size());
    } finally {
      in.close();
    }
  }
}
