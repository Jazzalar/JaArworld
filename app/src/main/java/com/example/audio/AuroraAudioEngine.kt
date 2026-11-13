package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Real-time stereo procedural audio synthesizer replicating the Web Audio soundscape
 * of Aurora Express:
 * - Train rumble (brown noise -> 135 Hz lowpass)
 * - Arctic wind (brown noise -> LFO-swept 380 Hz bandpass + reverb)
 * - Sleet hiss (white noise -> 2400..6800 Hz bandpass)
 * - Rhythmic dual rail-joint clacks (filtered noise burst + triangle pitch drop)
 * - Distant minor-third train whistle (dual triangle + 1100 Hz lowpass + stereo reverb)
 * - Aurora surge ice crack, glacial groan, and harmonic chime chord (220, 329.63, 493.88 Hz)
 */
class AuroraAudioEngine {
  companion object {
    private const val SAMPLE_RATE = 44100
    private const val TWO_PI = (2.0 * PI)
    private const val CHUNK_FRAMES = 512
  }

  private enum class FilterType {
    LOWPASS,
    HIGHPASS,
    BANDPASS,
  }

  private class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun set(type: FilterType, freq: Double, q: Double) {
      val clampedFreq = freq.coerceIn(20.0, SAMPLE_RATE * 0.45)
      val w0 = TWO_PI * clampedFreq / SAMPLE_RATE
      val cosW0 = cos(w0)
      val alpha = sin(w0) / (2.0 * max(0.1, q))
      val a0 = 1.0 + alpha

      when (type) {
        FilterType.LOWPASS -> {
          b0 = ((1.0 - cosW0) * 0.5) / a0
          b1 = (1.0 - cosW0) / a0
          b2 = ((1.0 - cosW0) * 0.5) / a0
        }
        FilterType.HIGHPASS -> {
          b0 = ((1.0 + cosW0) * 0.5) / a0
          b1 = -(1.0 + cosW0) / a0
          b2 = ((1.0 + cosW0) * 0.5) / a0
        }
        FilterType.BANDPASS -> {
          b0 = alpha / a0
          b1 = 0.0
          b2 = -alpha / a0
        }
      }
      a1 = (-2.0 * cosW0) / a0
      a2 = (1.0 - alpha) / a0
    }

    fun reset() {
      z1 = 0.0
      z2 = 0.0
    }

