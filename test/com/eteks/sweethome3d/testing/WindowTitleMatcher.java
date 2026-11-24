/*
 * WindowTitleMatcher.java
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
package com.eteks.sweethome3d.testing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;

/**
 * Matches the frames and dialogs displayed at screen with a given title.
 */
public class WindowTitleMatcher implements ComponentMatcher {
  private final String title;

  public WindowTitleMatcher(String title) {
    this.title = title;
  }

  public boolean matches(Component component) {
    String componentTitle = component instanceof Frame
        ? ((Frame)component).getTitle()
        : component instanceof Dialog
            ? ((Dialog)component).getTitle()
            : null;
    return componentTitle != null
        && componentTitle.equals(this.title)
        && component.isShowing();
  }

  @Override
  public String toString() {
    return "window titled " + this.title;
  }
}
