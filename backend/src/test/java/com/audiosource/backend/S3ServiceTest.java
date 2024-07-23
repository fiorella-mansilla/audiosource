package com.audiosource.backend;

import com.audiosource.backend.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.MalformedURLException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private Dotenv dotenv;

    @InjectMocks
    private S3Service s3Service;

    private final String bucketName = "test-bucket";
    private final String key = "test-key";
    private final String contentType = "text/plain";

    @BeforeEach
    void setUp() {
        when(dotenv.get("S3_BUCKET")).thenReturn("test-bucket");
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
}
