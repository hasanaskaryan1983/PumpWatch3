package com.pumpwatch.app.data

import org.junit.Assert.*
import org.junit.Test

class SecureStorageHelpersTest {

    @Test
    fun `joinRaw concatenates iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(20, 21, 22, 23)
        val joined = SecureStorage.joinRaw(iv, ct)
        assertEquals(16, joined.size)
        assertEquals(1, joined[0])
        assertEquals(12, joined[11])
        assertEquals(20, joined[12])
        assertEquals(23, joined[15])
    }

    @Test
    fun `splitRaw correctly separates iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(20, 21, 22, 23)
        val result = SecureStorage.splitRaw(iv + ct)
        assertNotNull(result)
        val (splitIv, splitCt) = result!!
        assertArrayEquals(iv, splitIv)
        assertArrayEquals(ct, splitCt)
    }

    @Test
    fun `splitRaw returns null for too-short input`() {
        assertNull(SecureStorage.splitRaw(ByteArray(12)))  // exactly IV_BYTES, no ciphertext
        assertNull(SecureStorage.splitRaw(ByteArray(5)))
        assertNull(SecureStorage.splitRaw(ByteArray(0)))
    }

    // ❌ حذف شد: packBlob/unpackBlob از android.util.Base64 استفاده می‌کنند
    // که در JVM test کار نمی‌کند. نیاز به Robolectric یا androidTest دارد.
}
