package com.audiosource.backend.service.metadata;

import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileMetadataServiceTest {
    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @InjectMocks
    private FileMetadataService fileMetadataService;

    private FileMetadata fileMetadata;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fileMetadata = new FileMetadata();
        fileMetadata.setCorrelationId("test-correlation-id");
        fileMetadata.setUserEmail("test@example.com");
        fileMetadata.setOriginalKeyName("originalFile.txt");
        fileMetadata.setNotificationStatus("PENDING");
    }

    /* Test cases for saveInitialMetadata() method */

    @Test
    void saveInitialMetadata_ShouldSaveFileMetadata() {

        when(fileMetadataRepository.save(any(FileMetadata.class))).thenReturn(fileMetadata);

        FileMetadata result = fileMetadataService.saveInitialMetadata("test-correlation-id", "test@example.com", "originalFile.txt", "PENDING");

        assertNotNull(result);
        assertEquals("test-correlation-id", result.getCorrelationId());
        assertEquals("test@example.com", result.getUserEmail());
        assertEquals("originalFile.txt", result.getOriginalKeyName());
        assertEquals("PENDING", result.getNotificationStatus());

        verify(fileMetadataRepository).save(any(FileMetadata.class));
    }

    @Test
    void saveInitialMetadata_ShouldThrowException_WhenParametersAreNull() {

        String correlationId = null;
        String userEmail = null;
        String originalKeyName = null;
        String notificationStatus = null;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            fileMetadataService.saveInitialMetadata(correlationId, userEmail, originalKeyName, notificationStatus);
        });

        assertEquals("Parameters cannot be null", thrown.getMessage());
    }

    @Test
    void saveInitialMetadata_ShouldThrowException_WhenParametersAreEmptyStrings() {

        String correlationId = "";
        String userEmail = "";
        String originalKeyName = "";
        String notificationStatus = "";

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            fileMetadataService.saveInitialMetadata(correlationId, userEmail, originalKeyName, notificationStatus);
        });

        assertEquals("Parameters cannot be empty", thrown.getMessage());
    }

    /* Test cases for updateDownloadUrl() method */

    @Test
    void updateDownloadUrl_ShouldUpdateAndReturnTrue_WhenFileMetadataExists() {

        String newDownloadUrl = "http://example.com/download";
        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.of(fileMetadata));

        boolean result = fileMetadataService.updateDownloadUrl("test-correlation-id", newDownloadUrl);

        assertTrue(result);
        assertEquals(newDownloadUrl, fileMetadata.getDownloadUrl());
        verify(fileMetadataRepository).save(fileMetadata);
    }

    @Test
    void updateDownloadUrl_ShouldReturnFalse_WhenFileMetadataDoesNotExist() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.empty());

        boolean result = fileMetadataService.updateDownloadUrl("test-correlation-id", "http://example.com/download");

        assertFalse(result);
        verify(fileMetadataRepository, never()).save(any(FileMetadata.class));
    }

    /* Test cases for findUserEmailByCorrelationId() method */

    @Test
    void findUserEmailByCorrelationId_ShouldReturnUserEmail_WhenFileMetadataExists() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.of(fileMetadata));

        Optional<String> result = fileMetadataService.findUserEmailByCorrelationId("test-correlation-id");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get());
    }

    @Test
    void findUserEmailByCorrelationId_ShouldReturnEmpty_WhenFileMetadataDoesNotExist() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.empty());

        Optional<String> result = fileMetadataService.findUserEmailByCorrelationId("test-correlation-id");

        assertFalse(result.isPresent());
    }

    /* Test cases for updateNotificationStatus() method */

    @Test
    void updateNotificationStatus_ShouldUpdateAndReturnTrue_WhenFileMetadataExists() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.of(fileMetadata));

        boolean result = fileMetadataService.updateNotificationStatus("test-correlation-id");

        assertTrue(result);
        assertEquals("SENT", fileMetadata.getNotificationStatus());
        verify(fileMetadataRepository).save(fileMetadata);
    }

    @Test
    void updateNotificationStatus_ShouldReturnFalse_WhenFileMetadataDoesNotExist() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.empty());

        boolean result = fileMetadataService.updateNotificationStatus("test-correlation-id");

        assertFalse(result);
        verify(fileMetadataRepository, never()).save(any(FileMetadata.class));
    }

    /* Test cases for findByCorrelationId() method */

    @Test
    void findByCorrelationId_ShouldReturnFileMetadata_WhenExists() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.of(fileMetadata));

        Optional<FileMetadata> result = fileMetadataService.findByCorrelationId("test-correlation-id");

        assertTrue(result.isPresent());
        assertEquals(fileMetadata, result.get());
    }

    @Test
    void findByCorrelationId_ShouldReturnEmpty_WhenNotExists() {

        when(fileMetadataRepository.findByCorrelationId("test-correlation-id")).thenReturn(Optional.empty());

        Optional<FileMetadata> result = fileMetadataService.findByCorrelationId("test-correlation-id");

        assertFalse(result.isPresent());
    }
}
