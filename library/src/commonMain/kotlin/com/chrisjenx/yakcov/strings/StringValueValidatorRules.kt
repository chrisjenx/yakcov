@file:Suppress("FunctionName")

package com.chrisjenx.yakcov.strings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.ResourceValidationResult
import com.chrisjenx.yakcov.ValidationResult
import com.chrisjenx.yakcov.ValueValidator
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.isEmail
import com.chrisjenx.yakcov.isPhoneNumber
import kotlinx.datetime.LocalDate
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleDay
import yakcov.library.generated.resources.ruleDecimal
import yakcov.library.generated.resources.ruleEmail
import yakcov.library.generated.resources.ruleInvalidDate
import yakcov.library.generated.resources.ruleMaxLength
import yakcov.library.generated.resources.ruleMaxValue
import yakcov.library.generated.resources.ruleMinLength
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
        return if (value.toFloatOrNull() == null) {
            ResourceValidationResult.error(Res.string.ruleDecimal)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class MinValue(val minValue: Number) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        val number = value.toDoubleOrNull()
        return if (number == null || number < minValue.toDouble()) {
            ResourceValidationResult.error(Res.string.ruleMinValue, minValue)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class MaxValue(val maxValue: Float) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        val number = value.toFloatOrNull()
        return if (value.isNotBlank() && (number == null || number > maxValue)) {
            ResourceValidationResult.error(Res.string.ruleMaxValue, maxValue)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class MinLength(val minLength: Int) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        return if (value.length < minLength) {
            ResourceValidationResult.error(Res.string.ruleMinLength, minLength)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class MaxLength(val maxLength: Int) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        return if (value.length > maxLength) {
            ResourceValidationResult.error(Res.string.ruleMaxLength, maxLength)
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
        return if (!value.isEmail()) {
            ResourceValidationResult.error(Res.string.ruleEmail)
        } else {
            ResourceValidationResult.success()
        }
    }
}

// Phone
/**
 * @param defaultRegion the default region to use for phone number validation, ISO 3166-1 alpha-2 code US, GB, ES, etc
 */
@Stable
data class Phone(val defaultRegion: String = "US") : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        // only validate if not empty as Required will check if not empty
        return if (!value.isPhoneNumber(defaultRegion)) {
            ResourceValidationResult.error(Res.string.rulePhone)
        } else {
            ResourceValidationResult.success()
        }
    }
}

@Stable
data class DayValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        val current = localDate.value
        val dayOfMonth = value.toIntOrNull()
            ?: return ResourceValidationResult.error(Res.string.ruleDay)
        runCatching {
            LocalDate(current.year, current.monthNumber, dayOfMonth)
        }.onFailure {
            return ResourceValidationResult.error(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return ResourceValidationResult.success()
    }
}

@Stable
data class MonthValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        val current = localDate.value
        val monthOfYear = value.toIntOrNull()
            ?: return ResourceValidationResult.error(Res.string.ruleMonth)
        runCatching {
            LocalDate(current.year, monthOfYear, current.dayOfMonth)
        }.onFailure {
            return ResourceValidationResult.error(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return ResourceValidationResult.success()
    }
}

@Stable
data class YearValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    override fun validate(value: String): ValidationResult {
        val current = localDate.value
        val year = value.toIntOrNull() ?: return ResourceValidationResult.error(Res.string.ruleYear)
        runCatching {
            LocalDate(year, current.monthNumber, current.dayOfMonth)
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
