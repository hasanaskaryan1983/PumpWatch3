package com.pumpwatch.app.engine

/**
 * 🚀 P3-3: مدل فرمول سیگنال سفارشی.
 * کاربر می‌تواند این مقادیر را تغییر دهد؛
 * مقدار DEFAULT همان تنظیمات استاندارد و بهینه‌شده است.
 */

enum class RangeMode(val label: String) {
    ANY("هر جای محدوده"),
    NEAR_HIGH("نزدیک سقف ۲۴س"),
    NEAR_LOW("نزدیک کف ۲۴س")
}

enum class FormulaSide(val label: String) {
    BOTH("هر دو جهت"),
    PUMP("فقط پامپ 📈"),
    DUMP("فقط دامپ 📉")
}

data class SignalFormula(
    val minScore: Int = 35,
    val minChange1h: Double = 0.0,
    val minVolumeRatio: Double = 0.0,
    val rangeMode: RangeMode = RangeMode.ANY,
    val side: FormulaSide = FormulaSide.BOTH
) {
    companion object {
        /**
         * تنظیمات استاندارد و بهینه‌شده.
         * این مقادیر دقیقاً معادل رفتار فعلی موتور تست‌شده است،
         * پس «بازگشت به بهترین» یعنی بازگشت به همین حالت.
         */
        val DEFAULT = SignalFormula()
    }
}
