package dev.chungjungsoo.gptmobile.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

internal fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

internal fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

internal fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
