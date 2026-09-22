package com.peetracker.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedIndex = if (uiState.selectedPeriod == LeaderboardPeriod.TODAY) 0 else 1

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedIndex) {
                Tab(
                    selected = selectedIndex == 0,
                    onClick = { viewModel.onPeriodSelected(LeaderboardPeriod.TODAY) },
                    text = { Text("Today") }
                )
                Tab(
                    selected = selectedIndex == 1,
                    onClick = { viewModel.onPeriodSelected(LeaderboardPeriod.THIS_WEEK) },
                    text = { Text("This Week") }
                )
            }

            val rows = uiState.rows
            if (rows.isEmpty()) {
                EmptyLeaderboard(period = uiState.selectedPeriod)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.uid }) { row ->
                        LeaderboardRowCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLeaderboard(period: LeaderboardPeriod) {
    val message = if (period == LeaderboardPeriod.TODAY) {
        "No trips logged yet today — go take the lead!"
    } else {
        "No trips logged yet this week — be the first on the board!"
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun LeaderboardRowCard(row: LeaderboardRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (row.isCurrentUser) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                if (row.rank == 1) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Current leader",
                        tint = Color(0xFFFFC107)
                    )
                } else {
                    Text(text = "${row.rank}", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.entry.displayName.ifBlank { "Anonymous" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${row.entry.count} trips · ${formatDuration(row.entry.totalDurationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm %02ds".format(minutes, seconds)
}
