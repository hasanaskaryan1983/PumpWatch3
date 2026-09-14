package com.pumpwatch.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.KlineCache
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.ParamsStore
import com.pumpwatch.app.engine.RuleCondition
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.engine.SignalResult
import java.util.Locale

/**
 * MonitorWorker — نسخهٔ یکپارچه
 * - BatchScanner از UnifiedSignalEngine + Binance klines استفاده می‌کند
 * - گیت واحد Dedup: فقط وقتی SignalLogger.log موفق شود نوتیفیکیشن 📡 می‌فرستیم
 *
 * 🚀 P1-4: KlineCache.prune() در ابتدای هر اجرا
 * 🚀 Sprint 3: time = candleCloseTs (نه زمان اسکن)
 * 🚀 Sprint 4: پارامترهای سیگنال از ParamsStore (بهینه‌شده یا default)
 * 🚀 Sprint 5: ارزیابی قوانین هشدار سفارشی روی همهٔ نتایج + نوتیفیکیشن 🔔
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

        const val KEY_MODE = "mode"
    }

    override suspend fun doWork(): Result {
        return try {
            // 🚀 P1-4: خانه‌تکانی دوره‌ای cache
            KlineCache.prune()

            val modeRaw = inputData.getString(KEY_MODE)
                ?: applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
                    .getString("mode", "SPOT")
                ?: "SPOT"
            val mode = if (modeRaw == "FUTURES") "FUT" else "SPOT"

            // 🚀 Sprint 4: پارامترهای فعال
            val signalParams = ParamsStore.load(applicationContext)

            val results = BatchScanner.scan(mode, signalParams, limit = 25)
            val hot = results.filter { it.side != "NONE" && it.score >= MIN_SCORE }.take(3)

            hot.forEachIndexed { i, r ->
                val logSide = if (r.side == "PUMP") "BUY" else "SELL"

                // 🚀 Sprint 3: timestamp = زمان بسته شدن کندل مولد سیگنال
                val signalTs = if (r.candleCloseTs > 0L) r.candleCloseTs else System.currentTimeMillis()

                // گیت واحد dedup با QuickScanner
                val logged = SignalLogger.log(
                    applicationContext,
                    LoggedSignal(
                        symbol = r.symbol,
                        side = logSide,
                        score = r.score,
                        entry = r.entry,
                        stop = r.stopLoss,
                        target = r.target1,
                        time = signalTs,
                        mode = mode
                    )
                )

                if (logged) {
                    showNotification(
                        id = 1000 + i,
                        title = "${if (r.side == "PUMP") "🚀 پامپ" else "🩸 دامپ"} ${r.symbol} — ${r.score}/100 ${if (r.golden) "🏅" else ""} 📡",
                        text = String.format(
                            Locale.US,
                            "ورود: %.6f | استاپ: %.6f | هدف: %.6f",
                            r.entry, r.stopLoss, r.target1
                        )
                    )
                }
            }

            // 🚀 Sprint 5: ارزیابی قوانین هشدار سفارشی روی همهٔ نتایج اسکن
            try {
                evaluateRules(results)
            } catch (_: Exception) { }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * 🚀 Sprint 5: بررسی قوانین سفارشی کاربر.
     * ترتیب گیت‌ها (ارزان‌ترین اول): enabled → symbol → cooldown → matches
     * حداکثر MAX_RULE_ALERTS_PER_RUN نوتیفیکیشن در هر اجرا.
     */
    private fun evaluateRules(results: List<SignalResult>) {
        val now = System.currentTimeMillis()
        val rules = AlertRulesStore.load(applicationContext)
        if (rules.isEmpty()) return

        var ruleAlerts = 0
        for (r in results) {
            if (ruleAlerts >= MAX_RULE_ALERTS_PER_RUN) break
            for (rule in rules) {
                if (ruleAlerts >= MAX_RULE_ALERTS_PER_RUN) break
                if (!rule.enabled) continue
                if (!AlertRulesStore.symbolMatches(rule, r.symbol)) continue
                if (AlertRulesStore.inCooldown(rule, now)) continue
                if (!AlertRulesStore.matches(rule, r.price, r.score, r.funding, r.rsi, r.volumeRatio)) continue

                showRuleNotification(rule, r)
                AlertRulesStore.markTriggered(applicationContext, rule.id, now)
                ruleAlerts++
            }
        }
    }

    /**
     * نمایش مقدار فعلی مرتبط با شرط قانون (برای متن نوتیفیکیشن)
     */
    private fun currentValueText(rule: AlertRule, r: SignalResult): String = when (rule.condition) {
        RuleCondition.PRICE_ABOVE, RuleCondition.PRICE_BELOW ->
            String.format(Locale.US, "قیمت فعلی: %.6f", r.price)
        RuleCondition.SCORE_ABOVE ->
            "امتیاز فعلی: ${r.score}/100"
        RuleCondition.FUNDING_ABOVE, RuleCondition.FUNDING_BELOW ->
            String.format(Locale.US, "فاندینگ فعلی: %.4f%%", (r.funding ?: 0.0) * 100)
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

    private fun showRuleNotification(rule: AlertRule, r: SignalResult) {
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
            .setContentText(currentValueText(rule, r))
            .setStyle(NotificationCompat.BigTextStyle().bigText(currentValueText(rule, r)))
            .setAutoCancel(true)
            .build()

        nm.notify("rule_${rule.id}".hashCode(), notification)
    }

    private fun showNotification(id: Int, title: String, text: String) {
        val nm = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "هشدارهای پامپ/دامپ",
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

        nm.notify(id, notification)
    }
}
