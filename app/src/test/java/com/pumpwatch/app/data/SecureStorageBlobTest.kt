package com.pumpwatch.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🚀 Commit 78: تست‌های pure blob operations (بدون نیاز به Android/Keystore).
 *
 * هدف: اثبات صحت joinRaw/splitRaw.
 *
 * نکته: packBlob/unpackBlob به android.util.Base64 وابسته‌اند که در JVM خالص
 * (unit test) در دسترس نیست. آن‌ها wrapperهای ساده‌ای روی یک API مستند
 * هستند و round-trip آن‌ها از طریق تست‌های integration روی دستگاه واقعی
 * پوشش داده می‌شود.
 */
class SecureStorageBlobTest {

    private val storage = SecureStorage

    @Test
    fun `joinRaw concatenates iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(13, 14, 15, 16)
        val raw = storage.joinRaw(iv, ct)
        assert(raw.size == 16)
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

        val oneByte = ByteArray(1)
        assertNull(storage.splitRaw(oneByte))
    }

    @Test
    fun `splitRaw handles various ciphertext lengths`() {
        val iv = ByteArray(12) { it.toByte() }

        // Ciphertext کوچک
        val ctSmall = byteArrayOf(100, 101)
        val (iv1, ct1) = storage.splitRaw(iv + ctSmall)!!
        assertArrayEquals(iv, iv1)
        assertArrayEquals(ctSmall, ct1)

        // Ciphertext بزرگ (مثل یک blob واقعی رمزنگاری‌شده)
        val ctLarge = ByteArray(256) { (it * 7).toByte() }
        val (iv2, ct2) = storage.splitRaw(iv + ctLarge)!!
        assertArrayEquals(iv, iv2)
        assertArrayEquals(ctLarge, ct2)
    }

    @Test
    fun `joinRaw and splitRaw are inverse for typical blob`() {
        val iv = ByteArray(12) { (it + 1).toByte() }
        val ct = ByteArray(50) { (it * 3).toByte() }
        val raw = storage.joinRaw(iv, ct)
        val (splitIv, splitCt) = storage.splitRaw(raw)!!
        assertArrayEquals(iv, splitIv)
        assertArrayEquals(ct, splitCt)
    }

    @Test
    fun `splitRaw with exact IV+1 bytes works`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = byteArrayOf(42) // فقط یک بایت ciphertext
        val raw = iv + ct
        val (splitIv, splitCt) = storage.splitRaw(raw)!!
        assertArrayEquals(iv, splitIv)
        assertArrayEquals(ct, splitCt)
    }
}
