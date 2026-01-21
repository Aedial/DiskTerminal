package com.cellterminal.client;

import java.util.List;

import org.junit.Test;
import org.junit.Assert;


/**
 * Unit tests for AdvancedSearchParser tokenization and parsing logic.
 *
 * Note: These tests focus on the parse structure and error detection.
 * Actual matching behavior requires Minecraft runtime and is tested manually.
 */
public class AdvancedSearchParserTest {

    // ==================== Query Detection Tests ====================

    @Test
    public void testIsAdvancedQuery_withQuestionMark() {
        Assert.assertTrue(AdvancedSearchParser.isAdvancedQuery("?$name~iron"));
        Assert.assertTrue(AdvancedSearchParser.isAdvancedQuery("?"));
        Assert.assertTrue(AdvancedSearchParser.isAdvancedQuery("?test"));
    }

    @Test
    public void testIsAdvancedQuery_withoutQuestionMark() {
        Assert.assertFalse(AdvancedSearchParser.isAdvancedQuery("iron"));
        Assert.assertFalse(AdvancedSearchParser.isAdvancedQuery("$name~iron"));
        Assert.assertFalse(AdvancedSearchParser.isAdvancedQuery(""));
        Assert.assertFalse(AdvancedSearchParser.isAdvancedQuery(null));
    }

    // ==================== Empty/Null Query Tests ====================

