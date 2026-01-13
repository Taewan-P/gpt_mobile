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

val Error: ImageVector
    @Composable
    get() = Builder(
        name = "Error Icon",
        defaultWidth = 24.0.dp,
        defaultHeight = 24.0.dp,
        viewportWidth = 960.0f,
        viewportHeight = 960.0f
    ).apply {
        path(
            fill = SolidColor(MaterialTheme.colorScheme.error),
            stroke = null,
            strokeLineWidth = 0.0f,
            strokeLineCap = Butt,
            strokeLineJoin = Miter,
            strokeLineMiter = 4.0f,
            pathFillType = NonZero
        ) {
            moveTo(479.95f, 688.13f)
            quadToRelative(19.92f, 0.0f, 33.45f, -13.48f)
            quadToRelative(13.53f, -13.48f, 13.53f, -33.4f)
            quadToRelative(0.0f, -19.92f, -13.47f, -33.58f)
            quadToRelative(-13.48f, -13.65f, -33.41f, -13.65f)
            quadToRelative(-19.92f, 0.0f, -33.45f, 13.65f)
            quadToRelative(-13.53f, 13.66f, -13.53f, 33.58f)
            quadToRelative(0.0f, 19.92f, 13.47f, 33.4f)
            quadToRelative(13.48f, 13.48f, 33.41f, 13.48f)
            close()
            moveTo(480.0f, 520.48f)
            quadToRelative(19.15f, 0.0f, 32.33f, -13.18f)
            quadToRelative(13.17f, -13.17f, 13.17f, -32.32f)
            verticalLineToRelative(-154.5f)
            quadToRelative(0.0f, -19.15f, -13.17f, -32.33f)
            quadToRelative(-13.18f, -13.17f, -32.33f, -13.17f)
            reflectiveQuadToRelative(-32.33f, 13.17f)
            quadToRelative(-13.17f, 13.18f, -13.17f, 32.33f)
            verticalLineToRelative(154.5f)
            quadToRelative(0.0f, 19.15f, 13.17f, 32.32f)
            quadToRelative(13.18f, 13.18f, 32.33f, 13.18f)
            close()
            moveTo(480.0f, 888.13f)
            quadToRelative(-84.91f, 0.0f, -159.34f, -32.12f)
            quadToRelative(-74.44f, -32.12f, -129.5f, -87.17f)
            quadToRelative(-55.05f, -55.06f, -87.17f, -129.5f)
            quadTo(71.87f, 564.91f, 71.87f, 480.0f)
            reflectiveQuadToRelative(32.12f, -159.34f)
            quadToRelative(32.12f, -74.44f, 87.17f, -129.5f)
            quadToRelative(55.06f, -55.05f, 129.5f, -87.17f)
            quadToRelative(74.43f, -32.12f, 159.34f, -32.12f)
            reflectiveQuadToRelative(159.34f, 32.12f)
            quadToRelative(74.44f, 32.12f, 129.5f, 87.17f)
            quadToRelative(55.05f, 55.06f, 87.17f, 129.5f)
            quadToRelative(32.12f, 74.43f, 32.12f, 159.34f)
            reflectiveQuadToRelative(-32.12f, 159.34f)
            quadToRelative(-32.12f, 74.44f, -87.17f, 129.5f)
            quadToRelative(-55.06f, 55.05f, -129.5f, 87.17f)
            quadTo(564.91f, 888.13f, 480.0f, 888.13f)
            close()
            moveTo(480.0f, 797.13f)
            quadToRelative(133.04f, 0.0f, 225.09f, -92.04f)
            quadToRelative(92.04f, -92.05f, 92.04f, -225.09f)
            quadToRelative(0.0f, -133.04f, -92.04f, -225.09f)
            quadToRelative(-92.05f, -92.04f, -225.09f, -92.04f)
            quadToRelative(-133.04f, 0.0f, -225.09f, 92.04f)
            quadToRelative(-92.04f, 92.05f, -92.04f, 225.09f)
            quadToRelative(0.0f, 133.04f, 92.04f, 225.09f)
            quadToRelative(92.05f, 92.04f, 225.09f, 92.04f)
            close()
            moveTo(480.0f, 480.0f)
            close()
        }
    }
        .build()
