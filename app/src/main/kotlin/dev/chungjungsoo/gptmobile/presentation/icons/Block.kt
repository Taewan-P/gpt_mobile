package dev.chungjungsoo.gptmobile.presentation.icons

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Block: ImageVector
    @Composable
    get() = Builder(
        name = "Blocked Icon",
        defaultWidth = 24.0.dp,
        defaultHeight = 24.0.dp,
        viewportWidth = 960.0f,
        viewportHeight = 960.0f
    ).apply {
        path(
            fill = SolidColor(MaterialTheme.colorScheme.outline),
            stroke = null,
            strokeLineWidth = 0.0f,
            strokeLineCap = Butt,
            strokeLineJoin = Miter,
            strokeLineMiter = 4.0f,
            pathFillType = NonZero
        ) {
            moveTo(480.0f, 888.13f)
            quadToRelative(-84.67f, 0.0f, -159.11f, -32.22f)
            quadToRelative(-74.43f, -32.21f, -129.63f, -87.41f)
            quadToRelative(-55.19f, -55.2f, -87.29f, -129.75f)
            quadToRelative(-32.1f, -74.55f, -32.1f, -159.23f)
            quadToRelative(0.0f, -84.67f, 32.1f, -158.99f)
            quadToRelative(32.1f, -74.31f, 87.29f, -129.39f)
            quadToRelative(55.2f, -55.07f, 129.63f, -87.17f)
            quadToRelative(74.44f, -32.1f, 159.11f, -32.1f)
            quadToRelative(84.67f, 0.0f, 159.11f, 32.1f)
            quadToRelative(74.43f, 32.1f, 129.63f, 87.17f)
            quadToRelative(55.19f, 55.08f, 87.29f, 129.39f)
            quadToRelative(32.1f, 74.32f, 32.1f, 158.99f)
            quadToRelative(0.0f, 84.68f, -32.1f, 159.23f)
            quadToRelative(-32.1f, 74.55f, -87.29f, 129.75f)
            quadToRelative(-55.2f, 55.2f, -129.63f, 87.41f)
            quadTo(564.67f, 888.13f, 480.0f, 888.13f)
            close()
            moveTo(480.0f, 797.13f)
            quadToRelative(52.09f, 0.0f, 100.65f, -16.3f)
            quadToRelative(48.57f, -16.31f, 89.13f, -48.11f)
            lineTo(226.8f, 289.98f)
            quadToRelative(-31.32f, 41.04f, -47.63f, 89.37f)
            quadToRelative(-16.3f, 48.32f, -16.3f, 100.17f)
            quadToRelative(0.0f, 132.81f, 92.28f, 225.21f)
            reflectiveQuadTo(480.0f, 797.13f)
            close()
            moveTo(733.43f, 669.07f)
            quadToRelative(31.09f, -41.05f, 47.4f, -89.37f)
            quadToRelative(16.3f, -48.33f, 16.3f, -100.18f)
            quadToRelative(0.0f, -132.56f, -92.28f, -224.61f)
            quadToRelative(-92.28f, -92.04f, -224.85f, -92.04f)
            quadToRelative(-51.85f, 0.0f, -100.05f, 16.06f)
            quadToRelative(-48.21f, 16.07f, -89.25f, 47.16f)
            lineToRelative(442.73f, 442.98f)
            close()
        }
    }
        .build()
