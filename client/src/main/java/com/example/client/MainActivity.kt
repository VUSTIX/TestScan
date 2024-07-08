package com.example.client

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.example.client.ui.theme.ScanTheme


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScanTheme {
                val ip = remember { mutableStateOf("192.168.0.24") }
                val port = remember { mutableStateOf("8080") }

                val showConfigDialog = remember { mutableStateOf(false) }
                val showScanListDialog = remember { mutableStateOf(false) }
                val showRecoveryScanDialog = remember { mutableStateOf(false) }

                val connectServer = remember { mutableStateOf(false) }
                val isScanning = remember { mutableStateOf(false) }
                val isArchiving = remember { mutableStateOf("") }
                val memory = remember { mutableStateOf("") }

                val scan = remember { mutableStateOf(" ") }
                val scanOld = remember { mutableStateOf(" ") }
                val scanList = remember { mutableStateOf<List<String>>(listOf()) }

                val textButton = remember {
                    derivedStateOf {
                        if (isScanning.value) "Stop" else "Start"
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ElevatedButton(
                            modifier = Modifier
                                .width(150.dp),
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = PaddingValues(0.dp),
                            onClick = { showConfigDialog.value = true }
                        ) {
                            Text(text = "Config")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ElevatedButton(
                            modifier = Modifier
                                .width(150.dp),
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = PaddingValues(0.dp),
                            onClick = {
                                if (!connectServer.value) {
                                    WebSocketClient.initialize(ip.value, port.value.toInt()) { errorMessage ->
                                        showToast(errorMessage)
                                    }
                                    WebSocketClient.connect(
                                        onMemoryUpdate = { message ->
                                            memory.value = message
                                        },
                                        onScanUpdate = { message ->
                                            scanOld.value = scan.value
                                            scan.value = message
                                        },
                                        onScanListUpdate = { message ->
                                            val data = message.split(",")
                                            scanList.value = data
                                        },
                                        onScanningUpdate = { message ->
                                            isScanning.value = message == "TRUE"
                                        },
                                        onConnectUpdate = { message ->
                                            connectServer.value = message == "TRUE"
                                        },
                                        onArchiveUpgrade = { message ->
                                            isArchiving.value = message
                                            showRecoveryScanDialog.value = true
                                        }
                                    )
                                }
                                if (!isScanning.value && connectServer.value) {
                                    WebSocketClient.send("START_SCANNING")
                                } else if (isScanning.value && connectServer.value) {
                                    WebSocketClient.send("STOP_SCANNING")
                                }
                            }
                        ) {
                            Text(textButton.value)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ElevatedButton(
                            modifier = Modifier
                                .width(150.dp),
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = PaddingValues(0.dp),
                            onClick = {
                                showScanListDialog.value = true
                            }
                        ) {
                            Text(text = "Scan List")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = memory.value)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp)
                                .verticalScroll(rememberScrollState()),
                            text = checkChangeTree(scanOld.value, scan.value))
                    }
                }
                ConfigDialog(ip, port, showConfigDialog)
                ScanListDialog(scanList, showScanListDialog)
                RecoveryScanDialog(showRecoveryScanDialog, isArchiving)
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

}

fun checkChangeTree(scanOld: String, scanNew: String): AnnotatedString {
    if (scanOld != " ") {
        val oldScan = scanOld.split("\n")
        val newScan = scanNew.split("\n")

        val oldMap = mutableMapOf<String, String>()
        val newMap = mutableMapOf<String, String>()

        oldScan.drop(5).forEach { line ->
            if (line.contains("(") && line.contains(")")) {
                val name = line.substringBefore("(")
                val size = line.substringAfter("(").substringBefore(")")
                oldMap[name] = size
            }
        }

        newScan.drop(5).forEach { line ->
            if (line.contains("(") && line.contains(")")) {
                val name = line.substringBefore("(")
                val size = line.substringAfter("(").substringBefore(")")
                newMap[name] = size
            }
        }

        val colorScan = AnnotatedString.Builder()
        newScan.take(5).forEach { line ->
            colorScan.append("$line\n")
        }

        newMap.forEach { (name, size) ->
            if (!oldMap.containsKey(name)) {
                colorScan.pushStyle(SpanStyle(color = Color.Green))
                colorScan.append("$name($size)\n")
                colorScan.pop()
            } else if (oldMap[name] != size) {
                colorScan.pushStyle(SpanStyle(color = Color.Blue))
                colorScan.append("$name($size)\n")
                colorScan.pop()
            } else {
                colorScan.append("$name($size)\n")
            }
        }

        return colorScan.toAnnotatedString()
    } else {
        return AnnotatedString(scanNew)
    }
}
