package com.fingertipfuture.hub.data

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {

    @Test
    fun `host header keeps non default port`() {
        assertEquals(
            "nas.local:8080",
            WebDavClient.hostHeader(URL("http://nas.local:8080/dav/"), 8080),
        )
    }

    @Test
    fun `host header omits default https port`() {
        assertEquals(
            "dav.example.com",
            WebDavClient.hostHeader(URL("https://dav.example.com/dav/"), 443),
        )
    }

    @Test
    fun `credentials are rejected over remote plain http`() {
        assertFalse(
            WebDavClient.isWebDavCredentialTransportAllowed(
                "http://192.168.1.2:8080/dav/",
                hasCredentials = true,
            ),
        )
        assertTrue(
            WebDavClient.isWebDavCredentialTransportAllowed(
                "https://192.168.1.2:8443/dav/",
                hasCredentials = true,
            ),
        )
        assertTrue(
            WebDavClient.isWebDavCredentialTransportAllowed(
                "http://127.0.0.1:8080/dav/",
                hasCredentials = true,
            ),
        )
    }
}
