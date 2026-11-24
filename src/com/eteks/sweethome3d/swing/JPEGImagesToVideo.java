/*
 * JPEGImagesToVideo.java
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
package com.eteks.sweethome3d.swing;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * A writer of QuickTime movies made of JPEG images, written from the description
 * of the QuickTime file format: the images are stored one after the other in the
 * <code>mdat</code> atom as they are produced, followed by the <code>moov</code> atom
 * which describes a video track of Photo - JPEG samples, all of them key frames.
 */
public class JPEGImagesToVideo {
  /**
   * The source of the images of a video.
   */
  public interface FrameSource {
    /**
     * Returns the next image of the video encoded at JPEG format,
     * or <code>null</code> if the video has no more images.
     */
    byte [] readFrame() throws IOException;
  }

  /** The count of seconds between the 1st of January 1904, origin of QuickTime dates, and the 1st of January 1970. */
  private static final long QUICKTIME_EPOCH_OFFSET = 2082844800L;
  /** The duration of an image in units of the time scale, itself equal to this value times the frame rate. */
  private static final int  FRAME_DURATION = 100;
  /** The matrix that leaves images as they are. */
  private static final int [] IDENTITY_MATRIX = {0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000};

  private static final int  FILE_TYPE_ATOM_LENGTH = 20;

  private volatile boolean cancelled;

  /**
   * Creates a video file at the given size and frame rate with the images read from
   * <code>source</code> until it returns <code>null</code>. The images are read in an
   * other thread while the calling thread waits, and each one is written as soon as
   * it's read, to keep only one of them in memory.
   * @throws InterruptedIOException if the calling thread is interrupted. No more image
   *     is read then, and the file is deleted once the image being read is back.
   */
  public void createVideoFile(final int width, final int height, final int frameRate,
                              final FrameSource source, final File file) throws IOException {
    if (width <= 0 || height <= 0 || width > 0xFFFF || height > 0xFFFF) {
      throw new IOException("Invalid video size " + width + " x " + height);
    } else if (frameRate <= 0) {
      throw new IOException("Invalid frame rate " + frameRate);
    }
    this.cancelled = false;
    final Throwable [] failure = {null};
    Thread writer = new Thread("Video file writer") {
        @Override
        public void run() {
          try {
            writeVideoFile(width, height, frameRate, source, file);
          } catch (Throwable ex) {
            failure [0] = ex;
          } finally {
            if (cancelled) {
              file.delete();
            }
          }
        }
      };
    writer.setPriority(Thread.currentThread().getPriority());
    writer.start();
    try {
      writer.join();
    } catch (InterruptedException ex) {
      this.cancelled = true;
      try {
        // Give a chance to the image being read to end
        writer.join(2000);
      } catch (InterruptedException ex2) {
      }
      // Leave the calling thread as an interrupted one for the source of images which checks it
      Thread.currentThread().interrupt();
      throw new InterruptedIOException("Video creation interrupted");
    }
    if (failure [0] instanceof IOException) {
      throw (IOException)failure [0];
    } else if (failure [0] instanceof RuntimeException) {
      throw (RuntimeException)failure [0];
    } else if (failure [0] instanceof Error) {
      throw (Error)failure [0];
    }
  }

