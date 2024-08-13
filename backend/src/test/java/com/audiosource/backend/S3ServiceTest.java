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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3TransferManager s3TransferManager;

    @Mock
    private Logger mockLogger;

    @InjectMocks
    private S3Service s3Service;

    @Mock
    private Dotenv dotenv;

    private final String bucketName = "test-bucket";
    private final String keyName = "test-key";
    private final String contentType = "audio/mp3";
    private final String expectedPresignedUrl = "https://test-bucket.s3.amazonaws.com/test-key";
    private static final String SUB_BUCKET = "separated/";
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

    private Path createTestFile(String fileName) throws IOException {
        Path file = tempDirectory.resolve(fileName);
        Files.createFile(file);
        return file;
    }

    @Test
    void uploadDirectoryAsZipToS3_ValidInput_ShouldReturnPresignedURL() throws Exception {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        Path immediateChildDirectory = sourceDirectory.resolve("childDirectory");
        Files.createDirectory(immediateChildDirectory);

        Path zipFilePath = immediateChildDirectory.resolveSibling("childDirectory.zip");

        FileUpload fileUpload = mock(FileUpload.class);

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.getImmediateChildDirectory(sourceDirectory)).thenReturn(immediateChildDirectory);
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenReturn(zipFilePath);
        mockedS3Utils.when(S3Utils::generateUniqueDirectoryName).thenReturn("renamedChildDirectory");

        CompletedFileUpload completedFileUpload = CompletedFileUpload.builder()
                .response(PutObjectResponse.builder().build())
                .build();

        CompletableFuture<CompletedFileUpload> future = CompletableFuture.completedFuture(completedFileUpload);

        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(future);

        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(presignedGetObjectRequest.url()).thenReturn(URI.create(expectedPresignedUrl).toURL());

        // Custom matcher for GetObjectPresignRequest
        ArgumentMatcher<GetObjectPresignRequest> requestMatcher = request ->
                request.signatureDuration().equals(Duration.ofMinutes(60)) &&
                        request.getObjectRequest().bucket().equals(bucketName) &&
                        request.getObjectRequest().key().equals(SUB_BUCKET + zipFilePath.getFileName().toString());

        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        String presignedUrl = s3Service.uploadDirectoryAsZipToS3(sourceDirectory.toString(), bucketName);

        assertEquals(expectedPresignedUrl, presignedUrl);
        mockedS3Utils.verify(() -> S3Utils.getImmediateChildDirectory(sourceDirectory), times(1));
        mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(any()), times(1));

        verify(s3TransferManager).uploadFile(any(UploadFileRequest.class));

        ArgumentCaptor<UploadFileRequest> uploadFileRequestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(s3TransferManager).uploadFile(uploadFileRequestCaptor.capture());
        UploadFileRequest capturedRequest = uploadFileRequestCaptor.getValue();

        assertAll("uploadFileRequest",
                () -> assertEquals(zipFilePath, capturedRequest.source(), "The source should match the zip file path"),
                () -> assertEquals(bucketName, capturedRequest.putObjectRequest().bucket(), "The bucket name should match"),
                () -> assertEquals(SUB_BUCKET + zipFilePath.getFileName().toString(),
                        capturedRequest.putObjectRequest().key(), "The key should match the expected S3 key")
        );
    }

    @Test
    void uploadFileFromLocalToS3_ValidInput_ShouldUploadSuccessfully() throws Exception {

        Path zipS3File = createTestFile("test.zip");

        FileUpload fileUpload = mock(FileUpload.class);

        CompletedFileUpload completedFileUpload = CompletedFileUpload.builder()
                .response(PutObjectResponse.builder().build())
                .build();

        CompletableFuture<CompletedFileUpload> future = CompletableFuture.completedFuture(completedFileUpload);

        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(future);

        s3Service.uploadFileFromLocalToS3(zipS3File, bucketName);

        ArgumentCaptor<UploadFileRequest> uploadFileRequestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(s3TransferManager).uploadFile(uploadFileRequestCaptor.capture());
        UploadFileRequest capturedUploadFileRequest = uploadFileRequestCaptor.getValue();

        assertAll("uploadFileRequest",
                () -> assertNotNull(capturedUploadFileRequest, "UploadFileRequest should not be null"),

                () -> assertEquals(zipS3File, capturedUploadFileRequest.source(), "The file source should match the provided zipS3File path"),
                () -> assertEquals(bucketName, capturedUploadFileRequest.putObjectRequest().bucket(), "The bucket name should match the provided bucketName"),
                () -> assertEquals(SUB_BUCKET + zipS3File.getFileName().toString(),
                        capturedUploadFileRequest.putObjectRequest().key(),
                        "The key should match the SUB_BUCKET plus the file name")
        );

//        verify(mockLogger, times(1)).info("Successfully uploaded {} to S3 bucket {}", zipS3File, bucketName);
    }

    @Test
    void uploadFileFromLocalToS3_TransferManagerException_ShouldHandleException() throws IOException {

        Path zipS3File = createTestFile("test.zip");

        FileUpload fileUpload = mock(FileUpload.class);
        CompletableFuture<CompletedFileUpload> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Simulated S3 Transfer Exception"));

        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(future);

        // Verify the exception is handled appropriately
        Exception exception = assertThrows(Exception.class, () -> {
            s3Service.uploadFileFromLocalToS3(zipS3File, bucketName);
        });

        assertEquals("Simulated S3 Transfer Exception", exception.getCause().getMessage());

        verify(mockLogger, never()).info("Successfully uploaded {} to S3 bucket {}", zipS3File, bucketName);
    }

    @Test
    public void prepareDirectoryForUpload_ValidDirectory_ShouldReturnZippedPath() throws IOException {

        Path originalDirectory = tempDirectory.resolve("testDir");
        Files.createDirectory(originalDirectory);

        Path renamedDirectory = originalDirectory.resolveSibling("renamedDir");
        Path zipPath = renamedDirectory.resolveSibling("renamedDir.zip");

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any(Path.class))).thenReturn(zipPath);
        mockedS3Utils.when(S3Utils::generateUniqueDirectoryName).thenReturn("renamedDir");

        Path result = s3Service.prepareDirectoryForUpload(originalDirectory);

        assertEquals(zipPath, result);
        mockedS3Utils.verify(S3Utils::generateUniqueDirectoryName);
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(renamedDirectory));

        assertFalse(Files.exists(originalDirectory), "The original directory should be renamed.");
        assertTrue(Files.exists(renamedDirectory), "The renamed directory should exist.");
    }

    @Test
    void createPresignedGetRequest_ValidInput_ShouldReturnPresignedURL() throws MalformedURLException {

        Path zipS3File = Paths.get("test-zip-file.zip");

        // Mock the presigned URL request and response
        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(presignedGetObjectRequest.url()).thenReturn(URI.create(expectedPresignedUrl).toURL());

        // Custom matcher for GetObjectPresignRequest
        ArgumentMatcher<GetObjectPresignRequest> requestMatcher = request ->
                request.signatureDuration().equals(Duration.ofMinutes(60)) &&
                        request.getObjectRequest().bucket().equals(bucketName) &&
                        request.getObjectRequest().key().equals(SUB_BUCKET + zipS3File.getFileName().toString());

        // Use the custom matcher in thewhen call
        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        String actualUrl = s3Service.createPresignedGetRequest(bucketName, zipS3File);

        assertEquals(expectedPresignedUrl, actualUrl);
        verify(s3Presigner).presignGetObject(argThat(requestMatcher));
    }

    @Test
    void getObjectFromBucket_ValidSmallFile_ShouldDownloadSuccessfully() throws IOException {

        String directoryPath = tempDirectory.toString();

        long fileSizeInBytes = 50 * 1024; // 50 MB

        byte[] fileData = new byte[(int) fileSizeInBytes];
        Arrays.fill(fileData, (byte) 'a'); // Fill with dummy data

        // Create a ResponseBytes object with the file data
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), fileData);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertTrue(result.isPresent(), "The result should be present");
        assertEquals(directoryPath + keyName, result.get(), "The file path should match");

        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void getObjectFromBucket_ValidLargeFile_ShouldDownloadSuccessfullyUsingTransferManager() throws Exception {

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

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

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
    void getObjectFromBucket_S3ExceptionDuringDownload_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
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
    void createPresignedPutRequest_ValidInput_ShouldReturnPresignedURL() throws MalformedURLException {

        when(dotenv.get("S3_BUCKET")).thenReturn(bucketName);

        // Mock PresignedPutObjectRequest
        PresignedPutObjectRequest presignedPutObjectRequest = mock(PresignedPutObjectRequest.class);
        when(presignedPutObjectRequest.url()).thenReturn(URI.create(expectedPresignedUrl).toURL());

        // Mock SdkHttpRequest
        SdkHttpRequest sdkHttpRequest = mock(SdkHttpRequest.class);
        when(sdkHttpRequest.method()).thenReturn(SdkHttpMethod.valueOf("PUT"));

        when(presignedPutObjectRequest.httpRequest()).thenReturn(sdkHttpRequest);

        // Custom matcher for PutObjectPresignRequest
        ArgumentMatcher<PutObjectPresignRequest> requestMatcher = request -> {
            PutObjectRequest putRequest = request.putObjectRequest();
            return request.signatureDuration().equals(Duration.ofMinutes(10)) &&
                    putRequest.bucket().equals(bucketName) &&
                    putRequest.key().equals(keyName) &&
                    putRequest.contentType().equals(contentType);
        };

        // Configure the mock to return the PresignedPutObjectRequest
        when(s3Presigner.presignPutObject(argThat(requestMatcher)))
                .thenReturn(presignedPutObjectRequest);

        String result = s3Service.createPresignedPutRequest(keyName, contentType);

        assertEquals(expectedPresignedUrl, result);
        verify(s3Presigner).presignPutObject(argThat(requestMatcher));
        verify(dotenv).get("S3_BUCKET");
    }

    @Test
    void listObjects_ValidBucket_ShouldReturnListOfS3ObjectDto() {

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

        S3ObjectDto resultDto = s3Service.toDto(s3Object);

        assertEquals("test-file.mp3", resultDto.getKey());
        assertEquals(expectedSizeMB, resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }
}
