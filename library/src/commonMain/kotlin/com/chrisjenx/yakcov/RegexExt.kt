package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.NumberParseException
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private val emailRegex =
    """(?:[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])"""
        .toRegex(RegexOption.IGNORE_CASE)

@OptIn(ExperimentalContracts::class)
fun String?.isEmail(): Boolean {
    contract { returns(true) implies (this@isEmail != null) }
    this ?: return false
    return matches(emailRegex)
}

/**
 * Lenient, dependency-free "looks like a phone number" check (issue #29). The server is
 * authoritative and normalizes to E.164; this only gates obviously-wrong input client-side.
 * For region-aware validity (rejecting wrong-region/structurally-invalid numbers), use the
 * opt-in libphonenumber path ([isPhoneNumber]) instead.
 *
 * Accepts an optional leading `+`, then ASCII digits and the separators `space ( ) . / -`,
 * with a total ASCII-digit count in the E.164-sane range 7..15.
 *
 * **Portability is load-bearing** — this runs on three regex engines (java.util.regex on
 * JVM/Android, the JetBrains Kotlin engine on Native+WasmJS, and ECMAScript `RegExp(…, 'u')`
 * on Kotlin/JS). Every construct here is in the portable intersection:
 * - explicit `[0-9]` (never `\d`) and counting via `it in '0'..'9'` (never `Char.isDigit()`),
 *   because `\d`/`Char.isDigit()` are Unicode-aware on some targets and would count
 *   Arabic-Indic/fullwidth digits, diverging per platform;
 * - `-` is **last** in the class (literal) and only `+` is escaped — under the JS `'u'` flag,
 *   escaping a non-syntax char (e.g. `\-`) throws a runtime `SyntaxError`. Do not "tidy" it;
 * - a fixed ASCII end-trim (not `String.trim()`, which is Unicode-aware/expect-actual and has
 *   diverged on JS for NBSP);
 * - `matches()` for full-string anchoring (no `^`/`$`, which differ under ECMAScript);
 * - a 40-char input cap to avoid the Kotlin engine's pathological-backtracking crashes
 *   (KT-46211 on Native + WasmJS).
 */
fun String?.isPhoneNumberFormat(): Boolean {
    val raw = this ?: return false
    val trimmed = raw.trim { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
    if (trimmed.isEmpty() || trimmed.length > PHONE_FORMAT_MAX_LEN) return false
    if (!trimmed.matches(phoneFormatRegex)) return false
    return trimmed.count { it in '0'..'9' } in 7..15
}

private const val PHONE_FORMAT_MAX_LEN = 40

// hyphen LAST (literal); only '+' escaped — see isPhoneNumberFormat() for why this exact form.
private val phoneFormatRegex = Regex("""\+?[0-9 ()./-]{6,}""")

internal expect val phoneUtil: PhoneNumberUtil


/**
 * Check if is phone number to best ability of each platform.
 *
 * @param defaultRegion The default region to use if the number is not in international format.
 *  it's two digits country code. e.g. "US", "GB", "ES"
 */
fun String?.isPhoneNumber(defaultRegion: String? = "US"): Boolean {
    this ?: return false
    return try {
        val result = phoneUtil.parse(this, defaultRegion?.uppercase())
        phoneUtil.isValidNumber(result)
    } catch (expected: NumberParseException) {
        // Thrown on every partial/invalid keystroke (e.g. "6", "65"). Expected and harmless —
        // do NOT log it (issue #29: this spammed the browser console on Kotlin/Wasm).
        false
    } catch (t: Throwable) {
        // Unexpected — e.g. libphonenumber missing at runtime (it is compileOnly). Keep it
        // visible so the actionable "add the dependency" message is not silently swallowed.
        t.printStackTrace()
        false
    }
}
