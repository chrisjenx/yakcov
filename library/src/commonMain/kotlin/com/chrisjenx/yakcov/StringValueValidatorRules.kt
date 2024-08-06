@file:Suppress("FunctionName")

package com.chrisjenx.yakcov

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.text.input.TextFieldValue
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
    override fun validate(value: String): StringValidation? {
        return if (value.isBlank()) StringResourceValidation(Res.string.ruleRequired) else null
    }
}

@Stable
data object Numeric : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        return if (value.toLongOrNull() == null) {
            StringResourceValidation(Res.string.ruleNumeric)
        } else {
            null
        }
    }
}


@Stable
data object Decimal : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        return if (value.toFloatOrNull() == null) {
            StringResourceValidation(Res.string.ruleDecimal)
        } else {
            null
        }
    }
}

@Stable
data class MinValue(val minValue: Number) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        val number = value.toDoubleOrNull()
        return if (number == null || number < minValue.toDouble()) {
            StringResourceValidation(Res.string.ruleMinValue, minValue)
        } else null
    }
}

@Stable
data class MaxValue(val maxValue: Float) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        val number = value.toFloatOrNull()
        return if (value.isNotBlank() && (number == null || number > maxValue)) {
            StringResourceValidation(Res.string.ruleMaxValue, maxValue)
        } else null
    }
}

@Stable
data class MinLength(val minLength: Int) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        return if (value.length < minLength) {
            StringResourceValidation(Res.string.ruleMinLength, minLength)
        } else null
    }
}

@Stable
data class MaxLength(val maxLength: Int) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        return if (value.length > maxLength) {
            StringResourceValidation(Res.string.ruleMaxLength, maxLength)
        } else null
    }
}

// Email
@Stable
data object Email : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        // only validate if not empty as Required will check if not empty
        return if (!value.isEmail()) {
            StringResourceValidation(Res.string.ruleEmail)
        } else null
    }
}

// Phone
@Stable
data object Phone : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        // only validate if not empty as Required will check if not empty
        return if (!value.isPhoneNumber()) {
            StringResourceValidation(Res.string.rulePhone)
        } else null
    }
}

@Stable
data class DayValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        val current = localDate.value
        val dayOfMonth = value.toIntOrNull()
            ?: return StringResourceValidation(Res.string.ruleDay)
        runCatching {
            LocalDate(
                current.year,
                current.monthNumber,
                dayOfMonth
            )
        }.onFailure {
            return StringResourceValidation(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return null
    }
}

@Stable
data class MonthValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        val current = localDate.value
        val monthOfYear = value.toIntOrNull()
            ?: return StringResourceValidation(Res.string.ruleMonth)
        runCatching {
            LocalDate(
                current.year,
                monthOfYear,
                current.dayOfMonth
            )
        }.onFailure {
            return StringResourceValidation(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return null
    }
}

@Stable
data class YearValidation(val localDate: State<LocalDate>) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        val current = localDate.value
        val year = value.toIntOrNull() ?: return StringResourceValidation(Res.string.ruleYear)
        runCatching {
            LocalDate(
                year,
                current.monthNumber,
                current.dayOfMonth
            )
        }.onFailure {
            return StringResourceValidation(Res.string.ruleInvalidDate, it.message ?: "")
        }
        return null
    }
}


@Stable
internal data class PasswordMatchesString(
    val otherField: ValueValidator<String, *>
) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        return if (value != otherField.value) StringResourceValidation(Res.string.rulePasswords)
        else null
    }
}

@Stable
internal data class PasswordMatchesTextFieldValue(
    val otherField: ValueValidator<TextFieldValue, *>
) : ValueValidatorRule<String> {
    override fun validate(value: String): StringValidation? {
        return if (value != otherField.value.text) StringResourceValidation(Res.string.rulePasswords)
        else null
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
