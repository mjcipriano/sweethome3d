/*
 * HomeGeoreference.java 21 nov. 2026
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;

/**
 * Where a home stands on the Earth: the latitude and the longitude of a point of its
 * plan and the direction of the true north in it. With it, the points of a survey or of a
 * parcel given by their coordinates are placed in the plan, and points of the plan get
 * their coordinates.
 * <p>The plan is taken as flat around its reference point, which is exact enough for a
 * plot (a few millimeters over a kilometer). Projected coordinates are computed on the
 * GRS 80 ellipsoid, which the NAD 83 and WGS 84 datums share within a millimeter.
 * @author Sweet Home 3D
 */
public class HomeGeoreference {
  /** The property of a home that keeps its georeference. */
  public static final String PROPERTY = "assistantGeoreference";

  private static final double A = 6378137;
  private static final double F = 1 / 298.257222101;
  private static final double E2 = F * (2 - F);
  private static final double US_SURVEY_FOOT = 1200. / 3937;
  private static final double FOOT = 0.3048;

  private final double latitude;
  private final double longitude;
  private final double x;
  private final double y;
  private final double rotation;

  /**
   * @param latitude  the latitude in degrees of the point (x, y) of the plan
   * @param longitude its longitude in degrees
   * @param x         the abscissa in cm of the reference point
   * @param y         its ordinate in cm
   * @param rotation  the direction of the true north in the plan, in degrees clockwise from its top
   */
  public HomeGeoreference(double latitude, double longitude, double x, double y, double rotation) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.x = x;
    this.y = y;
    this.rotation = rotation;
  }

  public double getLatitude() {
    return this.latitude;
  }

  public double getLongitude() {
    return this.longitude;
  }

  public double getX() {
    return this.x;
  }

  public double getY() {
    return this.y;
  }

  public double getRotation() {
    return this.rotation;
  }

  /**
   * Returns the georeference of <code>home</code>, or <code>null</code> if it has none.
   */
  public static HomeGeoreference get(Home home) {
    String text = home.getProperty(PROPERTY);
    if (text == null) {
      return null;
    }
    try {
      Object parsed = JSONParser.parse(text);
      if (parsed instanceof Map) {
        Map<?, ?> map = (Map<?, ?>)parsed;
        return new HomeGeoreference(number(map, "lat"), number(map, "lon"), number(map, "x"), number(map, "y"),
            number(map, "rotation"));
      }
    } catch (IllegalArgumentException ex) {
      // No georeference
    }
    return null;
  }

  private static double number(Map<?, ?> map, String key) {
    Object value = map.get(key);
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Missing " + key);
    }
    return ((Number)value).doubleValue();
  }

  /**
   * Returns the text kept in the property of a home.
   */
  public String toJSON() {
    return String.format(Locale.US, "{\"lat\":%.9f,\"lon\":%.9f,\"x\":%.2f,\"y\":%.2f,\"rotation\":%.5f}",
        this.latitude, this.longitude, this.x, this.y, this.rotation);
  }

  /**
   * Returns the meters to the east and to the north of the reference point for a degree
   * of longitude and of latitude.
   */
  private double [] getMetersPerDegree() {
    double sin = Math.sin(Math.toRadians(this.latitude));
    double w = Math.sqrt(1 - E2 * sin * sin);
    double primeVertical = A / w;
    double meridian = A * (1 - E2) / (w * w * w);
    return new double [] {Math.toRadians(1) * primeVertical * Math.cos(Math.toRadians(this.latitude)),
                          Math.toRadians(1) * meridian};
  }

  /**
   * Returns the point (x, y) of the plan in cm at the given latitude and longitude in degrees.
   */
  public double [] toPlan(double latitude, double longitude) {
    double [] metersPerDegree = getMetersPerDegree();
    double east = (longitude - this.longitude) * metersPerDegree [0];
    double north = (latitude - this.latitude) * metersPerDegree [1];
    double angle = Math.toRadians(this.rotation);
    // In the plan y goes down: the north is (sin, -cos) and the east (cos, sin)
    return new double [] {this.x + (east * Math.cos(angle) + north * Math.sin(angle)) * 100,
                          this.y + (east * Math.sin(angle) - north * Math.cos(angle)) * 100};
  }

  /**
   * Returns the latitude and the longitude in degrees of a point of the plan in cm.
   */
  public double [] toLatitudeLongitude(double x, double y) {
    double dx = (x - this.x) / 100;
    double dy = (y - this.y) / 100;
    double angle = Math.toRadians(this.rotation);
    double east = dx * Math.cos(angle) + dy * Math.sin(angle);
    double north = dx * Math.sin(angle) - dy * Math.cos(angle);
    double [] metersPerDegree = getMetersPerDegree();
    return new double [] {this.latitude + north / metersPerDegree [1], this.longitude + east / metersPerDegree [0]};
  }

  /**
   * Returns the georeference that puts two points of known latitude and longitude at two
   * points of the plan. The scale of the plan isn't changed: the distance between the two
   * points of the plan should be the one between the two places.
   * @return the georeference, or <code>null</code> if the points are the same
   */
  public static HomeGeoreference fromControlPoints(double latitude1, double longitude1, double x1, double y1,
                                                   double latitude2, double longitude2, double x2, double y2) {
    HomeGeoreference north = new HomeGeoreference(latitude1, longitude1, x1, y1, 0);
    double [] point = north.toPlan(latitude2, longitude2);
    if (Math.hypot(point [0] - x1, point [1] - y1) < 1 || Math.hypot(x2 - x1, y2 - y1) < 1) {
      return null;
    }
    // Turns the plan so the second point is in the direction it has on the ground
    double rotation = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1) - Math.atan2(point [1] - y1, point [0] - x1));
    rotation = ((rotation % 360) + 540) % 360 - 180;
    return new HomeGeoreference(latitude1, longitude1, x1, y1, rotation);
  }

  /**
   * Returns the distance in cm between two places given by their latitude and longitude in degrees.
   */
  public static double getDistance(double latitude1, double longitude1, double latitude2, double longitude2) {
    double [] point = new HomeGeoreference(latitude1, longitude1, 0, 0, 0).toPlan(latitude2, longitude2);
    return Math.hypot(point [0], point [1]);
  }

  // ---- Coordinate systems ----

  /**
   * A coordinate system in which the points of a survey are given.
   */
  public static final class Projection {
    private static final int GEOGRAPHIC = 0;
    private static final int WEB_MERCATOR = 1;
    private static final int TRANSVERSE_MERCATOR = 2;
    private static final int LAMBERT_CONFORMAL_CONIC = 3;

    private final int    type;
    private final String name;
    private double latitudeOrigin;
    private double centralMeridian;
    private double scale = 1;
    private double falseEasting;
    private double falseNorthing;
    private double standardParallel1;
    private double standardParallel2;
    private double unit = 1;

    private Projection(int type, String name) {
      this.type = type;
      this.name = name;
    }

    public String getName() {
      return this.name;
    }

    /**
     * Returns <code>true</code> if coordinates are a longitude and a latitude in degrees.
     */
    public boolean isGeographic() {
      return this.type == GEOGRAPHIC;
    }

    /**
     * Returns the length in meters of the unit of the coordinates, or 0 for degrees.
     */
    public double getUnit() {
      return this.type == GEOGRAPHIC ? 0 : this.unit;
    }

    /**
     * Returns the coordinate system named by <code>srs</code>: <code>EPSG:4326</code> (longitude and
     * latitude), <code>EPSG:3857</code> (web maps), a UTM zone (<code>EPSG:326xx</code>, <code>327xx</code>,
     * <code>269xx</code>), <code>EPSG:2240</code> and <code>2239</code> (Georgia West and East in US survey feet),
     * <code>EPSG:26967</code> and <code>26966</code> (the same in meters), or the one described by a PROJ text
     * like <code>+proj=tmerc +lat_0=30 +lon_0=-84.1666666667 +k=0.9999 +x_0=700000 +y_0=0 +units=us-ft</code>
     * (<code>tmerc</code>, <code>utm</code>, <code>lcc</code>, <code>merc</code> and <code>longlat</code>).
     * @throws IOException if the system isn't known
     */
    public static Projection get(String srs) throws IOException {
      if (srs == null || srs.trim().length() == 0) {
        return new Projection(GEOGRAPHIC, "EPSG:4326");
      }
      String text = srs.trim();
      if (text.indexOf("+proj") >= 0) {
        return parseProj(text);
      }
      String upper = text.toUpperCase(Locale.ENGLISH).replace(" ", "");
      String digits = upper.replaceAll("^(URN:OGC:DEF:CRS:)?(EPSG|ESRI)::?", "");
      if (upper.equals("WGS84") || upper.equals("CRS84") || upper.endsWith(":CRS84")
          || digits.equals("4326") || digits.equals("4269") || digits.equals("4979")) {
        return new Projection(GEOGRAPHIC, "EPSG:4326");
      }
      int code;
      try {
        code = Integer.parseInt(digits);
      } catch (NumberFormatException ex) {
        throw new IOException("the coordinate system \"" + srs + "\" isn't known: give an EPSG code like EPSG:4326, "
            + "or a PROJ text like +proj=tmerc +lat_0=30 +lon_0=-84.1666666667 +k=0.9999 +x_0=700000 +y_0=0 +units=us-ft");
      }
      if (code == 3857 || code == 102100 || code == 900913 || code == 102113) {
        return new Projection(WEB_MERCATOR, "EPSG:3857");
      } else if (code >= 32601 && code <= 32660 || code >= 32701 && code <= 32760 || code >= 26901 && code <= 26923) {
        Projection projection = new Projection(TRANSVERSE_MERCATOR, "EPSG:" + code);
        int zone = code % 100;
        projection.centralMeridian = zone * 6 - 183;
        projection.scale = 0.9996;
        projection.falseEasting = 500000;
        projection.falseNorthing = code >= 32701 && code <= 32760 ? 10000000 : 0;
        return projection;
      } else if (code == 2240 || code == 2239 || code == 26967 || code == 26966) {
        // NAD 83 / Georgia West and East
        boolean west = code == 2240 || code == 26967;
        Projection projection = new Projection(TRANSVERSE_MERCATOR, "EPSG:" + code);
        projection.latitudeOrigin = 30;
        projection.centralMeridian = west ? -(84 + 1 / 6.) : -(82 + 1 / 6.);
        projection.scale = 0.9999;
        projection.falseEasting = west ? 700000 : 200000;
        projection.unit = code < 3000 ? US_SURVEY_FOOT : 1;
        return projection;
      }
      throw new IOException("the coordinate system EPSG:" + code + " isn't built in: give its definition as a PROJ text "
          + "in \"srs\", like +proj=tmerc +lat_0=30 +lon_0=-84.1666666667 +k=0.9999 +x_0=700000 +y_0=0 +units=us-ft "
          + "(found at epsg.io/" + code + ".proj4), or give longitudes and latitudes with EPSG:4326");
    }

    private static Projection parseProj(String text) throws IOException {
      Map<String, String> parameters = new LinkedHashMap<String, String>();
      for (String word : text.trim().split("\\s+")) {
        if (word.startsWith("+")) {
          int equal = word.indexOf('=');
          parameters.put(equal > 0 ? word.substring(1, equal) : word.substring(1), equal > 0 ? word.substring(equal + 1) : "");
        }
      }
      String proj = parameters.get("proj");
      Projection projection;
      if ("longlat".equals(proj) || "latlong".equals(proj)) {
        return new Projection(GEOGRAPHIC, "longlat");
      } else if ("merc".equals(proj)) {
        projection = new Projection(WEB_MERCATOR, text);
      } else if ("tmerc".equals(proj) || "utm".equals(proj) || "etmerc".equals(proj)) {
        projection = new Projection(TRANSVERSE_MERCATOR, text);
      } else if ("lcc".equals(proj)) {
        projection = new Projection(LAMBERT_CONFORMAL_CONIC, text);
      } else {
        throw new IOException("the projection \"" + proj + "\" isn't supported (tmerc, utm, lcc, merc, longlat)");
      }
      try {
        if ("utm".equals(proj)) {
          projection.centralMeridian = Integer.parseInt(parameters.get("zone")) * 6 - 183;
          projection.scale = 0.9996;
          projection.falseEasting = 500000;
          projection.falseNorthing = parameters.containsKey("south") ? 10000000 : 0;
        }
        projection.latitudeOrigin = getNumber(parameters, "lat_0", projection.latitudeOrigin);
        projection.centralMeridian = getNumber(parameters, "lon_0", projection.centralMeridian);
        projection.scale = getNumber(parameters, "k_0", getNumber(parameters, "k", projection.scale));
        projection.falseEasting = getNumber(parameters, "x_0", projection.falseEasting);
        projection.falseNorthing = getNumber(parameters, "y_0", projection.falseNorthing);
        projection.standardParallel1 = getNumber(parameters, "lat_1", projection.latitudeOrigin);
        projection.standardParallel2 = getNumber(parameters, "lat_2", projection.standardParallel1);
        String units = parameters.get("units");
        projection.unit = parameters.containsKey("to_meter") ? Double.parseDouble(parameters.get("to_meter"))
            : "us-ft".equals(units) ? US_SURVEY_FOOT
            : "ft".equals(units) ? FOOT
            : 1;
      } catch (NumberFormatException ex) {
        throw new IOException("the PROJ text \"" + text + "\" has a parameter that isn't a number");
      }
      if (!(projection.unit > 0)) {
        throw new IOException("the unit of the coordinate system must be positive");
      }
      return projection;
    }

    private static double getNumber(Map<String, String> parameters, String key, double defaultValue) {
      return parameters.containsKey(key) ? Double.parseDouble(parameters.get(key)) : defaultValue;
    }

    /**
     * Returns the latitude and the longitude in degrees of the point of coordinates (x, y):
     * easting and northing, or longitude and latitude for a geographic system.
     */
    public double [] toLatitudeLongitude(double x, double y) {
      switch (this.type) {
        case GEOGRAPHIC :
          return new double [] {y, x};
        case WEB_MERCATOR :
          return new double [] {Math.toDegrees(2 * Math.atan(Math.exp(y * this.unit / A)) - Math.PI / 2),
                                this.centralMeridian + Math.toDegrees(x * this.unit / A)};
        case TRANSVERSE_MERCATOR :
          return inverseTransverseMercator(x * this.unit - this.falseEasting, y * this.unit - this.falseNorthing);
        default :
          return inverseLambert(x * this.unit - this.falseEasting, y * this.unit - this.falseNorthing);
      }
    }

    /**
     * Returns the coordinates (x, y) of a place given by its latitude and longitude in degrees.
     */
    public double [] fromLatitudeLongitude(double latitude, double longitude) {
      double [] point;
      switch (this.type) {
        case GEOGRAPHIC :
          return new double [] {longitude, latitude};
        case WEB_MERCATOR :
          return new double [] {Math.toRadians(longitude - this.centralMeridian) * A / this.unit,
              A * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(latitude) / 2)) / this.unit};
        case TRANSVERSE_MERCATOR :
          point = forwardTransverseMercator(latitude, longitude);
          break;
        default :
          point = forwardLambert(latitude, longitude);
          break;
      }
      return new double [] {(point [0] + this.falseEasting) / this.unit, (point [1] + this.falseNorthing) / this.unit};
    }

    // Transverse Mercator by the series of Krueger, exact to a millimeter within 3000 km of the central meridian

    private static final double N = F / (2 - F);
    private static final double RECTIFYING_RADIUS = A / (1 + N) * (1 + N * N / 4 + N * N * N * N / 64);
    private static final double [] ALPHA = {
        N / 2 - 2 * N * N / 3 + 5 * N * N * N / 16,
        13 * N * N / 48 - 3 * N * N * N / 5,
        61 * N * N * N / 240};
    private static final double [] BETA = {
        N / 2 - 2 * N * N / 3 + 37 * N * N * N / 96,
        N * N / 48 + N * N * N / 15,
        17 * N * N * N / 480};

    /**
     * Returns the length of the meridian from the equator to a latitude, in the plane of the projection.
     */
    private double getMeridianDistance(double latitude) {
      return forwardTransverseMercator(latitude, this.centralMeridian, 0) [1];
    }

    private double [] forwardTransverseMercator(double latitude, double longitude) {
      return forwardTransverseMercator(latitude, longitude, getMeridianDistance(this.latitudeOrigin));
    }

    private double [] forwardTransverseMercator(double latitude, double longitude, double originDistance) {
      double phi = Math.toRadians(latitude);
      double lambda = Math.toRadians(longitude - this.centralMeridian);
      double e = Math.sqrt(E2);
      double conformal = Math.sinh(atanh(Math.sin(phi)) - e * atanh(e * Math.sin(phi)));
      double xi = Math.atan2(conformal, Math.cos(lambda));
      double eta = atanh(Math.sin(lambda) / Math.sqrt(1 + conformal * conformal));
      double x = eta;
      double y = xi;
      for (int j = 1; j <= 3; j++) {
        x += ALPHA [j - 1] * Math.cos(2 * j * xi) * Math.sinh(2 * j * eta);
        y += ALPHA [j - 1] * Math.sin(2 * j * xi) * Math.cosh(2 * j * eta);
      }
      return new double [] {this.scale * RECTIFYING_RADIUS * x, this.scale * RECTIFYING_RADIUS * y - originDistance};
    }

    private double [] inverseTransverseMercator(double easting, double northing) {
      double xi = (northing + getMeridianDistance(this.latitudeOrigin)) / (this.scale * RECTIFYING_RADIUS);
      double eta = easting / (this.scale * RECTIFYING_RADIUS);
      double xi0 = xi;
      double eta0 = eta;
      for (int j = 1; j <= 3; j++) {
        xi0 -= BETA [j - 1] * Math.sin(2 * j * xi) * Math.cosh(2 * j * eta);
        eta0 -= BETA [j - 1] * Math.cos(2 * j * xi) * Math.sinh(2 * j * eta);
      }
      double conformal = Math.asin(Math.sin(xi0) / Math.cosh(eta0));
      double lambda = Math.atan2(Math.sinh(eta0), Math.cos(xi0));
      return new double [] {Math.toDegrees(getLatitude(Math.tan(Math.PI / 4 + conformal / 2))),
                            this.centralMeridian + Math.toDegrees(lambda)};
    }

    /**
     * Returns the latitude in radians whose isometric latitude has <code>t</code> as exponential.
     */
    private static double getLatitude(double t) {
      double e = Math.sqrt(E2);
      double phi = 2 * Math.atan(t) - Math.PI / 2;
      for (int i = 0; i < 12; i++) {
        double sin = e * Math.sin(phi);
        double next = 2 * Math.atan(t * Math.pow((1 + sin) / (1 - sin), e / 2)) - Math.PI / 2;
        if (Math.abs(next - phi) < 1E-13) {
          return next;
        }
        phi = next;
      }
      return phi;
    }

    private static double atanh(double value) {
      return 0.5 * Math.log((1 + value) / (1 - value));
    }

    // Lambert conformal conic with two standard parallels

    private static double getM(double phi) {
      double sin = Math.sin(phi);
      return Math.cos(phi) / Math.sqrt(1 - E2 * sin * sin);
    }

    private static double getT(double phi) {
      double e = Math.sqrt(E2);
      double sin = e * Math.sin(phi);
      return Math.tan(Math.PI / 4 - phi / 2) / Math.pow((1 - sin) / (1 + sin), e / 2);
    }

    /**
     * Returns the constants {n, F, rho0} of the cone.
     */
    private double [] getCone() {
      double phi1 = Math.toRadians(this.standardParallel1);
      double phi2 = Math.toRadians(this.standardParallel2);
      double n = Math.abs(phi1 - phi2) < 1E-10 ? Math.sin(phi1)
          : (Math.log(getM(phi1)) - Math.log(getM(phi2))) / (Math.log(getT(phi1)) - Math.log(getT(phi2)));
      double f = getM(phi1) / (n * Math.pow(getT(phi1), n));
      double rho0 = A * this.scale * f * Math.pow(getT(Math.toRadians(this.latitudeOrigin)), n);
      return new double [] {n, f, rho0};
    }

    private double [] forwardLambert(double latitude, double longitude) {
      double [] cone = getCone();
      double rho = A * this.scale * cone [1] * Math.pow(getT(Math.toRadians(latitude)), cone [0]);
      double theta = cone [0] * Math.toRadians(longitude - this.centralMeridian);
      return new double [] {rho * Math.sin(theta), cone [2] - rho * Math.cos(theta)};
    }

    private double [] inverseLambert(double easting, double northing) {
      double [] cone = getCone();
      double n = cone [0];
      double rho = Math.signum(n) * Math.hypot(easting, cone [2] - northing);
      double theta = Math.atan2(Math.signum(n) * easting, Math.signum(n) * (cone [2] - northing));
      double t = Math.pow(rho / (A * this.scale * cone [1]), 1 / n);
      return new double [] {Math.toDegrees(getLatitude(1 / t)), this.centralMeridian + Math.toDegrees(theta / n)};
    }
  }
}
