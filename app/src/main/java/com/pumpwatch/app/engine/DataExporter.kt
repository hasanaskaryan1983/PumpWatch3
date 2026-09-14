package com.pumpwatch.app.engine

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pumpwatch.app.ui.FavStore

/**
 * گزارش export برای نمایش در UI
 */
data class ExportResult(
    val fileName: String,
    val json: String,
    val signalCount: Int,
    val walletCount: Int
)

/**
 * 🚀 Sprint 6: export داده‌های اپ (سیگنال‌ها + کیف‌ها) به JSON برای پشتیبان‌گیری
 * یا انتقال به دستگاه دیگر.
 *
 * طراحی:
 *  - همهٔ exportها یک String JSON برمی‌گردانند (caller تصمیم می‌گیرد چه کند)
 *  - GsonBuilder.setPrettyPrinting() برای JSON خوانا (فایل ذخیره‌شده قابل بازرسی دستی)
 *  - version field برای آینده: اگر فرمت عوض شد، importer نسخه را می‌بیند
 *  - timestamp: زمان export به‌عنوان metadata
 */
object DataExporter {

    private const val FORMAT_VERSION = 1
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * ساختار ریشهٔ export کامل (signals + wallets)
     */
    data class FullExport(
        val version: Int,
        val exportedAt: Long,
        val appName: String = "PumpWatch",
        val signals: List<LoggedSignal>,
        val wallets: List<Any>,   // FavWallet + trash + alerts در یک ساختار
        val metadata: Map<String, Any>
    )

    /**
     * ساختار wallets برای export (چون FavStore سه لیست مجزا دارد)
     */
    data class WalletsExport(
        val favorites: List<Any>,
        val trash: List<Any>,
        val alerts: List<Any>
    )

    /**
     * export سیگنال‌ها به تنهایی (فقط logs).
     */
    fun exportSignals(ctx: Context): ExportResult {
        val signals = SignalLogger.load(ctx)
        val wrapper = mapOf(
            "version" to FORMAT_VERSION,
            "type" to "signals",
            "exportedAt" to System.currentTimeMillis(),
            "count" to signals.size,
            "signals" to signals
        )
        return ExportResult(
            fileName = "pumpwatch_signals_${System.currentTimeMillis()}.json",
            json = GSON.toJson(wrapper),
            signalCount = signals.size,
            walletCount = 0
        )
    }

    /**
     * export کیف‌های مورد پسند (favorites + trash + alerts).
     */
    fun exportWallets(ctx: Context): ExportResult {
        FavStore.load(ctx)
        val wrapper = mapOf(
            "version" to FORMAT_VERSION,
            "type" to "wallets",
            "exportedAt" to System.currentTimeMillis(),
            "count" to FavStore.favs.value.size,
            "wallets" to WalletsExport(
                favorites = FavStore.favs.value,
                trash = FavStore.trash.value,
                alerts = FavStore.alerts.value
            )
        )
        return ExportResult(
            fileName = "pumpwatch_wallets_${System.currentTimeMillis()}.json",
            json = GSON.toJson(wrapper),
            signalCount = 0,
            walletCount = FavStore.favs.value.size
        )
    }

    /**
     * export کامل (سیگنال‌ها + کیف‌ها) برای پشتیبان‌گیری همه‌جانبه.
     */
    fun exportAll(ctx: Context): ExportResult {
        FavStore.load(ctx)
        val signals = SignalLogger.load(ctx)
        val full = FullExport(
            version = FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            signals = signals,
            wallets = listOf(
                WalletsExport(
                    favorites = FavStore.favs.value,
                    trash = FavStore.trash.value,
                    alerts = FavStore.alerts.value
                )
            ),
            metadata = mapOf(
                "signalCount" to signals.size,
                "walletCount" to FavStore.favs.value.size,
                "trashCount" to FavStore.trash.value.size,
                "alertCount" to FavStore.alerts.value.size
            )
        )
        return ExportResult(
            fileName = "pumpwatch_backup_${System.currentTimeMillis()}.json",
            json = GSON.toJson(full),
            signalCount = signals.size,
            walletCount = FavStore.favs.value.size
        )
    }

    /**
     * اشتراک‌گذاری JSON از طریق share intent (Telegram, Gmail, Drive, ...).
     * caller می‌تواند از LocalContext برای startActivity استفاده کند.
     */
    fun buildShareIntent(result: ExportResult): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            putExtra(Intent.EXTRA_TEXT, "PumpWatch backup — ${result.signalCount} سیگنال، ${result.walletCount} کیف")
            putExtra(Intent.EXTRA_TEXT, result.json)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
