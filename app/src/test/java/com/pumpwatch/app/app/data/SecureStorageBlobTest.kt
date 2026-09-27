package com.pumpwatch.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🚀 Commit 78: تست‌های pure blob operations (بدون نیاز به Android/Keystore).
 *
 * هدف: اثبات صحت joinRaw/splitRaw/packBlob/unpackBlob.
 * تست‌های KeyPermanentlyInvalidated نیاز به Android instrumentation دارند.
 */
class SecureStorageBlobTest {

    private val storage = SecureStorage

    @Test
    fun `joinRaw concatenates iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(13, 14, 15, 16)
        val raw = storage.joinRaw(iv, ct)
        assertEquals(16, raw.size)
        assertArrayEquals(iv + ct, raw)
    }

    @Test
    fun `splitRaw correctly separates iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(13, 14, 15, 16, 17)
        val raw = iv + ct
        val (splitIv, splitCt) = storage.splitRaw(raw)!!
        assertArrayEquals(iv, splitIv)
        assertArrayEquals(ct, splitCt)
    }

    @Test
    fun `splitRaw returns null for too-short input`() {
        val tooShort = ByteArray(12) // exactly IV size, no ciphertext
        assertNull(storage.splitRaw(tooShort))

        val empty = ByteArray(0)
        assertNull(storage.splitRaw(empty))
    }

    @Test
    fun `packBlob and unpackBlob are inverse operations`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(13, 14, 15, 16, 17, 18, 19, 20)
        val blob = storage.packBlob(iv, ct)
        val (unpackIv, unpackCt) = storage.unpackBlob(blob)!!
        assertArrayEquals(iv, unpackIv)
        assertArrayEquals(ct, unpackCt)
    }

    @Test
    fun `unpackBlob returns null for invalid base64`() {
        assertNull(storage.unpackBlob("not-valid-base64!!!"))
    }

    @Test
    fun `unpackBlob returns null for empty string`() {
        assertNull(storage.unpackBlob(""))
    }

    @Test
    fun `round-trip preserves data integrity`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = ByteArray(100) { (it * 2).toByte() }
        val blob = storage.packBlob(iv, ct)
        val (unpackIv, unpackCt) = storage.unpackBlob(blob)!!
        assertArrayEquals(iv, unpackIv)
        assertArrayEquals(ct, unpackCt)
    }
}
