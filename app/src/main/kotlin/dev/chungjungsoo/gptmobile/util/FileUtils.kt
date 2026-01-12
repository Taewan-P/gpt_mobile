package dev.chungjungsoo.gptmobile.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object FileUtils {

    /**
     * Read file from URI and encode to base64
     * @param context Android context for ContentResolver
     * @param uriString File URI as string (content://, file://, or absolute path)
     * @return Base64-encoded file content, or null if error
     */
    fun readAndEncodeFile(context: Context, uriString: String): String? = try {
        val inputStream = getInputStreamFromUri(context, uriString)
        inputStream?.use { stream ->
            val bytes = stream.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
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
        e.printStackTrace()
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

    /**
     * Get MIME type from file extension
     * @param filename File name or path
     * @return MIME type string, or "application/octet-stream" if unknown
     */
    private fun getMimeTypeFromExtension(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
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
        e.printStackTrace()
        -1L
    }

    /**
     * Validate file size
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @param maxSizeBytes Maximum allowed size in bytes (default 5MB)
     * @return true if file size is within limit, false otherwise
     */
    fun validateFileSize(context: Context, uriString: String, maxSizeBytes: Long = 5 * 1024 * 1024): Boolean {
        val size = getFileSize(context, uriString)
        return size in 1..maxSizeBytes
    }
}
