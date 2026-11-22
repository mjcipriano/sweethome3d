/*
 * SunPosition.java
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
package com.eteks.sweethome3d.model;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * The position of the Sun in the sky of a place at an instant, and the light it gives by clear sky.
 * The position is computed with the formulas of Jean Meeus (<i>Astronomical Algorithms</i>, chapter 25,
 * as used by the solar calculator of NOAA): between 1900 and 2100, the azimuth and the elevation
 * are within about 0.01 degree of the ephemerides, and the times of sunrise and sunset within a minute
 * for latitudes between -72 and +72 degrees.
 * @since 7.29
 */
public class SunPosition {
  /** Elevation of the center of the Sun at sunrise and sunset, with refraction and the radius of its disk. */
  private static final double SUNRISE_ELEVATION = -0.833;
  /** Illuminance of the Sun out of the atmosphere at its mean distance, in lux. */
  private static final double SOLAR_ILLUMINANCE_CONSTANT = 133800;
  /** Optical extinction of a clear sky (IES clear sky model). */
  private static final double CLEAR_SKY_EXTINCTION = 0.21;

  private final double azimuth;
  private final double elevation;
  private final double apparentElevation;
  private final double declination;
  private final double equationOfTime;
  private final double distance;

  private SunPosition(double azimuth, double elevation, double apparentElevation,
                      double declination, double equationOfTime, double distance) {
    this.azimuth = azimuth;
    this.elevation = elevation;
    this.apparentElevation = apparentElevation;
    this.declination = declination;
    this.equationOfTime = equationOfTime;
    this.distance = distance;
  }

