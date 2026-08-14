package com.wordbattle.com.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.Cell
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.LetterTile
import com.wordbattle.com.ui.components.PlayerScoreCard
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Mist
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.PurpleDark
import com.wordbattle.com.ui.theme.Teal

@Composable
fun GameScreen(
    game: GameState?,
    selectedLetter: Char?,
    ownedPlayerIds: Set<String>,
    turnSeconds: Int,
    onSelectLetter: (Char) -> Unit,
    onCell: (Int, Int) -> Unit,
    onSkip: () -> Unit,
    onExit: () -> Unit
) {
    GradientBackground {
        if (game == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading battle…", color = Color.White) }
            return@GradientBackground
        }
        val current = game.players.firstOrNull { it.id == game.currentTurnPlayerId }
        val canPlay = game.currentTurnPlayerId in ownedPlayerIds
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Leave game", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("WORD BATTLE", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(if (canPlay) "Your move, ${current?.name}" else "Waiting for ${current?.name}", color = if (canPlay) Gold else Color.White.copy(alpha = .72f))
                }
                Surface(shape = CircleShape, color = if (turnSeconds <= 10) Color(0xFFFF4E4E) else PurpleDark.copy(alpha = .7f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("${turnSeconds}s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(game.players, key = { it.id }) { player ->
                    PlayerScoreCard(player, active = player.id == game.currentTurnPlayerId, modifier = Modifier.width(110.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFE9E1F0),
                border = BorderStroke(3.dp, Color.White),
                shadowElevation = 9.dp
            ) {
                val horizontal = rememberScrollState(initial = ((game.board.cols * 32 - 330) / 2).coerceAtLeast(0))
                val vertical = rememberScrollState(initial = ((game.board.rows * 32 - 330) / 2).coerceAtLeast(0))
                Box(
                    Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(vertical).padding(5.dp)
                ) {
                    Column(Modifier.width((game.board.cols * 32).dp)) {
                        game.board.cells.forEach { row ->
                            Row {
                                row.forEach { cell -> BoardCell(cell, enabled = canPlay && selectedLetter != null) { onCell(cell.row, cell.col) } }
                            }
                        }
                    }
                }
            }
            Column(Modifier.background(PurpleDark.copy(alpha = .82f)).padding(top = 9.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            !canPlay -> "WAITING"
                            selectedLetter == null -> "PICK A LETTER"
                            else -> "PLACE $selectedLetter ON THE BOARD"
                        },
                        color = if (canPlay) Gold else Muted,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSkip, enabled = canPlay) { Text("Skip", color = if (canPlay) Color.White else Muted) }
                }
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(('A'..'Z').toList()) { letter ->
                        LetterTile(letter, size = 41.dp, selected = selectedLetter == letter, onClick = if (canPlay) ({ onSelectLetter(letter) }) else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(cell: Cell, enabled: Boolean, onClick: () -> Unit) {
    val special = (cell.row == 7 && cell.col == 7) || ((cell.row + cell.col) % 11 == 0)
    val background = when {
        cell.letter != null -> Color.White
        special -> Gold.copy(alpha = .45f)
        (cell.row + cell.col) % 2 == 0 -> Color(0xFFF8F5FA)
        else -> Color(0xFFEDE7F2)
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .padding(1.dp)
            .background(background, RoundedCornerShape(5.dp))
            .then(if (cell.letter == null && enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (cell.letter != null) {
            Text(cell.letter.toString(), color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        } else if (special) {
            Text("★", color = Purple.copy(alpha = .45f), style = MaterialTheme.typography.labelMedium)
        }
    }
}
