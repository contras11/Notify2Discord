package com.notify2discord.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notify2discord.app.R
import com.notify2discord.app.data.AppInfo
import com.notify2discord.app.data.SettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    apps: List<AppInfo>,
    onSaveWebhook: (String) -> Unit,
    onToggleForwarding: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTestSend: () -> Unit,
    onToggleExclude: (String, Boolean) -> Unit
) {
    // TextFieldValue は Saveable に非対応なので、保存可能な String で保持する
    var webhookText by rememberSaveable(state.webhookUrl) {
        mutableStateOf(state.webhookUrl)
    }

    LaunchedEffect(state.webhookUrl) {
        if (webhookText != state.webhookUrl) {
            webhookText = state.webhookUrl
        }
    }

    val isWebhookValid = remember(webhookText) {
        webhookText.startsWith("https://discord.com/api/webhooks/") ||
            webhookText.startsWith("https://discordapp.com/api/webhooks/")
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.settings_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.notification_access_help),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpenNotificationAccess) {
                Text(text = stringResource(id = R.string.open_notification_access))
            }

            OutlinedTextField(
                value = webhookText,
                onValueChange = { webhookText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.webhook_label)) },
                isError = webhookText.isNotBlank() && !isWebhookValid
            )

            if (webhookText.isBlank()) {
                Text(
                    text = stringResource(id = R.string.webhook_empty_help),
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!isWebhookValid) {
                Text(
                    text = stringResource(id = R.string.webhook_invalid_help),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onSaveWebhook(webhookText) }) {
                    Text(text = stringResource(id = R.string.webhook_save))
                }
                Button(onClick = onTestSend, enabled = isWebhookValid) {
                    Text(text = stringResource(id = R.string.test_send))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.forwarding_toggle))
                Switch(
                    checked = state.forwardingEnabled,
                    onCheckedChange = onToggleForwarding
                )
            }

            Divider()

            Text(text = stringResource(id = R.string.excluded_section))

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val excluded = state.excludedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleExclude(app.packageName, !excluded) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = excluded,
                            onCheckedChange = { onToggleExclude(app.packageName, it) }
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Column {
                            Text(text = app.label)
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
