package com.example.server

import android.content.Context
import android.widget.Toast
import com.example.server.data.FileDao
import com.example.server.data.FileEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object FileHandler {

    private lateinit var applicationContext: Context
    private lateinit var fileDao: FileDao
    private var tree = " "
    private var treeOld = " "

    fun initialize(context: Context, dao: FileDao) {
        if (!this::applicationContext.isInitialized) {
            applicationContext = context.applicationContext
        }
        if (!this::fileDao.isInitialized) {
            fileDao = dao
        }
    }

    fun getMemoryInfo(): Map<String, Long> {
        val runtime = Runtime.getRuntime()
        val m1 = runtime.totalMemory() - runtime.freeMemory()
        val mmax = runtime.maxMemory()
        return mapOf("M1" to m1, "Mmax" to mmax)
    }

    suspend fun scanFileSystem() {
        val chromeFolders = listOf(
            "/data/data/com.android.chrome"
        )

        while (true) {
            val startTimeScanning = System.currentTimeMillis()
            val scanResults = chromeFolders.mapNotNull { folder ->
                val folderTree = executeCommandWithRoot("du -ah $folder")
                folderTree?.let { folder to it }
            }.toMap()

            val endTimeScanning = System.currentTimeMillis()
            val scanTime = endTimeScanning - startTimeScanning

            val totalSize = scanResults.values.sumOf { it.length }

            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat: DateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            val message = Gson().toJson(
                mapOf(
                    "scanResults" to scanResults,
                    "totalSize" to totalSize,
                    "scanTime" to scanTime,
                    "date" to dateFormat.format(Date()),
                    "startTime" to timeFormat.format(Date(startTimeScanning))
                )
            )

            treeOld = tree
            tree = createTree(message)
            WebSocketServer.send("SCAN:$tree")

            handleChanges(applicationContext, treeOld, tree)

            delay(5000)
        }
    }

    private fun executeCommandWithRoot(command: String): String? {
        var process: Process? = null
        var output: String? = null

        try {
            process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            os.write(command.toByteArray())
            os.flush()
            os.close()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val builder = StringBuilder()

            var line: String? = null
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
                builder.append("\n")
            }

            reader.close()
            output = builder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }

        return output
    }

    private fun createTree(json: String): String {
        val jsonObject = JSONObject(json)
        val scanResults = jsonObject.getJSONObject("scanResults")
        val totalSize = jsonObject.getLong("totalSize")
        val scanTime = jsonObject.getLong("scanTime")
        val date = jsonObject.getString("date")
        val startTime = jsonObject.getString("startTime")

        val formattedResults = StringBuilder("ScanResult\n")
        formattedResults.append("Дата: $date\n")
        formattedResults.append("Время начала: $startTime\n")
        formattedResults.append("Время сканирования: ${scanTime}ms\n")
        formattedResults.append("Общий размер: ${totalSize}K\n")

        val chromeFolderPath = "/data/data/com.android.chrome"

        scanResults.keys().forEach { key ->
            val value = scanResults.getString(key)
            val paths = value.split("\n")
            val folderSizes = mutableMapOf<String, Double>()
            paths.forEach { path ->
                if (path.isNotBlank() && path.contains(chromeFolderPath)) {
                    var sizeStr = path.substringBefore("\t")
                    val multiplier = 1.0
                    if (sizeStr.endsWith("K")) {
                        sizeStr = sizeStr.removeSuffix("K")
                    } else if (sizeStr.endsWith("M")) {
                        sizeStr = sizeStr.removeSuffix("M")
                    }
                    val size = sizeStr.toDouble() * multiplier
                    val folders = path.substringAfter(chromeFolderPath).split("/")
                    var currentPath = chromeFolderPath
                    folders.forEach { folder ->
                        currentPath += "/$folder"
                        if (currentPath != chromeFolderPath) {
                            folderSizes[currentPath] = (folderSizes[currentPath] ?: 0.0) + size
                        }
                    }
                }
            }
            val sortedFolders = folderSizes.keys.sorted()
            var isFirst = true
            sortedFolders.forEach { folder ->
                if (folder != chromeFolderPath) {
                    if (isFirst) {
                        isFirst = false
                    } else {
                        val indentLevel = max(0, folder.count { it == '/' } - 4)
                        val indents = "| ".repeat(indentLevel)
                        val folderName = folder.split("/").last()
                        formattedResults.append("$indents$folderName (${folderSizes[folder]}K)\n")
                    }
                }
            }
        }
        return formattedResults.toString()
    }

    private fun handleChanges(context: Context, scanOld: String, scanNew: String) {
        if (checkChangeTree(scanOld, scanNew) && scanOld != " ") {
            var fileEntity = FileEntity(0, "", "")
            val id = fileDao.insert(fileEntity)

            fileEntity = fileEntity.copy(
                id = id.toInt(),
                txtFilePath = "chrome_tree_${id}.txt",
                archivePath = "chrome_backup_${id}.tar.gz"
            )

            fileDao.update(fileEntity)

            archiveDirectory(context, fileEntity, tree)
        }
    }

    private fun checkChangeTree(scanOld: String, scanNew: String): Boolean {
        val oldLines = scanOld.split("\n")
        val newLines = scanNew.split("\n")

        val oldMap = mutableMapOf<String, String>()
        val newMap = mutableMapOf<String, String>()

        oldLines.drop(5).forEach { line ->
            val name = line.substringBefore("(")
            val size = line.substringAfter("(").substringBefore(")")
            oldMap[name] = size
        }

        newLines.drop(5).forEach { line ->
            val name = line.substringBefore("(")
            val size = line.substringAfter("(").substringBefore(")")
            newMap[name] = size
        }

        var isChanged = false

        newMap.forEach { (name, size) ->
            if (!oldMap.containsKey(name) || oldMap[name] != size) {
                isChanged = true
                return@forEach
            }
        }

        return isChanged
    }

    private fun archiveDirectory(context: Context, fileEntity: FileEntity, treeNew: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val appDirectory = File(context.filesDir.path)
            if (!appDirectory.exists()) {
                appDirectory.mkdirs()
            }

            val archiveName = fileEntity.archivePath
            val txtFileName = fileEntity.txtFilePath
            val archivePath = "${appDirectory.path}/$archiveName"

            val treeNewFile = File(context.filesDir, txtFileName)
            treeNewFile.writeText(treeNew)

            val command = arrayOf("su", "-c", "tar -czvf $archivePath -C /data/data com.android.chrome")

            try {
                val process = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()

                val result = process.waitFor()
                if (result == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Команда успешно выполнена", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка при выполнении команды", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка при выполнении команды", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun replaceDirectoryWithArchive(context: Context, id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WebSocketServer.send("REPLACED_ARCHIVE:ARCHIVING")
                val stopChromeCommand = arrayOf("su", "-c", "am force-stop com.android.chrome")
                val stopProcess = ProcessBuilder(*stopChromeCommand)
                    .redirectErrorStream(true)
                    .start()
                val stopResult = stopProcess.waitFor()

                if (stopResult == 0) {
                    val startTimeArchiving = System.currentTimeMillis()
                    val appDirectory = File(context.filesDir.path)
                    val archivePath = "${appDirectory.path}/chrome_backup_$id.tar.gz"

                    val command = arrayOf(
                        "su", "-c",
                        "rm -rf /data/data/com.android.chrome/* && tar -xzvf $archivePath -C /data/data"
                    )

                    val process = ProcessBuilder(*command)
                        .redirectErrorStream(true)
                        .start()

                    val result = process.waitFor()
                    val endTimeArchiving = System.currentTimeMillis()
                    val timeArchiving = endTimeArchiving - startTimeArchiving

                    if (result == 0) {
                        val startChromeCommand = arrayOf("su", "-c", "am start com.android.chrome")
                        val startProcess = ProcessBuilder(*startChromeCommand)
                            .redirectErrorStream(true)
                            .start()
                        val startResult = startProcess.waitFor()

                        if (startResult == 0) {
                            val status = "Скан $id восстановлен"
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val dateRestored = dateFormat.format(Date())
                            val message = "$status\n$dateRestored\n$timeArchiving ms"
                            WebSocketServer.send("REPLACED_ARCHIVE:$message")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Файлы успешно заменены", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Ошибка при запуске хрома", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Ошибка при замене файлов", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка при остановке хрома", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка при замене файлов", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}