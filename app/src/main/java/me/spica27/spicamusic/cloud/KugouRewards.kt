package me.spica27.spicamusic.cloud

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.spica27.spicamusic.R
import me.spica27.spicamusic.player.api.IMusicPlayer
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal fun kugouRewardDay(): String = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()

internal data class KugouRewardState(
    val autoEnabled: Boolean = true,
    val signedToday: Boolean = false,
    val claimedBaseToday: Boolean = false,
    val listenedToday: Boolean = false,
    val canClaimListening: Boolean = false,
    val busy: Boolean = false,
    val message: String = cloudText(R.string.kugou_auto_default),
)

/** Dates are keyed by the service user ID, so logging in again cannot reset today's claims. */
internal class KugouRewardLedger(
    private val prefs: SharedPreferences,
) {
    fun value(
        userId: String,
        key: String,
    ): String = prefs.getString("$userId:$key", "").orEmpty()

    fun hasDay(
        userId: String,
        key: String,
        day: String,
    ): Boolean = value(userId, key) == day

    fun put(
        userId: String,
        key: String,
        value: String,
    ) {
        check(
            prefs.edit().putString("$userId:$key", value).commit(),
        ) { cloudText(R.string.kugou_checkin_saving_error) }
    }

    fun enabled(userId: String): Boolean = value(userId, "disabled") != "true"

    fun shouldRun(
        userId: String,
        day: String,
    ): Boolean =
        enabled(userId) &&
            !hasDay(userId, "signed", day) &&
            !hasDay(userId, "attempt", day)
}

