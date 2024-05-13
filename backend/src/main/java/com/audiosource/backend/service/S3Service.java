package com.audiosource.backend.service;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class S3Service {

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private Dotenv dotenv;

    /* Creates a pre-signed URL with the link of the Object to use in a subsequent PUT request */
    public Map<String, String> createPresignedPost(String key, String contentType){

        // Create a PutObjectRequest to be pre-signed
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(dotenv.get("S3_BUCKET"))
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
        String fileLink = "https://" + dotenv.get("S3_BUCKET") + ".s3." + Region.EU_NORTH_1 + ".amazonaws.com/" + key;

        Map<String, String> data = new HashMap<>();
        data.put("signedUrl", signedUrl);
        data.put("fileLink", fileLink);

        return data;
    }
}
