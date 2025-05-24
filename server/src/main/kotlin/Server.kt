import java.io.File
import java.io.IOException
import java.net.*
import kotlin.concurrent.thread

object SocketHolder {
    var discoverySocket: DatagramSocket? = null
    var tcpServerSocket: ServerSocket? = null
}

fun main() {
    // Запуск серверов
    thread { startDiscoveryServer() }
    thread { startTcpServer() }

    println("Сервер запущен. Нажмите Enter для остановки.")
    readlnOrNull()

    // Закрытие сокетов при выходе
    SocketHolder.discoverySocket?.close()
    SocketHolder.tcpServerSocket?.close()
}

private fun startDiscoveryServer() {
    val discoveryPort = 54321
    try {
        // Создаем сокет с возможностью повторного использования адреса
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(Inet6Address.getByName("::"), discoveryPort))
            broadcast = true
        }
        SocketHolder.discoverySocket = socket
        println("UDP сервер запущен на порту $discoveryPort")

        while (true) {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            val message = String(packet.data, 0, packet.length)
            if (message.trim() == "DISCOVER") {
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
                println("Отправлен ответ клиенту: ${packet.address} (Имя: $serverName)")
            }
        }
    } catch (e: Exception) {
        println("Ошибка в UDP сервере: ${e.message}")
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
        println("TCP сервер запущен на порту $tcpPort")

        while (true) {
            val clientSocket = serverSocket.accept()
            println("Подключен клиент: ${clientSocket.inetAddress}")
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
                                else -> {
                                    output.write("UNKNOWN_COMMAND\n")
                                    output.flush()
                                }
                            }
                        }
                    } catch (e: IOException) {
                        println("Ошибка при работе с клиентом: ${e.message}")
                    } finally {
                        socket.close()
                    }
                }
                println("Клиент отключен: ${clientSocket.inetAddress}")
            }
        }
    } catch (e: Exception) {
        println("Ошибка в TCP сервере: ${e.message}")
    }
}
