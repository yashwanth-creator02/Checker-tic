package com.leo.checkertic.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.core.image.BackgroundCatalog
import com.leo.checkertic.core.image.BackgroundRef
import com.leo.checkertic.core.image.CuratedBackground
import com.leo.checkertic.core.image.ImageStore
import com.leo.checkertic.ui.theme.AppTheme

/**
 * ============================================================================
 *  BACKGROUNDS
 * ============================================================================
 *
 * Draws whatever a `background` reference points at, behind arbitrary
 * content.
 *
 * Curated backgrounds are gradients drawn straight into the existing draw
 * scope — no bitmap, no decode, no extra layout node. User picks go through
 * [ImageStore], which decodes them downsampled and cached on a background
 * thread; until that returns, the curated fallback colour is drawn, so a card
 * never flashes white then fills in.
 *
 * A scrim is always drawn over an image background. Without one, text
 * contrast depends entirely on which photo the user chose, and some photo
 * will always make the title unreadable.
 */
@Composable
fun BackgroundSurface(
    reference: String?,
    modifier: Modifier = Modifier,
    target: ImageStore.Target = ImageStore.Target.CARD,
    scrim: Boolean = true,
    fallback: Color = AppTheme.colors.surface,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val curated = remember(reference) { BackgroundCatalog.forRef(reference) }
    val fileName = remember(reference) { BackgroundRef.fileNameOf(reference) }

    var bitmap by remember(reference, target) { mutableStateOf<ImageBitmap?>(null) }

    // Decoding is suspended work on Dispatchers.IO inside ImageStore. The
    // key includes the reference so scrolling a recycled card cancels the
    // previous decode instead of racing it.
    LaunchedEffect(fileName, target) {
        bitmap = fileName?.let { ImageStore.load(context, it, target) }
    }

    val scrimBrush = remember(colors.isDark) {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = if (colors.isDark) 0.35f else 0.18f),
                Color.Black.copy(alpha = if (colors.isDark) 0.60f else 0.34f)
            )
        )
    }

    Box(
        modifier = modifier.drawWithCache {
            val image = bitmap
            onDrawBehind {
                when {
                    image != null -> {
                        drawImageCover(image)
                        if (scrim) drawRect(brush = scrimBrush)
                    }
                    curated != null -> drawRect(brush = curated.brush(size.width, size.height))
                    else -> drawRect(color = fallback)
                }
            }
        },
        content = content
    )
}

/**
 * Centre-crop draw.
 *
 * `Image(contentScale = Crop)` would work but adds a layout node per card.
 * Doing the arithmetic here keeps a background-bearing card exactly as cheap
 * to lay out as a plain one.
 */
private fun DrawScope.drawImageCover(image: ImageBitmap) {
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    translate(
        left = (size.width - width) / 2f,
        top = (size.height - height) / 2f
    ) {
        drawImage(image = image, dstSize = IntSize(width.toInt(), height.toInt()))
    }
}

/** True when a reference resolves to a light background needing dark text. */
@Composable
fun isLightBackground(reference: String?): Boolean =
    remember(reference) { BackgroundCatalog.forRef(reference)?.light == true }

/**
 * Background picker.
 *
 * Uses `PickVisualMedia`, which is the system Photo Picker — no storage
 * permission is requested anywhere in the app, and the user grants access to
 * exactly one image rather than their whole library. The contract falls back
 * to `ACTION_OPEN_DOCUMENT` automatically on devices without the picker, so
 * there is no version branching here.
 */
@Composable
fun BackgroundPicker(
    current: String?,
    onSelect: (String?) -> Unit,
    onPickFromGallery: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickFromGallery) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Background",
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(spacing.sm))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            item {
                SwatchButton(
                    selected = current == null,
                    onClick = { onSelect(null) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = "No background",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            item {
                SwatchButton(
                    selected = BackgroundRef.fileNameOf(current) != null,
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "Choose from gallery",
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            items(BackgroundCatalog.all, key = { it.id }) { background ->
                CuratedSwatch(
                    background = background,
                    selected = current == BackgroundRef.asset(background.id),
                    onClick = { onSelect(BackgroundRef.asset(background.id)) }
                )
            }
        }
    }
}

@Composable
private fun SwatchButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .background(colors.surfaceSunken)
            .then(
                if (selected) {
                    Modifier.outlined(colors.accent)
                } else {
                    Modifier.outlined(colors.hairline)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun CuratedSwatch(
    background: CuratedBackground,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .outlined(if (selected) colors.accent else colors.hairline)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = background.brush(size.width, size.height),
                cornerRadius = CornerRadius(0f, 0f)
            )
        }
    }
}

/** A hairline outline at the swatch radius. */
private fun Modifier.outlined(color: Color): Modifier =
    this.border(width = 1.dp, color = color, shape = RoundedCornerShape(12.dp))
