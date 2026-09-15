package com.enderGimbi.wtlocal.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class GameDirectoryService {

    public Path getLangDirectory(Path gamePath){
        Path langPath = gamePath.resolve("lang");
        if(!Files.exists(langPath)||!Files.isDirectory(langPath)){
            throw new IllegalArgumentException("Папка 'lang' не найдена по адресу"+langPath);
        }

        return langPath;
    }

    public List<Path> getLocalizationFiles(Path langPath){
        try(Stream<Path> stream = Files.list(langPath)){
            return stream
                    .filter(path->path.toString().endsWith(".csv"))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать содержимое папки lang",e);
        }
    }
}
