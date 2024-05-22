package com.audiosource.backend.dto;

public class S3ObjectDto {
    private String key;
    private String sizeMB;
    private String lastModified;

    public S3ObjectDto(String key, String sizeMB, String lastModified) {
        this.key = key;
        this.sizeMB = sizeMB;
        this.lastModified = lastModified;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSizeMB() {
        return sizeMB;
    }

    public void setSizeMB(String sizeMB) {
        this.sizeMB = sizeMB;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
}
