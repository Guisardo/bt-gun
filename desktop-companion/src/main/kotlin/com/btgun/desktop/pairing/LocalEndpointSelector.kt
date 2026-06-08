package com.btgun.desktop.pairing

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.DatagramSocket
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
        configuredHost()?.let { host ->
            return LocalEndpoint(
                host = host,
                port = port,
                fallback = false,
                detail = "configured_host",
            )
        }
        defaultRouteAddress()?.let { address ->
            return LocalEndpoint(
                host = address.hostAddress,
                port = port,
                fallback = false,
                detail = "default_route_ipv4",
            )
        }

        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val candidate = interfaces
            .asSequence()
            .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses
                    .toList()
                    .asSequence()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { address ->
                        val priority = candidatePriority(
                            name = networkInterface.name,
                            displayName = networkInterface.displayName,
                            virtual = networkInterface.isVirtual,
                            pointToPoint = networkInterface.isPointToPoint,
                            multicast = runCatching { networkInterface.supportsMulticast() }.getOrDefault(false),
                            address = address,
                        ) ?: return@mapNotNull null
                        EndpointCandidate(address, priority)
                    }
            }
            .maxByOrNull { candidate -> candidate.priority }

        return if (candidate != null) {
            LocalEndpoint(
                host = candidate.address.hostAddress,
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

    private fun configuredHost(): String? =
        (System.getProperty("btgun.desktop.host") ?: System.getenv("BT_GUN_DESKTOP_HOST"))
            ?.trim()
            ?.takeIf { host -> host.isNotBlank() }

    private fun defaultRouteAddress(): Inet4Address? =
        runCatching {
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress(DEFAULT_ROUTE_PROBE_HOST, DEFAULT_ROUTE_PROBE_PORT))
                socket.localAddress as? Inet4Address
            }
        }.getOrNull()
            ?.takeIf { address -> usableAddress(address) }

    private data class EndpointCandidate(
        val address: Inet4Address,
        val priority: Int,
    )

    companion object {
        const val DEFAULT_PORT = 41731
        private const val DEFAULT_ROUTE_PROBE_HOST = "8.8.8.8"
        private const val DEFAULT_ROUTE_PROBE_PORT = 53

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

        internal fun candidatePriority(
            name: String,
            displayName: String?,
            virtual: Boolean,
            pointToPoint: Boolean,
            multicast: Boolean,
            address: Inet4Address,
        ): Int? {
            if (!usableAddress(address) || virtual || pointToPoint || excludedInterfaceName(name, displayName)) {
                return null
            }

            val lowerName = name.lowercase()
            val lowerDisplay = displayName.orEmpty().lowercase()
            var priority = 0
            if (address.isSiteLocalAddress) priority += 100
            if (address.hostAddress.startsWith("192.168.")) priority += 30
            if (address.hostAddress.startsWith("10.")) priority += 20
            if (lowerName.startsWith("en") || lowerName.startsWith("wl") || lowerDisplay.contains("wi-fi") || lowerDisplay.contains("wifi")) {
                priority += 25
            }
            if (multicast) priority += 5
            return priority
        }

        private fun usableAddress(address: Inet4Address): Boolean =
            !address.isAnyLocalAddress &&
                !address.isLoopbackAddress &&
                !address.isLinkLocalAddress &&
                !address.isMulticastAddress

        private fun excludedInterfaceName(name: String, displayName: String?): Boolean {
            val combined = "${name.lowercase()} ${displayName.orEmpty().lowercase()}"
            return listOf(
                "bridge",
                "docker",
                "feth",
                "hyper-v",
                "llw",
                "utun",
                "vbox",
                "vethernet",
                "vmnet",
                "vmware",
                "vnic",
                "wsl",
            ).any(combined::contains) || name.lowercase().startsWith("br-")
        }
    }
}
