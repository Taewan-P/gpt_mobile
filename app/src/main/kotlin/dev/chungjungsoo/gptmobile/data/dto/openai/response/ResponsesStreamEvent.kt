package dev.chungjungsoo.gptmobile.data.dto.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Streaming events from OpenAI Responses API.
 * These events are used for reasoning models to stream both reasoning and text content.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ResponsesStreamEvent

/**
 * Emitted when a delta is added to reasoning summary text.
 */
@Serializable
@SerialName("response.reasoning_summary_text.delta")
data class ReasoningSummaryTextDeltaEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("summary_index")
    val summaryIndex: Int,

    @SerialName("delta")
    val delta: String
) : ResponsesStreamEvent()

/**
 * Emitted when reasoning summary text is complete.
 */
@Serializable
@SerialName("response.reasoning_summary_text.done")
data class ReasoningSummaryTextDoneEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("summary_index")
    val summaryIndex: Int,

    @SerialName("text")
    val text: String
) : ResponsesStreamEvent()

/**
 * Emitted when a reasoning summary part is added.
 */
@Serializable
@SerialName("response.reasoning_summary_part.added")
data class ReasoningSummaryPartAddedEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("summary_index")
    val summaryIndex: Int,

    @SerialName("part")
    val part: SummaryPart
) : ResponsesStreamEvent()

/**
 * Emitted when a reasoning summary part is done.
 */
@Serializable
@SerialName("response.reasoning_summary_part.done")
data class ReasoningSummaryPartDoneEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("summary_index")
    val summaryIndex: Int,

    @SerialName("part")
    val part: SummaryPart
) : ResponsesStreamEvent()

/**
 * Emitted when a delta is added to output text.
 */
@Serializable
@SerialName("response.output_text.delta")
data class OutputTextDeltaEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("content_index")
    val contentIndex: Int,

    @SerialName("delta")
    val delta: String
) : ResponsesStreamEvent()

/**
 * Emitted when output text is complete.
 */
@Serializable
@SerialName("response.output_text.done")
data class OutputTextDoneEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("content_index")
    val contentIndex: Int,

    @SerialName("text")
    val text: String
) : ResponsesStreamEvent()

/**
 * Emitted when the response is completed.
 */
@Serializable
@SerialName("response.completed")
data class ResponseCompletedEvent(
    @SerialName("response")
    val response: ResponseObject
) : ResponsesStreamEvent()

/**
 * Emitted when the response fails.
 */
@Serializable
@SerialName("response.failed")
data class ResponseFailedEvent(
    @SerialName("response")
    val response: ResponseObject
) : ResponsesStreamEvent()

/**
 * Emitted when there's an error.
 */
@Serializable
@SerialName("error")
data class ResponseErrorEvent(
    @SerialName("code")
    val code: String? = null,

    @SerialName("message")
    val message: String,

    @SerialName("param")
    val param: String? = null
) : ResponsesStreamEvent()

/**
 * Response created event - emitted when response is first created
 */
@Serializable
@SerialName("response.created")
data class ResponseCreatedEvent(
    @SerialName("response")
    val response: ResponseObject
) : ResponsesStreamEvent()

/**
 * Response in progress event
 */
@Serializable
@SerialName("response.in_progress")
data class ResponseInProgressEvent(
    @SerialName("response")
    val response: ResponseObject
) : ResponsesStreamEvent()

/**
 * Content part added event
 */
@Serializable
@SerialName("response.content_part.added")
data class ContentPartAddedEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("content_index")
    val contentIndex: Int,

    @SerialName("part")
    val part: ContentPart
) : ResponsesStreamEvent()

/**
 * Content part done event
 */
@Serializable
@SerialName("response.content_part.done")
data class ContentPartDoneEvent(
    @SerialName("item_id")
    val itemId: String,

    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("content_index")
    val contentIndex: Int,

    @SerialName("part")
    val part: ContentPart
) : ResponsesStreamEvent()

/**
 * Output item added event
 */
@Serializable
@SerialName("response.output_item.added")
data class OutputItemAddedEvent(
    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("item")
    val item: OutputItem
) : ResponsesStreamEvent()

/**
 * Output item done event
 */
@Serializable
@SerialName("response.output_item.done")
data class OutputItemDoneEvent(
    @SerialName("output_index")
    val outputIndex: Int,

    @SerialName("item")
    val item: OutputItem
) : ResponsesStreamEvent()

/**
 * Catch-all for unrecognized events
 */
@Serializable
@SerialName("unknown")
data object UnknownEvent : ResponsesStreamEvent()

@Serializable
data class ResponseObject(
    @SerialName("id")
    val id: String,

    @SerialName("status")
    val status: String? = null,

    @SerialName("error")
    val error: ResponseError? = null
)

@Serializable
data class ResponseError(
    @SerialName("code")
    val code: String? = null,

    @SerialName("message")
    val message: String
)

@Serializable
data class ContentPart(
    @SerialName("type")
    val type: String,

    @SerialName("text")
    val text: String? = null
)

@Serializable
data class OutputItem(
    @SerialName("type")
    val type: String,

    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("arguments")
    val arguments: String? = null,

    @SerialName("call_id")
    val callId: String? = null
)

@Serializable
data class SummaryPart(
    @SerialName("type")
    val type: String,

    @SerialName("text")
    val text: String? = null
)
