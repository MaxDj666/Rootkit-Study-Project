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
                val response = "SERVER_RESPONSE:12345"
                val sendData = response.toByteArray()
                val responsePacket = DatagramPacket(
                    sendData,
                    sendData.size,
                    packet.address,
                    packet.port
                )
                socket.send(responsePacket)
                println("Отправлен ответ клиенту: ${packet.address}")
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
                clientSocket.use {
                    it.getOutputStream().apply {
                        write("Успешное подключение к серверу!\n".toByteArray())
                        flush()
                    }
                }
            }
        }
    } catch (e: Exception) {
        println("Ошибка в TCP сервере: ${e.message}")
    }
}
