package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import java.security.MessageDigest

object CompactionKeys {
    fun endpointModelKey(platform: PlatformV2): String = listOf(platform.compatibleType.name, platform.apiUrl.trimEnd('/'), platform.model).joinToString("|")

    fun sourceFingerprint(
        turns: List<ConversationTurn>,
        platform: PlatformV2,
        toolEvidence: List<ToolEvidence> = emptyList(),
        toolDefinitionNames: List<String> = emptyList()
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(platform.systemPrompt.orEmpty().toByteArray())
        digest.update(0)
        digest.update(endpointModelKey(platform).toByteArray())
        digest.update(0)
        toolDefinitionNames.sorted().forEach { name ->
            digest.update(name.toByteArray())
            digest.update(0)
        }
        toolEvidence.forEach { evidence ->
            digest.update(evidence.runId.toByteArray())
            digest.update(evidence.toolName.toByteArray())
            digest.update(evidence.result.toByteArray())
            digest.update(if (evidence.isError) 1 else 0)
        }
        turns.forEach { turn ->
            digest.update(turn.userMessage.content.toByteArray())
            digest.update(0)
            turn.userMessage.attachments.forEach { attachment ->
                digest.update((attachment.preparedFilePath.ifBlank { attachment.localFilePath }).toByteArray())
                digest.update(0)
            }
            digest.update((turn.assistantMessage?.content ?: "").toByteArray())
            digest.update(0)
            digest.update((turn.assistantMessage?.activeRevisionIndex ?: -1).toString().toByteArray())
            digest.update(0)
            digest.update((turn.assistantMessage?.currentRunId ?: "").toByteArray())
            digest.update(1)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
