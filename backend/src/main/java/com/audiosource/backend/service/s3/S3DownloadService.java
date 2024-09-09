package com.audiosource.backend.service.s3;

import com.audiosource.backend.dto.AudioFileMessage;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

@Service
public class S3DownloadService {
    private final S3Client s3Client;
    private final S3TransferManager s3TransferManager;
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(S3DownloadService.class);

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${demucs.inputDirectory}")
    private String originalDirectoryPath;

    @Autowired
    public S3DownloadService(S3Client s3Client, S3TransferManager s3TransferManager) {
        this.s3Client = s3Client;
        this.s3TransferManager = s3TransferManager;
    }

    /* Download a file from the specified S3 bucket and keyName to the Local file system. */
    public Optional<String> getObjectFromBucket(AudioFileMessage message) {
        long fileSize = message.getFileSize();
        String keyName = message.getKeyName();

        final long LARGE_FILE_THRESHOLD = 100 * 1024 * 1024; // 100MB
        try {
            String fileName = keyName.substring(keyName.lastIndexOf("/") + 1);
            String originalFilePath = originalDirectoryPath + fileName;
            File originalAudioFile = new File(originalFilePath);

            if (fileSize > LARGE_FILE_THRESHOLD) {
                /* If the file is larger than 100MB, then we use S3TransferManager for retrieving it */
                DownloadFileRequest downloadFileRequest = DownloadFileRequest.builder()
                        .getObjectRequest(b -> b.bucket(bucketName).key(keyName))
                        .addTransferListener(LoggingTransferListener.create())  // Add listener.
                        .destination(originalAudioFile.toPath())
                        .build();

                FileDownload downloadFile = s3TransferManager.downloadFile(downloadFileRequest);
                CompletedFileDownload downloadResult = downloadFile.completionFuture().join(); // Wait for the download to complete

                LOGGER.info("Content length [{}]", downloadResult.response().contentLength());
                LOGGER.info("Downloaded large file {} to {}", keyName, originalFilePath);
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
                try (OutputStream outputStream = new FileOutputStream(originalAudioFile)) {
                    outputStream.write(data);
                }
                LOGGER.info("Downloaded small file {} to {}", keyName, originalFilePath);
            }
            return Optional.of(originalFilePath);
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
}
