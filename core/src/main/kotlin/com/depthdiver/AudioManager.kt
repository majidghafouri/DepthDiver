package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.audio.Sound
import com.depthdiver.audio.LayerDirector
import com.depthdiver.audio.LayerMix
import com.depthdiver.audio.OnePole
import com.depthdiver.audio.RateLimiter
import com.depthdiver.audio.attackDecay
import com.depthdiver.audio.heartbeatPeriod
import com.depthdiver.audio.linearFade
import com.depthdiver.audio.noise
import com.depthdiver.audio.saw
import com.depthdiver.audio.sine
import com.depthdiver.audio.synth
import java.util.Random
import kotlin.math.abs

internal fun canStartAmbience(muted: Boolean, playing: Boolean, soundAvailable: Boolean): Boolean =
    !muted && !playing && soundAvailable

class AudioManager {

    private var pickup: Sound? = null
    private var oxygen: Sound? = null
    private var crash: Sound? = null
    private var click: Sound? = null
    private var ambience: Sound? = null
    private var achieve: Sound? = null
    private var alert: Sound? = null
    private var alarm: Sound? = null
    private var shield: Sound? = null
    private var shieldBreak: Sound? = null
    private var combo: Sound? = null
    private var countdown: Sound? = null
    private var bossRoar: Sound? = null
    private var bossHit: Sound? = null
    private var levelUp: Sound? = null
    private var heartbeat: Sound? = null
    private var tensionLayer: Sound? = null
    private var dangerLayer: Sound? = null
    private var ambienceId = 0L
    private var tensionId = 0L
    private var dangerId = 0L
    private var ambiencePlaying = false
    private var initialized = false
    private var prefs: Preferences? = null
    private val director = LayerDirector()
    private val heartbeatGate = RateLimiter(0.25f)
    private var heartbeatTimer = 0f

    var muted: Boolean = false
        set(value) {
            field = value
            prefs?.putBoolean("muted", value)?.flush()
            if (value) {
                stopLoops()
            } else {
                director.reset()
                heartbeatTimer = 0f
                startAmbience()
            }
        }

