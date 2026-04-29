package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.model.AttachmentProviderRef
import dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentUploadCoordinatorTest {
    @Test
    fun `existing openai ref is reused without upload`() = runBlocking {
        val openAIAPI = FakeOpenAIAPI(isAvailable = true)
        val coordinator = AttachmentUploadCoordinator(openAIAPI, FakeAnthropicAPI(), FakeGoogleAPI())
        val message = MessageV2(
            content = "hello",
            platformType = null,
            attachments = listOf(
                ChatAttachment(
                    localFilePath = "/tmp/image.png",
                    preparedFilePath = "/tmp/image.png",
                    displayName = "image.png",
                    mimeType = "image/png",
                    sizeBytes = 1,
                    providerRefs = listOf(
                        AttachmentProviderRef(
                            platformUid = "openai-platform",
                            remoteType = AttachmentRemoteType.OPENAI_FILE,
                            remoteId = "file-existing",
                            mimeType = "image/png",
                            uploadedAt = 1L
                        )
                    )
                )
            )
        )

        val updated = coordinator.ensureMessageAttachmentsForPlatform(
            message,
            PlatformV2(
                uid = "openai-platform",
                name = "OpenAI",
                compatibleType = ClientType.OPENAI,
                apiUrl = "https://api.openai.com",
                model = "gpt-4.1"
            )
        )

        assertEquals(0, openAIAPI.uploadCount)
        assertEquals("file-existing", updated.attachments.single().providerRefs.single().remoteId)
    }

    @Test
    fun `missing google ref uploads and stores remote uri`() = runBlocking {
        val googleAPI = FakeGoogleAPI()
        val coordinator = AttachmentUploadCoordinator(FakeOpenAIAPI(), FakeAnthropicAPI(), googleAPI)
        val tempFile = File.createTempFile("attachment", ".png").apply {
            writeBytes(ByteArray(32))
            deleteOnExit()
        }
        val message = MessageV2(
            content = "describe",
            platformType = null,
            attachments = listOf(
                ChatAttachment(
                    localFilePath = tempFile.absolutePath,
                    preparedFilePath = tempFile.absolutePath,
                    displayName = tempFile.name,
                    mimeType = "image/png",
                    sizeBytes = tempFile.length()
                )
            )
        )

        val updated = coordinator.ensureMessageAttachmentsForPlatform(
            message,
            PlatformV2(
                uid = "google-platform",
                name = "Google",
                compatibleType = ClientType.GOOGLE,
                apiUrl = "https://generativelanguage.googleapis.com",
                model = "gemini-2.0-flash"
            )
        )

        assertEquals(1, googleAPI.uploadCount)
        assertEquals("google-uri", updated.attachments.single().providerRefs.single().remoteId)
        assertEquals("files/google-file", updated.attachments.single().providerRefs.single().remoteName)
    }

    @Test(expected = IllegalStateException::class)
    fun `inline attachment budget rejects oversized payloads`() = runBlocking {
        val coordinator = AttachmentUploadCoordinator(FakeOpenAIAPI(), FakeAnthropicAPI(), FakeGoogleAPI())
        val first = File.createTempFile("inline-first", ".png").apply {
            writeBytes(ByteArray(7 * 1024 * 1024))
            deleteOnExit()
        }
        val second = File.createTempFile("inline-second", ".png").apply {
            writeBytes(ByteArray(7 * 1024 * 1024))
            deleteOnExit()
        }

        coordinator.validateInlineAttachmentBudget(
            contextTurns = listOf(
                ConversationTurn(
                    userMessage = MessageV2(
                        content = "hi",
                        platformType = null,
                        attachments = listOf(
                            ChatAttachment(
                                localFilePath = first.absolutePath,
                                preparedFilePath = first.absolutePath,
                                displayName = first.name,
                                mimeType = "image/png",
                                sizeBytes = first.length()
                            ),
                            ChatAttachment(
                                localFilePath = second.absolutePath,
                                preparedFilePath = second.absolutePath,
                                displayName = second.name,
                                mimeType = "image/png",
                                sizeBytes = second.length()
                            )
                        )
                    ),
                    assistantMessage = null,
                    isCurrentTurn = true
                )
            )
        )
    }

    private class FakeOpenAIAPI(
        private val isAvailable: Boolean = false
    ) : OpenAIAPI {
        var uploadCount = 0

        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamChatCompletion(request: ChatCompletionRequest, timeoutSeconds: Int): Flow<ChatCompletionChunk> = emptyFlow()

        override fun streamResponses(request: ResponsesRequest, timeoutSeconds: Int): Flow<ResponsesStreamEvent> = emptyFlow()

        override suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile {
            uploadCount += 1
            return UploadedProviderFile(id = "file-uploaded", mimeType = mimeType)
        }

        override suspend fun isFileAvailable(fileId: String): Boolean = isAvailable
    }

    private class FakeAnthropicAPI : AnthropicAPI {
        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamChatMessage(messageRequest: MessageRequest, timeoutSeconds: Int): Flow<MessageResponseChunk> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String
        ): UploadedProviderFile = UploadedProviderFile(id = "anthropic-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String): Boolean = false
    }

    private class FakeGoogleAPI : GoogleAPI {
        var uploadCount = 0

        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamGenerateContent(
            request: GenerateContentRequest,
            model: String,
            timeoutSeconds: Int
        ): Flow<GenerateContentResponse> = emptyFlow()

        override suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile {
            uploadCount += 1
            return UploadedProviderFile(
                id = "google-id",
                name = "files/google-file",
                uri = "google-uri",
                mimeType = mimeType
            )
        }

        override suspend fun isFileAvailable(fileName: String): Boolean = false
    }
}
