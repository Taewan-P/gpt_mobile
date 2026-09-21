package dev.chungjungsoo.gptmobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

object FileUtils {
    private const val TAG = "FileUtils"
    const val MAX_UPLOAD_SIZE_BYTES = 50L * 1024 * 1024
    private const val MAX_IMAGE_UPLOAD_DIMENSION = 2048
    private const val MAX_IMAGE_DIRECT_UPLOAD_SIZE_BYTES = 8L * 1024 * 1024
    private const val IMAGE_UPLOAD_QUALITY = 95

    data class EncodedImage(
        val mimeType: String,
        val base64Data: String
    )

    data class AttachmentPreparationResult(
        val preparedFilePath: String,
        val mimeType: String,
        val wasResized: Boolean
    )

    /**
     * Read file from URI and encode it to base64 for upload.
     *
     * Image inputs may be downsampled and recompressed before encoding to reduce
     * memory usage during request construction.
     *
     * @param context Android context for ContentResolver
     * @param uriString File URI as string (content://, file://, or absolute path)
     * @return Base64-encoded upload payload, or null if error
     */
    fun readAndEncodeFile(context: Context, uriString: String): String? = try {
        encodeFileForUpload(context, uriString, getMimeType(context, uriString))?.base64Data
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encode file for upload: $uriString", e)
        null
    }

    /**
     * Read an image from URI and encode it to base64 for upload.
     *
     * Animated/vector formats that should not be recompressed are streamed as-is.
     * Other image formats may be downsampled and recompressed to lower memory use.
     */
    fun readAndEncodeImageForUpload(context: Context, uriString: String): EncodedImage? = try {
        val mimeType = getMimeType(context, uriString)
        readAndEncodeImageForUpload(context, uriString, mimeType)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encode image for upload: $uriString", e)
        null
    }

    internal fun readAndEncodeImageForUpload(context: Context, uriString: String, mimeType: String): EncodedImage? {
        if (!isImage(mimeType)) return null
        return encodeFileForUpload(context, uriString, mimeType)
    }

    fun prepareAttachmentForUpload(context: Context, filePath: String): AttachmentPreparationResult? {
        val mimeType = getMimeType(context, filePath)
        val fileSize = getFileSize(context, filePath)
        if (!validateFileSize(context, filePath, MAX_UPLOAD_SIZE_BYTES)) return null
        if (!isSupportedUploadMimeType(mimeType) || mimeType == "image/gif" || mimeType == "image/svg+xml") {
            return AttachmentPreparationResult(
                preparedFilePath = filePath,
                mimeType = mimeType,
                wasResized = false
            )
        }

        val dimensions = getImageDimensions(context, filePath) ?: return AttachmentPreparationResult(
            preparedFilePath = filePath,
            mimeType = mimeType,
            wasResized = false
        )

        if (!shouldResizeImageForUpload(dimensions.first, dimensions.second, fileSize)) {
            return AttachmentPreparationResult(
                preparedFilePath = filePath,
                mimeType = mimeType,
                wasResized = false
            )
        }

        val resizedImagePath = createResizedImageCopy(context, filePath, mimeType) ?: return null
        return AttachmentPreparationResult(
            preparedFilePath = resizedImagePath.preparedFilePath,
            mimeType = resizedImagePath.mimeType,
            wasResized = true
        )
    }

