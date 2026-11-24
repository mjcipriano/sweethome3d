/*
 * HomePDFPrinter.java 7 sept. 07
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

import java.awt.Font;
import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Date;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.viewcontroller.ContentManager;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Home PDF printer. PDF creation is implemented with {@link PDFDocumentWriter}.
 * @author Emmanuel Puybaret
 */
public class HomePDFPrinter {
  private final Home            home;
  private final UserPreferences preferences;
  private final HomeController  controller;
  private final Font            defaultFont;

  /**
   * Creates a PDF printer able to write to an output stream. 
   */
  public HomePDFPrinter(Home home, 
                        UserPreferences preferences, 
                        HomeController controller, 
                        Font defaultFont) {
    this.home = home;
    this.preferences = preferences;
    this.controller = controller;
    this.defaultFont = defaultFont;
  }

  /**
   * Writes to <code>outputStream</code> the print of a home in PDF format.
   */
  public void write(OutputStream outputStream) throws IOException {
    PageFormat pageFormat = HomePrintableComponent.getPageFormat(this.home.getPrint());
    try {
      PDFDocumentWriter pdfWriter = new PDFDocumentWriter(outputStream);

      // Set PDF document description
      pdfWriter.setAuthor(System.getProperty("user.name", ""));
      String pdfDocumentCreator = this.preferences.getLocalizedString(
          HomePDFPrinter.class, "pdfDocument.creator");
      pdfWriter.setCreator(pdfDocumentCreator);
      pdfWriter.setCreationDate(new Date());
      String homeName = this.home.getName();
      if (homeName != null) {
        pdfWriter.setTitle(this.controller.getContentManager().getPresentationName(
            homeName, ContentManager.ContentType.PDF));
      }

      HomePrintableComponent printableComponent =
          new HomePrintableComponent(this.home, this.controller, this.defaultFont);
      // Print each page
      for (int page = 0, pageCount = printableComponent.getPageCount(); page < pageCount; page++) {
        // Check current thread isn't interrupted
        if (Thread.interrupted()) {
          throw new InterruptedIOException();
        }
        Graphics g = pdfWriter.createPage((float)pageFormat.getWidth(),
            (float)pageFormat.getHeight());

        printableComponent.print(g, pageFormat, page);

        g.dispose();
      }
      pdfWriter.close();
    } catch (InterruptedPrinterException ex) {
      throw new InterruptedIOException("Print to PDF interrupted");
    } catch (PrinterException ex) {
      IOException exception = new IOException("Couldn't print to PDF");
      exception.initCause(ex);
      throw exception;
    }
  }
}
