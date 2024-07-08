package com.example.server

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.server.ui.theme.ScanTheme
import com.scottyab.rootbeer.RootBeer
import eu.chainfire.libsuperuser.Shell

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val rooting = remember { mutableStateOf(checkRootAccess()) }
                    if (rooting.value) {
                        val port = remember { mutableStateOf("8080") }
                        val showConfigDialog = remember { mutableStateOf(false) }
                        val serverStatus = remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            verticalArrangement = Arrangement.Center,
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
                                enabled = !serverStatus.value,
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    WebSocketServer.initialize(
                                        port.value.toInt(),
                                        { message -> showToast(message) },
                                        context = this@MainActivity
                                    )
                                    WebSocketServer.start()
                                    serverStatus.value = true
                                }
                            ) {
                                Text(text = "Включить")
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            ElevatedButton(
                                modifier = Modifier
                                    .width(150.dp),
                                enabled = serverStatus.value,
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    // Stop the server
                                    WebSocketServer.stop()
                                    serverStatus.value = false
                                    showToast("Сервер выключен")
                                }
                            ) {
                                Text(text = "Выключить")
                            }
                        }
                        ConfigDialog(port, showConfigDialog)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Для работы сервера необходимо\nпредоставить рут доступ",
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ElevatedButton(
                                modifier = Modifier
                                    .width(150.dp),
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    rooting.value = checkRootAccess()
                                }
                            ) {
                                Text(text = "Продолжить")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRootAccess(): Boolean {
        val rootBeer = RootBeer(this)
        return if (rootBeer.isRooted) {
            Shell.SU.available()
        } else {
            false
        }
    }

}

@Composable
fun ConfigDialog(port: MutableState<String>, shouldShowDialog: MutableState<Boolean>) {
    if (shouldShowDialog.value) {
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