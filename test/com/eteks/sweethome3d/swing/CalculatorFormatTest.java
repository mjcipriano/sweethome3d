/*
 * CalculatorFormatTest.java
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

import com.eteks.sweethome3d.model.LengthUnit;

import junit.framework.TestCase;

/**
 * Tests the formulas computed in the fields where numbers are typed.
 */
public class CalculatorFormatTest extends TestCase {
  private static CalculatorFormat getFormat(Locale locale, LengthUnit unit) {
    return new CalculatorFormat(new DecimalFormat("0.###", new DecimalFormatSymbols(locale)), unit);
  }

  /**
   * Checks the value of a formula that is read to its end.
   */
  private static void assertValue(double expected, String formula) {
    ParsePosition position = new ParsePosition(0);
    Number value = getFormat(Locale.US, null).parse(formula, position);
    assertNotNull(formula, value);
    assertEquals(formula, formula.length(), position.getIndex());
    assertEquals(formula, expected, value.doubleValue(), 1E-6);
  }

  /**
   * Checks a wrong formula gives what the format of numbers reads at its start.
   */
  private static void assertWrong(Double expected, int expectedIndex, String formula) {
    ParsePosition position = new ParsePosition(0);
    Number value = getFormat(Locale.US, null).parse(formula, position);
    if (expected == null) {
      assertNull(formula, value);
    } else {
      assertEquals(formula, expected.doubleValue(), value.doubleValue(), 1E-6);
    }
    assertEquals(formula, expectedIndex, position.getIndex());
  }

  public void testOperators() {
    assertValue(7, "1+2*3");
    assertValue(9, "(1+2)*3");
    assertValue(2.5, "10/4");
    assertValue(3, "8-3-2");
    assertValue(2, "16/4/2");
    assertValue(70, "2*(3+4)*5");
    assertValue(2, "((2))");
    assertValue(5, " 2 + 3 ");
    assertValue(5, "2+\t3");
    assertValue(14, "2 * ( 3 + 4 )");
    assertValue(2001, "2E3+1");
    assertValue(Double.POSITIVE_INFINITY, "1/0");
  }

  public void testPowersAndOpposites() {
    // Powers are computed from left to right
    assertValue(64, "2^3^2");
    assertValue(16, "2^3*2");
    assertValue(18, "2*3^2");
    assertValue(0.5, "2^-1");
    assertValue(Math.sqrt(2), "2^0.5");
    assertValue(0.0625, "2^-2^2");
    // The opposite of a number is computed before its power
    assertValue(4, "-2^2");
    assertValue(9, "-3^2");
    assertValue(18, "2*-3^2");
    assertValue(-6, "2*-3");
    assertValue(4, "-2*-2");
    assertValue(4, "2--2");
    assertValue(4, "2- -2");
    assertValue(2, "--2");
    assertValue(-5, "-(2+3)");
  }

  public void testFunctions() {
    assertValue(2, "sqrt(4)");
    assertValue(16, "sqr(3+1)");
    assertValue(1, "cos(60)*2");
    assertValue(0.25, "sin(30)^2");
    assertValue(Math.E, "exp(1)");
    assertValue(3, "LOG(1000)");
    assertValue(Math.log(6), "ln(2)+ln 3");
    // Without brackets, a function is applied to the number that follows it
    assertValue(1.5, "sin 30+1");
    assertValue(8, "sqrt 4*4");
    assertValue(16, "sqrt 16^2");
    assertValue(10, "sqr 3+1");
    assertValue(1, "tan 45");
    assertValue(45, "arctan 1");
    assertValue(90, "arccos 0.5+arcsin 0.5");
    assertValue(2, "log 100");
    assertValue(1, "ln exp 1");
    assertValue(16, "sqr sqrt 16");
    assertValue(-0.5, "-sin 30");
    assertValue(-0.5, "sin -30");
    assertValue(0.5, "Sin  30");
    assertTrue(Double.isNaN(getFormat(Locale.US, null).parse("sqrt -4", new ParsePosition(0)).doubleValue()));
  }

  public void testWrongFormulas() {
    assertWrong(null, 0, "");
    assertWrong(null, 0, "+2");
    assertWrong(null, 0, "(2+3");
    assertWrong(null, 0, "sin");
    assertWrong(null, 0, "sin()");
    assertWrong(null, 0, "abc");
    assertWrong(null, 0, "pi");
    assertWrong(null, 0, "*2");
    assertWrong(null, 0, "()");
    // A function is separated from its parameter
    assertWrong(null, 0, "SIN30");
    assertWrong(null, 0, "sqrt(16)(2)");
    assertWrong(null, 0, "exp 2 3");
    assertWrong(2., 1, "2+");
    assertWrong(2., 1, "2 3");
    assertWrong(2., 1, "2(3)");
    assertWrong(2., 1, "2+3)");
    assertWrong(3., 1, "3+abc");
    assertWrong(2., 1, "2^^3");
    assertWrong(1., 1, "1+-+2");
    assertWrong(12., 2, "12a");
    // A number doesn't start with a point in a formula
    assertWrong(0.5, 2, ".5+.5");
  }

  public void testFormatsAndUnits() {
    ParsePosition position = new ParsePosition(0);
    assertEquals(2.5, getFormat(Locale.FRANCE, null).parse("1,5+1", position).doubleValue(), 1E-6);
    assertEquals(5, position.getIndex());
    position = new ParsePosition(0);
    assertEquals(5, getFormat(Locale.FRANCE, null).parse("2,5*2", position).doubleValue(), 1E-6);
    position = new ParsePosition(0);
    assertEquals(1, getFormat(Locale.FRANCE, null).parse("1.5+1", position).doubleValue(), 1E-6);
    assertEquals(1, position.getIndex());
    // A number alone is read by the format of numbers
    position = new ParsePosition(0);
    assertEquals(12.5, getFormat(Locale.US, null).parse("12.5", position).doubleValue(), 1E-6);
    assertEquals(4, position.getIndex());
    assertEquals("12.5", getFormat(Locale.US, null).format(12.5));
    // The numbers of a formula are lengths in centimeters, changed to the unit to compute
    // it then back to centimeters: products and functions depend on the unit
    position = new ParsePosition(0);
    assertEquals(1.06f, getFormat(Locale.US, LengthUnit.METER).parse("1+2*3", position).floatValue(), 1E-5f);
    assertEquals(5, position.getIndex());
    position = new ParsePosition(0);
    assertEquals(3f, getFormat(Locale.US, LengthUnit.METER).parse("1+2", position).floatValue(), 1E-5f);
    position = new ParsePosition(0);
    assertEquals(3.3622048f, getFormat(Locale.US, LengthUnit.INCH).parse("1+2*3", position).floatValue(), 1E-5f);
    // A formula read after the start of a text
    position = new ParsePosition(2);
    assertEquals(5, getFormat(Locale.US, null).parse("= 2+3", position).doubleValue(), 1E-6);
    assertEquals(5, position.getIndex());
  }
}
