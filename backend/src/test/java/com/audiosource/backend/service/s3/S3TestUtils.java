package com.audiosource.backend.service.s3;

import com.audiosource.backend.util.S3Utils;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;

public class S3TestUtils {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");

    public static S3Object createTestS3Object(long sizeInBytes, Instant lastModified) {
        return S3Object.builder()
                .key("test-file.mp3")
                .size(sizeInBytes)
                .lastModified(lastModified)
                .build();
    }

    public static ListObjectsV2Response createListObjectsResponse(List<S3Object> s3Objects) {
        return ListObjectsV2Response.builder()
                .contents(s3Objects)
                .build();
    }

    public static String formatSize(double sizeInBytes) {
        return DECIMAL_FORMAT.format(sizeInBytes / (1024.0 * 1024.0)) + " MB";
    }

    public static String formatLastModified(Instant lastModified) {
        // Assuming S3Utils.formatLastModified returns a formatted date string
        return S3Utils.formatLastModified(lastModified);
    }
}
