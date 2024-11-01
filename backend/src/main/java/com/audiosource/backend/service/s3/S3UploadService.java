package com.audiosource.backend.service.s3;

import com.audiosource.backend.exception.S3UploadException;
import com.audiosource.backend.util.S3Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class S3UploadService {

    private final S3Presigner s3Presigner;
    private final S3TransferManager s3TransferManager;
    private final S3Client s3Client;
    private static final String SUB_BUCKET = "separated/";
    private static final Logger LOGGER = LoggerFactory.getLogger(S3UploadService.class);

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Autowired
    public S3UploadService(S3Presigner s3Presigner, S3TransferManager s3TransferManager, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.s3TransferManager = s3TransferManager;
        this.s3Client = s3Client;
    }

    /**
     * Uploads a zipped directory with the separated audios to an S3 bucket
     * and returns a pre-signed URL for downloading the ZIP file.
     *
     * @param processedAudioFilePath The local directory path of processed files to upload to S3.
     * @param bucketName    The name of the S3 bucket.
     * @return A pre-signed GET URL for downloading the uploaded ZIP file from S3, or null if an error occurs.
     */
    public String uploadDirectoryAsZipToS3(String processedAudioFilePath, String bucketName) throws S3UploadException {
        
        validateParameters(processedAudioFilePath, bucketName);

        Path sourceDirectory = Paths.get(processedAudioFilePath);

        try {
            Path zipS3DirectoryPath = prepareDirectoryForUpload(sourceDirectory);

            uploadFileFromLocalToS3(zipS3DirectoryPath, bucketName);

            return createPresignedGetRequest(bucketName, zipS3DirectoryPath);

        } catch (IOException | CompletionException e) {
            Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;
            LOGGER.error("Error during upload process for directory '{}': {}", processedAudioFilePath, cause.getMessage(), cause);
            throw new S3UploadException("Failed to upload directory as zip to S3", cause);

        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid argument for directory '{}': {}", processedAudioFilePath, e.getMessage(), e);
            throw e;

        } catch (S3UploadException e) {
            LOGGER.error("Error during S3 upload for directory '{}': {}", processedAudioFilePath, e.getMessage(), e);
            throw e;

        } catch (Exception e) {
            LOGGER.error("Unexpected error during upload of directory '{}': {}", processedAudioFilePath, e.getMessage(), e);
            throw new S3UploadException("Failed to upload directory as zip to S3", e);
        }
    }

    /**
     * Prepares the directory with processed audio files for upload to S3, by renaming it
     * with a unique name and creating a ZIP file of the renamed directory.
     *
     * @param processedAudioFilePath The original directory with processed audios to prepare for upload.
     * @return zipFilePath The path to the ZIP file of the renamed directory.
     * @throws IOException If an I/O error occurs during directory preparation.
     */
    public Path prepareDirectoryForUpload(Path processedAudioFilePath) throws IOException {

        // Generate a unique name for the directory of processed audio files
        String uniqueDirectoryName = S3Utils.generateUniqueDirectoryName();

        // Rename the directory with the new unique name
        Path renamedDirectory = processedAudioFilePath.resolveSibling(uniqueDirectoryName);

        LOGGER.info("Renaming directory from '{}' to '{}'", processedAudioFilePath, renamedDirectory);

        // Move the original directory to the new unique name (this also makes processedAudioFilePath invalid after this point)
        Files.move(processedAudioFilePath, renamedDirectory);

        // Create a ZIP file for the renamed directory
        Path zipFilePath = S3Utils.toZipDirectory(renamedDirectory);

        LOGGER.info("Created ZIP file '{}' for directory '{}'", zipFilePath, renamedDirectory);

        return zipFilePath;
    }

    /**
     * Upload a large file from the local directory to S3 using TransferManager from AWS,
     * blocking until the upload is complete.
     *
     * @param zipS3DirectoryPath The path to the ZIP directory to upload.
     * @param bucketName The name of the S3 bucket.
     * @throws Exception If an error occurs during the upload process.
     */
    public void uploadFileFromLocalToS3(Path zipS3DirectoryPath, String bucketName) throws S3UploadException {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(SUB_BUCKET + zipS3DirectoryPath.getFileName().toString())
                    .build();

            UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                    .putObjectRequest(putObjectRequest)
                    .source(zipS3DirectoryPath)
                    .build();

            FileUpload fileUpload = s3TransferManager.uploadFile(uploadFileRequest);
            CompletableFuture<CompletedFileUpload> future = fileUpload.completionFuture();
            future.join(); // Wait until the upload is complete

            LOGGER.info("Successfully uploaded {} to S3 bucket {}", zipS3DirectoryPath, bucketName);
        } catch (CompletionException e) {
            // Unwrap and handle the actual cause
            Throwable cause = e.getCause();
            LOGGER.error("Error uploading file to S3 bucket '{}': {}", bucketName, cause.getMessage(), cause);
            throw new S3UploadException("Failed to upload file to S3", cause);
        } catch (Exception e) {
            LOGGER.error("Error uploading file to S3 bucket '{}': {}", bucketName, e.getMessage(), e);
            throw new S3UploadException("Failed to upload file to S3", e);
        } finally {
            s3TransferManager.close();
        }
    }

    /**
     * Create a pre-signed URL for directly downloading (GET) an object from an S3 bucket.
     *
     * @param bucketName The name of the S3 bucket.
     * @param zipS3DirectoryPath  The path to the ZIP file in S3.
     * @return A pre-signed URL for downloading the object, valid for a limited duration.
     */
    public String createPresignedGetRequest(String bucketName, Path zipS3DirectoryPath) {

        try {
            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(60))
                    .getObjectRequest(req -> req.bucket(bucketName).key(SUB_BUCKET + zipS3DirectoryPath.getFileName().toString()))
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

    /* Create a pre-signed URL to use in a subsequent PUT request of a File to an S3 bucket. */
    public String createPresignedPutRequest(String key, String contentType) {

        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (contentType == null || contentType.isEmpty()) {
            throw new IllegalArgumentException("Content-Type cannot be null or empty");
        }

        try {
            if (bucketName == null || bucketName.isEmpty()) {
                throw new IllegalStateException("S3 bucket name is not set in the environment variables.");
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

            String presignedUrl = presignedPutObjectRequest.url().toString();

            LOGGER.info("Presigned URL to upload a file to: [{}]", presignedUrl);
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

    /**
     * Validates input parameters.
     */
    private void validateParameters(String directoryPath, String bucketName) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            throw new IllegalArgumentException("Directory path cannot be null or empty");
        }
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("Bucket name cannot be null or empty");
        }
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
