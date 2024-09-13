package com.audiosource.backend.dto;

public class NotificationMessage {

    private String correlationId;
    private String downloadUrl;

    public NotificationMessage(String correlationId, String downloadUrl) {
        this.correlationId = correlationId;
        this.downloadUrl = downloadUrl;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    @Override
    public String toString() {
        return "NotificationMessage {" +
                "correlationId='" + correlationId + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }
}
