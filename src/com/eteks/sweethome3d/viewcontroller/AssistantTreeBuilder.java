/*
 * AssistantTreeBuilder.java
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
package com.eteks.sweethome3d.viewcontroller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Builds a tree or a shrub that looks grown: branches that divide toward points scattered in the
 * measured shape of its crown (space colonization, after Runions, Lane and Prusinkiewicz), limbs
 * as thick as what they carry, bark, and clusters of leaves drawn on cards one sees through.
 * How each species grows is read in <code>AssistantPlantProfiles.properties</code>.
 * @author Sweet Home 3D
 */
final class AssistantTreeBuilder {
  private static final String PROFILES = "AssistantPlantProfiles.properties";
  private static Map<String, String> profiles;

  private final AssistantMeshBuilder       mesh;
  private final AssistantPlantBuilder.Plant plant;
  private final Random                     random;
  private final int                        detail;
  private final Map<String, String>        profile;
  private int                              triangles;

  // The crown
  private final AssistantPlantBuilder.Form form;
  private final float    height;
  private final float    crownBase;
  private final float    crownHeight;
  private final float    radiusX;
  private final float    radiusZ;
  private final float    leanX;
  private final float    leanZ;
  private final float [] phases = new float [3];
  private final float    irregularity;
  /** The share of the crown the ends of the branches stay in, to leave its edge to their leaves. */
  private final float    inset;

  // The skeleton
  private float [] nodeX;
  private float [] nodeY;
  private float [] nodeZ;
  private int []   nodeParent;
  private int []   nodeChildren;
  private float [] nodeRadius;
  private int      nodeCount;
  private int      trunkNodes;

  private AssistantTreeBuilder(AssistantMeshBuilder mesh, AssistantPlantBuilder.Plant plant, Random random, int detail) {
    this.mesh = mesh;
    this.plant = plant;
    this.random = random;
    this.detail = detail;
    this.profile = getProfile(plant.species, plant.profile);
    this.form = plant.species.form;
    this.height = plant.height;
    this.crownBase = Math.max(0, Math.min(plant.height * 0.92f, plant.crownBase));
    this.crownHeight = this.height - this.crownBase;
    this.radiusX = Math.max(1, plant.crownWidth / 2);
    this.radiusZ = Math.max(1, plant.crownDepth / 2);
    this.leanX = (float)(Math.sin(Math.toRadians(plant.leanAngle)) * Math.cos(Math.toRadians(plant.leanDirection - 90)));
    this.leanZ = (float)(Math.sin(Math.toRadians(plant.leanAngle)) * Math.sin(Math.toRadians(plant.leanDirection - 90)));
    for (int i = 0; i < this.phases.length; i++) {
      this.phases [i] = random.nextFloat() * (float)Math.PI * 2;
    }
    this.irregularity = number("irregularity", 0.15f);
    this.inset = Math.max(0.55f, 1 - number("card", 0.3f) * (detail == 0 ? 1 : 0.42f));
  }

  /**
   * Returns <code>true</code> if the plants of this form are built by this class.
   */
  static boolean builds(AssistantPlantBuilder.Form form) {
    return form != AssistantPlantBuilder.Form.PALM && form != AssistantPlantBuilder.Form.GRASS
        && form != AssistantPlantBuilder.Form.STUMP;
  }

  /**
   * Adds a plant to <code>mesh</code> and returns the count of its triangles.
   */
  static int build(AssistantMeshBuilder mesh, AssistantPlantBuilder.Plant plant, Random random, int detail) {
    AssistantTreeBuilder builder = new AssistantTreeBuilder(mesh, plant, random, detail);
    builder.build();
    return builder.triangles;
  }

  // ---- Profiles ----

  private static synchronized Map<String, String> getProfiles() {
    if (profiles == null) {
      Map<String, String> lines = new LinkedHashMap<String, String>();
      try {
        InputStream in = AssistantTreeBuilder.class.getResourceAsStream(PROFILES);
        if (in != null) {
          try {
            // Read in the order of the file
            final List<String> keys = new ArrayList<String>();
            Properties properties = new Properties() {
                @Override
                public synchronized Object put(Object key, Object value) {
                  keys.add(String.valueOf(key));
                  return super.put(key, value);
                }
              };
            properties.load(in);
            for (String key : keys) {
              lines.put(key, properties.getProperty(key));
            }
          } finally {
            in.close();
          }
        }
      } catch (IOException ex) {
        // Trees grow with the values of this class
      }
      profiles = lines;
    }
    return profiles;
  }

  private static void read(Map<String, String> profile, String line) {
    if (line != null) {
      for (String pair : line.trim().split("\\s+")) {
        int colon = pair.indexOf(':');
        if (colon > 0) {
          profile.put(pair.substring(0, colon), pair.substring(colon + 1));
        }
      }
    }
  }

