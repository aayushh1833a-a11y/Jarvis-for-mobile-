package com.example.integrations

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.security.SecureVault
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class GoogleServiceStatus(
    val serviceId: String,
    val displayName: String,
    val isConnected: Boolean,
    val requiredScopes: List<String>,
    val accessSummary: String
)

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val webViewLink: String,
    val isFolder: Boolean
)

data class CalendarEventSummary(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val htmlLink: String
)

data class GmailMessageSummary(
    val id: String,
    val from: String,
    val subject: String,
    val date: String,
    val snippet: String
)

data class GoogleContactItem(
    val displayName: String,
    val email: String,
    val phone: String
)

data class YouTubePlaylistItem(
    val id: String,
    val title: String,
    val description: String,
    val itemCount: Int
)

sealed interface GoogleAuthOutcome {
    data class Connected(val email: String, val grantedScopes: Set<String>) : GoogleAuthOutcome
    data class NeedsConsentResolution(val email: String, val consentIntent: Intent) : GoogleAuthOutcome
    data class Error(val message: String) : GoogleAuthOutcome
}

class GoogleWorkspaceClient(
    private val context: Context,
    private val secureVault: SecureVault
) {
    companion object {
        private const val TAG = "GoogleWorkspaceClient"

        const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        const val SCOPE_GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
        const val SCOPE_GMAIL_COMPOSE = "https://www.googleapis.com/auth/gmail.compose"
        const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        const val SCOPE_CONTACTS_READONLY = "https://www.googleapis.com/auth/contacts.readonly"
        const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"

        val ALL_WORKSPACE_SCOPES = listOf(
            SCOPE_DRIVE,
            SCOPE_GMAIL_READONLY,
            SCOPE_GMAIL_COMPOSE,
            SCOPE_CALENDAR_EVENTS,
            SCOPE_CONTACTS_READONLY,
            SCOPE_YOUTUBE_READONLY
        )
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    fun getServiceStatuses(): List<GoogleServiceStatus> {
        val email = secureVault.googleConnectedEmail
        val hasToken = secureVault.googleAccessToken.isNotBlank() &&
            (secureVault.googleTokenExpiryMillis == 0L || System.currentTimeMillis() < secureVault.googleTokenExpiryMillis)
        val granted = secureVault.googleGrantedScopes
        val isAccountActive = email.isNotBlank() && hasToken

        return listOf(
            GoogleServiceStatus(
                serviceId = "drive",
                displayName = "Google Drive",
                isConnected = isAccountActive && (SCOPE_DRIVE in granted || granted.any { it.contains("drive") }),
                requiredScopes = listOf(SCOPE_DRIVE),
                accessSummary = "Search files by name/metadata, list folders, read & summarize documents/PDFs, upload user-selected files, create folders, rename/move, and download files. Deleting or overwriting always requires your explicit confirmation."
            ),
            GoogleServiceStatus(
                serviceId = "gmail",
                displayName = "Gmail",
                isConnected = isAccountActive && (SCOPE_GMAIL_READONLY in granted || SCOPE_GMAIL_COMPOSE in granted || granted.any { it.contains("gmail") }),
                requiredScopes = listOf(SCOPE_GMAIL_READONLY, SCOPE_GMAIL_COMPOSE),
                accessSummary = "Search emails from selected senders, read message snippets, and create drafts with mandatory confirmation."
            ),
            GoogleServiceStatus(
                serviceId = "calendar",
                displayName = "Google Calendar",
                isConnected = isAccountActive && (SCOPE_CALENDAR_EVENTS in granted || granted.any { it.contains("calendar") }),
                requiredScopes = listOf(SCOPE_CALENDAR_EVENTS),
                accessSummary = "Read upcoming events from your primary Google Calendar."
            ),
            GoogleServiceStatus(
                serviceId = "contacts",
                displayName = "Google Contacts",
                isConnected = isAccountActive && (SCOPE_CONTACTS_READONLY in granted || granted.any { it.contains("contacts") }),
                requiredScopes = listOf(SCOPE_CONTACTS_READONLY),
                accessSummary = "Read-only lookup of contact names, emails, and phone numbers when requested."
            ),
            GoogleServiceStatus(
                serviceId = "youtube",
                displayName = "YouTube",
                isConnected = isAccountActive && (SCOPE_YOUTUBE_READONLY in granted || granted.any { it.contains("youtube") }),
                requiredScopes = listOf(SCOPE_YOUTUBE_READONLY),
                accessSummary = "Read-only access to view your YouTube playlists."
            ),
            GoogleServiceStatus(
                serviceId = "photos",
                displayName = "Google Photos (Android Photo Picker)",
                isConnected = true,
                requiredScopes = listOf("Android Photo Picker (Zero-Permission)"),
                accessSummary = "Uses Android's official zero-permission Photo Picker so you choose exact photos to analyze without granting broad library access."
            )
        )
    }

    /**
     * Signs in the user via Jetpack CredentialManager (GetSignInWithGoogleOption) and requests
     * an OAuth 2.0 access token for the requested Google Workspace scopes.
     */
    suspend fun connectGoogleAccount(
        activity: Activity,
        requestedScopes: List<String> = ALL_WORKSPACE_SCOPES
    ): GoogleAuthOutcome {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return GoogleAuthOutcome.Error("Google Sign-In configuration missing: default_web_client_id not found.")
        }

        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdCred.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val email = authResult.user?.email ?: googleIdCred.id
                secureVault.googleConnectedEmail = email
                completeConsentResolution(email, requestedScopes)
            } else {
                GoogleAuthOutcome.Error("Unexpected credential type returned by Credential Manager.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleAuthOutcome.Error("Google Sign-In was cancelled or dismissed.")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleAuthOutcome.Error(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }

    /**
     * Uses Android's built-in AccountManager to request an `oauth2:` access token for the
     * authorized Google account, returning `NeedsConsentResolution(consentIntent)` if user
     * approval is required for the Workspace scopes.
     */
    @Suppress("DEPRECATION")
    suspend fun completeConsentResolution(
        email: String,
        requestedScopes: List<String> = ALL_WORKSPACE_SCOPES
    ): GoogleAuthOutcome = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext GoogleAuthOutcome.Error("No Google account email selected.")
        }
        val scopeTokenType = "oauth2:${requestedScopes.joinToString(" ")}"
        try {
            val accountManager = AccountManager.get(context)
            val account = Account(email, "com.google")
            val future = accountManager.getAuthToken(account, scopeTokenType, null, false, null, null)
            val bundle = future.result
            val consentIntent = bundle.getParcelable<Intent>(AccountManager.KEY_INTENT)
            if (consentIntent != null) {
                return@withContext GoogleAuthOutcome.NeedsConsentResolution(email, consentIntent)
            }
            val token = bundle.getString(AccountManager.KEY_AUTHTOKEN).orEmpty()
            if (token.isNotBlank()) {
                secureVault.googleConnectedEmail = email
                secureVault.googleAccessToken = token
                secureVault.googleTokenExpiryMillis = System.currentTimeMillis() + 50 * 60 * 1000L
                secureVault.googleGrantedScopes = requestedScopes.toSet()
                GoogleAuthOutcome.Connected(email, requestedScopes.toSet())
            } else {
                GoogleAuthOutcome.Error(
                    "Signed in as $email, but no OAuth 2.0 Workspace token was returned by device AccountManager. If testing on a headless emulator, you can verify a token via 'OAuth Token Fallback'."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AccountManager OAuth token fetch note: ${e.localizedMessage}")
            GoogleAuthOutcome.Error(
                "Signed in as $email. To authorize Workspace APIs on an emulator without Play AccountManager access, use 'OAuth Token Fallback' or grant account access on device."
            )
        }
    }

    /**
     * Validates an OAuth 2.0 Access Token against Google's official tokeninfo endpoint
     * and stores it encrypted in Android Keystore.
     */
    suspend fun saveAndVerifyManualOAuthToken(rawToken: String): Result<String> = withContext(Dispatchers.IO) {
        val clean = rawToken.trim().removePrefix("Bearer ").trim()
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("OAuth Access Token is empty."))
        }
        try {
            val url = "https://oauth2.googleapis.com/tokeninfo?access_token=${URLEncoder.encode(clean, "UTF-8")}"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Google OAuth token validation failed (HTTP ${resp.code}). The token may be expired or invalid.")
                    )
                }
                val json = JSONObject(body)
                val email = json.optString("email").ifBlank { secureVault.googleConnectedEmail.ifBlank { "Authorized Google User" } }
                val scopeStr = json.optString("scope")
                val expiresInSec = json.optLong("expires_in", 3600L)
                val scopesSet = scopeStr.split(" ").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

                secureVault.googleConnectedEmail = email
                secureVault.googleAccessToken = clean
                secureVault.googleTokenExpiryMillis = System.currentTimeMillis() + (expiresInSec * 1000L)
                if (scopesSet.isNotEmpty()) {
                    secureVault.googleGrantedScopes = scopesSet
                } else {
                    secureVault.googleGrantedScopes = ALL_WORKSPACE_SCOPES.toSet()
                }
                Result.success("Verified OAuth 2.0 token for $email (${secureVault.googleGrantedScopes.size} scopes active, expires in ${expiresInSec / 60}m).")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testGoogleConnection(): Result<String> = withContext(Dispatchers.IO) {
        val token = secureVault.googleAccessToken
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No active Google OAuth 2.0 token found. Tap 'Connect Google Account' first."))
        }
        saveAndVerifyManualOAuthToken(token)
    }

    suspend fun disconnectGoogleServices(): Result<String> = withContext(Dispatchers.IO) {
        val token = secureVault.googleAccessToken
        if (token.isNotBlank()) {
            try {
                val revokeUrl = "https://oauth2.googleapis.com/revoke?token=${URLEncoder.encode(token, "UTF-8")}"
                val req = Request.Builder()
                    .url(revokeUrl)
                    .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w(TAG, "Token revoke request note: ${e.localizedMessage}")
            }
            try {
                AccountManager.get(context).invalidateAuthToken("com.google", token)
            } catch (_: Exception) {}
        }

        secureVault.clearGoogleOAuthCredentials()
        try {
            Firebase.auth.signOut()
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing credential state", e)
        }
        Result.success("Disconnected Google Workspace account and revoked stored OAuth token.")
    }

    private suspend fun getValidAccessTokenOrThrow(): String {
        val existing = secureVault.googleAccessToken
        val expiry = secureVault.googleTokenExpiryMillis
        if (existing.isNotBlank() && (expiry == 0L || System.currentTimeMillis() < expiry)) {
            return existing
        }
        val email = secureVault.googleConnectedEmail
        if (email.isNotBlank()) {
            val outcome = completeConsentResolution(email, ALL_WORKSPACE_SCOPES)
            if (outcome is GoogleAuthOutcome.Connected && secureVault.googleAccessToken.isNotBlank()) {
                return secureVault.googleAccessToken
            }
        }
        if (existing.isNotBlank()) return existing
        throw IllegalStateException("Google Workspace is not connected or OAuth token has expired. Open Settings → Integrations to connect your Google Account.")
    }

    // =========================================================================
    // 1. GOOGLE DRIVE REST API v3 OPERATIONS
    // =========================================================================

    suspend fun searchAndListDriveFiles(
        nameQuery: String = "",
        mimeTypeFilter: String? = null,
        parentFolderId: String? = null
    ): Result<List<DriveFileItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val clauses = mutableListOf("trashed = false")
            if (nameQuery.isNotBlank()) {
                val safe = nameQuery.replace("'", "\\'")
                clauses.add("name contains '$safe'")
            }
            if (!mimeTypeFilter.isNullOrBlank()) {
                clauses.add("mimeType = '${mimeTypeFilter.replace("'", "")}'")
            }
            if (!parentFolderId.isNullOrBlank()) {
                clauses.add("'${parentFolderId.replace("'", "")}' in parents")
            }

            val qEncoded = URLEncoder.encode(clauses.joinToString(" and "), "UTF-8")
            val fields = URLEncoder.encode("files(id,name,mimeType,size,modifiedTime,webViewLink,parents)", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$qEncoded&fields=$fields&pageSize=25&orderBy=modifiedTime%20desc"

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Google Drive search"))
                }
                val filesArr = JSONObject(body).optJSONArray("files") ?: JSONArray()
                val items = (0 until filesArr.length()).map { i ->
                    val obj = filesArr.getJSONObject(i)
                    val mime = obj.optString("mimeType", "application/octet-stream")
                    DriveFileItem(
                        id = obj.optString("id"),
                        name = obj.optString("name", "Untitled"),
                        mimeType = mime,
                        sizeBytes = obj.optLong("size", 0L),
                        modifiedTime = obj.optString("modifiedTime", ""),
                        webViewLink = obj.optString("webViewLink", ""),
                        isFolder = mime == "application/vnd.google-apps.folder"
                    )
                }
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads a Google Drive document, spreadsheet, presentation, text file, or PDF
     * so JARVIS can summarize it with Gemini.
     */
    suspend fun readDriveDocumentText(fileItem: DriveFileItem): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val mime = fileItem.mimeType

            when {
                mime == "application/vnd.google-apps.document" ||
                    mime == "application/vnd.google-apps.spreadsheet" ||
                    mime == "application/vnd.google-apps.presentation" -> {
                    val exportMime = if (mime.contains("spreadsheet")) "text/csv" else "text/plain"
                    val url = "https://www.googleapis.com/drive/v3/files/${fileItem.id}/export?mimeType=${URLEncoder.encode(exportMime, "UTF-8")}"
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            return@withContext Result.failure(parseGoogleApiError(resp.code, text, "Drive Export"))
                        }
                        Result.success(text.take(24_000))
                    }
                }

                mime == "application/pdf" || fileItem.name.endsWith(".pdf", ignoreCase = true) -> {
                    val url = "https://www.googleapis.com/drive/v3/files/${fileItem.id}?alt=media"
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            val errBody = resp.body?.string().orEmpty()
                            return@withContext Result.failure(parseGoogleApiError(resp.code, errBody, "Drive PDF Download"))
                        }
                        val bytes = resp.body?.bytes()
                            ?: return@withContext Result.failure(IllegalStateException("Empty PDF bytes from Drive."))
                        val localPdfFile = File(context.cacheDir, "drive_${fileItem.id.take(12)}.pdf")
                        FileOutputStream(localPdfFile).use { it.write(bytes) }

                        var pageCount = 0
                        ParcelFileDescriptor.open(localPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                pageCount = renderer.pageCount
                            }
                        }

                        val rawAscii = String(bytes, Charsets.ISO_8859_1)
                            .replace(Regex("[^\\x20-\\x7E\\r\\n]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .take(12_000)

                        Result.success(
                            "Google Drive PDF Document: '${fileItem.name}' ($pageCount pages, ${bytes.size / 1024} KB).\n" +
                                "Extracted PDF Stream Text Snippet:\n$rawAscii"
                        )
                    }
                }

                else -> {
                    val url = "https://www.googleapis.com/drive/v3/files/${fileItem.id}?alt=media"
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            return@withContext Result.failure(parseGoogleApiError(resp.code, text, "Drive File Read"))
                        }
                        Result.success(text.take(24_000))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDriveFolder(folderName: String, parentFolderId: String? = null): Result<DriveFileItem> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val cleanName = folderName.trim().ifBlank { "JARVIS Folder" }
            val meta = JSONObject()
                .put("name", cleanName)
                .put("mimeType", "application/vnd.google-apps.folder")
            if (!parentFolderId.isNullOrBlank()) {
                meta.put("parents", JSONArray().put(parentFolderId))
            }

            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,webViewLink")
                .header("Authorization", "Bearer $token")
                .post(meta.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Create Drive Folder"))
                }
                val json = JSONObject(body)
                Result.success(
                    DriveFileItem(
                        id = json.optString("id"),
                        name = json.optString("name", cleanName),
                        mimeType = "application/vnd.google-apps.folder",
                        sizeBytes = 0L,
                        modifiedTime = "Just now",
                        webViewLink = json.optString("webViewLink"),
                        isFolder = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameDriveFile(fileId: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val patchJson = JSONObject().put("name", newName.trim())
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .patch(patchJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Rename Drive File"))
                }
                val updatedName = JSONObject(body).optString("name", newName)
                Result.success("Renamed Google Drive file to '$updatedName'.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadUserSelectedFileToDrive(
        uri: Uri,
        parentFolderId: String? = null
    ): Result<DriveFileItem> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var fileName = "jarvis_upload_${System.currentTimeMillis()}"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }

            val fileBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not read selected local file."))

            val boundary = "jarvis_drive_boundary_${System.currentTimeMillis()}"
            val meta = JSONObject().put("name", fileName)
            if (!parentFolderId.isNullOrBlank()) {
                meta.put("parents", JSONArray().put(parentFolderId))
            }

            val preamble = (
                "--$boundary\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                    "${meta}\r\n" +
                    "--$boundary\r\n" +
                    "Content-Type: $mimeType\r\n\r\n"
                ).toByteArray(Charsets.UTF_8)
            val epilogue = "\r\n--$boundary--".toByteArray(Charsets.UTF_8)
            val multipartBytes = preamble + fileBytes + epilogue

            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size,webViewLink")
                .header("Authorization", "Bearer $token")
                .post(multipartBytes.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Drive Upload"))
                }
                val json = JSONObject(body)
                Result.success(
                    DriveFileItem(
                        id = json.optString("id"),
                        name = json.optString("name", fileName),
                        mimeType = json.optString("mimeType", mimeType),
                        sizeBytes = json.optLong("size", fileBytes.size.toLong()),
                        modifiedTime = "Uploaded just now",
                        webViewLink = json.optString("webViewLink"),
                        isFolder = false
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadDriveFileToCache(fileItem: DriveFileItem): Result<File> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val url = if (fileItem.mimeType.startsWith("application/vnd.google-apps.")) {
                "https://www.googleapis.com/drive/v3/files/${fileItem.id}/export?mimeType=application/pdf"
            } else {
                "https://www.googleapis.com/drive/v3/files/${fileItem.id}?alt=media"
            }

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    return@withContext Result.failure(parseGoogleApiError(resp.code, err, "Drive Download"))
                }
                val bytes = resp.body?.bytes()
                    ?: return@withContext Result.failure(IllegalStateException("Downloaded file is empty."))
                val safeName = fileItem.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val outDir = context.getExternalFilesDir(null) ?: context.filesDir
                val outFile = File(outDir, safeName)
                FileOutputStream(outFile).use { it.write(bytes) }
                Result.success(outFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDriveFileConfirmed(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Delete Drive File"))
                }
                Result.success("Deleted file ($fileId) from Google Drive.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. GOOGLE CALENDAR REST API v3 OPERATIONS
    // =========================================================================

    suspend fun listUpcomingCalendarEvents(maxResults: Int = 10): Result<List<CalendarEventSummary>> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = URLEncoder.encode(isoFmt.format(Date()), "UTF-8")
            val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=$nowIso&maxResults=$maxResults&singleEvents=true&orderBy=startTime"

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Google Calendar"))
                }
                val itemsArr = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val events = (0 until itemsArr.length()).map { i ->
                    val obj = itemsArr.getJSONObject(i)
                    val startObj = obj.optJSONObject("start")
                    val endObj = obj.optJSONObject("end")
                    CalendarEventSummary(
                        id = obj.optString("id"),
                        title = obj.optString("summary", "(No Title)"),
                        startTime = startObj?.optString("dateTime")?.ifBlank { startObj.optString("date") }.orEmpty(),
                        endTime = endObj?.optString("dateTime")?.ifBlank { endObj.optString("date") }.orEmpty(),
                        location = obj.optString("location", ""),
                        htmlLink = obj.optString("htmlLink", "")
                    )
                }
                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. GMAIL REST API v1 OPERATIONS
    // =========================================================================

    suspend fun searchGmailMessages(query: String = "", maxResults: Int = 6): Result<List<GmailMessageSummary>> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val qParam = if (query.isNotBlank()) "&q=${URLEncoder.encode(query, "UTF-8")}" else ""
            val listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=$maxResults$qParam"

            val listReq = Request.Builder()
                .url(listUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val msgIds = mutableListOf<String>()
            httpClient.newCall(listReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Gmail Search"))
                }
                val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank()) msgIds.add(id)
                }
            }

            val detailed = mutableListOf<GmailMessageSummary>()
            for (msgId in msgIds) {
                val detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date"
                val detailReq = Request.Builder()
                    .url(detailUrl)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                httpClient.newCall(detailReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        val snippet = obj.optString("snippet", "")
                        val headers = obj.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
                        var subject = "(No Subject)"
                        var from = ""
                        var date = ""
                        for (h in 0 until headers.length()) {
                            val hObj = headers.getJSONObject(h)
                            when (hObj.optString("name")) {
                                "Subject" -> subject = hObj.optString("value", subject)
                                "From" -> from = hObj.optString("value", from)
                                "Date" -> date = hObj.optString("value", date)
                            }
                        }
                        detailed.add(
                            GmailMessageSummary(
                                id = msgId,
                                from = from,
                                subject = subject,
                                date = date,
                                snippet = snippet
                            )
                        )
                    }
                }
            }
            Result.success(detailed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGmailDraftConfirmed(to: String, subject: String, bodyText: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val rawEmail = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n$bodyText"
            val encodedRaw = Base64.encodeToString(
                rawEmail.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            val payload = JSONObject().put("message", JSONObject().put("raw", encodedRaw))
            val req = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/me/drafts")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, respBody, "Create Gmail Draft"))
                }
                Result.success("Created Gmail draft to $to ('$subject').")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. GOOGLE CONTACTS (PEOPLE API v1) & YOUTUBE DATA API v3
    // =========================================================================

    suspend fun searchGoogleContacts(query: String = ""): Result<List<GoogleContactItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,phoneNumbers&pageSize=50"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "Google Contacts"))
                }
                val conns = JSONObject(body).optJSONArray("connections") ?: JSONArray()
                val contacts = mutableListOf<GoogleContactItem>()
                for (i in 0 until conns.length()) {
                    val person = conns.getJSONObject(i)
                    val name = person.optJSONArray("names")?.optJSONObject(0)?.optString("displayName").orEmpty()
                    val email = person.optJSONArray("emailAddresses")?.optJSONObject(0)?.optString("value").orEmpty()
                    val phone = person.optJSONArray("phoneNumbers")?.optJSONObject(0)?.optString("value").orEmpty()
                    if (name.isNotBlank() || email.isNotBlank() || phone.isNotBlank()) {
                        contacts.add(GoogleContactItem(displayName = name.ifBlank { email }, email = email, phone = phone))
                    }
                }
                val filtered = if (query.isBlank()) contacts else contacts.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                        it.email.contains(query, ignoreCase = true) ||
                        it.phone.contains(query, ignoreCase = true)
                }
                Result.success(filtered)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listYouTubePlaylists(): Result<List<YouTubePlaylistItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessTokenOrThrow()
            val url = "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&mine=true&maxResults=15"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(parseGoogleApiError(resp.code, body, "YouTube API"))
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val list = (0 until items.length()).map { i ->
                    val obj = items.getJSONObject(i)
                    val snippet = obj.optJSONObject("snippet")
                    val details = obj.optJSONObject("contentDetails")
                    YouTubePlaylistItem(
                        id = obj.optString("id"),
                        title = snippet?.optString("title", "Untitled Playlist").orEmpty(),
                        description = snippet?.optString("description", "").orEmpty(),
                        itemCount = details?.optInt("itemCount", 0) ?: 0
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGoogleApiError(code: Int, rawBody: String, contextLabel: String): Exception {
        val message = try {
            JSONObject(rawBody).optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        } ?: "HTTP $code"
        if (code == 401 || code == 403) {
            return IllegalStateException("$contextLabel authorization error ($message). Please reconnect or refresh your Google OAuth token in Integrations.")
        }
        return IllegalStateException("$contextLabel failed: $message")
    }
}
