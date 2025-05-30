import com.sun.jna.Library
import com.sun.jna.Native
import java.io.*
import java.net.*
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

interface User32 : Library {
    fun BlockInput(fBlock: Boolean): Boolean
    fun SendMessageW(hWnd: Int, msg: Int, wParam: Int, lParam: Int): Int

    companion object {
        val INSTANCE: User32 by lazy {
            Native.load("user32", User32::class.java)
        }

        // Константы для управления монитором
        const val WM_SYSCOMMAND: Int = 0x0112
        const val SC_MONITORPOWER: Int = 0xF170
        const val MONITOR_OFF: Int = 2
        const val MONITOR_ON: Int = -1
    }
}

object SocketHolder {
    var discoverySocket: DatagramSocket? = null
    var tcpServerSocket: ServerSocket? = null
}

private val isBlocked = AtomicBoolean(false)

fun main() {
    // Запуск серверов
    thread { startDiscoveryServer() }
    thread { startTcpServer() }

    println("The server is running. Press Enter to stop.")
    readlnOrNull()

    // Закрытие сокетов при выходе
    SocketHolder.discoverySocket?.close()
    SocketHolder.tcpServerSocket?.close()
    println("The server has stopped.")
}

private fun startDiscoveryServer() {
    val discoveryPort = 54321
    try {
        // Создаем сокет с возможностью повторного использования адреса
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(discoveryPort))
            broadcast = true
        }
        SocketHolder.discoverySocket = socket
        println("UDP server running on port $discoveryPort")

        while (true) {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            val message = String(packet.data, 0, packet.length)
            if (message.trim() == "DISCOVER") {
                println("Received DISCOVER request from ${packet.address}")
                val serverName = InetAddress.getLocalHost().hostName
                val response = "SERVER_RESPONSE:$serverName:12345"
                val sendData = response.toByteArray()
                val responsePacket = DatagramPacket(
                    sendData,
                    sendData.size,
                    packet.address,
                    packet.port
                )
                socket.send(responsePacket)
                println("Reply sent to client: ${packet.address} (Name: $serverName)")
            }
        }
    } catch (e: SocketException) {
        if (e.message == "Socket closed") {
            println("UDP server stopped correctly")
        } else {
            println("Error in UDP server: ${e.message}")
        }
    } catch (e: Exception) {
        println("Error in UDP server: ${e.message}")
    }
}

