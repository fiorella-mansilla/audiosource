package com.audiosource.backend.dto;

public class NotificationMessage {

    private final String correlationId;
    private final String downloadUrl;

    public NotificationMessage(String correlationId, String downloadUrl) {
        this.correlationId = correlationId;
        this.downloadUrl = downloadUrl;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Override
    public String toString() {
        return "NotificationMessage {" +
                "correlationId='" + correlationId + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }
}
