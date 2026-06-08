package com.chrisjenx.yakcov.strings

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for [DayValidation], [MonthValidation] and [YearValidation].
 *
 * Each rule rebuilds a [LocalDate] from the reference date plus the field under
 * validation. These tests were added when migrating off the deprecated
 * kotlinx-datetime `LocalDate.monthNumber`/`dayOfMonth` accessors (now `month`/`day`)
 * to confirm the date-construction behaviour is unchanged, including leap-year and
 * day-overflow edge cases that exercise the `month`/`day` accessors directly.
 */
class DateRuleTest {

    // DayValidation: validates day-of-month against the reference's year + month.

    @Test
    fun day_valid_in_leap_february_success() {
        // Feb 2024 is a leap month, so day 29 is valid.
        assertEquals(Outcome.SUCCESS, DayValidation(LocalDate(2024, 2, 1)).validate("29").outcome())
    }

    @Test
    fun day_overflow_for_month_error() {
        // February never has a 30th.
        assertEquals(Outcome.ERROR, DayValidation(LocalDate(2024, 2, 1)).validate("30").outcome())
    }

    @Test
    fun day_non_numeric_error() {
        assertEquals(Outcome.ERROR, DayValidation(LocalDate(2024, 2, 1)).validate("abc").outcome())
    }

    // MonthValidation: validates month against the reference's year + day.

    @Test
    fun month_valid_for_reference_day_success() {
        // Reference day 31; March has 31 days.
        assertEquals(Outcome.SUCCESS, MonthValidation(LocalDate(2024, 1, 31)).validate("3").outcome())
    }

    @Test
    fun month_with_invalid_day_for_month_error() {
        // Reference day 31; February has no 31st.
        assertEquals(Outcome.ERROR, MonthValidation(LocalDate(2024, 1, 31)).validate("2").outcome())
    }

    @Test
    fun month_out_of_range_error() {
        assertEquals(Outcome.ERROR, MonthValidation(LocalDate(2024, 1, 15)).validate("13").outcome())
    }

    // YearValidation: validates year against the reference's month + day.

    @Test
    fun year_leap_keeps_feb29_success() {
        // 2024 is a leap year, so Feb 29 2024 is valid.
        assertEquals(Outcome.SUCCESS, YearValidation(LocalDate(2024, 2, 29)).validate("2024").outcome())
    }

    @Test
    fun year_non_leap_rejects_feb29_error() {
        // 2023 is not a leap year, so Feb 29 2023 is invalid.
        assertEquals(Outcome.ERROR, YearValidation(LocalDate(2024, 2, 29)).validate("2023").outcome())
    }

    @Test
    fun year_non_numeric_error() {
        assertEquals(Outcome.ERROR, YearValidation(LocalDate(2024, 2, 29)).validate("abc").outcome())
    }
}
