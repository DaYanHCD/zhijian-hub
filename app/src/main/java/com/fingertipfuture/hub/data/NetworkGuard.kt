package com.fingertipfuture.hub.data

import java.io.IOException
import java.net.InetAddress
import java.net.URI

/**
 * 明文 HTTP 的运行时收紧。
 *
 * Android 的 network-security-config 只能按域名配置，不能写 CIDR；而本 App 要支持用户
 * 随意填写的局域网地址。因此 XML 仍保留明文能力，但所有网络入口统一经过这里：
 * 公网域名必须 HTTPS，HTTP 只允许本机、局域网 IP、`.local` 或单段主机名。
 */
internal object NetworkGuard {

    fun requireAllowed(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw IOException("接口地址不合法")
        val scheme = uri.scheme?.lowercase() ?: throw IOException("接口地址缺少 http:// 或 https://")
        if (scheme == "https") return
        if (scheme != "http") throw IOException("只支持 http:// 或 https://")
        val host = uri.host?.removeSurrounding("[", "]")?.lowercase()
            ?: throw IOException("接口地址缺少主机名")
        if (!isLocalHost(host)) {
            throw IOException("为保护数据，公网地址只允许 HTTPS；明文 HTTP 仅限本机或局域网")
        }
    }

    internal fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host == "0.0.0.0") return true
        if (host.endsWith(".local")) return true
        // 单段主机名（如 nas）允许，但必须先解析，且解析结果全部是内网/回环。
        // 不能因为“没有点”就直接放行，否则 DNS 搜索域可能把它解析到公网。
        if (!host.contains('.')) return resolvesToPrivate(host)
        // 只有真正带冒号的 IPv6 地址才按本地段判断；不能把 fc.example.com
        // 这类公网域名误判成 fc00::/7。
        if (host.contains(':')) {
            val v6 = host.lowercase()
            if (v6 == "::1" || v6.startsWith("fe80:") || v6.startsWith("fc") || v6.startsWith("fd")) {
                return true
            }
        }
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            parts[0] == 127 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 169 && parts[1] == 254)
    }

    private fun resolvesToPrivate(host: String): Boolean {
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        if (addresses.isEmpty()) return false
        return addresses.all { address ->
            address.isLoopbackAddress ||
                address.isSiteLocalAddress ||
                address.isLinkLocalAddress ||
                address.hostAddress?.lowercase()?.startsWith("fc") == true ||
                address.hostAddress?.lowercase()?.startsWith("fd") == true
        }
    }
}
