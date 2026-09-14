package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.ui.FavStore

/**
 * گزارش بازیابی برای نمایش در UI
 */
data class ImportReport(
    val signalsAdded: Int,
    val signalsSkipped: Int,
    val walletsAdded: Int,
    val walletsSkipped: Int,
    val ok: Boolean,
    val error: String? = null
)

/**
 * 🚀 Sprint 6: بازیابی داده‌ها از JSON پشتیبان (خروجی DataExporter).
 *
 * طراحی:
 *  - هر سه قالب export را می‌فهمد: full / signals-only / wallets-only
 *    (تشخیص با حضور فیلدهای "signals" و "wallets" در ریشهٔ JSON)
 *  - dedupe سیگنال‌ها با کلید (symbol + side + time) — همان کلید منطقی Sprint 3
 *  - dedupe کیف‌ها با addr؛ افزودن فقط از طریق FavStore.addFav (منبع واحد حقیقت)
 *  - پس از merge: مرتب‌سازی نزولی بر اساس time و سقف ۲۰۰ (همان قاعدهٔ SignalLogger)
 *  - هر خطای parse → ImportReport(ok=false) — فایل خراب هرگز crash نمی‌سازد
 */
object DataImporter {

    private val GSON = Gson()

    fun importJson(ctx: Context, json: String): ImportReport {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            var sAdded = 0
            var sSkipped = 0
            var wAdded = 0
            var wSkipped = 0

            // ---------- سیگنال‌ها ----------
            if (root.has("signals") && root.get("signals").isJsonArray) {
                val arr = root.getAsJsonArray("signals")
                val incoming: List<LoggedSignal> =
                    GSON.fromJson(arr, object : TypeToken<List<LoggedSignal>>() {}.type) ?: emptyList()

                val existing = SignalLogger.load(ctx).toMutableList()
                for (s in incoming) {
                    val dup = existing.any { it.symbol == s.symbol && it.side == s.side && it.time == s.time }
                    if (dup) {
                        sSkipped++
                    } else {
                        existing.add(s)
                        sAdded++
                    }
                }
                val merged = existing.sortedByDescending { it.time }.take(200)
                SignalLogger.save(ctx, merged)
            }

            // ---------- کیف‌ها ----------
            if (root.has("wallets")) {
                FavStore.load(ctx)
                val walletsNode = root.get("wallets")
                val favArray: JsonArray? = when {
                    walletsNode.isJsonObject && walletsNode.asJsonObject.has("favorites") ->
                        walletsNode.asJsonObject.getAsJsonArray("favorites")
                    walletsNode.isJsonArray -> walletsNode.asJsonArray
                    else -> null
                }

                val existingJson = GSON.toJson(FavStore.favs.value)
                favArray?.forEach { el ->
                    if (!el.isJsonObject) {
                        wSkipped++
                        return@forEach
                    }
                    val obj = el.asJsonObject
                    val addr = listOf("addr", "address", "wallet")
                        .firstNotNullOfOrNull { k -> if (obj.has(k) && !obj.get(k).isJsonNull) obj.get(k).asString else null }
                    val note = listOf("note", "label", "symbol")
                        .firstNotNullOfOrNull { k -> if (obj.has(k) && !obj.get(k).isJsonNull) obj.get(k).asString else null }
                        ?: ""

                    if (addr.isNullOrBlank() || existingJson.contains(addr)) {
                        wSkipped++
                    } else {
                        FavStore.addFav(ctx, addr, note)
                        wAdded++
                    }
                }
            }

            if (sAdded == 0 && sSkipped == 0 && wAdded == 0 && wSkipped == 0) {
                ImportReport(0, 0, 0, 0, ok = false, error = "فایل معتبر است ولی هیچ دادهٔ قابل بازیابی ندارد")
            } else {
                ImportReport(sAdded, sSkipped, wAdded, wSkipped, ok = true)
            }
        } catch (e: Exception) {
            ImportReport(0, 0, 0, 0, ok = false, error = e.message)
        }
    }
}
