import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.net.*

class ClientApp : Application() {
    private val servers = FXCollections.observableArrayList<String>()
    private lateinit var logArea: TextArea
    private lateinit var serversList: ListView<String>

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Клиент для управления серверами"

        val scanButton = Button("Сканировать").apply {
            setOnAction { scanServers() }
        }

        val connectButton = Button("Подключиться").apply {
            setOnAction { connectToServer() }
        }

        serversList = ListView(servers)
        logArea = TextArea().apply {
            isEditable = false
            promptText = "Лог событий..."
        }

        val layout = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(scanButton, serversList, connectButton, logArea)
        }

        primaryStage.scene = Scene(layout, 600.0, 400.0)
        primaryStage.show()
    }

    private fun scanServers() {
        log("Сканирование начато...")
        Thread {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 5000

                    // Отправка широковещательного запроса
                    val request = "DISCOVER".toByteArray()
                    val packet = DatagramPacket(
                        request,
                        request.size,
                        InetAddress.getByName("255.255.255.255"),
                        54321
                    )
                    socket.send(packet)
                    log("Запрос обнаружения отправлен")

                    // Поиск ответов от серверов
                    val buffer = ByteArray(1024)
                    while (true) {
                        try {
                            val responsePacket = DatagramPacket(buffer, buffer.size)
                            socket.receive(responsePacket)
                            val message = String(responsePacket.data, 0, responsePacket.length)
                            if (message.startsWith("SERVER_RESPONSE")) {
                                val serverInfo = "${responsePacket.address.hostAddress}:${message.split(":")[1]}"
                                Platform.runLater {
                                    if (!servers.contains(serverInfo)) {
                                        servers.add(serverInfo)
                                        log("Найден сервер: $serverInfo")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            break // Таймаут или ошибка чтения
                        }
                    }
                }
                log("Сканирование завершено")
            } catch (e: Exception) {
                log("Ошибка при сканировании: ${e.message}")
            }
        }.start()
    }

    private fun connectToServer() {
        val selected = serversList.selectionModel.selectedItem ?: run {
            log("Сервер не выбран!")
            return
        }
        val (address, port) = selected.split(":")
        Thread {
            try {
                Socket(address, port.toInt()).use { socket ->
                    val response = socket.getInputStream().bufferedReader().readLine()
                    log("Ответ сервера: $response")
                }
            } catch (e: Exception) {
                log("Ошибка подключения: ${e.message}")
            }
        }.start()
    }

    private fun log(message: String) {
        Platform.runLater {
            logArea.appendText("$message\n")
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(ClientApp::class.java)
        }
    }
}