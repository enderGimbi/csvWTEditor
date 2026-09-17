package com.enderGimbi.wtlocal;

import com.enderGimbi.wtlocal.model.CsvParserResult;
import com.enderGimbi.wtlocal.model.LocalizationEntry;
import com.enderGimbi.wtlocal.service.CsvParserService;
import com.enderGimbi.wtlocal.service.CsvWriterService;
import com.enderGimbi.wtlocal.service.GameDirectoryService;
import com.enderGimbi.wtlocal.service.I18nService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.DefaultStringConverter;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

public class Main extends Application {

    private static final String PREF_LAST_DIR = "last_opened_dir";
    private static final String PREF_LAST_FILE = "last_opened_file";
    private static final String PREF_CREATE_BACKUP = "create_backup_file";

    private final GameDirectoryService directoryService = new GameDirectoryService();
    private final CsvParserService parserService = new CsvParserService();
    private final CsvWriterService writerService = new CsvWriterService();
    private final Preferences prefs = Preferences.userNodeForPackage(Main.class);
    private final I18nService i18n = new I18nService();

    private final ObservableList<LocalizationEntry> masterData = FXCollections.observableArrayList();
    private FilteredList<LocalizationEntry> filteredData;

    // Снапшот исходных значений для отслеживания изменений (Key -> Map<LangHeader, OriginalValue>)
    private final Map<String, Map<String, String>> originalDataSnapshot = new HashMap<>();

    private final TableView<LocalizationEntry> fixedTableView = new TableView<>();
    private final TableView<LocalizationEntry> mainTableView = new TableView<>();

    private final ComboBox<String> fileComboBox = new ComboBox<>();
    private final ComboBox<String> uiLangComboBox = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label();

    private final Button selectFolderBtn = new Button();
    private final Button selectFileBtn = new Button();
    private final Button saveBtn = new Button();
    private final CheckBox backupCheckBox = new CheckBox(".bak");

    private TableColumn<LocalizationEntry, String> keyCol;
    private Path currentLangDir;
    private CsvParserResult currentResult;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        // --- ВЕРХНЯЯ ПАНЕЛЬ ---
        uiLangComboBox.getItems().addAll("EN", "RU");
        uiLangComboBox.setValue(i18n.getCurrentLanguage().toUpperCase());

        backupCheckBox.setSelected(prefs.getBoolean(PREF_CREATE_BACKUP, true));
        backupCheckBox.setOnAction(e -> prefs.putBoolean(PREF_CREATE_BACKUP, backupCheckBox.isSelected()));

        HBox topBar = new HBox(10, selectFolderBtn, selectFileBtn, fileComboBox, searchField, saveBtn, backupCheckBox, new Label("UI:"), uiLangComboBox);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // --- НАСТРОЙКА ТАБЛИЦ ---
        filteredData = new FilteredList<>(masterData, p -> true);
        fixedTableView.setItems(filteredData);
        mainTableView.setItems(filteredData);

        mainTableView.setEditable(true);
        fixedTableView.setFocusTraversable(false);

        // Отключаем горизонтальный скроллбар в левой таблице
        fixedTableView.setStyle("-fx-scroll-bar-policy: never vertical;");

