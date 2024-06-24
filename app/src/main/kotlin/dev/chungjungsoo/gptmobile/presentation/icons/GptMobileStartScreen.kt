package dev.chungjungsoo.gptmobile.presentation.icons

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

val GptMobileStartScreen: ImageVector
    @Composable
    get() {
        // It should be recomposed when theme is changed. So calculate every time (Expensive, but only used in get started screen)
        return Builder(
            name = "GptMobileStartScreen",
            defaultWidth = 488.61.dp,
            defaultHeight = 317.24.dp,
            viewportWidth = 488.61f,
            viewportHeight =
            317.24f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 4.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(309.28f, 44.69f)
                lineTo(475.1f, 100.72f)
                curveToRelative(12.78f, 4.32f, 15.31f, 13.44f, 5.65f, 20.37f)
                lineToRelative(-260.8f, 187.19f)
                curveToRelative(-9.66f, 6.93f, -27.84f, 9.05f, -40.62f, 4.73f)
                lineTo(13.52f, 256.98f)
                curveToRelative(-12.78f, -4.32f, -15.31f, -13.44f, -5.65f, -20.37f)
                lineToRelative(260.8f, -187.19f)
                curveTo(278.32f, 42.5f, 296.51f, 40.38f, 309.28f, 44.69f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 4.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(305.86f, 51.23f)
                lineTo(463.95f, 104.65f)
                curveToRelative(9.58f, 3.24f, 11.48f, 10.08f, 4.24f, 15.27f)
                lineTo(213.22f, 302.93f)
                curveToRelative(-7.24f, 5.2f, -20.88f, 6.79f, -30.47f, 3.55f)
                lineTo(24.66f, 253.05f)
                curveToRelative(-9.58f, -3.24f, -11.48f, -10.08f, -4.24f, -15.27f)
                lineTo(275.39f, 54.77f)
                curveTo(282.63f, 49.58f, 296.27f, 47.99f, 305.86f, 51.23f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 1.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(175.71f, 290.1f)
                lineToRelative(12.85f, -9.22f)
                arcToRelative(1.67f, 1.67f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.99f, dy1 = -0.27f)
                arcToRelative(1.47f, 1.47f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.94f, dy1 = 0.26f)
                lineToRelative(13.76f, 11.41f)
                curveToRelative(0.31f, 0.28f, 0.1f, 0.66f, -0.48f, 0.84f)
                arcToRelative(2.27f, 2.27f, 0.0f, false, isPositiveArc = true, dx1 = -0.83f, dy1 = 0.1f)
                lineToRelative(-26.56f, -2.22f)
                arcToRelative(1.13f, 1.13f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.82f, dy1 = -0.34f)
                curveTo(175.41f, 290.48f, 175.47f, 290.27f, 175.71f, 290.1f)
                close()
                moveTo(178.69f, 289.92f)
                lineTo(200.81f, 291.8f)
                lineTo(189.37f, 282.26f)
                lineTo(185.44f, 285.08f)
                lineTo(191.48f, 288.65f)
                lineTo(182.59f, 287.12f)
                close()
                moveTo(183.99f, 286.12f)
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 4.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(55.82f, 229.71f)
                lineToRelative(159.95f, 54.05f)
                curveToRelative(3.73f, 1.26f, 4.47f, 3.92f, 1.65f, 5.94f)
                lineTo(202.8f, 300.19f)
                curveToRelative(-2.82f, 2.02f, -8.12f, 2.64f, -11.85f, 1.38f)
                lineTo(31.0f, 247.52f)
                curveToRelative(-3.73f, -1.26f, -4.47f, -3.92f, -1.65f, -5.94f)
                lineToRelative(14.62f, -10.49f)
                curveTo(46.79f, 229.07f, 52.09f, 228.45f, 55.82f, 229.71f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 4.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(188.11f, 233.29f)
                lineTo(246.05f, 252.87f)
                curveToRelative(8.52f, 2.88f, 10.21f, 8.96f, 3.77f, 13.58f)
                lineToRelative(-0.11f, 0.08f)
                curveToRelative(-6.44f, 4.62f, -18.56f, 6.03f, -27.08f, 3.15f)
                lineToRelative(-57.94f, -19.58f)
                curveToRelative(-8.52f, -2.88f, -10.21f, -8.96f, -3.77f, -13.58f)
                lineToRelative(0.11f, -0.08f)
                curveTo(167.46f, 231.82f, 179.59f, 230.41f, 188.11f, 233.29f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(175.99f, 238.39f)
                curveToRelative(0.21f, 0.08f, -0.05f, -0.06f, -0.09f, -0.06f)
                curveToRelative(0.07f, 0.0f, 0.04f, -0.01f, 0.08f, 0.16f)
                curveToRelative(0.02f, 0.11f, 0.04f, 0.01f, -0.01f, 0.24f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.19f, dy1 = 0.61f)
                arcToRelative(14.94f, 14.94f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.36f, dy1 = 2.18f)
                curveToRelative(-0.97f, 1.41f, -2.56f, 3.28f, -1.41f, 4.69f)
                curveToRelative(1.26f, 1.54f, 4.75f, 1.41f, 7.38f, 0.62f)
                curveToRelative(2.88f, -0.86f, 5.37f, -2.17f, 8.07f, -3.07f)
                arcToRelative(14.59f, 14.59f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.59f, dy1 = -0.44f)
                curveToRelative(-0.03f, 0.01f, 0.08f, 0.03f, 0.42f, -0.05f)
                curveToRelative(-0.05f, 0.01f, -0.06f, -0.04f, -0.03f, 0.01f)
                curveToRelative(-0.04f, -0.05f, -0.35f, -0.11f, -0.08f, -0.02f)
                curveToRelative(0.37f, 0.12f, -0.03f, -0.03f, -0.06f, -0.03f)
                curveToRelative(0.15f, 0.02f, -0.06f, 0.06f, -0.02f, -0.06f)
                curveToRelative(-0.11f, 0.28f, 0.1f, 0.35f, -0.04f, 0.78f)
                curveToRelative(-0.48f, 1.47f, -2.06f, 3.09f, -2.81f, 4.64f)
                curveToRelative(-0.71f, 1.46f, -0.6f, 3.25f, 2.67f, 3.65f)
                curveToRelative(2.99f, 0.36f, 6.15f, -1.0f, 8.44f, -1.93f)
                curveToRelative(1.35f, -0.55f, 2.67f, -1.13f, 4.09f, -1.62f)
                curveToRelative(0.48f, -0.16f, 0.97f, -0.31f, 1.49f, -0.44f)
                arcToRelative(1.93f, 1.93f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.45f, dy1 = -0.09f)
                curveToRelative(0.46f, -0.06f, 0.23f, -0.01f, 0.21f, -0.03f)
                curveToRelative(-0.07f, -0.12f, 0.05f, 0.12f, -0.07f, -0.02f)
                curveToRelative(-0.14f, -0.16f, 0.09f, 0.07f, 0.1f, 0.04f)
                arcToRelative(0.62f, 0.62f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.13f, dy1 = -0.05f)
                curveToRelative(-0.1f, 0.06f, 0.02f, -0.14f, -0.0f, -0.02f)
                curveToRelative(-0.01f, 0.06f, 0.05f, 0.13f, 0.05f, 0.19f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.04f, dy1 = 0.3f)
                arcToRelative(6.86f, 6.86f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.16f, dy1 = 2.15f)
                curveToRelative(-0.98f, 1.45f, -2.53f, 3.16f, -2.05f, 4.67f)
                curveToRelative(0.55f, 1.74f, 3.96f, 2.09f, 6.92f, 1.37f)
                curveToRelative(3.0f, -0.73f, 5.47f, -2.04f, 8.16f, -3.03f)
                arcToRelative(14.49f, 14.49f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.53f, dy1 = -0.5f)
                curveToRelative(0.27f, -0.08f, 0.55f, -0.14f, 0.84f, -0.2f)
                curveToRelative(-0.52f, 0.09f, 0.67f, -0.07f, 0.3f, -0.03f)
                arcToRelative(0.13f, 0.13f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.12f, dy1 = -0.02f)
                curveToRelative(0.05f, 0.06f, 0.09f, -0.02f, 0.02f, 0.02f)
                curveToRelative(-0.04f, 0.02f, -0.04f, -0.08f, -0.05f, -0.05f)
                curveToRelative(-0.01f, 0.06f, 0.05f, 0.13f, 0.05f, 0.19f)
                curveToRelative(0.0f, 0.12f, 0.02f, -0.14f, 0.0f, 0.07f)
                curveToRelative(-0.12f, 1.45f, -1.74f, 3.0f, -2.59f, 4.49f)
                curveToRelative(-0.81f, 1.42f, -1.48f, 3.28f, 1.34f, 4.08f)
                curveToRelative(2.84f, 0.8f, 6.24f, -0.47f, 8.61f, -1.4f)
                curveToRelative(1.34f, -0.53f, 2.64f, -1.09f, 3.99f, -1.61f)
                quadToRelative(0.75f, -0.3f, 1.56f, -0.55f)
                curveToRelative(0.28f, -0.08f, 0.57f, -0.16f, 0.86f, -0.23f)
                arcToRelative(5.19f, 5.19f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.64f, dy1 = -0.11f)
                curveToRelative(0.17f, -0.03f, 0.12f, -0.02f, -0.09f, -0.02f)
                curveToRelative(0.25f, 0.0f, -0.18f, -0.12f, 0.22f, 0.06f)
                curveToRelative(-0.23f, -0.1f, -0.15f, -0.08f, -0.16f, -0.04f)
                curveToRelative(0.09f, -0.27f, 0.04f, 0.22f, 0.03f, 0.14f)
                arcToRelative(3.86f, 3.86f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.82f, dy1 = 1.86f)
                curveToRelative(-0.99f, 1.55f, -2.55f, 3.19f, -2.49f, 4.81f)
                curveToRelative(-0.03f, 0.76f, 0.62f, 1.45f, 1.77f, 1.88f)
                curveToRelative(2.25f, 0.84f, 4.98f, -1.12f, 2.73f, -1.96f)
                curveToRelative(-0.21f, -0.08f, 0.05f, 0.06f, 0.09f, 0.06f)
                curveToRelative(-0.07f, -0.0f, -0.04f, 0.01f, -0.08f, -0.16f)
                curveToRelative(-0.02f, -0.11f, -0.04f, -0.01f, 0.01f, -0.24f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.18f, dy1 = -0.61f)
                arcToRelative(14.89f, 14.89f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.36f, dy1 = -2.18f)
                curveToRelative(0.97f, -1.41f, 2.56f, -3.28f, 1.41f, -4.69f)
                curveToRelative(-1.26f, -1.54f, -4.75f, -1.41f, -7.39f, -0.62f)
                curveToRelative(-2.88f, 0.86f, -5.37f, 2.17f, -8.08f, 3.06f)
                arcToRelative(14.57f, 14.57f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.59f, dy1 = 0.44f)
                curveToRelative(0.03f, -0.01f, -0.08f, -0.03f, -0.42f, 0.05f)
                curveToRelative(0.05f, -0.01f, 0.06f, 0.04f, 0.03f, -0.01f)
                curveToRelative(0.04f, 0.05f, 0.35f, 0.11f, 0.08f, 0.02f)
                curveToRelative(-0.37f, -0.12f, 0.03f, 0.03f, 0.06f, 0.03f)
                curveToRelative(-0.15f, -0.02f, 0.06f, -0.06f, 0.02f, 0.06f)
                curveToRelative(0.11f, -0.28f, -0.1f, -0.35f, 0.04f, -0.78f)
                curveToRelative(0.48f, -1.47f, 2.06f, -3.09f, 2.8f, -4.64f)
                curveToRelative(0.71f, -1.46f, 0.59f, -3.25f, -2.68f, -3.65f)
                curveToRelative(-2.99f, -0.36f, -6.15f, 1.0f, -8.44f, 1.93f)
                curveToRelative(-1.35f, 0.55f, -2.67f, 1.13f, -4.09f, 1.62f)
                curveToRelative(-0.48f, 0.16f, -0.97f, 0.31f, -1.49f, 0.44f)
                arcToRelative(1.92f, 1.92f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.45f, dy1 = 0.09f)
                curveToRelative(-0.46f, 0.06f, -0.23f, 0.01f, -0.21f, 0.03f)
                curveToRelative(0.06f, 0.12f, -0.05f, -0.12f, 0.07f, 0.02f)
                curveToRelative(0.14f, 0.16f, -0.09f, -0.07f, -0.1f, -0.04f)
                arcToRelative(0.62f, 0.62f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.13f, dy1 = 0.05f)
                curveToRelative(0.1f, -0.06f, -0.02f, 0.14f, 0.0f, 0.02f)
                curveToRelative(0.01f, -0.06f, -0.05f, -0.13f, -0.05f, -0.19f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.04f, dy1 = -0.3f)
                arcToRelative(6.86f, 6.86f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.16f, dy1 = -2.15f)
                curveToRelative(0.98f, -1.45f, 2.53f, -3.16f, 2.05f, -4.67f)
                curveToRelative(-0.55f, -1.74f, -3.96f, -2.09f, -6.92f, -1.37f)
                curveToRelative(-3.0f, 0.73f, -5.47f, 2.04f, -8.16f, 3.03f)
                arcToRelative(14.48f, 14.48f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.53f, dy1 = 0.5f)
                curveToRelative(-0.27f, 0.08f, -0.55f, 0.14f, -0.84f, 0.2f)
                curveToRelative(0.52f, -0.09f, -0.67f, 0.07f, -0.3f, 0.03f)
                arcToRelative(0.13f, 0.13f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.12f, dy1 = 0.02f)
                curveToRelative(-0.05f, -0.06f, -0.09f, 0.02f, -0.02f, -0.02f)
                curveToRelative(0.04f, -0.02f, 0.04f, 0.08f, 0.05f, 0.05f)
                curveToRelative(0.01f, -0.06f, -0.05f, -0.13f, -0.05f, -0.19f)
                curveToRelative(-0.0f, -0.12f, -0.02f, 0.14f, -0.0f, -0.07f)
                curveToRelative(0.12f, -1.45f, 1.74f, -3.0f, 2.59f, -4.49f)
                curveToRelative(0.81f, -1.42f, 1.48f, -3.28f, -1.34f, -4.08f)
                curveToRelative(-2.84f, -0.8f, -6.24f, 0.47f, -8.6f, 1.4f)
                curveToRelative(-1.34f, 0.53f, -2.64f, 1.09f, -3.99f, 1.61f)
                quadToRelative(-0.75f, 0.3f, -1.56f, 0.55f)
                curveToRelative(-0.28f, 0.08f, -0.57f, 0.16f, -0.86f, 0.24f)
                arcToRelative(5.33f, 5.33f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.64f, dy1 = 0.11f)
                curveToRelative(-0.17f, 0.04f, -0.12f, 0.02f, 0.09f, 0.02f)
                curveToRelative(-0.26f, -0.0f, 0.18f, 0.12f, -0.22f, -0.06f)
                curveToRelative(0.23f, 0.1f, 0.15f, 0.08f, 0.16f, 0.04f)
                curveToRelative(-0.09f, 0.27f, -0.03f, -0.22f, -0.03f, -0.14f)
                arcToRelative(3.87f, 3.87f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.82f, dy1 = -1.86f)
                curveToRelative(0.99f, -1.55f, 2.55f, -3.19f, 2.49f, -4.8f)
                curveToRelative(0.03f, -0.76f, -0.62f, -1.45f, -1.77f, -1.88f)
                curveToRelative(-2.25f, -0.84f, -4.98f, 1.12f, -2.73f, 1.96f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(377.43f, 93.81f)
                curveToRelative(0.21f, 0.08f, -0.05f, -0.06f, -0.09f, -0.06f)
                curveToRelative(0.07f, 0.0f, 0.04f, -0.01f, 0.08f, 0.16f)
                curveToRelative(0.02f, 0.11f, 0.04f, 0.01f, -0.01f, 0.24f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.19f, dy1 = 0.61f)
                arcToRelative(14.94f, 14.94f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.36f, dy1 = 2.18f)
                curveToRelative(-0.97f, 1.41f, -2.56f, 3.28f, -1.41f, 4.69f)
                curveToRelative(1.26f, 1.54f, 4.75f, 1.41f, 7.38f, 0.62f)
                curveToRelative(2.88f, -0.86f, 5.37f, -2.17f, 8.07f, -3.07f)
                arcToRelative(14.58f, 14.58f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.59f, dy1 = -0.44f)
                curveToRelative(-0.03f, 0.01f, 0.08f, 0.03f, 0.42f, -0.05f)
                curveToRelative(-0.05f, 0.01f, -0.06f, -0.04f, -0.03f, 0.01f)
                curveToRelative(-0.04f, -0.05f, -0.35f, -0.11f, -0.08f, -0.02f)
                curveToRelative(0.37f, 0.12f, -0.03f, -0.03f, -0.06f, -0.03f)
                curveToRelative(0.15f, 0.02f, -0.06f, 0.06f, -0.02f, -0.06f)
                curveToRelative(-0.11f, 0.28f, 0.1f, 0.35f, -0.04f, 0.78f)
                curveToRelative(-0.48f, 1.47f, -2.06f, 3.09f, -2.81f, 4.64f)
                curveToRelative(-0.71f, 1.46f, -0.6f, 3.25f, 2.67f, 3.65f)
                curveToRelative(2.99f, 0.36f, 6.15f, -1.0f, 8.44f, -1.93f)
                curveToRelative(1.35f, -0.55f, 2.67f, -1.13f, 4.09f, -1.62f)
                curveToRelative(0.48f, -0.16f, 0.97f, -0.31f, 1.49f, -0.44f)
                arcToRelative(1.93f, 1.93f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.45f, dy1 = -0.09f)
                curveToRelative(0.46f, -0.06f, 0.23f, -0.01f, 0.21f, -0.03f)
                curveToRelative(-0.07f, -0.12f, 0.05f, 0.12f, -0.07f, -0.02f)
                curveToRelative(-0.14f, -0.16f, 0.09f, 0.07f, 0.1f, 0.04f)
                arcToRelative(0.62f, 0.62f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.13f, dy1 = -0.05f)
                curveToRelative(-0.1f, 0.06f, 0.02f, -0.14f, -0.0f, -0.02f)
                curveToRelative(-0.01f, 0.06f, 0.05f, 0.13f, 0.05f, 0.19f)
                arcToRelative(2.97f, 2.97f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.04f, dy1 = 0.3f)
                arcToRelative(6.86f, 6.86f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.16f, dy1 = 2.15f)
                curveToRelative(-0.98f, 1.45f, -2.53f, 3.16f, -2.05f, 4.67f)
                curveToRelative(0.55f, 1.74f, 3.96f, 2.09f, 6.92f, 1.37f)
                curveToRelative(3.0f, -0.73f, 5.47f, -2.04f, 8.16f, -3.03f)
                arcToRelative(14.48f, 14.48f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.53f, dy1 = -0.5f)
                curveToRelative(0.27f, -0.08f, 0.55f, -0.14f, 0.84f, -0.2f)
                curveToRelative(-0.52f, 0.09f, 0.67f, -0.07f, 0.3f, -0.03f)
                arcToRelative(0.13f, 0.13f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.12f, dy1 = -0.02f)
                curveToRelative(0.05f, 0.06f, 0.09f, -0.02f, 0.02f, 0.02f)
                curveToRelative(-0.04f, 0.02f, -0.04f, -0.08f, -0.05f, -0.05f)
                curveToRelative(-0.01f, 0.06f, 0.05f, 0.13f, 0.05f, 0.19f)
                curveToRelative(0.0f, 0.12f, 0.02f, -0.14f, 0.0f, 0.07f)
                curveToRelative(-0.12f, 1.45f, -1.74f, 3.0f, -2.59f, 4.49f)
                curveToRelative(-0.81f, 1.42f, -1.48f, 3.28f, 1.34f, 4.08f)
                curveToRelative(2.84f, 0.8f, 6.24f, -0.47f, 8.61f, -1.4f)
                curveToRelative(1.34f, -0.53f, 2.64f, -1.09f, 3.99f, -1.61f)
                quadToRelative(0.75f, -0.3f, 1.56f, -0.55f)
                curveToRelative(0.28f, -0.08f, 0.57f, -0.16f, 0.86f, -0.23f)
                arcToRelative(5.19f, 5.19f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.64f, dy1 = -0.11f)
                curveToRelative(0.17f, -0.03f, 0.12f, -0.02f, -0.09f, -0.02f)
                curveToRelative(0.25f, 0.0f, -0.18f, -0.12f, 0.22f, 0.06f)
                curveToRelative(-0.23f, -0.1f, -0.15f, -0.08f, -0.16f, -0.04f)
                curveToRelative(0.09f, -0.27f, 0.04f, 0.22f, 0.03f, 0.14f)
                arcToRelative(3.86f, 3.86f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.82f, dy1 = 1.86f)
                curveToRelative(-0.99f, 1.55f, -2.55f, 3.19f, -2.49f, 4.81f)
                curveToRelative(-0.03f, 0.76f, 0.62f, 1.45f, 1.77f, 1.88f)
                curveToRelative(2.25f, 0.84f, 4.98f, -1.12f, 2.73f, -1.96f)
                curveToRelative(-0.21f, -0.08f, 0.05f, 0.06f, 0.09f, 0.06f)
                curveToRelative(-0.07f, -0.0f, -0.04f, 0.01f, -0.08f, -0.16f)
                curveToRelative(-0.02f, -0.11f, -0.04f, -0.01f, 0.01f, -0.24f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.18f, dy1 = -0.61f)
                arcToRelative(14.89f, 14.89f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.36f, dy1 = -2.18f)
                curveToRelative(0.97f, -1.41f, 2.56f, -3.28f, 1.41f, -4.69f)
                curveToRelative(-1.26f, -1.54f, -4.75f, -1.41f, -7.39f, -0.62f)
                curveToRelative(-2.88f, 0.86f, -5.37f, 2.17f, -8.08f, 3.06f)
                arcToRelative(14.57f, 14.57f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.59f, dy1 = 0.44f)
                curveToRelative(0.03f, -0.01f, -0.08f, -0.03f, -0.42f, 0.05f)
                curveToRelative(0.05f, -0.01f, 0.06f, 0.04f, 0.03f, -0.01f)
                curveToRelative(0.04f, 0.05f, 0.35f, 0.11f, 0.08f, 0.02f)
                curveToRelative(-0.37f, -0.12f, 0.03f, 0.03f, 0.06f, 0.03f)
                curveToRelative(-0.15f, -0.02f, 0.06f, -0.06f, 0.02f, 0.06f)
                curveToRelative(0.11f, -0.28f, -0.1f, -0.35f, 0.04f, -0.78f)
                curveToRelative(0.48f, -1.47f, 2.06f, -3.09f, 2.8f, -4.64f)
                curveToRelative(0.71f, -1.46f, 0.59f, -3.25f, -2.68f, -3.65f)
                curveToRelative(-2.99f, -0.36f, -6.15f, 1.0f, -8.44f, 1.93f)
                curveToRelative(-1.35f, 0.55f, -2.67f, 1.13f, -4.09f, 1.62f)
                curveToRelative(-0.48f, 0.16f, -0.97f, 0.31f, -1.49f, 0.44f)
                arcToRelative(1.92f, 1.92f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.45f, dy1 = 0.09f)
                curveToRelative(-0.46f, 0.06f, -0.23f, 0.01f, -0.21f, 0.03f)
                curveToRelative(0.06f, 0.12f, -0.05f, -0.12f, 0.07f, 0.02f)
                curveToRelative(0.14f, 0.16f, -0.09f, -0.07f, -0.1f, -0.04f)
                arcToRelative(0.61f, 0.61f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.13f, dy1 = 0.05f)
                curveToRelative(0.1f, -0.06f, -0.02f, 0.14f, 0.0f, 0.02f)
                curveToRelative(0.01f, -0.06f, -0.05f, -0.13f, -0.05f, -0.19f)
                arcToRelative(2.98f, 2.98f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.04f, dy1 = -0.3f)
                arcToRelative(6.86f, 6.86f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.16f, dy1 = -2.15f)
                curveToRelative(0.98f, -1.45f, 2.53f, -3.16f, 2.05f, -4.67f)
                curveToRelative(-0.55f, -1.74f, -3.96f, -2.09f, -6.92f, -1.37f)
                curveToRelative(-3.0f, 0.73f, -5.47f, 2.04f, -8.16f, 3.03f)
                arcToRelative(14.48f, 14.48f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.53f, dy1 = 0.5f)
                curveToRelative(-0.27f, 0.08f, -0.55f, 0.14f, -0.84f, 0.2f)
                curveToRelative(0.52f, -0.09f, -0.67f, 0.07f, -0.3f, 0.03f)
                arcToRelative(0.13f, 0.13f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.12f, dy1 = 0.02f)
                curveToRelative(-0.05f, -0.06f, -0.09f, 0.02f, -0.02f, -0.02f)
                curveToRelative(0.04f, -0.02f, 0.04f, 0.08f, 0.05f, 0.05f)
                curveToRelative(0.01f, -0.06f, -0.05f, -0.13f, -0.05f, -0.19f)
                curveToRelative(-0.0f, -0.12f, -0.02f, 0.14f, -0.0f, -0.07f)
                curveToRelative(0.12f, -1.45f, 1.74f, -3.0f, 2.59f, -4.49f)
                curveToRelative(0.81f, -1.42f, 1.48f, -3.28f, -1.34f, -4.08f)
                curveToRelative(-2.84f, -0.8f, -6.24f, 0.47f, -8.6f, 1.4f)
                curveToRelative(-1.34f, 0.53f, -2.64f, 1.09f, -3.99f, 1.61f)
                quadToRelative(-0.75f, 0.3f, -1.56f, 0.55f)
                curveToRelative(-0.28f, 0.08f, -0.57f, 0.16f, -0.86f, 0.23f)
                arcToRelative(5.33f, 5.33f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.64f, dy1 = 0.11f)
                curveToRelative(-0.17f, 0.04f, -0.12f, 0.02f, 0.09f, 0.02f)
                curveToRelative(-0.26f, -0.0f, 0.18f, 0.12f, -0.22f, -0.06f)
                curveToRelative(0.23f, 0.1f, 0.15f, 0.08f, 0.16f, 0.04f)
                curveToRelative(-0.09f, 0.27f, -0.03f, -0.22f, -0.03f, -0.14f)
                arcToRelative(3.87f, 3.87f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.82f, dy1 = -1.86f)
                curveToRelative(0.99f, -1.55f, 2.55f, -3.19f, 2.49f, -4.8f)
                curveToRelative(0.03f, -0.76f, -0.62f, -1.45f, -1.77f, -1.88f)
                curveToRelative(-2.25f, -0.84f, -4.98f, 1.12f, -2.73f, 1.96f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 4.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(389.54f, 88.71f)
                lineTo(447.48f, 108.29f)
                curveToRelative(8.52f, 2.88f, 10.21f, 8.96f, 3.77f, 13.58f)
                lineToRelative(-0.11f, 0.08f)
                curveToRelative(-6.44f, 4.62f, -18.56f, 6.03f, -27.08f, 3.15f)
                lineTo(366.12f, 105.52f)
                curveToRelative(-8.52f, -2.88f, -10.21f, -8.96f, -3.77f, -13.58f)
                lineToRelative(0.11f, -0.08f)
                curveTo(368.9f, 87.24f, 381.03f, 85.83f, 389.54f, 88.71f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(MaterialTheme.colorScheme.primary),
                strokeLineWidth = 4.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(262.77f, 90.33f)
                lineToRelative(113.61f, 38.39f)
                curveToRelative(8.52f, 2.88f, 10.21f, 8.96f, 3.77f, 13.58f)
                lineTo(264.96f, 224.97f)
                curveToRelative(-6.44f, 4.62f, -18.56f, 6.03f, -27.08f, 3.15f)
                lineToRelative(-113.61f, -38.39f)
                curveToRelative(-8.52f, -2.88f, -10.21f, -8.96f, -3.77f, -13.58f)
                lineToRelative(115.19f, -82.68f)
                curveTo(242.13f, 88.86f, 254.25f, 87.45f, 262.77f, 90.33f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(137.78f, 181.41f)
                lineToRelative(97.34f, 32.89f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineTo(155.09f, 183.42f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(153.66f, 170.01f)
                lineTo(251.01f, 202.91f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(169.54f, 158.61f)
                lineTo(266.89f, 191.51f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(185.43f, 147.21f)
                lineToRelative(97.34f, 32.89f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(201.31f, 135.81f)
                lineTo(298.65f, 168.71f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(217.19f, 124.41f)
                lineToRelative(97.34f, 32.89f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(233.07f, 113.02f)
                lineToRelative(97.34f, 32.89f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(248.96f, 101.62f)
                lineToRelative(97.34f, 32.89f)
                curveToRelative(4.48f, 1.51f, 8.87f, 3.12f, 13.47f, 4.55f)
                curveToRelative(0.07f, 0.02f, 0.13f, 0.04f, 0.2f, 0.07f)
                curveToRelative(3.1f, 1.05f, 6.75f, -1.56f, 3.64f, -2.61f)
                lineToRelative(-97.34f, -32.89f)
                curveToRelative(-4.48f, -1.51f, -8.87f, -3.12f, -13.47f, -4.55f)
                curveToRelative(-0.07f, -0.02f, -0.13f, -0.04f, -0.2f, -0.07f)
                curveToRelative(-3.1f, -1.05f, -6.75f, 1.56f, -3.64f, 2.61f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFFf2f0f3)),
                stroke = SolidColor(Color(0xFF45464f)),
                strokeLineWidth = 7.708525f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(316.1f, 3.89f)
                lineTo(178.43f, 12.21f)
                arcToRelative(8.45f, 8.45f, 0.0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -8.43f, dy1 = 7.17f)
                lineToRelative(-18.31f, 84.86f)
                curveToRelative(-0.75f, 4.59f, 2.77f, 7.1f, 8.43f, 7.17f)
                lineTo(265.95f, 111.4f)
                arcToRelative(2.01f, 2.01f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.5f, dy1 = 0.63f)
                lineToRelative(21.44f, 29.31f)
                curveToRelative(5.89f, 6.65f, 12.77f, 2.49f, 13.98f, -5.4f)
                lineToRelative(4.36f, -24.54f)
                lineTo(324.53f, 11.06f)
                curveTo(325.22f, 7.33f, 322.49f, 3.42f, 316.1f, 3.89f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF00a67d)),
                stroke = SolidColor(Color(0xFF45464f)),
                strokeLineWidth = 7.708525f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(208.32f, 35.72f)
                lineTo(264.21f, 35.72f)
                arcTo(5.78f, 5.78f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 269.99f, y1 = 41.5f)
                lineTo(269.99f, 78.11f)
                arcTo(5.78f, 5.78f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 264.21f, y1 = 83.9f)
                lineTo(208.32f, 83.9f)
                arcTo(5.78f, 5.78f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 202.54f, y1 = 78.11f)
                lineTo(202.54f, 41.5f)
                arcTo(5.78f, 5.78f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 208.32f, y1 = 35.72f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(Color(0xFF45464f)),
                strokeLineWidth = 7.708525f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(188.08f, 51.25f)
                lineTo(188.08f, 68.37f)
            }
            path(
                fill = SolidColor(Color(0x00000000)),
                stroke = SolidColor(Color(0xFF45464f)),
                strokeLineWidth = 7.708525f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(284.44f, 51.25f)
                lineTo(284.44f, 68.37f)
            }
            path(
                fill = SolidColor(Color(0xFF45464f)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(221.27f, 55.55f)
                moveToRelative(-4.82f, 0.0f)
                arcToRelative(4.82f, 4.82f, 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 9.64f, dy1 = 0.0f)
                arcToRelative(4.82f, 4.82f, 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -9.64f, dy1 = 0.0f)
            }
            path(
                fill = SolidColor(Color(0xFF45464f)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(251.37f, 55.55f)
                moveToRelative(-4.82f, 0.0f)
                arcToRelative(4.82f, 4.82f, 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 9.64f, dy1 = 0.0f)
                arcToRelative(4.82f, 4.82f, 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -9.64f, dy1 = 0.0f)
            }
        }
            .build()
    }

@Preview
@Composable
fun GPTLogoStartScreen() {
    GPTMobileTheme(
        dynamicTheme = DynamicTheme.ON,
        themeMode = ThemeMode.DARK
    ) {
        Image(imageVector = GptMobileStartScreen, contentDescription = "")
    }
}
