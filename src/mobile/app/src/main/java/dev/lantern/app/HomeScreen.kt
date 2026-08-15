package dev.lantern.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lantern.app.brand.DisplayFont
import dev.lantern.app.brand.EvidenceMono
import dev.lantern.app.brand.LocalBrandColors
import dev.lantern.app.brand.LocalBrandTokens
import dev.lantern.app.brand.UiFont
import dev.lantern.app.brand.colorOf

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val colors = LocalBrandColors.current
    val tokens = LocalBrandTokens.current
    val pane = colors.colorOf(state.pane)
    val agentLabel = when {
        state.status == null -> "unknown"
        state.stale -> "stale"
        else -> state.status!!.state.name.lowercase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.midnight)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "lantern",
            color = colors.bone,
            fontFamily = DisplayFont,
            fontSize = 28.sp,
        )
        Text(
            text = tokens.tagline,
            color = colors.textSecondary,
            fontFamily = UiFont,
            fontSize = 14.sp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardFill, RoundedCornerShape(14.dp))
                .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(pane, RoundedCornerShape(9.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("agent $agentLabel", color = colors.bone, fontFamily = UiFont, fontSize = 16.sp)
                Text(
                    text = "lastSeenUtc ${state.lastSeenUtc ?: "none"}",
                    color = colors.candle,
                    fontFamily = EvidenceMono,
                    fontSize = 13.sp,
                )
                state.status?.currentTask?.title?.let {
                    Text(it, color = colors.textBody, fontFamily = UiFont, fontSize = 13.sp)
                }
            }
        }

        OutlinedTextField(
            value = state.draft,
            onValueChange = viewModel::onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("send message", color = colors.textLabel, fontFamily = UiFont) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.bone,
                unfocusedTextColor = colors.bone,
                focusedBorderColor = colors.candle,
                unfocusedBorderColor = colors.hairline,
                cursorColor = colors.candle,
            ),
        )
        Button(
            onClick = viewModel::send,
            enabled = !state.sending,
            colors = ButtonDefaults.buttonColors(containerColor = colors.steeple, contentColor = colors.bone),
        ) {
            Text(if (state.sending) "sending" else "send", fontFamily = UiFont)
        }
        state.message?.let {
            Text(it, color = colors.candle, fontFamily = EvidenceMono, fontSize = 12.sp)
        }
        state.lastResponse?.let { response ->
            Text(
                text = "response ${response.status.name.lowercase()} ${response.commandId}",
                color = colors.textBody,
                fontFamily = EvidenceMono,
                fontSize = 12.sp,
            )
        }
    }
}
