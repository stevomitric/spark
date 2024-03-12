package org.apache.spark.unsafe.types;

import org.apache.spark.SparkException;
import org.apache.spark.sql.catalyst.util.CollationFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class UTF8StringWithCollationSuite {

  private void assertStartsWith(String pattern, String prefix, String collationName, boolean value)
      throws SparkException {
    assertEquals(UTF8String.fromString(pattern).startsWith(UTF8String.fromString(prefix),
      CollationFactory.collationNameToId(collationName)), value);
  }

  private void assertEndsWith(String pattern, String suffix, String collationName, boolean value)
      throws SparkException {
    assertEquals(UTF8String.fromString(pattern).endsWith(UTF8String.fromString(suffix),
      CollationFactory.collationNameToId(collationName)), value);
  }

  @Test
  public void startsWithTest() throws SparkException {
    assertStartsWith("", "", "UCS_BASIC", true);
    assertStartsWith("c", "", "UCS_BASIC", true);
    assertStartsWith("", "c", "UCS_BASIC", false);
    assertStartsWith("abcde", "a", "UCS_BASIC", true);
    assertStartsWith("abcde", "A", "UCS_BASIC", false);
    assertStartsWith("abcde", "bcd", "UCS_BASIC", false);
    assertStartsWith("abcde", "BCD", "UCS_BASIC", false);
    assertStartsWith("", "", "UNICODE", true);
    assertStartsWith("c", "", "UNICODE", true);
    assertStartsWith("", "c", "UNICODE", false);
    assertStartsWith("abcde", "a", "UNICODE", true);
    assertStartsWith("abcde", "A", "UNICODE", false);
    assertStartsWith("abcde", "bcd", "UNICODE", false);
    assertStartsWith("abcde", "BCD", "UNICODE", false);
    assertStartsWith("", "", "UCS_BASIC_LCASE", true);
    assertStartsWith("c", "", "UCS_BASIC_LCASE", true);
    assertStartsWith("", "c", "UCS_BASIC_LCASE", false);
    assertStartsWith("abcde", "a", "UCS_BASIC_LCASE", true);
    assertStartsWith("abcde", "A", "UCS_BASIC_LCASE", true);
    assertStartsWith("abcde", "abc", "UCS_BASIC_LCASE", true);
    assertStartsWith("abcde", "BCD", "UCS_BASIC_LCASE", false);
    assertStartsWith("", "", "UNICODE_CI", true);
    assertStartsWith("c", "", "UNICODE_CI", true);
    assertStartsWith("", "c", "UNICODE_CI", false);
    assertStartsWith("abcde", "a", "UNICODE_CI", true);
    assertStartsWith("abcde", "A", "UNICODE_CI", true);
    assertStartsWith("abcde", "abc", "UNICODE_CI", true);
    assertStartsWith("abcde", "BCD", "UNICODE_CI", false);
  }

  @Test
  public void endsWithTest() throws SparkException {
    assertEndsWith("", "", "UCS_BASIC", true);
    assertEndsWith("c", "", "UCS_BASIC", true);
    assertEndsWith("", "c", "UCS_BASIC", false);
    assertEndsWith("abcde", "e", "UCS_BASIC", true);
    assertEndsWith("abcde", "E", "UCS_BASIC", false);
    assertEndsWith("abcde", "bcd", "UCS_BASIC", false);
    assertEndsWith("abcde", "BCD", "UCS_BASIC", false);
    assertEndsWith("", "", "UNICODE", true);
    assertEndsWith("c", "", "UNICODE", true);
    assertEndsWith("", "c", "UNICODE", false);
    assertEndsWith("abcde", "e", "UNICODE", true);
    assertEndsWith("abcde", "E", "UNICODE", false);
    assertEndsWith("abcde", "bcd", "UNICODE", false);
    assertEndsWith("abcde", "BCD", "UNICODE", false);
    assertEndsWith("", "", "UCS_BASIC_LCASE", true);
    assertEndsWith("c", "", "UCS_BASIC_LCASE", true);
    assertEndsWith("", "c", "UCS_BASIC_LCASE", false);
    assertEndsWith("abcde", "e", "UCS_BASIC_LCASE", true);
    assertEndsWith("abcde", "E", "UCS_BASIC_LCASE", true);
    assertEndsWith("abcde", "cde", "UCS_BASIC_LCASE", true);
    assertEndsWith("abcde", "BCD", "UCS_BASIC_LCASE", false);
    assertEndsWith("", "", "UNICODE_CI", true);
    assertEndsWith("c", "", "UNICODE_CI", true);
    assertEndsWith("", "c", "UNICODE_CI", false);
    assertEndsWith("abcde", "e", "UNICODE_CI", true);
    assertEndsWith("abcde", "E", "UNICODE_CI", true);
    assertEndsWith("abcde", "cde", "UNICODE_CI", true);
    assertEndsWith("abcde", "BCD", "UNICODE_CI", false);
  }
}