        // Синхронизация выделения строк
        fixedTableView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (newIdx != null && newIdx.intValue() >= 0) {
                mainTableView.getSelectionModel().select(newIdx.intValue());
            }
        });

        mainTableView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (newIdx != null && newIdx.intValue() >= 0) {
                fixedTableView.getSelectionModel().select(newIdx.intValue());
            }
        });

        SplitPane splitPane = new SplitPane(fixedTableView, mainTableView);
        SplitPane.setResizableWithParent(fixedTableView, false);
        splitPane.setDividerPositions(0.25);

        // --- СОБЫТИЯ UI ---
        uiLangComboBox.setOnAction(e -> {
            String selected = uiLangComboBox.getValue().toLowerCase();
            i18n.loadLanguage(selected);
            updateUiText();
        });

        selectFolderBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            if (currentLangDir != null && Files.exists(currentLangDir)) {
                chooser.setInitialDirectory(currentLangDir.toFile());
            }
            File selectedDir = chooser.showDialog(primaryStage);

            if (selectedDir != null) {
                try {
                    currentLangDir = directoryService.getLangDirectory(selectedDir.toPath());
                    refreshFileList();

                    if (fileComboBox.getItems().contains("ui.csv")) {
                        fileComboBox.getSelectionModel().select("ui.csv");
                    } else if (!fileComboBox.getItems().isEmpty()) {
                        fileComboBox.getSelectionModel().select(0);
                    }
                } catch (Exception ex) {
                    showError("Error", ex.getMessage());
                }
            }
        });

        selectFileBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            if (currentLangDir != null && Files.exists(currentLangDir)) {
                chooser.setInitialDirectory(currentLangDir.toFile());
            }
            File file = chooser.showOpenDialog(primaryStage);
            if (file != null) {
                currentLangDir = file.getParentFile().toPath();
                refreshFileList();
                fileComboBox.getSelectionModel().select(file.getName());
                loadFile(file.toPath());
            }
        });

        fileComboBox.setOnAction(e -> {
            String selected = fileComboBox.getValue();
            if (selected != null && currentLangDir != null) {
                loadFile(currentLangDir.resolve(selected));
            }
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(entry -> {
                if (newVal == null || newVal.isBlank()) return true;
                String filter = newVal.toLowerCase().trim();
                if (entry.getKey() != null && entry.getKey().toLowerCase().contains(filter)) return true;
                for (String t : entry.getTranslations().values()) {
                    if (t != null && t.toLowerCase().contains(filter)) return true;
                }
                return false;
            });
            statusLabel.setText(i18n.get("status_matches", filteredData.size(), masterData.size()));
        });

        saveBtn.setOnAction(e -> {
            String selected = fileComboBox.getValue();
            if (selected != null && currentResult != null && currentLangDir != null) {
                try {
                    Path targetFile = currentLangDir.resolve(selected);

                    // 1. Создаем резервную копию (.bak), если включена галочка
                    if (backupCheckBox.isSelected() && Files.exists(targetFile)) {
                        Path backupFile = currentLangDir.resolve(selected + ".bak");
                        Files.copy(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    // 2. Экспортируем данные в основной CSV
                    writerService.export(targetFile, currentResult);

                    // 3. Обновляем снимок (сбрасывается зелёная подсветка)
                    takeOriginalSnapshot();
                    mainTableView.refresh();

                    statusLabel.setText(i18n.get("status_saved", targetFile.getFileName()));
                } catch (Exception ex) {
                    showError("Save Error", ex.getMessage());
                }
            }
        });

        HBox statusBar = new HBox(10, statusLabel);
        statusBar.setPadding(new Insets(5, 10, 5, 10));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(splitPane);
        root.setBottom(statusBar);

        updateUiText();

        Scene scene = new Scene(root, 1150, 650);
        primaryStage.setScene(scene);
        primaryStage.show();

        synchronizeScrollBars();

        // Восстановление последнего пути
        restoreLastPath();
    }

    private void refreshFileList() {
        if (currentLangDir != null && Files.exists(currentLangDir)) {
            try {
                List<Path> files = directoryService.getLocalizationFiles(currentLangDir);
                fileComboBox.getItems().clear();
                files.forEach(f -> fileComboBox.getItems().add(f.getFileName().toString()));
            } catch (Exception e) {
                showError("Error", "Failed to list directory: " + e.getMessage());
            }
        }
    }

    private void updateUiText() {
        primaryStage.setTitle(i18n.get("app_title"));
        selectFolderBtn.setText(i18n.get("btn_select_folder"));
        selectFileBtn.setText(i18n.get("btn_select_file"));
        saveBtn.setText(i18n.get("btn_save"));
        fileComboBox.setPromptText(i18n.get("combo_prompt"));
        searchField.setPromptText(i18n.get("search_prompt"));
        statusLabel.setText(i18n.get("status_ready"));
        backupCheckBox.setText(i18n.get("chk_backup"));
        if (keyCol != null) {
            keyCol.setText(i18n.get("col_key"));
        }
    }

    private void loadFile(Path filePath) {
        try {
            currentResult = parserService.parse(filePath);
            fixedTableView.getColumns().clear();
            mainTableView.getColumns().clear();
            masterData.clear();
            originalDataSnapshot.clear();

            masterData.addAll(currentResult.entries());
            takeOriginalSnapshot();

            // Запоминаем открытую папку и файл
            saveLastPaths(currentLangDir, filePath.getFileName().toString());

            // 1. Фиксированный столбец Key / ID в левой таблице
            keyCol = new TableColumn<>(i18n.get("col_key"));
            keyCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getKey()));
            keyCol.setPrefWidth(250);
            fixedTableView.getColumns().add(keyCol);

            // 2. Языковые столбцы с кастомным редактированием и подсветкой
            for (int i = 1; i < currentResult.headers().size(); i++) {
                String langHeader = currentResult.headers().get(i);
                TableColumn<LocalizationEntry, String> langCol = new TableColumn<>(langHeader);
                langCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTranslations(langHeader)));

                langCol.setCellFactory(col -> new TextFieldTableCell<LocalizationEntry, String>(new DefaultStringConverter()) {
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                            setStyle("");
                            return;
                        }

                        LocalizationEntry entry = getTableRow().getItem();
                        Map<String, String> originalRow = originalDataSnapshot.get(entry.getKey());
                        String origVal = (originalRow != null) ? originalRow.get(langHeader) : "";
                        String currentVal = item != null ? item : "";

                        if (!currentVal.equals(origVal)) {
                            setStyle("-fx-background-color: #e2f0d9; -fx-text-fill: #1b5e20; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                });

                langCol.setOnEditCommit(event -> {
                    LocalizationEntry entry = event.getRowValue();
                    entry.setTranslation(langHeader, event.getNewValue());
                    mainTableView.refresh();
                });

                langCol.setPrefWidth(180);
                mainTableView.getColumns().add(langCol);
            }

            statusLabel.setText(i18n.get("status_loaded", filePath.getFileName(), masterData.size()));

        } catch (Exception e) {
            showError("Error", e.getMessage());
        }
    }

    private void takeOriginalSnapshot() {
        originalDataSnapshot.clear();
        for (LocalizationEntry entry : masterData) {
            Map<String, String> rowCopy = new HashMap<>(entry.getTranslations());
            originalDataSnapshot.put(entry.getKey(), rowCopy);
        }
    }

    private void synchronizeScrollBars() {
        ScrollBar leftVerticalBar = findScrollBar(fixedTableView);
        ScrollBar rightVerticalBar = findScrollBar(mainTableView);

        if (leftVerticalBar != null && rightVerticalBar != null) {
            leftVerticalBar.valueProperty().bindBidirectional(rightVerticalBar.valueProperty());
        }
    }

    private ScrollBar findScrollBar(TableView<?> table) {
        for (Node n : table.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                return bar;
            }
        }
        return null;
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void restoreLastPath() {
        String lastDir = prefs.get(PREF_LAST_DIR, null);
        String lastFile = prefs.get(PREF_LAST_FILE, null);

        if (lastDir != null) {
            Path dirPath = Path.of(lastDir);
            if (Files.exists(dirPath)) {
                currentLangDir = dirPath;
                refreshFileList();
            }
        }

        if (lastFile != null && currentLangDir != null) {
            Path filePath = currentLangDir.resolve(lastFile);
            if (Files.exists(filePath)) {
                fileComboBox.getSelectionModel().select(lastFile);
                loadFile(filePath);
            }
        }
    }

    private void saveLastPaths(Path dir, String fileName) {
        if (dir != null) prefs.put(PREF_LAST_DIR, dir.toAbsolutePath().toString());
        if (fileName != null) prefs.put(PREF_LAST_FILE, fileName);
    }

    @Override
    public void stop() throws Exception {
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}