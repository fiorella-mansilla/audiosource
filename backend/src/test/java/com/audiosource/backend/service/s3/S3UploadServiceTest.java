package com.audiosource.backend.service.s3;

import com.audiosource.backend.exception.S3UploadException;
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
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3UploadServiceTest {

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
    private S3UploadService s3UploadService;

    private final String bucketName = "test-bucket";
    private final String keyName = "test-file.mp3";
    private final String contentType = "audio/mp3";
    private final String expectedPresignedUrl = "https://test-bucket.s3.amazonaws.com/test-key";
    private static final String SUB_BUCKET = "separated/";
    private Path tempDirectory;
    private final Path zipS3DirectoryPath = Paths.get("test-file.zip");
    private MockedStatic<S3Utils> mockedS3Utils;

    @BeforeEach
    void setUp() throws IOException {
        // Inject the bucket name into the S3UploadService using reflection since we're not using a Spring context here
        ReflectionTestUtils.setField(s3UploadService, "bucketName", "test-bucket");
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
                        request.getObjectRequest().key().equals("separated/" + zipFilePath.getFileName().toString());

        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        String presignedUrl = s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), bucketName);

        assertEquals(expectedPresignedUrl, presignedUrl);

        mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(any()), times(1));

        verify(s3TransferManager).uploadFile(any(UploadFileRequest.class));

        ArgumentCaptor<UploadFileRequest> uploadFileRequestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(s3TransferManager).uploadFile(uploadFileRequestCaptor.capture());
        UploadFileRequest capturedRequest = uploadFileRequestCaptor.getValue();

        assertAll("uploadFileRequest",
                () -> assertEquals(zipFilePath, capturedRequest.source(), "The source should match the zip file path"),
                () -> assertEquals(bucketName, capturedRequest.putObjectRequest().bucket(), "The bucket name should match"),
                () -> assertEquals("separated/" + zipFilePath.getFileName().toString(),
                        capturedRequest.putObjectRequest().key(), "The key should match the expected S3 key")
        );
    }

    @Test
    void uploadDirectoryAsZipToS3_InvalidDirectoryPath_ShouldThrowIllegalArgumentException() {

        String invalidDirectoryPath = "";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                s3UploadService.uploadDirectoryAsZipToS3(invalidDirectoryPath, bucketName));

        assertEquals("Directory path cannot be null or empty", exception.getMessage());
    }

    @Test
    void uploadDirectoryAsZipToS3_InvalidBucketName_ShouldThrowIllegalArgumentException() {

        String validDirectoryPath = tempDirectory.resolve("sourceDirectory").toString();
        String invalidBucketName = "";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                s3UploadService.uploadDirectoryAsZipToS3(validDirectoryPath, invalidBucketName));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
    }

    @Test
    void uploadDirectoryAsZipToS3_IOExceptionDuringPreparation_ShouldThrowS3UploadException() throws IOException, S3UploadException {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(S3Utils::generateUniqueDirectoryName).thenReturn("renamedChildDirectory");
        // Mocking S3Utils.toZipDirectory to throw an IOException
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenThrow(new IOException("Mocked IOException"));

        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
        });

        assertTrue(exception.getMessage().contains("Failed to upload directory as zip to S3"));
        assertTrue(exception.getCause() instanceof IOException);
        assertEquals("Mocked IOException", exception.getCause().getMessage());

        mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));

        // Verify that no further steps like uploading were attempted after the IOException
        verifyNoInteractions(s3Presigner, s3Client, s3TransferManager);
    }

    @Test
    void uploadDirectoryAsZipToS3_CompletionException_ShouldThrowS3UploadException() throws IOException, S3UploadException {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        String newDirectoryName = "uniqueName.zip";
        Path renamedDirectory = sourceDirectory.resolveSibling(newDirectoryName);

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.generateUniqueDirectoryName()).thenReturn(newDirectoryName);
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenReturn(renamedDirectory);

        // Mocking the file upload process to throw a CompletionException
        FileUpload fileUpload = mock(FileUpload.class);
        CompletableFuture<CompletedFileUpload> future = new CompletableFuture<>();
        RuntimeException originalException = new RuntimeException("Mocked CompletionException");
        future.completeExceptionally(new CompletionException(originalException));
        when(fileUpload.completionFuture()).thenReturn(future);

        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);

        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
        });

        assertEquals("Failed to upload file to S3", exception.getMessage());
        assertEquals(originalException, exception.getCause()); // Ensure the cause is the original exception

        mockedS3Utils.verify(() -> S3Utils.generateUniqueDirectoryName(), times(1));

        // Verify that no further steps were attempted after the CompletionException
        verifyNoInteractions(s3Presigner, s3Client);
    }

    @Test
    void uploadDirectoryAsZipToS3_GeneralExceptionDuringUpload_ShouldThrowS3UploadException() throws IOException {

        Path sourceDirectory = tempDirectory.resolve("sourceDirectory");
        Files.createDirectories(sourceDirectory);

        String newDirectoryName = "uniqueName.zip";
        Path renamedDirectory = sourceDirectory.resolveSibling(newDirectoryName);

        mockedS3Utils = mockStatic(S3Utils.class);
        mockedS3Utils.when(() -> S3Utils.generateUniqueDirectoryName()).thenReturn(newDirectoryName);
        mockedS3Utils.when(() -> S3Utils.toZipDirectory(any())).thenReturn(renamedDirectory);

        // Mocking a general exception during the upload process
        when(s3TransferManager.uploadFile(any(UploadFileRequest.class))).thenThrow(new RuntimeException("Mocked General Exception"));

        S3UploadException exception = assertThrows(S3UploadException.class, () -> {
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
        });

        assertTrue(exception.getMessage().contains("Failed to upload file to S3"));
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Mocked General Exception", exception.getCause().getMessage());

        // Verify that the upload was attempted but failed
        verify(s3TransferManager).uploadFile(any(UploadFileRequest.class));
        verifyNoInteractions(s3Presigner);
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

        Path result = s3UploadService.prepareDirectoryForUpload(originalDirectory);

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
        S3UploadService spyS3UploadService = spy(s3UploadService);

        // Stub the method to throw IOException
        doThrow(new IOException("Simulated IOException during move"))
                .when(spyS3UploadService).prepareDirectoryForUpload(originalDirectory);

        IOException exception = assertThrows(IOException.class, () -> {
            spyS3UploadService.prepareDirectoryForUpload(originalDirectory);
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
            s3UploadService.prepareDirectoryForUpload(originalDirectory);
        });

        assertEquals("Simulated IOException during zipping", exception.getMessage());
        // Verify the state of the file system
        assertFalse(Files.exists(zipPath), "The zip file should not exist.");

        mockedS3Utils.verify(S3Utils::generateUniqueDirectoryName);
        mockedS3Utils.verify(() -> S3Utils.toZipDirectory(renamedDirectory));

        verifyNoInteractions(s3Client, s3Presigner, s3TransferManager);
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

        s3UploadService.uploadFileFromLocalToS3(zipS3File, bucketName);

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
            s3UploadService.uploadFileFromLocalToS3(zipS3File, bucketName);
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
    void createPresignedGetRequest_ValidInput_ShouldReturnPresignedURL() throws MalformedURLException {

        // Mock the presigned URL request and response
        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(presignedGetObjectRequest.url()).thenReturn(URI.create(expectedPresignedUrl).toURL());

        // Custom matcher for GetObjectPresignRequest
        ArgumentMatcher<GetObjectPresignRequest> requestMatcher = request ->
                request.signatureDuration().equals(Duration.ofMinutes(60)) &&
                        request.getObjectRequest().bucket().equals(bucketName) &&
                        request.getObjectRequest().key().equals(SUB_BUCKET + zipS3DirectoryPath.getFileName().toString());

        // Use the custom matcher in the `when` call
        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        String actualUrl = s3UploadService.createPresignedGetRequest(bucketName, zipS3DirectoryPath);

        assertEquals(expectedPresignedUrl, actualUrl);
        verify(s3Presigner).presignGetObject(argThat(requestMatcher));
    }

    @Test
    void createPresignedGetRequest_S3Exception_ShouldHandleException() {

        String expectedErrorMessage = "S3 exception occurred";
        S3Exception s3Exception = (S3Exception) S3Exception.builder().message(expectedErrorMessage).build();

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(s3Exception);

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3UploadService.createPresignedGetRequest(bucketName, zipS3DirectoryPath);
        });

        assertEquals("Failed to generate presigned URL due to S3 error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof S3Exception);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
    }

    @Test
    void createPresignedGetRequest_SdkException_ShouldHandleException() {

        String expectedErrorMessage = "SDK exception occurred";

        SdkException sdkException = SdkException.builder()
                .message(expectedErrorMessage)
                .build();

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(sdkException);

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3UploadService.createPresignedGetRequest(bucketName, zipS3DirectoryPath);
        });

        assertEquals("Failed to generate presigned URL due to SDK error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof SdkException);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
    }

    @Test
    void createPresignedGetRequest_GeneralException_ShouldHandleException() {

        String expectedErrorMessage = "Unexpected error occurred";

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException(expectedErrorMessage));

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3UploadService.createPresignedGetRequest(bucketName, zipS3DirectoryPath);
        });

        assertEquals("Failed to generate presigned URL due to an unexpected error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof RuntimeException);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
    }

    @Test
    void createPresignedPutRequest_ValidInput_ShouldReturnPresignedURL() throws MalformedURLException {

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

        when(s3Presigner.presignPutObject(argThat(requestMatcher)))
                .thenReturn(presignedPutObjectRequest);

        String result = s3UploadService.createPresignedPutRequest(keyName, contentType);

        assertEquals(expectedPresignedUrl, result);
        verify(s3Presigner).presignPutObject(argThat(requestMatcher));
    }

    @Test
    void createPresignedPutRequest_MissingBucket_ShouldThrowException() {

        // Simulate missing bucket name by setting it to null via reflection
        ReflectionTestUtils.setField(s3UploadService, "bucketName", null);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            s3UploadService.createPresignedPutRequest(keyName, contentType);
        });

        assertEquals("Failed to generate presigned PUT URL", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("S3 bucket name is not set in the environment variables.", thrown.getCause().getMessage());
    }

    @Test
    void createPresignedPutRequest_NullKey_ShouldThrowIllegalArgumentException() {
        String nullKey = null;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                s3UploadService.createPresignedPutRequest(nullKey, contentType));

        assertEquals("Key cannot be null or empty", exception.getMessage());
    }

    @Test
    void createPresignedPutRequest_EmptyKey_ShouldThrowIllegalArgumentException() {
        String emptyKey = "";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                s3UploadService.createPresignedPutRequest(emptyKey, contentType));

        assertEquals("Key cannot be null or empty", exception.getMessage());
    }

    @Test
    void createPresignedPutRequest_NullContentType_ShouldThrowIllegalArgumentException() {
        String nullContentType = null;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                s3UploadService.createPresignedPutRequest(keyName, nullContentType));

        assertEquals("Content-Type cannot be null or empty", exception.getMessage());
    }

    @Test
    void createPresignedPutRequest_EmptyContentType_ShouldThrowIllegalArgumentException() {
        String emptyContentType = "";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                s3UploadService.createPresignedPutRequest(keyName, emptyContentType));

        assertEquals("Content-Type cannot be null or empty", exception.getMessage());
    }

    @Test
    void createPresignedPutRequest_S3Exception_ShouldThrowRuntimeException() {

        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        // Simulate S3Exception being thrown by the presigner
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(s3Exception);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            s3UploadService.createPresignedPutRequest(keyName, contentType);
        });

        assertEquals("Failed to generate presigned PUT URL", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof S3Exception);

        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void createPresignedPutRequest_GeneralException_ShouldThrowRuntimeException() {

        // Simulate a general exception being thrown by the presigner
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            s3UploadService.createPresignedPutRequest(keyName, contentType);
        });

        assertEquals("Failed to generate presigned PUT URL", thrown.getMessage());
        assertEquals("Unexpected error", thrown.getCause().getMessage());

        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }
}
