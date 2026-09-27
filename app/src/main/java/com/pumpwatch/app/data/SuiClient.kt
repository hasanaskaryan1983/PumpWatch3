/**
     * 🚀 Commit 74: طبقه‌بندی خطا (مثل WhaleApi.kt).
     * Transient (شبکه/timeout/5xx/429) → Log.w
     * Terminal (باگ/parse/ورودی نامعتبر) → Log.e
     *
     * 🚀 Commit 74-fix: try-catch روی Log calls — در JVM unit test
     * (بدون Robolectric) android.util.Log در دسترس نیست، پس به
     * println fallback می‌کنیم.
     */
    private fun handleError(method: String, e: Exception) {
        val msg = e.message ?: ""
        val cls = e::class.java.simpleName
        val isTransient = msg.contains("429") ||
            msg.contains("timeout", true) ||
            msg.contains("503") ||
            msg.contains("502") ||
            msg.contains("504") ||
            msg.contains("500") ||
            msg.contains("network", true) ||
            cls.contains("Timeout", true) ||
            cls.contains("Connect", true) ||
            cls.contains("Socket", true) ||
            cls.contains("UnknownHost", true)

        val logMsg = "[$method] $cls: $msg"
        try {
            if (isTransient) {
                Log.w(TAG, logMsg)
            } else {
                Log.e(TAG, logMsg, e)
            }
        } catch (_: Throwable) {
            // Log در JVM unit test در دسترس نیست — fallback به println
            println("$TAG: $logMsg")
        }
    }
