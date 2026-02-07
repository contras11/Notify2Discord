package com.notify2discord.app.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.notify2discord.app.notification.model.AttachmentPayload
import java.io.File
import java.io.FileOutputStream

object NotificationAttachmentExtractor {
    fun extract(context: Context, notification: Notification, payloadId: String): AttachmentPayload? {
        extractBigPicture(context, notification, payloadId)?.let { return it }
        extractMessagingAttachment(context, notification, payloadId)?.let { return it }
        extractLargeIcon(context, notification, payloadId)?.let { return it }
        return null
    }

    private fun extractBigPicture(
        context: Context,
        notification: Notification,
        payloadId: String
    ): AttachmentPayload? {
        val bitmap = notification.extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
            ?: return null
        return saveBitmap(context, bitmap, "big_picture_$payloadId.png")
    }

    private fun extractMessagingAttachment(
        context: Context,
        notification: Notification,
        payloadId: String
    ): AttachmentPayload? {
        val messages = notification.extras.getParcelableArray(
            Notification.EXTRA_MESSAGES,
            Notification.MessagingStyle.Message::class.java
        ) ?: return null

        val latestWithData = messages
            .mapNotNull { it as? Notification.MessagingStyle.Message }
            .lastOrNull { it.dataUri != null }
            ?: return null

        val mimeType = latestWithData.dataMimeType ?: "application/octet-stream"
        val uri = latestWithData.dataUri ?: return null
        return copyUriToCache(context, uri, "message_$payloadId", mimeType)
    }

    private fun extractLargeIcon(
        context: Context,
        notification: Notification,
        payloadId: String
    ): AttachmentPayload? {
        val bitmap = notification.extras.getParcelable(
            Notification.EXTRA_LARGE_ICON,
            Bitmap::class.java
        ) ?: notification.extras.getParcelable(
            Notification.EXTRA_LARGE_ICON_BIG,
            Bitmap::class.java
        ) ?: return null
        return saveBitmap(context, bitmap, "large_icon_$payloadId.png")
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): AttachmentPayload? {
        return runCatching {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            AttachmentPayload(
                filePath = file.absolutePath,
                fileName = file.name,
                contentType = "image/png"
            )
        }.getOrNull()
    }

    private fun copyUriToCache(
        context: Context,
        uri: Uri,
        baseName: String,
        mimeType: String
    ): AttachmentPayload? {
        return runCatching {
            val ext = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("webp") -> "webp"
                else -> "bin"
            }
            val file = File(context.cacheDir, "$baseName.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            AttachmentPayload(
                filePath = file.absolutePath,
                fileName = file.name,
                contentType = mimeType
            )
        }.getOrNull()
    }
}
