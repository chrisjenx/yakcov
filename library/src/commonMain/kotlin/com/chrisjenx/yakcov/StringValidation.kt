package com.chrisjenx.yakcov

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
@Suppress("DataClassPrivateConstructor")
data class StringValidation private constructor(
    private val stringResource: StringResource,
    private val varargs: List<Any> = emptyList(),
) {

    constructor(stringResource: StringResource, vararg varargs: Any) : this(
        stringResource = stringResource,
        varargs = varargs.toList()
    )

    @Composable
    fun format(): String = stringResource(stringResource, *varargs.toTypedArray())
}