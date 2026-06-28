package com.synocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.synocam.data.NasConfig

@Composable
fun SetupScreen(
    initial: NasConfig?,
    loading: Boolean,
    error: String?,
    onConnect: (NasConfig) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initial?.host ?: "") }
    var port by rememberSaveable { mutableStateOf((initial?.port ?: 5000).toString()) }
    var useHttps by rememberSaveable { mutableStateOf(initial?.useHttps ?: false) }
    var account by rememberSaveable { mutableStateOf(initial?.account ?: "") }
    var password by rememberSaveable { mutableStateOf(initial?.password ?: "") }
    var columns by rememberSaveable { mutableStateOf(initial?.gridColumns ?: 2) }

    val canSubmit = host.isNotBlank() && account.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("SynoCam", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Connect to Synology Surveillance Station",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))

        val fieldWidth = Modifier.widthIn(max = 520.dp).fillMaxWidth()

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("NAS address (IP or hostname)") },
            singleLine = true,
            modifier = fieldWidth,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp),
            )
            Spacer(Modifier.width(24.dp))
            Text("HTTPS", color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = useHttps,
                onCheckedChange = {
                    useHttps = it
                    if (it && port == "5000") port = "5001"
                    if (!it && port == "5001") port = "5000"
                },
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text("Account") },
            singleLine = true,
            modifier = fieldWidth,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = fieldWidth,
        )
        Spacer(Modifier.height(20.dp))

        Text("Grid size (cameras per row)", color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 2, 3, 4).forEach { n ->
                FilterChip(
                    selected = columns == n,
                    onClick = { columns = n },
                    label = { Text(n.toString()) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                onConnect(
                    NasConfig(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 5000,
                        useHttps = useHttps,
                        account = account.trim(),
                        password = password,
                        gridColumns = columns,
                    ),
                )
            },
            enabled = canSubmit && !loading,
        ) {
            Text(if (loading) "Connecting…" else "Connect")
        }

        if (loading) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        if (error != null) {
            Spacer(Modifier.height(20.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
