package com.notify2discord.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.notify2discord.app.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject

class SettingsBackupManager(private val context: Context) {
    companion object {
        private const val BACKUP_DIR_NAME = "Notify2Discord"
        private const val BACKUP_PREFIX = "notify2discord_backup_"
        private val FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    // アプリ専用Documents配下の保存先を確実に作成する
    fun getOrCreateBackupDir(): File {
        val documentsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "Documents")
        val backupDir = File(documentsRoot, BACKUP_DIR_NAME)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IOException("バックアップフォルダを作成できません")
        }
        if (!backupDir.isDirectory) {
            throw IOException("バックアップ保存先がディレクトリではありません")
        }
        return backupDir
    }

    fun writeBackupFile(snapshotJson: String): Result<File> {
        return runCatching {
            val backupDir = getOrCreateBackupDir()
            val timestamp = LocalDateTime.now().format(FILE_TIME_FORMAT)
            val targetFile = File(backupDir, "${BACKUP_PREFIX}${timestamp}.json")
            targetFile.writeText(snapshotJson, Charsets.UTF_8)
            targetFile
        }
    }

    fun writeBackupToUri(uri: Uri, payload: String): Result<Unit> {
        return runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("バックアップ書き込み先を開けません")
            outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        }
    }

    fun readBackupFromUri(uri: Uri): Result<String> {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("バックアップファイルを開けません")
            inputStream.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        }
    }

    fun readLatestSnapshotJson(): Result<String> {
        return readLatestBackupFile().map { file ->
            file.readText(Charsets.UTF_8)
        }
    }

    fun readLatestBackupFile(): Result<File> {
        return runCatching {
            val backupDir = getOrCreateBackupDir()
            val candidates = listBackupFiles(backupDir)
            candidates.firstOrNull() ?: throw IOException("バックアップファイルが見つかりません")
        }
    }

    fun pruneOldBackups(maxCount: Int): Result<Int> {
        return runCatching {
            if (maxCount < 1) return@runCatching 0
            val backupDir = getOrCreateBackupDir()
            val targets = listBackupFiles(backupDir).drop(maxCount)
            var deleted = 0
            targets.forEach { file ->
                if (file.delete()) {
                    deleted += 1
                }
            }
            deleted
        }
    }

    fun buildBackupEnvelope(settingsJson: String): String {
        val settingsObj = JSONObject(settingsJson)
        val canonicalSettingsJson = settingsObj.toString()
        val checksum = sha256(canonicalSettingsJson)
        return JSONObject()
            .put("schemaVersion", SettingsState.BACKUP_SCHEMA_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("settings", settingsObj)
            .put("checksumSha256", checksum)
            .toString()
    }

    fun verifyBackupEnvelope(payload: String): Result<String> {
        return runCatching {
            val root = JSONObject(payload)
            val settingsJson = when (val rawSettings = root.opt("settings")) {
                is JSONObject -> rawSettings.toString()
                is String -> JSONObject(rawSettings).toString()
                else -> throw IOException("バックアップ形式が不正です: settings がありません")
            }

            // 旧形式（checksum未導入）には互換対応し、導入済み形式は必ず検証する
            if (root.has("checksumSha256")) {
                val expectedChecksum = root.optString("checksumSha256")
                if (expectedChecksum.isBlank()) {
                    throw IOException("バックアップ形式が不正です: checksum が空です")
                }
                val actualChecksum = sha256(settingsJson)
                if (!expectedChecksum.equals(actualChecksum, ignoreCase = true)) {
                    throw IOException("バックアップファイルが破損している可能性があります（チェックサム不一致）")
                }
            }

            settingsJson
        }
    }

    private fun listBackupFiles(backupDir: File): List<File> {
        return backupDir
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith(BACKUP_PREFIX) &&
                    file.extension.equals("json", ignoreCase = true)
            }
            .sortedByDescending { it.lastModified() }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
