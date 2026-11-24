/*
 * ComponentFinder.java
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
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Searches a component among the ones a container or a window contains,
 * the windows it owns that are displayed and what they contain.
 */
public class ComponentFinder {
  /**
   * Returns the only component in the hierarchy of <code>root</code> that <code>matcher</code>
   * matches, <code>root</code> included. If <code>root</code> is <code>null</code>, all the
   * windows of the program are searched.
   * @throws ComponentLookupException if no component or more than one match
   */
  public Component find(Container root, ComponentMatcher matcher) throws ComponentLookupException {
    List<Component> found = new ArrayList<Component>();
    Map<Component, Boolean> visited = new IdentityHashMap<Component, Boolean>();
    if (root == null) {
      for (Window window : Window.getWindows()) {
        if (window.isShowing()) {
          search(window, matcher, found, visited);
        }
      }
    } else {
      search(root, matcher, found, visited);
    }
    if (found.isEmpty()) {
      throw new ComponentLookupException("No component matches " + matcher);
    } else if (found.size() > 1) {
      throw new ComponentLookupException(found.size() + " components match " + matcher);
    }
    return found.get(0);
  }

  private void search(Component component, ComponentMatcher matcher,
                      List<Component> found, Map<Component, Boolean> visited) {
    if (visited.put(component, Boolean.TRUE) != null) {
      return;
    }
    if (matcher.matches(component)) {
      found.add(component);
    }
    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        search(child, matcher, found, visited);
      }
    }
    if (component instanceof Window) {
      for (Window ownedWindow : ((Window)component).getOwnedWindows()) {
        // Not the dialogs that were closed
        if (ownedWindow.isShowing()) {
          search(ownedWindow, matcher, found, visited);
        }
      }
    }
  }
}