private fun startTcpServer() {
    val tcpPort = 12345
    try {
        val serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(tcpPort))
        }
        SocketHolder.tcpServerSocket = serverSocket
        println("TCP server running on port $tcpPort")

        while (true) {
            val clientSocket = serverSocket.accept()
            println("Client connected: ${clientSocket.inetAddress}")
            thread {
                clientSocket.use { socket ->
                    try {
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream().bufferedWriter()

                        while (true) {
                            val command = input.readLine() ?: break // Выход при разрыве
                            when (command) {
                                "LIST_DIRS_C" -> {
                                    val dirs = File("C:/")
                                        .listFiles { file: File -> file.isDirectory }
                                        ?.map { "${it.name}\\" } // Добавляем "\" к именам директорий
                                    output.write("DIRS:${dirs?.joinToString(";") ?: ""}\n")
                                    output.flush()
                                }
                                "LIST_FILES" -> {
                                    val path = input.readLine()
                                    val dir = File(path)
                                    if (dir.exists() && dir.isDirectory) {
                                        val files = dir.listFiles()?.map {
                                            // Заменяем "/" на "\\" для Windows
                                            "${it.name}${if (it.isDirectory) "\\" else ""};${it.length()}"
                                        } ?: emptyList()
                                        output.write("FILES:${files.joinToString("|")}\n")
                                    } else {
                                        output.write("ERROR:Invalid directory\n")
                                    }
                                    output.flush()
                                }
                                "DELETE_FILE" -> {
                                    val filePath = input.readLine()
                                    val file = File(filePath)
                                    try {
                                        if (file.exists() && file.isFile()) {
                                            if (file.delete()) {
                                                output.write("DELETE_SUCCESS\n")
                                            } else {
                                                output.write("DELETE_FAILED\n")
                                            }
                                        } else {
                                            output.write("DELETE_INVALID\n")
                                        }
                                    } catch (e: SecurityException) {
                                        output.write("DELETE_DENIED\n")
                                    }
                                    output.flush()
                                }
                                "RENAME" -> {
                                    val oldPath = input.readLine()
                                    val newPath = input.readLine()
                                    val file = File(oldPath)
                                    try {
                                        if (file.exists()) {
                                            if (file.renameTo(File(newPath))) {
                                                output.write("RENAME_SUCCESS\n")
                                            } else {
                                                output.write("RENAME_FAILED\n")
                                            }
                                        } else {
                                            output.write("RENAME_NOT_FOUND\n")
                                        }
                                    } catch (e: SecurityException) {
                                        output.write("RENAME_DENIED\n")
                                    }
                                    output.flush()
                                }
                                "GET_FILE" -> {
                                    val filePath = input.readLine()
                                    val file = File(filePath)
                                    try {
                                        if (file.exists() && file.isFile()) {
                                            // Получаем бинарный OutputStream сокета
                                            val dataOutput = socket.getOutputStream()

                                            // Отправляем заголовок через Writer
                                            output.write("FILE_START:${file.length()}\n")
                                            output.flush() // Важно: сбрасываем буфер!

                                            // Передаем файл ТОЛЬКО через бинарный поток
                                            FileInputStream(file).use { fis ->
                                                fis.copyTo(dataOutput) // Автоматически копирует все байты
                                            }

                                            // Отправляем окончание через Writer
                                            output.write("FILE_END\n")
                                            output.flush()
                                        } else {
                                            output.write("FILE_NOT_FOUND\n")
                                            output.flush()
                                        }
                                    } catch (e: SecurityException) {
                                        output.write("FILE_ACCESS_DENIED\n")
                                        output.flush()
                                    }
                                }
                                "PUT_FILE" -> {
                                    val savePath = input.readLine()
                                    val file = File(savePath)
                                    try {
                                        if (file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true) {
                                            output.write("READY_FOR_DATA\n")
                                            output.flush()

                                            // Читаем размер файла
                                            val sizeLine = input.readLine()
                                            val fileSize = sizeLine.toLong()

                                            FileOutputStream(file).use { fos ->
                                                val dataInput = socket.getInputStream()
                                                var remaining = fileSize
                                                val buffer = ByteArray(8192)
                                                while (remaining > 0) {
                                                    val read = dataInput.read(buffer, 0, minOf(buffer.size, remaining.toInt()))
                                                    if (read == -1) throw IOException("Unexpected end of stream")
                                                    fos.write(buffer, 0, read)
                                                    remaining -= read
                                                }
                                            }
                                            output.write("FILE_RECEIVED:${file.length()}\n")
                                        } else {
                                            output.write("PATH_INVALID\n")
                                        }
                                    } catch (e: SecurityException) {
                                        output.write("ACCESS_DENIED\n")
                                    } catch (e: NumberFormatException) {
                                        output.write("ERROR:Invalid file size\n")
                                    } catch (e: IOException) {
                                        output.write("ERROR:Failed to receive file: ${e.message}\n")
                                    }
                                    output.flush()
                                }
                                "LIST_PROCESSES" -> {
                                    try {
                                        val process = Runtime.getRuntime().exec("tasklist /fo csv /nh")
                                        val reader = BufferedReader(InputStreamReader(process.inputStream, Charset.forName("CP866")))
                                        val processes = mutableListOf<String>()

                                        reader.useLines { lines ->
                                            lines.filter { it.isNotBlank() }.forEach { line ->
                                                val parts = line.split("\",\"")
                                                if (parts.size >= 5) {
                                                    val name = parts[0].trim('"') // Используем trim для удаления кавычек
                                                    val pid = parts[1].trim('"')
                                                    val memory = parts[4].trim('"')
                                                    processes.add("$pid|$name|$memory")
                                                }
                                            }
                                        }
                                        output.write("PROCESSES:${processes.joinToString(";")}\n")
                                    } catch (e: Exception) {
                                        output.write("ERROR:${e.message}\n")
                                    }
                                    output.flush()
                                }
                                "KILL_PROCESS" -> {
                                    try {
                                        val pid = input.readLine()
                                        val process = Runtime.getRuntime().exec("taskkill /PID $pid /F")
                                        val exitCode = process.waitFor()
                                        if (exitCode == 0) {
                                            output.write("PROCESS_KILLED\n")
                                        } else {
                                            output.write("KILL_FAILED:$exitCode\n")
                                        }
                                    } catch (e: Exception) {
                                        output.write("ERROR:${e.message}\n")
                                    }
                                    output.flush()
                                }
                                "START_PROCESS" -> {
                                    try {
                                        val commandInput = input.readLine()
                                        val process = Runtime.getRuntime().exec(commandInput)
                                        // Не ждём завершения процесса!
                                        output.write("PROCESS_STARTED:${process.pid()}\n")
                                    } catch (e: Exception) {
                                        output.write("ERROR:${e.message}\n")
                                    }
                                    output.flush()
                                }
                                "TOGGLE_MOUSE_KEYBOARD" -> {
                                    try {
                                        val newState = !isBlocked.get()
                                        val success = User32.INSTANCE.BlockInput(newState)

                                        if (success) {
                                            isBlocked.set(newState)
                                            output.write(
                                                if (newState) "MOUSE_KEYBOARD_BLOCKED\n"
                                                else "MOUSE_KEYBOARD_UNBLOCKED\n"
                                            )
                                        } else {
                                            output.write("ERROR: Failed to toggle mouse/keyboard. Try running as Administrator.\n")
                                        }
                                    } catch (e: Exception) {
                                        output.write("ERROR: ${e.message}\n")
                                    }
                                    output.flush()
                                }
                                "TOGGLE_MONITOR" -> {
                                    try {
                                        val turnOff = input.readLine().toBoolean()
                                        User32.INSTANCE.SendMessageW(
                                            0xFFFF, // HWND_BROADCAST (все окна)
                                            User32.WM_SYSCOMMAND, // Системная команда
                                            User32.SC_MONITORPOWER, // Управление монитором
                                            if (turnOff) User32.MONITOR_OFF else User32.MONITOR_ON // 2 = MONITOR_OFF, -1 = MONITOR_ON
                                        )
                                        output.write("MONITOR_${if (turnOff) "OFF" else "ON"}\n")
                                    } catch (e: Exception) {
                                        output.write("ERROR: ${e.message}\n")
                                    }
                                    output.flush()
                                }
                                "SHOW_MESSAGE" -> { // TODO: Очень упрощенная версия, необходима работа с видеобуфером
                                    try {
                                        val message = input.readLine()
                                        val powerShellCommand = arrayOf(
                                            "powershell",
                                            "-command",
                                            "Add-Type -AssemblyName PresentationFramework;" +
                                                    "[System.Windows.MessageBox]::Show('$message', 'Сообщение от клиента')"
                                        )
                                        Runtime.getRuntime().exec(powerShellCommand)
                                        output.write("MESSAGE_SHOWN\n")
                                    } catch (e: Exception) {
                                        output.write("ERROR:${e.message}\n")
                                    }
                                    output.flush()
                                }
                                else -> {
                                    output.write("UNKNOWN_COMMAND\n")
                                    output.flush()
                                }
                            }
                        }
                    } catch (e: IOException) {
                        println("Error while working with the client: ${e.message}")
                    } finally {
                        socket.close()
                    }
                }
                println("Client disconnected: ${clientSocket.inetAddress}")
            }
        }
    } catch (e: SocketException) {
        if (e.message == "Socket closed") {
            println("TCP server stopped correctly")
        } else {
            println("TCP server error: ${e.message}")
        }
    } catch (e: Exception) {
        println("TCP server error: ${e.message}")
    }
}
