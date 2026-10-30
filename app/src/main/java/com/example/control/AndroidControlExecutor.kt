package com.example.control

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class PendingSensitiveAction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val actionType: SensitiveActionType,
    val targetValue: String,
    val payloadText: String = ""
)

enum class SensitiveActionType {
    DIAL_NUMBER,
    COMPOSE_SMS,
    OPEN_URL,
    SHARE_TEXT,
    LAUNCH_APP
}

data class WebSearchResultItem(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

data class SelectedFilePayload(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val bitmap: Bitmap? = null,
    val extractedText: String? = null,
    val sizeBytes: Long = 0L
)

data class LocalCommandOutcome(
    val handledOffline: Boolean,
    val spokenResponse: String,
    val pendingSensitiveAction: PendingSensitiveAction? = null
)

class AndroidControlExecutor(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "AndroidControlExecutor"
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getBatteryStatusSummary(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        return if (pct >= 0) {
            "Battery is at $pct% (${if (isCharging) "Charging" else "On Battery"})."
        } else {
            "Battery level unavailable."
        }
    }

    fun getCurrentDateTimeSummary(): String {
        val now = Date()
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
        return "It is currently $timeFmt on $dateFmt."
    }

    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isNotEmpty() && pkg != context.packageName) {
                    InstalledAppInfo(appName = label, packageName = pkg)
                } else {
                    null
                }
            }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase(Locale.getDefault()) }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps", e)
            emptyList()
        }
    }

    fun launchAppByPackage(packageName: String): Result<String> {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Result.success("Launched application ($packageName).")
            } else {
                Result.failure(IllegalStateException("App cannot be launched directly."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            Result.failure(e)
        }
    }

    fun launchAppByName(queryName: String): Result<String> {
        val clean = queryName.trim().lowercase(Locale.getDefault())
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("App name is empty."))

        val installed = getInstalledLaunchableApps()
        val matched = installed.firstOrNull { it.appName.lowercase(Locale.getDefault()) == clean }
            ?: installed.firstOrNull { it.appName.lowercase(Locale.getDefault()).contains(clean) }
            ?: installed.firstOrNull { it.packageName.lowercase(Locale.getDefault()).contains(clean) }

        if (matched != null) {
            return launchAppByPackage(matched.packageName).map {
                "Opening ${matched.appName}."
            }
        }

        // Common system app fallbacks
        val systemFallbackIntent = when {
            clean.contains("setting") || clean.contains("सेटिंग") -> Intent(Settings.ACTION_SETTINGS)
            clean.contains("wifi") || clean.contains("wi-fi") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            clean.contains("bluetooth") || clean.contains("ब्लूटूथ") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            clean.contains("browser") || clean.contains("chrome") -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            clean.contains("calculator") || clean.contains("कैलकुलेटर") -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            clean.contains("calendar") || clean.contains("कैलेंडर") -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            clean.contains("contacts") || clean.contains("संपर्क") -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
            clean.contains("maps") || clean.contains("मैप") -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
            else -> null
        }

        if (systemFallbackIntent != null) {
            return try {
                systemFallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(systemFallbackIntent)
                Result.success("Opened $queryName.")
            } catch (e: Exception) {
                Result.failure(IllegalStateException("Could not find or launch '$queryName' on this device."))
            }
        }

        return Result.failure(IllegalStateException("App '$queryName' was not found on this device."))
    }

    fun openSystemSettings(settingKey: String): Result<String> {
        val action = when (settingKey.lowercase(Locale.getDefault())) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications", "notification_listener" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "overlay" -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            "app_details" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION ||
                    action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                ) {
                    data = Uri.parse("package:${context.packageName}")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opened Android $settingKey settings.")
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Result.success("Opened main Android Settings.")
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        }
    }

    fun setSystemTimer(seconds: Int, label: String): Result<String> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS Timer" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.success("System timer intent launched for ${seconds / 60}m ${seconds % 60}s ($label). Also tracked inside JARVIS.")
            } else {
                Result.success("Saved timer '$label' inside JARVIS Reminders & Timers tracker (no external Clock app handler found).")
            }
        } catch (e: Exception) {
            Result.success("Saved timer '$label' inside JARVIS local tracker.")
        }
    }

    fun setSystemAlarm(hour24: Int, minute: Int, label: String): Result<String> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.success("System alarm dialog opened for %02d:%02d (%s).".format(hour24, minute, label))
            } else {
                Result.success("Saved alarm for %02d:%02d (%s) in JARVIS local reminders.".format(hour24, minute, label))
            }
        } catch (e: Exception) {
            Result.success("Saved alarm for %02d:%02d (%s) in JARVIS local reminders.".format(hour24, minute, label))
        }
    }

    fun executeConfirmedSensitiveAction(action: PendingSensitiveAction): Result<String> {
        return try {
            when (action.actionType) {
                SensitiveActionType.DIAL_NUMBER -> {
                    val cleanNumber = action.targetValue.filter { it.isDigit() || it == '+' }
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Result.success("Opened Phone Dialer with $cleanNumber.")
                }
                SensitiveActionType.COMPOSE_SMS -> {
                    val cleanNumber = action.targetValue.filter { it.isDigit() || it == '+' }
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanNumber")).apply {
                        putExtra("sms_body", action.payloadText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Result.success("Opened SMS composer for $cleanNumber.")
                }
                SensitiveActionType.OPEN_URL -> {
                    val formattedUrl = if (action.targetValue.startsWith("http://") || action.targetValue.startsWith("https://")) {
                        action.targetValue
                    } else {
                        "https://${action.targetValue}"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Result.success("Opened $formattedUrl in browser.")
                }
                SensitiveActionType.SHARE_TEXT -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, action.payloadText.ifBlank { action.targetValue })
                    }
                    val chooser = Intent.createChooser(sendIntent, "Share via JARVIS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    Result.success("Opened Android Share Sheet.")
                }
                SensitiveActionType.LAUNCH_APP -> {
                    launchAppByName(action.targetValue)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sensitive action failed", e)
            Result.failure(e)
        }
    }

    /**
     * Evaluates whether a command can be handled immediately by the Offline/Local Device Engine
     * (time, date, battery, opening apps, opening settings, setting timers/alarms, dialing confirmation).
     */
    fun tryHandleLocalCommand(rawText: String): LocalCommandOutcome {
        val text = rawText.trim()
        val lower = text.lowercase(Locale.getDefault())

        // 1. Time / Date queries
        if (lower in listOf("time", "what time is it", "current time", "date", "what is the date", "आज की तारीख", "समय क्या है", "kya time hua hai", "time kya hai")) {
            return LocalCommandOutcome(
                handledOffline = true,
                spokenResponse = "${getCurrentDateTimeSummary()} ${getBatteryStatusSummary()}"
            )
        }

        // 2. Battery status
        if (lower.contains("battery status") || lower == "battery" || lower.contains("बैटरी")) {
            return LocalCommandOutcome(
                handledOffline = true,
                spokenResponse = getBatteryStatusSummary()
            )
        }

        // 3. Open Settings
        if (lower.startsWith("open ") && lower.contains("setting")) {
            val sub = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("display") || lower.contains("brightness") -> "display"
                lower.contains("sound") || lower.contains("volume") -> "sound"
                lower.contains("accessibility") -> "accessibility"
                lower.contains("notification") -> "notifications"
                lower.contains("overlay") -> "overlay"
                else -> "general"
            }
            val res = openSystemSettings(sub)
            return LocalCommandOutcome(
                handledOffline = true,
                spokenResponse = res.getOrElse { "Unable to open Android settings: ${it.localizedMessage}" }
            )
        }

        // 4. Open App ("open chrome", "launch calculator", "चलाओ ...")
        val openPrefixes = listOf("open app ", "open ", "launch ", "start app ")
        val matchedPrefix = openPrefixes.firstOrNull { lower.startsWith(it) }
        if (matchedPrefix != null && !lower.contains("http") && !lower.contains(".com")) {
            val targetApp = text.substring(matchedPrefix.length).trim()
            if (targetApp.isNotBlank() && targetApp.split(" ").size <= 4) {
                val res = launchAppByName(targetApp)
                if (res.isSuccess) {
                    return LocalCommandOutcome(
                        handledOffline = true,
                        spokenResponse = res.getOrThrow()
                    )
                }
            }
        }

        // 5. Call / Dial phone number -> Requires explicit user confirmation
        val callRegex = Regex("""(?:call|dial)\s+([+\d][\d\s\-]{3,})""", RegexOption.IGNORE_CASE)
        val callMatch = callRegex.find(text)
        if (callMatch != null) {
            val phone = callMatch.groupValues[1].trim()
            val pending = PendingSensitiveAction(
                title = "Confirm Phone Dial",
                description = "JARVIS requests confirmation before opening the dialer for $phone.",
                actionType = SensitiveActionType.DIAL_NUMBER,
                targetValue = phone
            )
            return LocalCommandOutcome(
                handledOffline = true,
                spokenResponse = "Please confirm on screen before I open the phone dialer for $phone.",
                pendingSensitiveAction = pending
            )
        }

        return LocalCommandOutcome(handledOffline = false, spokenResponse = "")
    }

    /**
     * Reads a user-selected file (Image, PDF page, or Text document) via Storage Access Framework Uri.
     */
    suspend fun loadUserSelectedFile(uri: Uri): Result<SelectedFilePayload> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var displayName = "selected_file"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            if (sizeBytes > 20L * 1024L * 1024L) {
                return@withContext Result.failure(
                    IllegalArgumentException("File exceeds 20 MB inline multimodal limit (${sizeBytes / (1024 * 1024)} MB).")
                )
            }

            when {
                mimeType.startsWith("image/") -> {
                    val bitmap = resolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: return@withContext Result.failure(IllegalStateException("Could not decode selected image."))
                    // Scale down if very large to keep memory and payload fast
                    val scaled = scaleBitmapIfNeeded(bitmap, 1280)
                    Result.success(
                        SelectedFilePayload(
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            bitmap = scaled,
                            sizeBytes = sizeBytes
                        )
                    )
                }
                mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true) -> {
                    val pfd = resolver.openFileDescriptor(uri, "r")
                        ?: return@withContext Result.failure(IllegalStateException("Cannot open PDF file descriptor."))
                    pfd.use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            if (renderer.pageCount <= 0) {
                                return@withContext Result.failure(IllegalStateException("PDF has 0 pages."))
                            }
                            val pageCount = renderer.pageCount
                            renderer.openPage(0).use { page ->
                                val width = (page.width * 2).coerceIn(600, 1600)
                                val height = (page.height * 2).coerceIn(800, 2200)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                Result.success(
                                    SelectedFilePayload(
                                        uri = uri,
                                        displayName = "$displayName (Page 1 of $pageCount)",
                                        mimeType = "application/pdf",
                                        bitmap = bmp,
                                        extractedText = "PDF Document '$displayName' ($pageCount pages). First page rendered for visual inspection.",
                                        sizeBytes = sizeBytes
                                    )
                                )
                            }
                        }
                    }
                }
                mimeType.startsWith("text/") ||
                    displayName.endsWith(".txt", true) ||
                    displayName.endsWith(".md", true) ||
                    displayName.endsWith(".json", true) ||
                    displayName.endsWith(".csv", true) -> {
                    val sb = StringBuilder()
                    resolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                            var line: String?
                            var chars = 0
                            while (reader.readLine().also { line = it } != null && chars < 24_000) {
                                sb.append(line).append("\n")
                                chars += (line?.length ?: 0)
                            }
                        }
                    }
                    Result.success(
                        SelectedFilePayload(
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            extractedText = sb.toString(),
                            sizeBytes = sizeBytes
                        )
                    )
                }
                else -> {
                    Result.failure(
                        IllegalArgumentException("Unsupported file format ($mimeType). Please select an Image, Screenshot, PDF, or Text/Markdown/CSV/JSON document.")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read selected file", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimension && h <= maxDimension) return source
        val ratio = w.toFloat() / h.toFloat()
        val (newW, newH) = if (w >= h) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /**
     * Performs verified Web Research using Wikipedia OpenSearch API & DuckDuckGo Instant Answer API.
     * Never fabricates links or citations.
     */
    suspend fun searchVerifiedWebSources(query: String): Result<List<WebSearchResultItem>> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(IllegalStateException("Device is offline. Web search is unavailable without internet."))
        }
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Search query cannot be empty."))
        }

        val results = mutableListOf<WebSearchResultItem>()
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

            // 1. DuckDuckGo Instant Answer API
            val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val ddgReq = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                .build()

            httpClient.newCall(ddgReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText")
                        val abstractUrl = json.optString("AbstractURL")
                        val heading = json.optString("Heading").ifBlank { cleanQuery }
                        if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                            results.add(
                                WebSearchResultItem(
                                    title = heading,
                                    snippet = abstractText,
                                    url = abstractUrl,
                                    source = json.optString("AbstractSource", "DuckDuckGo Instant Answer")
                                )
                            )
                        }
                        val related = json.optJSONArray("RelatedTopics") ?: JSONArray()
                        for (i in 0 until minOf(related.length(), 4)) {
                            val item = related.optJSONObject(i) ?: continue
                            val text = item.optString("Text")
                            val firstUrl = item.optString("FirstURL")
                            if (text.isNotBlank() && firstUrl.isNotBlank()) {
                                results.add(
                                    WebSearchResultItem(
                                        title = text.take(65) + if (text.length > 65) "..." else "",
                                        snippet = text,
                                        url = firstUrl,
                                        source = "DuckDuckGo Reference"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Wikipedia OpenSearch API
            val wikiUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=4&namespace=0&format=json"
            val wikiReq = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                .build()

            httpClient.newCall(wikiReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val arr = JSONArray(body)
                        val titles = arr.optJSONArray(1) ?: JSONArray()
                        val descriptions = arr.optJSONArray(2) ?: JSONArray()
                        val urls = arr.optJSONArray(3) ?: JSONArray()
                        for (i in 0 until titles.length()) {
                            val t = titles.optString(i)
                            val d = descriptions.optString(i).ifBlank { "Verified Wikipedia entry for $t" }
                            val u = urls.optString(i)
                            if (t.isNotBlank() && u.isNotBlank() && results.none { it.url == u }) {
                                results.add(
                                    WebSearchResultItem(
                                        title = t,
                                        snippet = d,
                                        url = u,
                                        source = "Wikipedia"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Web search failed", e)
            Result.failure(e)
        }
    }
}
