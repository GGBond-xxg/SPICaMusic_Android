package me.spica27.spicamusic.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.util.Log
import me.spica27.spicamusic.BuildConfig
import timber.log.Timber
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes the diagnostic build's lifecycle and playback log to the public Download directory. */
object DiagnosticLog {
    private const val LOG_TAG = "SPICaDiagnostic"
    private const val FILE_PREFIX = "SPICaMusic"
    private val lock = Any()
    private val timestampFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
    private val traceFileTimestampFormatter =
        DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault())
    private var application: Application? = null
    private var writer: BufferedWriter? = null
    private var writerDate: LocalDate? = null
    private var callbacksRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var systemEventsRegistered = false
    private var networkCallbackRegistered = false
    private val heartbeat =
        object : Runnable {
            override fun run() {
                application?.let { writeRuntimeSnapshot(it, "heartbeat") }
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }

    val timberTree: Timber.Tree =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                write(priorityName(priority), tag ?: "Timber", message, t)
            }
        }

    fun initialize(app: Application) {
        if (!BuildConfig.DIAGNOSTIC_LOGGING) return
        synchronized(lock) {
            application = app
            ensureWriterLocked(LocalDate.now())
            if (!callbacksRegistered) {
                app.registerActivityLifecycleCallbacks(ActivityLogger)
                callbacksRegistered = true
            }
        }
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        write(
            level = "I",
            tag = "DiagnosticLog",
            message =
                "session-start process=${Application.getProcessName()} pid=${android.os.Process.myPid()} " +
                    "version=${packageInfo.versionName}(${packageInfo.longVersionCode}) " +
                    "device=${Build.MANUFACTURER}/${Build.MODEL}/${Build.DEVICE} " +
                    "android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} " +
                    "build=${Build.VERSION.INCREMENTAL}",
        )
        writeHistoricalExitReasons(app)
        writeSystemSnapshot(app)
        writeProcessSnapshot(app, "application-created")
        registerSystemEventLogging(app)
        registerNetworkLogging(app)
        mainHandler.removeCallbacks(heartbeat)
        mainHandler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * A hard process kill cannot run lifecycle callbacks or flush a final in-process event.
     * Android keeps the exit reason outside the process, so read it on the next launch.
     */
    private fun writeHistoricalExitReasons(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = app.getSystemService(ActivityManager::class.java)
        runCatching {
            activityManager
                .getHistoricalProcessExitReasons(app.packageName, 0, MAX_EXIT_REASONS)
                .forEachIndexed { index, info ->
                    write(
                        level = "W",
                        tag = "PreviousExit",
                        message =
                            "index=$index timestamp=${timestampFormatter.format(Instant.ofEpochMilli(info.timestamp))} " +
                                "reason=${exitReasonName(info.reason)}(${info.reason}) status=${info.status} " +
                                "importance=${info.importance} pssKb=${info.pss} rssKb=${info.rss} " +
                                "process=${info.processName} description=${info.description.orEmpty()}",
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val trace = info.traceInputStream
                        if (trace != null) {
                            exportExitTrace(app, info.timestamp, info.reason, trace)
                        } else if (
                            info.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                            info.reason == android.app.ApplicationExitInfo.REASON_ANR
                        ) {
                            write(
                                "W",
                                "PreviousExitTrace",
                                "timestamp=${info.timestamp} reason=${exitReasonName(info.reason)} trace-unavailable",
                            )
                        }
                    }
                }
        }.onFailure { error ->
            write("E", "PreviousExit", "unable-to-read-historical-exit-reasons", error)
        }
    }

    private fun exportExitTrace(
        app: Application,
        timestamp: Long,
        reason: Int,
        traceStream: java.io.InputStream,
    ) {
        runCatching {
            val reasonName = exitReasonName(reason)
            val fileName =
                "$FILE_PREFIX-exit-$reasonName-" +
                    "${traceFileTimestampFormatter.format(Instant.ofEpochMilli(timestamp))}.txt"
            val bytes = traceStream.use { it.readBytes() }
            val uri = findOrCreateDownloadUri(app, fileName, "text/plain")
            requireNotNull(app.contentResolver.openOutputStream(uri, "rwt")).bufferedWriter().use { output ->
                output.appendLine("SPICaMusic Android exit trace (Base64 encoded)")
                output.appendLine("timestamp=$timestamp")
                output.appendLine("reason=$reasonName($reason)")
                output.appendLine(
                    if (reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) {
                        "format=Android Tombstone protobuf"
                    } else {
                        "format=Android system trace"
                    },
                )
                output.append(Base64.encodeToString(bytes, Base64.NO_WRAP))
                output.newLine()
            }
            write(
                "I",
                "PreviousExitTrace",
                "exported=Download/$fileName reason=$reasonName bytes=${bytes.size}",
            )
        }.onFailure { error ->
            write("E", "PreviousExitTrace", "unable-to-export timestamp=$timestamp", error)
        }
    }

    fun write(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!BuildConfig.DIAGNOSTIC_LOGGING) return
        synchronized(lock) {
            if (application == null) return
            ensureWriterLocked(LocalDate.now())
            val target = writer ?: return
            val prefix =
                "${timestampFormatter.format(Instant.now())} $level/$tag " +
                    "[${Thread.currentThread().name}] "
            runCatching {
                redact(message).lineSequence().forEach { line ->
                    target.write(prefix)
                    target.write(line)
                    target.newLine()
                }
                throwable?.stackTraceToString()?.let(::redact)?.lineSequence()?.forEach { line ->
                    target.write(prefix)
                    target.write(line)
                    target.newLine()
                }
                target.flush()
            }.onFailure { error ->
                Log.e(LOG_TAG, "Unable to write diagnostic log", error)
                runCatching { target.close() }
                writer = null
            }
        }
    }

    fun writeProcessSnapshot(
        context: Context,
        event: String,
    ) {
        if (!BuildConfig.DIAGNOSTIC_LOGGING) return
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        write(
            level = "I",
            tag = "Process",
            message =
                "$event importance=${processInfo.importance} importanceReason=${processInfo.importanceReasonCode} " +
                    "backgroundRestricted=${activityManager.isBackgroundRestricted} " +
                    "powerSave=${powerManager.isPowerSaveMode} " +
                    "batteryOptimizationIgnored=${powerManager.isIgnoringBatteryOptimizations(context.packageName)} " +
                    "notifications=${notificationManager.areNotificationsEnabled()} " +
                    "overlay=${Settings.canDrawOverlays(context)}",
        )
    }

    fun writeRuntimeSnapshot(
        context: Context,
        event: String,
    ) {
        if (!BuildConfig.DIAGNOSTIC_LOGGING) return
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val processMemory = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()
        val runtime = Runtime.getRuntime()
        val powerManager = context.getSystemService(PowerManager::class.java)
        write(
            level = "I",
            tag = "Runtime",
            message =
                "$event uptimeMs=${SystemClock.uptimeMillis()} elapsedMs=${SystemClock.elapsedRealtime()} " +
                    "cpuMs=${android.os.Process.getElapsedCpuTime()} importance=${processInfo.importance} " +
                    "pssKb=${processMemory?.totalPss ?: -1} nativePssKb=${processMemory?.nativePss ?: -1} " +
                    "dalvikPssKb=${processMemory?.dalvikPss ?: -1} heapUsedMb=${(runtime.totalMemory() - runtime.freeMemory()) / MIB} " +
                    "heapMaxMb=${runtime.maxMemory() / MIB} systemAvailMb=${memoryInfo.availMem / MIB} " +
                    "systemLowMemory=${memoryInfo.lowMemory} threads=${Thread.activeCount()} " +
                    "interactive=${powerManager.isInteractive} powerSave=${powerManager.isPowerSaveMode} " +
                    "thermal=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager.currentThermalStatus else -1}",
        )
    }

    private fun writeSystemSnapshot(app: Application) {
        val activityManager = app.getSystemService(ActivityManager::class.java)
        val audioManager = app.getSystemService(AudioManager::class.java)
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val configuration = app.resources.configuration
        write(
            "I",
            "System",
            "fingerprint=${Build.FINGERPRINT} abis=${Build.SUPPORTED_ABIS.joinToString()} " +
                "hardware=${Build.HARDWARE} cores=${Runtime.getRuntime().availableProcessors()} " +
                "memoryClassMb=${activityManager.memoryClass} largeMemoryClassMb=${activityManager.largeMemoryClass} " +
                "dataFreeMb=${statFs.availableBytes / MIB} dataTotalMb=${statFs.totalBytes / MIB} " +
                "locale=${configuration.locales.toLanguageTags()} timeZone=${ZoneId.systemDefault().id} " +
                "densityDpi=${configuration.densityDpi} fontScale=${configuration.fontScale} uiMode=${configuration.uiMode}",
        )
        write(
            "I",
            "AudioSystem",
            "mode=${audioManager.mode} musicActive=${audioManager.isMusicActive} " +
                "musicVolume=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                "${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} " +
                "outputTypes=${audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString { device ->
                    device.type.toString()
                }}",
        )
        writeBatterySnapshot(app, "initial")
        writeNetworkSnapshot(app, "initial")
    }

    private fun registerSystemEventLogging(app: Application) {
        synchronized(lock) {
            if (systemEventsRegistered) return
            systemEventsRegistered = true
        }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_SHUTDOWN)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(SystemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(SystemEventReceiver, filter)
        }
    }

    private fun registerNetworkLogging(app: Application) {
        synchronized(lock) {
            if (networkCallbackRegistered) return
            networkCallbackRegistered = true
        }
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(NetworkLogger)
        }.onFailure { write("E", "Network", "unable-to-register-callback", it) }
    }

    private fun writeBatterySnapshot(
        context: Context,
        event: String,
        suppliedIntent: Intent? = null,
    ) {
        val battery = suppliedIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        write(
            "I",
            "Battery",
            "$event percent=$percent status=${battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)} " +
                "plugged=${battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)} " +
                "temperatureTenthsC=${battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)} " +
                "voltageMv=${battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)}",
        )
    }

    private fun writeNetworkSnapshot(
        context: Context,
        event: String,
        network: Network? = null,
    ) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val target = network ?: manager.activeNetwork
        val capabilities = target?.let(manager::getNetworkCapabilities)
        write("I", "Network", "$event ${describeNetwork(capabilities)}")
    }

    private fun describeNetwork(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "unavailable"
        val transports =
            buildList {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
            }
        return "transports=${transports.joinToString()} validated=${capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )} " +
            "internet=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} " +
            "metered=${!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)} " +
            "roaming=${!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)} " +
            "downKbps=${capabilities.linkDownstreamBandwidthKbps} upKbps=${capabilities.linkUpstreamBandwidthKbps}"
    }

    private object SystemEventReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            write("I", "SystemEvent", "action=${intent.action}")
            if (
                intent.action == Intent.ACTION_POWER_CONNECTED ||
                intent.action == Intent.ACTION_POWER_DISCONNECTED ||
                intent.action == Intent.ACTION_BATTERY_LOW ||
                intent.action == Intent.ACTION_BATTERY_OKAY
            ) {
                writeBatterySnapshot(context, intent.action.orEmpty(), intent)
            }
            writeRuntimeSnapshot(context, "system-event-${intent.action}")
        }
    }

    private object NetworkLogger : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            application?.let { writeNetworkSnapshot(it, "available", network) }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            write("I", "Network", "capabilities-changed ${describeNetwork(networkCapabilities)}")
        }

        override fun onLost(network: Network) {
            write("W", "Network", "lost")
        }

        override fun onBlockedStatusChanged(
            network: Network,
            blocked: Boolean,
        ) {
            write("W", "Network", "blocked=$blocked")
        }
    }

    private fun ensureWriterLocked(date: LocalDate) {
        if (writer != null && writerDate == date) return
        runCatching { writer?.close() }
        writer = null
        writerDate = null
        val app = application ?: return
        runCatching {
            val fileName = "$FILE_PREFIX-$date.txt"
            val uri = findOrCreateDownloadUri(app, fileName, "text/plain")
            val stream =
                requireNotNull(app.contentResolver.openOutputStream(uri, "wa")) {
                    "Unable to open $uri"
                }
            writer = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))
            writerDate = date
        }.onFailure { error ->
            Log.e(LOG_TAG, "Unable to open Download diagnostic log", error)
        }
    }

    private fun findOrCreateDownloadUri(
        context: Context,
        fileName: String,
        mimeType: String,
    ): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/"
        resolver
            .query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf(fileName, relativePath),
                "${MediaStore.Downloads.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }

        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
        return requireNotNull(resolver.insert(collection, values)) {
            "Unable to create Download/$fileName"
        }
    }

    private fun priorityName(priority: Int): String =
        when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }

    private fun exitReasonName(reason: Int): String =
        when (reason) {
            1 -> "exit-self"
            2 -> "signaled"
            3 -> "low-memory"
            4 -> "crash"
            5 -> "native-crash"
            6 -> "anr"
            7 -> "initialization-failure"
            8 -> "permission-change"
            9 -> "excessive-resource-usage"
            10 -> "user-requested"
            11 -> "user-stopped"
            12 -> "dependency-died"
            13 -> "other"
            14 -> "freezer"
            15 -> "package-state-change"
            16 -> "package-updated"
            else -> "unknown"
        }

    private fun redact(value: String): String =
        value
            .replace(BEARER_PATTERN, "$1<redacted>")
            .replace(SECRET_FIELD_PATTERN, "$1=<redacted>")
            .replace(SECRET_QUERY_PATTERN, "$1<redacted>")

    private object ActivityLogger : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = activityEvent(activity, "created savedState=${savedInstanceState != null}")

        override fun onActivityStarted(activity: Activity) = activityEvent(activity, "started")

        override fun onActivityResumed(activity: Activity) = activityEvent(activity, "resumed")

        override fun onActivityPaused(activity: Activity) = activityEvent(activity, "paused")

        override fun onActivityStopped(activity: Activity) = activityEvent(activity, "stopped")

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = activityEvent(activity, "save-instance-state")

        override fun onActivityDestroyed(activity: Activity) = activityEvent(activity, "destroyed")

        private fun activityEvent(
            activity: Activity,
            event: String,
        ) {
            write(
                level = "I",
                tag = "Activity",
                message =
                    "${activity.javaClass.simpleName} $event finishing=${activity.isFinishing} " +
                        "changingConfiguration=${activity.isChangingConfigurations}",
            )
            if (event == "paused" || event == "stopped") {
                writeProcessSnapshot(activity, "activity-$event")
            }
        }
    }

    private const val MAX_EXIT_REASONS = 10
    private const val HEARTBEAT_INTERVAL_MS = 10_000L
    private const val MIB = 1024L * 1024L
    private val BEARER_PATTERN = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+")
    private val SECRET_FIELD_PATTERN =
        Regex("(?i)(authorization|access[_-]?token|refresh[_-]?token|token|cookie|secret|password|api[_-]?hash)\\s*[:=]\\s*[^\\s,;&]+")
    private val SECRET_QUERY_PATTERN =
        Regex("(?i)([?&](?:access[_-]?token|refresh[_-]?token|token|key|sign|auth|cookie|secret|password)=)[^&\\s]+")
}
