package com.example

import com.example.scene.ParallaxLayerSpec
import com.example.scene.ParallaxSceneBuilder
import com.example.scene.SCENE_TILE_WIDTH
import com.example.scene.makeRidgeSampler
import com.example.viewmodel.AuroraExpressViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuroraExpressTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ridge sampler produces normalized deterministic values`() {
    val a = makeRidgeSampler(seed = 111L, harmonics = 5, sharp = 1.4f)
    val b = makeRidgeSampler(seed = 111L, harmonics = 5, sharp = 1.4f)
    var x = 0f
    while (x <= SCENE_TILE_WIDTH) {
      val va = a(x)
      val vb = b(x)
      assertEquals(va, vb, 1e-6f)
      assertTrue("Value $va out of [0,1]", va in -0.001f..1.001f)
      x += 60f
    }
  }

  @Test
  fun `scene builder generates all 10 parallax layers`() {
    val layers = ParallaxSceneBuilder.buildLayers()
    assertEquals(10, layers.size)
    assertEquals(2, layers.count { it is ParallaxLayerSpec.Mist })
    assertEquals(8, layers.count { it is ParallaxLayerSpec.VectorPaths })
  }

  @Test
  fun `viewmodel toggles sound, hud, speed, and triggers surge`() {
    val vm = AuroraExpressViewModel()
    val initial = vm.uiState.value
    assertFalse(initial.isSoundOn)
    assertTrue(initial.hudVisible)
    assertEquals(1.0f, initial.speedMultiplier, 0.001f)

    vm.cycleSpeed()
    assertEquals(1.5f, vm.uiState.value.speedMultiplier, 0.001f)
    vm.cycleSpeed()
    assertEquals(2.0f, vm.uiState.value.speedMultiplier, 0.001f)
    vm.cycleSpeed()
    assertEquals(0.5f, vm.uiState.value.speedMultiplier, 0.001f)

    vm.toggleHud()
    assertFalse(vm.uiState.value.hudVisible)

    vm.triggerManualSurge(0.42f)
    val surge = vm.uiState.value.activeSurge
    assertNotNull(surge)
    assertEquals(0.42f, surge!!.xFraction, 0.001f)
  }
}
