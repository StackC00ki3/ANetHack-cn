package com.yywspace.anethack.keybord

import java.util.Base64

/**
 * Versioned short code for sharing a bottom command bar layout.
 * Format: `NHB<version>-<payload>` where the payload is the URL-safe Base64
 * (no padding) of the UTF-8 encoded [TouchCommandBar.serialize] output, so
 * per-row enabled flags and visible limits are carried along.
 */
object CommandBarCode {
    const val CODE_VERSION = 1
    private const val PREFIX = "NHB"

    sealed interface DecodeResult {
        data class Ok(val config: String) : DecodeResult
        object Invalid : DecodeResult
        data class TooNew(val version: Int) : DecodeResult
    }

    fun encode(config: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(config.toByteArray(Charsets.UTF_8))
        return "$PREFIX$CODE_VERSION-$payload"
    }

    fun decode(code: String): DecodeResult {
        val trimmed = code.trim()
        if (!trimmed.startsWith(PREFIX)) return DecodeResult.Invalid
        val dash = trimmed.indexOf('-')
        if (dash < 0) return DecodeResult.Invalid
        val version = trimmed.substring(PREFIX.length, dash).toIntOrNull()
            ?: return DecodeResult.Invalid
        if (version < 1) return DecodeResult.Invalid
        if (version > CODE_VERSION) return DecodeResult.TooNew(version)
        val payload = trimmed.substring(dash + 1)
        if (payload.isEmpty()) return DecodeResult.Invalid
        val bytes = try {
            Base64.getUrlDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Invalid
        }
        val config = String(bytes, Charsets.UTF_8)
        return if (TouchCommandBar.parseRows(config).isEmpty()) DecodeResult.Invalid
        else DecodeResult.Ok(config)
    }
}
