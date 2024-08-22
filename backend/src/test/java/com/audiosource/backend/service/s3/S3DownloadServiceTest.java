package com.audiosource.backend.service.s3;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.util.S3Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3DownloadServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3TransferManager s3TransferManager;

    @Mock
    private AwsErrorDetails awsErrorDetails;

    @Mock
    private S3Exception s3Exception;

    @InjectMocks
    private S3DownloadService s3DownloadService;

    private final String bucketName = "test-bucket";
    private final String keyName = "test-file.mp3";
    private Path tempDirectory;
    private MockedStatic<S3Utils> mockedS3Utils;

    @BeforeEach
    void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("_temp");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteDirectoryRecursively(tempDirectory);

        // Close static mocks if they were initialized
        if (mockedS3Utils != null) {
            mockedS3Utils.close();
        }
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                System.err.println("Unable to delete file: " + file.getAbsolutePath());
                            }
                        });
            }
        }
    }

    @Test
    void getObjectFromBucket_ValidSmallFile_ShouldDownloadSuccessfully() {

        String directoryPath = tempDirectory.toString();

        long fileSizeInBytes = 50 * 1024; // 50 MB

        byte[] fileData = new byte[(int) fileSizeInBytes];
        Arrays.fill(fileData, (byte) 'a'); // Fill with dummy data

        // Create a ResponseBytes object with the file data
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), fileData);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertTrue(result.isPresent(), "The result should be present");
        assertEquals(directoryPath + keyName, result.get(), "The file path should match");

        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void getObjectFromBucket_ValidLargeFile_ShouldDownloadSuccessfullyUsingTransferManager() {

        String directoryPath = tempDirectory.toString();
        String expectedFilePath = directoryPath + keyName;

        long fileSizeInBytes = 150 * 1024 * 1024; // 150MB
        byte[] fileData = new byte[(int) fileSizeInBytes];
        Arrays.fill(fileData, (byte) 'a'); // Fill with dummy data

        FileDownload fileDownload = mock(FileDownload.class);

        // Set up the response with the expected file size
        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentLength(fileSizeInBytes)
                .build();

        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(getObjectResponse, fileData);

        CompletedFileDownload completedFileDownload = CompletedFileDownload.builder()
                .response(getObjectResponse)
                .build();

        CompletableFuture<CompletedFileDownload> future = CompletableFuture.completedFuture(completedFileDownload);

        when(s3TransferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(fileDownload);
        when(fileDownload.completionFuture()).thenReturn(future);

        Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertAll("Verifying large file download",
                () -> assertTrue(result.isPresent(), "Result should be present"),
                () -> assertEquals(expectedFilePath, result.get(), "The file path should match the expected value"),
                () -> verify(s3TransferManager, times(1)).downloadFile(any(DownloadFileRequest.class))
        );

        ArgumentCaptor<DownloadFileRequest> downloadFileRequestCaptor = ArgumentCaptor.forClass(DownloadFileRequest.class);
        verify(s3TransferManager).downloadFile(downloadFileRequestCaptor.capture());
        DownloadFileRequest capturedRequest = downloadFileRequestCaptor.getValue();

        assertAll("downloadFileRequest",
                () -> assertEquals(bucketName, capturedRequest.getObjectRequest().bucket(), "Bucket name should match"),
                () -> assertEquals(keyName, capturedRequest.getObjectRequest().key(), "Key name should match"),
                () -> assertEquals(expectedFilePath, capturedRequest.destination().toString(), "Destination directory should match")
        );
    }

    @Test
    void getObjectFromBucket_IOExceptionDuringSmallFileDownload_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
        long fileSizeInBytes = 50 * 1024 * 1024; // 50 MB

        byte[] fileData = new byte[(int) fileSizeInBytes];
        Arrays.fill(fileData, (byte) 'a');
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), fileData);

        // Mock the S3 client to return the ResponseBytes
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        // Simulate IOException when writing to the output stream
        try (MockedConstruction<FileOutputStream> mocked = mockConstruction(FileOutputStream.class,
                (mock, context) -> {
                    doThrow(new IOException("Simulated IOException during file writing")).when(mock).write(any(byte[].class));
                })) {

            Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

            assertFalse(result.isPresent(), "Result should be empty due to IOException during download");

            verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
        }
    }

    @Test
    void getObjectFromBucket_IOExceptionDuringLargeFileDownload_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
        long fileSizeInBytes = 150 * 1024 * 1024; // 150 MB

        // Mock the S3TransferManager's FileDownload and CompletableFuture
        FileDownload fileDownload = mock(FileDownload.class);

        CompletableFuture<CompletedFileDownload> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IOException("Simulated IOException during large file download"));

        when(s3TransferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(fileDownload);
        when(fileDownload.completionFuture()).thenReturn(failedFuture);

        Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent(), "Result should be empty due to IOException during large file download");

        verify(s3TransferManager, times(1)).downloadFile(any(DownloadFileRequest.class));
    }

    @Test
    void getObjectFromBucket_S3ExceptionDuringDownload_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
        long fileSizeInBytes = 50 * 1024 * 1024; // 50 MB

        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(s3Exception);

        Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent());

        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void getObjectFromBucket_GeneralException_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
        long fileSizeInBytes = 50 * 1024 * 1024; // 50 MB

        // Mock a general exception during the small file download process
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(new RuntimeException("Simulated general exception"));

        Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent(), "Result should be empty due to general exception");

        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void getObjectFromBucket_GeneralExceptionDuringLargeFileDownload_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
        long fileSizeInBytes = 150 * 1024 * 1024; // 150 MB

        // Mock the S3TransferManager's FileDownload and CompletableFuture
        FileDownload fileDownload = mock(FileDownload.class);

        // Simulate a general exception during the download process
        CompletableFuture<CompletedFileDownload> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Simulated general exception during large file download"));

        when(s3TransferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(fileDownload);
        when(fileDownload.completionFuture()).thenReturn(failedFuture);

        Optional<String> result = s3DownloadService.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent(), "Result should be empty due to general exception during large file download");

        verify(s3TransferManager, times(1)).downloadFile(any(DownloadFileRequest.class));
    }

    @Test
    void listObjects_ValidBucket_ShouldReturnListOfS3ObjectDto() {

        S3Object s3Object = S3TestUtils.createTestS3Object(123L, Instant.now());

        ListObjectsV2Response listObjectsResponse = S3TestUtils.createListObjectsResponse(List.of(s3Object));

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listObjectsResponse);

        List<S3ObjectDto> result = s3DownloadService.listObjects(bucketName);

        String expectedSizeMB = S3TestUtils.formatSize(s3Object.size());

        assertEquals(1, result.size());
        assertEquals(keyName, result.get(0).getKey());
        assertEquals(expectedSizeMB, result.get(0).getSizeMB());
    }

    @Test
    void listObjects_EmptyBucket_ShouldReturnEmptyList() {

        ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
                .contents(Collections.emptyList()) // Simulating empty bucket
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(emptyResponse);

        List<S3ObjectDto> result = s3DownloadService.listObjects(bucketName);

        assertTrue(result.isEmpty(), "Expected an empty list when the bucket is empty");
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void listObjects_S3ExceptionDuringListing_ShouldHandleException() {

        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(s3Exception);

        List<S3ObjectDto> result = s3DownloadService.listObjects(bucketName);

        assertTrue(result.isEmpty(), "Expected an empty list when an S3 exception is thrown");

        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void toDto_ValidS3Object_ShouldReturnCorrectS3ObjectDto() {

        long sizeInBytes = 123456L;
        Instant lastModified = Instant.parse("2024-07-23T12:00:00Z");

        S3Object s3Object = S3TestUtils.createTestS3Object(sizeInBytes, lastModified);
        String expectedSizeMB = S3TestUtils.formatSize(sizeInBytes);
        String expectedLastModified = S3TestUtils.formatLastModified(lastModified);

        mockedS3Utils = mockStatic(S3Utils.class);

        // Mock the static method for S3Utils.formatLastModified
        mockedS3Utils.when(() -> S3Utils.formatLastModified(lastModified))
                .thenReturn(expectedLastModified);

        S3ObjectDto resultDto = s3DownloadService.toDto(s3Object);

        assertEquals(keyName, resultDto.getKey());
        assertEquals(expectedSizeMB, resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }

    @Test
    void toDto_SmallFileSize_ShouldFormatSizeAndDateCorrectly() {

        long smallSizeInBytes = 1L; // Small size in bytes
        Instant lastModified = Instant.parse("2024-01-01T00:00:00Z");

        S3Object s3Object = S3TestUtils.createTestS3Object(smallSizeInBytes, lastModified);
        String expectedSizeMB = S3TestUtils.formatSize(smallSizeInBytes);
        String expectedLastModified = S3TestUtils.formatLastModified(lastModified);

        mockedS3Utils = mockStatic(S3Utils.class);

        mockedS3Utils.when(() -> S3Utils.formatLastModified(lastModified))
                .thenReturn(expectedLastModified);

        S3ObjectDto resultDto = s3DownloadService.toDto(s3Object);

        assertEquals(keyName, resultDto.getKey());
        assertEquals(expectedSizeMB, resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }

    @Test
    void toDto_LargeFileSize_ShouldFormatSizeAndDateCorrectly() {

        long largeSizeInBytes = 123456789L; // Large size in bytes
        Instant lastModified = Instant.parse("2024-12-31T23:59:59Z");

        S3Object s3Object = S3TestUtils.createTestS3Object(largeSizeInBytes, lastModified);
        String expectedSizeMB = S3TestUtils.formatSize(largeSizeInBytes);
        String expectedLastModified = S3TestUtils.formatLastModified(lastModified);

        mockedS3Utils = mockStatic(S3Utils.class);

        mockedS3Utils.when(() -> S3Utils.formatLastModified(lastModified))
                .thenReturn(expectedLastModified);

        S3ObjectDto resultDto = s3DownloadService.toDto(s3Object);

        assertEquals(keyName, resultDto.getKey());
        assertEquals(expectedSizeMB, resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }

    @Test
    void toDto_BoundaryDate_ShouldFormatSizeAndDateCorrectly() {

        long sizeInBytes = 123456L;
        Instant boundaryDate = Instant.parse("2024-06-30T12:00:00Z");

        S3Object s3Object = S3TestUtils.createTestS3Object(sizeInBytes, boundaryDate);
        String expectedLastModified = S3TestUtils.formatLastModified(boundaryDate);

        mockedS3Utils = mockStatic(S3Utils.class);

        mockedS3Utils.when(() -> S3Utils.formatLastModified(boundaryDate))
                .thenReturn(expectedLastModified);

        S3ObjectDto resultDto = s3DownloadService.toDto(s3Object);

        assertEquals(keyName, resultDto.getKey());
        assertEquals(S3TestUtils.formatSize(sizeInBytes), resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }
}
