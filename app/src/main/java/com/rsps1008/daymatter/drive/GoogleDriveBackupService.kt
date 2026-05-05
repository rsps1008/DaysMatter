package com.rsps1008.daymatter.drive

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object GoogleDriveBackupService {
    const val DriveScope = "https://www.googleapis.com/auth/drive.appdata"

    private const val BACKUP_FILE_NAME = "daymatter-backup.csv"
    private const val BACKUP_MIME_TYPE = "text/csv"
    private val driveScope = Scope(DriveScope)

    suspend fun authorize(activity: Activity): AuthorizationResult {
        return withContext(Dispatchers.IO) {
            Tasks.await(
                Identity.getAuthorizationClient(activity).authorize(
                    AuthorizationRequest.Builder()
                        .setRequestedScopes(listOf(driveScope))
                        .build()
                )
            )
        }
    }

    fun resolveAuthorization(activity: Activity, intent: Intent): AuthorizationResult {
        return Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent)
    }

    suspend fun backupCsv(accessToken: String, csv: String) {
        withContext(Dispatchers.IO) {
            val fileId = findBackupFileId(accessToken) ?: createBackupFile(accessToken)
            uploadBackupContent(accessToken, fileId, csv)
        }
    }

    suspend fun restoreCsv(accessToken: String): String {
        return withContext(Dispatchers.IO) {
            val fileId = findBackupFileId(accessToken)
                ?: throw IOException("找不到 Google Drive 備份")
            downloadBackupContent(accessToken, fileId)
        }
    }

    private fun findBackupFileId(accessToken: String): String? {
        val query = "'appDataFolder' in parents and name='$BACKUP_FILE_NAME' and trashed=false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = URL(
            "https://www.googleapis.com/drive/v3/files" +
                "?spaces=appDataFolder" +
                "&q=$encodedQuery" +
                "&orderBy=modifiedTime desc" +
                "&fields=files(id,name,modifiedTime)"
        )
        val response = executeJsonRequest("GET", url, accessToken)
        val files = response.optJSONArray("files") ?: JSONArray()
        return if (files.length() > 0) {
            files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    private fun createBackupFile(accessToken: String): String {
        val url = URL("https://www.googleapis.com/drive/v3/files?fields=id")
        val body = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("parents", JSONArray().put("appDataFolder"))
            put("mimeType", BACKUP_MIME_TYPE)
        }.toString()
        val response = executeJsonRequest("POST", url, accessToken, body)
        return response.optString("id").takeIf { it.isNotBlank() }
            ?: throw IOException("建立 Google Drive 備份失敗")
    }

    private fun uploadBackupContent(accessToken: String, fileId: String, csv: String) {
        val url = URL(
            "https://www.googleapis.com/upload/drive/v3/files/$fileId" +
                "?uploadType=media" +
                "&fields=id,modifiedTime"
        )
        val connection = openConnection(
            url = url,
            method = "PATCH",
            accessToken = accessToken,
            contentType = "$BACKUP_MIME_TYPE; charset=UTF-8",
        )
        val payload = csv.toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(payload.size)
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write(payload)
        }
        if (connection.responseCode !in 200..299) {
            throw IOException(readError(connection))
        }
        connection.disconnect()
    }

    private fun downloadBackupContent(accessToken: String, fileId: String): String {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        val connection = openConnection(url = url, method = "GET", accessToken = accessToken)
        if (connection.responseCode !in 200..299) {
            throw IOException(readError(connection))
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        connection.disconnect()
        return body
    }

    private fun executeJsonRequest(
        method: String,
        url: URL,
        accessToken: String,
        body: String? = null,
    ): JSONObject {
        val connection = openConnection(
            url = url,
            method = method,
            accessToken = accessToken,
            contentType = "application/json; charset=UTF-8",
        )
        if (body != null) {
            val payload = body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(payload)
            }
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            readError(connection)
        }
        connection.disconnect()
        if (responseCode !in 200..299) {
            throw IOException(responseText)
        }
        return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
    }

    private fun openConnection(
        url: URL,
        method: String,
        accessToken: String,
        contentType: String? = null,
    ): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            doInput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            contentType?.let { setRequestProperty("Content-Type", it) }
        }
    }

    private fun readError(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: connection.inputStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return if (body.isBlank()) {
            "Google Drive request failed with HTTP ${connection.responseCode}"
        } else {
            body
        }
    }
}
