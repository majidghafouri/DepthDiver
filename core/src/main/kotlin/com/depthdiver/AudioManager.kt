package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.audio.Sound
import com.depthdiver.audio.MusicDirector
import com.depthdiver.audio.MusicScore
import com.depthdiver.audio.MusicMix
import com.depthdiver.audio.OnePole
import com.depthdiver.audio.RateLimiter
import com.depthdiver.audio.attackDecay
import com.depthdiver.audio.HEARTBEAT_STRAIN_THRESHOLD
import com.depthdiver.audio.heartbeatPeriod
import com.depthdiver.audio.strainFor
import com.depthdiver.audio.linearFade
import com.depthdiver.audio.noise
import com.depthdiver.audio.saw
import com.depthdiver.audio.sine
import com.depthdiver.audio.synth
import java.util.Random

internal fun canStartAmbience(muted: Boolean, playing: Boolean, soundAvailable: Boolean): Boolean =
    !muted && !playing && soundAvailable

/** The quick menu toggle silences whichever channel is still audible. */
internal fun masterMuteTarget(musicMuted: Boolean, sfxMuted: Boolean): Pair<Boolean, Boolean> =
    if (musicMuted && sfxMuted) (false to false) else (true to true)

class AudioManager {

    private var pickup: Sound? = null
    private var oxygen: Sound? = null
    private var crash: Sound? = null
    private var click: Sound? = null
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
    private var musicBed: Sound? = null
    private var deepPad: Sound? = null
    private var musicBedId = 0L
    private var deepPadId = 0L
    private var musicPlaying = false
    private var initialized = false
    private var prefs: Preferences? = null
    private val director = MusicDirector()
    private val heartbeatGate = RateLimiter(0.25f)
    private var heartbeatTimer = 0f

    var musicMuted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Profile.setMusicMuted(value)
            prefs?.putBoolean("musicMuted", value)?.flush()
            if (value) {
                stopMusicLoops()
            } else {
                director.reset()
                startMusic()
            }
        }

    var sfxMuted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Profile.setSfxMuted(value)
            prefs?.putBoolean("sfxMuted", value)?.flush()
        }

    /** True when neither music nor effects can be heard. */
    val muted: Boolean
        get() = musicMuted && sfxMuted

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
        // Profile owns the persisted truth; the libGDX prefs copy is kept in
        // step for anything still reading the legacy key.
        musicMuted = Profile.musicMuted()
        sfxMuted = Profile.sfxMuted()
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
        musicBed = generateMusicBed()
        deepPad = generateDeepPad()
        if (!muted) startMusic()
    }

    fun updateVolumes() {
        masterVolume = Profile.masterVolume()
        sfxVolume = Profile.sfxVolume()
        musicVolume = Profile.musicVolume()
    }

    fun updateMuted() {
        musicMuted = Profile.musicMuted()
        sfxMuted = Profile.sfxMuted()
    }

    fun updateMusic(depthFactor: Float, oxygenRatio: Float, dt: Float) {
        if (dt <= 0f) return
        if (musicMuted) {
            director.update(0f, dt)
            return
        }
        val mix = director.update(depthFactor, dt)
        if (!musicPlaying) return
        applyMix(mix)

        // Air trouble is a moment-to-moment cue, not a layer of the soundtrack:
        // the heartbeat only surfaces once the player is genuinely low.
        val strain = strainFor(oxygenRatio)
        if (strain < HEARTBEAT_STRAIN_THRESHOLD) {
            heartbeatTimer = 0f
            return
        }
        heartbeatTimer -= dt
        if (heartbeatTimer > 0f) return
        heartbeatTimer = heartbeatPeriod(strain)
        if (heartbeatGate.allow(strain)) playHeartbeat(strain)
    }

    fun stopMusic() {
        director.update(0f, 1f)
        heartbeatTimer = 0f
    }

    private fun startMusic() {
        if (!canStartAmbience(musicMuted, musicPlaying, musicBed != null)) return
        musicBedId = musicBed?.loop(0f) ?: 0L
        deepPadId = deepPad?.loop(0f) ?: 0L
        musicPlaying = true
        applyMix()
    }

    private fun stopMusicLoops() {
        if (musicBedId != 0L) musicBed?.stop(musicBedId)
        if (deepPadId != 0L) deepPad?.stop(deepPadId)
        musicBedId = 0L
        deepPadId = 0L
        musicPlaying = false
    }

    private fun applyMix(mix: MusicMix = director.mix) {
        if (!musicPlaying) return
        val volume = masterVolume * musicVolume
        if (musicBedId != 0L) musicBed?.setVolume(musicBedId, (mix.bed * volume).coerceIn(0f, 1f))
        if (deepPadId != 0L) deepPad?.setVolume(deepPadId, (mix.deep * volume).coerceIn(0f, 1f))
    }

    /** Quick master toggle for the menu and the M key. */
    fun toggleMute() {
        val (music, sfx) = masterMuteTarget(musicMuted, sfxMuted)
        musicMuted = music
        sfxMuted = sfx
    }

    fun toggleMusicMute() {
        musicMuted = !musicMuted
    }

    fun toggleSfxMute() {
        sfxMuted = !sfxMuted
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
        if (sfxMuted) return
        val volume = gain * masterVolume * sfxVolume
        if (volume <= 0f) return
        sound?.play(volume.coerceIn(0f, 1f))
    }

    fun dispose() {
        stopMusicLoops()
        for (sound in listOf(
            pickup, oxygen, crash, click, achieve, alert, alarm,
            shield, shieldBreak, combo, countdown, bossRoar, bossHit, levelUp, heartbeat,
            musicBed, deepPad
        )) {
            sound?.dispose()
        }
        pickup = null
        oxygen = null
        crash = null
        click = null
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
        musicBed = null
        deepPad = null
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

    /**
     * The constant layer: a light piano loop that never builds, whatever the run
     * is doing. The notes live in [MusicScore]; this only renders them.
     */
    private fun generateMusicBed(): Sound? =
        writeSound("music-bed", synth(MusicScore.LOOP_SECONDS) { t, _ -> MusicScore.bed(t) })

    /** The layer that opens up with depth. Bright and sparse, never a drone. */
    private fun generateDeepPad(): Sound? =
        writeSound("music-deep", synth(MusicScore.LOOP_SECONDS) { t, _ -> MusicScore.deep(t) })
}
