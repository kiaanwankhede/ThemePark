package com.themepark.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads and writes the save file shared by the app and the widget. */
object SaveCodec {
    const val VERSION = 1

    @Serializable
    private data class SaveFile(val version: Int, val state: ParkState)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: ParkState): String = json.encodeToString(SaveFile.serializer(), SaveFile(VERSION, state))

    fun decode(text: String): ParkState {
        val file = json.decodeFromString(SaveFile.serializer(), text)
        require(file.version == VERSION) { "Unsupported save version ${file.version}" }
        return file.state
    }
}
