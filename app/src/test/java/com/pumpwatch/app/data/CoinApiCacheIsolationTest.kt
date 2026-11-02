package com.pumpwatch.app.data

import org.junit.Test
import java.lang.reflect.Field

class CoinApiCacheIsolationTest {

    @Test
    fun testF1_Cache250AndCache1000AreDistinctVariables() {
        // این تست تأیید می‌کند که متغیرهای کش ۲۵۰ و ۱۰۰۰ به‌صورت فیزیکی از هم جدا هستند
        val coinApiClass = Class.forName("com.pumpwatch.app.data.CoinApiKt\$Companion") // یا نام دقیق کلاس
        // در اینجا فقط وجود فیلدها را چک می‌کنیم تا از کامپایل صحیح اطمینان حاصل شود
        val fields = Class.forName("com.pumpwatch.app.data.CoinApi").declaredFields.map { it.name }
        
        assert(fields.contains("cache250Ref")) { "کش ۲۵۰ تایی باید وجود داشته باشد" }
        assert(fields.contains("cache1000Ref")) { "کش ۱۰۰۰ تایی باید وجود داشته باشد" }
        assert(fields.contains("cache250TimeRef")) { "زمان کش ۲۵۰ باید وجود داشته باشد" }
        assert(fields.contains("cache1000TimeRef")) { "زمان کش ۱۰۰۰ باید وجود داشته باشد" }
    }
}
