package com.synocam.api

import kotlinx.serialization.Serializable

/**
 * Data shapes for the Synology Surveillance Station Web API responses we use.
 * Every response is `{ "success": bool, "data": {...}, "error": { "code": int } }`.
 * Unknown fields are ignored (Json.ignoreUnknownKeys), so these only declare what we read.
 */

@Serializable
data class SynoError(val code: Int = 0)

@Serializable
data class LoginData(val sid: String = "")

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val data: LoginData? = null,
    val error: SynoError? = null,
)

@Serializable
data class Camera(
    val id: Int = 0,
    val newName: String? = null,
    val name: String? = null,
    val enabled: Boolean = true,
    val status: Int = -1,
    val vendor: String? = null,
    val model: String? = null,
) {
    val displayName: String
        get() = (newName ?: name)?.takeIf { it.isNotBlank() } ?: "Camera $id"

    /** Surveillance Station reports status 1 = "Normal". Treat anything else as not-yet-confirmed. */
    val isOnline: Boolean get() = status == 1
}

@Serializable
data class CameraListData(
    val total: Int = 0,
    val cameras: List<Camera> = emptyList(),
)

@Serializable
data class CameraListResponse(
    val success: Boolean = false,
    val data: CameraListData? = null,
    val error: SynoError? = null,
)

@Serializable
data class LiveViewPath(
    val id: Int = 0,
    val rtspPath: String = "",
    val rtspOverHttpPath: String = "",
    val mjpegHttpPath: String = "",
    val multicstPath: String = "",
)

@Serializable
data class LiveViewPathResponse(
    val success: Boolean = false,
    val data: List<LiveViewPath> = emptyList(),
    val error: SynoError? = null,
)
