package com.notify2discord.app.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import com.notify2discord.app.notification.model.AttachmentPayload
import java.io.File
import java.io.FileOutputStream

object NotificationAttachmentExtractor {
    /**
     * 通知本文が空でも、添付情報があれば送信対象として扱うための軽量判定。
     * ここではファイル保存を行わず、extrasの有無のみ確認する。
     */
    fun hasExtractableAttachment(notification: Notification): Boolean {
        return runCatching {
            if (hasBigPicture(notification)) return@runCatching true
            if (hasMessagingAttachment(notification)) return@runCatching true
            if (hasLargeIcon(notification)) return@runCatching true
            false
        }.getOrDefault(false)
    }

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

    private fun hasBigPicture(notification: Notification): Boolean {
        return notification.extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java) != null
    }

    private fun hasMessagingAttachment(notification: Notification): Boolean {
        // 端末実装差異を吸収するため、型指定APIと生配列APIの両方で安全に判定する
        val typed = runCatching {
            notification.extras.getParcelableArray(
                Notification.EXTRA_MESSAGES,
                Notification.MessagingStyle.Message::class.java
            )
        }.getOrNull()
        if (typed != null) {
            return typed
                .mapNotNull { it as? Notification.MessagingStyle.Message }
                .any { it.dataUri != null }
        }

        val raw = notification.extras.get(Notification.EXTRA_MESSAGES) as? Array<*> ?: return false
        val bundles = raw.mapNotNull { it as? android.os.Bundle }.toTypedArray()
        if (bundles.isEmpty()) return false
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
        return messages.any { it.dataUri != null }
    }

    private fun hasLargeIcon(notification: Notification): Boolean {
        val extras = notification.extras
        return extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java) != null ||
            extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Bitmap::class.java) != null ||
            extras.getParcelable(Notification.EXTRA_LARGE_ICON, Icon::class.java) != null ||
            extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Icon::class.java) != null
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
