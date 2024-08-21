package com.audiosource.backend.service.s3;

import com.audiosource.backend.exception.S3UploadException;
import com.audiosource.backend.util.S3Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class S3UploadService {
    private final S3PresignedUrlService s3PresignedUrlService;
    private final S3TransferManager s3TransferManager;
    private static final String SUB_BUCKET = "separated/";
    private static final Logger LOGGER = LoggerFactory.getLogger(S3UploadService.class);

    @Autowired
    public S3UploadService(S3PresignedUrlService s3PresignedUrlService, S3TransferManager s3TransferManager) {
        this.s3PresignedUrlService = s3PresignedUrlService;
        this.s3TransferManager = s3TransferManager;
    }

    /**
     * Uploads a zipped directory with the separated audios to an S3 bucket
     * and returns a pre-signed URL for downloading the ZIP file.
     *
     * @param directoryPath The local directory path to upload.
     * @param bucketName    The name of the S3 bucket.
     * @return A pre-signed URL for downloading the uploaded ZIP file from S3, or null if an error occurs.
     */
    public String uploadDirectoryAsZipToS3(String directoryPath, String bucketName) throws S3UploadException {
        Path sourceDirectory = Paths.get(directoryPath);

        try {
            Path immediateChildDirectory = S3Utils.getImmediateChildDirectory(sourceDirectory);

            if (immediateChildDirectory == null) {
                throw new IllegalArgumentException("No immediate child directory found in " + directoryPath);
            }

            Path zipS3File = prepareDirectoryForUpload(immediateChildDirectory);

            uploadFileFromLocalToS3(zipS3File, bucketName);

            return s3PresignedUrlService.createPresignedGetRequest(bucketName, zipS3File);

        } catch (IOException | CompletionException e) {
            Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;
            LOGGER.error("Error during upload process for directory '{}': {}", directoryPath, cause.getMessage(), cause);
            throw new S3UploadException("Failed to upload directory as zip to S3", cause);

        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid argument for directory '{}': {}", directoryPath, e.getMessage(), e);
            throw e;

        } catch (S3UploadException e) {
            LOGGER.error("Error during S3 upload for directory '{}': {}", directoryPath, e.getMessage(), e);
            throw e;

        } catch (Exception e) {
            LOGGER.error("Unexpected error during upload of directory '{}': {}", directoryPath, e.getMessage(), e);
            throw new S3UploadException("Failed to upload directory as zip to S3", e);
        }
    }

    /**
     * Prepares a directory for upload to S3 by renaming it with a unique name and creating a ZIP file.
     *
     * @param originalDirectory The original directory path to prepare for upload.
     * @return The path to the ZIP file created for the directory.
     * @throws IOException If an I/O error occurs during directory preparation.
     */
    public Path prepareDirectoryForUpload(Path originalDirectory) throws IOException {

        String newDirectoryName = S3Utils.generateUniqueDirectoryName();
        Path renamedDirectory = originalDirectory.resolveSibling(newDirectoryName);
        Files.move(originalDirectory, renamedDirectory);

        return S3Utils.toZipDirectory(renamedDirectory);
    }

    /**
     * Uploads a large file from the local directory to S3 using TransferManager from AWS,
     * blocking until the upload is complete.
     *
     * @param zipS3File The path to the ZIP file to upload.
     * @param bucketName The name of the S3 bucket.
     * @throws Exception If an error occurs during the upload process.
     */
    public void uploadFileFromLocalToS3(Path zipS3File, String bucketName) throws S3UploadException {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(SUB_BUCKET + zipS3File.getFileName().toString())
                    .build();

            UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                    .putObjectRequest(putObjectRequest)
                    .source(zipS3File)
                    .build();

            FileUpload fileUpload = s3TransferManager.uploadFile(uploadFileRequest);
            CompletableFuture<CompletedFileUpload> future = fileUpload.completionFuture();
            future.join(); // Wait until the upload is complete

            LOGGER.info("Successfully uploaded {} to S3 bucket {}", zipS3File, bucketName);
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
}
