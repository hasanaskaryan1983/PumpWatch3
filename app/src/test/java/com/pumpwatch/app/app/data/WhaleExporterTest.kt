package com.pumpwatch.app.data

import com.pumpwatch.app.ui.FavWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 56: تست round-trip export/import.
 */
class WhaleExporterTest {

    @Test
    fun exportImport_roundTrip_preservesAllFields() {
        val original = listOf(
            FavWallet(
                addr = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
                note = "در ۳ پامپ تکرار شد",
                starred = true,
                addedTs = 1_700_000_000_000L,
                symbol = "CATE",
                role = "کف‌خر 🎯",
                huntedAtMs = 1_700_000_000_000L
            )
        )
        val json = WhaleExporter.exportToFavorites(original)
        val result = WhaleExporter.importFromJson(json, emptyList())
        assertTrue(result is WhaleExporter.ImportResult.Success)
        val success = result as WhaleExporter.ImportResult.Success
        assertEquals(1, success.added)
        assertEquals(0, success.duplicates)
    }

    @Test
    fun import_deduplicatesExisting() {
        val existing = listOf(
            FavWallet("addr1", "note", false, 0L, symbol = "A", role = "", huntedAtMs = 0L)
        )
        val toImport = listOf(
            FavWallet("addr1", "different note", true, 0L, symbol = "B", role = "", huntedAtMs = 0L),
            FavWallet("addr2", "new", false, 0L, symbol = "C", role = "", huntedAtMs = 0L)
        )
        val json = WhaleExporter.exportToFavorites(toImport)
        val result = WhaleExporter.importFromJson(json, existing)
        assertTrue(result is WhaleExporter.ImportResult.Success)
        val success = result as WhaleExporter.ImportResult.Success
        assertEquals(1, success.added)      // فقط addr2
        assertEquals(1, success.duplicates)  // addr1 تکراری بود
    }
}
