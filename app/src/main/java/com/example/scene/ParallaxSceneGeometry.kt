package com.example.scene

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.ui.theme.DriftBase
import com.example.ui.theme.DriftRidge
import com.example.ui.theme.FencePost
import com.example.ui.theme.FenceRail
import com.example.ui.theme.FenceSnow
import com.example.ui.theme.FjordFarFill
import com.example.ui.theme.FjordFarSnow
import com.example.ui.theme.FjordMidFill
import com.example.ui.theme.FjordMidSnow
import com.example.ui.theme.PowerFrost
import com.example.ui.theme.PowerPole
import com.example.ui.theme.PowerWire
import com.example.ui.theme.SnowHillCap
import com.example.ui.theme.SnowHillFill
import com.example.ui.theme.TaigaFill
import com.example.ui.theme.TaigaFillAlt
import com.example.ui.theme.TaigaGround
import com.example.ui.theme.TaigaSnow
import com.example.ui.theme.TaigaTrunk
import com.example.ui.theme.VillageBody
import com.example.ui.theme.VillageSnow
import com.example.ui.theme.WindowDim
import com.example.ui.theme.WindowLit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

const val SCENE_TILE_WIDTH = 2400f
private const val TAU = (Math.PI * 2.0).toFloat()

/**
 * Deterministic xorshift32 PRNG matching the original Aurora Express procedural generator.
 */
class XorshiftRng(seed: Long) {
  private var state: UInt = if (seed.toUInt() == 0u) 0x9e3779b9u else seed.toUInt()

  fun nextFloat(): Float {
    var s = state
    s = s xor (s shl 13)
    s = s xor (s shr 17)
    s = s xor (s shl 5)
    state = s
    return s.toDouble().div(4294967296.0).toFloat()
  }
}

private data class RidgeComp(val f: Int, val a: Float, val p: Float)

fun makeRidgeSampler(seed: Long, harmonics: Int = 5, sharp: Float = 1f): (Float) -> Float {
  val r = XorshiftRng(seed)
  val comps = ArrayList<RidgeComp>(harmonics)
  var f = 1
  for (i in 0 until harmonics) {
    val freq = f + floor(r.nextFloat() * f).toInt()
    val amp = 0.55f.pow(i)
    val phase = r.nextFloat() * TAU
    comps.add(RidgeComp(freq, amp, phase))
    f *= 2
  }

  fun raw(x: Float): Float {
    var v = 0f
    for (c in comps) {
      v += c.a * sin(TAU * c.f * x / SCENE_TILE_WIDTH + c.p)
    }
    return if (sharp == 1f) v else sign(v) * abs(v).pow(sharp)
  }

  var lo = Float.POSITIVE_INFINITY
  var hi = Float.NEGATIVE_INFINITY
  var x = 0f
  while (x <= SCENE_TILE_WIDTH) {
    val v = raw(x)
    if (v < lo) lo = v
    if (v > hi) hi = v
    x += 3f
  }
  val span = (hi - lo).let { if (it == 0f) 1f else it }
  return { queryX -> (raw(queryX) - lo) / span }
}

sealed interface ParallaxLayerSpec {
  val bottomFraction: Float
  val baseHeight: Float
  val speed: Float

  data class Mist(
    override val bottomFraction: Float,
    override val baseHeight: Float,
  ) : ParallaxLayerSpec {
    override val speed: Float = 0f
  }

  data class VectorPaths(
    override val bottomFraction: Float,
    override val baseHeight: Float,
    override val speed: Float,
    val draws: List<LayerDraw>,
  ) : ParallaxLayerSpec
}

data class LayerDraw(
  val path: Path,
  val color: Color,
  val alpha: Float = 1f,
  val strokeWidth: Float = 0f, // 0f means fill, >0f means stroke
)

