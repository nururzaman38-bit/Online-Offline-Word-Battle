package com.wordbattle.com.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.AiDifficulty
import com.wordbattle.com.data.model.CampaignProgress
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import com.wordbattle.com.ui.theme.Brown
import com.wordbattle.com.ui.theme.BrownDark
import com.wordbattle.com.ui.theme.BrownInk
import com.wordbattle.com.ui.theme.BrownLight
import com.wordbattle.com.ui.theme.Parchment
import com.wordbattle.com.ui.theme.ParchmentDeep
import com.wordbattle.com.ui.theme.ParchmentLight
import com.wordbattle.com.ui.theme.ScrollGold
import com.wordbattle.com.ui.theme.ScrollGoldLight
import kotlin.math.sin

/**
 * Vertical, ancient-scroll style campaign level map.
 *
 * Parchment background with faint scattered letters, level nodes joined by a hand-drawn ink trail,
 * classic padlocks on locked levels, and a crowned-eagle "RIDDLE MASTER" boss every 5th level.
 * Chapters are titled on ornamental ribbon banners.
 */
@Composable
fun LevelSelectScreen(
    levels: List<LevelDefinition>,
    progress: List<CampaignProgress>,
    currentUnlocked: Int,
    onSelectLevel: (LevelDefinition) -> Unit,
    onBack: () -> Unit
) {
    val progressMap = progress.associateBy { it.levelNumber }
    val chapters = levels.chunked(5) // Every 5th level is a boss → one boss per chapter finale

    ParchmentBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = BrownInk)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "CAMPAIGN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrownInk,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    val totalStars = progress.sumOf { it.stars }
                    Text(
                        "Level $currentUnlocked / ${levels.size}   •   ★ $totalStars",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brown.copy(alpha = 0.85f)
                    )
                }
                Surface(shape = CircleShape, color = ScrollGold, border = androidx.compose.foundation.BorderStroke(2.dp, BrownDark)) {
                    Text(
                        "★",
                        color = BrownDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 120.dp)
            ) {
                items(chapters.withIndex().toList()) { (chapterIndex, chapterLevels) ->
                    ChapterSection(
                        chapterIndex = chapterIndex,
                        levels = chapterLevels,
                        progressMap = progressMap,
                        currentUnlocked = currentUnlocked,
                        onSelectLevel = onSelectLevel
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterSection(
    chapterIndex: Int,
    levels: List<LevelDefinition>,
    progressMap: Map<Int, CampaignProgress>,
    currentUnlocked: Int,
    onSelectLevel: (LevelDefinition) -> Unit
) {
    // Parchment panel per chapter
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ParchmentLight.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ParchmentDeep),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            ChapterRibbon(chapterIndex)
            Spacer(Modifier.height(10.dp))
            ChapterLevels(
                levels = levels,
                progressMap = progressMap,
                currentUnlocked = currentUnlocked,
                onSelectLevel = onSelectLevel
            )
        }
    }
}

