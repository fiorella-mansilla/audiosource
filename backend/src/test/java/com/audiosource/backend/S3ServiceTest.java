package com.audiosource.backend;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.service.S3Service;
import com.audiosource.backend.util.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private Dotenv dotenv;

    @InjectMocks
    private S3Service s3Service;

    private final String bucketName = "test-bucket";
    private final String key = "test-key";
    private final String contentType = "audio/mp3";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");

    @BeforeEach
    void setUp() {
    }

    @Test
    void testCreatePresignedPutRequest() throws MalformedURLException {

        String expectedUrl = "https://test-bucket.s3.amazonaws.com/test-key";

        PutObjectRequest putObjectRequest = S3TestUtils.createPutObjectRequest(bucketName, key, contentType);

        PutObjectPresignRequest putObjectPresignRequest = S3TestUtils.createPutObjectPresignRequest(putObjectRequest);

        // Mock PresignedPutObjectRequest
        PresignedPutObjectRequest presignedPutObjectRequest = mock(PresignedPutObjectRequest.class);
        when(presignedPutObjectRequest.url()).thenReturn(URI.create(expectedUrl).toURL());

        // Mock SdkHttpRequest
        SdkHttpRequest sdkHttpRequest = mock(SdkHttpRequest.class);
        when(sdkHttpRequest.method()).thenReturn(SdkHttpMethod.valueOf("PUT"));

        when(presignedPutObjectRequest.httpRequest()).thenReturn(sdkHttpRequest);

        // Configure the mock to return the PresignedPutObjectRequest
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);

        String result = s3Service.createPresignedPutRequest(key, contentType);

        assertEquals(expectedUrl, result);
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
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
