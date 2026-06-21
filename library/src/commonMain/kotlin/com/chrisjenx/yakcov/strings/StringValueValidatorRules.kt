@file:Suppress("FunctionName")

package com.chrisjenx.yakcov.strings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.ImmutableBooleanState
import com.chrisjenx.yakcov.ImmutableIntState
import com.chrisjenx.yakcov.ImmutableLocalDateState
import com.chrisjenx.yakcov.ImmutableNumberState
import com.chrisjenx.yakcov.ImmutableStringState
import com.chrisjenx.yakcov.ImmutableValueState
import com.chrisjenx.yakcov.ResourceValidationResult
import com.chrisjenx.yakcov.ValidationResult
import com.chrisjenx.yakcov.ValueValidator
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.isEmail
import com.chrisjenx.yakcov.isPhoneNumber
import com.chrisjenx.yakcov.isPhoneNumberFormat
import kotlinx.datetime.LocalDate
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleDay
import yakcov.library.generated.resources.ruleDecimal
import yakcov.library.generated.resources.ruleEmail
import yakcov.library.generated.resources.ruleHexColor
import yakcov.library.generated.resources.ruleInList
import yakcov.library.generated.resources.ruleInvalidDate
import yakcov.library.generated.resources.ruleMaxLength
import yakcov.library.generated.resources.ruleMaxValue
import yakcov.library.generated.resources.ruleMinLength
import yakcov.library.generated.resources.ruleMinLengthNoWhitespace
import yakcov.library.generated.resources.ruleMinValue
import yakcov.library.generated.resources.ruleMonth
import yakcov.library.generated.resources.ruleNumeric
import yakcov.library.generated.resources.rulePasswords
import yakcov.library.generated.resources.rulePhone
import yakcov.library.generated.resources.ruleRequired
import yakcov.library.generated.resources.ruleYear
import kotlin.jvm.JvmName

@Stable
data object Required : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        return if (value.isBlank()) ResourceValidationResult.error(Res.string.ruleRequired)
        else ResourceValidationResult.success()
    }
}

@Stable
data object Numeric : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        if (value.isBlank()) return ResourceValidationResult.success()
        return if (value.toLongOrNull() == null) {
            ResourceValidationResult.error(Res.string.ruleNumeric)
        } else {
            ResourceValidationResult.success()
        }
    }
}


@Stable
data object Decimal : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        if (value.isBlank()) return ResourceValidationResult.success()
        return if (value.toFloatOrNull() == null) {
            ResourceValidationResult.error(Res.string.ruleDecimal)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class MinValue(val minValue: State<Number>) : ValueValidatorRule<String> {
    constructor(minValue: Number) : this(ImmutableNumberState(minValue))

    private val _minValue by minValue
    override fun validate(value: String): ValidationResult {
        if (value.isBlank()) return ResourceValidationResult.success()
        val number = value.toDoubleOrNull()
        return if (number == null || number < _minValue.toDouble()) {
            ResourceValidationResult.error(Res.string.ruleMinValue, _minValue)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class MaxValue(val maxValue: State<Number>) : ValueValidatorRule<String> {
    constructor(maxValue: Number) : this(ImmutableNumberState(maxValue))

    private val _maxValue by maxValue
    override fun validate(value: String): ValidationResult {
        if (value.isBlank()) return ResourceValidationResult.success()
        val number = value.toDoubleOrNull()
        return if (value.isNotBlank() && (number == null || number > _maxValue.toDouble())) {
            ResourceValidationResult.error(Res.string.ruleMaxValue, _maxValue)
        } else {
            ResourceValidationResult.success()
        }
    }
}

/**
 * Validates that the string is at least [minLength] long. Blank passes ([Required] owns
 * emptiness) — compose with [Required] to reject blank, or use `Required.onlyWhen(state)`
 * alongside `MinLength(n)` for an optional field that must be at least `n` chars *if* the user
 * types something.
 *
 * @param trim if true will trim the start and end before checking the length
 * @param includeWhiteSpace if false will not count white space chars in this string.
 *  As defined by [Char.isWhitespace]
 */
@Stable
data class MinLength(
    val minLength: State<Int>,
    val trim: State<Boolean> = ImmutableBooleanState(value = true),
    val includeWhiteSpace: State<Boolean> = ImmutableBooleanState(value = true),
) : ValueValidatorRule<String> {
    constructor(minLength: Int, trim: Boolean = true, includeWhiteSpace: Boolean = true) : this(
        ImmutableIntState(minLength),
        ImmutableBooleanState(trim),
        ImmutableBooleanState(includeWhiteSpace)
    )

    private val _minLength by minLength
    private val _trim by trim
    private val _includeWhiteSpace by includeWhiteSpace
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        if (value.isBlank()) return ResourceValidationResult.success()
        val trimmed = value.let { if (_trim) it.trim() else it }
        return when {
            _includeWhiteSpace && trimmed.length < _minLength -> {
                ResourceValidationResult.error(Res.string.ruleMinLength, _minLength)
            }

            !_includeWhiteSpace && trimmed.count { !it.isWhitespace() } < _minLength -> {
                ResourceValidationResult.error(Res.string.ruleMinLengthNoWhitespace, _minLength)
            }

            else -> ResourceValidationResult.success()
        }
    }
}

/**
 * Max length of a string
 *
 * @param maxLength the max length of the string
 */
@Stable
data class MaxLength(
    val maxLength: State<Int>
) : ValueValidatorRule<String> {
    constructor(maxLength: Int) : this(ImmutableIntState(maxLength))

    private val _maxLength by maxLength
    override fun validate(value: String): ValidationResult {
        return if (value.length > _maxLength) {
            ResourceValidationResult.error(Res.string.ruleMaxLength, _maxLength)
        } else {
            ResourceValidationResult.success()
        }
    }
}

// Email
@Stable
data object Email : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        if (value.isBlank()) return ResourceValidationResult.success()
        return if (!value.isEmail()) {
            ResourceValidationResult.error(Res.string.ruleEmail)
        } else {
            ResourceValidationResult.success()
        }
    }
}

// HexColor
/**
 * Validates a CSS-style hex color: a leading `#` followed by 3, 4, 6 or 8 hex digits
 * (`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`). Case-insensitive. Blank passes ([Required] owns
 * emptiness).
 */
@Stable
data object HexColor : ValueValidatorRule<String> {
    private val regex = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        if (value.isBlank()) return ResourceValidationResult.success()
        return if (regex.matches(value)) {
            ResourceValidationResult.success()
        } else {
            ResourceValidationResult.error(Res.string.ruleHexColor)
        }
    }
}

