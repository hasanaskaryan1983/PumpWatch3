package com.pumpwatch.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.data.KlineCache
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.ParamsStore
import com.pumpwatch.app.engine.RuleCondition
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.engine.SignalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * MonitorWorker — نسخهٔ یکپارچه (کامیت ۱۱۰ + ۱۱۱ + ۱۱۴)
 *
 * 🚀 Commit 114: هشدارهای واچ‌لیست هم اینجا ارزیابی می‌شوند (evaluator یگانه).
 * 🚀 Commit 111: buildCandidates از Pair به‌جای copy استفاده می‌کند.
 * 🚀 Commit 110: A5 (ارزیابی background سیگنال‌ها) + A3 (کل بازار) +
 *    F14 (markTriggered فقط بعد از notify) + F10 (فاندینگ واقعی).
 */
class MonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "pumpwatch_monitor"
        private const val CHANNEL_RULES = "pumpwatch_rules"
        private const val MIN_SCORE = 70
        private const val MAX_RULE_ALERTS_PER_RUN = 3
        private const val TAG = "MonitorWorker"

        const val KEY_MODE = "mode"

        internal fun isTransient(e: Throwable): Boolean {
            val name = e::class.java.simpleName
            val msg = (e.message ?: "").lowercase(Locale.US)
            if (name.contains("Timeout", true) ||
                name.contains("Network", true) ||
                name.contains("Socket", true) ||
                name.contains("Connect", true) ||
                name.contains("UnknownHost", true) ||
                name.contains("IOException", true)
            ) return true
            if (msg.contains("timeout") || msg.contains("429") ||
                msg.contains("500") || msg.contains("502") ||
                msg.contains("503") || msg.contains("504") ||
                msg.contains("network") || msg.contains("connection") ||
                msg.contains("unreachable")
            ) return true
            return false
        }
    }

    private fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun markNotificationsBlocked(blocked: Boolean) {
        applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
            .edit().putBoolean("notif_permission_denied", blocked).apply()
    }

    override suspend fun doWork(): Result {
        return try {
            KlineCache.prune()

            // 🚀 Commit 110 (A5): ارزیابی background سیگنال‌های باز
            try {
                val currentLogs = SignalLogger.load(applicationContext)
                val hasOpen = currentLogs.any { it.status == "OPEN" || it.status == "EXP" }
                if (hasOpen) {
                    val updated = SignalLogger.updateOpenSignals(applicationContext, currentLogs)
                    SignalLogger.save(applicationContext, updated)
                }
            } catch (e: Exception) {
                Log.w(TAG, "SignalLogger background eval failed", e)
            }

            val modeRaw = inputData.getString(KEY_MODE)
                ?: applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
                    .getString("mode", "SPOT")
                ?: "SPOT"
            val mode = if (modeRaw == "FUTURES") "FUT" else "SPOT"

            val signalParams = ParamsStore.load(applicationContext)
            val results = BatchScanner.scan(mode, signalParams, limit = 25)
            val hot = results.filter { it.side != "NONE" && it.score >= MIN_SCORE }.take(3)

            hot.forEachIndexed { i, r ->
                val logSide = if (r.side == "PUMP") "BUY" else "SELL"
                val signalTs = if (r.candleCloseTs > 0L) r.candleCloseTs else System.currentTimeMillis()

                val logged = SignalLogger.log(
                    applicationContext,
                    LoggedSignal(
                        symbol = r.symbol, side = logSide, score = r.score,
                        entry = r.entry, stop = r.stopLoss, target = r.target1,
                        time = signalTs, mode = mode
                    )
                )

                if (logged) {
                    val ok = showNotification(
                        id = 1000 + i,
                        title = "${if (r.side == "PUMP") "🚀 پامپ" else "🩸 دامپ"} ${r.symbol} — ${r.score}/100 ${if (r.golden) "🏅" else ""} 📡",
                        text = String.format(
                            Locale.US, "ورود: %.6f | استاپ: %.6f | هدف: %.6f",
                            r.entry, r.stopLoss, r.target1
                        )
                    )
                    if (!ok) Log.w(TAG, "Notification delivery failed for ${r.symbol}")
                }
            }

            // 🚀 Commit 110 (A3 + F10): ارزیابی قوانین روی کل بازار
            try {
                evaluateRulesOnFullMarket(mode)
            } catch (e: Exception) {
                Log.w(TAG, "evaluateRulesOnFullMarket failed (non-critical)", e)
            }

            // 🚀 Commit 114: evaluator یگانه — هشدارهای واچ‌لیست هم اینجا ارزیابی می‌شوند
            try {
                val fired = com.pumpwatch.app.store.WatchlistStore.checkAndFire(applicationContext)
                if (fired > 0) Log.i(TAG, "Watchlist alerts fired: $fired")
            } catch (e: Exception) {
                Log.w(TAG, "Watchlist checkAndFire failed (non-critical)", e)
            }

            Result.success()
        } catch (e: Exception) {
            if (isTransient(e)) {
                if (runAttemptCount < 3) {
                    Log.w(TAG, "Transient error (attempt ${runAttemptCount + 1}/3), will retry", e)
                    Result.retry()
                } else {
                    Log.e(TAG, "Transient error but max retries reached, failing", e)
                    Result.failure()
                }
            } else {
                Log.e(TAG, "Terminal error (code/data bug), failing immediately", e)
                Result.failure()
            }
        }
    }

    /**
     * 🚀 Commit 111: استفاده از Pair به‌جای copy (SignalResult ممکن است data class نباشد)
     */
    private suspend fun evaluateRulesOnFullMarket(mode: String) {
        val rules = AlertRulesStore.load(applicationContext)
        if (rules.isEmpty()) return
        if (!rules.any { it.enabled }) return

        val symbolCandidates = buildCandidates(mode)
        val now = System.currentTimeMillis()
        var ruleAlerts = 0

        for ((r, funding) in symbolCandidates) {
            if (ruleAlerts >= MAX_RULE_ALERTS_PER_RUN) break
            for (rule in rules) {
                if (ruleAlerts >= MAX_RULE_ALERTS_PER_RUN) break
                if (!rule.enabled) continue
                if (!AlertRulesStore.symbolMatches(rule, r.symbol)) continue
                if (AlertRulesStore.inCooldown(rule, now)) continue
                if (!AlertRulesStore.matches(rule, r.price, r.score, funding, r.rsi, r.volumeRatio)) continue

                // 🚀 Commit 110 (F14): markTriggered فقط پس از موفقیت notify
                val ok = showRuleNotification(rule, r, funding)
                if (ok) {
                    AlertRulesStore.markTriggered(applicationContext, rule.id, now)
                    ruleAlerts++
                }
            }
        }
    }

    private suspend fun buildCandidates(mode: String): List<Pair<SignalResult, Double?>> {
        val params = ParamsStore.load(applicationContext)
        val baseResults = BatchScanner.scan(mode, params, limit = 200)
        if (mode != "FUT") return baseResults.map { it to it.funding }

        return withContext(Dispatchers.IO) {
            val fundingMap = try {
                BinanceFutures.api.premiumIndexAll()
                    .filter { it.symbol.endsWith("USDT") }
                    .associate {
                        val base = it.symbol.removeSuffix("USDT")
                        base to (it.lastFundingRate?.toDoubleOrNull() ?: 0.0)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load funding data", e)
                emptyMap()
            }
            baseResults.map { r -> r to (fundingMap[r.symbol] ?: r.funding) }
        }
    }

    private fun currentValueText(
        rule: AlertRule,
        r: SignalResult,
        funding: Double? = r.funding
    ): String = when (rule.condition) {
        RuleCondition.PRICE_ABOVE, RuleCondition.PRICE_BELOW ->
            String.format(Locale.US, "قیمت فعلی: %.6f", r.price)
        RuleCondition.SCORE_ABOVE ->
            "امتیاز فعلی: ${r.score}/100"
        RuleCondition.FUNDING_ABOVE, RuleCondition.FUNDING_BELOW ->
            String.format(Locale.US, "فاندینگ فعلی: %.4f%%", (funding ?: 0.0) * 100)
        RuleCondition.RSI_ABOVE, RuleCondition.RSI_BELOW ->
            String.format(Locale.US, "RSI فعلی: %.1f", r.rsi)
        RuleCondition.VOLUME_ABOVE ->
            String.format(Locale.US, "نسبت حجم فعلی: %.2f", r.volumeRatio)
    }

    private fun fmtThreshold(v: Double): String = when {
        v >= 1000 -> String.format(Locale.US, "%.2f", v)
        v >= 1 -> String.format(Locale.US, "%.4f", v)
        else -> String.format(Locale.US, "%.6f", v)
    }

    private fun showRuleNotification(
        rule: AlertRule,
        r: SignalResult,
        funding: Double? = r.funding
    ): Boolean {
        if (!canPostNotifications()) {
            markNotificationsBlocked(true)
            return false
        }

        val nm = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RULES,
                    "هشدارهای سفارشی 🔔",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_RULES)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("🔔 ${r.symbol}: ${rule.condition.label} ${fmtThreshold(rule.threshold)}")
            .setContentText(currentValueText(rule, r, funding))
            .setStyle(NotificationCompat.BigTextStyle().bigText(currentValueText(rule, r, funding)))
            .setAutoCancel(true)
            .build()

        return try {
            nm.notify("rule_${rule.id}".hashCode(), notification)
            markNotificationsBlocked(false)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on notify (OEM restriction)", e)
            markNotificationsBlocked(true)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error on notify", e)
            false
        }
    }

    private fun showNotification(id: Int, title: String, text: String): Boolean {
        if (!canPostNotifications()) {
            markNotificationsBlocked(true)
            return false
        }

        val nm = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "هشدارهای پامپ/دامپ",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        return try {
            nm.notify(id, notification)
            markNotificationsBlocked(false)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on notify (OEM restriction)", e)
            markNotificationsBlocked(true)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error on notify", e)
            false
        }
    }
}
