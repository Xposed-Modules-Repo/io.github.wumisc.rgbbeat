package io.github.wumisc.rgbbeat.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.wumisc.rgbbeat.data.Prefs
import io.github.wumisc.rgbbeat.service.AppTriggerService
import io.github.wumisc.rgbbeat.service.BeatService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一个可勾选的应用条目（图标已转成 Bitmap，跨线程安全） */
data class AppEntry(val pkg: String, val label: String, val icon: Bitmap?, val recommended: Boolean)

/** 系统能力桥：权限、服务开关、应用列表。UI 只调这里，不直接碰系统 API。 */
object SystemBits {

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun openOverlaySettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun hasNotificationPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

    fun hasAudioPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = AppTriggerService::class.java.name
        val enabled = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Throwable) {
            null
        } ?: return false
        return enabled.split(':').any { it.contains(ctx.packageName) && it.contains(expected) }
    }

    fun openNotificationSettings(ctx: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAccessibilitySettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 启动服务：先校验悬浮窗权限，缺权限时直接跳到授权页 */
    fun startService(ctx: Context): Boolean {
        if (!canDrawOverlays(ctx)) {
            toast(ctx, "请先授予悬浮窗权限")
            openOverlaySettings(ctx)
            return false
        }
        return try {
            BeatService.start(ctx)
            toast(ctx, "RGBBeat 光效已启动")
            true
        } catch (t: Throwable) {
            toast(ctx, "启动失败：" + t.message)
            false
        }
    }

    fun stopService(ctx: Context) {
        try {
            BeatService.stop(ctx)
            toast(ctx, "RGBBeat 光效已停止")
        } catch (_: Throwable) {
        }
    }

    fun toggleService(ctx: Context): Boolean =
        if (BeatService.isRunningNow()) {
            stopService(ctx); false
        } else startService(ctx)

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }

    fun shareText(ctx: Context, title: String, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 列出可启动的应用；图标统一缩放到 96px 的 Bitmap，避免 UI 线程实时取图抖动 */
    suspend fun loadLaunchableApps(ctx: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = try {
            pm.queryIntentActivities(main, 0)
        } catch (_: Throwable) {
            emptyList()
        }
        infos.asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .map { ai ->
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (_: Throwable) {
                    ai.packageName
                }
                val icon = try {
                    ai.loadIcon(pm).toSized(96)
                } catch (_: Throwable) {
                    null
                }
                AppEntry(
                    pkg = ai.packageName,
                    label = label,
                    icon = icon,
                    recommended = Prefs.isCarsickApp(ai.packageName),
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.recommended }.thenBy { it.label })
            .toList()
    }
}

/** Drawable → 固定尺寸 Bitmap（避免图标绘制时反复缩放） */
private fun Drawable.toSized(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap.width >= size) {
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}
