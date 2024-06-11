package com.audiosource.backend.service;

import com.audiosource.backend.dto.S3ObjectDto;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private Dotenv dotenv;

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

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
        String fileLink = "https://" + dotenv.get("S3_BUCKET") + ".s3." + dotenv.get("AWS_REGION") + ".amazonaws.com/" + key;

        Map<String, String> data = new HashMap<>();
        data.put("signedUrl", signedUrl);
        data.put("fileLink", fileLink);

        return data;
    }

    /* Downloads a file from the specified S3 bucket and keyName to your Local file system. */
    public Optional<String> getObject(String bucketName, String keyName, String directoryPath) {
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
        String formattedLastModified = formatLastModified(s3Object.lastModified());

        return new S3ObjectDto(
                s3Object.key(),
                sizeMB,
                formattedLastModified
        );
    }

    /* Formats an Instant to a readable date-time string. */
    private String formatLastModified(Instant lastModified) {
        return DATE_FORMATTER.format(lastModified);
    }
}
