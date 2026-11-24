/*
 * ClassComponentMatcher.java
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

/**
 * Matches the components of a class or of its subclasses.
 */
public class ClassComponentMatcher implements ComponentMatcher {
  private final Class<?> componentClass;
  private final boolean  showing;

  public ClassComponentMatcher(Class<?> componentClass) {
    this(componentClass, false);
  }

  /**
   * @param showing if <code>true</code>, only the components displayed at screen match
   */
  public ClassComponentMatcher(Class<?> componentClass, boolean showing) {
    this.componentClass = componentClass;
    this.showing = showing;
  }

  public boolean matches(Component component) {
    return this.componentClass.isInstance(component)
        && (!this.showing || component.isShowing());
  }

  @Override
  public String toString() {
    return (this.showing ? "showing " : "") + this.componentClass.getName();
  }
}
