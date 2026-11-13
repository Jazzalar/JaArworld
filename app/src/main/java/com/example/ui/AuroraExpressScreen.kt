package com.example.ui

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.scene.ParallaxLayerSpec
import com.example.scene.ParallaxSceneBuilder
import com.example.scene.SCENE_TILE_WIDTH
import com.example.scene.XorshiftRng
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraEmerald
import com.example.ui.theme.AuroraGreenAlt
import com.example.ui.theme.AuroraPurpleAlt
import com.example.ui.theme.AuroraSnowFlash
import com.example.ui.theme.AuroraSurgeMint
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.FrostButtonBg
import com.example.ui.theme.FrostButtonBorder
import com.example.ui.theme.FrostTextMuted
import com.example.ui.theme.FrostTextPrimary
import com.example.ui.theme.GroundDeep
import com.example.ui.theme.MistIce
import com.example.ui.theme.MoonCore
import com.example.ui.theme.MoonHalo
import com.example.ui.theme.MoonInner
import com.example.ui.theme.SkyHorizon
import com.example.ui.theme.SkyHorizonUpper
import com.example.ui.theme.SkyLower
import com.example.ui.theme.SkyMid
import com.example.ui.theme.SkyTop
import com.example.ui.theme.SkyUpper
import com.example.ui.theme.SnowFlakeColor
import com.example.viewmodel.AuroraExpressViewModel
import com.example.viewmodel.AuroraSurgeEvent
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (Math.PI * 2.0).toFloat()

private data class StarSpec(
  val xFrac: Float,
  val yFrac: Float,
  val radiusDp: Float,
  val color: Color,
  val phase: Float,
)

private data class SnowBandSpec(
  val radiusDp: Float,
  val alpha: Float,
  val minVy: Float,
  val maxVy: Float,
  val windFactor: Float,
  val share: Float,
)

private class SnowFlake(
  var x: Float,
  var y: Float,
  var vy: Float,
  var sway: Float,
  var swayAmp: Float,
)

private val SNOW_BANDS = listOf(
  SnowBandSpec(0.9f, 0.28f, 55f, 105f, 0.45f, 0.44f),
  SnowBandSpec(1.5f, 0.44f, 105f, 175f, 0.75f, 0.31f),
  SnowBandSpec(2.3f, 0.60f, 170f, 260f, 1.05f, 0.17f),
  SnowBandSpec(3.4f, 0.74f, 250f, 360f, 1.35f, 0.08f),
)