    var masterVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyMix()
        }

    var sfxVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    var musicVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyMix()
        }

    fun init() {
        if (initialized) return
        initialized = true
        prefs = Gdx.app.getPreferences("depthdiver-settings")
        muted = prefs?.getBoolean("muted", false) ?: false
        masterVolume = Profile.masterVolume()
        sfxVolume = Profile.sfxVolume()
        musicVolume = Profile.musicVolume()
        pickup = loadTone("pickup", 880f, 0.14f)
        oxygen = loadTone("oxygen", 640f, 0.2f)
        crash = loadTone("crash", 120f, 0.35f)
        click = loadTone("click", 1400f, 0.05f)
        achieve = loadTone("achieve", 1150f, 0.22f)
        alert = loadTone("alert", 980f, 0.09f)
        alarm = loadTone("alarm", 130f, 0.6f)
        shield = loadSweep("shield", 420f, 1500f, 0.28f)
        shieldBreak = loadShieldBreak()
        combo = loadSweep("combo", 620f, 1240f, 0.16f)
        countdown = loadTone("countdown", 1050f, 0.09f)
        bossRoar = loadBossRoar()
        bossHit = loadBossHit()
        levelUp = loadLevelUp()
        heartbeat = loadHeartbeat()
        ambience = generateAmbience()
        tensionLayer = generateTensionLayer()
        dangerLayer = generateDangerLayer()
        if (!muted) startAmbience()
    }

    fun updateVolumes() {
        masterVolume = Profile.masterVolume()
        sfxVolume = Profile.sfxVolume()
        musicVolume = Profile.musicVolume()
    }

    fun updateMuted() {
        muted = prefs?.getBoolean("muted", false) ?: false
    }

    fun updateMusic(targetIntensity: Float, dt: Float) {
        if (dt <= 0f) return
        if (muted) {
            director.update(0f, dt)
            return
        }
        val mix = director.update(targetIntensity, dt)
        if (!ambiencePlaying) return
        applyMix(mix)
        val danger = mix.danger
        if (danger < 0.15f) {
            heartbeatTimer = 0f
            return
        }
        heartbeatTimer -= dt
        if (heartbeatTimer > 0f) return
        heartbeatTimer = heartbeatPeriod(danger)
        if (heartbeatGate.allow(mix.danger)) playHeartbeat(danger)
    }

    fun stopMusic() {
        director.update(0f, 1f)
        heartbeatTimer = 0f
    }

    private fun startAmbience() {
        if (!canStartAmbience(muted, ambiencePlaying, ambience != null)) return
        ambienceId = ambience?.loop(0f) ?: 0L
        tensionId = tensionLayer?.loop(0f) ?: 0L
        dangerId = dangerLayer?.loop(0f) ?: 0L
        ambiencePlaying = true
        applyMix()
    }

    private fun stopLoops() {
        if (ambienceId != 0L) ambience?.stop(ambienceId)
        if (tensionId != 0L) tensionLayer?.stop(tensionId)
        if (dangerId != 0L) dangerLayer?.stop(dangerId)
        ambienceId = 0L
        tensionId = 0L
        dangerId = 0L
        ambiencePlaying = false
    }

    private fun applyMix(mix: LayerMix = director.mix) {
        if (!ambiencePlaying) return
        val volume = masterVolume * musicVolume
        if (ambienceId != 0L) ambience?.setVolume(ambienceId, (mix.calm * volume).coerceIn(0f, 1f))
        if (tensionId != 0L) tensionLayer?.setVolume(tensionId, (mix.tension * volume).coerceIn(0f, 1f))
        if (dangerId != 0L) dangerLayer?.setVolume(dangerId, (mix.danger * volume).coerceIn(0f, 1f))
    }

    fun toggleMute() {
        muted = !muted
    }

    fun playPickup() = playSound(pickup, 0.6f)

    fun playOxygen() = playSound(oxygen, 0.7f)

    fun playCrash() = playSound(crash, 0.9f)

    fun playClick() = playSound(click, 0.5f)

    fun playAchieve() = playSound(achieve, 0.7f)

    fun playAlert() = playSound(alert, 0.4f)

    fun playAlarm() = playSound(alarm, 0.8f)

    fun playShield() = playSound(shield, 0.65f)

    fun playShieldBreak() = playSound(shieldBreak, 0.7f)

    fun playCombo(level: Int) {
        val gain = (0.45f + (level.coerceIn(2, 10) - 2) * 0.04f).coerceIn(0f, 0.9f)
        playSound(combo, gain)
    }

    fun playCountdown(step: Int) = playSound(countdown, 0.35f + (step.coerceIn(1, 3) - 1) * 0.12f)

    fun playBossRoar() = playSound(bossRoar, 0.85f)

    fun playBossHit() = playSound(bossHit, 0.7f)

    fun playLevelUp() = playSound(levelUp, 0.7f)

    private fun playHeartbeat(danger: Float) {
        playSound(heartbeat, 0.3f + 0.5f * danger.coerceIn(0f, 1f))
    }

    private fun playSound(sound: Sound?, gain: Float) {
        if (muted) return
        val volume = gain * masterVolume * sfxVolume
        if (volume <= 0f) return
        sound?.play(volume.coerceIn(0f, 1f))
    }

    fun dispose() {
        stopLoops()
        for (sound in listOf(
            pickup, oxygen, crash, click, ambience, achieve, alert, alarm,
            shield, shieldBreak, combo, countdown, bossRoar, bossHit, levelUp, heartbeat,
            tensionLayer, dangerLayer
        )) {
            sound?.dispose()
        }
        pickup = null
        oxygen = null
        crash = null
        click = null
        ambience = null
        achieve = null
        alert = null
        alarm = null
        shield = null
        shieldBreak = null
        combo = null
        countdown = null
        bossRoar = null
        bossHit = null
        levelUp = null
        heartbeat = null
        tensionLayer = null
        dangerLayer = null
        director.reset()
        heartbeatGate.reset()
        prefs = null
        initialized = false
    }

    private fun writeSound(name: String, bytes: ByteArray): Sound? {
        return try {
            val file = Gdx.files.local("audio-runtime/$name.wav")
            file.writeBytes(bytes, false)
            Gdx.audio.newSound(file)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadTone(name: String, freq: Float, duration: Float): Sound? =
        writeSound(name, synth(duration) { t, _ ->
            val env = attackDecay(t, duration, 0.004f)
            (sine(t, freq) * 0.6f + sine(t, freq * 2f) * 0.2f) * env
        })

    private fun loadSweep(name: String, from: Float, to: Float, duration: Float): Sound? =
        writeSound(name, synth(duration) { t, _ ->
            val k = (t / duration).coerceIn(0f, 1f)
            val freq = from + (to - from) * k
            val env = attackDecay(t, duration, 0.006f, 1.2f)
            (sine(t, freq) * 0.55f + saw(t, freq) * 0.18f) * env
        })

    private fun loadShieldBreak(): Sound? = writeSound("shield-break", synth(0.42f) { t, _ ->
        val k = (t / 0.42f).coerceIn(0f, 1f)
        val freq = 1500f - 1300f * k
        val env = attackDecay(t, 0.42f, 0.003f, 1.1f)
        (saw(t, freq) * 0.4f + sine(t, freq * 0.5f) * 0.3f) * env
    })

    private fun loadBossRoar(): Sound? = writeSound("boss-roar", synth(1.6f) { t, _ ->
        val k = (t / 1.6f).coerceIn(0f, 1f)
        val wobble = 1f + 0.06f * sine(t, 5.5f)
        val freq = (78f - 26f * k) * wobble
        val grit = 0.18f * sine(t, freq * 3.01f)
        val env = linearFade(t, 1.6f, 0.12f, 0.5f)
        (saw(t, freq) * 0.5f + sine(t, freq * 0.5f) * 0.35f + grit) * env
    })

    private fun loadBossHit(): Sound? = writeSound("boss-hit", synth(0.3f) { t, i ->
        val random = Random(31L + i / 64)
        val env = attackDecay(t, 0.3f, 0.002f, 2.4f)
        val thump = sine(t, 210f - 120f * (t / 0.3f)) * 0.55f
        val rattle = noise(random) * 0.35f
        (thump + rattle) * env
    })

    private fun loadLevelUp(): Sound? = writeSound("level-up", synth(0.55f) { t, _ ->
        val steps = floatArrayOf(523f, 659f, 784f, 1047f)
        val index = (t / 0.13f).toInt().coerceIn(0, steps.size - 1)
        val local = t - index * 0.13f
        val env = attackDecay(local, 0.13f, 0.005f, 1.4f)
        (sine(t, steps[index]) * 0.5f + sine(t, steps[index] * 2f) * 0.2f) * env
    })

    private fun loadHeartbeat(): Sound? = writeSound("heartbeat", synth(0.36f) { t, _ ->
        val first = if (t < 0.18f) attackDecay(t, 0.18f, 0.008f, 2.2f) else 0f
        val secondT = t - 0.2f
        val second = if (secondT in 0f..0.16f) attackDecay(secondT, 0.16f, 0.008f, 2.4f) else 0f
        (sine(t, 62f) * 0.7f * first) + (sine(t, 54f) * 0.5f * second)
    })

    private fun generateAmbience(): Sound? =
        writeSound("ambience", synth(14f) { t, _ ->
            val tide = 0.7f + 0.3f * sine(t, 0.08f)
            var wave = (sine(t, 55f) * 0.30f + sine(t, 110f) * 0.11f) * tide
            val blipAge = t % 3.4f
            if (blipAge < 0.4f) {
                wave += sine(blipAge, 380f + 900f * blipAge) * (1f - blipAge / 0.4f) * 0.16f
            }
            wave
        })

    private fun generateTensionLayer(): Sound? =
        writeSound("music-tension", synth(12f) { t, _ ->
            val pulse = 0.55f + 0.45f * abs(sine(t, 1.5f))
            val drone = (saw(t, 82.5f) * 0.22f + sine(t, 123.5f) * 0.14f) * pulse
            val shimmer = if (t % 1.5f < 0.5f) {
                val local = t % 1.5f
                sine(local, 660f + 40f * local) * attackDecay(local, 0.5f, 0.02f, 2.6f) * 0.12f
            } else {
                0f
            }
            val env = linearFade(t, 12f, 0.8f, 0.8f)
            (drone + shimmer) * env
        })

    private fun generateDangerLayer(): Sound? =
        writeSound("music-danger", synth(12f) { t, i ->
            val random = Random(101L + i / 128)
            val filter = OnePole(0.12f)
            val tremolo = 0.6f + 0.4f * sine(t, 3.2f)
            val rumble = filter.next(noise(random)) * 0.22f
            val dissonant = (sine(t, 311f) * 0.10f + sine(t, 329.6f) * 0.10f) * tremolo
            val ticks = if (t % 0.75f < 0.08f) {
                val local = t % 0.75f
                sine(local, 900f) * attackDecay(local, 0.08f, 0.002f, 3f) * 0.10f
            } else {
                0f
            }
            val env = linearFade(t, 12f, 0.5f, 0.5f)
            (sine(t, 55f) * 0.26f * tremolo + rumble + dissonant + ticks) * env
        })
}
