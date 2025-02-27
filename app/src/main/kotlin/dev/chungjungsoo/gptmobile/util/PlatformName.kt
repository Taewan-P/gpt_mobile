package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

fun List<PlatformV2>.getPlatformName(uid: String): String = this.find { it.uid == uid }?.name ?: "Unknown"
