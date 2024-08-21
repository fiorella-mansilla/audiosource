package com.audiosource.backend.service.s3;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.util.S3Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3ObjectServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private AwsErrorDetails awsErrorDetails;

    @Mock
    private S3Exception s3Exception;

    @InjectMocks
    S3ObjectService s3ObjectService;

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
    void listObjects_ValidBucket_ShouldReturnListOfS3ObjectDto() {

        S3Object s3Object = S3TestUtils.createTestS3Object(123L, Instant.now());

        ListObjectsV2Response listObjectsResponse = S3TestUtils.createListObjectsResponse(List.of(s3Object));

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listObjectsResponse);

        List<S3ObjectDto> result = s3ObjectService.listObjects(bucketName);

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

        List<S3ObjectDto> result = s3ObjectService.listObjects(bucketName);

        assertTrue(result.isEmpty(), "Expected an empty list when the bucket is empty");
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void listObjects_S3ExceptionDuringListing_ShouldHandleException() {

        when(awsErrorDetails.errorMessage()).thenReturn("An S3 error occurred");
        when(s3Exception.awsErrorDetails()).thenReturn(awsErrorDetails);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(s3Exception);

        List<S3ObjectDto> result = s3ObjectService.listObjects(bucketName);

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

        S3ObjectDto resultDto = s3ObjectService.toDto(s3Object);

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

        S3ObjectDto resultDto = s3ObjectService.toDto(s3Object);

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

        S3ObjectDto resultDto = s3ObjectService.toDto(s3Object);

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

        S3ObjectDto resultDto = s3ObjectService.toDto(s3Object);

        assertEquals(keyName, resultDto.getKey());
        assertEquals(S3TestUtils.formatSize(sizeInBytes), resultDto.getSizeMB());
        assertEquals(expectedLastModified, resultDto.getLastModified());
    }
}
