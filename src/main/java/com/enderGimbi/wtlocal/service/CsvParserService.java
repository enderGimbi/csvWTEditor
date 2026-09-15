package com.enderGimbi.wtlocal.service;

import com.enderGimbi.wtlocal.model.CsvParserResult;
import com.enderGimbi.wtlocal.model.LocalizationEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvParserService {

    public CsvParserResult parse(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        List<String> rawRows = splitCsvRows(content);

        if (rawRows.isEmpty()) {
            return new CsvParserResult(Collections.emptyList(), Collections.emptyList());
        }

        // 1. Читаем заголовки (первая логическая строка)
        List<String> headers = parseCsvLine(rawRows.get(0));
        List<LocalizationEntry> entries = new ArrayList<>();

        // 2. Читаем остальные строки
        for (int i = 1; i < rawRows.size(); i++) {
            String rowStr = rawRows.get(i);
            if (rowStr.isBlank()) continue;

            List<String> columns = parseCsvLine(rowStr);
            if (columns.isEmpty()) continue;

            String key = columns.get(0);
            LocalizationEntry entry = new LocalizationEntry(key);

            for (int j = 1; j < headers.size(); j++) {
                String headerName = headers.get(j);
                String val = (j < columns.size()) ? columns.get(j) : "";
                entry.setTranslation(headerName, val);
            }

            entries.add(entry);
        }

        return new CsvParserResult(headers, entries);
    }

    /**
     * Разбивает весь текст файла на логические CSV-строки с учетом многострочных кавычек.
     */
    private List<String> splitCsvRows(String text) {
        List<String> rows = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++; // Пропускаем \r в комбинации \r\n
                }
                if (current.length() > 0) {
                    rows.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            rows.add(current.toString());
        }

        return rows;
    }

    /**
     * Парсит одну логическую CSV-строку по разделителю ';' с учетом кавычек.
     */
    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ';' && !inQuotes) {
                tokens.add(cleanValue(sb.toString()));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(cleanValue(sb.toString()));

        return tokens;
    }

    private String cleanValue(String val) {
        val = val.trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1).replace("\"\"", "\"");
        }
        return val;
    }
}