package com.audiosource.backend;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.service.S3Service;
import com.audiosource.backend.util.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private Dotenv dotenv;

    @Mock
    private S3TransferManager s3TransferManager ;

    @InjectMocks
    private S3Service s3Service;

    private final String bucketName = "test-bucket";
    private final String key = "test-key";
    private final String contentType = "audio/mp3";
    private final String zipFileName = "test-zip-file.zip";
    private static final String SUB_BUCKET = "separated/";

    @BeforeEach
    void setUp() {
    }

    @Test
    void testGetObjectFromBucketLargeFile() throws IOException {

        String keyName = "path/to/largefile.mp3";
        String directoryPath = "/local/dir/";
        long fileSizeInBytes = 150 * 1024 * 1024; // 150MB
        String expectedFilePath = directoryPath + "largefile.mp3";

        File directory = new File(directoryPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File myAudioFile = new File(expectedFilePath);

        DownloadFileRequest downloadFileRequest = mock(DownloadFileRequest.class);
        FileDownload fileDownload = mock(FileDownload.class);
        CompletedFileDownload completedFileDownload = mock(CompletedFileDownload.class);
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);

        // Custom matcher for DownloadFileRequest
        ArgumentMatcher<DownloadFileRequest> requestMatcher = request ->
                request.getObjectRequest().bucket().equals(bucketName) &&
                        request.getObjectRequest().key().equals(keyName) &&
                        request.destination().equals(Paths.get(expectedFilePath));

        // Mocking the transfer manager and download file completion
        when(s3TransferManager.downloadFile(argThat(requestMatcher))).thenReturn(fileDownload);

        CompletableFuture<CompletedFileDownload> completableFuture = CompletableFuture.completedFuture(completedFileDownload);
        when(fileDownload.completionFuture()).thenReturn(completableFuture);
        when(completedFileDownload.response()).thenReturn(getObjectResponse);
        when(getObjectResponse.contentLength()).thenReturn(fileSizeInBytes);

        Optional<String> result = s3Service.getObjectFromBucket(bucketName, keyName, directoryPath, fileSizeInBytes);

        assertEquals(Optional.of(expectedFilePath), result);
        verify(s3TransferManager).downloadFile(argThat(requestMatcher));
        myAudioFile.delete(); // Clean up
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

        // Use the custom matcher in the`when` call
        when(s3Presigner.presignGetObject(argThat(requestMatcher))).thenReturn(presignedGetObjectRequest);

        // Call the method under test
        String actualUrl = s3Service.createPresignedGetRequest(bucketName, zipS3File);

        assertEquals(expectedUrl, actualUrl);
        verify(s3Presigner).presignGetObject(argThat(requestMatcher));
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

        // Mock the static method for S3Utils.formatLastModified
        try (var mockedStatic = mockStatic(S3Utils.class)) {
            mockedStatic.when(() -> S3Utils.formatLastModified(lastModified))
                    .thenReturn(expectedLastModified);

            S3ObjectDto resultDto = s3Service.toDto(s3Object);

            assertEquals("test-file.mp3", resultDto.getKey());
            assertEquals(expectedSizeMB, resultDto.getSizeMB());
            assertEquals(expectedLastModified, resultDto.getLastModified());
        }
    }

}
