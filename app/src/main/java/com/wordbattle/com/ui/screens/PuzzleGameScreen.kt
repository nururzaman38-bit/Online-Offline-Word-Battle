package com.wordbattle.com.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.game.PuzzleEngine
import com.wordbattle.com.data.model.CellStyle
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.ui.theme.*

@Composable
fun PuzzleGameScreen(
    level: LevelDefinition,
    puzzleState: PuzzleEngine.PuzzleState,
    elapsedSeconds: Int,
    livesCurrent: Int,
    livesMax: Int,
    selectedLetter: Char?,
    wrongCells: Set<Pair<Int, Int>>,
    onSelectLetter: (Char) -> Unit,
    onPlaceLetter: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Top bar: timer + lives
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(.12f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}", color = Color.White)
                    level.parTimeSeconds?.let {
                        Spacer(Modifier.width(6.dp))
                        Text("/ ${it}s", color = Color.White.copy(.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row {
                repeat(livesMax) { idx ->
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        tint = if (idx < livesCurrent) Color.Red else Color.White.copy(.2f),
                        modifier = Modifier.size(20.dp).padding(2.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onBack) { Text("Exit", color = Color.White) }
        }

        Spacer(Modifier.height(12.dp))

        Text("Puzzle Level ${level.levelNumber}", color = Color.White, style = MaterialTheme.typography.titleLarge)
        if (level.isBoss) {
            Surface(color = Gold, shape = CircleShape) {
                Text("BOSS", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Ink)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Board
        val rows = puzzleState.rows
        val cols = puzzleState.cols
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(.08f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until cols) {
                        val def = puzzleState.defs[r][c]
                        when (def.style) {
                            CellStyle.BLOCKED -> Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(Color.Transparent)
                            )
                            CellStyle.GIVEN -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Purple)
                                        .border(1.dp, Color.White.copy(.2f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(def.letter?.toString() ?: "", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            CellStyle.BLANK -> {
                                val filled = puzzleState.charAt(r, c)
                                val isWrong = Pair(r, c) in wrongCells
                                val bg by animateColorAsState(if (isWrong) Color.Red.copy(alpha = .6f) else Color.White, label = "bg")
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, if (isWrong) Color.Red else Ink.copy(.2f), RoundedCornerShape(8.dp))
                                        .clickable { onPlaceLetter(r, c) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(filled?.toString() ?: "", color = Ink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Letter tray – reuse existing LetterTile concept simplified
        Text("Pick a letter", color = Color.White.copy(.7f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        val letters = ('A'..'Z').toList()
        // Simple grid of letters
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            letters.chunked(7).forEach { rowLetters ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowLetters.forEach { ch ->
                        val selected = selectedLetter == ch
                        Surface(
                            shape = CircleShape,
                            color = if (selected) Gold else Color.White,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onSelectLetter(ch) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(ch.toString(), fontWeight = FontWeight.Bold, color = Ink)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Fill all blanks with valid words. Wrong line costs 1 life.",
            color = Color.White.copy(.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun LivesBottomSheet(
    livesCurrent: Int,
    livesMax: Int,
    regenMinutes: Long,
    coins: Int,
    friends: List<FriendProfile>,
    onBuyLife: () -> Unit,
    onRequestLife: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Out of Lives", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("You have $livesCurrent/$livesMax lives. Next life in $regenMinutes min.", color = Muted)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBuyLife, enabled = coins >= 30, modifier = Modifier.fillMaxWidth()) {
                Text("Buy +1 life for 30 coins (You have $coins)")
            }
            Spacer(Modifier.height(12.dp))
            Text("Ask a friend for life", style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.height(6.dp))
            if (friends.isEmpty()) {
                Text(
                    "No friends yet — add friends to ask for lives.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                friends.take(5).forEach { friend ->
                    OutlinedButton(
                        onClick = { onRequestLife(friend.profile.uid) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(friend.profile.displayName)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}
