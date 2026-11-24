/*
 * JPEGImagesToVideoTest.java
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.eteks.sweethome3d.swing.JPEGImagesToVideo;

import junit.framework.TestCase;

/**
 * Tests the QuickTime movies written by {@link JPEGImagesToVideo}.
 */
public class JPEGImagesToVideoTest extends TestCase {
  private static final int WIDTH = 176;
  private static final int HEIGHT = 132;

  /**
   * Returns a JPEG image whose color and drawing depend on its index.
   */
  private static byte [] createFrame(int index) throws IOException {
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = image.createGraphics();
    g2D.setColor(new Color(index * 20 % 256, 80, 200 - index * 15 % 200));
    g2D.fillRect(0, 0, WIDTH, HEIGHT);
    g2D.setColor(Color.WHITE);
    g2D.fillOval(10 + index * 12, 40, 40, 40);
    g2D.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(image, "JPEG", out));
    return out.toByteArray();
  }

  /**
   * Returns the atoms found in <code>length</code> bytes of <code>file</code> from <code>start</code>,
   * the ones that contain other atoms being read too. Each key is the path of an atom
   * and each value the position and the length of its content.
   */
  private static Map<String, long []> readAtoms(RandomAccessFile file, long start, long length,
                                                String path, Map<String, long []> atoms) throws IOException {
    long position = start;
    while (position < start + length) {
      file.seek(position);
      long atomLength = file.readInt() & 0xFFFFFFFFL;
      byte [] type = new byte [4];
      file.readFully(type);
      int headerLength = 8;
      if (atomLength == 1) {
        atomLength = file.readLong();
        headerLength = 16;
      }
      assertTrue("Atom length " + atomLength, atomLength >= headerLength && position + atomLength <= start + length);
      String atomPath = path + new String(type, "ISO-8859-1");
      assertFalse(atomPath + " twice", atoms.containsKey(atomPath));
      atoms.put(atomPath, new long [] {position + headerLength, atomLength - headerLength});
      if (atomPath.matches("(moov|moov/trak|moov/trak/mdia|moov/trak/mdia/minf|moov/trak/mdia/minf/dinf|moov/trak/mdia/minf/stbl)")) {
        readAtoms(file, position + headerLength, atomLength - headerLength, atomPath + "/", atoms);
      }
      position += atomLength;
    }
    assertEquals(start + length, position);
    return atoms;
  }

  public void testMovieOfJPEGImages() throws IOException {
    final int frameCount = 12;
    final int frameRate = 25;
    final List<byte []> frames = new ArrayList<byte []>();
    for (int i = 0; i < frameCount; i++) {
      frames.add(createFrame(i));
    }
    final Thread testThread = Thread.currentThread();
    final Thread [] readingThread = {null};
    File file = File.createTempFile("video", ".mov");
    try {
      new JPEGImagesToVideo().createVideoFile(WIDTH, HEIGHT, frameRate, new JPEGImagesToVideo.FrameSource() {
          private int index;

          public byte [] readFrame() {
            readingThread [0] = Thread.currentThread();
            return this.index < frames.size() ? frames.get(this.index++) : null;
          }
        }, file);
      // Images are read in an other thread while the calling one waits
      assertNotNull(readingThread [0]);
      assertNotSame(testThread, readingThread [0]);

      RandomAccessFile in = new RandomAccessFile(file, "r");
      try {
        Map<String, long []> atoms = readAtoms(in, 0, in.length(), "", new LinkedHashMap<String, long []>());
        assertEquals("[ftyp, wide, mdat, moov, moov/mvhd, moov/trak, moov/trak/tkhd, moov/trak/mdia, "
            + "moov/trak/mdia/mdhd, moov/trak/mdia/hdlr, moov/trak/mdia/minf, moov/trak/mdia/minf/vmhd, "
            + "moov/trak/mdia/minf/hdlr, moov/trak/mdia/minf/dinf, moov/trak/mdia/minf/dinf/dref, "
            + "moov/trak/mdia/minf/stbl, moov/trak/mdia/minf/stbl/stsd, moov/trak/mdia/minf/stbl/stts, "
            + "moov/trak/mdia/minf/stbl/stsc, moov/trak/mdia/minf/stbl/stsz, moov/trak/mdia/minf/stbl/stco]",
            atoms.keySet().toString());

        // Media data contains the images and nothing else
        long imagesLength = 0;
        for (byte [] frame : frames) {
          imagesLength += frame.length;
        }
        assertEquals(imagesLength, atoms.get("mdat") [1]);

        // Durations
        in.seek(atoms.get("moov/mvhd") [0] + 12);
        int timeScale = in.readInt();
        int duration = in.readInt();
        assertEquals((float)frameCount / frameRate, (float)duration / timeScale, 1E-6f);
        in.seek(atoms.get("moov/trak/mdia/mdhd") [0] + 12);
        assertEquals(timeScale, in.readInt());
        assertEquals(duration, in.readInt());
        in.seek(atoms.get("moov/trak/tkhd") [0] + 20);
        assertEquals(duration, in.readInt());
        in.seek(atoms.get("moov/trak/tkhd") [0] + 76);
        assertEquals(WIDTH << 16, in.readInt());
        assertEquals(HEIGHT << 16, in.readInt());
        in.seek(atoms.get("moov/trak/mdia/minf/stbl/stts") [0] + 4);
        assertEquals(1, in.readInt());
        assertEquals(frameCount, in.readInt());
        assertEquals(duration / frameCount, in.readInt());

        // Description of the images
        long [] description = atoms.get("moov/trak/mdia/minf/stbl/stsd");
        assertEquals(8 + 86, description [1]);
        in.seek(description [0] + 4);
        assertEquals(1, in.readInt());
        assertEquals(86, in.readInt());
        byte [] codec = new byte [4];
        in.readFully(codec);
        assertEquals("jpeg", new String(codec, "ISO-8859-1"));
        in.seek(description [0] + 40);
        assertEquals(WIDTH, in.readShort());
        assertEquals(HEIGHT, in.readShort());

        // Each image is found where the tables tell it is
        long sizes = atoms.get("moov/trak/mdia/minf/stbl/stsz") [0];
        long offsets = atoms.get("moov/trak/mdia/minf/stbl/stco") [0];
        in.seek(sizes + 8);
        assertEquals(frameCount, in.readInt());
        in.seek(offsets + 4);
        assertEquals(frameCount, in.readInt());
        for (int i = 0; i < frameCount; i++) {
          in.seek(sizes + 12 + 4 * i);
          int size = in.readInt();
          in.seek(offsets + 8 + 4 * i);
          long offset = in.readInt() & 0xFFFFFFFFL;
          assertEquals(frames.get(i).length, size);
          assertTrue(offset >= atoms.get("mdat") [0] && offset + size <= atoms.get("mdat") [0] + atoms.get("mdat") [1]);
          byte [] frame = new byte [size];
          in.seek(offset);
          in.readFully(frame);
          assertTrue("Image " + i, java.util.Arrays.equals(frames.get(i), frame));
        }
        BufferedImage lastImage = ImageIO.read(new ByteArrayInputStream(frames.get(frameCount - 1)));
        assertEquals(WIDTH, lastImage.getWidth());
        assertEquals(HEIGHT, lastImage.getHeight());
      } finally {
        in.close();
      }
    } finally {
      file.delete();
    }
  }

  public void testInterruptedCreationDeletesFile() throws Exception {
    final File file = File.createTempFile("video", ".mov");
    final byte [] frame = createFrame(0);
    final CountDownLatch firstFrameRead = new CountDownLatch(1);
    final int [] readFrames = {0};
    final Throwable [] result = {null};
    Thread thread = new Thread() {
        @Override
        public void run() {
          try {
            new JPEGImagesToVideo().createVideoFile(WIDTH, HEIGHT, 25, new JPEGImagesToVideo.FrameSource() {
                public byte [] readFrame() {
                  readFrames [0]++;
                  firstFrameRead.countDown();
                  try {
                    Thread.sleep(50);
                  } catch (InterruptedException ex) {
                    fail("Thread reading images interrupted");
                  }
                  return frame;
                }
              }, file);
          } catch (Throwable ex) {
            result [0] = ex;
          }
        }
      };
    thread.start();
    assertTrue(firstFrameRead.await(10, TimeUnit.SECONDS));
    thread.interrupt();
    thread.join(10000);
    assertFalse(thread.isAlive());
    assertTrue(String.valueOf(result [0]), result [0] instanceof InterruptedIOException);
    assertFalse(file.exists());
    // No more image was asked after the one being read
    int count = readFrames [0];
    Thread.sleep(200);
    assertEquals(count, readFrames [0]);
  }

  public void testFailures() throws IOException {
    File file = File.createTempFile("video", ".mov");
    try {
      try {
        new JPEGImagesToVideo().createVideoFile(WIDTH, HEIGHT, 25, new JPEGImagesToVideo.FrameSource() {
            public byte [] readFrame() {
              return null;
            }
          }, file);
        fail("No image");
      } catch (IOException ex) {
        assertFalse(ex instanceof InterruptedIOException);
      }
      // The exception of the source is the one thrown
      final IOException exception = new IOException("Rendering failed");
      try {
        new JPEGImagesToVideo().createVideoFile(WIDTH, HEIGHT, 25, new JPEGImagesToVideo.FrameSource() {
            public byte [] readFrame() throws IOException {
              throw exception;
            }
          }, file);
        fail("Exception expected");
      } catch (IOException ex) {
        assertSame(exception, ex);
      }
      try {
        new JPEGImagesToVideo().createVideoFile(WIDTH, HEIGHT, 25, new JPEGImagesToVideo.FrameSource() {
            public byte [] readFrame() {
              throw new OutOfMemoryError("Too large");
            }
          }, file);
        fail("Error expected");
      } catch (OutOfMemoryError ex) {
        assertEquals("Too large", ex.getMessage());
      }
    } finally {
      file.delete();
    }
  }
}