@Composable
fun AuroraExpressScreen(
  viewModel: AuroraExpressViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> viewModel.onLifecyclePause()
        Lifecycle.Event.ON_RESUME -> viewModel.onLifecycleResume()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val reducedMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
  val layers = remember { ParallaxSceneBuilder.buildLayers() }
  val stars = remember { buildStars() }

  var elapsedSec by remember { mutableFloatStateOf(0f) }
  var sceneElapsedSec by remember { mutableFloatStateOf(0f) }
  var lastSurgeAnim by remember { mutableStateOf<AuroraSurgeEvent?>(null) }

  LaunchedEffect(uiState.activeSurge?.id) {
    if (uiState.activeSurge != null) {
      lastSurgeAnim = uiState.activeSurge
    }
  }

  // Snow particle state stored cleanly outside Compose recomposition
  val density = LocalDensity.current.density
  val snowBandsState = remember { Array(SNOW_BANDS.size) { mutableListOf<SnowFlake>() } }
  var snowViewportW by remember { mutableFloatStateOf(0f) }
  var snowViewportH by remember { mutableFloatStateOf(0f) }

  // 60/120fps frame loop
  LaunchedEffect(reducedMotion, uiState.speedMultiplier) {
    if (reducedMotion) return@LaunchedEffect
    var lastNanos = 0L
    while (true) {
      withFrameNanos { nowNanos ->
        if (lastNanos == 0L) {
          lastNanos = nowNanos
          return@withFrameNanos
        }
        val dt = ((nowNanos - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastNanos = nowNanos
        elapsedSec += dt
        sceneElapsedSec += dt * uiState.speedMultiplier

        // Update snow particles in-place
        if (snowViewportW > 0f && snowViewportH > 0f) {
          val gust = 0.8f +
            0.25f * sin(elapsedSec * 0.45f) +
            0.14f * sin(elapsedSec * 1.4f)
          val speedScale = 0.7f + 0.3f * uiState.speedMultiplier
          for (b in SNOW_BANDS.indices) {
            val spec = SNOW_BANDS[b]
            val list = snowBandsState[b]
            val vx = -spec.windFactor * gust * 290f * density * speedScale
            for (f in list) {
              f.sway += dt * 1.8f
              f.y += f.vy * density * dt
              f.x += (vx + sin(f.sway) * f.swayAmp * density) * dt
              if (f.y > snowViewportH + 15f * density || f.x < -120f * density) {
                f.x = Random.nextFloat() * (snowViewportW + 260f * density)
                f.y = -15f * density - Random.nextFloat() * 50f * density
                f.vy = spec.minVy + Random.nextFloat() * (spec.maxVy - spec.minVy)
              }
            }
          }
        }
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SkyTop)
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          val xFrac = (offset.x / size.width.toFloat().coerceAtLeast(1f))
          viewModel.triggerManualSurge(xFrac)
        }
      },
  ) {
    // 1. Sky, Stars, Moon, Aurora Borealis & Clouds (with Screen blend compositing)
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
      drawArcticSky(
        elapsedSec = elapsedSec,
        stars = stars,
        activeSurge = lastSurgeAnim,
        reducedMotion = reducedMotion,
      )
    }

    // 2. Parallax Nordic Landscape, Frost Tint, Snow Particles, Surge Flash, Vignette
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Initialize or resize snow particle pools when canvas dimensions change
      if (kotlin.math.abs(w - snowViewportW) > 4f || kotlin.math.abs(h - snowViewportH) > 4f) {
        snowViewportW = w
        snowViewportH = h
        rebuildSnowPools(snowBandsState, w, h, density)
      }

      // Responsive scale factor matching CSS proportions across portrait/landscape/tablet
      val scale = (h / 820f).coerceIn(0.60f * density, 1.45f * density)

      // Solid deep Arctic ground beneath the lowest snow drifts
      val groundTop = h * 0.965f
      drawRect(
        color = GroundDeep,
        topLeft = Offset(0f, groundTop),
        size = Size(w, h - groundTop),
      )

      // 10 Parallax Layers
      for (layer in layers) {
        when (layer) {
          is ParallaxLayerSpec.Mist -> {
            val mistH = layer.baseHeight * scale
            val mistBottom = h * (1f - layer.bottomFraction)
            val mistTop = mistBottom - mistH
            drawRect(
              brush = Brush.verticalGradient(
                0f to MistIce.copy(alpha = 0f),
                0.48f to MistIce.copy(alpha = 0.14f),
                1f to MistIce.copy(alpha = 0f),
                startY = mistTop,
                endY = mistBottom,
              ),
              topLeft = Offset(0f, mistTop),
              size = Size(w, mistH),
            )
          }

          is ParallaxLayerSpec.VectorPaths -> {
            val bottomY = h * (1f - layer.bottomFraction)
            val topY = bottomY - layer.baseHeight * scale
            val tileW = SCENE_TILE_WIDTH * scale
            val offsetUnits = (sceneElapsedSec * layer.speed) % SCENE_TILE_WIDTH
            var startX = -offsetUnits * scale
            if (startX > 0f) startX -= tileW

            var tx = startX
            while (tx < w + tileW * 0.25f) {
              withTransform({
                translate(left = tx, top = topY)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
              }) {
                for (d in layer.draws) {
                  if (d.strokeWidth > 0f) {
                    drawPath(
                      path = d.path,
                      color = d.color,
                      alpha = d.alpha,
                      style = Stroke(width = d.strokeWidth),
                    )
                  } else {
                    drawPath(
                      path = d.path,
                      color = d.color,
                      alpha = d.alpha,
                    )
                  }
                }
              }
              tx += tileW
            }
          }
        }
      }

      // Frost Tint
      drawRect(
        brush = Brush.verticalGradient(
          0f to Color(0x52061022),
          0.55f to Color(0x2E0E223A),
          1f to Color(0x4008162A),
        ),
      )

      // Snow Particle System
      if (!reducedMotion) {
        for (b in SNOW_BANDS.indices) {
          val spec = SNOW_BANDS[b]
          val rPx = spec.radiusDp * density
          val flakeColor = SnowFlakeColor.copy(alpha = spec.alpha)
          val list = snowBandsState[b]
          for (i in 0 until list.size) {
            val f = list[i]
            drawCircle(
              color = flakeColor,
              radius = rPx,
              center = Offset(f.x, f.y),
            )
          }
        }
      }

      // Aurora Surge Snow Flash
      val surgeAlpha = computeSurgeFlashAlpha(lastSurgeAnim)
      if (surgeAlpha > 0.002f) {
        drawRect(
          color = AuroraSnowFlash.copy(alpha = surgeAlpha * 0.32f),
          blendMode = BlendMode.Screen,
        )
      }

      // Window Vignette
      val maxRadius = maxOf(w, h) * 0.72f
      drawRect(
        brush = Brush.radialGradient(
          0f to Color.Transparent,
          0.56f to Color.Transparent,
          0.84f to Color(0x12AFE1FF),
          1f to Color(0x85020814),
          center = Offset(w * 0.5f, h * 0.5f),
          radius = maxRadius,
        ),
      )
    }

    // 3. Minimalist Nordic Frosted-Glass HUD
    AuroraHudOverlay(
      isSoundOn = uiState.isSoundOn,
      speedMultiplier = uiState.speedMultiplier,
      hudVisible = uiState.hudVisible,
      onToggleSound = viewModel::toggleSound,
      onTriggerSurge = { viewModel.triggerManualSurge() },
      onTriggerWhistle = viewModel::triggerWhistle,
      onCycleSpeed = viewModel::cycleSpeed,
      onToggleHud = viewModel::toggleHud,
    )
  }
}

