package com.wordbattle.com.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.BattleToast
import com.wordbattle.com.ui.ToastKind
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.CardWhite
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.GoldLight
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Mist
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.PurpleDark
import com.wordbattle.com.ui.theme.PurpleLight
import com.wordbattle.com.ui.theme.Red
import com.wordbattle.com.ui.theme.Teal
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PurpleDark, Purple, PurpleLight)))
            .systemBarsPadding(),
        content = content
    )
}

@Composable
fun LetterTile(
    letter: Char,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    selected: Boolean = false,
    rotationSeed: Int = letter.code,
    onClick: (() -> Unit)? = null
) {
    val rotation = ((rotationSeed * 17) % 7 - 3).toFloat()
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation)
            .background(
                if (selected) Brush.linearGradient(listOf(GoldLight, Gold))
                else Brush.linearGradient(listOf(Color.White, Color(0xFFF4EAFD))),
                RoundedCornerShape(size * 0.22f)
            )
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else Color.White.copy(alpha = .7f)),
                RoundedCornerShape(size * 0.22f)
            )
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter.uppercaseChar().toString(),
            style = MaterialTheme.typography.titleLarge,
            fontSize = (size.value * .53f).sp,
            color = Ink,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedColor: Color = Gold,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Card(
        modifier = modifier.then(clickableModifier),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, if (selected) selectedColor else Color.Transparent),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp, pressedElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, label = "button-scale")
    Box(modifier = modifier.scale(scale).padding(bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 5.dp)
                .background(if (enabled) Color(0xFFCC8E00) else Muted, CircleShape)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(GoldLight, Gold))
                    else Brush.horizontalGradient(listOf(Muted, Muted.copy(alpha = .75f))),
                    CircleShape
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(21.dp), tint = if (enabled) Ink else Color.White)
                Spacer(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 18.sp, color = if (enabled) Ink else Color.White)
        }
    }
}

@Composable
fun PlayerAvatar(
    profile: UserProfile?,
    size: Dp = 46.dp,
    borderWidth: Dp = 3.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = PurpleLight,
        border = BorderStroke(borderWidth, Gold)
    ) {
        if (!profile?.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = profile?.photoUrl,
                contentDescription = profile?.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(profile?.displayName?.firstOrNull()?.uppercase() ?: "W", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun TopPlayerBar(profile: UserProfile?, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            PlayerAvatar(profile)
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(19.dp),
                shape = CircleShape,
                color = Gold,
                border = BorderStroke(1.dp, Color.White)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("${profile?.level ?: 1}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ink) }
            }
        }
        Spacer(Modifier.weight(1f))
        CurrencyPill(Icons.Default.Star, profile?.coins ?: 0, Gold)
        Spacer(Modifier.size(8.dp))
        CurrencyPill(Icons.Default.Diamond, profile?.gems ?: 0, Blue)
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
    }
}

@Composable
private fun CurrencyPill(icon: ImageVector, amount: Int, color: Color) {
    Surface(shape = CircleShape, color = PurpleDark.copy(alpha = .65f), border = BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text(amount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PlayerScoreCard(player: Player, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (active) Gold else CardWhite,
        border = BorderStroke(2.dp, if (active) GoldLight else Color.Transparent),
        shadowElevation = if (active) 8.dp else 3.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(player.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = Ink)
            Text(player.score.toString(), style = MaterialTheme.typography.titleLarge, color = if (active) PurpleDark else Purple)
        }
    }
}

@Composable
fun BattleToastOverlay(toast: BattleToast?, modifier: Modifier = Modifier) {
    if (toast == null) return
    val color = when (toast.kind) {
        ToastKind.DEFAULT -> Ink
        ToastKind.WARNING -> Red
        ToastKind.SUCCESS -> Teal
    }
    Surface(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 18.dp),
        shape = CircleShape,
        color = color,
        shadowElevation = 12.dp
    ) {
        Text(
            toast.text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = Mist) {
            Icon(icon, null, tint = PurpleLight, modifier = Modifier.padding(18.dp).size(34.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}
