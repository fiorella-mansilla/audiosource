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
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setCorrelationId(correlationId);
        fileMetadata.setUserEmail(userEmail);
        fileMetadata.setOriginalKeyName(originalKeyName);
        fileMetadata.setNotificationStatus(notificationStatus);

        return fileMetadataRepository.save(fileMetadata);
    }

    /* Finds the FileMetadata document by correlationId and updates only the downloadUrl field if the record exists.*/
    public boolean updateDownloadUrl(String correlationId, String downloadUrl) {
        Optional<FileMetadata> optionalFileMetadata = fileMetadataRepository.findByCorrelationId(correlationId);

        if (optionalFileMetadata.isPresent()) {
            FileMetadata fileMetadata = optionalFileMetadata.get();
            fileMetadata.setDownloadUrl(downloadUrl);

            fileMetadataRepository.save(fileMetadata);
            return true;
        }

        return false; // Return false if no FileMetadata is found for the correlationId
    }

    // Retrieve FileMetadata by correlation ID
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
