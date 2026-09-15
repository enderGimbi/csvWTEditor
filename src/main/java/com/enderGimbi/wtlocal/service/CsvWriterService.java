package com.enderGimbi.wtlocal.service;

import com.enderGimbi.wtlocal.model.CsvParserResult;
import com.enderGimbi.wtlocal.model.LocalizationEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvWriterService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public void export(Path targetPath, CsvParserResult result) throws IOException {
        // 1. Создаем папку backups, если исходный файл существует
        if (Files.exists(targetPath)) {
            Path backupDir = targetPath.getParent().resolve("backups");
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }

            // Формируем имя резервной копии: originalName_TIMESTAMP.bak.csv
            String fileName = targetPath.getFileName().toString();
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String backupFileName = fileName.replace(".csv", "") + "_" + timestamp + ".bak.csv";

            Path backupPath = backupDir.resolve(backupFileName);

            // Перемещаем (Move) оригинальный файл в папку бэкапов
            Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 2. Создаем новый CSV-файл на месте прежнего с кодировкой UTF-8 без BOM (стандарт для WT)
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8)) {
            // Записываем заголовок
            writer.write(String.join(";", result.headers()));
            writer.newLine();

            for (LocalizationEntry entry : result.entries()) {
                StringBuilder line = new StringBuilder();
                line.append(sanitizeValue(entry.getKey()));

                for (int i = 1; i < result.headers().size(); i++) {
                    String langHeader = result.headers().get(i);
                    String val = entry.getTranslations(langHeader);
                    line.append(";").append(sanitizeValue(val));
                }

                writer.write(line.toString());
                writer.newLine();
            }
        }
    }
    // Внутри CsvWriterService при записи каждого значения:
    private String sanitizeValue(String val) {
        if (val == null) return "";

        // Если есть переносы строк, кавычки или точка с запятой — экранируем
        if (val.contains(";") || val.contains("\n") || val.contains("\r") || val.contains("\"")) {
            val = val.replace("\"", "\"\""); // Экранируем двойные кавычки
            return "\"" + val + "\"";
        }
        return val;
    }
}