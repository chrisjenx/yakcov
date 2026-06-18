package com.chrisjenx.yakcov

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cross-platform corpus for the dependency-free lenient phone-format gate (issue #29).
 *
 * These tests live in commonTest **on purpose**: they must execute on every target
 * (JVM, Android, Kotlin/JS, Kotlin/WasmJS, Kotlin/Native iOS) because [isPhoneNumberFormat]
 * relies on regex/char-class semantics that historically diverge per engine. The 5 targets
 * collapse to 3 regex engines: java.util.regex (JVM/Android), the shared JetBrains Kotlin
 * engine (Native + WasmJS), and ECMAScript RegExp with the 'u' flag (Kotlin/JS only). A
 * JVM-only test cannot catch a JS 'u'-flag SyntaxError, so this corpus must run everywhere.
 *
 * No [initPhoneNumberUtil] / libphonenumber here — the lenient gate is pure common code.
 */
class PhoneFormatTest {

    /** The lenient gate MUST accept these — real numbers as humans type them worldwide. */
    private val mustPass = listOf(
        // NANP (US/CA)
        "+1 650-899-1234", "(650) 899-1234", "650.899.1234", "1-650-899-1234", "650 899 1234",
        "+1 (650) 899-1234", "+1(650)899-1234", "6508991234", "1 (800) 555-0199", "+1-416-555-0132",
        "001 650 899 1234", ".6508991234", "+1.650.899.1234", "650 . 899 . 1234", "+16508991234",
        "00 1 650 899 1234", "011 1 650 899 1234", "650/899/1234",
        // UK
        "+44 20 7946 0958", "020 7946 0958", "07700 900123", "+44 7700 900123", "+44 (0)20 7946 0958",
        "0800 123 4567", "0044 7700 900123", "+44  20  7946  0958",
        // DE / FR / ES / IT / NL / CH / AT
        "+49 30 901820", "030 901820", "0049 30 901820", "+49 170 1234567",
        "+33 1 09 75 83 51", "01 09 75 83 51", "0033 6 12 34 56 78",
        "+34 612 345 678", "612 345 678", "+39 06 6982 1234", "+39 06 698 1", "+390236618300",
        "+31 20 123 4567", "+41 44 668 18 00", "+43 1 58058",
        // Asia / Oceania
        "+91 98765 43210", "+86 138 0013 8000", "+81 3 1234 5678", "03-1234-5678",
        "+61 4 1234 5678", "0412 345 678", "(02) 9374 4000", "+65 6123 4567", "6123 4567",
        "+852 2123 4567", "2123 4567", "+64 9 123 4567", "09 123 4567",
        "+62 812 3456 7890", "+63 917 123 4567",
        // Latin America
        "+55 11 91234-5678", "(11) 91234-5678", "+52 (55) 1234 5678", "+52 1 55 1234 5678",
        "+5491187654321",
        // Africa / Middle East / South Asia
        "+234 802 123 4567", "+27 82 123 4567", "082 123 4567", "+254 712 345678",
        "+971 50 123 4567", "+92 300 1234567",
        // Russia
        "8 (495) 123-45-67", "+7 495 123-45-67", "8-800-555-35-35",
        // Boundary witnesses
        "+683 4002", "+690 4567",            // shortest real E.164 — TRUE 7-digit floor
        "1234567",                            // bare 7-digit, at floor
        "123456789012345",                    // bare 15-digit, at ceiling
        "0011 61 4 1234 5678", "+888 1234 5678 9012", // exactly 15 digits
        "+1 868 123 4567 8",
        // Whitespace / '+'-spacing handling
        "  650 899 1234  ", "+ 1 650 899 1234", // space after leading '+' is intentionally accepted
        // International toll-free / UAN
        "+800 345 600",
    )

    /** The lenient gate MUST reject these — non-numbers, out-of-range, or non-ASCII junk. */
    private val mustFail = listOf(
        // Empty / blank / below the 7-digit floor
        "", "   ", "1", "12", "123", "43435", "650899", "(123)", "+1", "+44", "12-34", "99999",
        "611", "*67",
        // Above the 15-digit ceiling
        "1234567890123456", "+12345678901234567890", "00000000000000000000",
        "123456789012345678901234567890", "4111 1111 1111 1111", "0011 49 1609 1234567",
        // Letters / vanity / emails / schemes
        "1-800-FLOWERS", "CALL-NOW", "phone", "+1 650 ABC 1234", "650899l234",
        "john@example.com", "tel:+16508991234", "0xx11 91234-5678",
        // Disallowed punctuation
        "650,899,1234", "6508991234,1234", "650*899*1234", "#650899", "650.899.1234!",
        "~!@#\$%^&", "\$1,650.00", "#31#",
        // '+' position / multiplicity
        "++16508991234", "1+6508991234", "650 899 1234 +", "(+44) 20 7946 0958",
        // Non-ASCII separators / digits / symbols  (built with \u escapes so source is unambiguous)
        "650—899—1234",                                   // em-dash U+2014 between groups
        "650 899 1234",                                   // NBSP U+00A0 separators
        "６５０８９９１２３４", // fullwidth digits ６５０８９９１２３４
        "📞6508991234",                                   // 📞 emoji prefix (surrogate pair)
        "☎ 650 899 1234",                                      // ☎ telephone symbol U+260E
        // Internal control whitespace (only ASCII END-trim happens; internal is not in the class)
        "650\n899\n1234", "650\t899\t1234",
        // Word extensions
        "+1 (650) 899-1234 ext 567",
    )

    /**
     * Lenient ACCEPTS these but libphonenumber/`StrictPhone` REJECTS them. They lock the
     * lenient boundary and document exactly what the opt-in strict rule adds (region/realness).
     * A format-only gate cannot decide these without becoming locale-specific.
     */
    private val strictOnlyAccepted = listOf(
        "65025300001", "650253000", "+1 (123) 456 7890", "+1 (071) 255 1234", "234 911 5678",
        "+1 000 000 0000", "0000000000", "1234567890",
        "2024-06-18", "20240618",               // date-like false-positives; only StrictPhone rejects
        "192.168.1.100", "10.0.0.138",          // IPv4 false-positives; only StrictPhone rejects
        ")))1234567(((", "-1234567", "1234 5678 9012",
        "00 49 1609 1234567", "07745973912",
    )

    @Test
    fun acceptsEveryMustPass() {
        val rejected = mustPass.filterNot { it.isPhoneNumberFormat() }
        assertTrue(rejected.isEmpty(), "lenient gate should ACCEPT these but rejected: $rejected")
    }

    @Test
    fun rejectsEveryMustFail() {
        val accepted = mustFail.filter { it.isPhoneNumberFormat() }
        assertTrue(accepted.isEmpty(), "lenient gate should REJECT these but accepted: $accepted")
    }

    @Test
    fun acceptsStrictOnlyDivergences() {
        val rejected = strictOnlyAccepted.filterNot { it.isPhoneNumberFormat() }
        assertTrue(
            rejected.isEmpty(),
            "lenient gate should ACCEPT these (only StrictPhone rejects) but rejected: $rejected",
        )
    }

    @Test
    fun nullIsRejected() {
        assertFalse((null as String?).isPhoneNumberFormat())
    }

    @Test
    fun floorAndCeilingBoundaries() {
        assertFalse("650899".isPhoneNumberFormat(), "6 digits is below the floor")
        assertTrue("1234567".isPhoneNumberFormat(), "7 digits is the floor")
        assertTrue("123456789012345".isPhoneNumberFormat(), "15 digits is the ceiling")
        assertFalse("1234567890123456".isPhoneNumberFormat(), "16 digits exceeds E.164 max")
    }

    @Test
    fun asciiDigitsOnly_unicodeDigitsRejected() {
        // Arabic-Indic ٠١٢٣٤٥٦ — would be counted by Char.isDigit()/\d on JVM/Native/WasmJS but
        // not on JS. Must reject identically everywhere via explicit [0-9] / 'it in 0..9'.
        assertFalse("٠١٢٣٤٥٦".isPhoneNumberFormat())
        // Fullwidth ７ digits
        assertFalse("１２３４５６７".isPhoneNumberFormat())
    }

    @Test
    fun longInputDoesNotCrashAndIsRejected() {
        // KT-46211: the JetBrains Kotlin regex engine (Native + WasmJS) has stack-overflowed on
        // pathological input. The helper caps input length; assert no crash and a false result.
        assertFalse("-".repeat(200).isPhoneNumberFormat())
        assertFalse("1".repeat(200).isPhoneNumberFormat())
        assertFalse(("1234567890 ()./-".repeat(20)).isPhoneNumberFormat())
    }
}
