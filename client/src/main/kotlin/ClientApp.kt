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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ClientApp : Application() {
    // Модель данных для таблиц
    data class ServerInfo(
        val name: String,
        val address: String,
        val port: Int
    ) {
        val nameProperty = SimpleStringProperty(name)
        val addressProperty = SimpleStringProperty(address)
        val portProperty = SimpleStringProperty(port.toString())
    }

    data class ProcessInfo(
        val pid: String,
        val name: String,
        val memory: String
    ) {
        val pidProperty = SimpleStringProperty(pid)
        val nameProperty = SimpleStringProperty(name)
        val memoryProperty = SimpleStringProperty(memory)
    }

    private val servers = FXCollections.observableArrayList<ServerInfo>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var currentConnection: Socket? = null
    private var currentPath = "C:\\"
    private var allProcesses = emptyList<ProcessInfo>()
    
    private lateinit var logArea:           TextArea
    private lateinit var serversTable:      TableView<ServerInfo>
    private lateinit var processesTable:    TableView<ProcessInfo>
    private lateinit var fileSystemList:    ListView<String>
    private lateinit var currentPathLabel:  Label
    private lateinit var searchField:       TextField
    private lateinit var progressBar:       ProgressBar
    private lateinit var statusBar:         Label
    private lateinit var messageField:      TextField

    // Кнопки
    private lateinit var disconnectButton:  Button
    private lateinit var deleteButton:      Button
    private lateinit var renameButton:      Button
    private lateinit var copyButton:        Button
    private lateinit var uploadButton:      Button
    private lateinit var backButton:        Button
    private lateinit var killButton:        Button
    private lateinit var searchButton:      Button
    private lateinit var startButton:       Button
    private lateinit var blockButton:       Button
    private lateinit var monitorButton:     Button
    private lateinit var sendMessageButton: Button

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Клиент управления рабочими станциями"

        // Создаем вкладки
        val tabPane = TabPane().apply {
            tabs.addAll(
                createDiscoveryTab(),
                createManagementTab(),
            )
        }

        // Статус-бар с выравниванием по центру
        statusBar = Label("Не подключено").apply {
            padding = Insets(5.0)
            style = """
                -fx-background-color: #f0f0f0; 
                -fx-font-size: 12px;
                -fx-alignment: center;
            """.trimIndent()
            maxWidth = Double.MAX_VALUE
        }

        val mainLayout = BorderPane().apply {
            center = tabPane
            bottom = statusBar
        }

        primaryStage.scene = Scene(mainLayout, 1000.0, 700.0)
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

        progressBar = ProgressBar().apply {
            isVisible = false
            prefWidth = 200.0
        }

        val scanBox = HBox(10.0).apply {
            children.addAll(scanButton, progressBar)
        }

        val buttonBox = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(scanBox, connectButton, disconnectButton)
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
        val tabPane = TabPane().apply {
            tabs.addAll(
                createFileManagementTab(),
                createProcessManagementTab(),
                createHardwareTab(),
            )
        }
        return Tab("Управление").apply {
            content = tabPane
            isClosable = false
        }
    }

    private fun createFileManagementTab(): Tab {
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

        uploadButton = Button("Копировать на...").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
            setOnAction { uploadFileToServer() }
        }

        backButton = Button("←").apply {
            style = """
                -fx-font-size: 14px;
                -fx-pref-width: 40px;
                -fx-background-radius: 5px;
                -fx-font-weight: bold;
            """.trimIndent()
            setOnAction { navigateUp() }
        }

        currentPathLabel = Label(currentPath).apply {
            style = "-fx-font-size: 14px; -fx-text-fill: #333;"
        }

        val pathBox = HBox(5.0).apply {
            children.addAll(
                backButton,
                currentPathLabel
            )
        }

        val controlBox = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                listDirsButton,
                refreshButton,
                pathBox,
                Separator(),
                deleteButton,
                renameButton,
                copyButton,
                uploadButton,
            )
        }

        val layout = BorderPane().apply {
            top = controlBox
            center = fileSystemList
            padding = Insets(15.0)
        }

        return Tab("Файловая система").apply {
            content = layout
            isClosable = false
        }
    }

    private fun createProcessManagementTab(): Tab {
        processesTable = TableView<ProcessInfo>().apply {
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
            prefHeight = 400.0

            columns.addAll(
                TableColumn<ProcessInfo, String>("PID").apply {
                    cellValueFactory = PropertyValueFactory("pid")
                    prefWidth = 100.0
                },
                TableColumn<ProcessInfo, String>("Имя процесса").apply {
                    cellValueFactory = PropertyValueFactory("name")
                    prefWidth = 300.0
                },
                TableColumn<ProcessInfo, String>("Память").apply {
                    cellValueFactory = PropertyValueFactory("memory")
                    prefWidth = 150.0
                }
            )
        }

        val refreshButton = Button("Обновить список процессов").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
            setOnAction { refreshProcesses() }
        }

        killButton = Button("Прервать процесс").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 200px; -fx-text-fill: #c62828;"
            setOnAction { killSelectedProcess() }
        }

        searchField = TextField().apply {
            promptText = "Поиск по имени или PID"
            style = "-fx-font-size: 14px; -fx-pref-width: 300px;"
        }

        searchButton = Button("Найти").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 100px;"
            setOnAction { applyProcessFilter() }
        }

        startButton = Button("Запустить процесс").apply {
            style = """
                -fx-font-size: 14px; 
                -fx-pref-width: 200px;
                -fx-text-fill: #2e7d32;
            """.trimIndent()
            setOnAction { startNewProcess() }
        }

        val searchBox = HBox(10.0).apply {
            padding = Insets(0.0, 0.0, 10.0, 0.0)
            children.addAll(searchField, searchButton)
        }

        val layout = VBox(10.0).apply {
            padding = Insets(15.0)
            children.addAll(
                refreshButton,
                killButton,
                startButton,
                searchBox,
                processesTable
            )
            VBox.setVgrow(processesTable, Priority.ALWAYS)
        }

        return Tab("Процессы").apply {
            content = layout
            isClosable = false
        }
    }

    private fun createHardwareTab(): Tab {
        blockButton = Button("Заблокировать клавиатуру и мышь").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 300px;"
            setOnAction { toggleKeyboard() }
        }

        monitorButton = Button("Выключить монитор").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 300px;"
            setOnAction { toggleMonitor() }
        }

        messageField = TextField().apply {
            promptText = "Введите сообщение..."
            style = "-fx-font-size: 14px; -fx-pref-width: 200px;"
        }

        sendMessageButton = Button("Отправить").apply {
            style = "-fx-font-size: 14px; -fx-pref-width: 100px;"
            setOnAction { sendMessageToServer() }
        }

        val messageBox = HBox(10.0).apply {
            padding = Insets(0.0, 0.0, 10.0, 0.0)
            children.addAll(messageField, sendMessageButton)
        }

        val layout = VBox(10.0).apply {
            padding = Insets(15.0)
            children.addAll(
                Label("Управление периферией").apply {
                    style = "-fx-font-size: 14px;"
                },
                blockButton,
                monitorButton,
                Separator(),
                Label("Управление сообщениями").apply {
                    style = "-fx-font-size: 14px;"
                },
                messageBox
            )
        }

        return Tab("Оборудование").apply {
            content = layout
            isClosable = false
        }
    }

    /*******************************************************************
     START OF FILE PROCESSING HERE
     *******************************************************************/

    private fun listRootDirectories() {
        if(!isConnected()) return
        
        currentPath = "C:\\"
        Platform.runLater {
            currentPathLabel.text = currentPath
            refreshFileSystemView()
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
        if (!isConnected()) return
        
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
        if (!isConnected()) return
        
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
        if (!isConnected()) return
        
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
        if (!isConnected()) return
        
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

    private fun uploadFileToServer() {
        if (!isConnected()) return
        
        val fileChooser = FileChooser().apply {
            title = "Выберите файл для загрузки"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("Все файлы", "*.*")
            )
        }

        val selectedFile = fileChooser.showOpenDialog(null) ?: return
        val fileName = selectedFile.name
        val serverPath = convertToServerPath("$currentPath$fileName")

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("PUT_FILE\n")
                    write("$serverPath\n")
                    flush()
                }

                when (val response = currentConnection!!.getInputStream().bufferedReader().readLine()) {
                    "READY_FOR_DATA" -> {
                        log("Начата загрузка файла: ${selectedFile.name}")
                        val fileSize = selectedFile.length()
                        // Отправляем размер файла
                        currentConnection!!.getOutputStream().bufferedWriter().apply {
                            write("$fileSize\n")
                            flush()
                        }

                        FileInputStream(selectedFile).use { fis ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (fis.read(buffer).also { bytesRead = it } != -1) {
                                currentConnection!!.getOutputStream().write(buffer, 0, bytesRead)
                            }
                        }
                        currentConnection!!.getOutputStream().flush()

                        val endResponse = currentConnection!!.getInputStream().bufferedReader().readLine()
                        when {
                            endResponse.startsWith("FILE_RECEIVED:") -> {
                                val size = endResponse.substringAfter(":").toLong()
                                log("Файл успешно загружен ($size байт)")
                                Platform.runLater { refreshFileSystemView() } // Обновляем список
                            }
                            endResponse.startsWith("ERROR:") -> {
                                log("Ошибка на сервере: ${endResponse.substringAfter("ERROR:")}")
                            }
                            else -> log("Неизвестный ответ сервера: $endResponse")
                        }
                    }
                    "PATH_INVALID" -> log("Недопустимый путь на сервере")
                    "ACCESS_DENIED" -> log("Нет прав на запись")
                    else -> log("Неизвестный ответ сервера: $response")
                }
            } catch (e: Exception) {
                log("Ошибка при загрузке файла: ${e.message}")
            }
        }.start()
    }

    private fun navigateUp() {
        if (!isConnected()) return
        
        try {
            val currentDir = File(currentPath)
            val canonicalDir = currentDir.canonicalFile

            // Проверка корневой директории
            if (canonicalDir in File.listRoots()) {
                log("Вы уже в корневой директории")
                return
            }

            val parentDir = canonicalDir.parentFile ?: run {
                log("Ошибка навигации: родительский каталог не существует")
                return
            }

            // Обновляем текущий путь
            currentPath = parentDir.absolutePath + File.separator
            currentPathLabel.text = currentPath
            
            refreshFileSystemView() // Обновляем список

        } catch (e: IOException) {
            log("Ошибка обработки пути: ${e.message}")
        }
    }

    private fun refreshFileSystemView() {
        if (!isConnected()) return

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

    private fun convertToServerPath(windowsPath: String): String {
        val normalized = windowsPath
            .replace("/", "\\")
            .let { if (it.length > 260) "\\\\?\\$it" else it }
        return normalized
    }

    /*******************************************************************
     START OF PROCESS PROCESSING HERE
     *******************************************************************/

    private fun refreshProcesses() {
        if (!isConnected()) return
        
        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("LIST_PROCESSES\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response.startsWith("PROCESSES:") -> {
                        val processes = response.substringAfter("PROCESSES:")
                            .split(";")
                            .map {
                                val parts = it.split("|")
                                ProcessInfo(
                                    pid = parts.getOrElse(0) { "N/A" },
                                    name = parts.getOrElse(1) { "Unknown" },
                                    memory = parts.getOrElse(2) { "0 K" }
                                )
                            }
                        Platform.runLater {
                            allProcesses = processes // Сохраняем полный список
                            processesTable.items.setAll(processes)
                            searchField.text = "" // Сбрасываем фильтр
                            log("Получено процессов: ${processes.size}")
                        }
                    }
                    response.startsWith("ERROR:") -> {
                        log("Ошибка: ${response.substringAfter("ERROR:")}")
                    }
                }
            } catch (e: Exception) {
                log("Ошибка при получении процессов: ${e.message}")
            }
        }.start()
    }

    private fun killSelectedProcess() {
        if (!isConnected()) return
        
        val selected = processesTable.selectionModel.selectedItem ?: run {
            log("Процесс не выбран!")
            return
        }

        val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Подтверждение прерывания"
            headerText = "Вы уверены, что хотите прервать процесс?"
            contentText = "PID: ${selected.pid}\nИмя: ${selected.name}"
        }
        if (alert.showAndWait().get() != ButtonType.OK) return

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("KILL_PROCESS\n")
                    write("${selected.pid}\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response == "PROCESS_KILLED" -> {
                        log("Процесс успешно прерван: ${selected.name}")
                        refreshProcesses()
                    }
                    response.startsWith("KILL_FAILED") -> {
                        val code = response.substringAfter(":")
                        log("Ошибка прерывания (код $code)")
                    }
                    response.startsWith("ERROR") -> {
                        log("Ошибка: ${response.substringAfter(":")}")
                    }
                }
            } catch (e: Exception) {
                log("Ошибка при прерывании процесса: ${e.message}")
            }
        }.start()
    }

    private fun applyProcessFilter() {
        if (!isConnected()) return
        
        val query = searchField.text.trim().lowercase()
        if (query.isEmpty()) {
            processesTable.items.setAll(allProcesses)
            return
        }

        val filtered = allProcesses.filter {
            it.name.lowercase().contains(query) ||
                    it.pid.lowercase().contains(query)
        }

        processesTable.items.setAll(filtered)
        log("Найдено процессов: ${filtered.size}")
    }

    private fun startNewProcess() {
        if (!isConnected()) return
        
        val dialog = TextInputDialog("notepad.exe").apply {
            title = "Запуск процесса"
            headerText = "Введите имя процесса и аргументы:"
            contentText = "Примеры:\nnotepad.exe\ncalc.exe"
        }

        val command = dialog.showAndWait().orElse(null) ?: return

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("START_PROCESS\n")
                    write("$command\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response.startsWith("PROCESS_STARTED") -> {
                        val pid = response.substringAfter(":")
                        log("Процесс запущен (PID: $pid)")
                        refreshProcesses()
                    }
                    response.startsWith("START_FAILED") -> {
                        val code = response.substringAfter(":")
                        log("Ошибка запуска (код $code)")
                    }
                    else -> log("Ошибка: ${response.substringAfter(":")}")
                }
            } catch (e: Exception) {
                log("Ошибка запуска процесса: ${e.message}")
            }
        }.start()
    }

    /*******************************************************************
     START OF HARDWARE PROCESSING HERE
     *******************************************************************/

    private fun toggleKeyboard() {
        if (!isConnected()) return

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("TOGGLE_MOUSE_KEYBOARD\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response == "MOUSE_KEYBOARD_BLOCKED" -> Platform.runLater {
                        blockButton.text = "Разблокировать клавиатуру/мышь"
                        log("Клавиатура/мышь заблокирована")
                    }
                    response == "MOUSE_KEYBOARD_UNBLOCKED" -> Platform.runLater {
                        blockButton.text = "Заблокировать клавиатуру/мышь"
                        log("Клавиатура/мышь разблокирована")
                    }
                    response.startsWith("ERROR") -> log("Ошибка: ${response.substringAfter("ERROR: ")}")
                }
            } catch (e: Exception) {
                log("Ошибка управления клавиатурой/мышью: ${e.message}")
            }
        }.start()
    }

    private fun toggleMonitor() {
        if (!isConnected()) return

        val btn = monitorButton
        Thread {
            try {
                val turnOff = btn.text == "Выключить монитор"

                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("TOGGLE_MONITOR\n")
                    write("$turnOff\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response.startsWith("MONITOR_OFF") -> Platform.runLater {
                        btn.text = "Включить монитор"
                        log("Монитор выключен")
                    }
                    response.startsWith("MONITOR_ON") -> Platform.runLater {
                        btn.text = "Выключить монитор"
                        log("Монитор включен")
                    }
                    response.startsWith("ERROR") -> log("Ошибка: ${response.substringAfter(":")}")
                }
            } catch (e: Exception) {
                log("Ошибка управления монитором: ${e.message}")
            }
        }.start()
    }

    private fun sendMessageToServer() {
        if (!isConnected()) return
        
        val message = messageField.text.trim()
        if (message.isEmpty()) {
            log("Сообщение не может быть пустым!")
            return
        }

        Thread {
            try {
                currentConnection!!.getOutputStream().bufferedWriter().apply {
                    write("SHOW_MESSAGE\n")
                    write("$message\n")
                    flush()
                }

                val response = currentConnection!!.getInputStream().bufferedReader().readLine()
                when {
                    response == "MESSAGE_SHOWN" -> log("Сообщение успешно отправлено")
                    response.startsWith("ERROR") -> log("Ошибка: ${response.substringAfter(":")}")
                }
            } catch (e: Exception) {
                log("Ошибка отправки сообщения: ${e.message}")
            }
        }.start()
    }

    /*******************************************************************
     START OF SCAN PROCESSING HERE
     *******************************************************************/

    private fun scanServers() {
        log("Сканирование начато...")
        progressBar.isVisible = true
        progressBar.progress = ProgressIndicator.INDETERMINATE_PROGRESS
        
        Thread {
            try {
                val localAddress = getLocalIPv4Address()
                val networkInterface = NetworkInterface.getByInetAddress(localAddress)

                val broadcastAddress = networkInterface.interfaceAddresses
                    .first { it.address == localAddress }
                    .broadcast ?: run {
                    // Fallback для ручного расчета
                    val octets = localAddress.hostAddress.split(".").map { it.toInt() }
                    InetAddress.getByName("${octets[0]}.${octets[1]}.${octets[2]}.255")
                }
                
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 5000

                    // Отправка широковещательного запроса
                    val request = "DISCOVER".toByteArray()
                    val packet = DatagramPacket(
                        request,
                        request.size,
                        broadcastAddress,
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
                                    val serverInfo = ServerInfo(
                                        name = parts[1],
                                        address = responsePacket.address.hostAddress, // Берем IP из пакета!
                                        port = parts[2].toInt()
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
            } finally {
                Platform.runLater { progressBar.isVisible = false }
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
                    statusBar.text = "Подключено к: ${selected.name} (${selected.address})"
                }
            } catch (e: Exception) {
                log("Ошибка подключения: ${e.message}")
                currentConnection = null
                Platform.runLater {
                    disconnectButton.isDisable = true
                    statusBar.text = "Не подключено"
                }
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
                        statusBar.text = "Не подключено"
                    }
                }
            } catch (e: Exception) {
                log("Ошибка при отключении: ${e.message}")
            } finally {
                currentConnection = null
            }
        }.start()
    }

    // Функция для получения локального IPv4 адреса
    private fun getLocalIPv4Address(): InetAddress {
        val validPrefixes = listOf("192.168.", "172.20.") // Добавьте нужные префиксы
        val interfaces = NetworkInterface.getNetworkInterfaces()

        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue

            for (addr in networkInterface.inetAddresses) {
                if (addr is Inet4Address && validPrefixes.any { addr.hostAddress.startsWith(it) }) {
                    // println("Выбран интерфейс: ${networkInterface.name}, IP: ${addr.hostAddress}") // Для дебага
                    return addr
                }
            }
        }
        throw SocketException("No suitable IPv4 address found")
    }
    
    private fun isConnected(): Boolean {
        return if (currentConnection == null || currentConnection!!.isClosed) { 
            log("Нет активного подключения к серверу!")
            false 
        } else true
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
