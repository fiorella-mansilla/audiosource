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
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.Map;

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

    /* Lists all files from the specified S3 bucket. */
    @GetMapping("/listObjects")
    public List<S3ObjectDto> listObjects(@RequestParam String bucketName) {
        return s3Service.listObjects(bucketName);
    }
}
