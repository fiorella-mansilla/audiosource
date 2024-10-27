package com.audiosource.backend.service.metadata;

import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/* Handles interactions with FileMetadata collection from MongoDB. */
@Service
public class FileMetadataService {
    private final FileMetadataRepository fileMetadataRepository;

    @Autowired
    public FileMetadataService(FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
    }

    /* Saves the initial metadata for the FileMetadata collection. */
    public FileMetadata saveInitialMetadata(String correlationId, String userEmail, String originalKeyName, String notificationStatus) {

        if (correlationId == null || userEmail == null || originalKeyName == null || notificationStatus == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        if (correlationId.isEmpty() || userEmail.isEmpty() || originalKeyName.isEmpty() || notificationStatus.isEmpty()) {
            throw new IllegalArgumentException("Parameters cannot be empty");
        }

        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setCorrelationId(correlationId);
        fileMetadata.setUserEmail(userEmail);
        fileMetadata.setOriginalKeyName(originalKeyName);
        fileMetadata.setNotificationStatus(notificationStatus);

        return fileMetadataRepository.save(fileMetadata);
    }

    /* Finds the FileMetadata document by correlationId and updates only the downloadUrl field if the record exists.*/
    public boolean updateDownloadUrl(String correlationId, String downloadUrl) {
        return fileMetadataRepository.findByCorrelationId(correlationId)
                .map(fileMetadata -> {
                    fileMetadata.setDownloadUrl(downloadUrl);
                    fileMetadataRepository.save(fileMetadata);
                    return true;
                }).orElse(false);
    }

    // Retrieve User email by correlation ID
    public Optional<String> findUserEmailByCorrelationId(String correlationId) {
        return fileMetadataRepository.findByCorrelationId(correlationId)
                .map(FileMetadata::getUserEmail);
    }

    // Update Notification status by correlation ID
    public boolean updateNotificationStatus(String correlationId) {
        return fileMetadataRepository.findByCorrelationId(correlationId)
                .map(fileMetadata -> {
                    fileMetadata.setNotificationStatus("SENT");
                    fileMetadataRepository.save(fileMetadata);
                    return true;
                }).orElse(false);
    }

    // Retrieve FileMetadata collection by correlation ID
    public Optional<FileMetadata> findByCorrelationId(String correlationId) {
        return fileMetadataRepository.findByCorrelationId(correlationId);
    }

    // Retrieve FileMetadata by User email
    public Optional<FileMetadata> findByUserEmail(String userEmail) {
        return fileMetadataRepository.findByUserEmail(userEmail);
    }

    // Retrieve FileMetadata by Original key name
    public Optional<FileMetadata> findByOriginalKeyName(String originalKeyName) {
        return fileMetadataRepository.findByOriginalKeyName(originalKeyName);
    }

    // Retrieve FileMetada by Notification status
    public Optional<FileMetadata> findByNotificationStatus(String notificationStatus) {
        return fileMetadataRepository.findByNotificationStatus(notificationStatus);
    }
}
