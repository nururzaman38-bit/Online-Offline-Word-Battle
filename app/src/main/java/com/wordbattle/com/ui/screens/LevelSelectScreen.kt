package com.wordbattle.com.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.CampaignProgress
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import com.wordbattle.com.ui.theme.*

@Composable
fun LevelSelectScreen(
    levels: List<LevelDefinition>,
    progress: List<CampaignProgress>,
    currentUnlocked: Int,
    onSelectLevel: (LevelDefinition) -> Unit,
    onBack: () -> Unit
) {
    val progressMap = progress.associateBy { it.levelNumber }
    val chapters = levels.chunked(5) // Per 5 level Chapter grouping

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Campaign", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back", color = Color.White) }
            }
        }
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

@Composable
private fun ChapterSection(
    chapterIndex: Int,
    levels: List<LevelDefinition>,
    progressMap: Map<Int, CampaignProgress>,
    currentUnlocked: Int,
    onSelectLevel: (LevelDefinition) -> Unit
) {
    val chapterColors = listOf(Teal, Blue, Purple, PurpleLight, Gold, Color(0xFF4CAF50), Color(0xFFE91E63))
    val color = chapterColors[chapterIndex % chapterColors.size]

    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = color) {
                    Text("C${chapterIndex + 1}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("Chapter ${chapterIndex + 1}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${levels.first().levelNumber}-${levels.last().levelNumber}", color = Color.White.copy(.6f))
            }
            Spacer(Modifier.height(12.dp))
            // Winding path – simple row with offset for visual path effect
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                levels.forEachIndexed { idx, level ->
                    val isUnlocked = level.levelNumber <= currentUnlocked
                    val prog = progressMap[level.levelNumber]
                    val offset = if (idx % 2 == 0) 0.dp else 32.dp
                    LevelNode(
                        level = level,
                        isUnlocked = isUnlocked,
                        progress = prog,
                        chapterColor = color,
                        modifier = Modifier.padding(start = offset).fillMaxWidth(),
                        onClick = { if (isUnlocked) onSelectLevel(level) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelNode(
    level: LevelDefinition,
    isUnlocked: Boolean,
    progress: CampaignProgress?,
    chapterColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isBoss = level.isBoss
    val size = if (isBoss) 78.dp else 64.dp
    val bg = when {
        !isUnlocked -> Color.White.copy(alpha = 0.15f)
        progress != null -> chapterColor
        else -> Color.White
    }
    val borderColor = if (isBoss) Gold else chapterColor.copy(alpha = 0.5f)

    Row(modifier = modifier.clickable(enabled = isUnlocked, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(if (isBoss) RoundedCornerShape(16.dp) else CircleShape)
                .background(bg)
                .border(2.dp, borderColor, if (isBoss) RoundedCornerShape(16.dp) else CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!isUnlocked) {
                Icon(Icons.Default.Lock, null, tint = Color.White.copy(.7f))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Icon(
                            imageVector = if (level.type == LevelType.PUZZLE_FILL) Icons.Default.Timer else Icons.Default.Flag,
                            contentDescription = null,
                            tint = if (progress != null) Color.White else Ink,
                            modifier = Modifier.size(18.dp)
                        )
                        if (isBoss) {
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Default.Whatshot, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("${level.levelNumber}", fontWeight = FontWeight.Bold, color = if (progress != null) Color.White else Ink)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Level ${level.levelNumber}",
                    color = Color.White,
                    fontWeight = if (isBoss) FontWeight.Bold else FontWeight.Medium
                )
                if (isBoss) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = Gold, shape = CircleShape) {
                        Text("BOSS", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Ink)
                    }
                }
            }
            when (level.type) {
                LevelType.SCORE_ATTACK -> Text(
                    "Target ${level.targetScore} • ${level.aiDifficulty} • ${level.turnTimeSeconds ?: "∞"}s • ${level.turnLimit} turns",
                    color = Color.White.copy(.6f),
                    style = MaterialTheme.typography.bodySmall
                )
                LevelType.PUZZLE_FILL -> Text(
                    "Puzzle ${level.puzzleGrid?.size ?: 0}x${level.puzzleGrid?.firstOrNull()?.size ?: 0} • Par ${level.parTimeSeconds}s",
                    color = Color.White.copy(.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (progress != null) {
                Row {
                    repeat(progress.stars) { Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(14.dp)) }
                    repeat(3 - progress.stars) { Icon(Icons.Default.Star, null, tint = Color.White.copy(.2f), modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
}