object ParallaxSceneBuilder {
  fun buildLayers(): List<ParallaxLayerSpec> = listOf(
    // 1. Far Fjords
    buildFjords(
      bottomFraction = 0.29f,
      h = 340f,
      speed = 5f,
      seed = 111L,
      height = 285f,
      fill = FjordFarFill,
      snowLine = 0.46f,
      snowFill = FjordFarSnow,
      snowAlpha = 0.72f,
    ),
    // 2. Far Mist
    ParallaxLayerSpec.Mist(bottomFraction = 0.26f, baseHeight = 170f),
    // 3. Mid Fjords
    buildFjords(
      bottomFraction = 0.24f,
      h = 290f,
      speed = 12f,
      seed = 222L,
      height = 220f,
      fill = FjordMidFill,
      snowLine = 0.58f,
      snowFill = FjordMidSnow,
      snowAlpha = 0.58f,
    ),
    // 4. Snow Hills
    buildSnowHills(
      bottomFraction = 0.19f,
      h = 210f,
      speed = 24f,
      seed = 333L,
      height = 115f,
      fill = SnowHillFill,
      capFill = SnowHillCap,
    ),
    // 5. Near Mist
    ParallaxLayerSpec.Mist(bottomFraction = 0.17f, baseHeight = 140f),
    // 6. Taiga Forest
    buildTaigaForest(
      bottomFraction = 0.135f,
      h = 250f,
      speed = 52f,
      seed = 444L,
      count = 34,
      minS = 84f,
      maxS = 168f,
      groundY = 226f,
    ),
    // 7. Nordic Village
    buildNordicVillage(
      bottomFraction = 0.08f,
      h = 190f,
      speed = 118f,
      seed = 555L,
    ),
    // 8. Snow Drifts
    buildSnowDrifts(
      bottomFraction = 0.025f,
      h = 130f,
      speed = 220f,
      seed = 666L,
    ),
    // 9. Powerline
    buildPowerline(
      bottomFraction = 0.005f,
      h = 315f,
      speed = 375f,
      count = 4,
      poleH = 295f,
      sag = 40f,
    ),
    // 10. Snow Fence
    buildSnowFence(
      bottomFraction = 0f,
      h = 125f,
      speed = 510f,
    ),
  )

