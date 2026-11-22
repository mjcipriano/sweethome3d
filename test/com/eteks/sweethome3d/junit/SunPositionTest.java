/*
 * SunPositionTest.java
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
package com.eteks.sweethome3d.junit;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.SunPosition;

import junit.framework.TestCase;

/**
 * Tests the position of the Sun against published values.
 */
public class SunPositionTest extends TestCase {
  private static long getTime(String timeZone, int year, int month, int day, int hour, int minute) {
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(timeZone));
    calendar.clear();
    calendar.set(year, month - 1, day, hour, minute, 0);
    return calendar.getTimeInMillis();
  }

  /**
   * Examples 25.a and 28.a of Astronomical Algorithms by Jean Meeus: October 13, 1992 at 0 h.
   */
  public void testMeeusExamples() {
    SunPosition sun = SunPosition.getInstance(getTime("UTC", 1992, 10, 13, 0, 0), 0, 0);
    assertEquals(-7.78507, sun.getDeclination(), 0.001);
    assertEquals(13.71, sun.getEquationOfTime(), 0.01);
    assertEquals(0.99766, sun.getDistance(), 0.00001);
  }

  public void testEquinoxAndSolstices() {
    assertEquals(0, SunPosition.getInstance(getTime("UTC", 2024, 3, 20, 3, 6), 0, 0).getDeclination(), 0.002);
    assertEquals(23.4386, SunPosition.getInstance(getTime("UTC", 2024, 6, 20, 20, 51), 0, 0).getDeclination(), 0.001);
    assertEquals(-23.4386, SunPosition.getInstance(getTime("UTC", 2024, 12, 21, 9, 21), 0, 0).getDeclination(), 0.001);
  }

  /**
   * Sunrises and sunsets published for London, New York and Paris, with a summer time,
   * a winter time and a time zone which isn't a whole number of hours.
   */
  public void testSunriseAndSunset() {
    assertDay("Europe/London", 51.5074, -0.1278, 2024, 6, 21, 4 * 60 + 43, 21 * 60 + 21);
    assertDay("America/New_York", 40.7128, -74.006, 2024, 12, 21, 7 * 60 + 16, 16 * 60 + 32);
    assertDay("Europe/Paris", 48.85, 2.35, 2026, 6, 21, 5 * 60 + 47, 21 * 60 + 58);
    // New Delhi at the equinox: a day of 12 h and a few minutes
    TimeZone timeZone = TimeZone.getTimeZone("Asia/Kolkata");
    long [] instants = SunPosition.getSunriseNoonSunset(getTime("Asia/Kolkata", 2024, 3, 20, 12, 0), timeZone, 28.6139, 77.209);
    assertEquals(12 * 60 + 8, (instants [2] - instants [0]) / 60000., 2);
    assertEquals(getTime("Asia/Kolkata", 2024, 3, 20, 12, 28), instants [1], 90000);
    // At noon the sun is in the south, at 90 - latitude + declination
    SunPosition noon = SunPosition.getInstance(instants [1], 28.6139, 77.209);
    assertEquals(180, noon.getAzimuth(), 0.01);
    assertEquals(90 - 28.6139 + noon.getDeclination(), noon.getElevation(), 0.001);
  }

  private void assertDay(String timeZoneId, double latitude, double longitude,
                         int year, int month, int day, int sunriseMinutes, int sunsetMinutes) {
    TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
    long midnight = getTime(timeZoneId, year, month, day, 0, 0);
    long [] instants = SunPosition.getSunriseNoonSunset(midnight + 12 * 3600000L, timeZone, latitude, longitude);
    assertEquals(timeZoneId + " sunrise", sunriseMinutes, (instants [0] - midnight) / 60000., 1.5);
    assertEquals(timeZoneId + " sunset", sunsetMinutes, (instants [2] - midnight) / 60000., 1.5);
    // The upper edge of the sun is then on the horizon
    assertEquals(-0.833, SunPosition.getInstance(instants [0], latitude, longitude).getElevation(), 0.01);
    assertEquals(-0.833, SunPosition.getInstance(instants [2], latitude, longitude).getElevation(), 0.01);
  }

  public void testPolarDayAndNight() {
    TimeZone timeZone = TimeZone.getTimeZone("Europe/Oslo");
    long [] summer = SunPosition.getSunriseNoonSunset(getTime("Europe/Oslo", 2024, 6, 21, 12, 0), timeZone, 69.6492, 18.9553);
    assertEquals(Long.MIN_VALUE, summer [0]);
    assertEquals(Long.MIN_VALUE, summer [2]);
    assertTrue(SunPosition.getInstance(summer [1], 69.6492, 18.9553).getElevation() > 40);
    long [] winter = SunPosition.getSunriseNoonSunset(getTime("Europe/Oslo", 2024, 12, 21, 12, 0), timeZone, 69.6492, 18.9553);
    assertEquals(Long.MIN_VALUE, winter [0]);
    assertTrue(SunPosition.getInstance(winter [1], 69.6492, 18.9553).getElevation() < 0);
  }

  /**
   * The southern hemisphere sees the sun in the north at noon.
   */
  public void testSouthernHemisphere() {
    TimeZone timeZone = TimeZone.getTimeZone("Australia/Sydney");
    long [] instants = SunPosition.getSunriseNoonSunset(getTime("Australia/Sydney", 2024, 12, 21, 12, 0), timeZone, -33.8688, 151.2093);
    SunPosition noon = SunPosition.getInstance(instants [1], -33.8688, 151.2093);
    assertTrue(noon.getAzimuth() < 0.01 || noon.getAzimuth() > 359.99);
    assertEquals(79.57, noon.getElevation(), 0.01);
    assertEquals(getTime("Australia/Sydney", 2024, 12, 21, 5, 41), instants [0], 90000);
    assertEquals(getTime("Australia/Sydney", 2024, 12, 21, 20, 6), instants [2], 90000);
  }

  public void testRefractionAndLight() {
    // Refraction raises the sun by about half a degree on the horizon and nothing at the zenith
    TimeZone timeZone = TimeZone.getTimeZone("UTC");
    long [] instants = SunPosition.getSunriseNoonSunset(getTime("UTC", 2025, 3, 20, 12, 0), timeZone, 0, 0);
    SunPosition sunrise = SunPosition.getInstance(instants [0], 0, 0);
    assertEquals(0.5, sunrise.getApparentElevation() - sunrise.getElevation(), 0.15);
    SunPosition noon = SunPosition.getInstance(instants [1], 0, 0);
    assertEquals(noon.getElevation(), noon.getApparentElevation(), 0.001);
    assertEquals(0, noon.getShadowLength(), 0.02);
    // Clear sky: about 100 000 lux from the sun at the zenith, much less when it's low, none at night
    assertEquals(108000, noon.getDirectNormalIlluminance(), 5000);
    assertEquals(16300, noon.getDiffuseHorizontalIlluminance(), 200);
    SunPosition morning = SunPosition.getInstance(instants [0] + 3600000, 0, 0);
    assertEquals(14.2, morning.getApparentElevation(), 0.3);
    assertTrue(morning.getDirectNormalIlluminance() < 70000 && morning.getDirectNormalIlluminance() > 40000);
    assertEquals(3.94, morning.getShadowLength(), 0.1);
    SunPosition night = SunPosition.getInstance(instants [1] + 12 * 3600000, 0, 0);
    assertEquals(0, night.getDirectNormalIlluminance(), 0);
    assertEquals(0, night.getDiffuseHorizontalIlluminance(), 0);
    assertTrue(Double.isInfinite(night.getShadowLength()));
  }

  /**
   * The compass gives the same sun in a time zone which isn't a whole number of hours.
   */
  public void testCompass() {
    Compass compass = new Compass(0, 0, 2);
    compass.setLatitude((float)Math.toRadians(28.6139));
    compass.setLongitude((float)Math.toRadians(77.209));
    compass.setTimeZone("Asia/Kolkata");
    long time = getTime("Asia/Kolkata", 2024, 3, 20, 12, 28);
    assertEquals(Math.PI, compass.getSunAzimuth(time), 0.01);
    assertEquals(Math.toRadians(61.45), compass.getSunElevation(time), 0.001);
  }
}