@Composable
private fun AuroraHudOverlay(
  isSoundOn: Boolean,
  speedMultiplier: Float,
  hudVisible: Boolean,
  onToggleSound: () -> Unit,
  onTriggerSurge: () -> Unit,
  onTriggerWhistle: () -> Unit,
  onCycleSpeed: () -> Unit,
  onToggleHud: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    // Top Bar: Brand Badge + Sound Pill + HUD Hide Toggle
    Row(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      // Left: Brand title + HUD toggle
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AnimatedVisibility(
          visible = hudVisible,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          Surface(
            color = FrostButtonBg,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, FrostButtonBorder),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
              Text(
                text = stringResource(R.string.title_aurora_express),
                style = MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.4.sp,
                ),
                color = FrostTextPrimary,
              )
              Text(
                text = stringResource(R.string.subtitle_arctic_night),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 9.sp,
                  letterSpacing = 1.1.sp,
                ),
                color = FrostTextMuted,
              )
            }
          }
        }

        val hudCd = stringResource(R.string.cd_toggle_hud)
        Surface(
          color = FrostButtonBg.copy(alpha = if (hudVisible) 0.58f else 0.34f),
          shape = CircleShape,
          border = BorderStroke(1.dp, FrostButtonBorder.copy(alpha = if (hudVisible) 0.32f else 0.18f)),
        ) {
          IconButton(
            onClick = onToggleHud,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("hud_toggle_button")
              .semantics { contentDescription = hudCd },
          ) {
            Icon(
              imageVector = if (hudVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = null,
              tint = FrostTextPrimary.copy(alpha = if (hudVisible) 0.9f else 0.65f),
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      // Right: Iconic Sound Toggle Pill matching HTML
      val soundLabel = if (isSoundOn) stringResource(R.string.sound_on) else stringResource(R.string.sound_off)
      val soundCd = if (isSoundOn) {
        stringResource(R.string.cd_sound_toggle_on)
      } else {
        stringResource(R.string.cd_sound_toggle_off)
      }
      Surface(
        color = if (isSoundOn) Color(0xB80A263D) else FrostButtonBg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
          width = 1.dp,
          color = if (isSoundOn) AuroraEmerald.copy(alpha = 0.65f) else FrostButtonBorder,
        ),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(999.dp))
          .clickable(onClick = onToggleSound)
          .testTag("sound_toggle_button")
          .semantics { contentDescription = soundCd },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = if (isSoundOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = null,
            tint = if (isSoundOn) AuroraEmerald else FrostTextPrimary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = soundLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = FrostTextPrimary,
          )
        }
      }
    }

    // Bottom Floating Control Pill (Surge · Whistle · Speed)
    AnimatedVisibility(
      visible = hudVisible,
      enter = fadeIn() + slideInVertically { it / 2 },
      exit = fadeOut() + slideOutVertically { it / 2 },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 18.dp, start = 16.dp, end = 16.dp),
    ) {
      Surface(
        color = FrostButtonBg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, FrostButtonBorder),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          HudPillAction(
            icon = Icons.Rounded.AutoAwesome,
            label = stringResource(R.string.action_aurora_surge),
            contentDesc = stringResource(R.string.cd_aurora_surge),
            tint = AuroraEmerald,
            tag = "surge_button",
            onClick = onTriggerSurge,
          )

          HudPillAction(
            icon = Icons.Rounded.Train,
            label = stringResource(R.string.action_whistle),
            contentDesc = stringResource(R.string.cd_whistle),
            tint = AuroraCyan,
            tag = "whistle_button",
            onClick = onTriggerWhistle,
          )

          HudPillAction(
            icon = Icons.Rounded.Speed,
            label = stringResource(R.string.action_speed_format, speedMultiplier),
            contentDesc = stringResource(R.string.cd_speed_cycle),
            tint = FrostTextPrimary,
            tag = "speed_button",
            onClick = onCycleSpeed,
          )
        }
      }
    }
  }
}

