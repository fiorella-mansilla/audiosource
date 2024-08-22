package com.audiosource.backend.controller;

import com.audiosource.backend.exception.S3UploadException;
import com.audiosource.backend.service.s3.S3UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for handling S3 file upload operations.
 * Provides endpoints to upload files to S3 and generate pre-signed URLs.
 */
@RestController
@RequestMapping("/s3")
public class S3UploadController {

    private final S3UploadService s3UploadService;
    private static final Logger LOGGER = LoggerFactory.getLogger(S3UploadController.class);

    @Autowired
    public S3UploadController(S3UploadService s3UploadService) {
        this.s3UploadService = s3UploadService;
    }

    /**
     * Uploads a zipped directory with the processed files to S3
     * and returns a pre-signed URL for downloading the ZIP file.
     *
     * @param directoryPath The local directory path to upload.
     * @param bucketName    The name of the S3 bucket.
     * @return ResponseEntity with a success message and pre-signed URL upon successful upload,
     *         or an error message if the upload fails.
     */
    @PostMapping("/upload/processed-files")
    public ResponseEntity<String> uploadProcessedFilesToS3(
            @RequestParam String directoryPath,
            @RequestParam String bucketName) {

        try {
            String presignedGetUrl = s3UploadService.uploadDirectoryAsZipToS3(directoryPath, bucketName);
            LOGGER.info("Successfully uploaded directory: {} to bucket: {}. Generated URL: {}",
                    directoryPath, bucketName, presignedGetUrl);

            return ResponseEntity.ok("Successful upload. Pre-signed URL: " + presignedGetUrl);

        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid input for directoryPath: {} or bucketName: {}", directoryPath, bucketName, e);
            return ResponseEntity.badRequest().body("Invalid input: " + e.getMessage());
        } catch (S3UploadException e) {
            LOGGER.error("Upload error for directoryPath: {} or bucketName: {}", directoryPath, bucketName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error uploading directory to S3: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }


    /**
     * Creates a pre-signed PUT request for the user to directly upload a file to S3.
     *
     * @param request A map containing the key and content_type for the object.
     * @return ResponseEntity with the pre-signed URL and additional data upon success,
     *         or an error message if the URL generation fails.
     */
    @PostMapping("/upload/generate-signed-url")
    public ResponseEntity<?> createPresignedPutRequest(@RequestBody Map<String, String> request) {

        try {
            String key = request.get("key");
            String contentType = request.get("content_type");

            String data = s3UploadService.createPresignedPutRequest(key, contentType);
            LOGGER.info("Generated pre-signed URL for key: {}", key);
            return ResponseEntity.ok().body(data);

        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid request: {}", request, e);
            return ResponseEntity.badRequest().body("Invalid input: " + e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Error generating pre-signed URL for key: {}", request.get("key"), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate pre-signed URL: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error generating pre-signed URL for key: {}", request.get("key"), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while generating the pre-signed URL.");
        }
    }

}
