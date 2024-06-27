package com.audiosource.backend.util;

import com.audiosource.backend.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class S3Utils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

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

    /* Formats an Instant to a readable date-time string. */
    public static String formatLastModified(Instant lastModified) {
        return DATE_FORMATTER.format(lastModified);
    }
}
