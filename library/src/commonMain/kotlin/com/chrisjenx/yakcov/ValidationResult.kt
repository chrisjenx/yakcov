package com.chrisjenx.yakcov

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.chrisjenx.yakcov.ValidationResult.Outcome
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
@Suppress("DataClassPrivateConstructor")
data class ResourceValidationResult private constructor(
    private val stringResource: StringResource?,
    private val varargs: List<Any> = emptyList(),
    private val outcome: Outcome,
) : ValidationResult {

    private constructor(
        stringResource: StringResource?,
        outcome: Outcome,
        vararg varargs: Any
    ) : this(
        stringResource = stringResource,
        outcome = outcome,
        varargs = varargs.toList()
    )

    @Composable
    override fun format(): String? = stringResource
        ?.let { stringResource(it, *varargs.toTypedArray()) }

    override fun outcome(): Outcome = outcome

    companion object {
        fun error(stringResource: StringResource, vararg varargs: Any) =
            ResourceValidationResult(stringResource, outcome = Outcome.ERROR, varargs = varargs)

        fun warning(stringResource: StringResource, vararg varargs: Any) =
            ResourceValidationResult(stringResource, outcome = Outcome.WARNING, varargs = varargs)

        fun info(stringResource: StringResource, vararg varargs: Any) =
            ResourceValidationResult(stringResource, outcome = Outcome.INFO, varargs = varargs)

        fun success(stringResource: StringResource? = null, vararg varargs: Any) =
            ResourceValidationResult(stringResource, outcome = Outcome.SUCCESS, varargs = varargs)
    }

}

@Immutable
@Suppress("DataClassPrivateConstructor")
data class RegularValidationResult private constructor(
    private val string: String?,
    private val outcome: Outcome,
) : ValidationResult {
    @Composable
    override fun format(): String? = string

    override fun outcome(): Outcome = outcome

    companion object {
        fun error(string: String) = RegularValidationResult(string, outcome = Outcome.ERROR)
        fun warning(string: String) = RegularValidationResult(string, outcome = Outcome.WARNING)
        fun info(string: String) = RegularValidationResult(string, outcome = Outcome.INFO)
        fun success(string: String? = null) = RegularValidationResult(
            string, outcome = Outcome.SUCCESS
        )
    }
}

interface ValidationResult {
    @Composable
    fun format(): String?

    fun outcome(): Outcome = Outcome.ERROR

    enum class Outcome(val severity: Short) {
        ERROR(severity = 40),
        WARNING(severity = 30),
        INFO(severity = 20),
        SUCCESS(severity = 10)
    }

}