@Composable
private fun HudPillAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  contentDesc: String,
  tint: Color,
  tag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .clip(RoundedCornerShape(999.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag(tag)
      .semantics { contentDescription = contentDesc },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = FrostTextPrimary,
    )
  }
}

private fun DrawScope.drawArcticSky(
  elapsedSec: Float,
  stars: List<StarSpec>,
  activeSurge: AuroraSurgeEvent?,
  reducedMotion: Boolean,
) {
  val w = size.width
  val h = size.height

  // 1. Sky vertical gradient
  drawRect(
    brush = Brush.verticalGradient(
      0.00f to SkyTop,
      0.22f to SkyUpper,
      0.46f to SkyMid,
      0.68f to SkyLower,
      0.86f to SkyHorizonUpper,
      1.00f to SkyHorizon,
    ),
  )

  // 2. Twinkling stars (upper 58% of sky)
  val globalTwinkle = if (reducedMotion) 0.7f else {
    0.665f + 0.215f * sin(elapsedSec * TAU / 18f)
  }
  for (s in stars) {
    val localTwinkle = if (reducedMotion) 1f else {
      0.75f + 0.25f * sin(elapsedSec * 0.9f + s.phase)
    }
    val alpha = (globalTwinkle * localTwinkle).coerceIn(0.15f, 0.95f)
    drawCircle(
      color = s.color.copy(alpha = alpha),
      radius = s.radiusDp * density,
      center = Offset(s.xFrac * w, s.yFrac * h),
    )
  }

  // 3. Moon (right 16%, top 11%, 11vmin diameter)
  val vmin = min(w, h)
  val moonRadius = vmin * 0.055f
  val moonCenter = Offset(w * 0.84f, h * 0.14f)
  // Soft outer halo
  drawCircle(
    brush = Brush.radialGradient(
      0f to MoonHalo.copy(alpha = 0.28f),
      0.45f to MoonHalo.copy(alpha = 0.14f),
      1f to Color.Transparent,
      center = moonCenter,
      radius = moonRadius * 2.8f,
    ),
    radius = moonRadius * 2.8f,
    center = moonCenter,
  )
  // Moon disc
  drawCircle(
    brush = Brush.radialGradient(
      0f to MoonCore,
      0.52f to MoonInner,
      0.74f to MoonHalo.copy(alpha = 0.30f),
      1f to Color.Transparent,
      center = Offset(moonCenter.x - moonRadius * 0.12f, moonCenter.y - moonRadius * 0.12f),
      radius = moonRadius * 1.1f,
    ),
    radius = moonRadius,
    center = moonCenter,
    alpha = 0.92f,
  )

  // 4. Aurora Borealis Ribbons (Screen blend)
  val swayX = if (reducedMotion) 0f else sin(elapsedSec * TAU / 22f) * w * 0.04f
  val swayScaleY = if (reducedMotion) 1f else 1f + 0.08f * sin(elapsedSec * TAU / 22f)
  val drift1 = if (reducedMotion) 0f else (elapsedSec * 24f) % w
  val drift2 = if (reducedMotion) 0f else -(elapsedSec * 18f) % w

  // Primary Aurora Ribbon Glows
  drawAuroraRibbonBlob(
    center = Offset(((w * 0.24f + drift1 + swayX) % (w * 1.2f)) - w * 0.1f, h * 0.24f),
    rx = w * 0.42f,
    ry = h * 0.18f * swayScaleY,
    color = AuroraEmerald.copy(alpha = 0.44f),
  )
  drawAuroraRibbonBlob(
    center = Offset(((w * 0.56f + drift1 * 0.8f + swayX) % (w * 1.2f)) - w * 0.1f, h * 0.19f),
    rx = w * 0.38f,
    ry = h * 0.16f * swayScaleY,
    color = AuroraCyan.copy(alpha = 0.38f),
  )
  drawAuroraRibbonBlob(
    center = Offset(((w * 0.82f + drift1 * 0.6f + swayX) % (w * 1.2f)) - w * 0.1f, h * 0.27f),
    rx = w * 0.40f,
    ry = h * 0.19f * swayScaleY,
    color = AuroraViolet.copy(alpha = 0.32f),
  )

  // Secondary Alt Ribbon
  drawAuroraRibbonBlob(
    center = Offset(((w * 0.38f + drift2 - swayX * 0.5f + w * 2f) % (w * 1.2f)) - w * 0.1f, h * 0.31f),
    rx = w * 0.35f,
    ry = h * 0.15f,
    color = AuroraGreenAlt.copy(alpha = 0.30f),
  )
  drawAuroraRibbonBlob(
    center = Offset(((w * 0.70f + drift2 * 0.9f + w * 2f) % (w * 1.2f)) - w * 0.1f, h * 0.23f),
    rx = w * 0.38f,
    ry = h * 0.17f,
    color = AuroraPurpleAlt.copy(alpha = 0.26f),
  )

  // Vertical Curtain Shimmer Rays across upper sky
  val rayCount = 14
  for (i in 0 until rayCount) {
    val phase = i * 0.72f + elapsedSec * 0.45f
    val rxFrac = ((i.toFloat() / rayCount) + (elapsedSec * 0.012f)) % 1f
    val cx = rxFrac * w
    val rayW = w * (0.045f + 0.02f * sin(phase))
    val rayTop = h * (0.05f + 0.04f * cos(phase * 0.8f))
    val rayH = h * (0.34f + 0.07f * sin(phase * 1.2f))
    val rayAlpha = (0.08f + 0.07f * sin(phase)).coerceAtLeast(0.02f)
    val rayColor = when (i % 3) {
      0 -> AuroraEmerald
      1 -> AuroraCyan
      else -> AuroraViolet
    }
    drawRect(
      brush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.3f to rayColor.copy(alpha = rayAlpha),
        0.7f to rayColor.copy(alpha = rayAlpha * 0.7f),
        1f to Color.Transparent,
        startY = rayTop,
        endY = rayTop + rayH,
      ),
      topLeft = Offset(cx - rayW * 0.5f, rayTop),
      size = Size(rayW, rayH),
      blendMode = BlendMode.Screen,
    )
  }

  // 5. Aurora Surge Bloom (triggered periodically or by user tap)
  if (activeSurge != null) {
    val surgeProgress = ((System.nanoTime() - activeSurge.startedAtNanos) / 3_600_000_000.0).toFloat()
    if (surgeProgress in 0f..1f) {
      val opacity = evaluateSurgeKeyframes(surgeProgress) * activeSurge.power
      if (opacity > 0.005f) {
        val sx = activeSurge.xFraction * w
        val sy = h * 0.26f
        val rx = w * 0.52f
        val ry = h * 0.30f
        withTransform({
          translate(left = sx, top = sy)
          scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset.Zero)
        }) {
          drawCircle(
            brush = Brush.radialGradient(
              0f to AuroraSurgeMint.copy(alpha = 0.75f * opacity),
              0.48f to Color(0x527D87FF).copy(alpha = 0.36f * opacity),
              1f to Color.Transparent,
              center = Offset.Zero,
              radius = rx,
            ),
            radius = rx,
            center = Offset.Zero,
            blendMode = BlendMode.Screen,
          )
        }
      }
    }
  }

  // 6. Drifting Arctic Clouds (upper 42%)
  val cloudPeriod = 1600f * density
  val cloudShift = if (reducedMotion) 0f else -((elapsedSec * (1600f / 115f)) % 1600f) * density
  val clouds = listOf(
    Triple(180f, 80f, Pair(280f, 75f) to Color(0x8C10243E)),
    Triple(640f, 125f, Pair(360f, 95f) to Color(0x990C1C34)),
    Triple(1080f, 70f, Pair(300f, 70f) to Color(0x80142A48)),
    Triple(1420f, 135f, Pair(340f, 90f) to Color(0x940E1E38)),
  )
  var tileStart = cloudShift
  if (tileStart > 0f) tileStart -= cloudPeriod
  while (tileStart < w + cloudPeriod * 0.3f) {
    for ((cxDp, cyDp, shape) in clouds) {
      val (rxDp, ryDp) = shape.first
      val color = shape.second
      val cx = tileStart + cxDp * density
      val cy = (cyDp / 340f) * (h * 0.42f)
      val rx = rxDp * density
      val ry = ryDp * density
      withTransform({
        translate(left = cx, top = cy)
        scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset.Zero)
      }) {
        drawCircle(
          brush = Brush.radialGradient(
            0f to color,
            0.7f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = rx,
          ),
          radius = rx,
          center = Offset.Zero,
        )
      }
    }
    tileStart += cloudPeriod
  }
}

