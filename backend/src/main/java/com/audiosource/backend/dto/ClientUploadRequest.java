package com.audiosource.backend.dto;

/* This DTO represents the exact structure of the incoming JSON from the client
and will give Spring Boot the ability to automatically map the JSON fields to this object.*/
public class ClientUploadRequest {
    private String keyName;
    private long fileSize;
    private String separationType;
    private String outputFormat;
    private String userEmail;

    public ClientUploadRequest() {
    }

    public ClientUploadRequest(String keyName, long fileSize, String separationType, String outputFormat, String userEmail) {
        this.keyName = keyName;
        this.fileSize = fileSize;
        this.separationType = separationType;
        this.outputFormat = outputFormat;
        this.userEmail = userEmail;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSeparationType() {
        return separationType;
    }

    public void setSeparationType(String separationType) {
        this.separationType = separationType;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
