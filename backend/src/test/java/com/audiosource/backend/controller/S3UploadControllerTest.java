package com.audiosource.backend.controller;

import com.audiosource.backend.dto.ClientUploadRequest;
import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.enums.OutputFormat;
import com.audiosource.backend.enums.SeparationType;
import com.audiosource.backend.exception.S3UploadException;
import com.audiosource.backend.messaging.producer.AudioFilesProducerService;
import com.audiosource.backend.service.metadata.FileMetadataService;
import com.audiosource.backend.service.s3.S3UploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class S3UploadControllerTest {

    @Mock
    private S3UploadService s3UploadService;

    @Mock
    private AudioFilesProducerService audioFilesProducerService;

    @Mock
    private FileMetadataService fileMetadataService;

    @InjectMocks
    private S3UploadController s3UploadController;

    @Test
    @DisplayName("Should return OK and pre-signed URL when inputs are valid")
    void createPresignedPutRequest_ValidInput_ReturnsOk() {

        Map<String, String> request = new HashMap<>();
        request.put("key", "test-key");
        request.put("content_type", "audio/mpeg");

        when(s3UploadService.createPresignedPutRequest("test-key", "audio/mpeg"))
                .thenReturn("http://presigned-url");

        ResponseEntity<?> response = s3UploadController.createPresignedPutRequest(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("http://presigned-url", response.getBody());
        verify(s3UploadService, times(1)).createPresignedPutRequest("test-key", "audio/mpeg");
    }

    @Test
    @DisplayName("Should return BadRequest when input is invalid")
    void createPresignedPutRequest_InvalidInput_ReturnsBadRequest() {

        Map<String, String> request = new HashMap<>(); // Empty request to trigger failure

        ResponseEntity<?> response = s3UploadController.createPresignedPutRequest(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid input: Missing required fields", response.getBody());
    }

    @Test
    @DisplayName("Should return InternalServerError when service fails")
    void createPresignedPutRequest_ServiceThrowsException_ReturnsInternalServerError() {
        Map<String, String> request = new HashMap<>();
        request.put("key", "test-key");
        request.put("content_type", "audio/mpeg");

        when(s3UploadService.createPresignedPutRequest(any(), any()))
                .thenThrow(new RuntimeException("Service failure"));

        ResponseEntity<?> response = s3UploadController.createPresignedPutRequest(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Failed to generate pre-signed URL: Service failure", response.getBody());
    }

    @Test
    @DisplayName("Should return OK when client upload notification is successful")
    void notifyClientUpload_ValidRequest_ReturnsOk() {

        ClientUploadRequest request = new ClientUploadRequest(
                "test-key", 1024L, SeparationType.VOCAL_REMOVER, OutputFormat.MP3, "user@example.com"
        );

        // Create a mock FileMetadata object to return
        FileMetadata mockMetadata = new FileMetadata();
        mockMetadata.setCorrelationId("dummy-correlation-id");
        mockMetadata.setUserEmail("user@example.com");
        mockMetadata.setOriginalKeyName("test-key");
        mockMetadata.setNotificationStatus("PENDING");

        when(fileMetadataService.saveInitialMetadata(any(), any(), any(), any()))
                .thenReturn(mockMetadata);

        doNothing().when(audioFilesProducerService).publishClientUploadNotification(any());

        ResponseEntity<String> response = s3UploadController.notifyClientUpload(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Notification sent successfully", response.getBody());

        verify(fileMetadataService, times(1)).saveInitialMetadata(any(), any(), any(), any());
        verify(audioFilesProducerService, times(1)).publishClientUploadNotification(any());
    }

    @Test
    @DisplayName("Should return BadRequest when client upload request is missing required fields")
    void notifyClientUpload_InvalidRequest_ReturnsBadRequest() {

        ClientUploadRequest request = new ClientUploadRequest(
                "test-key", 1024L, null, OutputFormat.MP3, null  // SeparationType and email are missing
        );

        ResponseEntity<String> response = s3UploadController.notifyClientUpload(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid input: Missing required fields", response.getBody());
    }

    @Test
    @DisplayName("Should return OK with pre-signed URL on successful directory upload")
    void uploadProcessedFilesToS3_ValidInput_ReturnsOk() throws S3UploadException {

        when(s3UploadService.uploadDirectoryAsZipToS3("path/to/dir", "bucket-name"))
                .thenReturn("http://presigned-url");

        ResponseEntity<String> response = s3UploadController.uploadProcessedFilesToS3("path/to/dir", "bucket-name");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Successful upload. Pre-signed URL: http://presigned-url", response.getBody());
        verify(s3UploadService, times(1)).uploadDirectoryAsZipToS3("path/to/dir", "bucket-name");
    }

    @Test
    @DisplayName("Should return BadRequest when directory upload input is invalid")
    void uploadProcessedFilesToS3_InvalidInput_ReturnsBadRequest() throws S3UploadException {
        when(s3UploadService.uploadDirectoryAsZipToS3(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid input"));

        ResponseEntity<String> response = s3UploadController.uploadProcessedFilesToS3("invalid/dir", "bucket-name");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid input: Invalid input", response.getBody());
    }

    @Test
    @DisplayName("Should return InternalServerError when S3 upload fails")
    void uploadProcessedFilesToS3_S3UploadException_ReturnsInternalServerError() throws S3UploadException {

        when(s3UploadService.uploadDirectoryAsZipToS3(any(), any()))
                .thenThrow(new S3UploadException("S3 upload failed"));

        ResponseEntity<String> response = s3UploadController.uploadProcessedFilesToS3("path/to/dir", "bucket-name");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Upload failed: S3 upload failed", response.getBody());
    }
}