// OneOf
/**
 * Validates that the value is one of [allowed]. By default the comparison ignores case ([ignoreCase])
 * and [trim]s the value and each allowed entry — covering enum-ish string fields, ISO country codes,
 * etc. Blank passes ([Required] owns emptiness).
 */
@Stable
data class OneOf(
    val allowed: State<Set<String>>,
    val ignoreCase: Boolean = true,
    val trim: Boolean = true,
) : ValueValidatorRule<String> {
    constructor(allowed: Set<String>, ignoreCase: Boolean = true, trim: Boolean = true) :
        this(ImmutableValueState(allowed), ignoreCase, trim)

    private val _allowed by allowed
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        if (value.isBlank()) return ResourceValidationResult.success()
        val candidate = if (trim) value.trim() else value
        val match = _allowed.any { entry ->
            val allowedEntry = if (trim) entry.trim() else entry
            allowedEntry.equals(candidate, ignoreCase = ignoreCase)
        }
        return if (match) {
            ResourceValidationResult.success()
        } else {
            ResourceValidationResult.error(Res.string.ruleInList)
        }
    }
}

// Phone
/**
 * @param defaultRegion the default region to use for phone number validation, ISO 3166-1 alpha-2 code US, GB, ES, etc
 */
@Stable
data class Phone(
    val defaultRegion: State<String> = ImmutableStringState(value = "US")
) : ValueValidatorRule<String> {
    constructor(defaultRegion: String) : this(ImmutableStringState(defaultRegion))

    private val _defaultRegion by defaultRegion
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        if (value.isBlank()) return ResourceValidationResult.success()
        return if (!value.isPhoneNumber(_defaultRegion)) {
            ResourceValidationResult.error(Res.string.rulePhone)
        } else {
            ResourceValidationResult.success()
        }
    }
}

// PhoneFormat
/**
 * Lenient, **dependency-free** phone-number *format* rule (no libphonenumber): delegates to
 * [isPhoneNumberFormat] (optional leading `+`, ASCII digits and `space ( ) . / -`, 7..15 digits).
 *
 * Use when the server is authoritative (normalizes to E.164 / verifies with a provider) and the
 * client only needs a cheap "looks like a phone number" gate. For region-aware validity, use the
 * opt-in [Phone] rule (which requires the libphonenumber dependency).
 */
@Stable
data object PhoneFormat : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        if (value.isBlank()) return ResourceValidationResult.success()
        return if (!value.isPhoneNumberFormat()) {
            ResourceValidationResult.error(Res.string.rulePhone)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class DayValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    constructor(localDate: LocalDate) : this(ImmutableLocalDateState(localDate))

    override fun validate(value: String): ValidationResult {
        val current = localDate.value
        val dayOfMonth = value.toIntOrNull()
            ?: return ResourceValidationResult.error(Res.string.ruleDay)
        runCatching {
            LocalDate(current.year, current.month, dayOfMonth)
        }.onFailure {
            return ResourceValidationResult.error(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return ResourceValidationResult.success()
    }
}

@Stable
data class MonthValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    constructor(localDate: LocalDate) : this(ImmutableLocalDateState(localDate))

    override fun validate(value: String): ValidationResult {
        val current = localDate.value
        val monthOfYear = value.toIntOrNull()
            ?: return ResourceValidationResult.error(Res.string.ruleMonth)
        runCatching {
            LocalDate(current.year, monthOfYear, current.day)
        }.onFailure {
            return ResourceValidationResult.error(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return ResourceValidationResult.success()
    }
}

@Stable
data class YearValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    constructor(localDate: LocalDate) : this(ImmutableLocalDateState(localDate))

    override fun validate(value: String): ValidationResult {
        val current = localDate.value
        val year = value.toIntOrNull() ?: return ResourceValidationResult.error(Res.string.ruleYear)
        runCatching {
            LocalDate(year, current.month, current.day)
        }.onFailure {
            return ResourceValidationResult.error(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return ResourceValidationResult.success()
    }
}


@Stable
internal data class PasswordMatchesString(
    val otherField: ValueValidator<String, *>
) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        return if (value != otherField.value) {
            ResourceValidationResult.error(Res.string.rulePasswords)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
internal data class PasswordMatchesTextFieldValue(
    val otherField: ValueValidator<TextFieldValue, *>
) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        return if (value != otherField.value.text) {
            ResourceValidationResult.error(Res.string.rulePasswords)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@JvmName("PasswordMatchesStrings")
fun PasswordMatches(stringField: ValueValidator<String, *>): ValueValidatorRule<String> {
    return PasswordMatchesString(stringField)
}

@JvmName("PasswordMatchesTextFieldValue")
fun PasswordMatches(textFieldValueField: ValueValidator<TextFieldValue, *>): ValueValidatorRule<String> {
    return PasswordMatchesTextFieldValue(textFieldValueField)
}


