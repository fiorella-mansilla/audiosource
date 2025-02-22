package com.audiosource.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class S3Utils {
    private static final Logger logger = LoggerFactory.getLogger(S3Utils.class);

    public static Path toZipDirectory(Path sourceDirectory) throws IOException {

        // Compute the path for the zip file
        Path zipDirectoryPath = Paths.get(sourceDirectory.toString() + ".zip");

        try (FileOutputStream fileOutputStream = new FileOutputStream(zipDirectoryPath.toFile());
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {

            Files.walk(sourceDirectory)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceDirectory.relativize(path).toString());
                        try {
                            zipOutputStream.putNextEntry(zipEntry);
                            Files.copy(path, zipOutputStream);
                            zipOutputStream.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to zip directory", e);
                        }
                    });
        }
        return zipDirectoryPath;
    }

    /* Generates a unique name for the output directory of processed files */
    public static String generateUniqueDirectoryName() {
        DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());
        String date = customFormatter.format(Instant.now());
        String shortenedUuid = UUID.randomUUID().toString().substring(0,8);
        return date + "_" + shortenedUuid;
    }
}
