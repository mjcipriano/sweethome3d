/*
 * CalculatorFormat.java 23 mars 2020
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
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

import com.eteks.sweethome3d.model.LengthUnit;

/**
 * A decimal format able to calculate formulas.
 * @author Emmanuel Puybaret
 */
class CalculatorFormat extends DecimalFormat {
  private DecimalFormat numberFormat;
  private LengthUnit    lengthUnit;

  public CalculatorFormat(DecimalFormat numberFormat,
                          LengthUnit    lengthUnit) {
    super(numberFormat.toPattern());
    this.numberFormat = numberFormat;
    this.lengthUnit = lengthUnit;
  }

  @Override
  public Number parse(String text, ParsePosition pos) {
    final String parsedText = text.substring(pos.getIndex());
    Number number = this.numberFormat.parse(text, pos);
    if (number == null || pos.getIndex() != text.length()) {
      try {
        // Try to compute a formula
        number = new Calculator(parsedText).compute();
        if (this.lengthUnit != null) {
          number = this.lengthUnit.unitToCentimeter(number.floatValue());
        }
        pos.setIndex(text.length());
      } catch (ParseException ex) {
        // Keep default value
      }
    }
    return number;
  }

  public StringBuffer format(double number, StringBuffer result, FieldPosition position) {
    return this.numberFormat.format(number, result, position);
  }

  /**
   * Computes the value of a formula written with numbers, brackets, the operators
   * <code>+ - * / ^</code>, the opposite operator <code>-</code> and the functions
   * <code>LN LOG EXP SQR SQRT COS SIN TAN ARCCOS ARCSIN ARCTAN</code> (angles in degrees),
   * whose parameter may be written without brackets. Operators of the same priority are
   * computed from left to right; the opposite of a number and a function are computed
   * before any operator, as in <code>-3^2</code> equal to 9 and <code>sqr 3+1</code> equal to 10.
   */
  private class Calculator {
    private static final String WHITE_SPACES = " \t\n\r";
    private static final String DELIMITERS = " \t\n\r-+*/^().";

    private final String expression;
    private int          index;

    public Calculator(String expression) {
      this.expression = expression;
    }

    /**
     * Returns the value of the whole expression.
     * @throws ParseException if the expression is incorrect
     */
    public Number compute() throws ParseException {
      double value = computeOperation(1);
      skipWhiteSpaces();
      if (this.index < this.expression.length()) {
        throw new ParseException("Unexpected character", this.index);
      }
      return Double.valueOf(value);
    }

    private void skipWhiteSpaces() {
      while (this.index < this.expression.length()
             && WHITE_SPACES.indexOf(this.expression.charAt(this.index)) >= 0) {
        this.index++;
      }
    }

    /**
     * Returns the priority of the binary operator at the current index, or 0 if there's none.
     */
    private int getBinaryOperatorPriority() {
      if (this.index < this.expression.length()) {
        switch (this.expression.charAt(this.index)) {
          case '+' :
          case '-' :
            return 1;
          case '*' :
          case '/' :
            return 2;
          case '^' :
            return 3;
        }
      }
      return 0;
    }

    /**
     * Returns the value of the operands bound by operators of a priority
     * at least equal to <code>minPriority</code>.
     */
    private double computeOperation(int minPriority) throws ParseException {
      double value = computeOperand();
      while (true) {
        skipWhiteSpaces();
        int priority = getBinaryOperatorPriority();
        if (priority < minPriority) {
          return value;
        }
        char operator = this.expression.charAt(this.index++);
        // Operators of the same priority are computed from left to right
        double value2 = computeOperation(priority + 1);
        switch (operator) {
          case '+' :
            value += value2;
            break;
          case '-' :
            value -= value2;
            break;
          case '*' :
            value *= value2;
            break;
          case '/' :
            value /= value2;
            break;
          default :
            value = Math.pow(value, value2);
            break;
        }
      }
    }

    /**
     * Returns the value of a number, of an expression between brackets, of
     * the opposite of an operand or of a function applied to an operand.
     */
    private double computeOperand() throws ParseException {
      skipWhiteSpaces();
      if (this.index >= this.expression.length()) {
        throw new ParseException("Missing operand", this.index);
      }
      char c = this.expression.charAt(this.index);
      if (c == '-') {
        this.index++;
        return -computeOperand();
      } else if (c == '(') {
        this.index++;
        double value = computeOperation(1);
        skipWhiteSpaces();
        if (this.index >= this.expression.length()
            || this.expression.charAt(this.index) != ')') {
          throw new ParseException("Missing closing bracket", this.index);
        }
        this.index++;
        return value;
      } else if (DELIMITERS.indexOf(c) >= 0) {
        throw new ParseException("Unexpected character", this.index);
      }

      ParsePosition position = new ParsePosition(this.index);
      Number literal = numberFormat.parse(this.expression, position);
      if (literal != null && position.getIndex() > this.index) {
        this.index = position.getIndex();
        if (lengthUnit != null) {
          return lengthUnit.centimeterToUnit(literal.floatValue());
        } else {
          return literal.doubleValue();
        }
      }

      // Search the name of a function, which ends at the first delimiter
      int start = this.index;
      while (this.index < this.expression.length()
             && DELIMITERS.indexOf(this.expression.charAt(this.index)) < 0) {
        this.index++;
      }
      String function = this.expression.substring(start, this.index).toUpperCase(Locale.ENGLISH);
      if (!isFunction(function)) {
        throw new ParseException("Unknown function", start);
      }
      return computeFunction(function, computeOperand());
    }

    private boolean isFunction(String function) {
      return "LN".equals(function)
          || "LOG".equals(function)
          || "EXP".equals(function)
          || "SQR".equals(function)
          || "SQRT".equals(function)
          || "COS".equals(function)
          || "SIN".equals(function)
          || "TAN".equals(function)
          || "ARCCOS".equals(function)
          || "ARCSIN".equals(function)
          || "ARCTAN".equals(function);
    }

    private double computeFunction(String function, double param) {
      if (function.equals("LN")) {
        return Math.log(param);
      } else if (function.equals("LOG")) {
        return Math.log(param) / Math.log(10.);
      } else if (function.equals("EXP")) {
        return Math.exp(param);
      } else if (function.equals("SQR")) {
        return param * param;
      } else if (function.equals("SQRT")) {
        return Math.sqrt(param);
      } else if (function.equals("COS")) {
        return Math.cos(Math.toRadians(param));
      } else if (function.equals("SIN")) {
        return Math.sin(Math.toRadians(param));
      } else if (function.equals("TAN")) {
        return Math.tan(Math.toRadians(param));
      } else if (function.equals("ARCCOS")) {
        return Math.toDegrees(Math.acos(param));
      } else if (function.equals("ARCSIN")) {
        return Math.toDegrees(Math.asin(param));
      } else {
        return Math.toDegrees(Math.atan(param));
      }
    }
  }
}
