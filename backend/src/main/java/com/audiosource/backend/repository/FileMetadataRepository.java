package com.audiosource.backend.repository;

import com.audiosource.backend.entities.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {

    Optional<FileMetadata> findByCorrelationId(String correlationId);

    Optional<FileMetadata> findByUserEmail(String userEmail);

    Optional<FileMetadata> findByOriginalKeyName(String originalKeyName);

    Optional<FileMetadata> findByNotificationStatus(String notificationStatus);
}
