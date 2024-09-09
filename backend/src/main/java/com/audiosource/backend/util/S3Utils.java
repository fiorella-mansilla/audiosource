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
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class S3Utils {
    private static final Logger logger = LoggerFactory.getLogger(S3Utils.class);
    private static final Set<String> SUPPORTED_FORMATS = Set.of(".mp3", ".wav");


    // Zip Utilities
    public static Path toZipDirectory(Path sourceDirectory) throws IOException {

        // Compute the path for the zip file
        Path outputZipPath = Paths.get(sourceDirectory.toString() + ".zip");

        try (FileOutputStream fileOutputStream = new FileOutputStream(outputZipPath.toFile());
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
        return outputZipPath;
    }

    // Directory Utilities

    /**
     * Helper method to get the immediate child directory from the given directory path.
     * @param directoryPath The path of the parent directory.
     * @return The path of the immediate child directory, or null if none exists or an error occurs.
     */
    public static Path getImmediateChildDirectory(Path directoryPath) {
        try {
            return Files.list(directoryPath)
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            logger.error("Error while getting immediate child directory", e);
            return null;
        }
    }

    // String Utilities

    /* Generates a unique name for each output directory of the separated files */
    public static String generateUniqueDirectoryName() {
        DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());
        String date = customFormatter.format(Instant.now());
        String shortenedUuid = UUID.randomUUID().toString().substring(0,8);
        return date + "_" + shortenedUuid;
    }

    // File Utilities

    public static boolean isSupportedFormat(String filePath) {
        return SUPPORTED_FORMATS.stream().anyMatch(filePath::endsWith);
    }
}
