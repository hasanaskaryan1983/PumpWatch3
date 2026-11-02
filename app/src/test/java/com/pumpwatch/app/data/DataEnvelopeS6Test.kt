package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DataEnvelopeS6Test {

    @Test
    fun testS6_PriceMaxAgeIs10Minutes() {
        // ۱۰ دقیقه = ۶۰۰ ثانیه
        assertEquals("حداکثر سن مجاز قیمت باید ۶۰۰ ثانیه (۱۰ دقیقه) باشد", 600L, MAX_AGE_PRICE_SEC)
    }

    @Test
    fun testS6_MetadataMaxAgeIs6Hours() {
        // ۶ ساعت = ۲۱۶۰۰ ثانیه
        assertEquals("حداکثر سن مجاز متادیتا باید ۲۱۶۰۰ ثانیه (۶ ساعت) باشد", 21600L, MAX_AGE_METADATA_SEC)
    }
}
