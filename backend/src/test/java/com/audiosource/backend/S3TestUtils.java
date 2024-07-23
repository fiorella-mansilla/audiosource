package com.audiosource.backend;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

public class S3TestUtils {

    public static PutObjectRequest createPutObjectRequest(String bucketName, String key, String contentType) {
        return PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
    }

    public static PutObjectPresignRequest createPutObjectPresignRequest(PutObjectRequest putObjectRequest) {
        return PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();
    }

}
