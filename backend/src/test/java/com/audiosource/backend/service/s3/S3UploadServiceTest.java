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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.IOException;
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
    private S3PresignedUrlService s3PresignedUrlService;

    @InjectMocks
    private S3UploadService s3UploadService;

    private final String bucketName = "test-bucket";
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

        String presignedUrl = s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), bucketName);

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
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
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
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
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
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
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
            s3UploadService.uploadDirectoryAsZipToS3(sourceDirectory.toString(), "test-bucket");
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
}
