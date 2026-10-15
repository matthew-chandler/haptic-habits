package com.machandler.haptichabits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonRulesTest {
    @Test
    fun nameIsTrimmedAndMustNotBeBlank() {
        assertEquals("Snack", ButtonRules.cleanName("  Snack "))
        assertNull(ButtonRules.cleanName(""))
        assertNull(ButtonRules.cleanName("   "))
    }

    @Test
    fun nameHasAMaximumLengthAfterTrimming() {
        val longest = "x".repeat(ButtonRules.MAX_NAME_LENGTH)
        assertEquals(longest, ButtonRules.cleanName(" $longest "))
        assertNull(ButtonRules.cleanName(longest + "x"))
    }

    @Test
    fun valueIsANonZeroWholeNumberWithinRange() {
        assertEquals(1, ButtonRules.parseValue("1"))
        assertEquals(-5, ButtonRules.parseValue("-5"))
        assertEquals(999, ButtonRules.parseValue("999"))
        assertEquals(-999, ButtonRules.parseValue("-999"))
        listOf("", "-", "0", "-0", "1000", "-1000", "1.5", "abc").forEach {
            assertNull(it, ButtonRules.parseValue(it))
        }
    }

    @Test
    fun valueErrorExplainsInvalidValuesButNotHalfTypedOnes() {
        assertNull(ButtonRules.valueError(""))
        assertNull(ButtonRules.valueError("-"))
        assertNull(ButtonRules.valueError("-3"))
        assertNotNull(ButtonRules.valueError("0"))
        assertNotNull(ButtonRules.valueError("1000"))
        assertNotNull(ButtonRules.valueError("1.5"))
    }

    @Test
    fun valueFieldAcceptsAnOptionalMinusAndUpToThreeDigits() {
        listOf("", "-", "7", "-7", "999", "-999").forEach { assertTrue(it, ButtonRules.isTypeableValue(it)) }
        listOf("1000", "--1", "1-", "+1", "1.5", "a", " 1").forEach { assertFalse(it, ButtonRules.isTypeableValue(it)) }
    }

    @Test
    fun toggleSignFlipsWhatIsTyped() {
        assertEquals("-5", ButtonRules.toggleSign("5"))
        assertEquals("5", ButtonRules.toggleSign("-5"))
        assertEquals("-", ButtonRules.toggleSign(""))
        assertEquals("", ButtonRules.toggleSign("-"))
    }

    @Test
    fun valuesAreShownWithAnExplicitSign() {
        assertEquals("+3", ButtonRules.formatValue(3))
        assertEquals("-2", ButtonRules.formatValue(-2))
    }
}