internal class KugouRewards(
    private val context: Context,
    private val accounts: CloudAccountStore,
    private val client: KugouClient,
) {
    private val ledger = KugouRewardLedger(context.getSharedPreferences("kugou_rewards", Context.MODE_PRIVATE))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val _states = MutableStateFlow<Map<String, KugouRewardState>>(emptyMap())
    val states = _states.asStateFlow()
    private var started = false
    private var playbackJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            accounts.revision.collectLatest {
                val active = accounts.getRemoteAccounts(RemoteMusicProvider.KUGOU)
                _states.update { current -> current.filterKeys { id -> active.any { it.userId == id } } }
                active.forEach { refresh(it.userId) }
                updateSchedule()
                runAutomaticSignIns()
            }
        }
    }

    fun onForeground(player: IMusicPlayer) {
        scope.launch {
            accounts.getRemoteAccounts(RemoteMusicProvider.KUGOU).forEach { refresh(it.userId) }
            runAutomaticSignIns()
        }
        if (playbackJob != null) return
        playbackJob =
            scope.launch {
                combine(player.isPlaying, player.currentMediaItem) { playing, item -> playing to item }.collectLatest { (playing, item) ->
                    if (!playing || item == null) return@collectLatest
                    val identity = parseCloudMediaIdentity(item.mediaId) ?: return@collectLatest
                    if (identity.provider != "kugou") return@collectLatest
                    val account =
                        accounts.getRemoteAccounts(RemoteMusicProvider.KUGOU).firstOrNull { it.id == identity.accountOrChatId }
                            ?: return@collectLatest
                    val audioId = identity.songId.substringAfter('_', "")
                    if (audioId.toLongOrNull()?.let { it > 0 } != true) return@collectLatest
                    var listenedMs = 0L
                    var position = player.currentPosition
                    var elapsed = SystemClock.elapsedRealtime()
                    val day = kugouRewardDay()
                    while (isActive && listenedMs < 60_000 && day == kugouRewardDay()) {
                        delay(1000)
                        val now = SystemClock.elapsedRealtime()
                        val nextPosition = player.currentPosition
                        // Count only active playback advancing normally; seeking cannot earn listening time.
                        if (player.isPlaying.value &&
                            player.currentMediaItem.value?.mediaId == item.mediaId &&
                            nextPosition - position in 1..3000 &&
                            now - elapsed in 1..3000
                        ) {
                            listenedMs += now - elapsed
                        }
                        position = nextPosition
                        elapsed = now
                    }
                    if (listenedMs >= 60_000 && activeAccount(account.userId) != null) {
                        ledger.put(account.userId, "audio", audioId)
                        ledger.put(account.userId, "eligible", day)
                        refresh(account.userId)
                    }
                }
            }
    }

    suspend fun runAutomaticSignIns() {
        accounts.getRemoteAccounts(RemoteMusicProvider.KUGOU).forEach { signIn(it.userId, automatic = true) }
    }

    fun setAutomatic(
        userId: String,
        enabled: Boolean,
    ) {
        if (activeAccount(userId) == null) return
        ledger.put(userId, "disabled", (!enabled).toString())
        refresh(userId)
        updateSchedule()
        if (enabled) scope.launch { signIn(userId, automatic = true) }
    }

    fun retry(userId: String) {
        scope.launch { signIn(userId, automatic = false) }
    }

    internal suspend fun signIn(
        userId: String,
        automatic: Boolean,
    ) = lock.withLock {
        val account = activeAccount(userId) ?: return@withLock
        val day = kugouRewardDay()
        if (ledger.hasDay(userId, "signed", day) || (automatic && !ledger.shouldRun(userId, day))) return@withLock
        try {
            requireKugouLogin(account)
            if (automatic) ledger.put(userId, "attempt", day)
            refresh(userId, busy = true, message = cloudText(R.string.kugou_checkin_busy))
            if (!ledger.hasDay(userId, "base", day)) {
                client.claimDailyVip(account, day)
                ledger.put(userId, "base", day)
            }
            if (activeAccount(userId) != account) return@withLock
            client.upgradeDailyVip(account)
            ledger.put(userId, "signed", day)
            ledger.put(userId, "messageDay", day)
            ledger.put(userId, "message", cloudText(R.string.kugou_checkin_success))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val prefix =
                if (ledger.hasDay(
                        userId,
                        "base",
                        day,
                    )
                ) {
                    cloudText(
                        R.string.kugou_checkin_partial,
                    )
                } else {
                    cloudText(R.string.kugou_checkin_failed)
                }
            ledger.put(userId, "messageDay", day)
            ledger.put(userId, "message", prefix + rewardError(error))
        } finally {
            refresh(userId)
        }
    }

    fun claimListening(userId: String) {
        scope.launch {
            lock.withLock {
                val account = activeAccount(userId) ?: return@withLock
                val day = kugouRewardDay()
                if (ledger.hasDay(userId, "listen", day)) return@withLock
                if (!ledger.hasDay(userId, "eligible", day)) {
                    refresh(userId, message = cloudText(R.string.kugou_listening_required))
                    return@withLock
                }
                try {
                    refresh(userId, busy = true, message = cloudText(R.string.kugou_listening_busy))
                    client.claimListeningReward(account, ledger.value(userId, "audio"))
                    ledger.put(userId, "listen", day)
                    ledger.put(userId, "messageDay", day)
                    ledger.put(userId, "message", cloudText(R.string.kugou_listening_claimed_today))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    ledger.put(userId, "messageDay", day)
                    ledger.put(userId, "message", cloudText(R.string.kugou_listening_error, rewardError(error)))
                } finally {
                    refresh(userId)
                }
            }
        }
    }

    private fun activeAccount(userId: String): RemoteMusicAccount? =
        accounts.getRemoteAccounts(RemoteMusicProvider.KUGOU).firstOrNull { it.userId == userId && it.secret.isNotBlank() }

    private fun refresh(
        userId: String,
        busy: Boolean = false,
        message: String? = null,
    ) {
        if (activeAccount(userId) == null) {
            _states.update { it - userId }
            return
        }
        val day = kugouRewardDay()
        val signed = ledger.hasDay(userId, "signed", day)
        _states.update {
            it + (
                userId to
                    KugouRewardState(
                        autoEnabled = ledger.enabled(userId),
                        signedToday = signed,
                        claimedBaseToday = ledger.hasDay(userId, "base", day),
                        listenedToday = ledger.hasDay(userId, "listen", day),
                        canClaimListening = ledger.hasDay(userId, "eligible", day),
                        busy = busy,
                        message =
                            message ?: if (ledger.hasDay(userId, "messageDay", day)) {
                                ledger
                                    .value(
                                        userId,
                                        "message",
                                    ).ifBlank { cloudText(R.string.kugou_checkin_attempted) }
                            } else {
                                cloudText(R.string.kugou_checkin_pending)
                            },
                    )
            )
        }
    }

    private fun updateSchedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val enabled = accounts.getRemoteAccounts(RemoteMusicProvider.KUGOU).any { ledger.enabled(it.userId) }
        if (!enabled) {
            scheduler.cancel(KUGOU_SIGN_IN_JOB)
            return
        }
        if (scheduler.getPendingJob(KUGOU_SIGN_IN_JOB) == null) {
            scheduler.schedule(
                JobInfo
                    .Builder(KUGOU_SIGN_IN_JOB, ComponentName(context, KugouSignInJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(TimeUnit.HOURS.toMillis(6))
                    .build(),
            )
        }
    }

    private fun rewardError(error: Exception): String =
        if (error is IllegalStateException || error is IllegalArgumentException) {
            error.message ?: cloudText(R.string.cloud_retry_later)
        } else {
            cloudText(R.string.cloud_network_error)
        }
}

private const val KUGOU_SIGN_IN_JOB = 311601

/** No credentials in JobInfo; each execution reads only accounts that are still logged in. */
class KugouSignInJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work =
            scope.launch {
                try {
                    GlobalContext.get().get<KugouRewards>().runAutomaticSignIns()
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    // Retry is available in the account page; never crash the service.
                } finally {
                    if (isActive) jobFinished(params, false)
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