@Composable
private fun ChapterRibbon(chapterIndex: Int) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = ParchmentDeep,
            border = androidx.compose.foundation.BorderStroke(2.dp, ScrollGold),
            shadowElevation = 4.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("❧", color = ScrollGold, fontSize = 18.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        "CHAPTER ${chapterIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ScrollGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        chapterName(chapterIndex),
                        style = MaterialTheme.typography.titleMedium,
                        color = BrownInk,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("❧", color = ScrollGold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ChapterLevels(
    levels: List<LevelDefinition>,
    progressMap: Map<Int, CampaignProgress>,
    currentUnlocked: Int,
    onSelectLevel: (LevelDefinition) -> Unit
) {
    val nodeAreaW = 94.dp
    val spacing = 90.dp

    Box(Modifier.fillMaxWidth()) {
        // Hand-drawn ink trail weaving down the node column
        // Match the size established by the level rows without participating in Box measurement.
        // This avoids requesting an infinite height when this item is measured by LazyColumn.
        Canvas(Modifier.matchParentSize()) {
            val cx = (nodeAreaW / 2).toPx()
            val firstY = (spacing / 2).toPx()
            val lastY = ((levels.size - 1) * spacing + spacing / 2).toPx()
            val path = Path()
            val steps = 56
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val y = firstY + t * (lastY - firstY)
                val x = cx + sin(t * Math.PI.toFloat() * 5f) * 7.dp.toPx()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                BrownInk.copy(alpha = 0.30f),
                Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path,
                BrownInk.copy(alpha = 0.72f),
                Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        Column(Modifier.fillMaxWidth()) {
            levels.forEach { level ->
                val isUnlocked = level.levelNumber <= currentUnlocked
                val prog = progressMap[level.levelNumber]
                val size = if (level.isBoss) 86.dp else 62.dp
                Row(
                    Modifier.fillMaxWidth().height(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(nodeAreaW).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        LevelNode(
                            level = level,
                            isUnlocked = isUnlocked,
                            completed = prog != null,
                            size = size,
                            modifier = Modifier.clickable(enabled = isUnlocked, onClick = { onSelectLevel(level) })
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        LevelInfo(level = level, isUnlocked = isUnlocked, progress = prog)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelNode(
    level: LevelDefinition,
    isUnlocked: Boolean,
    completed: Boolean,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    when {
        !isUnlocked -> {
            Box(
                modifier.size(size)
                    .clip(CircleShape)
                    .background(ParchmentDeep.copy(alpha = 0.55f))
                    .border(2.dp, BrownInk.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PadlockIcon(tint = BrownInk.copy(alpha = 0.7f))
            }
        }
        level.isBoss -> BossNode(size = size, modifier = modifier)
        else -> NormalNode(level = level, size = size, completed = completed, modifier = modifier)
    }
}

@Composable
private fun NormalNode(
    level: LevelDefinition,
    size: androidx.compose.ui.unit.Dp,
    completed: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.size(size)
            .clip(CircleShape)
            .background(if (completed) ScrollGold.copy(alpha = 0.22f) else ParchmentLight)
            .border(2.5.dp, if (completed) ScrollGold else Brown.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Glyph(level = level)
    }
}

@Composable
private fun BossNode(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(ParchmentLight, BrownDark)
                )
            )
            .border(3.dp, ScrollGold, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Inner ornate gold ring
        Box(
            Modifier.matchParentSize().padding(5.dp)
                .border(1.5.dp, ScrollGoldLight, RoundedCornerShape(13.dp))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👑", fontSize = 18.sp) // crown
            Text("🦅", fontSize = 34.sp) // crowned eagle
        }
    }
}

@Composable
private fun Glyph(level: LevelDefinition) {
    val spec = normalGlyph(level.levelNumber)
    when (spec) {
        is GlyphSpec.Letter -> {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.verticalGradient(listOf(BrownLight, Brown)))
                    .border(2.dp, BrownDark, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    spec.ch.uppercaseChar().toString(),
                    color = ParchmentLight,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
        }
        is GlyphSpec.Emoji -> Text(spec.emoji, fontSize = 30.sp)
    }
}

@Composable
private fun PadlockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(34.dp)) {
        val w = size.width
        val h = size.height
        val bodyH = h * 0.6f
        val bodyY = h * 0.4f
        val bodyW = w * 0.72f
        val bodyX = (w - bodyW) / 2
        // Shackle
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(bodyX + bodyW * 0.16f, bodyY - h * 0.20f),
            size = Size(bodyW * 0.68f, h * 0.46f),
            style = Stroke(width = w * 0.13f, cap = StrokeCap.Round)
        )
        // Body
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyX, bodyY),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.14f)
        )
        // Keyhole
        drawCircle(color = ParchmentLight, radius = w * 0.075f, center = Offset(w / 2, bodyY + bodyH * 0.40f))
        drawRect(
            color = ParchmentLight,
            topLeft = Offset(w / 2 - w * 0.028f, bodyY + bodyH * 0.40f),
            size = Size(w * 0.056f, bodyH * 0.34f)
        )
    }
}

@Composable
private fun LevelInfo(
    level: LevelDefinition,
    isUnlocked: Boolean,
    progress: CampaignProgress?
) {
    Column(Modifier.padding(end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LV ${level.levelNumber}",
                style = MaterialTheme.typography.labelLarge,
                color = if (level.isBoss) ScrollGold else BrownInk,
                fontWeight = FontWeight.Bold
            )
            if (level.isBoss) {
                Spacer(Modifier.width(6.dp))
                Surface(color = ScrollGold, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "BOSS",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrownDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            levelName(level),
            style = MaterialTheme.typography.titleMedium,
            color = if (level.isBoss) ScrollGold else BrownDark,
            fontWeight = if (level.isBoss) FontWeight.ExtraBold else FontWeight.SemiBold
        )
        if (isUnlocked) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                MetaChip("🎯 ${levelTarget(level)}")
                MetaChip("⚔ ${levelDifficulty(level)}")
                MetaChip("⏱ ${levelTime(level)}")
                MetaChip("🔄 ${levelTurns(level)}")
            }
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                Stars(progress.stars)
            }
        } else {
            Text("🔒 Locked", style = MaterialTheme.typography.bodyMedium, color = BrownInk.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ParchmentDeep.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Brown.copy(alpha = 0.4f))
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = BrownDark,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun Stars(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            Icon(
                Icons.Default.Star,
                null,
                tint = if (i < count) ScrollGold else BrownInk.copy(alpha = 0.2f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Parchment background with faint scattered letters
// ---------------------------------------------------------------------------

@Composable
private fun ParchmentBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Parchment)) {
        // Soft aged gradient
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(ParchmentLight, Parchment, ParchmentDeep)
                )
            )
        )
        // Faint mystical script scattered across the page
        ScatteredLetters()
        content()
    }
}

@Composable
private fun ScatteredLetters() {
    // Deterministic pseudo-random layout so it does not reshuffle on recomposition.
    val letters = remember {
        val rng = java.util.Random(0xC0FFEEL)
        val glyphs = ('A'..'Z').toList().map { it.toString() } +
            listOf("æ", "þ", "§", "¶", "Ω", "✶", "❧", "∆", "Ψ", "✷")
        List(70) {
            BGLetter(
                ch = glyphs[rng.nextInt(glyphs.size)],
                fx = rng.nextFloat(),
                fy = rng.nextFloat(),
                size = 18 + rng.nextInt(26),
                rot = (rng.nextFloat() * 60f - 30f),
                alpha = 0.05f + rng.nextFloat() * 0.06f
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        letters.forEach { l ->
            Text(
                l.ch,
                color = BrownInk.copy(alpha = l.alpha),
                fontSize = l.size.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (l.fx * 360).dp - 40.dp,
                        y = (l.fy * 1600).dp - 40.dp
                    )
                    .rotate(l.rot)
            )
        }
    }
}

private data class BGLetter(
    val ch: String,
    val fx: Float,
    val fy: Float,
    val size: Int,
    val rot: Float,
    val alpha: Float
)

// ---------------------------------------------------------------------------
// Level content helpers
// ---------------------------------------------------------------------------

private sealed class GlyphSpec {
    data class Letter(val ch: Char) : GlyphSpec()
    data class Emoji(val emoji: String) : GlyphSpec()
}

private fun normalGlyph(levelNumber: Int): GlyphSpec = when (levelNumber % 5) {
    1 -> GlyphSpec.Letter('A')      // wooden block A
    2 -> GlyphSpec.Letter('B')      // wooden block B
    3 -> GlyphSpec.Letter('C')      // wooden block C
    else -> GlyphSpec.Emoji(if (levelNumber % 2 == 0) "📖" else "🪶") // dictionary / quill & inkwell
}

private fun levelName(level: LevelDefinition): String = when {
    level.isBoss -> "RIDDLE MASTER"
    level.type == LevelType.PUZZLE_FILL -> PUZZLE_NAMES[level.levelNumber % PUZZLE_NAMES.size]
    else -> SCORE_NAMES[level.levelNumber % SCORE_NAMES.size]
}

private fun levelTarget(level: LevelDefinition): String = when (level.type) {
    LevelType.SCORE_ATTACK -> "Target ${level.targetScore ?: 100}"
    LevelType.PUZZLE_FILL -> "Fill Grid"
}

private fun levelDifficulty(level: LevelDefinition): String = when (level.type) {
    LevelType.SCORE_ATTACK -> (level.aiDifficulty ?: AiDifficulty.MEDIUM).name.lowercase()
        .replaceFirstChar { it.uppercaseChar() }
    LevelType.PUZZLE_FILL -> "Puzzle"
}

private fun levelTime(level: LevelDefinition): String = when (level.type) {
    LevelType.SCORE_ATTACK -> level.turnTimeSeconds?.let { "${it}s" } ?: "∞"
    LevelType.PUZZLE_FILL -> "Par ${level.parTimeSeconds ?: 0}s"
}

private fun levelTurns(level: LevelDefinition): String = when (level.type) {
    LevelType.SCORE_ATTACK -> level.turnLimit?.let { "${it}t" } ?: "∞"
    LevelType.PUZZLE_FILL -> {
        val g = level.puzzleGrid
        if (g.isNullOrEmpty()) "—" else "${g.size}×${g.first().size}"
    }
}

private fun chapterName(index: Int): String = CHAPTER_NAMES[index % CHAPTER_NAMES.size]

private val SCORE_NAMES = listOf(
    "The First Glyph", "Whispering Runes", "Ink & Quill", "The Word Forge", "Ember Lexicon",
    "Silent Scribes", "The Rune Path", "Quill & Inkwell", "The Lost Verse", "Tongue of Ancients",
    "The Grammar Gate", "Scroll Keeper", "Lexicon Lane", "The Cipher Stone", "Verbal Vigil",
    "The Scriptorium", "Mystic Alphabet", "The Glyph Garden", "Wordwright", "The Antique Tome"
)

private val PUZZLE_NAMES = listOf(
    "Riddle of Sand", "The Sealed Symbol", "Cipher of Dust", "The Hidden Rune", "Parchment Puzzle",
    "The Locked Lexicon", "Runic Maze", "The Frozen Word", "Crypt of Letters", "The Solved Scroll",
    "Enigma Ink", "The Vanished Verse", "Puzzle of Ages", "The Sealed Script", "Rune Reckoning",
    "The Quiet Quill", "Labyrinth of Letters", "The Bound Book", "Mystery Manuscript", "The Wax Seal"
)

private val CHAPTER_NAMES = listOf(
    "THE SCROLL OF BEGINNINGS", "WHISPERING RUNES", "THE EMBER CODEX", "SHADOWS OF THE SCRIPT",
    "THE CRIMSON QUILL", "TOMB OF FORGOTTEN WORDS", "THE GILDED GRAMMAR", "SONG OF THE SCRIBES",
    "THE OBSIDIAN ALPHABET", "RIDDLES OF THE RUIN", "THE MIDNIGHT MANUSCRIPT", "TRIALS OF THE TONGUE",
    "THE BRONZE BOOK", "ECHOES OF THE ANCIENTS", "THE SECRET SYLLABARY", "CURSE OF THE CIPHER",
    "THE AMBER ARCHIVE", "PROPHECY OF THE PEN", "THE HOLLOW HYMNAL", "MAZE OF THE MAGES",
    "THE LAST LEXICON", "CROWN OF THE CIPHER", "THE ETERNAL ETYMOLOGY", "REIGN OF THE RIDDLE MASTER"
)
