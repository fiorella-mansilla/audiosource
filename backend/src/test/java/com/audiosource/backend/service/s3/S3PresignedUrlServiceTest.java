package com.audiosource.backend.service.s3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3PresignedUrlServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private AwsErrorDetails awsErrorDetails;

    @Mock
    private S3Exception s3Exception;

    @InjectMocks
    private S3PresignedUrlService s3PresignedUrlService;

    @Mock
    private Dotenv dotenv;

    private final String bucketName = "test-bucket";
    private final String keyName = "test-file.mp3";
    private final String contentType = "audio/mp3";
    private final String expectedPresignedUrl = "https://test-bucket.s3.amazonaws.com/test-key";
    private static final String SUB_BUCKET = "separated/";
    private Path tempDirectory;

    @BeforeEach
    void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("_temp");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteDirectoryRecursively(tempDirectory);
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

        // Use the custom matcher in the `when` call
        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        String actualUrl = s3PresignedUrlService.createPresignedGetRequest(bucketName, zipS3File);

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
            s3PresignedUrlService.createPresignedGetRequest(bucketName, zipS3File);
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
            s3PresignedUrlService.createPresignedGetRequest(bucketName, zipS3File);
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
            s3PresignedUrlService.createPresignedGetRequest(bucketName, zipS3File);
        });

        assertEquals("Failed to generate presigned URL due to an unexpected error.", thrownException.getMessage());
        assertTrue(thrownException.getCause() instanceof RuntimeException);
        assertEquals(expectedErrorMessage, thrownException.getCause().getMessage());
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

        String result = s3PresignedUrlService.createPresignedPutRequest(keyName, contentType);

        assertEquals(expectedPresignedUrl, result);
        verify(s3Presigner).presignPutObject(argThat(requestMatcher));
        verify(dotenv).get("S3_BUCKET");
    }

    @Test
    void createPresignedPutRequest_MissingBucket_ShouldThrowException() {

        when(dotenv.get("S3_BUCKET")).thenReturn(null); // Simulate missing bucket name

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            s3PresignedUrlService.createPresignedPutRequest(keyName, contentType);
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
            s3PresignedUrlService.createPresignedPutRequest(keyName, contentType);
        });

        assertEquals("Failed to generate presigned PUT URL", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof S3Exception);

        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
        verify(dotenv).get("S3_BUCKET");
    }

}
