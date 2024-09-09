package com.audiosource.backend.service.metadata;

import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/* Handles interactions with FileMetadata collection from MongoDB*/
@Service
public class FileMetadataService {
    private final FileMetadataRepository fileMetadataRepository;

    @Autowired
    public FileMetadataService(FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
    }

    // Save correlationId and userEmail in the FileMetadata collection
    public FileMetadata saveInitialMetadata(String correlationId, String userEmail, String originalKeyName, String notificationStatus) {

        // Create a new FileMetadata object with the initial data
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setCorrelationId(correlationId);
        fileMetadata.setUserEmail(userEmail);
        fileMetadata.setOriginalKeyName(originalKeyName);
        fileMetadata.setNotificationStatus(notificationStatus);

        // Save the FileMetadata object
        return fileMetadataRepository.save(fileMetadata);
    }

    // Save originalKeyName and notificationStatus in the FileMetadata collection
    public FileMetadata saveOriginalKeyNameAndNotificationStatus(String originalKeyName, String notificationStatus) {
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setOriginalKeyName(originalKeyName);
        fileMetadata.setNotificationStatus(notificationStatus);
        return fileMetadataRepository.save(fileMetadata);  // Save the entire FileMetadata object
    }

    // Retrieve FileMetadata by correlation ID
    public Optional<FileMetadata> findByCorrelationId(String correlationId) {
        return fileMetadataRepository.findByCorrelationId(correlationId);
    }

    // Retrieve FileMetadata by User email
    public Optional<FileMetadata> findByUserEmail(String userEmail) {
        return fileMetadataRepository.findByUserEmail(userEmail);
    }

    // Retrieve FileMetadata by original key name
    public Optional<FileMetadata> findByOriginalKeyName(String originalKeyName) {
        return fileMetadataRepository.findByOriginalKeyName(originalKeyName);
    }

    // Retrieve FileMetada by notification status
    public Optional<FileMetadata> findByNotificationStatus(String notificationStatus) {
        return fileMetadataRepository.findByNotificationStatus(notificationStatus);
    }

    // Update existing FileMetadata
    public FileMetadata updateFileMetadata(FileMetadata updatedMetadata) {
        return fileMetadataRepository.save(updatedMetadata);
    }
}