  /**
   * Returns the position of the Sun at the given instant, seen from a place.
   * @param time      an instant in milliseconds since the Epoch (January 1, 1970 at 0 h UTC)
   * @param latitude  the latitude of the place in degrees, positive in the northern hemisphere
   * @param longitude the longitude of the place in degrees, positive to the east of Greenwich
   */
  public static SunPosition getInstance(long time, double latitude, double longitude) {
    double [] sun = getSunCoordinates(time);
    double declination = sun [0];
    double equationOfTime = sun [1];
    // True solar time in minutes
    double dayMinutes = (time % 86400000L + 86400000L) % 86400000L / 60000.;
    double solarTime = ((dayMinutes + equationOfTime + 4 * longitude) % 1440 + 1440) % 1440;
    double hourAngle = Math.toRadians(solarTime / 4 - 180);
    double latitudeAngle = Math.toRadians(latitude);
    double declinationAngle = Math.toRadians(declination);
    double sinElevation = Math.sin(latitudeAngle) * Math.sin(declinationAngle)
        + Math.cos(latitudeAngle) * Math.cos(declinationAngle) * Math.cos(hourAngle);
    double elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinElevation))));
    // Azimuth from the north to the east
    double azimuth = Math.toDegrees(Math.atan2(Math.sin(hourAngle),
        Math.cos(hourAngle) * Math.sin(latitudeAngle) - Math.tan(declinationAngle) * Math.cos(latitudeAngle))) + 180;
    azimuth = (azimuth % 360 + 360) % 360;
    return new SunPosition(azimuth, elevation, elevation + getRefraction(elevation),
        declination, equationOfTime, sun [2]);
  }

  /**
   * Returns the declination of the Sun in degrees, the equation of time in minutes
   * and the distance of the Sun in astronomical units at the given instant.
   */
  private static double [] getSunCoordinates(long time) {
    double julianDay = time / 86400000. + 2440587.5;
    double t = (julianDay - 2451545) / 36525;
    double meanLongitude = ((280.46646 + t * (36000.76983 + 0.0003032 * t)) % 360 + 360) % 360;
    double meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t);
    double eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
    double m = Math.toRadians(meanAnomaly);
    double center = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
        + Math.sin(2 * m) * (0.019993 - 0.000101 * t)
        + Math.sin(3 * m) * 0.000289;
    double trueLongitude = meanLongitude + center;
    double trueAnomaly = Math.toRadians(meanAnomaly + center);
    double distance = 1.000001018 * (1 - eccentricity * eccentricity) / (1 + eccentricity * Math.cos(trueAnomaly));
    // Nutation and aberration
    double omega = Math.toRadians(125.04 - 1934.136 * t);
    double apparentLongitude = Math.toRadians(trueLongitude - 0.00569 - 0.00478 * Math.sin(omega));
    double meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
    double obliquity = Math.toRadians(meanObliquity + 0.00256 * Math.cos(omega));
    double declination = Math.toDegrees(Math.asin(Math.sin(obliquity) * Math.sin(apparentLongitude)));
    double y = Math.tan(obliquity / 2) * Math.tan(obliquity / 2);
    double l0 = Math.toRadians(meanLongitude);
    double equationOfTime = 4 * Math.toDegrees(y * Math.sin(2 * l0)
        - 2 * eccentricity * Math.sin(m)
        + 4 * eccentricity * y * Math.sin(m) * Math.cos(2 * l0)
        - 0.5 * y * y * Math.sin(4 * l0)
        - 1.25 * eccentricity * eccentricity * Math.sin(2 * m));
    return new double [] {declination, equationOfTime, distance};
  }

  /**
   * Returns the angle in degrees the atmosphere adds to the elevation of an object seen
   * at the given elevation, for a standard pressure and temperature.
   */
  private static double getRefraction(double elevation) {
    if (elevation > 85) {
      return 0;
    }
    double tangent = Math.tan(Math.toRadians(elevation));
    double seconds;
    if (elevation > 5) {
      seconds = 58.1 / tangent - 0.07 / (tangent * tangent * tangent) + 0.000086 / Math.pow(tangent, 5);
    } else if (elevation > -0.575) {
      seconds = 1735 + elevation * (-518.2 + elevation * (103.4 + elevation * (-12.79 + elevation * 0.711)));
    } else {
      seconds = -20.772 / tangent;
    }
    return seconds / 3600;
  }

  /**
   * Returns the instants of the sunrise, of the solar noon and of the sunset of a day at a place,
   * in milliseconds since the Epoch. The sunrise and the sunset are equal to <code>Long.MIN_VALUE</code>
   * when the Sun doesn't cross the horizon that day.
   * @param time      an instant of the day in the given time zone
   * @param timeZone  the time zone of the place, which gives its day
   * @param latitude  the latitude of the place in degrees, positive in the northern hemisphere
   * @param longitude the longitude of the place in degrees, positive to the east of Greenwich
   */
  public static long [] getSunriseNoonSunset(long time, TimeZone timeZone, double latitude, double longitude) {
    Calendar calendar = new GregorianCalendar(timeZone);
    calendar.setTimeInMillis(time);
    Calendar utcCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    utcCalendar.clear();
    utcCalendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    long utcMidnight = utcCalendar.getTimeInMillis();
    // Solar noon
    long noon = utcMidnight + Math.round((720 - 4 * longitude) * 60000);
    for (int i = 0; i < 3; i++) {
      noon = utcMidnight + Math.round((720 - 4 * longitude - getSunCoordinates(noon) [1]) * 60000);
    }
    long [] instants = {Long.MIN_VALUE, noon, Long.MIN_VALUE};
    double latitudeAngle = Math.toRadians(latitude);
    for (int side = -1; side <= 1; side += 2) {
      long instant = noon;
      boolean found = true;
      for (int i = 0; i < 4; i++) {
        double [] sun = getSunCoordinates(instant);
        double declinationAngle = Math.toRadians(sun [0]);
        double cosHourAngle = (Math.sin(Math.toRadians(SUNRISE_ELEVATION))
            - Math.sin(latitudeAngle) * Math.sin(declinationAngle))
            / (Math.cos(latitudeAngle) * Math.cos(declinationAngle));
        if (cosHourAngle < -1 || cosHourAngle > 1) {
          found = false;
          break;
        }
        double hourAngle = Math.toDegrees(Math.acos(cosHourAngle));
        instant = utcMidnight + Math.round((720 - 4 * longitude - sun [1] + side * 4 * hourAngle) * 60000);
      }
      if (found) {
        instants [side + 1] = instant;
      }
    }
    return instants;
  }

  /**
   * Returns the azimuth of the Sun in degrees, measured from the north towards the east.
   */
  public double getAzimuth() {
    return this.azimuth;
  }

  /**
   * Returns the elevation of the center of the Sun over the horizon in degrees, without atmosphere.
   */
  public double getElevation() {
    return this.elevation;
  }

  /**
   * Returns the elevation in degrees at which the Sun is seen, raised by atmospheric refraction.
   * This is the direction its light and the shadows come from.
   */
  public double getApparentElevation() {
    return this.apparentElevation;
  }

  /**
   * Returns the declination of the Sun in degrees.
   */
  public double getDeclination() {
    return this.declination;
  }

  /**
   * Returns the equation of time in minutes: true solar time minus mean solar time.
   */
  public double getEquationOfTime() {
    return this.equationOfTime;
  }

  /**
   * Returns the distance of the Sun in astronomical units.
   */
  public double getDistance() {
    return this.distance;
  }

  /**
   * Returns the illuminance in lux received by clear sky from the Sun by a surface
   * which faces it, or 0 if the Sun is under the horizon (IES clear sky model).
   */
  public double getDirectNormalIlluminance() {
    if (this.apparentElevation <= 0) {
      return 0;
    }
    // Relative optical air mass of Kasten and Young
    double airMass = 1 / (Math.sin(Math.toRadians(this.apparentElevation))
        + 0.50572 * Math.pow(this.apparentElevation + 6.07995, -1.6364));
    return SOLAR_ILLUMINANCE_CONSTANT / (this.distance * this.distance) * Math.exp(-CLEAR_SKY_EXTINCTION * airMass);
  }

  /**
   * Returns the illuminance in lux received by clear sky from the whole sky, without the Sun,
   * by a horizontal surface that nothing hides (IES clear sky model). During twilight,
   * it decreases down to 0 when the Sun is 6 degrees under the horizon.
   */
  public double getDiffuseHorizontalIlluminance() {
    if (this.apparentElevation > 0) {
      return 800 + 15500 * Math.sqrt(Math.sin(Math.toRadians(this.apparentElevation)));
    } else if (this.apparentElevation > -6) {
      return 800 * (1 + this.apparentElevation / 6);
    } else {
      return 0;
    }
  }

  /**
   * Returns the length of the shadow of a vertical object of height 1 on a horizontal ground,
   * or <code>Double.POSITIVE_INFINITY</code> if the Sun is under the horizon.
   */
  public double getShadowLength() {
    return this.apparentElevation > 0
        ? 1 / Math.tan(Math.toRadians(this.apparentElevation))
        : Double.POSITIVE_INFINITY;
  }
}
