package com.themepark.app

import android.content.Context
import android.util.AtomicFile
import com.themepark.core.Outcome
import com.themepark.core.ParkState
import com.themepark.core.SaveCodec
import java.io.File

/**
 * The one save file, shared by the game screen and the widget. Both run in the app's process,
 * so a single lock is enough to keep a widget tap and a game tap from overwriting each other.
 */
class SaveStore private constructor(private val file: AtomicFile) {

    fun load(): ParkState = synchronized(this) { read() }

    /** Applies an action and saves the result if it succeeded. */
    fun act(action: (ParkState) -> Outcome): Outcome = synchronized(this) {
        val outcome = action(read())
        if (outcome is Outcome.Ok) write(outcome.state)
        outcome
    }

    private fun read(): ParkState {
        if (!file.baseFile.exists()) return ParkState.newGame(System.currentTimeMillis()).also(::write)
        return SaveCodec.decode(file.readFully().decodeToString())
    }

    private fun write(state: ParkState) {
        val out = file.startWrite()
        try {
            out.write(SaveCodec.encode(state).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    companion object {
        @Volatile private var instance: SaveStore? = null

        fun get(context: Context): SaveStore =
            instance ?: synchronized(this) {
                instance ?: SaveStore(AtomicFile(File(context.applicationContext.filesDir, "park.json")))
                    .also { instance = it }
            }
    }
}
