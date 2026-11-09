package com.chrisjenx.yakcov

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

/**
 * The text of this [TextFieldState] as a cold [Flow] of plain `String`s: the current text first, then
 * each distinct change. Selection, composition and caret moves do not emit.
 *
 * This is the hand-off for a reducer (UDF) form whose field owns a [TextFieldState]: collect it and
 * dispatch the text, and let the reducer fold the rules exactly as it does today —
 *
 * ```
 * // Component — owns the editing buffer as a `val`, outside the immutable Model.
 * val amount = TextFieldState()
 * init { scope.launch { amount.textChanges().collect { store.dispatch(AmountChanged(it)) } } }
 *
 * // Reducer — pure, unchanged in shape.
 * is AmountChanged -> model.copy(
 *     amount = event.text,
 *     amountState = amountRules.toFieldState(event.text, model.amountState.showError),
 * )
 * ```
 *
 * Prefer this over a raw `snapshotFlow { state.text }`: `TextFieldState.text` is a
 * `TextFieldCharSequence` whose equality includes the selection, so that flow also emits on every
 * caret move and hands the reducer a mutable-backed `CharSequence` rather than an immutable `String`.
 *
 * Emissions are conflated to snapshot applies, so a burst of edits may arrive as its final text only.
 */
fun TextFieldState.textChanges(): Flow<String> = snapshotFlow { text.toString() }
