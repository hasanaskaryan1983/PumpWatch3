package com.pumpwatch.app.worker

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🚀 Sprint 11 (C2b): ارتباط نوتیفیکیشن ↔ Workspace
 * 🚀 Commit 117 (Infra5): جایگزینی callbackFlow با StateFlow برای تضمین delivery
 *
 * یک singleton ساده که:
 * - یک flag persistent در SharedPreferences نگه می‌دارد (برای fresh start پس از click روی notification)
 * - یک StateFlow in-memory به workspace ها می‌دهد (برای reaction سریع)
 *
 * 🚀 Commit 117: چرا StateFlow به‌جای callbackFlow؟
 * 1. trySend در callbackFlow silently fails می‌کند اگر channel بسته باشد → event از دست می‌رود
 * 2. StateFlow با replay=1 تضمین می‌کند subscriber جدید همیشه آخرین value را می‌گیرد
 * 3. Sync اولیه از persisted value در observe() تضمین می‌کند fresh start هم کار می‌کند
 */
object SignalNavigator {
    private const val PREFS_NAME = "pumpwatch_prefs"
    private const val KEY_OPEN_SIGNALS = "pending_open_signals"

    // 🚀 Commit 117: StateFlow به‌جای callbackFlow
    private val _pendingSignal = MutableStateFlow(false)
    val pendingSignal: StateFlow<Boolean> = _pendingSignal.asStateFlow()

    fun markPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OPEN_SIGNALS, true)
            .apply()
        // 🚀 Commit 117: emit به in-memory flow برای workspace های زنده
        _pendingSignal.value = true
    }

    fun consume(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_OPEN_SIGNALS)
            .apply()
        // 🚀 Commit 117: reset in-memory flow
        _pendingSignal.value = false
    }

    fun isPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPEN_SIGNALS, false)

    /**
     * 🚀 Commit 117: StateFlow با sync اولیه از persisted value.
     * 
     * تضمین می‌کند:
     * 1. اگر flag از قبل set شده (مثلاً app پس از click روی notification تازه شروع شده)،
     *    workspace حتماً آن را می‌بیند (initial sync).
     * 2. subscriber جدید همیشه آخرین value را می‌گیرد (StateFlow replay=1).
     * 3. در حین اجرا، تغییرات به همه subscribers push می‌شوند.
     */
    fun observe(context: Context): StateFlow<Boolean> {
        // 🚀 Commit 117: sync اولیه از persisted value برای fresh start
        val persisted = isPending(context)
        if (persisted && !_pendingSignal.value) {
            _pendingSignal.value = true
        }
        return pendingSignal
    }
}
