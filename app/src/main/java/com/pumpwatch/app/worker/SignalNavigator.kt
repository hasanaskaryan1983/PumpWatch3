package com.pumpwatch.app.worker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 🚀 Sprint 11 (C2b): ارتباط نوتیفیکیشن ↔ Workspace
 *
 * یک singleton ساده که با SharedPreferences (که از قبل در اپ استفاده می‌شود)
 * یک flag را set/consume می‌کند و یک Flow<Boolean> reactive به workspace ها می‌دهد.
 *
 * - Worker: markPending() را صدا می‌زند
 * - Workspace: observe() را collect می‌کند؛ وقتی true شد، تب سیگنال را set
 *   و consume() را صدا می‌زند تا flag پاک شود
 *
 * چرا SharedPreferences به‌جای SharedFlow در memory؟
 * چون اگر اپ کاملاً کشته شده باشد و user روی نوتیفیکیشن کلیک کند،
 * app fresh start می‌شود و SharedFlow event را از دست می‌دهد —
 * اما SharedPreferences persistent است و flag بعد از restart هم می‌ماند.
 */
object SignalNavigator {
    private const val PREFS_NAME = "pumpwatch_prefs"
    private const val KEY_OPEN_SIGNALS = "pending_open_signals"

    fun markPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OPEN_SIGNALS, true)
            .apply()
    }

    fun consume(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_OPEN_SIGNALS)
            .apply()
    }

    fun isPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPEN_SIGNALS, false)

    /**
     * Flow reactive روی تغییرات flag — workspace در LaunchedEffect آن را collect می‌کند.
     * یک‌بار initial value هم می‌فرستد (برای حالتی که flag از قبل set شده).
     */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == KEY_OPEN_SIGNALS) {
                trySend(p.getBoolean(KEY_OPEN_SIGNALS, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // initial: اگر flag از قبل set شده (مثلاً app از scratch باز شده
        // و worker قبلاً نوتیفیکیشن فرستاده بود)
        if (prefs.getBoolean(KEY_OPEN_SIGNALS, false)) {
            trySend(true)
        }

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
