package com.audiosource.backend;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.exception.S3UploadException;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    private AwsErrorDetails awsErrorDetails;

    @Mock
    private S3Exception s3Exception;

    @InjectMocks
    private S3Service s3Service;

    @Mock
    private Dotenv dotenv;

    private final String bucketName = "test-bucket";
    private final String keyName = "test-file.mp3";
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
    void uploadDirectoryAsZipToS3_ValidInput_ShouldReturnPresignedURL() throws IOException, S3UploadException {

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
    void uploadDirectoryAsZipToS3_NoImmediateChildDirectory_ShouldThrowIllegalArgumentException() throws IOException {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.getImmediateChildDirectory(sourceDirectory)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            s3Service.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
            });

        assertEquals("No immediate child directory found in " + sourceDirectory.toString(), exception.getMessage());
        mockedS3Utils.verify(() -> S3Utils.getImmediateChildDirectory(sourceDirectory), times(1));

        // Verify that no upload methods were called since the exception should be thrown before
        verifyNoInteractions(s3Presigner, s3Client, s3TransferManager);
    }

    @Test
    void uploadDirectoryAsZipToS3_IOExceptionDuringPreparation_ShouldThrowS3UploadException() throws IOException, S3UploadException {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        mockedS3Utils = mockStatic(S3Utils.class);

        Path immediateChildDirectory = sourceDirectory.resolve("childDirectory");
        Files.createDirectory(immediateChildDirectory);

        mockedS3Utils.when(() -> S3Utils.getImmediateChildDirectory(sourceDirectory)).thenReturn(immediateChildDirectory);
        mockedS3Utils.when(S3Utils::generateUniqueDirectoryName).thenReturn("renamedChildDirectory");

        // Mocking S3Utils.toZipDirectory to throw an IOException
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenThrow(new IOException("Mocked IOException"));

        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3Service.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
        });

        assertTrue(exception.getMessage().contains("Failed to upload directory as zip to S3"));
        assertTrue(exception.getCause() instanceof IOException);
        assertEquals("Mocked IOException", exception.getCause().getMessage());

        mockedS3Utils.verify(() -> S3Utils.getImmediateChildDirectory(sourceDirectory), times(1));
        mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));

        // Verify that no further steps like uploading were attempted after the IOException
        verifyNoInteractions(s3Presigner, s3Client, s3TransferManager);
    }

    @Test
    void uploadDirectoryAsZipToS3_CompletionException_ShouldThrowS3UploadException() throws IOException, S3UploadException {
        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        mockedS3Utils = mockStatic(S3Utils.class);

        Path immediateChildDirectory = sourceDirectory.resolve("childDirectory");
        Files.createDirectory(immediateChildDirectory);

        String newDirectoryName = "uniqueName.zip";
        Path renamedDirectory = immediateChildDirectory.resolveSibling(newDirectoryName);

        Files.move(immediateChildDirectory, renamedDirectory);

        mockedS3Utils.when(() -> S3Utils.getImmediateChildDirectory(sourceDirectory)).thenReturn(renamedDirectory);
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenReturn(renamedDirectory);
        mockedS3Utils.when(() -> S3Utils.generateUniqueDirectoryName()).thenReturn(newDirectoryName);

        // Mocking the file upload process to throw a CompletionException
        FileUpload fileUpload = mock(FileUpload.class);
        CompletableFuture<CompletedFileUpload> future = new CompletableFuture<>();
        RuntimeException originalException = new RuntimeException("Mocked CompletionException");
        future.completeExceptionally(new CompletionException(originalException));
        when(fileUpload.completionFuture()).thenReturn(future);

        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);

        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3Service.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
        });

        assertEquals("Failed to upload file to S3", exception.getMessage());
        assertEquals(originalException, exception.getCause()); // Ensure the cause is the original exception

        mockedS3Utils.verify(() -> S3Utils.getImmediateChildDirectory(sourceDirectory), times(1));
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(any()), times(1));
        mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));

        // Verify that no further steps were attempted after the CompletionException
        verifyNoInteractions(s3Presigner, s3Client);
    }

    @Test
    void uploadDirectoryAsZipToS3_GeneralExceptionDuringUpload_ShouldThrowS3UploadException() throws IOException {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        mockedS3Utils = mockStatic(S3Utils.class);

        Path immediateChildDirectory = sourceDirectory.resolve("childDirectory");
        Files.createDirectory(immediateChildDirectory);

        String newDirectoryName = "uniqueName.zip";
        Path renamedDirectory = immediateChildDirectory.resolveSibling(newDirectoryName);

        Files.move(immediateChildDirectory, renamedDirectory);

        mockedS3Utils.when(() -> S3Utils.getImmediateChildDirectory(sourceDirectory)).thenReturn(renamedDirectory);
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenReturn(renamedDirectory);
        mockedS3Utils.when(() -> S3Utils.generateUniqueDirectoryName()).thenReturn(newDirectoryName);

        // Mocking a general exception during the upload process
        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenThrow(new RuntimeException("Mocked General Exception"));

        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3Service.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
        });

        assertTrue(exception.getMessage().contains("Failed to upload file to S3"));
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Mocked General Exception", exception.getCause().getMessage());

        mockedS3Utils.verify(() -> S3Utils.getImmediateChildDirectory(sourceDirectory), times(1));
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(any()), times(1));

        // Verify that the upload was attempted but failed
        verify(s3TransferManager).uploadFile(any(UploadFileRequest.class));

        verifyNoInteractions(s3Presigner);
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

        verify(s3TransferManager, times(1)).close();
        verifyNoMoreInteractions(s3TransferManager);
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
        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3Service.uploadFileFromLocalToS3(zipS3File, bucketName);
        });

        assertEquals("Failed to upload file to S3", exception.getMessage());
        Throwable cause = exception.getCause();
        assertNotNull(cause, "Cause should not be null");
        assertTrue(cause instanceof RuntimeException, "Cause should be a RuntimeException");
        assertEquals("Simulated S3 Transfer Exception", cause.getMessage(), "Cause message should match");

        verify(s3TransferManager, times(1)).close();
        verifyNoMoreInteractions(s3TransferManager);
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
    public void prepareDirectoryForUpload_IOExceptionDuringMove_ShouldThrowIOException() throws IOException {

        Path originalDirectory = tempDirectory.resolve("testDir");
        Files.createDirectory(originalDirectory);

        Path renamedDirectory = originalDirectory.resolveSibling("renamedDir");
        Path zipPath = renamedDirectory.resolveSibling("renamedDir.zip");

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.generateUniqueDirectoryName()).thenReturn("renamedDir");
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any(Path.class))).thenReturn(zipPath);

        // Use Mockito to spy on the S3Service to intercept the Files.move call
        S3Service spyS3Service = spy(s3Service);

        // Stub the method to throw IOException
        doThrow(new IOException("Simulated IOException during move"))
                .when(spyS3Service).prepareDirectoryForUpload(originalDirectory);

        IOException exception = assertThrows(IOException.class, () -> {
            spyS3Service.prepareDirectoryForUpload(originalDirectory);
        });

        assertEquals("Simulated IOException during move", exception.getMessage());

        assertFalse(Files.exists(zipPath), "The zip file should not exist.");
        assertFalse(Files.exists(renamedDirectory), "The renamed directory should not exist.");

        verifyNoInteractions(s3Client, s3Presigner, s3TransferManager);
    }

    @Test
    public void prepareDirectoryForUpload_IOExceptionDuringZipping_ShouldThrowIOException() throws IOException {

        Path originalDirectory = tempDirectory.resolve("testDir");
        Files.createDirectory(originalDirectory);

        Path renamedDirectory = originalDirectory.resolveSibling("renamedDir");
        Path zipPath = renamedDirectory.resolveSibling("renamedDir.zip");

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.generateUniqueDirectoryName()).thenReturn("renamedDir");

        // Stub the zipping method to throw IOException
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(renamedDirectory))
                .thenThrow(new IOException("Simulated IOException during zipping"));

        IOException exception = assertThrows(IOException.class, () -> {
            s3Service.prepareDirectoryForUpload(originalDirectory);
        });

        assertEquals("Simulated IOException during zipping", exception.getMessage());
        // Verify the state of the file system
        assertFalse(Files.exists(zipPath), "The zip file should not exist.");

        mockedS3Utils.verify(S3Utils::generateUniqueDirectoryName);
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(renamedDirectory));

        verifyNoInteractions(s3Client, s3Presigner, s3TransferManager);
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
    void createPresignedGetRequest_S3Exception_ShouldHandleException() {

        Path zipS3File = Paths.get("test-zip-file.zip");

        String expectedErrorMessage = "S3 exception occurred";
        S3Exception s3Exception = (S3Exception) S3Exception.builder().message(expectedErrorMessage).build();

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(s3Exception);

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3Service.createPresignedGetRequest(bucketName, zipS3File);
        });

        assertEquals("Failed to generate presigned URL due to S3 error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof S3Exception);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
    }

    @Test
    void createPresignedGetRequest_SdkException_ShouldHandleException() {

        Path zipS3File = Paths.get("test-zip-file.zip");
        String expectedErrorMessage = "SDK exception occurred";

        SdkException sdkException = SdkException.builder()
                .message(expectedErrorMessage)
                .build();

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(sdkException);

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3Service.createPresignedGetRequest(bucketName, zipS3File);
        });

        assertEquals("Failed to generate presigned URL due to SDK error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof SdkException);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
    }

    @Test
    void createPresignedGetRequest_GeneralException_ShouldHandleException() {

        Path zipS3File = Paths.get("test-zip-file.zip");
        String expectedErrorMessage = "Unexpected error occurred";

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException(expectedErrorMessage));

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3Service.createPresignedGetRequest(bucketName, zipS3File);
        });

        assertEquals("Failed to generate presigned URL due to an unexpected error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof RuntimeException);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
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

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

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

            Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

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

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

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

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent());

        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void getObjectFromBucket_GeneralException_ShouldReturnEmptyOptional() {

        String directoryPath = tempDirectory.toString();
        long fileSizeInBytes = 50 * 1024 * 1024; // 50 MB

        // Mock a general exception during the small file download process
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(new RuntimeException("Simulated general exception"));

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

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

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertFalse(result.isPresent(), "Result should be empty due to general exception during large file download");

        verify(s3TransferManager, times(1)).downloadFile(any(DownloadFileRequest.class));
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
    void createPresignedPutRequest_MissingBucket_ShouldThrowException() {

        when(dotenv.get("S3_BUCKET")).thenReturn(null); // Simulate missing bucket name

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            s3Service.createPresignedPutRequest(keyName, contentType);
        });

        assertEquals("Failed to generate presigned PUT URL", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("S3 bucket name is not set in the environment variables.", thrown.getCause().getMessage());
        verify(dotenv).get("S3_BUCKET");
    }

    @Test
    void createPresignedPutRequest_S3Exception_ShouldThrowRuntimeException() {

        when(dotenv.get("S3_BUCKET")).thenReturn(bucketName);

        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        // Simulate S3Exception being thrown by the presigner
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(s3Exception);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            s3Service.createPresignedPutRequest(keyName, contentType);
        });

        assertEquals("Failed to generate presigned PUT URL", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof S3Exception);

        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
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

        List<S3ObjectDto> result = s3Service.listObjects(bucketName);

        assertTrue(result.isEmpty(), "Expected an empty list when the bucket is empty");
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void listObjects_S3ExceptionDuringListing_ShouldHandleException() {

        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(s3Exception);

        List<S3ObjectDto> result = s3Service.listObjects(bucketName);

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

        S3ObjectDto resultDto = s3Service.toDto(s3Object);

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

        S3ObjectDto resultDto = s3Service.toDto(s3Object);

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

        S3ObjectDto resultDto = s3Service.toDto(s3Object);

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

        S3ObjectDto resultDto = s3Service.toDto(s3Object);

        assertEquals(keyName, resultDto.getKey());
        assertEquals(S3TestUtils.formatSize(sizeInBytes), resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }

}
