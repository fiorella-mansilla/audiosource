package com.audiosource.backend.service.s3;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.util.S3Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class S3ObjectService {
    private final S3Client s3Client;
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ObjectService.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");

    @Autowired
    public S3ObjectService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /* List all files from the specified AWS S3 bucket, excluding empty directories. */
    public List<S3ObjectDto> listObjects(String bucketName) {
        try {
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request
                    .builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(listObjectsRequest);
            LOGGER.info("Listed objects in bucket '{}'", bucketName);

            return response.contents().stream()
                    .filter(s3Object -> !s3Object.key().endsWith("/") || s3Object.size() != 0)
                    .map(this::toDto)
                    .collect(Collectors.toList());

        } catch (S3Exception e) {
            LOGGER.error("S3 error while listing objects in bucket '{}': {}", bucketName, e.awsErrorDetails().errorMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.error("Unexpected error while listing objects in bucket '{}': {}", bucketName, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /* Convert an S3Object to an S3ObjectDto, formatting the size to MB with one decimal place
     * and the last modified date to a readable format. */
    public S3ObjectDto toDto(S3Object s3Object) {

        double size = s3Object.size() / (1024.0 * 1024.0);
        String sizeMB = DECIMAL_FORMAT.format(size) + " MB";
        String formattedLastModified = S3Utils.formatLastModified(s3Object.lastModified());

        return new S3ObjectDto(
                s3Object.key(),
                sizeMB,
                formattedLastModified
        );
    }

    /* Delete a specific object (file/directory) from the S3 bucket */
    public void deleteObjectFromS3(String bucketName, String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            LOGGER.info("Successfully deleted object [{}] from bucket [{}]", key, bucketName);
        } catch (S3Exception e) {
            LOGGER.error("Failed to delete object [{}] from bucket [{}]: {}",
                    key, bucketName, e.awsErrorDetails().errorMessage());
        }
    }
}
