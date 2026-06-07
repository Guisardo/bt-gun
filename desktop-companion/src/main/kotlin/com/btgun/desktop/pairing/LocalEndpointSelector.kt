package com.btgun.desktop.pairing

import java.net.Inet4Address
import java.net.NetworkInterface

data class LocalEndpoint(
    val host: String,
    val port: Int,
    val fallback: Boolean,
    val detail: String,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65_535) { "port must be between 1 and 65535" }
    }
}

class LocalEndpointSelector private constructor(
    private val fixedEndpoint: LocalEndpoint?,
    private val port: Int,
) {
    constructor(port: Int = DEFAULT_PORT) : this(null, port)

    fun bestActiveIpv4(): LocalEndpoint {
        fixedEndpoint?.let { return it }

        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val address = interfaces
            .asSequence()
            .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
            .flatMap { networkInterface -> networkInterface.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { inetAddress -> !inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress }

        return if (address != null) {
            LocalEndpoint(
                host = address.hostAddress,
                port = port,
                fallback = false,
                detail = "active_lan_ipv4",
            )
        } else {
            LocalEndpoint(
                host = "127.0.0.1",
                port = port,
                fallback = true,
                detail = "no_active_lan_ipv4",
            )
        }
    }

    companion object {
        const val DEFAULT_PORT = 41731

        fun fixed(host: String, port: Int): LocalEndpointSelector =
            LocalEndpointSelector(
                fixedEndpoint = LocalEndpoint(
                    host = host,
                    port = port,
                    fallback = false,
                    detail = "fixed_test_endpoint",
                ),
                port = port,
            )
    }
}
