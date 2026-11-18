package com.pumpwatch.app.wallet.domain

import org.junit.Test
import org.junit.Assert.assertEquals

class AddressValidatorMaskTest {
    @Test
    fun `mask normal address`() {
        val addr = "0x1234567890abcdef1234567890abcdef12345678"
        assertEquals("0x1234...5678", AddressValidator.mask(addr))
    }

    @Test
    fun `mask short address`() {
        val addr = "0x123"
        assertEquals("***", AddressValidator.mask(addr))
    }

    @Test
    fun `mask with spaces`() {
        val addr = "  0x1234567890abcdef  "
        assertEquals("0x1234...cdef", AddressValidator.mask(addr))
    }
}
