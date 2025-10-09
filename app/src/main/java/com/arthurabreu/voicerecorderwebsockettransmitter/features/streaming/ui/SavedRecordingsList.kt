package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun SavedRecordingsList(
    items: List<File>,
    onPlay: (File) -> Unit,
    onDelete: (File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F0F))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Saved recordings", style = MaterialTheme.typography.titleMedium, color = Color.White)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.absolutePath }) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, color = Color(0xFFEEEEEE))
                        Text("${file.length()/1024} KB", color = Color(0xFFAAAAAA))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onPlay(file) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                            Text("Play", color = Color.White)
                        }
                        Button(onClick = { onDelete(file) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))) {
                            Text("Delete", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
