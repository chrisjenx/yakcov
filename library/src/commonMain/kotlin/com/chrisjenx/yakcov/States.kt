package com.chrisjenx.yakcov

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import kotlinx.datetime.LocalDate

@Immutable
internal class ImmutableBooleanState(override val value: Boolean) : State<Boolean>

@Immutable
internal class ImmutableIntState(override val value: Int) : State<Int>

@Immutable
internal class ImmutableNumberState(override val value: Number) : State<Number>

@Immutable
internal class ImmutableStringState(override val value: String) : State<String>

@Immutable
internal class ImmutableLocalDateState(override val value: LocalDate) : State<LocalDate>

@Immutable
internal class ImmutableListState<T>(override val value: List<T>) : State<List<T>>
