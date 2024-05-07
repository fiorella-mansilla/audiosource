package com.audiosource.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${PORT}")
    private int port;

    @Value("${AWS_ACCESS_KEY_ID}")
    private String awsAccessKeyId;

    @Value("${AWS_SECRET_KEY}")
    private String awsSecretKey;

    @Value("${AWS_S3_BUCKET_NAME}")
    private String awsS3BucketName;

    private final String awsRegion = "eu-north-1";

    public int getPort() {
        return port;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public String getAwsS3BucketName() {
        return awsS3BucketName;
    }

    public String getAwsRegion() {
        return awsRegion;
    }
}
