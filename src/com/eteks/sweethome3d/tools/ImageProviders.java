/*
 * ImageProviders.java 25 nov. 2026
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
package com.eteks.sweethome3d.tools;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.spi.ImageWriterSpi;

/**
 * Keeps the readers and writers of images of Java ahead of the ones registered by
 * other programs. A plug-in may register its own writer of PNG images, and the class
 * that reads or writes an image is otherwise picked in no particular order among the
 * ones which handle its format: a writer which fails, because a library it needs isn't
 * there anymore for instance, would make every image written by the application fail.
 * @author Sweet Home 3D contributors
 */
public class ImageProviders {
  private ImageProviders() {
    // Utility class
  }

  /**
   * Makes the readers and writers of images of Java preferred to the other ones
   * registered at this moment for the same formats. The other ones remain available
   * for the formats that Java doesn't handle and for the programs that ask for them.
   * This method may be called again after other providers were registered.
   * @return the count of providers registered by other programs
   */
  public static int preferPlatformProviders() {
    try {
      IIORegistry registry = IIORegistry.getDefaultInstance();
      return preferPlatformProviders(registry, ImageWriterSpi.class)
          + preferPlatformProviders(registry, ImageReaderSpi.class);
    } catch (RuntimeException ex) {
      // Images are read and written with the providers in the order of the registry
      return 0;
    } catch (LinkageError ex) {
      return 0;
    }
  }

  private static <T extends ImageReaderWriterSpi> int preferPlatformProviders(IIORegistry registry, Class<T> category) {
    List<T> platformProviders = new ArrayList<T>();
    List<T> otherProviders = new ArrayList<T>();
    for (Iterator<T> it = registry.getServiceProviders(category, false); it.hasNext(); ) {
      T provider = it.next();
      if (isPlatformProvider(provider)) {
        platformProviders.add(provider);
      } else {
        otherProviders.add(provider);
      }
    }
    for (T otherProvider : otherProviders) {
      for (T platformProvider : platformProviders) {
        if (isSameFormat(platformProvider, otherProvider)) {
          registry.setOrdering(category, platformProvider, otherProvider);
        }
      }
    }
    return otherProviders.size();
  }

  /**
   * Returns <code>true</code> if the class of the given provider belongs to Java.
   */
  private static boolean isPlatformProvider(Object provider) {
    ClassLoader classLoader = provider.getClass().getClassLoader();
    if (classLoader == null) {
      return true;
    }
    // The class loaders above the one of the application load the classes of Java
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    for (ClassLoader loader = systemClassLoader != null ? systemClassLoader.getParent() : null;
         loader != null; loader = loader.getParent()) {
      if (loader == classLoader) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSameFormat(ImageReaderWriterSpi provider1, ImageReaderWriterSpi provider2) {
    String [] formats1 = provider1.getFormatNames();
    String [] formats2 = provider2.getFormatNames();
    if (formats1 != null && formats2 != null) {
      for (String format1 : formats1) {
        for (String format2 : formats2) {
          if (format1 != null && format1.equalsIgnoreCase(format2)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
