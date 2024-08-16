package com.audiosource.backend.service;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.exception.S3UploadException;
import com.audiosource.backend.util.S3Utils;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
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
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Service
public class S3Service {
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3AsyncClient s3AsyncClient;
    private final S3TransferManager s3TransferManager;
    private final Dotenv dotenv;

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Service.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");
    private static final String SUB_BUCKET = "separated/";

    @Autowired
    public S3Service(S3Presigner s3Presigner, S3Client s3Client, S3AsyncClient s3AsyncClient, S3TransferManager s3TransferManager, Dotenv dotenv) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.s3AsyncClient = s3AsyncClient;
        this.s3TransferManager = s3TransferManager;
        this.dotenv = dotenv;
    }

    /**
     * Uploads a zipped directory with the separated audios to an S3 bucket
     * and returns a pre-signed URL for downloading the ZIP file.
     *
     * @param directoryPath The local directory path to upload.
     * @param bucketName    The name of the S3 bucket.
     * @return A pre-signed URL for downloading the uploaded ZIP file from S3, or null if an error occurs.
     */
    public String uploadDirectoryAsZipToS3(String directoryPath, String bucketName) throws S3UploadException {
        Path sourceDirectory = Paths.get(directoryPath);

        try {
            Path immediateChildDirectory = S3Utils.getImmediateChildDirectory(sourceDirectory);

            if (immediateChildDirectory == null) {
                throw new IllegalArgumentException("No immediate child directory found in " + directoryPath);
            }

            Path zipS3File = prepareDirectoryForUpload(immediateChildDirectory);

            uploadFileFromLocalToS3(zipS3File, bucketName);

            return createPresignedGetRequest(bucketName, zipS3File);

        } catch (IOException | CompletionException e) {
            Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;
            LOGGER.error("Error during upload process for directory '{}': {}", directoryPath, cause.getMessage(), cause);
            throw new S3UploadException("Failed to upload directory as zip to S3", cause);

        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid argument for directory '{}': {}", directoryPath, e.getMessage(), e);
            throw e;

        } catch (S3UploadException e) {
            LOGGER.error("Error during S3 upload for directory '{}': {}", directoryPath, e.getMessage(), e);
            throw e;

        } catch (Exception e) {
            LOGGER.error("Unexpected error during upload of directory '{}': {}", directoryPath, e.getMessage(), e);
            throw new S3UploadException("Failed to upload directory as zip to S3", e);
        }
    }

    /**
     * Prepares a directory for upload to S3 by renaming it with a unique name and creating a ZIP file.
     *
     * @param originalDirectory The original directory path to prepare for upload.
     * @return The path to the ZIP file created for the directory.
     * @throws IOException If an I/O error occurs during directory preparation.
     */
    public Path prepareDirectoryForUpload(Path originalDirectory) throws IOException {

        String newDirectoryName = S3Utils.generateUniqueDirectoryName();
        Path renamedDirectory = originalDirectory.resolveSibling(newDirectoryName);
        Files.move(originalDirectory, renamedDirectory);

        return S3Utils.toZipDirectory(renamedDirectory);
    }

    /**
     * Uploads a large file from the local directory to S3 using TransferManager from AWS,
     * blocking until the upload is complete.
     *
     * @param zipS3File The path to the ZIP file to upload.
     * @param bucketName The name of the S3 bucket.
     * @throws Exception If an error occurs during the upload process.
     */
    public void uploadFileFromLocalToS3(Path zipS3File, String bucketName) throws S3UploadException {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(SUB_BUCKET + zipS3File.getFileName().toString())
                    .build();

            UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                    .putObjectRequest(putObjectRequest)
                    .source(zipS3File)
                    .build();

            FileUpload fileUpload = s3TransferManager.uploadFile(uploadFileRequest);
            CompletableFuture<CompletedFileUpload> future = fileUpload.completionFuture();
            future.join(); // Wait until the upload is complete

            LOGGER.info("Successfully uploaded {} to S3 bucket {}", zipS3File, bucketName);
        } catch (CompletionException e) {
            // Unwrap and handle the actual cause
            Throwable cause = e.getCause();
            LOGGER.error("Error uploading file to S3 bucket '{}': {}", bucketName, cause.getMessage(), cause);
            throw new S3UploadException("Failed to upload file to S3", cause);
        } catch (Exception e) {
            LOGGER.error("Error uploading file to S3 bucket '{}': {}", bucketName, e.getMessage(), e);
            throw new S3UploadException("Failed to upload file to S3", e);
        } finally {
            s3TransferManager.close();
        }
    }

    /**
     * Creates a pre-signed URL for directly downloading an object from an S3 bucket.
     *
     * @param bucketName The name of the S3 bucket.
     * @param zipS3File  The path to the ZIP file in S3.
     * @return A pre-signed URL for downloading the object, valid for a limited duration.
     */
    public String createPresignedGetRequest(String bucketName, Path zipS3File) {

        try {
            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(60))
                    .getObjectRequest(req -> req.bucket(bucketName).key(SUB_BUCKET + zipS3File.getFileName().toString()))
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
            return presignedGetObjectRequest.url().toString();

        } catch (S3Exception e) {
            LOGGER.error("S3 exception occurred: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL due to S3 error.", e);

        } catch (SdkException e) {
            // Handle general AWS SDK exceptions
            LOGGER.error("SDK exception occurred: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL due to SDK error.", e);

        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL due to an unexpected error.", e);
        }
    }

    /* Download a file from the specified S3 bucket and keyName to the Local file system. */
    public Optional<String> getObjectFromBucket(String bucketName, String keyName, String directoryPath, long fileSizeInBytes) {

        final long LARGE_FILE_THRESHOLD = 100 * 1024 * 1024; // 100MB

        try {
            int titleStart = keyName.indexOf("/");
            String fileName = keyName.substring(titleStart + 1);
            String filePath = directoryPath + fileName;
            File myAudioFile = new File(filePath);

            if (fileSizeInBytes > LARGE_FILE_THRESHOLD) {

                /* If the file is larger than 100MB, then we use S3TransferManager for retrieving it */
                DownloadFileRequest downloadFileRequest = DownloadFileRequest.builder()
                        .getObjectRequest(b -> b.bucket(bucketName).key(keyName))
                        .addTransferListener(LoggingTransferListener.create())  // Add listener.
                        .destination(myAudioFile.toPath())
                        .build();

                FileDownload downloadFile = s3TransferManager.downloadFile(downloadFileRequest);

                CompletedFileDownload downloadResult = downloadFile.completionFuture().join(); // Wait for the download to complete

                LOGGER.info("Content length [{}]", downloadResult.response().contentLength());
                LOGGER.info("Successfully downloaded {} to {}", keyName, filePath);

            } else {
                // Otherwise, we use GetObjectRequest for smaller files
                GetObjectRequest getObjectRequest = GetObjectRequest
                        .builder()
                        .bucket(bucketName)
                        .key(keyName)
                        .build();

                ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
                byte[] data = objectBytes.asByteArray();

                // Write the data to a local file
                try (OutputStream outputStream = new FileOutputStream(myAudioFile)) {
                    outputStream.write(data);
                }
                LOGGER.info("Successfully obtained bytes from S3 object {}", keyName);
            }
            return Optional.of(filePath);

        } catch(IOException exc) {
            LOGGER.error("IO error while getting object from bucket '{}': {}", bucketName, exc.getMessage(), exc);
            return Optional.empty();
        } catch(S3Exception e) {
            LOGGER.error("S3 error while getting object from bucket '{}': {}", bucketName, e.awsErrorDetails().errorMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Error downloading object from S3 bucket '{}': {}", bucketName, e.getMessage(), e);
            return Optional.empty();
        } finally {
            s3TransferManager.close();
        }
    }

    /* List all files from the specified AWS S3 bucket, excluding empty directories. */
    public List<S3ObjectDto> listObjects(String bucketName) {

        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request
                .builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listObjectsRequest);
        LOGGER.info("Listed objects in bucket '{}'", bucketName);

        return response.contents().stream()
                .filter(s3Object -> !s3Object.key().endsWith("/") || s3Object.size() != 0)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /* Convert an S3Object to an S3ObjectDto, formatting the size to MB with one decimal place
     * and the last modified date to a readable format. */
    public S3ObjectDto toDto(S3Object s3Object) {

        double size = s3Object.size() / (1024.0 * 1024.0);
        String sizeMB = DECIMAL_FORMAT.format(size) + " MB";
        String formattedLastModified = S3Utils.formatLastModified(s3Object.lastModified());

        return new S3ObjectDto(
                s3Object.key(),
                sizeMB,
                formattedLastModified
        );
    }

    /* Creates a pre-signed URL to use in a subsequent PUT request of a File to an S3 bucket. */
    public String createPresignedPutRequest(String key, String contentType) {

        try {
            String bucketName = dotenv.get("S3_BUCKET");

            if (bucketName == null || bucketName.isEmpty()) {
                String errorMessage = "S3 bucket name is not set in the environment variables.";
                LOGGER.error(errorMessage);
                throw new IllegalStateException(errorMessage); // Ensure the method fails visibly
            }

            // Create a PutObjectRequest to be pre-signed
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
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

            LOGGER.info("Presigned URL to upload a file to: [{}]", signedUrl);
            LOGGER.info("HTTP method: [{}]", presignedPutObjectRequest.httpRequest().method());

            return presignedPutObjectRequest.url().toExternalForm();

        } catch (S3Exception e) {
            LOGGER.error("S3 error while generating presigned PUT URL for key [{}]: {}", key, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT URL", e);

        } catch (SdkException e) {
            LOGGER.error("SDK error while generating presigned PUT URL for key [{}]: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT URL", e);

        } catch (Exception e) {
            LOGGER.error("Unexpected error while generating presigned PUT URL for key [{}]: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT URL", e);
        }
    }

    /* Delete a specific object (file/directory) from the S3 bucket */
    public void deleteObjectFromS3(String bucketName, String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            LOGGER.info("Successfully deleted object [{}] from bucket [{}]", key, bucketName);
        } catch (S3Exception e) {
            LOGGER.error("Failed to delete object [{}] from bucket [{}]: {}",
                    key, bucketName, e.awsErrorDetails().errorMessage());
        }
    }
}
