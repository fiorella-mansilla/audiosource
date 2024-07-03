package com.audiosource.backend.controller;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/s3")
public class S3Controller {
    private final S3Service s3Service;
    private static final Logger logger = LoggerFactory.getLogger(S3Controller.class);

    @Autowired
    public S3Controller(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Endpoint to upload a directory as a ZIP file to S3 and return a pre-signed URL for downloading the ZIP file.
     *
     * @param directoryPath The local directory path to upload.
     * @param bucketName    The name of the S3 bucket.
     * @return ResponseEntity with a success message and pre-signed URL upon successful upload,
     *         or an error message if the upload fails.
     */
    @PostMapping("/upload/separatedFiles")
    public ResponseEntity<String> uploadDirectoryToS3(
            @RequestParam String directoryPath,
            @RequestParam String bucketName) {

        try {
            // Upload directory as a ZIP file to S3 and retrieve the pre-signed URL
            String presignedGetUrl = s3Service.uploadDirectoryAsZipToS3(directoryPath, bucketName);

            if (presignedGetUrl == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to upload directory or generate pre-signed URL.");
            }

            return ResponseEntity.ok("Successful Upload. Pre-signed URL: " + presignedGetUrl);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("An error occurred: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred: " + e.getMessage());
        }
    }

    /**
     * Creates a pre-signed PUT request for the user to upload a file to the S3 bucket.
     *
     * @param request A map containing the key and content_type for the object.
     * @return ResponseEntity with the pre-signed URL and additional data upon success,
     *         or an error message if the URL generation fails.
     */
    @PostMapping("/upload/signed_url")
    public ResponseEntity<?> createPresignedPutRequest(@RequestBody Map<String, String> request) {

        try {
            // All the put objects will be located in the `originals` S3 sub-bucket
            String key = request.get("key");
            String contentType = request.get("content_type");
            Map<String, String> data = s3Service.createPresignedPutRequest(key, contentType);
            return ResponseEntity.ok().body(data);
        } catch (Exception e) {
            logger.error("Error generating the pre-signed URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating the pre-signed URL");
        }
    }

    /**
     * Downloads the latest file from the specified S3 bucket.
     *
     * @param bucketName    The name of the S3 bucket.
     * @param directoryPath The local directory path to save the downloaded file.
     * @return ResponseEntity with a success message and file path upon successful download,
     *         or an error message if the download fails or no files are found.
     */
    @GetMapping("/download/latestFile")
    public ResponseEntity<String> downloadLatestFile(@RequestParam String bucketName,
                                                     @RequestParam String directoryPath) {
        try {
            List<S3ObjectDto> files = s3Service.listObjects(bucketName);

            // Find the latest File based on the last modified Date
            S3ObjectDto latestFile = files.stream()
                    .max(Comparator.comparing(S3ObjectDto::getLastModified))
                    .orElse(null);

            // If the latestFile is not null, call the getObjectFromBucket function to download it
            if(latestFile != null) {
                // Convert size from MB to bytes
                double sizeInMB = Double.parseDouble(latestFile.getSizeMB().replace(" MB", ""));
                long fileSizeInBytes = (long) (sizeInMB * 1024 * 1024);

                Optional<String> filePath = s3Service.getObjectFromBucket(bucketName, latestFile.getKey(), directoryPath, fileSizeInBytes);

                if (filePath.isPresent()) {
                    return ResponseEntity.ok("Successful download of the latest file from the S3 bucket: "
                            + filePath.get());
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Error retrieving the file from the S3 bucket");
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No files found in the S3 Bucket");
            }
        } catch (Exception e){
            logger.error("Error downloading the file from the S3 bucket: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error downloading the file from the S3 bucket");
        }
    }

    /**
     * Lists all files from the specified S3 bucket.
     *
     * @param bucketName The name of the S3 bucket.
     * @return List of S3ObjectDto representing the objects in the S3 bucket.
     */
    @GetMapping("/listObjects")
    public ResponseEntity<List<S3ObjectDto>> listObjects(@RequestParam String bucketName) {
        try {
            List<S3ObjectDto> objects = s3Service.listObjects(bucketName);
            return ResponseEntity.ok(objects);
        } catch (Exception e) {
            logger.error("Error listing objects from the S3 bucket: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }
}
