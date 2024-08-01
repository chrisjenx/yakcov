package com.chrisjenx.yakcov

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
@Suppress("DataClassPrivateConstructor")
data class StringResourceValidation private constructor(
    private val stringResource: StringResource,
    private val varargs: List<Any> = emptyList(),
) : StringValidation {

    constructor(stringResource: StringResource, vararg varargs: Any) : this(
        stringResource = stringResource,
        varargs = varargs.toList()
    )

    @Composable
    override fun format(): String = stringResource(stringResource, *varargs.toTypedArray())
}

@Immutable
data class RegularStringValidation(
    private val string: String,
) : StringValidation {
    @Composable
    override fun format(): String = string
}

interface StringValidation {
    @Composable
    fun format(): String
}
