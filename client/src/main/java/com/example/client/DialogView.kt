package com.example.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ConfigDialog(
    ip: MutableState<String>,
    port: MutableState<String>,
    shouldShowDialog: MutableState<Boolean>
) {
    if (shouldShowDialog.value) {
        val newIp = remember { mutableStateOf(ip.value) }
        val newPort = remember { mutableStateOf(port.value) }
        Dialog(
            onDismissRequest = { shouldShowDialog.value = false }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(15.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OutlinedTextField(
                        value = newIp.value,
                        label = { Text(text = "ip") },
                        onValueChange = {
                            newIp.value = it
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    OutlinedTextField(
                        value = newPort.value,
                        label = { Text(text = "port") },
                        onValueChange = {
                            newPort.value = it
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        shape = RoundedCornerShape(15.dp),
                        onClick = {
                            port.value = newPort.value
                            ip.value = newIp.value
                            shouldShowDialog.value = false
                        }
                    ) {
                        Text(text = "Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
fun ScanListDialog(
    scanList: MutableState<List<String>>,
    shouldShowDialog: MutableState<Boolean>
) {
    if (shouldShowDialog.value) {
        Dialog(
            onDismissRequest = { shouldShowDialog.value = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                LazyColumn {
                    itemsIndexed(scanList.value) { _, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(65.dp)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.outline),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                modifier = Modifier
                                    .padding(start = 5.dp),
                                text = item,
                                fontSize = 18.sp
                            )
                            FilledTonalButton(
                                modifier = Modifier
                                    .padding(end = 5.dp),
                                shape = RoundedCornerShape(10.dp),
                                onClick = {
                                    val id = item.replace(Regex("[^0-9]"), "")
                                    WebSocketClient.send("RESTORE_ITEM:$id")
                                    shouldShowDialog.value = false
                                }
                            ) {
                                Text("Восстановить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryScanDialog(
    shouldShowDialog: MutableState<Boolean>,
    archivingInfo: MutableState<String>,
) {
    if (shouldShowDialog.value) {
        Dialog(
            onDismissRequest = { }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(15.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (archivingInfo.value == "ARCHIVING") {
                        Text(
                            text = "На сервере идет процесс\nвосстановления скана",
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp
                        )
                    } else {
                        Text(
                            text = archivingInfo.value,
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            modifier = Modifier
                                .padding(end = 5.dp),
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                archivingInfo.value = ""
                                shouldShowDialog.value = false
                            }
                        ) {
                            Text("Продолжить")
                        }
                    }
                }
            }
        }
    }
}