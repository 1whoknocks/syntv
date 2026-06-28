package com.synocam.api

import com.synocam.data.NasConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class SurveillanceException(message: String) : Exception(message)

/**
 * Minimal client for the three Surveillance Station Web API calls this app needs:
 *  1. SYNO.API.Auth  Login   -> session id (sid)
 *  2. SYNO.SurveillanceStation.Camera  List          -> cameras
 *  3. SYNO.SurveillanceStation.Camera  GetLiveViewPath -> per-camera RTSP url
 *
 * The sid is cached; if a request fails with a session error the sid is dropped and
 * the call retried once after re-login.
 */
class SurveillanceClient(private val config: NasConfig) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = buildHttpClient(config.useHttps)

    @Volatile
    private var sid: String? = null

    suspend fun login(): String = withContext(Dispatchers.IO) {
        val url = base("/webapi/auth.cgi")
            .addQueryParameter("api", "SYNO.API.Auth")
            .addQueryParameter("method", "Login")
            .addQueryParameter("version", "6")
            .addQueryParameter("account", config.account)
            .addQueryParameter("passwd", config.password)
            .addQueryParameter("session", "SurveillanceStation")
            .addQueryParameter("format", "sid")
            .build()
        val resp = json.decodeFromString(LoginResponse.serializer(), get(url.toString()))
        if (!resp.success || resp.data?.sid.isNullOrBlank()) {
            throw SurveillanceException(authErrorMessage(resp.error?.code))
        }
        resp.data!!.sid.also { sid = it }
    }

    suspend fun listCameras(): List<Camera> = withContext(Dispatchers.IO) {
        val token = sid ?: login()
        val url = base("/webapi/entry.cgi")
            .addQueryParameter("api", "SYNO.SurveillanceStation.Camera")
            .addQueryParameter("method", "List")
            .addQueryParameter("version", "9")
            .addQueryParameter("privCamType", "1")
            .addQueryParameter("camStm", "0")
            .addQueryParameter("_sid", token)
            .build()
        val resp = json.decodeFromString(CameraListResponse.serializer(), get(url.toString()))
        if (!resp.success) {
            if (resp.error?.code in SESSION_ERRORS) {
                sid = null
                login()
                return@withContext listCameras()
            }
            throw SurveillanceException("Failed to list cameras (code ${resp.error?.code}).")
        }
        resp.data?.cameras.orEmpty()
    }

    /** Returns the live-view RTSP url for one camera (credentials embedded by the NAS). */
    suspend fun liveViewRtsp(cameraId: Int): String = withContext(Dispatchers.IO) {
        val token = sid ?: login()
        val url = base("/webapi/entry.cgi")
            .addQueryParameter("api", "SYNO.SurveillanceStation.Camera")
            .addQueryParameter("method", "GetLiveViewPath")
            .addQueryParameter("version", "9")
            .addQueryParameter("idList", cameraId.toString())
            .addQueryParameter("_sid", token)
            .build()
        val resp = json.decodeFromString(LiveViewPathResponse.serializer(), get(url.toString()))
        if (!resp.success) {
            if (resp.error?.code in SESSION_ERRORS) {
                sid = null
                login()
                return@withContext liveViewRtsp(cameraId)
            }
            throw SurveillanceException("Failed to get stream path (code ${resp.error?.code}).")
        }
        resp.data.firstOrNull()?.rtspPath?.takeIf { it.isNotBlank() }
            ?: throw SurveillanceException("No RTSP path returned for camera $cameraId.")
    }

    private fun base(path: String) = (config.baseUrl + path).toHttpUrl().newBuilder()

    private fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SurveillanceException("HTTP ${response.code} from NAS.")
            return response.body?.string() ?: throw SurveillanceException("Empty response from NAS.")
        }
    }

    companion object {
        // DSM session/auth error codes that mean "log in again".
        private val SESSION_ERRORS = setOf(105, 106, 107, 119)

        fun authErrorMessage(code: Int?): String = when (code) {
            400, 401 -> "Wrong account or password."
            402 -> "Account has no Surveillance Station permission."
            403, 404 -> "Two-factor (OTP) is enabled. Use a dedicated user without 2FA."
            else -> "Login failed (code ${code ?: "unknown"})."
        }

        private fun buildHttpClient(useHttps: Boolean): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            if (useHttps) {
                // Synology boxes ship self-signed certs by default. This app only ever talks
                // to a user-entered host on the local network, so we trust it explicitly
                // rather than forcing the user to install a cert. (LAN-only by design.)
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
                val ssl = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<javax.net.ssl.TrustManager>(trustAll), SecureRandom())
                }
                builder.sslSocketFactory(ssl.socketFactory, trustAll)
                builder.hostnameVerifier { _, _ -> true }
            }
            return builder.build()
        }
    }
}
