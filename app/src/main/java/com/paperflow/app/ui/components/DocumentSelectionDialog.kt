package com.paperflow.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperflow.app.data.model.DocumentEntity
import com.paperflow.app.ui.home.DocumentTransferMode

@Composable
fun DocumentSelectionDialog(
    documents: List<DocumentEntity>,
    mode: DocumentTransferMode,
    onDismiss: () -> Unit,
    onConfirm: (targetDocumentId: String) -> Unit,
) {
    var selectedTargetId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Destination Document")
        },
        text = {
            Column {
                Text(
                    text = "Choose a document to ${mode.label.lowercase()} to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(documents) { doc ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetId = doc.id }
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = doc.title,
                                fontWeight = if (selectedTargetId == doc.id) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (selectedTargetId == doc.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${doc.pageCount} pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedTargetId != null) {
                        onConfirm(selectedTargetId!!)
                    }
                },
                enabled = selectedTargetId != null
            ) {
                Text(mode.label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
