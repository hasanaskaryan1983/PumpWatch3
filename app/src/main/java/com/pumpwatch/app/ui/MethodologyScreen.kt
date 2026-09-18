package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MeGreen = Color(0xFF00E676)
private val MeGold = Color(0xFFFFC107)
private val MeGray = Color(0xFF8B949E)
private val MeBlue = Color(0xFF40C4FF)
private val MeCard = Color(0xFF1A2230)

/**
 * 🚀 Sprint 14 (مرحلهٔ ۰ گزارش / Commit 8C): صفحهٔ Methodology & Risk
 *
 * الزام گزارش: «یک صفحهٔ Methodology & Risk داخل اپ ایجاد کنید.»
 * همهٔ ادعاهای این صفحه دقیقاً مطابق رفتار واقعی کدِ shipping شده است —
 * نه بیشتر. اگر روزی موتوری عوض شد، این صفحه باید همان Commit آپدیت شود.
 */
@Composable
fun MethodologyScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = MeCard),
            shape = RoundedCornerShape(10.dp)
        ) { Text("→ برگشت", fontSize = 11.sp) }

        Text("📖 متدولوژی و ریسک", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "این صفحه توضیح می‌دهد هر عدد از کجا می‌آید و چه محدودیتی دارد. " +
                "هر جا برچسبی دیدی، معنایش اینجاست.",
            fontSize = 10.sp, color = MeGold, lineHeight = 16.sp
        )

        Section("🧠 سیگنال‌ها چگونه ساخته می‌شوند") {
            Bullet("فقط کندل‌های بسته‌شده وارد تحلیل می‌شوند؛ کندل در حال تشکیل حذف می‌شود.")
            Bullet("لایهٔ ۱: رژیم بازار از هم‌جهتی ۱س/۴س/روزانه (EMA و Supertrend).")
            Bullet("لایهٔ ۲: ستاپ = شکست مقاومت/حمایت + حجم ≥ ۱.۵ برابر + RSI در بازهٔ مجاز.")
            Bullet("لایهٔ ۳: تأیید MACD و ADX؛ امتیاز ۰ تا ۱۰۰؛ آستانهٔ سیگنال ۷۰ و طلایی ۸۵.")
            Bullet("ورود در بک‌تست و paper: کندل بعد از سیگنال (next-bar)، نه همان کندل.")
            Bullet("اگر تاریخچهٔ کندل ناکافی باشد، هیچ سیگنالی تولید نمی‌شود.")
        }

        Section("📡 منبع داده و معنای برچسب‌ها") {
            Bullet("کندل‌ها: زنجیرهٔ Bybit → OKX → Gate؛ نام venue واقعی زیر هر نمودار و کارت نوشته می‌شود.")
            Bullet("بازار و نمودار تاریخی: CoinGecko؛ استخرهای DEX و میم: GeckoTerminal؛ امنیت قرارداد: GoPlus.")
            Bullet("🟢 زنده = زیر ~۲ دقیقه • 🟡 کش = تا ۳۰ دقیقه • 🔴 مانده = بیشتر • «آفلاین» = دادهٔ دیسک.")
            Bullet("🟠 روی نمودار یعنی کندل واقعی صرافی موجود نبود و کندل ترکیبی تخمینی نمایش داده می‌شود.")
        }

        Section("🛡️ معنای Rug Safety") {
            Bullet("۱۲ چک: honeypot، mint، owner، proxy، selfdestruct، tax، تمرکز هولدرها، قفل نقدینگی و…")
            Bullet("UNKNOWN یعنی بررسی انجام نشد یا توکن شناخته نشد — هرگز به معنی safe نیست.")
            Bullet("EMPTY = GoPlus توکن را نمی‌شناسد • FAILED = خطای اتصال در بررسی.")
            Bullet("بررسی روی قرارداد خودِ توکن انجام می‌شود، نه آدرس استخر نقدینگی.")
        }

        Section("🐳 دو مفهوم متفاوت «نهنگ»") {
            Bullet("Large venue trade: معاملهٔ تکی ≥ ۱۰۰ هزار دلار در یک صرافی — فقط این «نهنگ واقعی» است.")
            Bullet("Pool buy/sell mix: نسبت خرید/فروش استخر DEX — نهنگ نیست؛ فشار خرده‌فروش‌هاست.")
            Bullet("اپ هر دو را جدا برچسب می‌زند تا مفهوم قاطی نشود.")
        }

        Section("🧪 مدل بک‌تست و paper trading") {
            Bullet("برخورد استاپ و تارگت در یک کندل: اول استاپ (محافظه‌کارانه).")
            Bullet("هزینهٔ رفت‌وبرگشت اسپات ۰.۳٪ و فیوچرز ۲×(کارمزد+۰.۰۵٪) کسر می‌شود.")
            Bullet("معاملهٔ کاغذی شبیه‌سازی است؛ بدون سرمایهٔ واقعی، بدون اجرای سفارش.")
            Bullet("نتایج گذشته تضمین آینده نیست؛ بک‌تست روی همان داده قابل بازتولید است.")
        }

        Section("🔔 هشدارها: صادقانه، نه لحظه‌ای") {
            Bullet("هشدارها با WorkManager و بازهٔ دوره‌ای اجرا می‌شوند؛ real-time نیستند و ممکن است تأخیر داشته باشند.")
            Bullet("اگر مجوز نوتیفیکیشن رد شده باشد، اپ بی‌صدا نمی‌ماند: در مرکز حریم خصوصی نمایش داده می‌شود.")
            Bullet("هر هشدار شامل نماد، دلیل و زمان کندل مولد است.")
        }

        Section("⚠️ ریسک‌ها") {
            Bullet("میم‌کوین‌ها و توکن‌های کم‌نقدینگی می‌توانند در دقیقه‌ها صفر شوند.")
            Bullet("هیچ خروجی این اپ توصیهٔ مالی شخصی یا تضمین سود نیست.")
            Bullet("اپ غیرامانی است: هیچ کلید، seed یا مجوز برداختی دریافت نمی‌کند.")
            Bullet("دادهٔ عمومی ممکن است تأخیر، خطا یا پوشش ناقص داشته باشد.")
        }

        Text(
            "نسخهٔ متدولوژی: Sprint 14 / Stage 3 — اگر موتور یا منبعی عوض شود، این صفحه در همان Commit به‌روز می‌شود.",
            fontSize = 9.sp, color = MeGray
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MeCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MeBlue)
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", fontSize = 10.sp, color = MeGray, lineHeight = 16.sp)
}
