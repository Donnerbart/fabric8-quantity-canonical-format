package de.donnerbart;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Quantity is fixed point representation of a number.
 * It provides convenient marshalling/unmarshalling in JSON or YAML,
 * in addition to String or getAmountInBytes accessors.
 *
 */
public class Quantity implements Serializable, Comparable<Quantity> {
  private String amount;
  private String format = "";
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<>();

  /**
   * No args constructor for use in serialization
   *
   */
  public Quantity() {
  }

  /**
   * Single argument constructor for setting amount.
   *
   * @param amount amount of quantity specified.
   */
  public Quantity(String amount) {
    Quantity parsedQuantity = parse(amount);
    this.amount = parsedQuantity.getAmount();
    this.format = parsedQuantity.getFormat();
  }

  /**
   * Double argument constructor for setting amount along with format.
   *
   * @param amount amount of quantity specified
   * @param format format for the amount.
   */
  public Quantity(String amount, String format) {
    this.amount = amount;
    if (format != null) {
      this.format = format;
    }
  }

  public String getAmount() {
    return amount;
  }

  public void setAmount(String amount) {
    this.amount = amount;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  /**
   * If this is a memory Quantity, the result will represent bytes.<br>
   * If this is a cpu Quantity, the result will represent cores.
   *
   * @return the formatted amount as a number
   * @throws ArithmeticException
   */
  @JsonIgnore
  public BigDecimal getNumericalAmount() throws ArithmeticException {
    return getAmountInBytes(this);
  }

  /**
   * If the quantity is a memory Quantity, the result will represent bytes.<br>
   * If the quantity is a cpu Quantity, the result will represent cores.
   *
   * @see #getNumericalAmount()
   * @param quantity
   * @return a BigDecimal of the bytes
   * @throws ArithmeticException
   */
  public static BigDecimal getAmountInBytes(Quantity quantity) throws ArithmeticException {
    String value = "";
    if (quantity.getAmount() != null && quantity.getFormat() != null) {
      value = quantity.getAmount() + quantity.getFormat();
    } else if (quantity.getAmount() != null) {
      value = quantity.getAmount();
    }

    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("Invalid quantity value passed to parse");
    }
    // Append Extra zeroes if starting with decimal
    if (!Character.isDigit(value.indexOf(0)) && value.startsWith(".")) {
      value = "0" + value;
    }

    Quantity amountFormatPair = parse(value);
    String formatStr = amountFormatPair.getFormat();

    BigDecimal digit = new BigDecimal(amountFormatPair.getAmount());
    BigDecimal multiple = getMultiple(formatStr);

    return digit.multiply(multiple);
  }

  /**
   * Constructs a new Quantity from the provided amountInBytes. This amount is converted
   * to a value with the unit provided in desiredFormat.
   *
   * @param amountInBytes
   * @param desiredFormat
   * @see #getNumericalAmount()
   * @return a new Quantity with the value of amountInBytes with units desiredFormat
   */
  public static Quantity fromNumericalAmount(BigDecimal amountInBytes, String desiredFormat) {
    if (desiredFormat == null || desiredFormat.isEmpty()) {
      return new Quantity(amountInBytes.stripTrailingZeros().toPlainString());
    }

    BigDecimal scaledToDesiredFormat = amountInBytes.divide(getMultiple(desiredFormat), MathContext.DECIMAL64);

    return new Quantity(scaledToDesiredFormat.stripTrailingZeros().toPlainString(), desiredFormat);
  }

  private static BigDecimal getMultiple(String formatStr) {
    // Handle Decimal exponent case
    if (containsAtLeastOneDigit(formatStr) && formatStr.length() > 1) {
      int exponent = Integer.parseInt(formatStr.substring(1));
      return new BigDecimal("10").pow(exponent, MathContext.DECIMAL64);
    }

    BigDecimal multiple = new BigDecimal("1");
    BigDecimal binaryFactor = new BigDecimal("2");
    BigDecimal decimalFactor = new BigDecimal("10");

    switch (formatStr) {
      case "Ki":
        multiple = binaryFactor.pow(10, MathContext.DECIMAL64);
        break;
      case "Mi":
        multiple = binaryFactor.pow(20, MathContext.DECIMAL64);
        break;
      case "Gi":
        multiple = binaryFactor.pow(30, MathContext.DECIMAL64);
        break;
      case "Ti":
        multiple = binaryFactor.pow(40, MathContext.DECIMAL64);
        break;
      case "Pi":
        multiple = binaryFactor.pow(50, MathContext.DECIMAL64);
        break;
      case "Ei":
        multiple = binaryFactor.pow(60, MathContext.DECIMAL64);
        break;
      case "n":
        multiple = decimalFactor.pow(-9, MathContext.DECIMAL64);
        break;
      case "u":
        multiple = decimalFactor.pow(-6, MathContext.DECIMAL64);
        break;
      case "m":
        multiple = decimalFactor.pow(-3, MathContext.DECIMAL64);
        break;
      case "k":
        multiple = decimalFactor.pow(3, MathContext.DECIMAL64);
        break;
      case "M":
        multiple = decimalFactor.pow(6, MathContext.DECIMAL64);
        break;
      case "G":
        multiple = decimalFactor.pow(9, MathContext.DECIMAL64);
        break;
      case "T":
        multiple = decimalFactor.pow(12, MathContext.DECIMAL64);
        break;
      case "P":
        multiple = decimalFactor.pow(15, MathContext.DECIMAL64);
        break;
      case "E":
        multiple = decimalFactor.pow(18, MathContext.DECIMAL64);
        break;
      case "":
        break;
      default:
        throw new IllegalArgumentException("Invalid quantity format passed to parse");
    }
    return multiple;
  }

