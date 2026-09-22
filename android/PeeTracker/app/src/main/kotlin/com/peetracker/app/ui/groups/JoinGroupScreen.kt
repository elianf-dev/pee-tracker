package com.peetracker.app.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun JoinGroupScreen(
    initialCode: String?,
    onJoined: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var code by rememberSaveable { mutableStateOf(initialCode.orEmpty().uppercase()) }

    LaunchedEffect(uiState.joinedGroup) {
        if (uiState.joinedGroup != null) onJoined()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Enter invite code", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Code, e.g. PEE-4X9KQ7MT") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.joinGroup(code.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && code.trim().isNotEmpty()
            ) {
                Text("Join")
            }

            if (uiState.isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