    fun process(x: Double): Double {
      val y = b0 * x + z1
      z1 = b1 * x - a1 * y + z2
      z2 = b2 * x - a2 * y
      return y
    }
  }

  private class CombFilter(size: Int, private val feedback: Double, private val damp: Double) {
    private val buf = DoubleArray(size)
    private var idx = 0
    private var filterStore = 0.0

    fun process(input: Double): Double {
      val out = buf[idx]
      filterStore = out * (1.0 - damp) + filterStore * damp
      buf[idx] = input + filterStore * feedback
      idx++
      if (idx >= buf.size) idx = 0
      return out
    }
  }

  private class AllpassFilter(size: Int) {
    private val buf = DoubleArray(size)
    private var idx = 0

    fun process(input: Double): Double {
      val bufOut = buf[idx]
      val out = -input + bufOut
      buf[idx] = input + bufOut * 0.5
      idx++
      if (idx >= buf.size) idx = 0
      return out
    }
  }

  private class StereoReverb {
    private val combsL = arrayOf(
      CombFilter(1557, 0.87, 0.28),
      CombFilter(1617, 0.86, 0.28),
      CombFilter(1491, 0.85, 0.28),
      CombFilter(1422, 0.88, 0.28),
    )
    private val combsR = arrayOf(
      CombFilter(1557 + 23, 0.87, 0.28),
      CombFilter(1617 + 23, 0.86, 0.28),
      CombFilter(1491 + 23, 0.85, 0.28),
      CombFilter(1422 + 23, 0.88, 0.28),
    )
    private val allpassL = arrayOf(AllpassFilter(225), AllpassFilter(556))
    private val allpassR = arrayOf(AllpassFilter(225 + 23), AllpassFilter(556 + 23))

    fun process(monoIn: Double): Pair<Double, Double> {
      val input = monoIn * 0.22
      var outL = 0.0
      var outR = 0.0
      for (i in 0..3) {
        outL += combsL[i].process(input)
        outR += combsR[i].process(input)
      }
      for (i in 0..1) {
        outL = allpassL[i].process(outL)
        outR = allpassR[i].process(outR)
      }
      return outL * 0.55 to outR * 0.55
    }
  }

  private class ClackVoice {
    var active = false
    var ageSamples = 0
    var noiseStartIdx = 0
    var noiseStep = 1.0
    var noisePos = 0.0
    var vol = 0.15
    var baseFreq = 185.0
    var triPhase = 0.0
    val bp = Biquad()
  }

  private class WhistleVoice {
    var active = false
    var ageSamples = 0
    var totalSamples = 0
    var baseFreq = 375.0
    var phase1 = 0.0
    var phase2 = 0.0
    val lp = Biquad().apply { set(FilterType.LOWPASS, 1100.0, 0.707) }
  }

  private class IceVoice {
    var active = false
    var delaySamples = 0
    var ageSamples = 0
    var power = 1.0
    var groanOffset = 0
    var phaseA = 0.0
    var phaseB = 0.0
    var phaseC = 0.0
    val crackBp = Biquad().apply { set(FilterType.BANDPASS, 2100.0, 2.4) }
    val groanLp = Biquad()
  }

  private val brownNoise = generateNoiseBuffer(8.0, brown = true)
  private val whiteNoise = generateNoiseBuffer(4.0, brown = false)

  private val rumbleLp = Biquad().apply { set(FilterType.LOWPASS, 135.0, 0.7) }
  private val windBp = Biquad().apply { set(FilterType.BANDPASS, 380.0, 3.2) }
  private val sleetHp = Biquad().apply { set(FilterType.HIGHPASS, 2400.0, 0.707) }
  private val sleetLp = Biquad().apply { set(FilterType.LOWPASS, 6800.0, 0.707) }
  private val reverb = StereoReverb()

  private val clacks = Array(8) { ClackVoice() }
  private val whistle = WhistleVoice()
  private val iceVoices = Array(4) { IceVoice() }

  private val lock = Any()
  @Volatile private var shouldPlay = false
  @Volatile private var isPaused = false
  @Volatile private var speedMultiplier = 1.0f
  @Volatile private var masterGain = 0.0
  @Volatile private var targetGain = 0.0

  private var workerThread: Thread? = null
  private var audioTrack: AudioTrack? = null

  private var rumbleIdx = 0
  private var windIdx = 0
  private var sleetIdx = 0
  private var totalFrames = 0L
  private var lfoPhase = 0.0

  private var nextClackFrame = 0L
  private var pendingSecondClackFrame = -1L
  private var pendingSecondClackVol = 0.12
  private var nextWhistleFrame = 0L

  private fun generateNoiseBuffer(seconds: Double, brown: Boolean): DoubleArray {
    val len = (SAMPLE_RATE * seconds).toInt()
    val buf = DoubleArray(len)
    var last = 0.0
    for (i in 0 until len) {
      val w = Random.nextDouble() * 2.0 - 1.0
      if (brown) {
        last = (last + 0.02 * w) / 1.02
        buf[i] = last * 3.5
      } else {
        buf[i] = w
      }
    }
    return buf
  }

  fun setSpeedMultiplier(multiplier: Float) {
    speedMultiplier = multiplier.coerceIn(0.25f, 3.0f)
  }

  fun start() {
    synchronized(lock) {
      shouldPlay = true
      targetGain = 0.9
      if (!isPaused) {
        ensureThreadRunning()
      }
    }
  }

  fun stop() {
    synchronized(lock) {
      shouldPlay = false
      targetGain = 0.0
    }
  }

  fun onPause() {
    synchronized(lock) {
      isPaused = true
      if (shouldPlay) {
        targetGain = 0.0
      }
    }
  }

  fun onResume() {
    synchronized(lock) {
      isPaused = false
      if (shouldPlay) {
        targetGain = 0.9
        ensureThreadRunning()
      }
    }
  }

  fun triggerIceSound(delaySeconds: Double, power: Float = 1f) {
    if (!shouldPlay || isPaused) return
    synchronized(lock) {
      val slot = iceVoices.firstOrNull { !it.active } ?: iceVoices[0]
      slot.active = true
      slot.delaySamples = (delaySeconds.coerceAtLeast(0.0) * SAMPLE_RATE).toInt()
      slot.ageSamples = 0
      slot.power = power.toDouble().coerceIn(0.3, 1.2)
      slot.groanOffset = Random.nextInt(brownNoise.size)
      slot.phaseA = 0.0
      slot.phaseB = 0.0
      slot.phaseC = 0.0
      slot.crackBp.reset()
      slot.groanLp.reset()
      slot.groanLp.set(FilterType.LOWPASS, 220.0, 0.707)
    }
  }

  fun triggerWhistleNow() {
    if (!shouldPlay || isPaused) return
    synchronized(lock) {
      startWhistle()
      nextWhistleFrame = totalFrames + ((32.0 + Random.nextDouble() * 44.0) * SAMPLE_RATE).toLong()
    }
  }

  fun release() {
    synchronized(lock) {
      shouldPlay = false
      isPaused = true
      targetGain = 0.0
    }
    workerThread?.interrupt()
    workerThread = null
  }

  private fun ensureThreadRunning() {
    if (workerThread?.isAlive == true) return
    val t = Thread({ runAudioLoop() }, "AuroraAudioThread").apply {
      priority = Thread.MAX_PRIORITY
      isDaemon = true
      start()
    }
    workerThread = t
  }

  private fun runAudioLoop() {
    val minBufBytes = AudioTrack.getMinBufferSize(
      SAMPLE_RATE,
      AudioFormat.CHANNEL_OUT_STEREO,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(CHUNK_FRAMES * 4 * 4)

    val track = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(SAMPLE_RATE)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
          .build()
      )
      .setBufferSizeInBytes(minBufBytes)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()

    audioTrack = track
    track.play()

    val pcm = ShortArray(CHUNK_FRAMES * 2)
    nextClackFrame = totalFrames + (0.05 * SAMPLE_RATE).toLong()
    nextWhistleFrame = totalFrames + ((10.0 + Random.nextDouble() * 14.0) * SAMPLE_RATE).toLong()

    try {
      while (!Thread.currentThread().isInterrupted) {
        val activeTarget = if (shouldPlay && !isPaused) targetGain else 0.0
        if (activeTarget == 0.0 && masterGain < 0.0005) {
          masterGain = 0.0
          if (!shouldPlay || isPaused) {
            break
          }
        }

        renderChunk(pcm, activeTarget)
        val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        if (written < 0) break
      }
    } catch (_: InterruptedException) {
      // Clean exit
    } finally {
      try {
        track.pause()
        track.flush()
        track.release()
      } catch (_: Throwable) {
      }
      audioTrack = null
      workerThread = null
    }
  }

  private fun spawnClack(vol: Double) {
    val slot = clacks.firstOrNull { !it.active } ?: return
    slot.active = true
    slot.ageSamples = 0
    slot.noiseStartIdx = Random.nextInt(whiteNoise.size)
    slot.noiseStep = 0.9 + Random.nextDouble() * 0.35
    slot.noisePos = 0.0
    slot.vol = vol
    slot.baseFreq = 175.0 + Random.nextDouble() * 35.0
    slot.triPhase = 0.0
    slot.bp.reset()
    slot.bp.set(FilterType.BANDPASS, 1750.0 + Random.nextDouble() * 600.0, 1.4)
  }

  private fun startWhistle() {
    whistle.active = true
    whistle.ageSamples = 0
    whistle.totalSamples = ((2.1 + Random.nextDouble() * 0.9) * SAMPLE_RATE).toInt()
    whistle.baseFreq = 370.0 + Random.nextDouble() * 20.0
    whistle.phase1 = 0.0
    whistle.phase2 = 0.0
    whistle.lp.reset()
  }

  private fun triangleWave(phase: Double): Double {
    val p = phase - kotlin.math.floor(phase)
    return 4.0 * kotlin.math.abs(p - 0.5) - 1.0
  }

  private fun renderChunk(out: ShortArray, activeTarget: Double) {
    val fadeUpStep = 0.9 / (1.2 * SAMPLE_RATE)
    val fadeDownStep = 0.9 / (0.5 * SAMPLE_RATE)
    val spdMult = speedMultiplier.toDouble()

    synchronized(lock) {
      for (i in 0 until CHUNK_FRAMES) {
        // Smooth master fade
        if (masterGain < activeTarget) {
          masterGain = min(activeTarget, masterGain + fadeUpStep)
        } else if (masterGain > activeTarget) {
          masterGain = max(activeTarget, masterGain - fadeDownStep)
        }

        val nowSec = totalFrames.toDouble() / SAMPLE_RATE

        // Schedule rail clacks
        if (totalFrames >= nextClackFrame) {
          val speed = spdMult * (1.0 + 0.05 * sin(nowSec * 0.06))
          val gap = (0.15 / speed).coerceAtLeast(0.05)
          val joint = (0.54 / speed).coerceAtLeast(0.18)
          val v = 0.14 + Random.nextDouble() * 0.05
          spawnClack(v)
          pendingSecondClackFrame = totalFrames + (gap * SAMPLE_RATE).toLong()
          pendingSecondClackVol = v * 0.8
          val jitter = ((Random.nextDouble() - 0.5) * 0.007 * SAMPLE_RATE).toLong()
          nextClackFrame = totalFrames + (joint * SAMPLE_RATE).toLong() + jitter
        }
        if (pendingSecondClackFrame in 0..totalFrames) {
          spawnClack(pendingSecondClackVol)
          pendingSecondClackFrame = -1L
        }

        // Schedule distant train whistle
        if (totalFrames >= nextWhistleFrame) {
          startWhistle()
          nextWhistleFrame = totalFrames + ((32.0 + Random.nextDouble() * 44.0) * SAMPLE_RATE).toLong()
        }

        // 1. Train rumble
        val rumbleSample = rumbleLp.process(brownNoise[rumbleIdx]) * 0.45
        rumbleIdx = (rumbleIdx + 1) % brownNoise.size

        // 2. Wind with 0.06 Hz LFO
        if ((i and 15) == 0) {
          val lfo = sin(lfoPhase) * 210.0
          windBp.set(FilterType.BANDPASS, 380.0 + lfo, 3.2)
        }
        lfoPhase += TWO_PI * 0.06 / SAMPLE_RATE
        if (lfoPhase > TWO_PI) lfoPhase -= TWO_PI
        val windSample = windBp.process(brownNoise[windIdx]) * 0.07
        windIdx = (windIdx + 1) % brownNoise.size

        // 3. Sleet hiss
        val sleetSample = sleetLp.process(sleetHp.process(whiteNoise[sleetIdx])) * 0.04
        sleetIdx = (sleetIdx + 1) % whiteNoise.size

        var dryBus = rumbleSample + windSample + sleetSample
        var verbIn = windSample

        // 4. Clacks
        for (c in clacks) {
          if (!c.active) continue
          val t = c.ageSamples.toDouble() / SAMPLE_RATE
          if (t > 0.13) {
            c.active = false
            continue
          }
          var clackSum = 0.0
          // Noise burst (0..65ms)
          if (t <= 0.065) {
            val envN = if (t < 0.002) t / 0.002 else exp(-(t - 0.002) / 0.011)
            val idx = (c.noiseStartIdx + c.noisePos.toInt()) % whiteNoise.size
            c.noisePos += c.noiseStep
            clackSum += c.bp.process(whiteNoise[idx]) * envN * c.vol
          }
          // Triangle thump (0..110ms)
          if (t <= 0.11) {
            val envT = if (t < 0.003) t / 0.003 else exp(-(t - 0.003) / 0.019)
            val freqRatio = (t / 0.045).coerceIn(0.0, 1.0)
            val curFreq = (c.baseFreq * 1.5) * (1.0 / 1.5).pow(freqRatio)
            c.triPhase += curFreq / SAMPLE_RATE
            clackSum += triangleWave(c.triPhase) * envT * (c.vol * 0.75)
          }
          dryBus += clackSum
          c.ageSamples++
        }

        // 5. Distant Train Whistle (to reverb send)
        if (whistle.active) {
          val n = whistle.ageSamples
          if (n >= whistle.totalSamples) {
            whistle.active = false
          } else {
            val t = n.toDouble() / SAMPLE_RATE
            val dur = whistle.totalSamples.toDouble() / SAMPLE_RATE
            val env = when {
              t < 0.38 -> 0.038 * (t / 0.38)
              t < dur - 0.55 -> 0.038
              else -> 0.038 * ((dur - t) / 0.55).coerceAtLeast(0.0)
            }
            val droop = 1.0 - 0.01 * (t / dur)
            whistle.phase1 += (whistle.baseFreq * droop) / SAMPLE_RATE
            whistle.phase2 += (whistle.baseFreq * 1.189 * droop) / SAMPLE_RATE
            val rawW = (triangleWave(whistle.phase1) + triangleWave(whistle.phase2)) * env
            verbIn += whistle.lp.process(rawW)
            whistle.ageSamples++
          }
        }

        // 6. Aurora Ice Events (dry + reverb send)
        var iceTotal = 0.0
        for (iv in iceVoices) {
          if (!iv.active) continue
          if (iv.delaySamples > 0) {
            iv.delaySamples--
            continue
          }
          val t = iv.ageSamples.toDouble() / SAMPLE_RATE
          if (t > 5.8) {
            iv.active = false
            continue
          }
          var vSum = 0.0
          val pwr = iv.power

          // Ice crack (0..0.42s)
          if (t <= 0.42) {
            val envC = if (t < 0.008) (t / 0.008) * 0.32 * pwr else 0.32 * pwr * exp(-(t - 0.008) / 0.07)
            val wn = whiteNoise[iv.ageSamples % whiteNoise.size]
            vSum += iv.crackBp.process(wn) * envC
          }

          // Glacial groan (0..3.4s)
          if (t <= 3.4) {
            if ((iv.ageSamples and 31) == 0) {
              val frac = (t / 3.2).coerceIn(0.0, 1.0)
              val cut = 220.0 * (55.0 / 220.0).pow(frac)
              iv.groanLp.set(FilterType.LOWPASS, cut, 0.707)
            }
            val envG = if (t < 0.18) (t / 0.18) * 0.55 * pwr else 0.55 * pwr * exp(-(t - 0.18) / 0.52)
            val bn = brownNoise[(iv.groanOffset + iv.ageSamples) % brownNoise.size]
            vSum += iv.groanLp.process(bn) * envG
          }

          // Harmonic chimes (0.2s..5.8s: 220, 329.63, 493.88 Hz)
          if (t >= 0.2) {
            val ct = t - 0.2
            val envH = if (ct < 1.6) {
              (ct / 1.6) * 0.022 * pwr
            } else {
              0.022 * pwr * exp(-(ct - 1.6) / 1.05)
            }
            iv.phaseA += TWO_PI * 220.0 / SAMPLE_RATE
            iv.phaseB += TWO_PI * 329.63 / SAMPLE_RATE
            iv.phaseC += TWO_PI * 493.88 / SAMPLE_RATE
            vSum += (sin(iv.phaseA) + sin(iv.phaseB) + sin(iv.phaseC)) * envH
          }

          iceTotal += vSum
          iv.ageSamples++
        }

        val (revL, revR) = reverb.process(verbIn + iceTotal)
        val mixL = tanh((dryBus + iceTotal + revL * 0.55) * masterGain * 1.15)
        val mixR = tanh((dryBus + iceTotal + revR * 0.55) * masterGain * 1.15)

        out[i * 2] = (mixL * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        out[i * 2 + 1] = (mixR * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        totalFrames++
      }
    }
  }
}
