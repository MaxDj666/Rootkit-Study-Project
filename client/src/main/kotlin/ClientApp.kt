import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.net.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ClientApp : Application() {
    // Модель данных для таблицы
    data class ServerInfo(
        val name: String,
        val address: String,
        val port: Int
    ) {
        val nameProperty = SimpleStringProperty(name)
        val addressProperty = SimpleStringProperty(address)
        val portProperty = SimpleStringProperty(port.toString())
    }

    private val servers = FXCollections.observableArrayList<ServerInfo>()
    private lateinit var logArea: TextArea
    private lateinit var serversTable: TableView<ServerInfo>
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Клиент для управления серверами"

        // Создаем вкладки
        val tabPane = TabPane().apply {
            tabs.addAll(
                createDiscoveryTab(),
                createManagementTab()
            )
        }

        primaryStage.scene = Scene(tabPane, 1000.0, 700.0)
        primaryStage.minWidth = 800.0
        primaryStage.minHeight = 600.0
        primaryStage.show()
    }

    private fun createDiscoveryTab(): Tab {
        // Настройка таблицы
        serversTable = TableView<ServerInfo>().apply {
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
            prefHeight = 300.0

            columns.addAll(
                TableColumn<ServerInfo, String>("Имя компьютера").apply {
                    cellValueFactory = PropertyValueFactory("name")
                    prefWidth = 300.0
                },
                TableColumn<ServerInfo, String>("IP-адрес").apply {
                    cellValueFactory = PropertyValueFactory("address")
                    prefWidth = 200.0
                },
                TableColumn<ServerInfo, String>("Порт").apply {
                    cellValueFactory = PropertyValueFactory("port")
                    prefWidth = 150.0
                }
            )
            items = servers
        }

        val scanButton = Button("Сканировать сеть").apply {
            setOnAction { scanServers() }
            style = "-fx-font-size: 14px; -fx-pref-width: 150px;"
        }

        val connectButton = Button("Подключиться").apply {
            setOnAction { connectToServer() }
            style = "-fx-font-size: 14px; -fx-pref-width: 150px;"
        }

        logArea = TextArea().apply {
            isEditable = false
            promptText = "Лог событий..."
            style = "-fx-font-family: 'Consolas'; -fx-font-size: 13px;"
        }

        val buttonBox = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(scanButton, connectButton)
        }

        val content = VBox(15.0).apply {
            padding = Insets(15.0)
            children.addAll(buttonBox, serversTable, logArea)
            VBox.setVgrow(serversTable, Priority.ALWAYS)
            VBox.setVgrow(logArea, Priority.ALWAYS)
        }

        return Tab("Поиск рабочих станций").apply {
            this.content = content
            isClosable = false
        }
    }

    private fun createManagementTab(): Tab {
        val label = Label("Управление подключенной станцией").apply {
            style = "-fx-font-size: 16px; -fx-padding: 20px;"
        }

        val content = VBox().apply {
            padding = Insets(20.0)
            children.add(label)
        }

        return Tab("Управление").apply {
            this.content = content
            isClosable = false
        }
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
                                val parts = message.split(":")
                                if (parts.size == 3) {
                                    val serverName = parts[1]
                                    val port = parts[2].toInt()
                                    val serverInfo = ServerInfo(
                                        name = serverName,
                                        address = responsePacket.address.hostAddress.toString(),
                                        port = port
                                    )
                                    Platform.runLater {
                                        if (servers.none { it.address == serverInfo.address }) {
                                            servers.add(serverInfo)
                                            log("Найден сервер: ${serverInfo.name} (${serverInfo.address})")
                                        }
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
        val selected = serversTable.selectionModel.selectedItem ?: run {
            log("Сервер не выбран!")
            return
        }
        Thread {
            try {
                Socket(selected.address, selected.port).use { socket ->
                    val response = socket.getInputStream().bufferedReader().readLine()
                    log("Ответ сервера '${selected.name}': $response")
                }
            } catch (e: Exception) {
                log("Ошибка подключения к '${selected.name}': ${e.message}")
            }
        }.start()
    }

    private fun log(message: String) {
        Platform.runLater {
            val timestamp = LocalTime.now().format(timeFormatter)
            logArea.appendText("[$timestamp] $message\n")

            // Автоматическая прокрутка к новому сообщению
            logArea.positionCaret(logArea.length)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(ClientApp::class.java)
        }
    }
}