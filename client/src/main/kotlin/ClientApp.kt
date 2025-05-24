import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.io.FileOutputStream
import java.io.IOException
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
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var currentConnection: Socket? = null
    private var currentPath = "C:\\"
    
    private lateinit var logArea: TextArea
    private lateinit var serversTable: TableView<ServerInfo>
    private lateinit var fileSystemList: ListView<String>
    private lateinit var disconnectButton: Button
    private lateinit var currentPathLabel: Label
    private lateinit var deleteButton: Button
    private lateinit var renameButton: Button
    private lateinit var copyButton: Button

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
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
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

        disconnectButton = Button("Отключиться").apply {
            setOnAction { disconnectFromServer() }
            style = "-fx-font-size: 14px; -fx-pref-width: 150px;"
            isDisable = true
        }

        val buttonBox = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(scanButton, connectButton, disconnectButton)
        }

        logArea = TextArea().apply {
            isEditable = false
            promptText = "Лог событий..."
            style = "-fx-font-family: 'Consolas'; -fx-font-size: 13px;"
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
        val label = Label("Управление файловой системой").apply {
            style = "-fx-font-size: 16px;"
        }

        fileSystemList = ListView<String>().apply {
            prefHeight = 400.0
        }

        fileSystemList.apply {
            onMouseClicked = EventHandler { event ->
                if (event.clickCount == 2) {
                    listFiles()
                }
            }
        }

        val listDirsButton = Button("Все директории на C:\\").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
            setOnAction { listRootDirectories() }
        }

        val refreshButton = Button("Обновить").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
            setOnAction { refreshFileSystemView() }
        }

        deleteButton = Button("Удалить файл").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px; -fx-text-fill: #c62828;"
            setOnAction { deleteSelectedFile() }
        }

        renameButton = Button("Переименовать").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
            setOnAction { renameSelected() }
        }

        copyButton = Button("Копировать с...").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
            setOnAction { copyFileFromServer() }
        }

        currentPathLabel = Label(currentPath).apply {
            style = "-fx-font-size: 14px; -fx-text-fill: #333;"
        }

        val pathBox = HBox(10.0).apply {
            children.addAll(currentPathLabel)
        }

        val controlBox = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                label,
                listDirsButton,
                refreshButton,
                pathBox,
                Separator(),
                deleteButton,
                renameButton,
                copyButton,
            )
        }

        val layout = BorderPane().apply {
            top = controlBox
            center = fileSystemList
            padding = Insets(15.0)
        }

        return Tab("Управление").apply {
            content = layout
            isClosable = false
        }
    }

    private fun listRootDirectories() {
        currentPath = "C:\\"
        Platform.runLater {
            currentPathLabel.text = currentPath
            refreshFileSystemView()
        }
        
        if (currentConnection == null || currentConnection!!.isClosed) {
            log("Нет активного подключения к серверу!")
            return
        }

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("LIST_DIRS_C\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                if (response.startsWith("DIRS:")) {
                    val dirs = response.substringAfter("DIRS:").split(";")
                    Platform.runLater {
                        fileSystemList.items.setAll(dirs)
                        log("Получено директорий: ${dirs.size}")
                    }
                }
            } catch (e: Exception) {
                log("Ошибка при получении директорий: ${e.message}")
            }
        }.start()
    }

    private fun listFiles() {
        val selected = fileSystemList.selectionModel.selectedItem ?: run {
            log("Директория не выбрана!")
            return
        }

        if (!selected.endsWith("\\")) { // Проверяем на "\\"
            log("Выбран файл, а не директория")
            return
        }

        val newPath = "$currentPath${selected.dropLast(1)}\\".replace("/", "\\")
        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("LIST_FILES\n")
                    write("$newPath\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response.startsWith("FILES:") -> {
                        val filesData = response.substringAfter("FILES:")
                        val items = filesData.split("|").filter { it.isNotEmpty() } // Фильтрация пустых элементов
                        val files = items.mapNotNull { item ->
                            val parts = item.split(";")
                            if (parts.size >= 2) {
                                val name = parts[0]
                                "${name}${if (name.endsWith("\\")) "" else " [${parts[1]} bytes]"}"
                            } else {
                                log("Некорректный формат элемента: $item")
                                null // Игнорируем некорректные элементы
                            }
                        }
                        Platform.runLater {
                            currentPath = newPath
                            currentPathLabel.text = currentPath
                            fileSystemList.items.setAll(files)
                            log("Загружено элементов: ${files.size}")
                        }
                    }
                    response.startsWith("ERROR:") -> {
                        log("Ошибка: ${response.substringAfter("ERROR:")}")
                    }
                }
            } catch (e: Exception) {
                log("Ошибка при получении файлов: ${e.message}")
            }
        }.start()
    }

    private fun deleteSelectedFile() {
        val selected = fileSystemList.selectionModel.selectedItem ?: run {
            log("Файл не выбран!")
            return
        }

        if (selected.endsWith("\\")) {
            log("Нельзя удалить директорию!")
            return
        }

        val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Подтверждение удаления"
            headerText = "Вы уверены, что хотите удалить файл?"
            contentText = "Файл: ${selected.substringBefore(" [")}"
        }
        if (alert.showAndWait().get() != ButtonType.OK) return

        val fullPath = "$currentPath${selected.substringBefore(" [")}"
        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("DELETE_FILE\n")
                    write("$fullPath\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when (response) {
                    "DELETE_SUCCESS" -> {
                        log("Файл успешно удален: ${selected.substringBefore(" [")}")
                        Platform.runLater { refreshFileSystemView() } // Обновляем список
                    }
                    "DELETE_FAILED" -> log("Ошибка удаления файла")
                    "DELETE_INVALID" -> log("Файл не существует")
                    "DELETE_DENIED" -> log("Нет прав на удаление")
                }
            } catch (e: Exception) {
                log("Ошибка при удалении файла: ${e.message}")
            }
        }.start()
    }

    private fun renameSelected() {
        val selected = fileSystemList.selectionModel.selectedItem ?: run {
            log("Файл или директория не выбраны!")
            return
        }

        val name = selected.substringBefore(" [")
        val dialog = TextInputDialog(name).apply {
            title = "Переименование"
            headerText = "Введите новое имя:"
            contentText = "Текущее имя: $name"
        }

        val newName = dialog.showAndWait().orElse(null) ?: return

        val oldPath = "$currentPath$name"
        val newPath = "$currentPath$newName"

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("RENAME\n")
                    write("$oldPath\n")
                    write("$newPath\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when (response) {
                    "RENAME_SUCCESS" -> {
                        log("Переименование успешно: $name -> $newName")
                        Platform.runLater { refreshFileSystemView() } // Обновляем список
                    }
                    "RENAME_FAILED" -> log("Ошибка переименования")
                    "RENAME_NOT_FOUND" -> log("Файл или директория не существует")
                    "RENAME_DENIED" -> log("Нет прав на переименование")
                }
            } catch (e: Exception) {
                log("Ошибка при переименовании: ${e.message}")
            }
        }.start()
    }

    private fun copyFileFromServer() {
        val selected = fileSystemList.selectionModel.selectedItem ?: run {
            log("Файл не выбран!")
            return
        }

        if (selected.endsWith("\\")) {
            log("Нельзя копировать директорию!")
            return
        }

        val fileName = selected.substringBefore(" [")
        val serverPath = "$currentPath$fileName".replace("/", "\\")

        // Диалог выбора места сохранения
        val fileChooser = FileChooser().apply {
            title = "Выберите место для сохранения файла"
            initialFileName = fileName
            extensionFilters.add(FileChooser.ExtensionFilter("Все файлы", "*.*"))
        }

        val saveFile = fileChooser.showSaveDialog(null) ?: return

        Thread {
            try {
                // Запрос на получение файла
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("GET_FILE\n")
                    write("$serverPath\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()

                when {
                    response.startsWith("FILE_START:") -> {
                        val fileSize = response.substringAfter(":").toLong()
                        log("Начато копирование файла ($fileSize байт)...")

                        FileOutputStream(saveFile).use { fos ->
                            val dataInput = currentConnection!!.getInputStream()

                            // Читаем ТОЧНОЕ количество байт
                            var remaining = fileSize
                            val buffer = ByteArray(8192)

                            while (remaining > 0) {
                                val read = dataInput.read(buffer, 0, minOf(buffer.size, remaining.toInt()))
                                if (read == -1) throw IOException("Unexpected end of stream")
                                fos.write(buffer, 0, read)
                                remaining -= read
                            }
                        }

                        // Теперь читаем FILE_END из BufferedReader
                        val endResponse = currentConnection!!.getInputStream().bufferedReader().readLine()
                        if (endResponse == "FILE_END") {
                            log("Файл успешно скопирован: ${saveFile.absolutePath}")
                        }
                    }
                    response == "FILE_NOT_FOUND" -> log("Файл не найден на сервере")
                    response == "FILE_ACCESS_DENIED" -> log("Нет прав доступа к файлу")
                    else -> log("Неизвестный ответ сервера: $response")
                }
            } catch (e: Exception) {
                log("Ошибка при копировании файла: ${e.message}")
            }
        }.start()
    }

    private fun refreshFileSystemView() {
        if (currentConnection?.isClosed != false) {
            log("Нет активного подключения!")
            return
        }

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("LIST_FILES\n")
                    write("$currentPath\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response.startsWith("FILES:") -> {
                        val filesData = response.substringAfter("FILES:")
                        val items = filesData.split("|").filter { it.isNotEmpty() } // Фильтр пустых элементов
                        val files = items.mapNotNull { item ->
                            val parts = item.split(";")
                            if (parts.size >= 2) { // Проверка корректности данных
                                val name = parts[0].replace("/", "\\")
                                "${name}${if (name.endsWith("\\")) "" else " [${parts[1]} bytes]"}"
                            } else {
                                log("Некорректный формат элемента: $item")
                                null // Игнорируем битые элементы
                            }
                        }

                        Platform.runLater {
                            currentPathLabel.text = currentPath
                            fileSystemList.items.setAll(files)
                            log("Интерфейс обновлен. Элементов: ${files.size}")
                        }
                    }
                    response.startsWith("ERROR:") -> {
                        Platform.runLater {
                            log("Ошибка обновления: ${response.substringAfter("ERROR:")}")
                        }
                    }
                }
            } catch (e: Exception) {
                Platform.runLater {
                    log("Ошибка при обновлении: ${e.message}")
                }
            }
        }.start()
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

    private fun disconnectFromServer() {
        Thread {
            try {
                currentConnection?.use {
                    it.close()
                    Platform.runLater {
                        disconnectButton.isDisable = true
                        log("Соединение с сервером разорвано")
                    }
                }
            } catch (e: Exception) {
                log("Ошибка при отключении: ${e.message}")
            } finally {
                currentConnection = null
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
                currentConnection?.close()
                currentConnection = Socket(selected.address, selected.port).apply {
                    soTimeout = 5000
                }
                Platform.runLater {
                    disconnectButton.isDisable = false
                    log("Успешно подключено к ${selected.name}")
                }
            } catch (e: Exception) {
                log("Ошибка подключения: ${e.message}")
                currentConnection = null
                Platform.runLater {
                    disconnectButton.isDisable = true
                }
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