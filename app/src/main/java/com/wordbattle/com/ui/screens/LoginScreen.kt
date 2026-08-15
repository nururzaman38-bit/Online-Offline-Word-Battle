package com.wordbattle.com.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordbattle.com.R
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.LetterTile
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple

/**
 * Sign-in entry point.
 *
 * Google is the primary action; email is a secondary, collapsible section. There is no separate
 * "register" mode any more — [onEmail] signs in and transparently creates the account when it does
 * not exist yet.
 */
@Composable
fun LoginScreen(
    busy: Boolean,
    onGoogle: () -> Unit,
    onEmail: (String, String) -> Unit,
    onOffline: () -> Unit
) {
    var showEmail by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmitEmail = email.contains('@') && password.length >= 6 && !busy
    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stringResource(R.string.brand_word).forEachIndexed { i, c -> LetterTile(c, size = 36.dp, rotationSeed = i) }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.brand_battle), color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.brand_tagline), color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(32.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 14.dp) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.login_ready), style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Text(
                        stringResource(R.string.login_subtitle),
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onGoogle,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                        border = BorderStroke(1.dp, Color(0xFFD8D3DE))
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Purple)
                        } else {
                            Surface(shape = CircleShape, color = Color(0xFFF4F4F4)) {
                                Text("G", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color(0xFF4285F4), fontSize = 18.sp)
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.login_google), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.login_or),
                            Modifier.padding(horizontal = 10.dp),
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    TextButton(onClick = { showEmail = !showEmail }) {
                        Icon(Icons.Default.Email, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(7.dp))
                        Text(stringResource(if (showEmail) R.string.login_email_hide else R.string.login_email_show))
                    }
                    AnimatedVisibility(visible = showEmail) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.login_email_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.login_password_label)) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onEmail(email, password) },
                                enabled = canSubmitEmail,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Purple)
                            ) { Text(stringResource(R.string.login_email_continue)) }
                            Spacer(Modifier.height(6.dp))
                            // Sign-up happens automatically, so no Login/Register switch is needed.
                            Text(
                                stringResource(R.string.login_email_note),
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOffline, enabled = !busy) {
                Text(stringResource(R.string.login_play_offline), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                stringResource(R.string.login_offline_note),
                color = Color.White.copy(alpha = .65f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
