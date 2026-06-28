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

    suspend fun listCameras(): List<Camera> = listCameras(reloginAllowed = true)

    private suspend fun listCameras(reloginAllowed: Boolean): List<Camera> = withContext(Dispatchers.IO) {
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
            // Re-login and retry ONCE for a genuinely-expired session — never loop.
            if (reloginAllowed && isSessionError(resp.error?.code)) {
                sid = null
                login()
                return@withContext listCameras(reloginAllowed = false)
            }
            throw SurveillanceException(cameraErrorMessage(resp.error?.code))
        }
        resp.data?.cameras.orEmpty()
    }

    /** Returns the live-view RTSP url for one camera (credentials embedded by the NAS). */
    suspend fun liveViewRtsp(cameraId: Int): String = liveViewRtsp(cameraId, reloginAllowed = true)

    private suspend fun liveViewRtsp(cameraId: Int, reloginAllowed: Boolean): String = withContext(Dispatchers.IO) {
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
            // 105 = insufficient privilege (a real, terminal error) — must NOT be retried as a
            // session error, or every camera spins forever re-logging in. Only retry once for a
            // truly expired session.
            if (reloginAllowed && isSessionError(resp.error?.code)) {
                sid = null
                login()
                return@withContext liveViewRtsp(cameraId, reloginAllowed = false)
            }
            throw SurveillanceException(pathErrorMessage(resp.error?.code))
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
        // DSM error codes that mean "the session is gone, log in again".
        // IMPORTANT: 105 (insufficient privilege) is deliberately NOT here. It is a terminal
        // permission error, and re-logging in returns the same 105 — treating it as a session
        // error caused an infinite re-login loop that hung the camera wall on "Connecting…".
        private val SESSION_ERRORS = setOf(106, 107, 119)

        /** True only for codes where dropping the sid and logging in again can actually help. */
        internal fun isSessionError(code: Int?): Boolean = code in SESSION_ERRORS

        fun authErrorMessage(code: Int?): String = when (code) {
            400, 401 -> "Wrong account or password."
            402 -> "Account has no Surveillance Station permission."
            403, 404 -> "Two-factor (OTP) is enabled. Use a dedicated user without 2FA."
            else -> "Login failed (code ${code ?: "unknown"})."
        }

        fun cameraErrorMessage(code: Int?): String = when (code) {
            105 -> "This account lacks Surveillance Station privilege to list cameras."
            else -> "Failed to list cameras (code ${code ?: "unknown"})."
        }

        fun pathErrorMessage(code: Int?): String = when (code) {
            // The most common real-world cause, now that login/list work: the dedicated user is a
            // Surveillance Station "Viewer". GetLiveViewPath (which hands back the RTSP URL with the
            // camera's own credentials embedded) requires "Manager" privilege.
            105 -> "No live-view permission for this camera (needs Surveillance Station Manager privilege)."
            else -> "Failed to get stream path (code ${code ?: "unknown"})."
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