    fun readImageBytesForLocalInference(context: Context, filePath: String): ByteArray? {
        val rawBytes = try {
            getInputStreamFromUri(context, filePath)?.use { inputStream -> inputStream.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image bytes for local inference: $filePath", e)
            null
        } ?: return null

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return rawBytes
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateImageInSampleSize(bounds.outWidth, bounds.outHeight)
            }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions) ?: return rawBytes
            try {
                val output = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    output.toByteArray()
                } else {
                    rawBytes
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image bytes for local inference: $filePath", e)
            rawBytes
        }
    }

    fun encodeFileForUpload(context: Context, filePath: String, mimeType: String): EncodedImage? {
        if (!isImage(mimeType)) {
            val base64Data = encodeFileToBase64(context, filePath) ?: return null
            return EncodedImage(mimeType = mimeType, base64Data = base64Data)
        }

        val preparedAttachment = prepareAttachmentForUpload(context, filePath) ?: return null
        return try {
            val base64Data = encodeFileToBase64(context, preparedAttachment.preparedFilePath) ?: return null
            EncodedImage(mimeType = preparedAttachment.mimeType, base64Data = base64Data)
        } finally {
            if (preparedAttachment.preparedFilePath != filePath) {
                File(preparedAttachment.preparedFilePath).delete()
            }
        }
    }

    /**
     * Get InputStream from URI string
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @return InputStream or null if error
     */
    private fun getInputStreamFromUri(context: Context, uriString: String): InputStream? = try {
        when {
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)
            }

            uriString.startsWith("file://") -> {
                val path = uriString.removePrefix("file://")
                FileInputStream(File(path))
            }

            else -> {
                // Assume it's an absolute path
                FileInputStream(File(uriString))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open input stream for: $uriString", e)
        null
    }

    /**
     * Get MIME type from URI
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @return MIME type string, or "application/octet-stream" if unknown
     */
    fun getMimeType(context: Context, uriString: String): String = try {
        when {
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(uriString)
            }

            else -> {
                getMimeTypeFromExtension(uriString)
            }
        }
    } catch (e: Exception) {
        "application/octet-stream"
    }

    fun getMimeTypeFromPath(path: String): String = getMimeTypeFromExtension(path)

    /**
     * Get MIME type from file extension
     * @param filename File name or path
     * @return MIME type string, or "application/octet-stream" if unknown
     */
    private fun getMimeTypeFromExtension(filename: String): String = when (val extension = filename.substringAfterLast('.', "").lowercase()) {
        // Images
        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "gif" -> "image/gif"

        "bmp" -> "image/bmp"

        "webp" -> "image/webp"

        "tiff", "tif" -> "image/tiff"

        "svg" -> "image/svg+xml"

        // Documents
        "pdf" -> "application/pdf"

        "txt" -> "text/plain"

        "doc" -> "application/msword"

        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        "xls" -> "application/vnd.ms-excel"

        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    /**
     * Check if file is an image based on MIME type
     * @param mimeType MIME type string
     * @return true if image, false otherwise
     */
    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    /**
     * Check if file is a document based on MIME type
     * @param mimeType MIME type string
     * @return true if document, false otherwise
     */
    fun isDocument(mimeType: String): Boolean = mimeType in listOf(
        "application/pdf",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

    /**
     * Get file size in bytes
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @return File size in bytes, or -1 if error
     */
    fun getFileSize(context: Context, uriString: String): Long = try {
        when {
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    fd.statSize
                } ?: -1L
            }

            uriString.startsWith("file://") -> {
                val path = uriString.removePrefix("file://")
                File(path).length()
            }

            else -> {
                File(uriString).length()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get file size for: $uriString", e)
        -1L
    }

    /**
     * Validate file size
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @param maxSizeBytes Maximum allowed size in bytes (default 50MB)
     * @return true if file size is within limit, false otherwise
     */
    fun validateFileSize(context: Context, uriString: String, maxSizeBytes: Long = MAX_UPLOAD_SIZE_BYTES): Boolean {
        val size = getFileSize(context, uriString)
        return size in 1..maxSizeBytes
    }

    internal fun wouldExceedTotalUploadLimit(
        currentTotalBytes: Long,
        newFileBytes: Long,
        maxSizeBytes: Long = MAX_UPLOAD_SIZE_BYTES
    ): Boolean = currentTotalBytes + newFileBytes > maxSizeBytes

    internal fun isSupportedUploadMimeType(mimeType: String): Boolean = isImage(mimeType)

    internal fun shouldResizeImage(
        originalWidth: Int,
        originalHeight: Int,
        maxDimension: Int = MAX_IMAGE_UPLOAD_DIMENSION
    ): Boolean = maxOf(originalWidth, originalHeight) > maxDimension

    internal fun shouldResizeImageForUpload(
        originalWidth: Int,
        originalHeight: Int,
        fileSizeBytes: Long,
        maxDimension: Int = MAX_IMAGE_UPLOAD_DIMENSION,
        maxDirectFileSizeBytes: Long = MAX_IMAGE_DIRECT_UPLOAD_SIZE_BYTES
    ): Boolean = shouldResizeImage(originalWidth, originalHeight, maxDimension) || fileSizeBytes > maxDirectFileSizeBytes

    internal fun calculateImageInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        maxDimension: Int = MAX_IMAGE_UPLOAD_DIMENSION
    ): Int {
        if (originalWidth <= 0 || originalHeight <= 0 || maxDimension <= 0) return 1

        val longestEdge = maxOf(originalWidth, originalHeight)
        var sampleSize = 1

        while (longestEdge / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        return sampleSize
    }

    internal fun shouldPreserveAlpha(mimeType: String): Boolean = mimeType.contains("png") || mimeType.contains("webp")

    internal fun encodeToBase64(writeBytes: (OutputStream) -> Boolean): String? {
        val outputStream = ByteArrayOutputStream()
        val success = Base64.getEncoder().wrap(outputStream).use { base64Stream ->
            writeBytes(base64Stream)
        }

        return if (success) {
            outputStream.toString(Charsets.UTF_8.name())
        } else {
            null
        }
    }

    fun getImageDimensionsForDisplay(context: Context, uriString: String): Pair<Int, Int>? = getImageDimensions(context, uriString)

    private fun getImageDimensions(context: Context, uriString: String): Pair<Int, Int>? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        getInputStreamFromUri(context, uriString)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null
        return boundsOptions.outWidth to boundsOptions.outHeight
    }

    private fun createResizedImageCopy(context: Context, uriString: String, mimeType: String): AttachmentPreparationResult? {
        val dimensions = getImageDimensions(context, uriString) ?: return null

        val (compressFormat, uploadMimeType) = resolveImageCompressFormat(mimeType)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateImageInSampleSize(dimensions.first, dimensions.second)
            inPreferredConfig = if (shouldPreserveAlpha(uploadMimeType)) {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.RGB_565
            }
        }

        val bitmap = getInputStreamFromUri(context, uriString)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        return try {
            val sourceFileName = resolveSourceFileName(context, uriString)
            val sourceFile = File(sourceFileName)
            val resizedFile = File(
                context.cacheDir,
                "${sourceFile.nameWithoutExtension}_upload.${uploadMimeType.substringAfter('/')}"
            )
            resizedFile.outputStream().use { outputStream ->
                val success = bitmap.compress(compressFormat, IMAGE_UPLOAD_QUALITY, outputStream)
                if (!success) return null
            }
            AttachmentPreparationResult(
                preparedFilePath = resizedFile.absolutePath,
                mimeType = uploadMimeType,
                wasResized = true
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun resolveSourceFileName(context: Context, uriString: String): String {
        if (!uriString.startsWith("content://")) {
            return File(uriString.removePrefix("file://")).name
        }

        return runCatching {
            context.contentResolver.query(Uri.parse(uriString), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(displayNameIndex)
                    } else {
                        null
                    }
                }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Uri.parse(uriString).lastPathSegment
            ?: "attachment_${System.currentTimeMillis()}"
    }

    private fun encodeFileToBase64(context: Context, uriString: String): String? {
        val outputStream = ByteArrayOutputStream()
        getInputStreamFromUri(context, uriString)?.use { inputStream ->
            Base64.getEncoder().wrap(outputStream).use { base64Stream ->
                inputStream.copyTo(base64Stream)
            }
        } ?: return null

        return outputStream.toString(Charsets.UTF_8.name())
    }

    private fun resolveImageCompressFormat(mimeType: String): Pair<Bitmap.CompressFormat, String> = when {
        mimeType.contains("png") -> Bitmap.CompressFormat.PNG to "image/png"
        mimeType.contains("webp") -> Bitmap.CompressFormat.WEBP_LOSSLESS to "image/webp"
        else -> Bitmap.CompressFormat.JPEG to "image/jpeg"
    }
}
