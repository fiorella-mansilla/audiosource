package com.audiosource.backend.controller;

import com.audiosource.backend.service.s3.S3DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/s3")
public class S3DownloadController {

    private final S3DownloadService s3DownloadService;
    private static final Logger logger = LoggerFactory.getLogger(S3DownloadController.class);

    @Autowired
    public S3DownloadController(S3DownloadService s3DownloadService) {
        this.s3DownloadService = s3DownloadService;
    }

    //TODO: Retrieve bucketName from .env file
    /**
     * Downloads the latest file from the specified S3 bucket.
     *
     * @param bucketName    The name of the S3 bucket.
     * @param originalDirectoryPath The local directory path to save the downloaded file.
     * @return ResponseEntity with a success message and file path upon successful download,
     *         or an error message if the download fails or no files are found.
     */
    @GetMapping("/download/latest-files")
    public ResponseEntity<String> downloadLatestFile(@RequestParam String bucketName,
                                                     @RequestParam String originalDirectoryPath) {
        try {
            List<S3ObjectDto> files = s3DownloadService.listObjects(bucketName);

            // List files that are stored inside 'originals/' from the S3 bucket
            List<S3ObjectDto> originalFiles = files.stream()
                    .filter(file -> file.getKey().startsWith("originals/"))
                    .collect(Collectors.toList());

            // Retrieve the latest File from the original Files based on the last modified Date
            S3ObjectDto latestFile = originalFiles.stream()
                    .max(Comparator.comparing(S3ObjectDto::getLastModified))
                    .orElse(null);

            // If the latestFile is not null, call the getObjectFromBucket function to download it
            if(latestFile != null) {
                // Convert size from MB to bytes
                double sizeInMB = Double.parseDouble(latestFile.getSizeMB().replace(" MB", ""));
                long fileSizeInBytes = (long) (sizeInMB * 1024 * 1024);

                Optional<String> filePath = s3DownloadService.getObjectFromBucket(bucketName, latestFile.getKey(), originalDirectoryPath, fileSizeInBytes);

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
}
