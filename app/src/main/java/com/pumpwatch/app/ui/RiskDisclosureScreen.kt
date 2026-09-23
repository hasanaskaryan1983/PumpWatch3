package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VCard = Color(0xFF1A2230)
private val VGold = Color(0xFFFFC107)
private val VRed = Color(0xFFFF5252)
private val VGreen = Color(0xFF00E676)
private val VBlue = Color(0xFF40C4FF)
private val VGray = Color(0xFF8B949E)

/**
 * 🚀 Commit 55 (فاز ۰ برنامهٔ اجرایی، §2.2): صفحهٔ صداقت و محدودیت‌ها.
 *
 * صداقت دربارهٔ:
 * - این اپ توصیهٔ مالی نیست
 * - دادهٔ آن‌چین عمومی، نه کامل
 * - تحلیل‌ها همبستگی‌اند، نه علیت
 * - مرزهای منابع رایگان (عمق تاریخچه، Rate limit)
 */
@Composable
fun RiskDisclosureScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📖 صداقت و محدودیت‌ها", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VGold)
        Text(
            "این صفحه صادقانه می‌گوید این اپ چه می‌تواند، چه نمی‌تواند، و چه نباید با آن کرد.",
            fontSize = 11.sp, color = VGray
        )

        // کارت ۱: توصیهٔ مالی نیست
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🚫 این اپ توصیهٔ مالی نیست", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VRed)
                Text(
                    "PumpWatch هیچ‌گاه نمی‌گوید «بخر» یا «بفروش». هر خروجی صرفاً مشاهدهٔ دادهٔ عمومی روی زنجیره است. " +
                    "تصمیم خرید/فروش، مسئولیت کامل شماست و می‌تواند به از دست رفتن سرمایه منجر شود.",
                    fontSize = 10.sp, color = VGray
                )
            }
        }

        // کارت ۲: دادهٔ آن‌چین محدود است
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔍 دادهٔ آن‌چین عمومی، نه کامل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VBlue)
                Text(
                    "• فقط توکن‌هایی که قیمتشان در منابع عمومی موجود است دیده می‌شوند\n" +
                    "• قیمت‌ها از منابع ثالث (GeckoTerminal، Blockscout، RPC) می‌آیند و ممکن است تأخیر یا خطا داشته باشند\n" +
                    "• موجودی‌ها لحظه‌ای‌اند؛ انتقال‌های بین کیف‌ها ممکن است بین اسکن‌ها دیده نشوند\n" +
                    "• منابع رایگان Rate limit دارند و در فشار شبکه ممکن است ناقص پاسخ دهند",
                    fontSize = 10.sp, color = VGray, lineHeight = 15.sp
                )
            }
        }

        // کارت ۳: تحلیل‌ها همبستگی‌اند
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔗 همبستگی ≠ علیت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VGreen)
                Text(
                    "• برچسب «🎯 کف‌خر» یعنی ورود یک کیف حوالی کفِ بازه — نه اثبات اینکه مطلع بوده\n" +
                    "• «رانتی» یک الگوی رفتاری است، نه هویت\n" +
                    "• کیف‌های نهنگ ممکن است چند نفر یا یک ربات باشند\n" +
                    "• هیچ برچسبی تضمین نتیجه در پامپ بعدی نیست",
                    fontSize = 10.sp, color = VGray, lineHeight = 15.sp
                )
            }
        }

        // کارت ۴: مرزهای فنی
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⏱️ مرزهای منابع رایگان", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VGold)
                Text(
                    "• موتور ۶ (جنایت‌شناسی): با منابع رایگان موبایل، معمولاً ۲۴–۴۸ ساعت اخیر قابل تحلیل است. بازه‌های قدیمی‌تر نیاز به ایندکسر سرور دارند\n" +
                    "• موتور ۵ (تاریخچه کیف): تا عمقی که RPC عمومی اجازه می‌دهد (معمولاً ۱۰۰۰ تراکنش اخیر)\n" +
                    "• هشدارهای خودکار: best-effort، نه لحظه‌ای (هر ۶ ساعت اسکن می‌شوند)\n" +
                    "• در صورت Rate limit یا خطای شبکه، اپ صادقانه می‌گوید چه چیزی ناموفق بوده — نه عدد جعلی",
                    fontSize = 10.sp, color = VGray, lineHeight = 15.sp
                )
            }
        }

        // کارت ۵: توصیه‌های عملی
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✅ استفادهٔ هوشمندانه", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VGreen)
                Text(
                    "• این اپ را **یکی از چند ابزار** تصمیم‌گیری بدانید، نه تنها ابزار\n" +
                    "• روی نهنگ‌های «شکارشده» هرگز کورکورانه copy-trade نکنید\n" +
                    "• همیشه خودتان قرارداد توکن، تیم، و نقدینگی را بررسی کنید\n" +
                    "• مبلغی را وارد نکنید که تحمل از دست دادنش را ندارید\n" +
                    "• خروجی‌های این اپ را با دوستان به‌عنوان «پیشنهاد خرید» به اشتراک نگذارید",
                    fontSize = 10.sp, color = VGray, lineHeight = 15.sp
                )
            }
        }

        // کارت ۶: مسئولیت
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1F1F)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚠️ مسئولیت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VRed)
                Text(
                    "استفاده از این اپ به معنی پذیرش این است که:\n" +
                    "• توسعه‌دهنده هیچ مسئولیتی در قبال ضررهای مالی ندارد\n" +
                    "• دادهٔ ارائه‌شده «همان‌گونه که هست» ارائه می‌شود (as-is)\n" +
                    "• شما خودتان مسئول تحقیق (DYOR) هستید\n" +
                    "• بازار رمزارز ذاتاً پرنوسان و پرریسک است",
                    fontSize = 10.sp, color = Color(0xFFFFB4B4), lineHeight = 15.sp
                )
            }
        }
    }
}