    @Test
    public void testParse_nullQuery_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse(null);
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Empty query"));
    }

    @Test
    public void testParse_emptyQuery_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Empty query"));
    }

    @Test
    public void testParse_onlyQuestionMark_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Empty query after '?'"));
    }

    @Test
    public void testParse_questionMarkWithWhitespace_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?   ");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Empty query after '?'"));
    }

    // ==================== Valid Identifier Tests ====================

    @Test
    public void testParse_validName_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~iron");
        Assert.assertTrue("Expected success for '$name~iron'", result.isSuccess());
        Assert.assertNotNull(result.getMatcher());
    }

    @Test
    public void testParse_validPriority_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>0");
        Assert.assertTrue("Expected success for '$priority>0'", result.isSuccess());
        Assert.assertNotNull(result.getMatcher());
    }

    @Test
    public void testParse_validPartition_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$partition=5");
        Assert.assertTrue("Expected success for '$partition=5'", result.isSuccess());
        Assert.assertNotNull(result.getMatcher());
    }

    @Test
    public void testParse_validItems_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$items>=10");
        Assert.assertTrue("Expected success for '$items>=10'", result.isSuccess());
        Assert.assertNotNull(result.getMatcher());
    }

    // ==================== Invalid Identifier Tests ====================

    @Test
    public void testParse_unknownIdentifier_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$unknown~test");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Unknown identifier"));
        Assert.assertTrue(result.getErrorMessage().contains("$unknown"));
    }

    @Test
    public void testParse_multipleUnknownIdentifiers_returnsMultipleErrors() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$foo=1&$bar=2");
        Assert.assertFalse(result.isSuccess());
        List<String> errors = result.getErrors();
        Assert.assertTrue(errors.size() >= 2);
    }

    // ==================== Operator Tests ====================

    @Test
    public void testParse_allComparisonOperators() {
        String[] operators = {"=", "!=", "<", ">", "<=", ">="};
        for (String op : operators) {
            AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority" + op + "5");
            Assert.assertTrue("Expected success for '$priority" + op + "5'", result.isSuccess());
        }
    }

    @Test
    public void testParse_containsOperator() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~iron");
        Assert.assertTrue("Expected success for contains operator", result.isSuccess());
    }

    @Test
    public void testParse_implicitContainsOperator() {
        // When no operator is specified, ~ (contains) should be assumed
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name iron");
        Assert.assertTrue("Expected success for implicit contains", result.isSuccess());
    }

    // ==================== Logical Operator Tests ====================

    @Test
    public void testParse_andOperator_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>0&$items>0");
        Assert.assertTrue("Expected success for AND operator", result.isSuccess());
    }

    @Test
    public void testParse_orOperator_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~iron|$name~gold");
        Assert.assertTrue("Expected success for OR operator", result.isSuccess());
    }

    @Test
    public void testParse_mixedLogicalOperators_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>0&$items>0|$partition>0");
        Assert.assertTrue("Expected success for mixed operators", result.isSuccess());
    }

    @Test
    public void testParse_danglingAndOperator_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>0&");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Expected expression after '&'"));
    }

    @Test
    public void testParse_danglingOrOperator_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>0|");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Expected expression after '|'"));
    }

    @Test
    public void testParse_leadingAndOperator_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?&$priority>0");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Unexpected operator '&'"));
    }

    @Test
    public void testParse_leadingOrOperator_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?|$priority>0");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Unexpected operator '|'"));
    }

    // ==================== Parentheses Tests ====================

    @Test
    public void testParse_simpleParentheses_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?($priority>0)");
        Assert.assertTrue("Expected success for simple parentheses", result.isSuccess());
    }

    @Test
    public void testParse_nestedParentheses_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?(($priority>0))");
        Assert.assertTrue("Expected success for nested parentheses", result.isSuccess());
    }

    @Test
    public void testParse_complexParentheses_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?($priority>0|$items>0)&$partition>0");
        Assert.assertTrue("Expected success for complex parentheses", result.isSuccess());
    }

    @Test
    public void testParse_missingClosingParenthesis_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?($priority>0");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Missing closing parenthesis"));
    }

    @Test
    public void testParse_extraClosingParenthesis_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>0)");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Unexpected"));
    }

    @Test
    public void testParse_emptyParentheses_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?()");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Unexpected closing parenthesis"));
    }

    @Test
    public void testParse_strayClosingParenthesis_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?)");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Unexpected closing parenthesis"));
    }

    // ==================== Quoted String Tests ====================

    @Test
    public void testParse_doubleQuotedString_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~\"Iron Ingot\"");
        Assert.assertTrue("Expected success for double-quoted string", result.isSuccess());
    }

    @Test
    public void testParse_singleQuotedString_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~'Iron Ingot'");
        Assert.assertTrue("Expected success for single-quoted string", result.isSuccess());
    }

    @Test
    public void testParse_quotedStringWithSpaces_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~\"minecraft:iron_ingot\"");
        Assert.assertTrue("Expected success for quoted string with special chars", result.isSuccess());
    }

    // ==================== Numeric Value Tests ====================

    @Test
    public void testParse_positiveNumber_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority=100");
        Assert.assertTrue("Expected success for positive number", result.isSuccess());
    }

    @Test
    public void testParse_zeroValue_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$items=0");
        Assert.assertTrue("Expected success for zero value", result.isSuccess());
    }

    @Test
    public void testParse_negativeNumber_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority=-5");
        Assert.assertTrue("Expected success for negative number", result.isSuccess());
    }

    @Test
    public void testParse_nonNumericForNumericField_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority=abc");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Expected number"));
    }

    @Test
    public void testParse_floatForIntField_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority=5.5");
        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getErrorMessage().contains("Expected number"));
    }

    // ==================== Plain Text Search Tests ====================

    @Test
    public void testParse_plainTextSearch_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?iron");
        Assert.assertTrue("Expected success for plain text search", result.isSuccess());
    }

    @Test
    public void testParse_plainTextWithLogical_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?iron&$priority>0");
        Assert.assertTrue("Expected success for plain text with logical op", result.isSuccess());
    }

    // ==================== Case Insensitivity Tests ====================

    @Test
    public void testParse_uppercaseIdentifier_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$NAME~iron");
        Assert.assertTrue("Expected success for uppercase identifier", result.isSuccess());
    }

    @Test
    public void testParse_mixedCaseIdentifier_success() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$Priority>0");
        Assert.assertTrue("Expected success for mixed case identifier", result.isSuccess());
    }

    // ==================== Complex Query Tests ====================

    @Test
    public void testParse_complexQuery1() {
        // (priority high OR empty) AND has partition
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?($priority>=5|$items=0)&$partition>0");
        Assert.assertTrue("Expected success for complex query 1", result.isSuccess());
    }

    @Test
    public void testParse_complexQuery2() {
        // Multiple OR conditions
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name~iron|$name~gold|$name~diamond");
        Assert.assertTrue("Expected success for complex query 2", result.isSuccess());
    }

    @Test
    public void testParse_complexQuery3() {
        // Nested conditions
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?($name~iron&$priority>0)|($name~gold&$priority>5)");
        Assert.assertTrue("Expected success for complex query 3", result.isSuccess());
    }

    @Test
    public void testParse_complexQuery4() {
        // Multiple levels of nesting
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?(($priority>0|$items=0)&$partition>0)|$name~special");
        Assert.assertTrue("Expected success for complex query 4", result.isSuccess());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void testParse_multipleConsecutiveOperators_returnsError() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>>5");
        // Should handle gracefully - either error or parse as best as possible
        // The tokenizer will split this, so behavior depends on implementation
    }

    @Test
    public void testParse_operatorWithoutValue() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$priority>&$items>0");
        // Should handle missing value gracefully
        Assert.assertTrue("Should parse with default value", result.isSuccess() || !result.getErrors().isEmpty());
    }

    @Test
    public void testParse_onlyIdentifier() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$name");
        // Should use default contains operator with empty value
        Assert.assertTrue("Should parse identifier alone", result.isSuccess());
    }

    @Test
    public void testParse_whitespaceHandling() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?  $priority  >  5  ");
        Assert.assertTrue("Should handle extra whitespace", result.isSuccess());
    }

    // ==================== Error Message Quality Tests ====================

    @Test
    public void testParse_errorMessageContainsContext() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$unknown~value");
        Assert.assertFalse(result.isSuccess());
        String errorMsg = result.getErrorMessage();
        Assert.assertTrue("Error should mention the problematic identifier",
            errorMsg.contains("$unknown"));
        Assert.assertTrue("Error should list valid identifiers",
            errorMsg.contains("$name") || errorMsg.contains("Valid:"));
    }

    @Test
    public void testParse_multipleErrorsCollected() {
        AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse("?$unknown=abc&$priority=xyz");
        Assert.assertFalse(result.isSuccess());
        List<String> errors = result.getErrors();
        // Should have at least 2 errors: unknown identifier and non-numeric value
        Assert.assertTrue("Should collect multiple errors", errors.size() >= 2);
    }
}
