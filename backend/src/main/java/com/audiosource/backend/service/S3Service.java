package com.audiosource.backend.service;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.util.S3Utils;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class S3Service {

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3AsyncClient s3AsyncClient;

    @Autowired
    private Dotenv dotenv;

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");
    private static final String SUB_BUCKET = "separated/";

    /* Create a pre-signed URL to download an object in a subsequent GET request. */


    /* Upload a directory containing the processed audio files (output) from the local server to an AWS S3 bucket. */
    public Integer uploadDirectoryToS3(String directoryPath, String bucketName) {

        // Get full path of immediate child directory from the given directoryPath
        Path sourceDirectory = Paths.get(directoryPath);
        Path originalS30bject = S3Utils.getImmediateChildDirectory(sourceDirectory);

        if (originalS30bject == null) {
            logger.error("No immediate child directory found in {}", directoryPath);
            return -1;
        }

        // Create a new name with a unique identifier for the directory be uploaded to S3
        String newS3ObjectName = S3Utils.generateUniqueDirectoryName() + ".zip";

        // Rename source Directory
        try {
            Path newS3Object = originalS30bject.resolveSibling(newS3ObjectName);
            Files.move(originalS30bject, newS3Object);
        } catch (IOException e) {
            logger.error("Failed to rename the source Directory", e);
            return -1;
        }

        // Create S3TransferManager instance
        S3TransferManager transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();

        DirectoryUpload directoryUpload = transferManager.uploadDirectory(UploadDirectoryRequest.builder()
                .source(sourceDirectory)
                .bucket(bucketName)
                .s3Prefix(SUB_BUCKET)
                .build());

        // Wait for the transfer to be completed
        CompletedDirectoryUpload completedDirectoryUpload = directoryUpload.completionFuture().join();

        // Log out and clean up any failed uploads
        completedDirectoryUpload.failedTransfers()
                .forEach(fail -> {
                    String keyFailedUpload = SUB_BUCKET + fail.toString();
                    logger.warn("Object [{}] failed to transfer, cleaning up....", keyFailedUpload);
                    deleteObjectFromS3(bucketName, keyFailedUpload);
                });

        return completedDirectoryUpload.failedTransfers().size();
    }

    /* Deletes a specific object (file/directory) from the S3 bucket */
    public void deleteObjectFromS3(String bucketName, String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            logger.info("Successfully deleted object [{}] from bucket [{}]", key, bucketName);
        } catch (S3Exception e) {
            logger.error("Failed to delete object [{}] from bucket [{}]: {}",
                    key, bucketName, e.awsErrorDetails().errorMessage());
        }
    }

    /* Creates a pre-signed URL with the link of the Object to use in a subsequent PUT request of a File to an S3 bucket. */
    public Map<String, String> createPresignedPutRequest(String key, String contentType){

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
        String fileLink = "https://" + dotenv.get("S3_BUCKET") + ".s3." + dotenv.get("AWS_REGION") + ".amazonaws.com/" + key;

        Map<String, String> data = new HashMap<>();
        data.put("signedUrl", signedUrl);
        data.put("fileLink", fileLink);

        return data;
    }

    /* Downloads a file from the specified S3 bucket and keyName to your Local file system. */
    public Optional<String> getObjectFromBucket(String bucketName, String keyName, String directoryPath) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest
                    .builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            byte[] data = objectBytes.asByteArray();

            int titleStart = keyName.indexOf("/");
            String fileName = keyName.substring(titleStart + 1);
            String filePath = directoryPath + fileName;

            // Write the data to a local file
            File myAudioFile = new File(filePath);
            OutputStream outputStream = new FileOutputStream(myAudioFile);
            outputStream.write(data);
            System.out.println("Successfully obtained bytes from an S3 object");
            outputStream.close();
            return Optional.of(filePath);

        } catch(IOException exc) {
            exc.printStackTrace();
            return Optional.empty();
        } catch(S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
            return Optional.empty();
        }
    }

    /* Lists all files from the specified AWS S3 bucket, excluding empty directories. */
    public List<S3ObjectDto> listObjects(String bucketName) {

        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request
                .builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listObjectsRequest);

        return response.contents().stream()
                .filter(s3Object -> !s3Object.key().endsWith("/") || s3Object.size() != 0)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /* Converts an S3Object to an S3ObjectDto, formatting the size to MB with one decimal place
     * and the last modified date to a readable format. */
    private S3ObjectDto toDto(S3Object s3Object) {

        double size = s3Object.size() / (1024.0 * 1024.0);
        String sizeMB = DECIMAL_FORMAT.format(size) + " MB";
        String formattedLastModified = S3Utils.formatLastModified(s3Object.lastModified());

        return new S3ObjectDto(
                s3Object.key(),
                sizeMB,
                formattedLastModified
        );
    }
}
