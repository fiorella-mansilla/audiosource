package com.audiosource.backend.service.s3;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.file.Path;
import java.time.Duration;


@Service
public class S3PresignedUrlService {
    private final S3Presigner s3Presigner;
    private final Dotenv dotenv;
    private static final Logger LOGGER = LoggerFactory.getLogger(S3PresignedUrlService.class);
    private static final String SUB_BUCKET = "separated/";

    @Autowired
    public S3PresignedUrlService(S3Presigner s3Presigner, Dotenv dotenv) {
        this.s3Presigner = s3Presigner;
        this.dotenv = dotenv;
    }

    /**
     * Creates a pre-signed URL for directly downloading an object from an S3 bucket.
     *
     * @param bucketName The name of the S3 bucket.
     * @param zipS3File  The path to the ZIP file in S3.
     * @return A pre-signed URL for downloading the object, valid for a limited duration.
     */
    public String createPresignedGetRequest(String bucketName, Path zipS3File) {

        try {
            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(60))
                    .getObjectRequest(req -> req.bucket(bucketName).key(SUB_BUCKET + zipS3File.getFileName().toString()))
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
            return presignedGetObjectRequest.url().toString();

        } catch (S3Exception e) {
            LOGGER.error("S3 exception occurred: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL due to S3 error.", e);

        } catch (SdkException e) {
            // Handle general AWS SDK exceptions
            LOGGER.error("SDK exception occurred: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL due to SDK error.", e);

        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL due to an unexpected error.", e);
        }
    }

    /* Creates a pre-signed URL to use in a subsequent PUT request of a File to an S3 bucket. */
    public String createPresignedPutRequest(String key, String contentType) {

        try {
            String bucketName = dotenv.get("S3_BUCKET");

            if (bucketName == null || bucketName.isEmpty()) {
                String errorMessage = "S3 bucket name is not set in the environment variables.";
                LOGGER.error(errorMessage);
                throw new IllegalStateException(errorMessage); // Ensure the method fails visibly
            }

            // Create a PutObjectRequest to be pre-signed
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            // Create a PutObjectPresignRequest to specify the signature duration
            PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10)) // The URL expires in 10 minutes.
                    .putObjectRequest(putObjectRequest)
                    .build();

            // Generate the Pre-signed request with the S3-Presigner
            PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);

            String signedUrl = presignedPutObjectRequest.url().toString();

            LOGGER.info("Presigned URL to upload a file to: [{}]", signedUrl);
            LOGGER.info("HTTP method: [{}]", presignedPutObjectRequest.httpRequest().method());

            return presignedPutObjectRequest.url().toExternalForm();

        } catch (S3Exception e) {
            LOGGER.error("S3 error while generating presigned PUT URL for key [{}]: {}", key, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT URL", e);

        } catch (SdkException e) {
            LOGGER.error("SDK error while generating presigned PUT URL for key [{}]: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT URL", e);

        } catch (Exception e) {
            LOGGER.error("Unexpected error while generating presigned PUT URL for key [{}]: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT URL", e);
        }
    }
}
