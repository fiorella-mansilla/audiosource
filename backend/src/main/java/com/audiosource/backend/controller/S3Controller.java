package com.audiosource.backend.controller;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/s3")
public class S3Controller {

    @Autowired
    private S3Service s3Service;

    @PostMapping("/upload/signed_url")
    public ResponseEntity<?> createPresignedPost(@RequestBody Map<String, String> request) {

        try {
            // All the put objects will be located in the `originals` S3 sub-bucket
            String key = "originals/" + request.get("key");
            String contentType = request.get("content_type");
            Map<String, String> data = s3Service.createPresignedPost(key, contentType);
            return ResponseEntity.ok().body(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error generating the pre-signed URL");
        }
    }

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
                Optional<String> filePath = s3Service.getObjectFromBucket(bucketName, latestFile.getKey(), directoryPath);
                if (filePath.isPresent()) {
                    return ResponseEntity.ok().body("Successful download of the latest file from the S3 bucket : " + filePath.get());
                } else {
                    return ResponseEntity.status(500).body("Error retrieving the file from the S3 bucket");
                }
            } else {
                return ResponseEntity.status(404).body("No files found in the S3 Bucket");
            }

        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error downloading the file from the S3 bucket");
        }
    }

    /* Lists all files from the specified S3 bucket. */
    @GetMapping("/listObjects")
    public List<S3ObjectDto> listObjects(@RequestParam String bucketName) {
        return s3Service.listObjects(bucketName);
    }
}
