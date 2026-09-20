package com.leo.checkertic.core.image

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * How a background is referenced from a row.
 *
 * Two forms, distinguished by prefix:
 *  - `asset:<id>`  a curated background from [BackgroundCatalog]
 *  - `file:<name>` a user pick living in `filesDir/backgrounds/`
 *
 * Keeping both in one nullable TEXT column avoids a second table and a join
 * on a field almost every row leaves null.
 */
object BackgroundRef {
    private const val ASSET = "asset:"
    private const val FILE = "file:"

    fun asset(id: String) = "$ASSET$id"
    fun file(name: String) = "$FILE$name"

    fun assetIdOf(ref: String?): String? = ref?.takeIf { it.startsWith(ASSET) }?.removePrefix(ASSET)
    fun fileNameOf(ref: String?): String? = ref?.takeIf { it.startsWith(FILE) }?.removePrefix(FILE)

    /** Every `file:` name in a set of refs — used to prune orphans. */
    fun fileNames(refs: Collection<String?>): Set<String> =
        refs.mapNotNull { fileNameOf(it) }.toSet()
}

@Immutable
data class CuratedBackground(
    val id: String,
    val label: String,
    val stops: List<Color>,
    /** True for backgrounds light enough to need dark text over them. */
    val light: Boolean = false
) {
    /**
     * Rendered as a gradient rather than a bundled image.
     *
     * A curated set of six photographs would add megabytes to the APK and
     * still need decoding at runtime. These draw in a single GPU op, scale to
     * any size without resampling, cost nothing in download size, and adapt
     * to the card they are drawn in. For "a small curated set of standard
     * backgrounds" that is strictly better than shipping JPEGs.
     */
    fun brush(width: Float, height: Float): Brush = Brush.linearGradient(
        colors = stops,
        start = Offset(0f, 0f),
        end = Offset(width, height)
    )
}

object BackgroundCatalog {

    val all: List<CuratedBackground> = listOf(
        CuratedBackground(
            id = "slate",
            label = "Slate",
            stops = listOf(Color(0xFF1F2937), Color(0xFF111827))
        ),
        CuratedBackground(
            id = "deepsea",
            label = "Deep sea",
            stops = listOf(Color(0xFF0B2E4F), Color(0xFF07182B))
        ),
        CuratedBackground(
            id = "moss",
            label = "Moss",
            stops = listOf(Color(0xFF14361F), Color(0xFF0A1F13))
        ),
        CuratedBackground(
            id = "ember",
            label = "Ember",
            stops = listOf(Color(0xFF4A1D1D), Color(0xFF241010))
        ),
        CuratedBackground(
            id = "plum",
            label = "Plum",
            stops = listOf(Color(0xFF32204A), Color(0xFF1A1029))
        ),
        CuratedBackground(
            id = "paper",
            label = "Paper",
            stops = listOf(Color(0xFFF7F3EA), Color(0xFFE8E2D4)),
            light = true
        ),
        CuratedBackground(
            id = "mist",
            label = "Mist",
            stops = listOf(Color(0xFFE8EEF5), Color(0xFFD3DDE8)),
            light = true
        ),
        CuratedBackground(
            id = "sand",
            label = "Sand",
            stops = listOf(Color(0xFFF0E4D0), Color(0xFFDCCBAF)),
            light = true
        )
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String?): CuratedBackground? = id?.let { byId[it] }

    fun forRef(ref: String?): CuratedBackground? = find(BackgroundRef.assetIdOf(ref))
}
