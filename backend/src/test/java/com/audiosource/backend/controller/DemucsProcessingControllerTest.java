package com.audiosource.backend.controller;

import com.audiosource.backend.service.demucs.DemucsProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DemucsProcessingControllerTest {
    @Mock
    private DemucsProcessingService demucsProcessingService;

    @InjectMocks
    private DemucsProcessingController demucsProcessingController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void checkServiceStatus_ServiceReady_ReturnsOk() {

        when(demucsProcessingService.isReadyForProcessing()).thenReturn(true);

        ResponseEntity<String> response = demucsProcessingController.checkServiceStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Service is available for processing", response.getBody());
        verify(demucsProcessingService, times(1)).isReadyForProcessing();
    }

    @Test
    void checkServiceStatus_ServiceNotReady_ReturnsServiceUnavailable() {

        when(demucsProcessingService.isReadyForProcessing()).thenReturn(false);

        ResponseEntity<String> response = demucsProcessingController.checkServiceStatus();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Service is not ready for processing", response.getBody());
        verify(demucsProcessingService, times(1)).isReadyForProcessing();
    }

    @Test
    void checkServiceStatus_ExceptionThrown_ReturnsInternalServerError() {

        when(demucsProcessingService.isReadyForProcessing()).thenThrow(new RuntimeException("Unexpected error"));

        ResponseEntity<String> response = demucsProcessingController.checkServiceStatus();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An error occurred while checking the service status", response.getBody());
        verify(demucsProcessingService, times(1)).isReadyForProcessing();
    }
}
