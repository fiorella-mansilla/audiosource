package com.audiosource.backend.repository;

import com.audiosource.backend.entities.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {

    // Find by correlation ID
    Optional<FileMetadata> findByCorrelationId(String correlationId);

    // Find by user email
    Optional<FileMetadata> findByUserEmail(String userEmail);

    // Find by original key name
    Optional<FileMetadata> findByOriginalKeyName(String originalKeyName);

    // Find by notification status
    Optional<FileMetadata> findByNotificationStatus(String notificationStatus);
}
