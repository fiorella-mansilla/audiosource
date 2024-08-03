package com.audiosource.backend;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.service.S3Service;
import com.audiosource.backend.util.S3Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3AsyncClient s3AsyncClient;

    @Mock
    private S3TransferManager transferManager;

    @Mock
    private FileUpload fileUpload;

    @Mock
    private Logger mockLogger;

    @InjectMocks
    private S3Service s3Service;

    @Captor
    private ArgumentCaptor<UploadFileRequest> uploadFileRequestCaptor;

    @Mock
    private Dotenv dotenv;

    private final String bucketName = "test-bucket";
    private final String key = "test-key";
    private final String contentType = "audio/mp3";
    private final String zipFileName = "test-zip-file.zip";
    private static final String SUB_BUCKET = "separated/";
    private MockedStatic<S3Utils> mockedS3Utils;
    private MockedStatic<Files> mockedFiles;

    @BeforeEach
    void setUp() {
        lenient().when(s3AsyncClient.serviceName()).thenReturn("s3");
    }

    @AfterEach
    void tearDown() {
        // Close static mocks if they were initialized
        if (mockedS3Utils != null) {
            mockedS3Utils.close();
        }
        if (mockedFiles != null) {
            mockedFiles.close();
        }
    }

    @Test
    void testUploadDirectoryAsZipToS3_Success() throws Exception {

        String directoryPath = "testDirectory";
        Path sourceDirectory = Paths.get(directoryPath);

        Path immediateChildDirectory = sourceDirectory.resolve("childDirectory");
        Path zipFilePath = immediateChildDirectory.resolveSibling("childDirectory.zip");

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.getImmediateChildDirectory(sourceDirectory)).thenReturn(immediateChildDirectory);
        mockedS3Utils.when(S3Utils::generateUniqueDirectoryName).thenReturn("uniqueDirectoryName");
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenReturn(zipFilePath);

        // Mock S3 transfer manager and upload process
        S3TransferManager transferManager = mock(S3TransferManager.class);
        FileUpload fileUpload = mock(FileUpload.class);
        CompletableFuture<CompletedFileUpload> future = CompletableFuture.completedFuture(mock(CompletedFileUpload.class));
        when(fileUpload.completionFuture()).thenReturn(future);

        try (MockedStatic<S3TransferManager> mockedTransferManager = mockStatic(S3TransferManager.class)) {
            mockedTransferManager.when(S3TransferManager::builder).thenReturn(mock(S3TransferManager.Builder.class));
            mockedTransferManager.when(() -> transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);

            String presignedUrl = s3Service.uploadDirectoryAsZipToS3(directoryPath, bucketName);

            assertNotNull(presignedUrl);
            mockedS3Utils.verify(() -> S3Utils.getImmediateChildDirectory(sourceDirectory), times(1));
            mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));
            mockedS3Utils.verify(() -> S3Utils.toZipDirectory(any()), times(1));
            mockedTransferManager.verify(() -> transferManager.uploadFile(any(UploadFileRequest.class)), times(1));
        }
    }

    @Test
    void testUploadFileFromLocalToS3_Success() throws Exception {

        Path testFilePath = Paths.get("test.zip");

        // Mock the file existence check and size retrieval
        mockedFiles = mockStatic(Files.class);

        mockedFiles.when(() -> Files.exists(testFilePath)).thenReturn(true);
        mockedFiles.when(() -> Files.size(testFilePath)).thenReturn(1024L);

        // Mock the CompletableFuture for the file upload
        CompletableFuture<CompletedFileUpload> mockFuture = mock(CompletableFuture.class);
        when(mockFuture.join()).thenReturn(mock(CompletedFileUpload.class));
        when(fileUpload.completionFuture()).thenReturn(mockFuture);

        // Setup the mock TransferManager
        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);

        s3Service.uploadFileFromLocalToS3(testFilePath, bucketName);

        verify(transferManager).uploadFile(uploadFileRequestCaptor.capture());
        UploadFileRequest capturedUploadFileRequest = uploadFileRequestCaptor.getValue();
        assertNotNull(capturedUploadFileRequest);
        assertEquals(testFilePath, capturedUploadFileRequest.source());

        verify(mockFuture).join();
        verify(mockLogger).info("Successfully uploaded {} to S3 bucket {}", testFilePath, bucketName);
    }

    @Test
    public void testPrepareDirectoryForUpload() throws IOException {

        Path originalDirectory = Paths.get("testDir");
        Path renamedDirectory = originalDirectory.resolveSibling("renamedDir");
        Path zipPath = Paths.get("renamedDir.zip");

        // Initialize static mocks
        mockedS3Utils = mockStatic(S3Utils.class);
        mockedFiles = mockStatic(Files.class);

        mockedS3Utils.when(S3Utils::generateUniqueDirectoryName).thenReturn("renamedDir");
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any(Path.class))).thenReturn(zipPath);
        mockedFiles.when(() -> Files.move(any(Path.class), any(Path.class))).thenReturn(renamedDirectory);

        Path result = s3Service.prepareDirectoryForUpload(originalDirectory);

        assertEquals(zipPath, result);
        mockedS3Utils.verify(S3Utils::generateUniqueDirectoryName);
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(renamedDirectory));
        mockedFiles.verify(() -> Files.move(originalDirectory, renamedDirectory));

        // Clean up test directory structure
        Files.deleteIfExists(renamedDirectory);
        Files.deleteIfExists(originalDirectory);
    }


    @Test
    void testCreatePresignedGetRequest() throws MalformedURLException {

        Path zipS3File = Paths.get(zipFileName);
        String expectedUrl = "https://example.com/presigned-url";

        // Mock the presigned URL request and response
        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(presignedGetObjectRequest.url()).thenReturn(URI.create(expectedUrl).toURL());

        // Custom matcher for GetObjectPresignRequest
        ArgumentMatcher<GetObjectPresignRequest> requestMatcher = request ->
                request.signatureDuration().equals(Duration.ofMinutes(60)) &&
                        request.getObjectRequest().bucket().equals(bucketName) &&
                        request.getObjectRequest().key().equals(SUB_BUCKET + zipS3File.getFileName().toString());

        // Use the custom matcher in thewhen call
        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        String actualUrl = s3Service.createPresignedGetRequest(bucketName, zipS3File);

        assertEquals(expectedUrl, actualUrl);
        verify(s3Presigner).presignGetObject(argThat(requestMatcher));
    }

    @Test
    void testGetObjectFromBucket_SmallFile_Success() throws IOException {

        String keyName = "test/small-file.mp3";
        String directoryPath = "local/dir/";
        long fileSizeInBytes = 50 * 1024 * 1024; // 50 MB

        byte[] fileData = "test content".getBytes();
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), fileData);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertTrue(result.isPresent(), "The result should be present");
        assertEquals(directoryPath + "small-file.mp3", result.get(), "The file path should match");

        // Verify that getObjectAsBytes was called
        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void testGetObjectFromBucket_LargeFile_Success() throws Exception {

        String keyName = "test/large-file.mp3";
        String directoryPath = "local/dir/";
        long fileSizeInBytes = 150 * 1024 * 1024; // 150MB

        FileDownload fileDownload = mock(FileDownload.class);
        CompletableFuture<CompletedFileDownload> future = CompletableFuture.completedFuture(mock(CompletedFileDownload.class));

        when(transferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(fileDownload);
        when(fileDownload.completionFuture()).thenReturn(future);

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertTrue(result.isPresent());
        verify(transferManager, times(1)).downloadFile(any(DownloadFileRequest.class));
        assertEquals(directoryPath + "large-file.mp3", result.get());
    }

    @Test
    void testGetObjectFromBucket_S3Exception() {

        String keyName = "test/key";
        String directoryPath = "local/dir/";
        long fileSizeInBytes = 50 * 1024 * 1024; // 50 MB

        // Create a mock AwsErrorDetails
        AwsErrorDetails awsErrorDetails = mock(AwsErrorDetails.class);
        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");

        // Create a mock S3Exception
        S3Exception s3Exception = mock(S3Exception.class);
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(s3Exception);

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent());

        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void testCreatePresignedPutRequest() throws MalformedURLException {

        String expectedUrl = "https://test-bucket.s3.amazonaws.com/test-key";

        // Mock dotenv to return the bucket name
        when(dotenv.get("S3_BUCKET")).thenReturn(bucketName);

        // Mock PresignedPutObjectRequest
        PresignedPutObjectRequest presignedPutObjectRequest = mock(PresignedPutObjectRequest.class);
        when(presignedPutObjectRequest.url()).thenReturn(URI.create(expectedUrl).toURL());

        // Mock SdkHttpRequest
        SdkHttpRequest sdkHttpRequest = mock(SdkHttpRequest.class);
        when(sdkHttpRequest.method()).thenReturn(SdkHttpMethod.valueOf("PUT"));

        when(presignedPutObjectRequest.httpRequest()).thenReturn(sdkHttpRequest);

        // Custom matcher for PutObjectPresignRequest
        ArgumentMatcher<PutObjectPresignRequest> requestMatcher = request -> {
            PutObjectRequest putRequest = request.putObjectRequest();
            return request.signatureDuration().equals(Duration.ofMinutes(10)) &&
                    putRequest.bucket().equals(bucketName) &&
                    putRequest.key().equals(key) &&
                    putRequest.contentType().equals(contentType);
        };

        // Configure the mock to return the PresignedPutObjectRequest
        when(s3Presigner.presignPutObject(argThat(requestMatcher)))
                .thenReturn(presignedPutObjectRequest);

        String result = s3Service.createPresignedPutRequest(key, contentType);

        assertEquals(expectedUrl, result);
        verify(s3Presigner).presignPutObject(argThat(requestMatcher));
        verify(dotenv).get("S3_BUCKET");
    }

    @Test
    void testListObjects() {

        S3Object s3Object = S3TestUtils.createTestS3Object(123L, Instant.now());

        ListObjectsV2Response listObjectsResponse = S3TestUtils.createListObjectsResponse(List.of(s3Object));

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listObjectsResponse);

        List<S3ObjectDto> result = s3Service.listObjects(bucketName);

        String expectedSizeMB = S3TestUtils.formatSize(s3Object.size());

        assertEquals(1, result.size());
        assertEquals("test-file.mp3", result.get(0).getKey());
        assertEquals(expectedSizeMB, result.get(0).getSizeMB());
    }

    @Test
    void testToDto() {

        long sizeInBytes = 123456L;
        Instant lastModified = Instant.parse("2024-07-23T12:00:00Z");

        S3Object s3Object = S3TestUtils.createTestS3Object(sizeInBytes, lastModified);
        String expectedSizeMB = S3TestUtils.formatSize(sizeInBytes);
        String expectedLastModified = S3TestUtils.formatLastModified(lastModified);

        mockedS3Utils = mockStatic(S3Utils.class);

        // Mock the static method for S3Utils.formatLastModified
        mockedS3Utils.when(() -> S3Utils.formatLastModified(lastModified))
                .thenReturn(expectedLastModified);

        S3ObjectDto resultDto = s3Service.toDto(s3Object);

        assertEquals("test-file.mp3", resultDto.getKey());
        assertEquals(expectedSizeMB, resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }
}
