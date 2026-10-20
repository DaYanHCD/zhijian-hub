package com.fingertipfuture.hub.data

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGuardTest {

    @Test
    fun `https is always allowed`() {
        NetworkGuard.requireAllowed("https://api.example.com/v1")
    }

    @Test
    fun `private and loopback http is allowed`() {
        NetworkGuard.requireAllowed("http://127.0.0.1:11434/v1")
        NetworkGuard.requireAllowed("http://10.0.2.2:8099/v1")
        NetworkGuard.requireAllowed("http://192.168.1.20:8080")
        NetworkGuard.requireAllowed("http://172.16.3.4")
        NetworkGuard.requireAllowed("http://ollama.local")
        // 单段主机名要先能解析到内网；测试机没有 nas 这条 DNS 记录，不在此断言。
    }

    @Test
    fun `public cleartext http is rejected`() {
        assertTrue(rejects("http://api.example.com/v1"))
        assertTrue(rejects("http://8.8.8.8"))
        assertTrue(rejects("http://172.32.0.1"))
        // 不能被 IPv6 fc00::/7 的前缀判断误伤成内网域名。
        assertTrue(rejects("http://fc.example.com/v1"))
        assertTrue(rejects("http://fd.example.com/v1"))
        assertFalse(NetworkGuard.isLocalHost("example.com"))
    }

    private fun rejects(url: String): Boolean = runCatching {
        NetworkGuard.requireAllowed(url)
    }.exceptionOrNull() is IOException
}