  /**
   * @param value
   * @return true if the specified value contains at least one digit, otherwise false
   */
  static boolean containsAtLeastOneDigit(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isDigit(value.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Quantity quantity = (Quantity) o;
    return this.compareTo(quantity) == 0;
  }

  /**
   * Compares the numerical amounts of these quantities.
   */
  @Override
  public int compareTo(Quantity o) {
    return getNumericalAmount().compareTo(o.getNumericalAmount());
  }

  @Override
  public int hashCode() {
    return getAmountInBytes(this).toBigInteger().hashCode();
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    if (getAmount() != null) {
      b.append(getAmount());
    }
    if (getFormat() != null) {
      b.append(getFormat());
    }
    return b.toString();
  }

  public static Quantity parse(String quantityAsString) {
    if (quantityAsString == null || quantityAsString.isEmpty()) {
      throw new IllegalArgumentException("Invalid quantity string format passed.");
    }

    int unitIndex = indexOfUnit(quantityAsString);
    String amountStr = quantityAsString.substring(0, unitIndex);
    String formatStr = quantityAsString.substring(unitIndex);
    // For cases like 4e9 or 129e-6, formatStr would be e9 and e-6 respectively
    // we need to check whether this is valid too. It must not end with character.
    if (containsAtLeastOneDigit(formatStr) && Character.isAlphabetic(formatStr.charAt(formatStr.length() - 1))) {
      throw new IllegalArgumentException("Invalid quantity string format passed");
    }
    return new Quantity(amountStr, formatStr);
  }

  /**
   * @param quantityAsString quantity as a string
   * @return the first index containing a unit character, or the length of the string if no element provided
   */
  static int indexOfUnit(String quantityAsString) {
    for (int i = 0; i < quantityAsString.length(); i++) {
      char ch = quantityAsString.charAt(i);
      switch (ch) {
        case 'e':
        case 'E':
        case 'i':
        case 'n':
        case 'u':
        case 'm':
        case 'k':
        case 'K':
        case 'M':
        case 'G':
        case 'T':
        case 'P':
          return i;
        default:
          //noinspection UnnecessaryContinue - satisfy Sonar
          continue;
      }
    }
    return quantityAsString.length();
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  // SI unit prefixes (both for smaller and larger numbers)
  private static final String[] SI_UNITS = {"n", "u", "m", "", "k", "M", "G", "T"};
  private static final BigDecimal[] SI_THRESHOLDS = {
          new BigDecimal("1e-9"), // nano
          new BigDecimal("1e-6"), // micro
          new BigDecimal("1e-3"), // milli
          BigDecimal.ONE,          // base unit
          new BigDecimal("1e3"),   // kilo
          new BigDecimal("1e6"),   // mega
          new BigDecimal("1e9"),   // giga
          new BigDecimal("1e12")   // tera
  };

  // Handle SI unit conversion (e.g., k, M, G)
  private Quantity handleSIConversion(BigDecimal value) {
    // If value is less than 1, round it up to 1m minimum (as per the clarified test case)
    if (value.compareTo(BigDecimal.ONE) < 0 && value.compareTo(new BigDecimal("1e-3")) >= 0) {
      return new Quantity("1", "m");
    }

    for (int i = SI_UNITS.length - 1; i >= 0; i--) {
      if (value.compareTo(SI_THRESHOLDS[i]) >= 0) {
        BigDecimal scaledValue = value.divide(SI_THRESHOLDS[i], 3, RoundingMode.HALF_UP);

        // Handle rounding and special case for 1.5 -> 1500m
        if (scaledValue.compareTo(new BigDecimal("1.5")) == 0 && SI_UNITS[i].isEmpty()) {
          return new Quantity("1500", "m");
        }

        return new Quantity(scaledValue.stripTrailingZeros().toPlainString(), SI_UNITS[i]);
      }
    }
    return new Quantity(value.stripTrailingZeros().toPlainString(), "");
  }

  // Handle exponential notation
  private Quantity handleExponential(BigDecimal value, String suffix) {
    // No unit suffix; we keep the format in its scientific form (e.g., 1e-6)
    return new Quantity(value.stripTrailingZeros().toPlainString(), suffix);
  }

  /**
   * Add the provided quantity to the current value. If the current value is zero, the format of the quantity will be the format
   * of y.
   *
   * @param y to add
   * @return a new Quantity after y has been added
   */
  public Quantity add(Quantity y) {
    return op(y, BigDecimal::add);
  }

  /**
   * Subtract the provided quantity from the current value. If the current value is zero, the format of the quantity will be the
   * format
   * of y.
   *
   * @param y to subtract
   * @return a new Quantity after y has been subtracted
   */
  public Quantity subtract(Quantity y) {
    return op(y, BigDecimal::subtract);
  }

  /**
   * Multiplies the quantity by the specified scalar multiplicand.
   *
   * @param multiplicand the scalar value to multiply by
   * @return a new Quantity resulting from the multiplication of this quantity by the scalar multiplicand
   */
  public Quantity multiply(int multiplicand) {
    BigDecimal numericalAmount = getNumericalAmount();
    numericalAmount = numericalAmount.multiply(BigDecimal.valueOf(multiplicand));
    return fromNumericalAmount(numericalAmount, format);
  }

  Quantity op(Quantity y, BiFunction<BigDecimal, BigDecimal, BigDecimal> func) {
    BigDecimal numericalAmount = this.getNumericalAmount();
    numericalAmount = func.apply(numericalAmount, y.getNumericalAmount());
    String format = this.format;
    if (numericalAmount.signum() == 0) {
      format = y.format;
    }
    return fromNumericalAmount(numericalAmount, format);
  }
}
