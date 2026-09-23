package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.MathUtils
import java.util.Random

class AudioManager {

    private var pickup: Sound? = null
    private var oxygen: Sound? = null
    private var crash: Sound? = null
    private var click: Sound? = null
    private var ambience: Sound? = null
    private var achieve: Sound? = null
    private var ambiencePlaying = false
    private var initialized = false
    private var prefs: Preferences? = null

    var muted: Boolean = false
        set(value) {
            field = value
            prefs?.putBoolean("muted", value)?.flush()
            if (value) {
                ambience?.stop()
                ambiencePlaying = false
            } else {
                startAmbience()
            }
        }

    fun init() {
        if (initialized) return
        initialized = true
        prefs = Gdx.app.getPreferences("depthdiver-settings")
        muted = prefs?.getBoolean("muted", false) ?: false
        pickup = generateSound("pickup", 880f, 0.14f)
        oxygen = generateSound("oxygen", 640f, 0.2f)
        crash = generateSound("crash", 120f, 0.35f)
        click = generateSound("click", 1400f, 0.05f)
        achieve = generateSound("achieve", 1150f, 0.22f)
        ambience = generateAmbience()
        if (!muted) startAmbience()
    }

    private fun startAmbience() {
        if (ambiencePlaying) return
        ambience?.loop(0.25f)
        ambiencePlaying = true
    }

    fun toggleMute() {
        muted = !muted
    }

    fun playPickup() {
        if (!muted) pickup?.play(0.6f)
    }

    fun playOxygen() {
        if (!muted) oxygen?.play(0.7f)
    }

    fun playCrash() {
        if (!muted) crash?.play(0.9f)
    }

    fun playClick() {
        if (!muted) click?.play(0.5f)
    }

    fun playAchieve() {
        if (!muted) achieve?.play(0.7f)
    }

    fun dispose() {
        ambience?.stop()
        pickup?.dispose()
        oxygen?.dispose()
        crash?.dispose()
        click?.dispose()
        ambience?.dispose()
        achieve?.dispose()
        pickup = null
        oxygen = null
        crash = null
        click = null
        ambience = null
        achieve = null
        initialized = false
    }

    private fun generateSound(name: String, freq: Float, duration: Float): Sound? {
        return try {
            val wav = generateWav(freq, duration)
            val file = Gdx.files.local("audio-runtime/$name.wav")
            file.writeBytes(wav, false)
            Gdx.audio.newSound(file)
        } catch (e: Exception) {
            null
        }
    }

    /** 14s calming underwater ambience: low drone with a slow tide LFO plus sparse bubbling blips, looped. */
    private fun generateAmbience(): Sound? {
        return try {
            val sampleRate = 22050
            val duration = 14f
            val samples = (sampleRate * duration).toInt()
            val data = wavContainer(samples)
            val rnd = Random(7)
            var nextBlip = 1.2f
            var blipT = -1f
            for (i in 0 until samples) {
                val t = i / sampleRate.toFloat()
                val tide = 0.7f + 0.3f * MathUtils.sin(MathUtils.PI2 * 0.08f * t)
                var wave = (MathUtils.sin(MathUtils.PI2 * 55f * t) * 0.30f +
                    MathUtils.sin(MathUtils.PI2 * 110f * t) * 0.11f) * tide
                if (blipT >= 0f) {
                    val age = t - blipT
                    if (age < 0.4f) {
                        val env = 1f - age / 0.4f
                        val f = 380f + 900f * age
                        wave += MathUtils.sin(MathUtils.PI2 * f * t) * env * 0.16f
                    } else {
                        blipT = -1f
                    }
                }
                if (blipT < 0f && t >= nextBlip) {
                    blipT = t
                    nextBlip = t + 1.2f + rnd.nextFloat() * 1.8f
                }
                val sample = (wave * Short.MAX_VALUE * 0.6f).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
                data[44 + i * 2] = (sample and 0xFF).toByte()
                data[44 + i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            val file = Gdx.files.local("audio-runtime/ambience.wav")
            file.writeBytes(data, false)
            Gdx.audio.newSound(file)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateWav(freq: Float, duration: Float): ByteArray {
        val sampleRate = 22050
        val samples = (sampleRate * duration).toInt()
        val data = wavContainer(samples)
        for (i in 0 until samples) {
            val t = i / sampleRate.toFloat()
            val env = (1f - t / duration).coerceIn(0f, 1f)
            val wave = MathUtils.sin(MathUtils.PI2 * freq * t) * 0.6f +
                MathUtils.sin(MathUtils.PI2 * freq * 2f * t) * 0.2f
            val sample = (wave * env * Short.MAX_VALUE).toInt()
            data[44 + i * 2] = (sample and 0xFF).toByte()
            data[44 + i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return data
    }

    private fun wavContainer(samples: Int): ByteArray {
        val data = ByteArray(44 + samples * 2)
        writeAscii(data, 0, "RIFF")
        writeIntLe(data, 4, 36 + samples * 2)
        writeAscii(data, 8, "WAVE")
        writeAscii(data, 12, "fmt ")
        writeIntLe(data, 16, 16)
        writeShortLe(data, 20, 1)
        writeShortLe(data, 22, 1)
        writeIntLe(data, 24, 22050)
        writeIntLe(data, 28, 22050 * 2)
        writeShortLe(data, 32, 2)
        writeShortLe(data, 34, 16)
        writeAscii(data, 36, "data")
        writeIntLe(data, 40, samples * 2)
        return data
    }

    private fun writeAscii(array: ByteArray, offset: Int, text: String) {
        for (i in text.indices) {
            array[offset + i] = text[i].code.toByte()
        }
    }

    private fun writeShortLe(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeIntLe(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
        array[offset + 2] = ((value shr 16) and 0xFF).toByte()
        array[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}