  private fun buildFjords(
    bottomFraction: Float,
    h: Float,
    speed: Float,
    seed: Long,
    height: Float,
    fill: Color,
    snowLine: Float,
    snowFill: Color,
    snowAlpha: Float,
  ): ParallaxLayerSpec.VectorPaths {
    val sample = makeRidgeSampler(seed, 5, 1.4f)
    fun yAt(x: Float) = h - height * sample(x)

    val mountainPath = Path().apply {
      moveTo(0f, h)
      var x = 0f
      while (x <= SCENE_TILE_WIDTH) {
        lineTo(x, yAt(x))
        x += 8f
      }
      lineTo(SCENE_TILE_WIDTH, h)
      close()
    }

    fun snowY(x: Float): Float =
      h - height * snowLine +
        sin(x / SCENE_TILE_WIDTH * TAU * 4f) * 9f +
        sin(x / SCENE_TILE_WIDTH * TAU * 9f + 1.2f) * 5f

    val snowPath = Path()
    val run = mutableListOf<Pair<Float, Float>>()

    fun flush() {
      if (run.size > 1) {
        snowPath.moveTo(run[0].first, run[0].second)
        for (i in 1 until run.size) {
          snowPath.lineTo(run[i].first, run[i].second)
        }
        val firstX = run.first().first
        val lastX = run.last().first
        snowPath.lineTo(lastX, snowY(lastX))
        snowPath.lineTo(firstX, snowY(firstX))
        snowPath.close()
      }
      run.clear()
    }

    var x = 0f
    while (x <= SCENE_TILE_WIDTH) {
      val y = yAt(x)
      if (y < snowY(x)) {
        run.add(x to y)
      } else {
        flush()
      }
      x += 8f
    }
    flush()

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(mountainPath, fill),
        LayerDraw(snowPath, snowFill, alpha = snowAlpha),
      ),
    )
  }

  private fun buildSnowHills(
    bottomFraction: Float,
    h: Float,
    speed: Float,
    seed: Long,
    height: Float,
    fill: Color,
    capFill: Color,
  ): ParallaxLayerSpec.VectorPaths {
    val sample = makeRidgeSampler(seed, 3, 0.8f)
    val bodyPath = Path().apply { moveTo(0f, h) }
    val capPath = Path()

    var x = 0f
    while (x <= SCENE_TILE_WIDTH) {
      val y = h - 28f - height * sample(x)
      bodyPath.lineTo(x, y)
      if (x == 0f) capPath.moveTo(x, y) else capPath.lineTo(x, y)
      x += 10f
    }
    bodyPath.lineTo(SCENE_TILE_WIDTH, h)
    bodyPath.close()

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(bodyPath, fill),
        LayerDraw(capPath, capFill, alpha = 0.45f, strokeWidth = 4f),
      ),
    )
  }

  private data class TreeSpec(val x: Float, val s: Float, val alt: Boolean)

  private fun buildTaigaForest(
    bottomFraction: Float,
    h: Float,
    speed: Float,
    seed: Long,
    count: Int,
    minS: Float,
    maxS: Float,
    groundY: Float,
  ): ParallaxLayerSpec.VectorPaths {
    val r = XorshiftRng(seed)
    val trees = ArrayList<TreeSpec>(count)
    for (i in 0 until count) {
      trees.add(
        TreeSpec(
          x = r.nextFloat() * SCENE_TILE_WIDTH,
          s = minS + r.nextFloat() * (maxS - minS),
          alt = r.nextFloat() < 0.38f,
        )
      )
    }
    trees.sortBy { it.s }

    val groundPath = Path().apply {
      moveTo(0f, groundY)
      lineTo(SCENE_TILE_WIDTH, groundY)
      lineTo(SCENE_TILE_WIDTH, h)
      lineTo(0f, h)
      close()
    }
    val mainFoliage = Path()
    val altFoliage = Path()
    val snowCaps = Path()
    val trunks = Path()

    val offsets = floatArrayOf(-SCENE_TILE_WIDTH, 0f, SCENE_TILE_WIDTH)
    for (t in trees) {
      for (dx in offsets) {
        val cx = t.x + dx
        if (cx < -maxS || cx > SCENE_TILE_WIDTH + maxS) continue

        // Trunk
        val trunkTop = groundY - t.s * 0.18f
        trunks.moveTo(cx - 1.6f, groundY)
        trunks.lineTo(cx - 1.6f, trunkTop)
        trunks.lineTo(cx + 1.6f, trunkTop)
        trunks.lineTo(cx + 1.6f, groundY)
        trunks.close()

        // 3 foliage tiers
        for (k in 0 until 3) {
          val y0 = groundY - t.s * 0.13f - k * t.s * 0.27f
          val half = t.s * (0.29f - k * 0.065f)
          val apex = y0 - t.s * 0.37f
          val target = if (t.alt) altFoliage else mainFoliage
          target.moveTo(cx - half, y0)
          target.lineTo(cx, apex)
          target.lineTo(cx + half, y0)
          target.close()

          val sHalf = half * 0.52f
          val sY0 = apex + (y0 - apex) * 0.48f
          snowCaps.moveTo(cx - sHalf, sY0)
          snowCaps.lineTo(cx, apex)
          snowCaps.lineTo(cx + sHalf, sY0)
          snowCaps.close()
        }
      }
    }

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(groundPath, TaigaGround),
        LayerDraw(mainFoliage, TaigaFill),
        LayerDraw(altFoliage, TaigaFillAlt),
        LayerDraw(snowCaps, TaigaSnow, alpha = 0.62f),
        LayerDraw(trunks, TaigaTrunk),
      ),
    )
  }

  private fun buildNordicVillage(
    bottomFraction: Float,
    h: Float,
    speed: Float,
    seed: Long,
  ): ParallaxLayerSpec.VectorPaths {
    val r = XorshiftRng(seed)
    val minH = 42f
    val maxH = 96f
    val minW = 54f
    val maxW = 118f
    val gap = 36f
    val litProb = 0.68f

    val bodies = Path()
    val snowRoofs = Path()
    val dimWindows = Path()
    val litWindows = Path()

    var x = 16f
    while (x < SCENE_TILE_WIDTH - 48f) {
      val w = minW + r.nextFloat() * (maxW - minW)
      if (x + w > SCENE_TILE_WIDTH - 18f) break
      val houseH = minH + r.nextFloat() * (maxH - minH)
      val top = h - houseH
      val pitch = 14f + r.nextFloat() * 20f
      val midX = x + w * 0.5f

      // House silhouette
      bodies.moveTo(x, h)
      bodies.lineTo(x, top)
      bodies.lineTo(midX, top - pitch)
      bodies.lineTo(x + w, top)
      bodies.lineTo(x + w, h)
      bodies.close()

      // Snow roof
      snowRoofs.moveTo(x - 3f, top + 2f)
      snowRoofs.lineTo(midX, top - pitch - 2f)
      snowRoofs.lineTo(x + w + 3f, top + 2f)
      snowRoofs.lineTo(x + w + 1f, top + 6f)
      snowRoofs.lineTo(midX, top - pitch + 3f)
      snowRoofs.lineTo(x - 1f, top + 6f)
      snowRoofs.close()

      // Chimney
      if (r.nextFloat() < 0.6f) {
        val cx = x + w * (0.2f + r.nextFloat() * 0.25f)
        val cy = top - pitch * 0.5f
        bodies.moveTo(cx, cy)
        bodies.lineTo(cx, cy - 12f)
        bodies.lineTo(cx + 5f, cy - 12f)
        bodies.lineTo(cx + 5f, cy)
        bodies.close()
      }

      // Windows
      var yy = top + 10f
      while (yy < h - 14f) {
        var xx = x + 9f
        while (xx < x + w - 12f) {
          val v = r.nextFloat()
          if (v < litProb) {
            litWindows.moveTo(xx, yy)
            litWindows.lineTo(xx + 6f, yy)
            litWindows.lineTo(xx + 6f, yy + 8f)
            litWindows.lineTo(xx, yy + 8f)
            litWindows.close()
          } else if (v < litProb + 0.22f) {
            dimWindows.moveTo(xx, yy)
            dimWindows.lineTo(xx + 6f, yy)
            dimWindows.lineTo(xx + 6f, yy + 8f)
            dimWindows.lineTo(xx, yy + 8f)
            dimWindows.close()
          }
          xx += 15f
        }
        yy += 16f
      }
      x += w + gap + r.nextFloat() * 48f
    }

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(bodies, VillageBody),
        LayerDraw(snowRoofs, VillageSnow, alpha = 0.85f),
        LayerDraw(dimWindows, WindowDim, alpha = 0.45f),
        LayerDraw(litWindows, WindowLit, alpha = 0.92f),
      ),
    )
  }

  private fun buildSnowDrifts(
    bottomFraction: Float,
    h: Float,
    speed: Float,
    seed: Long,
  ): ParallaxLayerSpec.VectorPaths {
    val r1 = makeRidgeSampler(seed, 3, 0.7f)
    val r2 = makeRidgeSampler(seed + 19L, 4, 0.85f)
    val d1 = Path().apply { moveTo(0f, h) }
    val d2 = Path().apply { moveTo(0f, h) }

    var x = 0f
    while (x <= SCENE_TILE_WIDTH) {
      d1.lineTo(x, h - 42f - 42f * r1(x))
      d2.lineTo(x, h - 18f - 30f * r2(x))
      x += 12f
    }
    d1.lineTo(SCENE_TILE_WIDTH, h)
    d1.close()
    d2.lineTo(SCENE_TILE_WIDTH, h)
    d2.close()

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(d1, DriftBase),
        LayerDraw(d2, DriftRidge),
      ),
    )
  }

  private fun buildPowerline(
    bottomFraction: Float,
    h: Float,
    speed: Float,
    count: Int,
    poleH: Float,
    sag: Float,
  ): ParallaxLayerSpec.VectorPaths {
    val step = SCENE_TILE_WIDTH / count
    val top = h - poleH
    val armY = top + 24f

    val poles = Path()
    val wires = Path()

    for (k in -1..count + 1) {
      val cx = k * step
      // Vertical pole
      val poleTop = h - (poleH - 6f)
      poles.moveTo(cx - 4f, h)
      poles.lineTo(cx - 4f, poleTop)
      poles.lineTo(cx + 4f, poleTop)
      poles.lineTo(cx + 4f, h)
      poles.close()

      // Crossarm
      poles.moveTo(cx - 32f, armY)
      poles.lineTo(cx + 32f, armY)
      poles.lineTo(cx + 32f, armY + 6f)
      poles.lineTo(cx - 32f, armY + 6f)
      poles.close()
    }

    val wireOffsets = floatArrayOf(-22f, 0f, 22f)
    for (k in -1..count) {
      val xa = k * step
      val xb = xa + step
      for (off in wireOffsets) {
        wires.moveTo(xa + off, armY)
        wires.quadraticTo((xa + xb) * 0.5f + off, armY + sag * 2f, xb + off, armY)
      }
    }

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(wires, PowerFrost, alpha = 0.28f, strokeWidth = 2.6f),
        LayerDraw(wires, PowerWire, alpha = 0.85f, strokeWidth = 1.2f),
        LayerDraw(poles, PowerPole),
      ),
    )
  }

  private fun buildSnowFence(
    bottomFraction: Float,
    h: Float,
    speed: Float,
  ): ParallaxLayerSpec.VectorPaths {
    val step = 200f
    val ph = 94f
    val rails = Path().apply {
      // Top rail
      moveTo(0f, h - 74f)
      lineTo(SCENE_TILE_WIDTH, h - 74f)
      lineTo(SCENE_TILE_WIDTH, h - 69f)
      lineTo(0f, h - 69f)
      close()
      // Bottom rail
      moveTo(0f, h - 42f)
      lineTo(SCENE_TILE_WIDTH, h - 42f)
      lineTo(SCENE_TILE_WIDTH, h - 37f)
      lineTo(0f, h - 37f)
      close()
    }

    val posts = Path()
    val caps = Path()
    val maxK = (SCENE_TILE_WIDTH / step).toInt() + 1
    for (k in -1..maxK) {
      val x = k * step
      posts.moveTo(x - 4f, h)
      posts.lineTo(x - 4f, h - ph)
      posts.lineTo(x + 4f, h - ph)
      posts.lineTo(x + 4f, h)
      posts.close()

      caps.moveTo(x - 5f, h - ph)
      caps.lineTo(x + 5f, h - ph)
      caps.lineTo(x + 5f, h - ph + 4f)
      caps.lineTo(x - 5f, h - ph + 4f)
      caps.close()
    }

    return ParallaxLayerSpec.VectorPaths(
      bottomFraction = bottomFraction,
      baseHeight = h,
      speed = speed,
      draws = listOf(
        LayerDraw(rails, FenceRail),
        LayerDraw(posts, FencePost),
        LayerDraw(caps, FenceSnow, alpha = 0.75f),
      ),
    )
  }
}