private fun DrawScope.drawAuroraRibbonBlob(
  center: Offset,
  rx: Float,
  ry: Float,
  color: Color,
) {
  if (rx <= 1f || ry <= 1f) return
  withTransform({
    translate(left = center.x, top = center.y)
    scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset.Zero)
  }) {
    drawCircle(
      brush = Brush.radialGradient(
        0f to color,
        0.72f to Color.Transparent,
        center = Offset.Zero,
        radius = rx,
      ),
      radius = rx,
      center = Offset.Zero,
      blendMode = BlendMode.Screen,
    )
  }
}

private fun evaluateSurgeKeyframes(t: Float): Float {
  // Keyframes: [0:0, 0.25:0.82, 0.52:0.45, 0.70:0.70, 1.0:0]
  return when {
    t < 0.25f -> (t / 0.25f) * 0.82f
    t < 0.52f -> 0.82f + ((t - 0.25f) / 0.27f) * (0.45f - 0.82f)
    t < 0.70f -> 0.45f + ((t - 0.52f) / 0.18f) * (0.70f - 0.45f)
    else -> 0.70f * (1f - (t - 0.70f) / 0.30f).coerceIn(0f, 1f)
  }
}

private fun computeSurgeFlashAlpha(surge: AuroraSurgeEvent?): Float {
  if (surge == null) return 0f
  val t = ((System.nanoTime() - surge.startedAtNanos) / 3_600_000_000.0).toFloat()
  if (t !in 0f..1f) return 0f
  return if (t < 0.35f) {
    (t / 0.35f) * 0.28f * surge.power
  } else {
    0.28f * surge.power * (1f - (t - 0.35f) / 0.65f).coerceIn(0f, 1f)
  }
}

