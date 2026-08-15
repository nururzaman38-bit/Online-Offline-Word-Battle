package com.wordbattle.com.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.GoldLight
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Mist
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.PurpleLight
import com.wordbattle.com.ui.theme.Teal

private data class BattleMode(
    val players: Int,
    @androidx.annotation.StringRes val title: Int,
    @androidx.annotation.StringRes val subtitle: Int,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun HomeScreen(
    profile: UserProfile?,
    selectedPlayers: Int,
    isOffline: Boolean,
    onSelectMode: (Int) -> Unit,
    onPlay: () -> Unit,
    onJoinRoom: () -> Unit,
    onCreateRoom: () -> Unit,
    onCampaignClick: () -> Unit = {}
) {
    val modes = listOf(
        BattleMode(1, R.string.mode_computer_title, R.string.mode_computer_subtitle, Icons.Default.SmartToy, Teal),
        BattleMode(2, R.string.mode_two_title, R.string.mode_two_subtitle, Icons.Default.Person, Blue),
        BattleMode(3, R.string.mode_three_title, R.string.mode_three_subtitle, Icons.Default.Groups, PurpleLight),
        BattleMode(4, R.string.mode_four_title, R.string.mode_four_subtitle, Icons.Default.SportsEsports, Gold)
    )
    val selectedMode = modes.firstOrNull { it.players == selectedPlayers } ?: modes.first()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroCard(profile = profile, isOffline = isOffline) }
        item { StatsStrip(profile) }
        item { CampaignCard(profile = profile, onClick = onCampaignClick) }
        item {
            SectionHeader(
                title = stringResource(R.string.home_choose_battle),
                hint = stringResource(R.string.home_choose_battle_hint)
            )
        }
        items(modes.chunked(2)) { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowModes.forEach { mode ->
                    ModeCard(
                        mode = mode,
                        selected = selectedPlayers == mode.players,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectMode(mode.players) }
                    )
                }
            }
        }
        item {
            GoldButton(
                text = stringResource(R.string.home_play_mode, stringResource(selectedMode.title)).uppercase(),
                onClick = onPlay,
                leadingIcon = Icons.Default.PlayArrow
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    stringResource(R.string.home_join_room),
                    stringResource(R.string.home_join_room_hint),
                    Icons.Default.VpnKey, Blue, Modifier.weight(1f), onJoinRoom
                )
                QuickAction(
                    stringResource(R.string.home_create_room),
                    stringResource(R.string.home_create_room_hint),
                    Icons.Default.AddCircle, Teal, Modifier.weight(1f), onCreateRoom
                )
            }
        }
        item { ScoringTipCard() }
    }
}

@Composable
private fun CampaignCard(profile: UserProfile?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Purple.copy(alpha = 0.15f)) {
                Icon(Icons.Default.Flag, null, tint = Purple, modifier = Modifier.padding(10.dp).size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Campaign", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    "Level ${minOf(profile?.campaignLevel ?: 1, 500)}/500 • ⭐ ${profile?.campaignStarsTotal ?: 0}",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Gold) {
                Text("PLAY", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Animated welcome banner: greeting, level progress and a slowly shimmering gradient. */
@Composable
private fun HeroCard(profile: UserProfile?, isOffline: Boolean) {
    val transition = rememberInfiniteTransition(label = "hero")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Reverse),
        label = "hero-shimmer"
    )
    val trophyTilt by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hero-trophy"
    )

    val played = profile?.gamesPlayed ?: 0
    val level = profile?.level ?: 1
    // Ten battles per level: simple, transparent and works offline.
    val progress = ((played % 10) / 10f).coerceIn(0.06f, 1f)

    Surface(shape = RoundedCornerShape(24.dp), color = Purple, shadowElevation = 12.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF241242), Purple, Color(0xFF3B1D6B)),
                        start = Offset(shimmer * 420f, 0f),
                        end = Offset(520f + shimmer * 420f, 460f)
                    )
                )
        ) {
            Text(
                "🏆",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .rotate(trophyTilt),
                style = MaterialTheme.typography.displayLarge
            )
            Column(Modifier.padding(18.dp).align(Alignment.CenterStart).fillMaxWidth(.68f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Gold) {
                        Text(
                            stringResource(R.string.home_weekly_quest),
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Ink,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (isOffline) {
                        Spacer(Modifier.size(6.dp))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = .16f)) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WifiOff, null, Modifier.size(12.dp), tint = Color.White)
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    stringResource(R.string.home_offline_badge),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    profile?.displayName?.let { stringResource(R.string.home_greeting, it) }
                        ?: stringResource(R.string.home_hero_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.home_hero_subtitle),
                    color = Color.White.copy(alpha = .72f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                LevelBar(level = level, progress = progress)
            }
        }
    }
}

/** Slim XP bar under the greeting. */
@Composable
private fun LevelBar(level: Int, progress: Float) {
    val animated by animateFloatAsState(progress, tween(700), label = "xp")
    Column {
        Text(
            stringResource(R.string.home_level, level),
            color = GoldLight,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .18f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .background(Brush.horizontalGradient(listOf(GoldLight, Gold)), CircleShape)
            )
        }
    }
}

/** Three compact counters: wins, battles played and this week's score. */
@Composable
private fun StatsStrip(profile: UserProfile?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatChip(
            value = (profile?.wins ?: 0).toString(),
            label = stringResource(R.string.home_stat_wins),
            icon = Icons.Default.EmojiEvents,
            tint = Gold,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            value = (profile?.gamesPlayed ?: 0).toString(),
            label = stringResource(R.string.home_stat_battles),
            icon = Icons.Default.SportsEsports,
            tint = Teal,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            value = (profile?.weeklyScore ?: 0).toString(),
            label = stringResource(R.string.home_stat_weekly),
            icon = Icons.Default.CheckCircle,
            tint = Blue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = .10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .14f))
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.height(3.dp))
            Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                label,
                color = Color.White.copy(alpha = .62f),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    Column {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(hint, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Battle mode tile. The selected tile lifts, tints its icon and shows a check badge. */
@Composable
private fun ModeCard(
    mode: BattleMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1.03f else 1f, tween(220), label = "mode-scale")
    val iconBackground by animateColorAsState(
        if (selected) mode.color else mode.color.copy(alpha = .13f),
        tween(220),
        label = "mode-icon-bg"
    )
    val badgeSize by animateDpAsState(if (selected) 20.dp else 0.dp, tween(220), label = "mode-badge")

    Box(modifier.scale(scale)) {
        WhiteCard(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.12f),
            selected = selected,
            selectedColor = mode.color,
            onClick = onClick
        ) {
            Surface(shape = CircleShape, color = iconBackground) {
                Icon(
                    mode.icon,
                    null,
                    tint = if (selected) Color.White else mode.color,
                    modifier = Modifier.padding(10.dp).size(24.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(mode.title),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(mode.subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badgeSize > 0.dp) {
            Surface(
                shape = CircleShape,
                color = mode.color,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(badgeSize)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    stringResource(R.string.home_mode_selected),
                    tint = Color.White,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    WhiteCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = color.copy(alpha = .13f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            Column {
                Text(title, color = Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    subtitle,
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Explains the scoring rule right on the home screen so new players are never surprised. */
@Composable
private fun ScoringTipCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Mist.copy(alpha = .14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = Gold.copy(alpha = .22f)) {
                Icon(Icons.Default.Lightbulb, null, tint = GoldLight, modifier = Modifier.padding(7.dp).size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    stringResource(R.string.home_tip_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.home_tip_body),
                    color = Color.White.copy(alpha = .74f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