  /**
   * Writes the images of <code>source</code> in a video file.
   */
  private void writeVideoFile(int width, int height, int frameRate, FrameSource source, File file) throws IOException {
    long [] frameOffsets = new long [1024];
    int []  frameSizes = new int [1024];
    int     frameCount = 0;
    long    position = 0;
    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), 1 << 16));
    try {
      // File type
      out.writeInt(FILE_TYPE_ATOM_LENGTH);
      out.writeBytes("ftyp");
      out.writeBytes("qt  ");
      out.writeInt(0);
      out.writeBytes("qt  ");
      // Room for the 64 bits length of a media data atom larger than 4 GB
      out.writeInt(8);
      out.writeBytes("wide");
      // Media data, its length being written once it's known
      out.writeInt(0);
      out.writeBytes("mdat");
      position = FILE_TYPE_ATOM_LENGTH + 16;
      while (true) {
        if (this.cancelled) {
          throw new InterruptedIOException("Video creation interrupted");
        }
        byte [] frame = source.readFrame();
        if (frame == null) {
          break;
        } else if (frame.length == 0) {
          throw new IOException("Empty image");
        }
        if (frameCount == frameSizes.length) {
          long [] offsets = new long [frameCount * 2];
          System.arraycopy(frameOffsets, 0, offsets, 0, frameCount);
          frameOffsets = offsets;
          int [] sizes = new int [frameCount * 2];
          System.arraycopy(frameSizes, 0, sizes, 0, frameCount);
          frameSizes = sizes;
        }
        frameOffsets [frameCount] = position;
        frameSizes [frameCount++] = frame.length;
        out.write(frame);
        position += frame.length;
      }
      if (frameCount == 0) {
        throw new IOException("No image");
      }
      out.write(getMovieAtom(width, height, frameRate, frameOffsets, frameSizes, frameCount));
    } finally {
      out.close();
    }

    // Write the length of media data
    long mediaDataLength = position - FILE_TYPE_ATOM_LENGTH - 8;
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    try {
      randomAccessFile.seek(FILE_TYPE_ATOM_LENGTH);
      if (mediaDataLength <= 0xFFFFFFFFL) {
        randomAccessFile.skipBytes(8);
        randomAccessFile.writeInt((int)mediaDataLength);
      } else {
        // The wide atom becomes the start of a media data atom with an extended length
        randomAccessFile.writeInt(1);
        randomAccessFile.writeBytes("mdat");
        randomAccessFile.writeLong(mediaDataLength + 8);
      }
    } finally {
      randomAccessFile.close();
    }
  }

  /**
   * Returns the <code>moov</code> atom which describes the images of a video.
   */
  private byte [] getMovieAtom(int width, int height, int frameRate,
                               long [] frameOffsets, int [] frameSizes, int frameCount) throws IOException {
    int  timeScale = frameRate * FRAME_DURATION;
    long longDuration = (long)frameCount * FRAME_DURATION;
    if (longDuration > Integer.MAX_VALUE) {
      throw new IOException("Too many images");
    }
    int  duration = (int)longDuration;
    int  date = (int)(System.currentTimeMillis() / 1000 + QUICKTIME_EPOCH_OFFSET);

    // Movie header
    Atom movieHeader = new Atom("mvhd");
    movieHeader.writeInt(0); // Version and flags
    movieHeader.writeInt(date); // Creation
    movieHeader.writeInt(date); // Modification
    movieHeader.writeInt(timeScale);
    movieHeader.writeInt(duration);
    movieHeader.writeInt(0x10000); // Normal rate
    movieHeader.writeShort(0x100); // Full volume
    movieHeader.write(new byte [10]); // Reserved
    for (int value : IDENTITY_MATRIX) {
      movieHeader.writeInt(value);
    }
    movieHeader.writeInt(0); // Preview time
    movieHeader.writeInt(0); // Preview duration
    movieHeader.writeInt(0); // Poster time
    movieHeader.writeInt(0); // Selection time
    movieHeader.writeInt(0); // Selection duration
    movieHeader.writeInt(0); // Current time
    movieHeader.writeInt(2); // Next track ID

    // Track header
    Atom trackHeader = new Atom("tkhd");
    trackHeader.writeInt(0xF); // Version 0, track enabled, used in movie, preview and poster
    trackHeader.writeInt(date);
    trackHeader.writeInt(date);
    trackHeader.writeInt(1); // Track ID
    trackHeader.writeInt(0); // Reserved
    trackHeader.writeInt(duration);
    trackHeader.write(new byte [8]); // Reserved
    trackHeader.writeShort(0); // Layer
    trackHeader.writeShort(0); // Alternate group
    trackHeader.writeShort(0); // Volume
    trackHeader.writeShort(0); // Reserved
    for (int value : IDENTITY_MATRIX) {
      trackHeader.writeInt(value);
    }
    trackHeader.writeInt(width << 16);
    trackHeader.writeInt(height << 16);

    // Media header
    Atom mediaHeader = new Atom("mdhd");
    mediaHeader.writeInt(0);
    mediaHeader.writeInt(date);
    mediaHeader.writeInt(date);
    mediaHeader.writeInt(timeScale);
    mediaHeader.writeInt(duration);
    mediaHeader.writeShort(0); // Language
    mediaHeader.writeShort(0); // Quality

    // Description of the images
    Atom sampleDescription = new Atom("stsd");
    sampleDescription.writeInt(0);
    sampleDescription.writeInt(1); // One description
    sampleDescription.writeInt(86); // Its length
    sampleDescription.writeBytes("jpeg"); // Photo - JPEG
    sampleDescription.write(new byte [6]); // Reserved
    sampleDescription.writeShort(1); // Data reference index
    sampleDescription.writeShort(0); // Version
    sampleDescription.writeShort(0); // Revision
    sampleDescription.writeInt(0); // Vendor
    sampleDescription.writeInt(0); // Temporal quality
    sampleDescription.writeInt(512); // Spatial quality
    sampleDescription.writeShort(width);
    sampleDescription.writeShort(height);
    sampleDescription.writeInt(72 << 16); // Horizontal resolution
    sampleDescription.writeInt(72 << 16); // Vertical resolution
    sampleDescription.writeInt(0); // Data size
    sampleDescription.writeShort(1); // One image per sample
    sampleDescription.writePascalString("Photo - JPEG", 32);
    sampleDescription.writeShort(24); // Depth
    sampleDescription.writeShort(-1); // No color table

    // All the images have the same duration
    Atom timeToSample = new Atom("stts");
    timeToSample.writeInt(0);
    timeToSample.writeInt(1);
    timeToSample.writeInt(frameCount);
    timeToSample.writeInt(FRAME_DURATION);

    // One image in each chunk
    Atom sampleToChunk = new Atom("stsc");
    sampleToChunk.writeInt(0);
    sampleToChunk.writeInt(1);
    sampleToChunk.writeInt(1); // First chunk
    sampleToChunk.writeInt(1); // Samples per chunk
    sampleToChunk.writeInt(1); // Description ID

    Atom sampleSizes = new Atom("stsz");
    sampleSizes.writeInt(0);
    sampleSizes.writeInt(0); // Sizes differ
    sampleSizes.writeInt(frameCount);
    for (int i = 0; i < frameCount; i++) {
      sampleSizes.writeInt(frameSizes [i]);
    }

    boolean longOffsets = frameOffsets [frameCount - 1] > 0xFFFFFFFFL;
    Atom chunkOffsets = new Atom(longOffsets ? "co64" : "stco");
    chunkOffsets.writeInt(0);
    chunkOffsets.writeInt(frameCount);
    for (int i = 0; i < frameCount; i++) {
      if (longOffsets) {
        chunkOffsets.writeLong(frameOffsets [i]);
      } else {
        chunkOffsets.writeInt((int)frameOffsets [i]);
      }
    }

    Atom videoMediaHeader = new Atom("vmhd");
    videoMediaHeader.writeInt(1); // Version 0 and flag set for QuickTime 1.0 and later
    videoMediaHeader.writeShort(0x40); // Dither copy
    videoMediaHeader.writeShort(0x8000); // Color of the operation
    videoMediaHeader.writeShort(0x8000);
    videoMediaHeader.writeShort(0x8000);

    // The data is in the file itself
    Atom dataReference = new Atom("dref");
    dataReference.writeInt(0);
    dataReference.writeInt(1);
    dataReference.writeInt(12);
    dataReference.writeBytes("alis");
    dataReference.writeInt(1);

    Atom movie = new Atom("moov").add(movieHeader).add(
        new Atom("trak").add(trackHeader).add(
            new Atom("mdia").add(mediaHeader).add(getHandlerAtom("mhlr", "vide", "Video media handler")).add(
                new Atom("minf").add(videoMediaHeader).add(getHandlerAtom("dhlr", "alis", "Data handler")).add(
                    new Atom("dinf").add(dataReference)).add(
                    new Atom("stbl").add(sampleDescription).add(timeToSample).add(sampleToChunk)
                        .add(sampleSizes).add(chunkOffsets)))));
    return movie.toByteArray();
  }

  /**
   * Returns a <code>hdlr</code> atom.
   */
  private Atom getHandlerAtom(String type, String subtype, String name) throws IOException {
    Atom handler = new Atom("hdlr");
    handler.writeInt(0);
    handler.writeBytes(type);
    handler.writeBytes(subtype);
    handler.writeInt(0); // Manufacturer
    handler.writeInt(0); // Flags
    handler.writeInt(0); // Flags mask
    handler.writePascalString(name, name.length() + 1);
    return handler;
  }

  /**
   * An atom of a QuickTime file: its length on 4 bytes, the 4 characters of its type, then its content.
   */
  private static class Atom {
    private final String                type;
    private final ByteArrayOutputStream content = new ByteArrayOutputStream();
    private final DataOutputStream      out = new DataOutputStream(this.content);

    public Atom(String type) {
      this.type = type;
    }

    public void writeInt(int value) throws IOException {
      this.out.writeInt(value);
    }

    public void writeLong(long value) throws IOException {
      this.out.writeLong(value);
    }

    public void writeShort(int value) throws IOException {
      this.out.writeShort(value);
    }

    public void write(byte [] bytes) throws IOException {
      this.out.write(bytes);
    }

    public void writeBytes(String text) throws IOException {
      this.out.writeBytes(text);
    }

    /**
     * Writes the length of <code>text</code> on a byte followed by its characters,
     * completed with zeros up to <code>length</code> bytes.
     */
    public void writePascalString(String text, int length) throws IOException {
      this.out.writeByte(text.length());
      this.out.writeBytes(text);
      this.out.write(new byte [length - 1 - text.length()]);
    }

    /**
     * Adds an atom to the content of this one.
     */
    public Atom add(Atom atom) throws IOException {
      atom.write(this.out);
      return this;
    }

    public void write(OutputStream out) throws IOException {
      DataOutputStream dataOut = new DataOutputStream(out);
      dataOut.writeInt(8 + this.content.size());
      dataOut.writeBytes(this.type);
      this.content.writeTo(dataOut);
      dataOut.flush();
    }

    public byte [] toByteArray() throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + this.content.size());
      write(bytes);
      return bytes.toByteArray();
    }
  }
}
