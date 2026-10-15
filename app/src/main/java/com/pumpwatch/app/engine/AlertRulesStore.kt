package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.store.StorageGuard
import java.util.UUID

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

data class AlertRule(
    val id: String,
    val symbol: String,
    val condition: RuleCondition,
    val threshold: Double,
    var enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var lastTriggeredTs: Long = 0L
)

object AlertRulesStore {

    private const val PREFS = "pumpwatch_prefs"
    private const val KEY = "alert_rules"
    private val GSON = Gson()

    const val COOLDOWN_MS = 6L * 3_600_000L

    // 🚀 P1-2: مهاجرت + قرنطینه به‌جای حذف بی‌صدای قوانین کاربر
    fun load(ctx: Context): MutableList<AlertRule> {
        val p = ctx.getSharedPreferences(PREFS, 0)
        val raw = p.getString(KEY, "") ?: ""
        if (raw.isEmpty()) return mutableListOf()
        val migrated = StorageGuard.migrateRaw(ctx, PREFS, KEY, raw)
        return try {
            GSON.fromJson(migrated, object : TypeToken<MutableList<AlertRule>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) {
            StorageGuard.quarantine(ctx, PREFS, KEY, migrated)
            p.edit().remove(KEY).apply()
            mutableListOf()
        }
    }

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

    fun inCooldown(rule: AlertRule, now: Long): Boolean =
        rule.lastTriggeredTs > 0 && now - rule.lastTriggeredTs < COOLDOWN_MS

    fun symbolMatches(rule: AlertRule, symbol: String): Boolean =
        rule.symbol == "*" || rule.symbol.equals(symbol, ignoreCase = true)

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
