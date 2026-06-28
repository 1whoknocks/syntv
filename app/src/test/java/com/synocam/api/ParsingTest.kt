package com.synocam.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun parsesLoginSid() {
        val body = """{"data":{"sid":"abc123"},"success":true}"""
        val resp = json.decodeFromString(LoginResponse.serializer(), body)
        assertTrue(resp.success)
        assertEquals("abc123", resp.data?.sid)
    }

    @Test
    fun parsesLoginError() {
        val body = """{"error":{"code":400},"success":false}"""
        val resp = json.decodeFromString(LoginResponse.serializer(), body)
        assertFalse(resp.success)
        assertEquals(400, resp.error?.code)
        assertEquals("Wrong account or password.", SurveillanceClient.authErrorMessage(resp.error?.code))
    }

    @Test
    fun parsesCameraListWithUnknownFields() {
        val body = """
            {"data":{"total":2,"cameras":[
              {"id":1,"newName":"Front Door","enabled":true,"status":1,"extraField":"ignored"},
              {"id":2,"name":"Backyard","enabled":false,"status":3}
            ]},"success":true}
        """.trimIndent()
        val resp = json.decodeFromString(CameraListResponse.serializer(), body)
        val cams = resp.data?.cameras.orEmpty()
        assertEquals(2, cams.size)
        assertEquals("Front Door", cams[0].displayName)
        assertTrue(cams[0].isOnline)
        assertEquals("Backyard", cams[1].displayName)
        assertFalse(cams[1].enabled)
    }

    @Test
    fun parsesLiveViewPath() {
        val body = """
            {"data":[{"id":1,"rtspPath":"rtsp://user:pass@192.168.1.100:554/Sms=1.unicast","mjpegHttpPath":"http://x/y"}],"success":true}
        """.trimIndent()
        val resp = json.decodeFromString(LiveViewPathResponse.serializer(), body)
        assertTrue(resp.success)
        assertEquals("rtsp://user:pass@192.168.1.100:554/Sms=1.unicast", resp.data.first().rtspPath)
    }
}
