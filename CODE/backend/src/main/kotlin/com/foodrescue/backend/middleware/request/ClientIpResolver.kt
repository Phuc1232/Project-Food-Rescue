// AC-25,26: ClientIpResolver responsibility.
package com.foodrescue.backend.middleware.request

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component
import java.net.InetAddress
@Component
class ClientIpResolver(@Value("\${auth.trusted-proxy-cidrs:}") cidrs: String) {
    private val trusted=cidrs.split(',').filter { it.isNotBlank() }.map { IpAddressMatcher(it.trim()) }
    private fun normalize(value: String): String? = try {
        if (!Regex("[0-9a-fA-F:.]+").matches(value)) null else InetAddress.getByName(value).hostAddress
    } catch (_: Exception) { null }
    fun resolve(request: HttpServletRequest): String {
        val peer=normalize(request.remoteAddr) ?: request.remoteAddr
        if(trusted.none { it.matches(peer) }) return peer
        val chain=request.getHeader("X-Forwarded-For") ?: return peer
        if(chain.length>2048) return peer
        var current=peer
        for(hop in chain.split(',').asReversed()) {
            if(trusted.none { it.matches(current) }) break
            current=normalize(hop.trim()) ?: return peer
        }
        return current
    }
}
