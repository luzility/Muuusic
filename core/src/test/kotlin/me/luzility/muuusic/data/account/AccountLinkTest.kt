package me.luzility.muuusic.data.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountLinkTest {
    @Test fun uriRoundTrips() {
        val offer = AccountLink.newOffer("192.168.1.20", 41234)
        val parsed = AccountLink.parse(AccountLink.uri(offer))!!
        assertEquals(offer.host, parsed.host)
        assertEquals(offer.port, parsed.port)
        assertEquals(offer.token, parsed.token)
        assertArrayEquals(offer.key, parsed.key)
    }

    @Test fun sealedCookieOpensOnlyWithTheSameOffer() {
        val offer = AccountLink.newOffer("192.168.1.20", 41234)
        val line = AccountLink.seal(AccountLink.parse(AccountLink.uri(offer))!!, "SAPISID=abc; SID=def")
        assertEquals("SAPISID=abc; SID=def", AccountLink.open(offer, line))
        assertNull(AccountLink.open(AccountLink.newOffer("192.168.1.20", 41234), line))
    }

    @Test fun foreignTextIsNotAnOffer() {
        assertNull(AccountLink.parse("https://music.youtube.com"))
        assertNull(AccountLink.parse("muuusic://link?h=x&p=1"))
    }
}