private fun rebuildSnowPools(
  bands: Array<MutableList<SnowFlake>>,
  w: Float,
  h: Float,
  density: Float,
) {
  val total = (((w / density) * (h / density)) / 4800f).roundToInt().coerceIn(40, 260)
  for (b in SNOW_BANDS.indices) {
    val spec = SNOW_BANDS[b]
    val count = (total * spec.share).roundToInt().coerceAtLeast(6)
    val list = bands[b]
    list.clear()
    for (i in 0 until count) {
      list.add(
        SnowFlake(
          x = Random.nextFloat() * (w + 300f * density) - 100f * density,
          y = Random.nextFloat() * (h + 20f * density) - 10f * density,
          vy = spec.minVy + Random.nextFloat() * (spec.maxVy - spec.minVy),
          sway = Random.nextFloat() * TAU,
          swayAmp = 12f + Random.nextFloat() * 22f,
        )
      )
    }
  }
}

private fun buildStars(): List<StarSpec> {
  val fixedStars = listOf(
    StarSpec(0.05f, 0.10f, 1.2f, Color.White, 0.1f),
    StarSpec(0.12f, 0.27f, 1.0f, Color(0xFFCCE8FF), 1.1f),
    StarSpec(0.19f, 0.06f, 1.8f, Color.White, 2.0f),
    StarSpec(0.27f, 0.20f, 1.0f, Color(0xFFE6F4FF), 2.8f),
    StarSpec(0.35f, 0.09f, 1.3f, Color.White, 3.6f),
    StarSpec(0.43f, 0.30f, 1.0f, Color(0xFFBFE2FF), 4.2f),
    StarSpec(0.49f, 0.05f, 1.6f, Color.White, 0.7f),
    StarSpec(0.57f, 0.17f, 1.0f, Color.White, 1.9f),
    StarSpec(0.64f, 0.10f, 1.4f, Color(0xFFDFF1FF), 2.4f),
    StarSpec(0.72f, 0.25f, 1.0f, Color.White, 3.3f),
    StarSpec(0.79f, 0.07f, 1.2f, Color.White, 4.9f),
    StarSpec(0.86f, 0.18f, 1.5f, Color(0xFFD6ECFF), 5.5f),
    StarSpec(0.93f, 0.12f, 1.0f, Color.White, 0.9f),
    StarSpec(0.97f, 0.29f, 1.3f, Color.White, 2.7f),
  )
  val rng = XorshiftRng(777L)
  val extra = List(28) {
    StarSpec(
      xFrac = rng.nextFloat(),
      yFrac = 0.03f + rng.nextFloat() * 0.45f,
      radiusDp = 0.7f + rng.nextFloat() * 0.8f,
      color = if (rng.nextFloat() < 0.5f) Color.White else Color(0xFFCDE9FF),
      phase = rng.nextFloat() * TAU,
    )
  }
  return fixedStars + extra
}
