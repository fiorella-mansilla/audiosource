package com.audiosource.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import static software.amazon.awssdk.transfer.s3.SizeConstant.MB;

@Configuration
public class AwsConfig {

    private final Dotenv dotenv;

    @Autowired
    public AwsConfig(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    private AwsBasicCredentials awsCredentials() {
        String awsAccessKeyId = dotenv.get("AWS_ACCESS_KEY_ID");
        String awsSecretKey = dotenv.get("AWS_SECRET_KEY");
        return AwsBasicCredentials.create(awsAccessKeyId, awsSecretKey);
    }

    private Region awsRegion() {
        String awsRegion = dotenv.get("AWS_REGION");
        return Region.of(awsRegion);
    }

    /* Create an S3-Presigner for the put Object and the pre-signed URL request. */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(awsRegion())
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials()))
                .build();
    }

    /* S3 Client that handles authentication and authorization for the Get Requests. */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(awsRegion())
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials()))
                .build();
    }

    @Bean
    public S3AsyncClient s3AsyncClient() {
        return S3AsyncClient.crtBuilder()
                .region(awsRegion())
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials()))
                .targetThroughputInGbps(20.0)
                .minimumPartSizeInBytes(8 * MB)
                .build();
    }

    @Bean
    public S3TransferManager transferManager() {
        S3AsyncClient s3AsyncClient = s3AsyncClient();
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }
}
