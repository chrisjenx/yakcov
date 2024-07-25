package com.chrisjenx.yakcov

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

class ShakingState(
    private val strength: Strength,
    private val direction: Direction
) {

    val xPosition = Animatable(0f)

    suspend fun shake(animationDuration: Int = 50) {
        val shakeAnimationSpec: AnimationSpec<Float> = tween(animationDuration)
        when (direction) {
            Direction.LEFT -> shakeToLeft(shakeAnimationSpec)
            Direction.RIGHT -> shakeToRight(shakeAnimationSpec)
            Direction.LEFT_THEN_RIGHT -> shakeToLeftThenRight(shakeAnimationSpec)
            Direction.RIGHT_THEN_LEFT -> shakeToRightThenLeft(shakeAnimationSpec)
        }
    }

    private suspend fun shakeToLeft(shakeAnimationSpec: AnimationSpec<Float>) {
        repeat(3) {
            xPosition.animateTo(-strength.value, shakeAnimationSpec)
            xPosition.animateTo(0f, shakeAnimationSpec)
        }
    }

    private suspend fun shakeToRight(shakeAnimationSpec: AnimationSpec<Float>) {
        repeat(3) {
            xPosition.animateTo(strength.value, shakeAnimationSpec)
            xPosition.animateTo(0f, shakeAnimationSpec)
        }
    }

    private suspend fun shakeToRightThenLeft(shakeAnimationSpec: AnimationSpec<Float>) {
        repeat(3) {
            xPosition.animateTo(strength.value, shakeAnimationSpec)
            xPosition.animateTo(0f, shakeAnimationSpec)
            xPosition.animateTo(-strength.value / 2, shakeAnimationSpec)
            xPosition.animateTo(0f, shakeAnimationSpec)
        }
    }

    private suspend fun shakeToLeftThenRight(shakeAnimationSpec: AnimationSpec<Float>) {
        repeat(3) {
            xPosition.animateTo(-strength.value, shakeAnimationSpec)
            xPosition.animateTo(0f, shakeAnimationSpec)
            xPosition.animateTo(strength.value / 2, shakeAnimationSpec)
            xPosition.animateTo(0f, shakeAnimationSpec)
        }
    }

    sealed class Strength(val value: Float) {
        data object Normal : Strength(17f)
        data object Strong : Strength(40f)
        data class Custom(val strength: Float) : Strength(strength)
    }

    enum class Direction {
        LEFT, RIGHT, LEFT_THEN_RIGHT, RIGHT_THEN_LEFT
    }
}

@Composable
fun rememberShakingState(
    strength: ShakingState.Strength = ShakingState.Strength.Normal,
    direction: ShakingState.Direction = ShakingState.Direction.LEFT
): ShakingState {
    return remember { ShakingState(strength = strength, direction = direction) }
}

fun Modifier.shakable(state: ShakingState): Modifier {
    return graphicsLayer {
        translationX = state.xPosition.value
    }
}