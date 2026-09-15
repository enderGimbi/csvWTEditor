package com.enderGimbi.wtlocal.model;

import java.util.List;

public record CsvParserResult(List<String> headers,List<LocalizationEntry> entries) {}
