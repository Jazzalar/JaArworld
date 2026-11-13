package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AuroraAudioEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class AuroraSurgeEvent(
  val id: Long,
  val xFraction: Float,
  val power: Float,
  val startedAtNanos: Long = System.nanoTime(),
)

data class AuroraUiState(
  val isSoundOn: Boolean = false,
  val speedMultiplier: Float = 1.0f,
  val hudVisible: Boolean = true,
  val activeSurge: AuroraSurgeEvent? = null,
)

class AuroraExpressViewModel : ViewModel() {
  private val audioEngine = AuroraAudioEngine()

  private val _uiState = MutableStateFlow(AuroraUiState())
  val uiState: StateFlow<AuroraUiState> = _uiState.asStateFlow()

  private var surgeCounter = 0L
  private var autoSurgeJob: Job? = null
  private var isForeground = true

  private val speedSteps = listOf(0.5f, 1.0f, 1.5f, 2.0f)

  init {
    startAutoSurgeLoop()
  }

  private fun startAutoSurgeLoop() {
    autoSurgeJob?.cancel()
    autoSurgeJob = viewModelScope.launch {
      // Initial subtle wait before first automatic surge
      delay(1500L)
      while (isActive) {
        if (isForeground) {
          val xFrac = 0.2f + Random.nextFloat() * 0.6f
          val power = 0.75f + Random.nextFloat() * 0.25f
          val soundDelay = 0.4 + Random.nextDouble() * 1.0
          fireSurgeInternal(xFrac, power, soundDelay)
        }
        val nextWaitMs = 11_000L + Random.nextLong(8_000L)
        delay(nextWaitMs)
      }
    }
  }

  fun toggleSound() {
    val nextSoundOn = !_uiState.value.isSoundOn
    _uiState.update { it.copy(isSoundOn = nextSoundOn) }
    if (nextSoundOn) {
      audioEngine.start()
    } else {
      audioEngine.stop()
    }
  }

  fun triggerManualSurge(xFraction: Float = 0.2f + Random.nextFloat() * 0.6f) {
    val clampedX = xFraction.coerceIn(0.12f, 0.88f)
    fireSurgeInternal(clampedX, power = 1.0f, soundDelaySec = 0.08)
  }

  private fun fireSurgeInternal(xFraction: Float, power: Float, soundDelaySec: Double) {
    surgeCounter++
    val event = AuroraSurgeEvent(
      id = surgeCounter,
      xFraction = xFraction,
      power = power,
    )
    _uiState.update { it.copy(activeSurge = event) }
    audioEngine.triggerIceSound(soundDelaySec, power)
  }

  fun triggerWhistle() {
    if (!_uiState.value.isSoundOn) {
      _uiState.update { it.copy(isSoundOn = true) }
      audioEngine.start()
    }
    audioEngine.triggerWhistleNow()
  }

  fun cycleSpeed() {
    val current = _uiState.value.speedMultiplier
    val idx = speedSteps.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
    val next = speedSteps[(idx + 1) % speedSteps.size]
    _uiState.update { it.copy(speedMultiplier = next) }
    audioEngine.setSpeedMultiplier(next)
  }

  fun toggleHud() {
    _uiState.update { it.copy(hudVisible = !it.hudVisible) }
  }

  fun onLifecyclePause() {
    isForeground = false
    audioEngine.onPause()
  }

  fun onLifecycleResume() {
    isForeground = true
    audioEngine.onResume()
  }

  override fun onCleared() {
    super.onCleared()
    autoSurgeJob?.cancel()
    audioEngine.release()
  }
}
