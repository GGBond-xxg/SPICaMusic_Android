package me.spica27.spicamusic

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import me.spcia.lyric_core.di.extraInfoModule
import me.spica27.spicamusic.crash.CrashHandler
import me.spica27.spicamusic.di.AppModule
import me.spica27.spicamusic.diagnostics.DiagnosticLog
import me.spica27.spicamusic.feature.library.domain.MusicScanUseCases
import me.spica27.spicamusic.feature.library.domain.libraryDomainModule
import me.spica27.spicamusic.feature.lyrics.domain.lyricsDomainModule
import me.spica27.spicamusic.feature.player.domain.playerDomainModule
import me.spica27.spicamusic.feature.settings.domain.settingsDomainModule
import me.spica27.spicamusic.player.api.IMusicPlayer
import me.spica27.spicamusic.player.impl.SpicaPlayer
import me.spica27.spicamusic.service.PlaybackService
import me.spica27.spicamusic.storage.impl.di.storageModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

/**
 * 应用程序类
 * 负责初始化 Koin 依赖注入、ImageLoader 和其他全局配置
 */
class App : Application() {
    private val musicScanService: MusicScanUseCases by inject()

    private val musicPlayer: IMusicPlayer by inject()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleController.wrap(base))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化日志
        DiagnosticLog.initialize(this)
        if (BuildConfig.DIAGNOSTIC_LOGGING) {
            Timber.plant(DiagnosticLog.timberTree)
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 初始化 Koin 依赖注入
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                storageModule, // 数据模块 (feature-library-data)
                SpicaPlayer.createModule(PlaybackService::class.java), // 数据模块 (feature-player-data)
                libraryDomainModule,
                playerDomainModule,
                settingsDomainModule,
                lyricsDomainModule,
                AppModule.appModule, // 应用模块
                extraInfoModule,
            )
        }

        // 启动 MediaStore 变更监听
        setupMediaStoreObserver()
        // FFT 采样由实际可见的播放器动态效果按需开启；这里负责后台兜底关闭。
        setupFftLifecycle()
        CrashHandler.init(this)
    }

    /**
     * 后台强制停止 FFT 频谱采样。
     * 前台不再无条件开启，避免只在列表听歌时仍持续做频谱计算；播放器动态背景
     * 真正可见时会通过 PlayerViewModel 按需启用。
     */
    private fun setupFftLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    DiagnosticLog.writeProcessSnapshot(this@App, "process-foreground")
                }

                override fun onStop(owner: LifecycleOwner) {
                    musicPlayer.fftProcessor.disable()
                    Timber.d("应用进入后台，FFT 采样已停止")
                    DiagnosticLog.writeProcessSnapshot(this@App, "process-background")
                }
            },
        )
    }

    /**
     * 设置 MediaStore 变更监听
     * 绑定到应用生命周期，前台时监听，后台时停止（节省资源）
     */
    private fun setupMediaStoreObserver() {
        // 立即启动监听器
        musicScanService.startMediaStoreObserver()
        Timber.i("MediaStore 监听器已启动")

        // 监听应用前后台切换，优化资源使用
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // 应用进入前台，启动监听
                    musicScanService.startMediaStoreObserver()
                    Timber.d("应用进入前台，MediaStore 监听器已启动")
                }

                override fun onStop(owner: LifecycleOwner) {
                    // 应用进入后台，停止监听
                    musicScanService.stopMediaStoreObserver()
                    Timber.d("应用进入后台，MediaStore 监听器已停止")
                }
            },
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DiagnosticLog.write(
            level = "I",
            tag = "Application",
            message =
                "configuration-changed locales=${newConfig.locales.toLanguageTags()} " +
                    "uiMode=${newConfig.uiMode}",
        )
    }

    override fun onTrimMemory(level: Int) {
        DiagnosticLog.write("W", "Application", "trim-memory level=$level")
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        DiagnosticLog.write("W", "Application", "low-memory")
        super.onLowMemory()
    }

    companion object {
        private lateinit var instance: App

        fun getInstance(): App = instance
    }
}