  /**
   * Returns how a species grows: the values of its form, then the ones of the words of its name, its own,
   * and the ones given for a plant.
   */
  static Map<String, String> getProfile(AssistantPlantBuilder.Species species, Map<String, Object> overrides) {
    Map<String, String> profile = new LinkedHashMap<String, String>();
    Map<String, String> lines = getProfiles();
    read(profile, lines.get("form." + species.form.name()));
    for (Map.Entry<String, String> line : lines.entrySet()) {
      if (line.getKey().startsWith("match.") && species.name.contains(line.getKey().substring(6))) {
        read(profile, line.getValue());
      }
    }
    read(profile, lines.get("species." + species.name));
    if (overrides != null) {
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        if (entry.getValue() != null) {
          profile.put(entry.getKey(), entry.getValue().toString());
        }
      }
    }
    return profile;
  }

  /**
   * Returns the names of the pieces of the catalog that may show a species, the best first.
   */
  static List<String> getCatalogNames(AssistantPlantBuilder.Species species) {
    Map<String, String> lines = getProfiles();
    List<String> names = new ArrayList<String>();
    String kind = species.form == AssistantPlantBuilder.Form.PALM ? "palm" : species.shrub ? "shrub"
        : species.form == AssistantPlantBuilder.Form.PINE || species.form == AssistantPlantBuilder.Form.CONICAL
            || species.form == AssistantPlantBuilder.Form.COLUMNAR ? species.form.name()
        : species.height < 900 ? "small" : species.crownRatio < 0.5f ? "narrow" : "default";
    for (String key : new String [] {"catalog.species." + species.name, "catalog." + kind, "catalog.default"}) {
      String line = lines.get(key);
      if (line != null) {
        for (String name : line.split("/")) {
          if (name.trim().length() > 0 && !names.contains(name.trim())) {
            names.add(name.trim());
          }
        }
      }
    }
    return names;
  }

  /**
   * Returns the names of the values a profile has.
   */
  static List<String> getProfileKeys() {
    return java.util.Arrays.asList("excurrent", "trunk_top", "shell", "irregularity", "gaps", "clumps", "up", "droop", "pipe",
        "leaf", "card", "lift", "along", "density", "bark", "marcescent", "autumn");
  }

  private float number(String key, float defaultValue) {
    try {
      String value = this.profile.get(key);
      return value != null ? Float.parseFloat(value) : defaultValue;
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private String text(String key, String defaultValue) {
    String value = this.profile.get(key);
    return value != null && value.length() > 0 ? value : defaultValue;
  }

  // ---- The shape of the crown ----

  /**
   * Returns the radius of the crown at a height, from 0 at its base to 1 at its top, as a share of its largest radius.
   */
  private float getOutline(float t) {
    if (t < 0 || t > 1) {
      return 0;
    }
    switch (this.form) {
      case SPREADING :
        return (float)Math.pow(Math.sin(Math.PI * Math.pow(t, 0.6)), 0.5);
      case VASE :
        return t < 0.72f ? 0.28f + 0.72f * (float)Math.pow(t / 0.72f, 0.9)
            : (float)Math.pow(Math.cos((t - 0.72f) / 0.28f * Math.PI / 2), 0.6);
      case WEEPING :
        return t < 0.55f ? 0.86f + 0.14f * t / 0.55f : (float)Math.pow(Math.cos((t - 0.55f) / 0.45f * Math.PI / 2), 0.7);
      case COLUMNAR :
        return (float)(Math.pow(Math.min(1, t / 0.1), 0.7) * (t > 0.55f ? Math.pow(Math.cos((t - 0.55f) / 0.45f * Math.PI / 2), 0.55) : 1));
      case CONICAL :
        return (float)(Math.pow(Math.min(1, t / 0.08), 0.8) * Math.pow(1 - t, 0.85));
      case PYRAMIDAL :
        return (float)(Math.pow(Math.min(1, t / 0.12), 0.8) * Math.pow(1 - t, 0.6));
      case PINE :
        return t < 0.3f ? 0.4f + 0.6f * t / 0.3f : t < 0.78f ? 1 : (float)Math.pow(Math.cos((t - 0.78f) / 0.22f * Math.PI / 2), 0.4);
      case MOUND :
        return (float)(Math.pow(Math.cos(t * Math.PI / 2), 0.5) * Math.min(1, 0.65 + t / 0.08));
      case SNAG :
        return 0.6f * (float)Math.pow(Math.sin(Math.PI * Math.pow(t, 0.85)), 0.6);
      default :
        return (float)Math.pow(Math.sin(Math.PI * Math.pow(t, 0.85)), 0.6);
    }
  }

  /**
   * Returns how far the outline of the crown goes in or out in a direction at a height.
   */
  private float getBump(double angle, float t) {
    return 1 + this.irregularity * (float)(0.5 * Math.sin(2 * angle + this.phases [0])
        + 0.3 * Math.sin(3 * angle + this.phases [1] + 4 * t) + 0.2 * Math.sin(5 * angle + this.phases [2] - 6 * t));
  }

  /**
   * Returns where a point is in the crown: 0 on its axis, 1 on its outline, more out of it.
   */
  private float getDepth(float x, float y, float z) {
    float t = (y - this.crownBase) / Math.max(1, this.crownHeight);
    float outline = getOutline(t);
    if (outline <= 0.01f) {
      return 10;
    }
    float dx = x - this.leanX * y;
    float dz = z - this.leanZ * y;
    float bump = getBump(Math.atan2(dz, dx), t);
    return (float)Math.hypot(dx / (this.radiusX * outline * bump), dz / (this.radiusZ * outline * bump));
  }

  private float [] getPoint(float shell) {
    for (int attempt = 0; attempt < 60; attempt++) {
      float t = this.random.nextFloat();
      float outline = getOutline(t);
      if (this.random.nextFloat() > outline * outline + 0.03f) {
        continue;
      }
      double angle = this.random.nextFloat() * Math.PI * 2;
      float share = (float)Math.pow(this.random.nextFloat(), 1 / shell) * outline * getBump(angle, t) * this.inset;
      float y = this.crownBase + t * this.crownHeight * (0.5f + this.inset / 2);
      return new float [] {this.leanX * y + (float)Math.cos(angle) * this.radiusX * share, y,
                           this.leanZ * y + (float)Math.sin(angle) * this.radiusZ * share};
    }
    float y = this.crownBase + this.crownHeight / 2;
    return new float [] {this.leanX * y, y, this.leanZ * y};
  }

  // ---- The skeleton ----

  private int addNode(float x, float y, float z, int parent) {
    if (this.nodeCount == this.nodeX.length) {
      int capacity = this.nodeCount * 2;
      this.nodeX = java.util.Arrays.copyOf(this.nodeX, capacity);
      this.nodeY = java.util.Arrays.copyOf(this.nodeY, capacity);
      this.nodeZ = java.util.Arrays.copyOf(this.nodeZ, capacity);
      this.nodeParent = java.util.Arrays.copyOf(this.nodeParent, capacity);
      this.nodeChildren = java.util.Arrays.copyOf(this.nodeChildren, capacity);
    }
    this.nodeX [this.nodeCount] = x;
    this.nodeY [this.nodeCount] = y;
    this.nodeZ [this.nodeCount] = z;
    this.nodeParent [this.nodeCount] = parent;
    this.nodeChildren [this.nodeCount] = 0;
    if (parent >= 0) {
      this.nodeChildren [parent]++;
    }
    return this.nodeCount++;
  }

  private void build() {
    AssistantPlantBuilder.Species species = this.plant.species;
    String season = this.plant.season;
    boolean snag = this.form == AssistantPlantBuilder.Form.SNAG;
    boolean bare = snag || !species.evergreen && "winter".equals(season);
    boolean flowering = species.flower != 0 && !snag
        && (species.summerFlowers ? "summer".equals(season) : "spring".equals(season));
    float density = number("density", 1);
    float crownRadius = Math.min(this.radiusX, this.radiusZ);
    float trunkRadius = Math.max(0.6f, this.plant.trunkDiameter / 2);
    boolean excurrent = number("excurrent", 0) > 0.5f;

    // The points the branches grow to
    int pointCount = Math.round((this.detail == 0 ? 90 : this.detail == 1 ? 650 : 1500) * density * (bare && this.detail > 0 ? 0.8f : 1));
    float shell = Math.max(1, number("shell", 3));
    List<float []> points = new ArrayList<float []>(pointCount);
    int clumps = Math.round(number("clumps", 0));
    if (clumps > 0) {
      // Separate masses, as the crown of an old pine
      float [][] centers = new float [clumps][];
      for (int i = 0; i < clumps; i++) {
        centers [i] = getPoint(shell);
      }
      float clumpRadius = Math.max(this.radiusX, this.radiusZ) * 0.42f;
      for (int i = 0; i < pointCount * 4 && points.size() < pointCount; i++) {
        float [] center = centers [i % clumps];
        float [] point = {center [0] + (this.random.nextFloat() * 2 - 1) * clumpRadius,
                          center [1] + (this.random.nextFloat() * 2 - 1) * clumpRadius * 0.55f,
                          center [2] + (this.random.nextFloat() * 2 - 1) * clumpRadius};
        if (getDepth(point [0], point [1], point [2]) <= 1) {
          points.add(point);
        }
      }
    } else {
      for (int i = 0; i < pointCount; i++) {
        points.add(getPoint(shell));
      }
      // Holes in the crown
      float gaps = number("gaps", 0.1f);
      int removed = 0;
      for (int i = 0; i < 6 && removed < points.size() * gaps; i++) {
        float [] center = points.get(this.random.nextInt(points.size()));
        float gapRadius = crownRadius * (0.28f + this.random.nextFloat() * 0.16f);
        for (int k = points.size() - 1; k >= 0 && removed < points.size() * gaps; k--) {
          float [] point = points.get(k);
          if (Math.abs(point [0] - center [0]) < gapRadius && Math.abs(point [1] - center [1]) < gapRadius
              && Math.abs(point [2] - center [2]) < gapRadius) {
            points.remove(k);
            removed++;
          }
        }
      }
    }

    // The trunks
    float step = Math.max(this.height * 0.012f, Math.max(crownRadius, this.crownHeight * 0.3f)
        * (this.detail == 0 ? 0.15f : this.detail == 1 ? 0.06f : 0.042f));
    int capacity = this.detail == 0 ? 500 : this.detail == 1 ? 4000 : 9000;
    this.nodeX = new float [256];
    this.nodeY = new float [256];
    this.nodeZ = new float [256];
    this.nodeParent = new int [256];
    this.nodeChildren = new int [256];
    int trunks = Math.max(1, this.plant.trunks);
    float trunkTop = excurrent ? this.crownBase + this.crownHeight * (this.form == AssistantPlantBuilder.Form.PINE ? 0.86f : 0.95f)
            * (0.5f + this.inset / 2)
        : this.crownBase + this.crownHeight * number("trunk_top", 0.14f);
    trunkTop = Math.max(trunkTop, Math.min(this.height * 0.5f, step));
    List<Integer> roots = new ArrayList<Integer>();
    for (int i = 0; i < trunks; i++) {
      double angle = Math.PI * 2 * i / trunks + this.random.nextFloat();
      float spread = trunks == 1 ? 0 : this.radiusX * 0.3f;
      float baseOffset = trunks == 1 ? 0 : trunkRadius * 1.1f;
      float wobble = this.form == AssistantPlantBuilder.Form.PINE || snag ? crownRadius * 0.025f : crownRadius * 0.012f;
      int steps = Math.max(2, Math.round(trunkTop / step));
      int node = addNode((float)Math.cos(angle) * baseOffset, 0, (float)Math.sin(angle) * baseOffset, -1);
      roots.add(Integer.valueOf(node));
      float driftX = 0;
      float driftZ = 0;
      for (int k = 1; k <= steps; k++) {
        float t = (float)k / steps;
        float y = trunkTop * t;
        driftX += (this.random.nextFloat() - 0.5f) * wobble * (k > 1 ? 1 : 0);
        driftZ += (this.random.nextFloat() - 0.5f) * wobble * (k > 1 ? 1 : 0);
        // Trunks that leave each other as they rise
        float out = baseOffset + (spread - baseOffset) * (float)Math.pow(t, 1.4);
        node = addNode((float)Math.cos(angle) * out + this.leanX * y + driftX, y,
            (float)Math.sin(angle) * out + this.leanZ * y + driftZ, node);
      }
    }
    int trunkNodes = this.nodeCount;
    this.trunkNodes = trunkNodes;

    // Branches grow toward the closest points until each one is reached
    float up = number("up", 0.1f);
    float droop = number("droop", 0.05f);
    float reach = step * 1.2f;
    int pointsLeft = points.size();
    boolean [] reached = new boolean [points.size()];
    float [] growX = new float [capacity];
    float [] growY = new float [capacity];
    float [] growZ = new float [capacity];
    int [] growCount = new int [capacity];
    float [] lastX = new float [capacity];
    float [] lastY = new float [capacity];
    float [] lastZ = new float [capacity];
    boolean [] grew = new boolean [capacity];
    int maxIterations = Math.round(Math.max(this.crownHeight, crownRadius * 2) / step * 2.5f) + 10;
    if (this.detail == 0 && excurrent) {
      // With little detail, a branch from the trunk to each point: too few steps would leave the top bare
      for (int p = 0; p < reached.length; p++) {
        float [] point = points.get(p);
        float away = (float)Math.hypot(point [0] - this.leanX * point [1], point [2] - this.leanZ * point [1]);
        int from = 0;
        float best = Float.MAX_VALUE;
        for (int n = 0; n < trunkNodes; n++) {
          float distance = Math.abs(this.nodeY [n] - (point [1] - away * 0.35f));
          if (distance < best) {
            best = distance;
            from = n;
          }
        }
        addNode(point [0], point [1], point [2], from);
        reached [p] = true;
      }
      pointsLeft = 0;
    }
    for (int iteration = 0; iteration < maxIterations && pointsLeft > 0 && this.nodeCount < capacity - 1; iteration++) {
      int nodes = this.nodeCount;
      java.util.Arrays.fill(growCount, 0, nodes, 0);
      for (int p = 0; p < reached.length; p++) {
        if (reached [p]) {
          continue;
        }
        float [] point = points.get(p);
        int nearest = -1;
        float nearestDistance = Float.MAX_VALUE;
        for (int n = 0; n < nodes; n++) {
          float dy = point [1] - this.nodeY [n];
          if (dy > -nearestDistance && dy < nearestDistance) {
            float dx = point [0] - this.nodeX [n];
            float dz = point [2] - this.nodeZ [n];
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance * nearestDistance) {
              nearestDistance = (float)Math.sqrt(distance);
              nearest = n;
            }
          }
        }
        if (nearestDistance < reach) {
          reached [p] = true;
          pointsLeft--;
        } else if (nearest >= 0) {
          if (growCount [nearest] == 0) {
            growX [nearest] = growY [nearest] = growZ [nearest] = 0;
          }
          growX [nearest] += (point [0] - this.nodeX [nearest]) / nearestDistance;
          growY [nearest] += (point [1] - this.nodeY [nearest]) / nearestDistance;
          growZ [nearest] += (point [2] - this.nodeZ [nearest]) / nearestDistance;
          growCount [nearest]++;
        }
      }
      boolean added = false;
      for (int n = 0; n < nodes && this.nodeCount < capacity - 1; n++) {
        if (growCount [n] == 0) {
          continue;
        }
        float x = growX [n] / growCount [n];
        float y = growY [n] / growCount [n];
        float z = growZ [n] / growCount [n];
        float away = (float)Math.hypot(this.nodeX [n] - this.leanX * this.nodeY [n], this.nodeZ [n] - this.leanZ * this.nodeY [n])
            / Math.max(1, Math.max(this.radiusX, this.radiusZ));
        y += up - droop * Math.min(1.5f, away * 1.5f);
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        if (length < 0.05f) {
          continue;
        }
        x /= length;
        y /= length;
        z /= length;
        if (grew [n] && x * lastX [n] + y * lastY [n] + z * lastZ [n] > 0.97f) {
          // It already grew that way
          continue;
        }
        grew [n] = true;
        lastX [n] = x;
        lastY [n] = y;
        lastZ [n] = z;
        addNode(this.nodeX [n] + x * step, Math.max(this.height * 0.02f, Math.min(this.height, this.nodeY [n] + y * step)),
            this.nodeZ [n] + z * step, n);
        added = true;
      }
      if (!added) {
        break;
      }
    }

    // Limbs that bend rather than turn at each step
    for (int pass = 0; pass < 2; pass++) {
      float [] sumX = new float [this.nodeCount];
      float [] sumY = new float [this.nodeCount];
      float [] sumZ = new float [this.nodeCount];
      for (int n = 0; n < this.nodeCount; n++) {
        int parent = this.nodeParent [n];
        if (parent >= 0) {
          sumX [parent] += this.nodeX [n];
          sumY [parent] += this.nodeY [n];
          sumZ [parent] += this.nodeZ [n];
        }
      }
      for (int n = this.nodeCount - 1; n >= 0; n--) {
        int parent = this.nodeParent [n];
        if (parent >= 0 && this.nodeChildren [n] == 1) {
          this.nodeX [n] = this.nodeX [n] * 0.5f + (this.nodeX [parent] + sumX [n]) * 0.25f;
          this.nodeY [n] = this.nodeY [n] * 0.5f + (this.nodeY [parent] + sumY [n]) * 0.25f;
          this.nodeZ [n] = this.nodeZ [n] * 0.5f + (this.nodeZ [parent] + sumZ [n]) * 0.25f;
        }
      }
    }

    // Limbs as thick as the ends they carry
    int [] ends = new int [this.nodeCount];
    for (int n = this.nodeCount - 1; n >= 0; n--) {
      if (this.nodeChildren [n] == 0) {
        ends [n] = 1;
      }
      if (this.nodeParent [n] >= 0) {
        ends [this.nodeParent [n]] += ends [n];
      }
    }
    float pipe = Math.max(1.5f, Math.min(3.5f, number("pipe", 2.4f)));
    float rootRadius = trunks == 1 ? trunkRadius : trunkRadius / (float)Math.sqrt(trunks) * 1.2f;
    float twigRadius = Math.max(0.3f, rootRadius * 0.025f);
    this.nodeRadius = new float [this.nodeCount];
    int [] rootOf = new int [this.nodeCount];
    for (int n = 0; n < this.nodeCount; n++) {
      rootOf [n] = this.nodeParent [n] < 0 ? n : rootOf [this.nodeParent [n]];
      float radius = rootRadius * (float)Math.pow((double)ends [n] / Math.max(1, ends [rootOf [n]]), 1 / pipe);
      if (n < trunkNodes && this.crownBase > 1 && this.nodeY [n] < this.crownBase) {
        // A trunk that widens to the ground
        radius = Math.max(radius, rootRadius * (0.8f + 0.2f * (1 - this.nodeY [n] / this.crownBase)));
        float flare = this.height * 0.045f;
        if (this.nodeY [n] < flare) {
          radius *= 1 + 0.4f * (1 - this.nodeY [n] / flare) * (1 - this.nodeY [n] / flare);
        }
      }
      this.nodeRadius [n] = Math.max(twigRadius, radius);
    }

    AssistantMeshBuilder.Material bark = this.detail == 0 ? this.mesh.getMaterial(species.bark)
        : this.mesh.getMaterial(text("bark", "bark"), 40, 80, species.bark, AssistantPlantBuilder.shade(species.bark, 0.74f), 0);
    addLimbs(bark, rootRadius, bare);
    if (!snag) {
      addFoliage(bare, flowering, trunkNodes, density);
    }
  }

  /**
   * Returns <code>true</code> if a node only continues a limb that goes straight.
   */
  private boolean isStraight(int node, float minCos) {
    int parent = this.nodeParent [node];
    if (parent < 0 || this.nodeChildren [node] != 1) {
      return false;
    }
    int grandParent = this.nodeParent [parent];
    if (grandParent < 0) {
      return false;
    }
    float x1 = this.nodeX [parent] - this.nodeX [grandParent];
    float y1 = this.nodeY [parent] - this.nodeY [grandParent];
    float z1 = this.nodeZ [parent] - this.nodeZ [grandParent];
    float x2 = this.nodeX [node] - this.nodeX [parent];
    float y2 = this.nodeY [node] - this.nodeY [parent];
    float z2 = this.nodeZ [node] - this.nodeZ [parent];
    double lengths = Math.sqrt((x1 * x1 + y1 * y1 + z1 * z1) * (x2 * x2 + y2 * y2 + z2 * z2));
    return lengths > 0 && (x1 * x2 + y1 * y2 + z1 * z2) / lengths >= minCos;
  }

  private void addLimbs(AssistantMeshBuilder.Material bark, float rootRadius, boolean bare) {
    // The thinnest limbs are left to the leaves, more of them when there are too many
    float share = bare ? (this.detail == 0 ? 0.1f : this.detail == 1 ? 0.03f : 0)
        : this.detail == 0 ? 0.2f : this.detail == 1 ? 0.075f : 0.035f;
    int budget = this.detail == 0 ? 700 : this.detail == 1 ? (bare ? 6000 : 2600) : (bare ? 22000 : 10000);
    float minCos = (float)Math.cos(Math.toRadians(this.detail == 0 ? 24 : this.detail == 1 ? 13 : 8));
    int [] sides = this.detail == 0 ? new int [] {5, 4, 3} : this.detail == 1 ? new int [] {7, 5, 3} : new int [] {10, 6, 4};
    // A node that continues a straight limb is skipped, the limb drawn in one piece
    boolean [] skipped = new boolean [this.nodeCount];
    for (int n = 0; n < this.nodeCount; n++) {
      if (this.nodeChildren [n] == 1) {
        for (int child = n + 1; child < this.nodeCount; child++) {
          if (this.nodeParent [child] == n) {
            skipped [n] = this.nodeParent [n] >= 0 && isStraight(child, minCos) && this.nodeY [n] > this.height * 0.06f;
            break;
          }
        }
      }
    }
    for (int attempt = 0; attempt < 12; attempt++) {
      int count = 0;
      for (int n = 0; n < this.nodeCount; n++) {
        if (this.nodeParent [n] >= 0 && !skipped [n] && (n < this.trunkNodes || this.nodeRadius [n] >= rootRadius * share)) {
          float radius = this.nodeRadius [n];
          count += 2 * (radius >= rootRadius * 0.5f ? sides [0] : radius >= rootRadius * 0.15f ? sides [1] : sides [2]);
        }
      }
      if (count <= budget) {
        break;
      }
      share = Math.max(share * 1.35f, 0.02f);
    }
    for (int n = 0; n < this.nodeCount; n++) {
      if (this.nodeParent [n] < 0 || skipped [n] || n >= this.trunkNodes && this.nodeRadius [n] < rootRadius * share) {
        continue;
      }
      int previous = n;
      int start = this.nodeParent [n];
      while (skipped [start]) {
        previous = start;
        start = this.nodeParent [start];
      }
      float radius = this.nodeRadius [n];
      float startRadius = Math.min(this.nodeRadius [start], this.nodeRadius [previous] * 1.12f);
      addTube(new float [] {this.nodeX [start], this.nodeY [start], this.nodeZ [start]},
          new float [] {this.nodeX [n], this.nodeY [n], this.nodeZ [n]}, startRadius,
          this.nodeChildren [n] == 0 ? radius * 0.4f : radius,
          radius >= rootRadius * 0.5f ? sides [0] : radius >= rootRadius * 0.15f ? sides [1] : sides [2], bark);
    }
  }

  /**
   * Adds a part of a limb, its bark laid around it.
   */
  private void addTube(float [] start, float [] end, float startRadius, float endRadius, int sides, AssistantMeshBuilder.Material bark) {
    float dx = end [0] - start [0];
    float dy = end [1] - start [1];
    float dz = end [2] - start [2];
    float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 0.5f) {
      return;
    }
    float [] u = Math.abs(dy) > 0.9f * length ? new float [] {1, 0, 0} : new float [] {-dz, 0, dx};
    float uLength = (float)Math.sqrt(u [0] * u [0] + u [1] * u [1] + u [2] * u [2]);
    u = new float [] {u [0] / uLength, u [1] / uLength, u [2] / uLength};
    float [] v = {(dy * u [2] - dz * u [1]) / length, (dz * u [0] - dx * u [2]) / length, (dx * u [1] - dy * u [0]) / length};
    if (start [1] > 1) {
      // It starts in the limb that carries it, to leave no gap where they meet
      float overlap = Math.min(length * 0.3f, startRadius * 0.6f) / length;
      start = new float [] {start [0] - dx * overlap, start [1] - dy * overlap, start [2] - dz * overlap};
    }
    int [] bottom = new int [sides];
    int [] top = new int [sides];
    for (int i = 0; i < sides; i++) {
      double angle = Math.PI * 2 * i / sides;
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      bottom [i] = this.mesh.addVertex(start [0] + (u [0] * cos + v [0] * sin) * startRadius,
          start [1] + (u [1] * cos + v [1] * sin) * startRadius, start [2] + (u [2] * cos + v [2] * sin) * startRadius);
      top [i] = this.mesh.addVertex(end [0] + (u [0] * cos + v [0] * sin) * endRadius,
          end [1] + (u [1] * cos + v [1] * sin) * endRadius, end [2] + (u [2] * cos + v [2] * sin) * endRadius);
    }
    // The bark turns around the limb a whole number of times
    float around = Math.max(1, Math.round(Math.PI * (startRadius + endRadius) / 40));
    float along = length / 80;
    float offset = this.random.nextFloat();
    float [] center = {(start [0] + end [0]) / 2, (start [1] + end [1]) / 2, (start [2] + end [2]) / 2};
    for (int i = 0; i < sides; i++) {
      int next = (i + 1) % sides;
      int [] indices = {bottom [i], bottom [next], top [next], top [i]};
      float [][] coordinates = {{around * i / sides, offset}, {around * (i + 1) / sides, offset},
                                {around * (i + 1) / sides, offset + along}, {around * i / sides, offset + along}};
      float [] normal = this.mesh.getNormal(indices);
      if (normal == null) {
        continue;
      }
      float [] middle = this.mesh.getCenter(indices);
      if (normal [0] * (middle [0] - center [0]) + normal [1] * (middle [1] - center [1]) + normal [2] * (middle [2] - center [2]) < 0) {
        AssistantMeshBuilder.reverse(indices);
        coordinates = new float [][] {coordinates [3], coordinates [2], coordinates [1], coordinates [0]};
      }
      this.mesh.addFace(indices, bark, true, bark.isTextured() ? coordinates : null, null);
      this.triangles += 2;
    }
  }

  // ---- Leaves ----

  private static int mix(int color1, int color2, float ratio) {
    int red = Math.round(((color1 >> 16) & 0xFF) * (1 - ratio) + ((color2 >> 16) & 0xFF) * ratio);
    int green = Math.round(((color1 >> 8) & 0xFF) * (1 - ratio) + ((color2 >> 8) & 0xFF) * ratio);
    int blue = Math.round((color1 & 0xFF) * (1 - ratio) + (color2 & 0xFF) * ratio);
    return red << 16 | green << 8 | blue;
  }

  private void addFoliage(boolean bare, boolean flowering, int trunkNodes, float density) {
    AssistantPlantBuilder.Species species = this.plant.species;
    String season = this.plant.season;
    String kind = text("leaf", "ovate");
    float leafShare = 1;
    float flowerShare = 0;
    int [] colors;
    if (bare) {
      // Twigs, with the dead leaves some trees keep and the flowers that come before the leaves
      float marcescent = number("marcescent", 0);
      if (this.detail == 0 && marcescent <= 0 && !flowering) {
        return;
      }
      kind = "twigs";
      leafShare = marcescent;
      colors = new int [] {0x8E6C42, 0x9C7A4C, 0x7A5A36};
    } else if (!species.evergreen && "autumn".equals(season)) {
      List<Integer> list = new ArrayList<Integer>();
      list.add(Integer.valueOf(species.autumnLeaf));
      String more = this.profile.get("autumn");
      if (more != null) {
        for (String color : more.split("/")) {
          try {
            list.add(Integer.valueOf(Integer.parseInt(color.trim(), 16)));
          } catch (NumberFormatException ex) {
            // The other colors are kept
          }
        }
      } else {
        list.add(Integer.valueOf(mix(species.autumnLeaf, 0xD08A20, 0.45f)));
        list.add(Integer.valueOf(mix(species.autumnLeaf, 0x7A3020, 0.35f)));
      }
      // Some leaves that didn't turn yet
      list.add(Integer.valueOf(mix(species.leaf, species.autumnLeaf, 0.45f)));
      colors = new int [list.size()];
      for (int i = 0; i < colors.length; i++) {
        colors [i] = list.get(i).intValue();
      }
      leafShare = 0.85f;
    } else {
      int leaf = species.leaf;
      if ("spring".equals(season) && !species.evergreen) {
        leaf = AssistantPlantBuilder.shade(mix(leaf, 0xA8D060, 0.35f), 1.08f);
      } else if ("winter".equals(season)) {
        leaf = AssistantPlantBuilder.shade(mix(leaf, 0x6A6A50, 0.15f), 0.95f);
      }
      colors = new int [] {leaf, AssistantPlantBuilder.shade(leaf, 1.12f), AssistantPlantBuilder.shade(leaf, 0.86f),
                           mix(leaf, 0xB4C454, 0.28f), mix(leaf, 0x2E4A2A, 0.3f)};
    }
    if (flowering) {
      flowerShare = bare ? 0.9f : species.summerFlowers ? 0.45f : 0.55f;
    }
    boolean centered = kind.equals("tuft");

    // Three tones of the same cluster: the heart of the crown, its outside, its top in the light
    AssistantMeshBuilder.Material [] materials = new AssistantMeshBuilder.Material [3];
    float [] tones = species.evergreen && !kind.equals("ovate") ? new float [] {1.1f, 1.45f, 1.75f} : new float [] {1, 1.3f, 1.55f};
    for (int i = 0; i < materials.length; i++) {
      int tone = this.detail == 0 && i != 1 ? 1 : i;
      if (tone != i) {
        materials [i] = materials [1] != null ? materials [1] : null;
        continue;
      }
      try {
        int [] pixels = AssistantLeafTextures.createPixels(kind, colors, species.bark, flowering ? species.flower : 0, flowerShare, leafShare,
            tones [i], this.detail == 0 ? 0.5f : 1, this.plant.seed * 31 + species.name.hashCode() + i * 7);
        byte [] image = AssistantLeafTextures.encode(pixels, AssistantLeafTextures.SIZE);
        int iconColor = flowering && bare ? species.flower : bare ? AssistantPlantBuilder.shade(species.bark, 1.1f)
            : AssistantPlantBuilder.shade(colors [0], tones [i]);
        materials [i] = this.mesh.getImageMaterial(kind + "_" + season + "_" + i, image, iconColor, pixels, AssistantLeafTextures.SIZE);
      } catch (IOException ex) {
        materials [i] = this.mesh.getMaterial(AssistantPlantBuilder.shade(colors [0], tones [i]));
      }
    }
    if (materials [0] == null) {
      materials [0] = materials [1];
    }
    if (materials [2] == null) {
      materials [2] = materials [1];
    }

    // Where the clusters go: the ends of the branches, and along the thin ones
    boolean along = number("along", 0) > 0.5f;
    List<Integer> places = new ArrayList<Integer>();
    for (int n = trunkNodes; n < this.nodeCount; n++) {
      if (this.nodeChildren [n] == 0) {
        places.add(Integer.valueOf(n));
      }
    }
    int ends = places.size();
    int wanted = Math.min(this.detail == 0 ? 90 : this.detail == 1 ? 740 : 3100,
        Math.round((this.detail == 0 ? 60 : this.detail == 1 ? (bare ? 360 : 500) : (bare ? 1100 : 2200)) * density));
    if (number("excurrent", 0) > 0.5f) {
      // The top of the trunk carries leaves too
      for (int n = 1; n < trunkNodes; n++) {
        if (n == trunkNodes - 1 || this.nodeParent [n + 1] < 0) {
          places.add(Integer.valueOf(n));
        }
      }
    }
    if (along || places.size() < wanted * 0.75f) {
      float thin = this.nodeRadius [0] * (this.detail == 0 ? 0.4f : 0.16f);
      for (int n = trunkNodes; n < this.nodeCount && places.size() < wanted * 1.6f; n++) {
        if (this.nodeChildren [n] > 0 && this.nodeRadius [n] < thin && (along || n % 2 == 0)) {
          places.add(Integer.valueOf(n));
        }
      }
    }
    if (places.isEmpty()) {
      // A plant too small to have branches
      places.add(Integer.valueOf(this.nodeCount - 1));
    }
    // Fewer and larger clusters when there are too many of them
    java.util.Collections.shuffle(places, new Random(this.plant.seed * 131 + 17));
    float crownRadius = Math.min(this.radiusX, this.radiusZ);
    float size = Math.max(this.height * 0.03f, crownRadius * number("card", 0.3f)) * (bare ? 1.2f : 1)
        * (this.detail == 0 ? 1.8f : this.detail == 1 ? 1 : 0.78f);
    if (places.size() > wanted) {
      places = places.subList(0, wanted);
    }
    float lift = number("lift", 0.3f);
    float droop = number("droop", 0.05f);
    float centerY = this.crownBase + this.crownHeight * 0.45f;
    for (Integer place : places) {
      int n = place.intValue();
      int parent = Math.max(0, this.nodeParent [n]);
      float x = this.nodeX [n];
      float y = this.nodeY [n];
      float z = this.nodeZ [n];
      // The way the branch goes, and the way out of the crown
      float [] growth = normalize(x - this.nodeX [parent], y - this.nodeY [parent], z - this.nodeZ [parent]);
      float [] out = normalize(x - this.leanX * y, 0, z - this.leanZ * y);
      float hang = droop > 0.5f ? droop : 0;
      float [] upward = normalize(growth [0] * 0.5f + out [0] * 0.5f + (this.random.nextFloat() - 0.5f) * 0.5f,
          growth [1] * 0.5f + lift - hang + (this.random.nextFloat() - 0.5f) * 0.3f,
          growth [2] * 0.5f + out [2] * 0.5f + (this.random.nextFloat() - 0.5f) * 0.5f);
      float [] any = normalize(this.random.nextFloat() - 0.5f, this.random.nextFloat() - 0.5f, this.random.nextFloat() - 0.5f);
      float [] side = normalize(upward [1] * any [2] - upward [2] * any [1], upward [2] * any [0] - upward [0] * any [2],
          upward [0] * any [1] - upward [1] * any [0]);
      float [] side2 = {upward [1] * side [2] - upward [2] * side [1], upward [2] * side [0] - upward [0] * side [2],
                        upward [0] * side [1] - upward [1] * side [0]};
      float clusterSize = size * (0.8f + this.random.nextFloat() * 0.45f);
      float depth = getDepth(x, y, z);
      float topShare = (y - this.crownBase) / Math.max(1, this.crownHeight);
      AssistantMeshBuilder.Material material = depth < 0.5f || this.random.nextFloat() < 0.14f ? materials [0]
          : depth > 0.75f && topShare > 0.5f && this.random.nextFloat() < 0.6f ? materials [2] : materials [1];
      // Lit as a part of the round mass of the crown
      float [] normal = normalize((x - this.leanX * centerY) * 0.6f, (y - centerY) * 0.6f + crownRadius * 0.7f,
          (z - this.leanZ * centerY) * 0.6f);
      int quads = this.detail == 2 && this.random.nextFloat() < 0.35f ? 3 : 2;
      for (int q = 0; q < quads; q++) {
        double turn = Math.PI * q / quads;
        float cos = (float)Math.cos(turn);
        float sin = (float)Math.sin(turn);
        float [] across = {side [0] * cos + side2 [0] * sin, side [1] * cos + side2 [1] * sin, side [2] * cos + side2 [2] * sin};
        float back = centered ? 0.5f : 0.1f;
        addCard(new float [] {x - upward [0] * clusterSize * back, y - upward [1] * clusterSize * back, z - upward [2] * clusterSize * back},
            across, upward, clusterSize, material, normal);
      }
      if (!centered && number("excurrent", 0) < 0.5f && (this.detail == 0 || topShare > 0.45f && this.random.nextFloat() < 0.5f)
          || centered && this.random.nextFloat() < 0.4f) {
        // A cluster that lies nearly flat, for who looks from above
        float [] flat = normalize(side2 [0], side2 [1] * 0.4f, side2 [2]);
        float [] flatSide = normalize(flat [2], 0, -flat [0]);
        float [] rise = normalize(flat [0], 0.3f, flat [2]);
        addCard(new float [] {x - rise [0] * clusterSize * 0.5f, y + clusterSize * (centered ? 0.05f : 0.3f), z - rise [2] * clusterSize * 0.5f},
            flatSide, rise, clusterSize, material, new float [] {normal [0] * 0.5f, 0.86f, normal [2] * 0.5f});
      }
    }
  }

  private static float [] normalize(float x, float y, float z) {
    float length = (float)Math.sqrt(x * x + y * y + z * z);
    return length < 1E-5f ? new float [] {0, 1, 0} : new float [] {x / length, y / length, z / length};
  }

  /**
   * Adds a card seen from both sides: its picture stands on <code>base</code>, the middle of its bottom side.
   */
  private void addCard(float [] base, float [] across, float [] upward, float size, AssistantMeshBuilder.Material material, float [] normal) {
    float half = size / 2;
    int a = this.mesh.addVertex(base [0] - across [0] * half, Math.max(0, base [1] - across [1] * half), base [2] - across [2] * half);
    int b = this.mesh.addVertex(base [0] + across [0] * half, Math.max(0, base [1] + across [1] * half), base [2] + across [2] * half);
    float top = this.height * 1.02f;
    int c = this.mesh.addVertex(base [0] + across [0] * half + upward [0] * size,
        Math.max(0, Math.min(top, base [1] + across [1] * half + upward [1] * size)), base [2] + across [2] * half + upward [2] * size);
    int d = this.mesh.addVertex(base [0] - across [0] * half + upward [0] * size,
        Math.max(0, Math.min(top, base [1] - across [1] * half + upward [1] * size)), base [2] - across [2] * half + upward [2] * size);
    boolean mirrored = this.random.nextBoolean();
    float left = mirrored ? 1 : 0;
    float [][] coordinates = material.isTextured() ? new float [][] {{left, 0}, {1 - left, 0}, {1 - left, 1}, {left, 1}} : null;
    float [][] reversed = coordinates != null ? new float [][] {coordinates [3], coordinates [2], coordinates [1], coordinates [0]} : null;
    this.mesh.addFace(new int [] {a, b, c, d}, material, false, coordinates, normal);
    this.mesh.addFace(new int [] {d, c, b, a}, material, false, reversed, normal);
    this.triangles += 4;
  }
}
