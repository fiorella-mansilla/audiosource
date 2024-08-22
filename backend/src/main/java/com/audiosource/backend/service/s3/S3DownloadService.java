package com.audiosource.backend.service.s3;

import com.audiosource.backend.dto.S3ObjectDto;
import com.audiosource.backend.util.S3Utils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class S3DownloadService {
    private final S3Client s3Client;
    private final S3TransferManager s3TransferManager;
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(S3DownloadService.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");

    @Autowired
    public S3DownloadService(S3Client s3Client, S3TransferManager s3TransferManager) {
        this.s3Client = s3Client;
        this.s3TransferManager = s3TransferManager;
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

        } catch(IOException e) {
            LOGGER.error("IO error while getting object from bucket '{}': {}", bucketName, e.getMessage(), e);
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
        try {
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

        } catch (S3Exception e) {
            LOGGER.error("S3 error while listing objects in bucket '{}': {}", bucketName, e.awsErrorDetails().errorMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.error("Unexpected error while listing objects in bucket '{}': {}", bucketName, e.getMessage(), e);
            return Collections.emptyList();
        }
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
}
