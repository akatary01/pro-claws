package com.vendistri.operations.features.location_contact

import com.vendistri.operations.network.NetworkConfig
import java.net.URLEncoder

object ContactServiceFormUrl {
    fun forMachine(machineId: String, appWebUrl: String = NetworkConfig.serviceFormWebUrl): String {
        return "${appWebUrl.trimEnd('/')}/service-forms?machineId=${machineId.urlEncoded()}"
    }
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
