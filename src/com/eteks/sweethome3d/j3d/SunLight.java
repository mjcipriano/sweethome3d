/*
 * SunLight.java
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
package com.eteks.sweethome3d.j3d;

import javax.media.j3d.DirectionalLight;
import javax.vecmath.Color3f;
import javax.vecmath.Vector3f;

/**
 * The main directional light of a scene, which may be the Sun at a date and a time.
 * The OpenGL renderer then casts shadows from its exact direction, and may replace
 * the colors of the scene by false colors which show the light received by the surfaces.
 * @since 7.29
 */
public class SunLight extends DirectionalLight {
  private volatile boolean sun;
  private volatile float   directIlluminance;
  private volatile float   skyIlluminance;
  private volatile float   falseColorsMaximum;

  public SunLight(Color3f color, Vector3f direction) {
    super(color, direction);
  }

  /**
   * Sets whether the direction of this light is the one of the Sun.
   */
  public void setSun(boolean sun) {
    this.sun = sun;
  }

  /**
   * Returns <code>true</code> if the direction of this light is the one of the Sun.
   */
  public boolean isSun() {
    return this.sun;
  }

  /**
   * Sets the illuminance in lux received from the Sun by a surface which faces it,
   * and the one received from the sky by a horizontal surface.
   */
  public void setIlluminance(float directIlluminance, float skyIlluminance) {
    this.directIlluminance = directIlluminance;
    this.skyIlluminance = skyIlluminance;
  }

  public float getDirectIlluminance() {
    return this.directIlluminance;
  }

  public float getSkyIlluminance() {
    return this.skyIlluminance;
  }

  /**
   * Sets the illuminance in lux shown with the last of the false colors,
   * or 0 to display the scene with its own colors.
   */
  public void setFalseColorsMaximum(float falseColorsMaximum) {
    this.falseColorsMaximum = falseColorsMaximum;
  }

  public float getFalseColorsMaximum() {
    return this.falseColorsMaximum;
  }

  /**
   * Returns the red, green and blue components of the false color which shows the given
   * part of the maximum illuminance, between 0 and 1: from dark blue to cyan, green, yellow and red.
   * The shader of the OpenGL renderer uses the same colors.
   */
  public static float [] getFalseColor(float value) {
    float [][] colors = FALSE_COLORS;
    float position = Math.max(0, Math.min(1, value)) * (colors.length - 1);
    int index = Math.min((int)position, colors.length - 2);
    float part = position - index;
    return new float [] {
        colors [index][0] + (colors [index + 1][0] - colors [index][0]) * part,
        colors [index][1] + (colors [index + 1][1] - colors [index][1]) * part,
        colors [index][2] + (colors [index + 1][2] - colors [index][2]) * part};
  }

  static final float [][] FALSE_COLORS = {
      {0.08f, 0.05f, 0.35f},
      {0.10f, 0.30f, 0.85f},
      {0.05f, 0.70f, 0.85f},
      {0.20f, 0.80f, 0.30f},
      {0.95f, 0.90f, 0.15f},
      {0.98f, 0.55f, 0.10f},
      {0.85f, 0.10f, 0.10f}};
}
