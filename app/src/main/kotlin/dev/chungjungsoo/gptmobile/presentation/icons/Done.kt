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

val Done: ImageVector
    // It should be recomposed when theme is changed. So calculate every time (Expensive, but only used in setup complete screen)
    @Composable
    get() {
        return Builder(
            name = "IcDone",
            defaultWidth = 48.0.dp,
            defaultHeight = 48.0.dp,
            viewportWidth = 960.0f,
            viewportHeight = 960.0f
        ).apply {
            path(
                fill = SolidColor(MaterialTheme.colorScheme.primary),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveToRelative(421.0f, 571.0f)
                lineToRelative(-98.0f, -98.0f)
                quadToRelative(-9.0f, -9.0f, -22.0f, -9.0f)
                reflectiveQuadToRelative(-23.0f, 10.0f)
                quadToRelative(-9.0f, 9.0f, -9.0f, 22.0f)
                reflectiveQuadToRelative(9.0f, 22.0f)
                lineToRelative(122.0f, 123.0f)
                quadToRelative(9.0f, 9.0f, 21.0f, 9.0f)
                reflectiveQuadToRelative(21.0f, -9.0f)
                lineToRelative(239.0f, -239.0f)
                quadToRelative(10.0f, -10.0f, 10.0f, -23.0f)
                reflectiveQuadToRelative(-10.0f, -23.0f)
                quadToRelative(-10.0f, -9.0f, -23.5f, -8.5f)
                reflectiveQuadTo(635.0f, 357.0f)
                lineTo(421.0f, 571.0f)
                close()
                moveTo(480.0f, 880.0f)
                quadToRelative(-82.0f, 0.0f, -155.0f, -31.5f)
                reflectiveQuadToRelative(-127.5f, -86.0f)
                quadTo(143.0f, 708.0f, 111.5f, 635.0f)
                reflectiveQuadTo(80.0f, 480.0f)
                quadToRelative(0.0f, -83.0f, 31.5f, -156.0f)
                reflectiveQuadToRelative(86.0f, -127.0f)
                quadTo(252.0f, 143.0f, 325.0f, 111.5f)
                reflectiveQuadTo(480.0f, 80.0f)
                quadToRelative(83.0f, 0.0f, 156.0f, 31.5f)
                reflectiveQuadTo(763.0f, 197.0f)
                quadToRelative(54.0f, 54.0f, 85.5f, 127.0f)
                reflectiveQuadTo(880.0f, 480.0f)
                quadToRelative(0.0f, 82.0f, -31.5f, 155.0f)
                reflectiveQuadTo(763.0f, 762.5f)
                quadToRelative(-54.0f, 54.5f, -127.0f, 86.0f)
                reflectiveQuadTo(480.0f, 880.0f)
                close()
                moveTo(480.0f, 820.0f)
                quadToRelative(142.0f, 0.0f, 241.0f, -99.5f)
                reflectiveQuadTo(820.0f, 480.0f)
                quadToRelative(0.0f, -142.0f, -99.0f, -241.0f)
                reflectiveQuadToRelative(-241.0f, -99.0f)
                quadToRelative(-141.0f, 0.0f, -240.5f, 99.0f)
                reflectiveQuadTo(140.0f, 480.0f)
                quadToRelative(0.0f, 141.0f, 99.5f, 240.5f)
                reflectiveQuadTo(480.0f, 820.0f)
                close()
                moveTo(480.0f, 480.0f)
                close()
            }
        }
            .build()
    }
