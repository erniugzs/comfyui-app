package com.huilian.comfymobile.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class NetworkScanner {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun scanForComfyUI(): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val semaphore = Semaphore(50)

        val localIp = getLocalIpPrefix() ?: return@withContext emptyList()

        val jobs = (1..254).map { i ->
            async {
                semaphore.withPermit {
                    val ip = "\$localIp.\$i"
                    if (isComfyUIServer(ip, 8188)) {
                        results.add("http://\$ip:8188/")
                    }
                }
            }
        }

        jobs.awaitAll()
        results
    }

    private fun getLocalIpPrefix(): String? {
        return try {
            val ip = InetAddress.getLocalHost().hostAddress
            ip.substringBeforeLast(".")
        } catch (e: Exception) {
            null
        }
    }

    private fun isComfyUIServer(ip: String, port: Int): Boolean {
        return try {
            val url = URL("http://\$ip:\$port/system_stats")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
