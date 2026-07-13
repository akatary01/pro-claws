package com.vendistri.operations.features.navigation

import com.vendistri.operations.BuildConfig
import com.vendistri.operations.network.NetworkConfig

object DebugRouteSimulationConfig {
    val isEnabled: Boolean
        get() = BuildConfig.DEBUG &&
            (isLocalDeploymentUrl(NetworkConfig.backendUrl) || isLocalDeploymentUrl(NetworkConfig.appWebUrl))

    private fun isLocalDeploymentUrl(url: String): Boolean {
        return url.contains("localhost", ignoreCase = true) ||
            url.contains("127.0.0.1") ||
            url.contains("10.0.2.2")
    }
}
