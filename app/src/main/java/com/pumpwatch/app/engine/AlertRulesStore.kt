package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * شرط‌های قابل‌انتخاب برای قوانین هشدار سفارشی.
 * همه از فیلدهای موجود در SignalResult ارزیابی می‌شوند (بدون تغییر موتور).
 */
enum class RuleCondition(val label: String) {
    PRICE_ABOVE("قیمت بالای"),
    PRICE_BELOW("قیمت زیر"),
    SCORE_ABOVE("امتیاز سیگنال بالای"),
    FUNDING_ABOVE("فاندینگ بالای"),
    FUNDING_BELOW("فاندینگ زیر"),
    RSI_ABOVE("RSI بالای"),
    RSI_BELOW("RSI زیر"),
    VOLUME_ABOVE("نسبت حجم بالای")
}

/**
 * یک قانون هشدار سفارشی.
 *
 * @param symbol نماد خاص ("BTC") یا "*" برای همهٔ نمادها
 * @param lastTriggeredTs برای cooldown شش‌ساعته (ضد اسپم نوتیفیکیشن)
 */
data class AlertRule(
    val id: String,
    val symbol: String,
    val condition: RuleCondition,
    val threshold: Double,
    var enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var lastTriggeredTs: Long = 0L
)

/**
 * 🚀 Sprint 5: ذخیره‌سازی و ارزیابی قوانین هشدار سفارشی.
 *
 * - CRUD کامل روی SharedPreferences (JSON با Gson)
 * - matches() pure است (بدون Context) → قابل تست واحد
 * - COOLDOWN_MS: یک قانون حداکثر هر ۶ ساعت یک‌بار نوتیفیکیشن می‌فرستد
 */
object AlertRulesStore {

    private const val PREFS = "pumpwatch_prefs"
    private const val KEY = "alert_rules"
    private val GSON = Gson()

    /** حداقل فاصلهٔ زمانی بین دو هشدار یک قانون (۶ ساعت) */
    const val COOLDOWN_MS = 6L * 3_600_000L

    fun load(ctx: Context): MutableList<AlertRule> = try {
        val json = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "") ?: ""
        if (json.isEmpty()) mutableListOf()
        else GSON.fromJson(json, object : TypeToken<MutableList<AlertRule>>() {}.type) ?: mutableListOf()
    } catch (_: Exception) { mutableListOf() }

    fun save(ctx: Context, rules: List<AlertRule>) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY, GSON.toJson(rules))
            .apply()
    }

    fun add(ctx: Context, symbol: String, condition: RuleCondition, threshold: Double): AlertRule {
        val rules = load(ctx)
        val rule = AlertRule(
            id = UUID.randomUUID().toString(),
            symbol = symbol.trim().uppercase(),
            condition = condition,
            threshold = threshold
        )
        rules.add(0, rule)
        save(ctx, rules)
        return rule
    }

    fun toggle(ctx: Context, id: String) {
        val rules = load(ctx)
        rules.firstOrNull { it.id == id }?.let { it.enabled = !it.enabled }
        save(ctx, rules)
    }

    fun delete(ctx: Context, id: String) {
        val rules = load(ctx)
        rules.removeAll { it.id == id }
        save(ctx, rules)
    }

    fun markTriggered(ctx: Context, id: String, ts: Long) {
        val rules = load(ctx)
        rules.firstOrNull { it.id == id }?.let { it.lastTriggeredTs = ts }
        save(ctx, rules)
    }

    /** آیا این قانون در cooldown است؟ */
    fun inCooldown(rule: AlertRule, now: Long): Boolean =
        rule.lastTriggeredTs > 0 && now - rule.lastTriggeredTs < COOLDOWN_MS

    /** آیا نماد سیگنال با نماد قانون匹配 می‌کند؟ ("*" = همه) */
    fun symbolMatches(rule: AlertRule, symbol: String): Boolean =
        rule.symbol == "*" || rule.symbol.equals(symbol, ignoreCase = true)

    /**
     * ارزیابی pure شرط قانون روی مقادیر داده‌شده.
     * برای شرط‌های فاندینگ: اگر funding null باشد، قانون هرگز match نمی‌شود.
     */
    fun matches(
        rule: AlertRule,
        price: Double,
        score: Int,
        funding: Double?,
        rsi: Double,
        volumeRatio: Double
    ): Boolean = when (rule.condition) {
        RuleCondition.PRICE_ABOVE -> price >= rule.threshold
        RuleCondition.PRICE_BELOW -> price <= rule.threshold
        RuleCondition.SCORE_ABOVE -> score >= rule.threshold
        RuleCondition.FUNDING_ABOVE -> funding != null && funding >= rule.threshold
        RuleCondition.FUNDING_BELOW -> funding != null && funding <= rule.threshold
        RuleCondition.RSI_ABOVE -> rsi >= rule.threshold
        RuleCondition.RSI_BELOW -> rsi <= rule.threshold
        RuleCondition.VOLUME_ABOVE -> volumeRatio >= rule.threshold
    }
}
