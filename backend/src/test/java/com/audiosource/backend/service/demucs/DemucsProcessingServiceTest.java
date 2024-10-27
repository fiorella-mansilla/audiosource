package com.audiosource.backend.service.demucs;

import com.audiosource.backend.enums.OutputFormat;
import com.audiosource.backend.enums.SeparationType;
import com.audiosource.backend.exception.DemucsProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DemucsProcessingServiceTest {

    @TempDir
    Path tempDir; // JUnit will automatically clean up this directory after tests

    @Mock
    private ProcessBuilder processBuilderMock;

    @Mock
    private Process processMock;

    @InjectMocks
    private DemucsProcessingService demucsProcessingService;

    private Path demucsOutputDirectory;
    private Path pythonEnvPath;
    private String testAudioFilePath;

    @BeforeEach
    void setUp() throws IOException {
        demucsOutputDirectory = Files.createDirectory(tempDir.resolve("demucsOutput"));
        pythonEnvPath = Files.createFile(tempDir.resolve("pythonEnv"));
        testAudioFilePath = Files.createFile(tempDir.resolve("audioFile.wav")).toString();

        // Set the values for the fields in the DemucsProcessingService instance
        ReflectionTestUtils.setField(demucsProcessingService, "demucsOutputDirectory", demucsOutputDirectory.toString());
        ReflectionTestUtils.setField(demucsProcessingService, "pythonEnvPath", pythonEnvPath.toString());
    }

    /* Tests of 'processRetrievedAudioFile' method */
    @Test
    void processAudioFile_ReturnsProcessedFilePath() throws Exception {
        DemucsProcessingService spyService = spy(demucsProcessingService);
        doReturn(true).when(spyService).isReadyForProcessing();
        doNothing().when(spyService).executeCommand(any(String[].class));

        String expectedPath = Paths.get(demucsOutputDirectory.toString(), "separated", "htdemucs", "audioFile").toString();

        String resultPath = spyService.processRetrievedAudioFile(testAudioFilePath, SeparationType.STEMS_SPLITTER, OutputFormat.WAV);

        assertEquals(expectedPath, resultPath, "Processed file path should match expected path.");
        verify(spyService, times(1)).executeCommand(any(String[].class));
    }

    @Test
    void processAudioFile_ThrowsException_WhenServiceNotReady() {
        DemucsProcessingService spyService = spy(demucsProcessingService);
        doReturn(false).when(spyService).isReadyForProcessing();

        DemucsProcessingException exception = assertThrows(DemucsProcessingException.class,
                () -> spyService.processRetrievedAudioFile(testAudioFilePath, SeparationType.STEMS_SPLITTER, OutputFormat.WAV));

        assertEquals("Service is not ready for processing. Check environment and output directory.", exception.getMessage());
    }

    @Test
    void processAudioFile_ThrowsException_WhenAudioFileNotFound() {
        String nonexistentFilePath = tempDir.resolve("nonexistentFile.wav").toString();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> demucsProcessingService.validateAudioFile(nonexistentFilePath));

        assertEquals("Audio file not found: " + nonexistentFilePath, exception.getMessage());
    }

    @Test
    void processAudioFile_ThrowsDemucsProcessingException_WhenExecutionFails() throws Exception {
        DemucsProcessingService spyService = spy(demucsProcessingService);
        doReturn(true).when(spyService).isReadyForProcessing();
        doThrow(new IOException("Execution failed")).when(spyService).executeCommand(any(String[].class));

        DemucsProcessingException exception = assertThrows(DemucsProcessingException.class,
                () -> spyService.processRetrievedAudioFile(testAudioFilePath, SeparationType.STEMS_SPLITTER, OutputFormat.WAV));

        assertTrue(exception.getMessage().contains("Error processing file " + testAudioFilePath));
        assertTrue(exception.getCause() instanceof IOException);
    }

    /* Tests of 'constructProcessedFilePath' method */
    @Test
    void constructProcessedFilePath_ReturnsCorrectPath() throws IOException {
        String originalAudioFilePath = tempDir.resolve("testAudioFile.wav").toString();
        Files.createFile(Paths.get(originalAudioFilePath));

        ReflectionTestUtils.setField(demucsProcessingService, "demucsOutputDirectory", demucsOutputDirectory.toString());

        String expectedPath = Paths.get(demucsOutputDirectory.toString(), "separated", "htdemucs", "testAudioFile").toString();
        String actualPath = demucsProcessingService.constructProcessedFilePath(originalAudioFilePath);

        assertEquals(expectedPath, actualPath, "The constructed processed file path should match the expected path.");
    }

    /* Tests of 'validateAudioFile' method */
    @Test
    void validateAudioFile_Succeeds_WhenFileExists() {

        Path testFile = tempDir.resolve("audioFile.wav");
        File existingFile = testFile.toFile();
        assertDoesNotThrow(existingFile::createNewFile); // Create the file and ensure no errors occur
        assertDoesNotThrow(
                () -> demucsProcessingService.validateAudioFile(existingFile.getAbsolutePath()),
                "Validation should succeed for an existing file path."
        );
    }

    @Test
    void validateAudioFile_ThrowsException_WhenFileDoesNotExist() {

        Path nonExistentFile = tempDir.resolve("nonExistentAudioFile.wav");
        assertThrows(
                IllegalArgumentException.class,
                () -> demucsProcessingService.validateAudioFile(nonExistentFile.toString()),
                "Validation should throw IllegalArgumentException when the file does not exist."
        );
    }

    /* Tests of 'constructCommandArgs' methods */
    @Test
    void testConstructCommandArgs_ReturnsDefaultArgs_WhenSeparationAndOutputFormatNotSpecified() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "-d", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructDefaultCommandArgs(testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "Default command args should be correctly constructed.");
    }

    @Test
    void testConstructCommandArgs_ReturnsVocalCommandArgs_WhenSeparationIsVocals() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "--two-stems=vocals", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructVocalsCommandArgs(testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "Vocals command args should be correctly constructed.");
    }

    @Test
    void testConstructCommandArgs_ReturnsMp3CommandArgs_WhenOutputFormatIsMp3() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "--mp3", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructMp3CommandArgs(testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "MP3 command args should be correctly constructed.");
    }

    @Test
    void testConstructCommandArgs_ReturnsVocalsMp3CommandArgs_WhenSeparationIsVocalsAndOutputIsMp3() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "--two-stems=vocals", "--mp3", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructCommandArgs(
                SeparationType.VOCAL_REMOVER, OutputFormat.MP3, testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "Command args for vocals removal with MP3 output should be correctly constructed.");
    }

    @Test
    void testConstructCommandArgs_ReturnsVocalsWavCommandArgs_WhenSeparationIsVocalsAndOutputIsWav() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "--two-stems=vocals", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructCommandArgs(
                SeparationType.VOCAL_REMOVER, OutputFormat.WAV, testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "Command args for vocals removal with WAV output should be correctly constructed.");
    }

    @Test
    void testConstructCommandArgs_ReturnsCommandArgs_WhenSeparationIsStemsWithMp3() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "--mp3", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructCommandArgs(
                SeparationType.STEMS_SPLITTER, OutputFormat.MP3, testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "Command args for full split with MP3 output should be correctly constructed.");
    }

    @Test
    void testConstructCommandArgs_ReturnsCommandArgs_WhenSeparationIsStemsWithWav() {
        String[] expectedArgs = {pythonEnvPath.toString(), "-m", "demucs", "-d", "cpu", testAudioFilePath};

        String[] actualArgs = demucsProcessingService.constructCommandArgs(
                SeparationType.STEMS_SPLITTER, OutputFormat.WAV, testAudioFilePath);

        assertArrayEquals(expectedArgs, actualArgs, "Command args for stems splitter with WAV output should be correctly constructed.");
    }

    /* Tests of 'isReadyForProcessing' method */

    @Test
    void testIsReadyForProcessing_WhenEnvironmentIsReady_ReturnTrue() throws IOException {
        // Make the pythonEnvPath executable and demucsOutputDir writable to simulate a ready environment.
        pythonEnvPath.toFile().setExecutable(true);
        demucsOutputDirectory.toFile().setWritable(true);

        assertTrue(demucsProcessingService.isReadyForProcessing(), "Service should be ready for processing.");
    }

    @Test
    void testIsReadyForProcessing_WhenPythonEnvIsNotExecutable_ReturnFalse() {
        pythonEnvPath.toFile().setExecutable(false);
        demucsOutputDirectory.toFile().setWritable(true);

        assertFalse(demucsProcessingService.isReadyForProcessing(), "Service should not be ready if pythonEnv is not executable.");
    }

    @Test
    void testIsReadyForProcessing_WhenOutputDirectoryIsNotWritable_ReturnFalse() {
        pythonEnvPath.toFile().setExecutable(true);
        demucsOutputDirectory.toFile().setWritable(false);

        assertFalse(demucsProcessingService.isReadyForProcessing(), "Service should not be ready if output directory is not writable.");
    }

    @Test
    void testIsReadyForProcessing_WhenPythonEnvDoesNotExist_ReturnFalse() {
        pythonEnvPath.toFile().delete();
        demucsOutputDirectory.toFile().setWritable(true);

        assertFalse(demucsProcessingService.isReadyForProcessing(), "Service should not be ready if Python environment does not exist.");
    }

    @Test
    void testIsReadyForProcessing_WhenOutputDirectoryDoesNotExist_ReturnFalse() {
        demucsOutputDirectory.toFile().delete();
        pythonEnvPath.toFile().setExecutable(true);

        assertFalse(demucsProcessingService.isReadyForProcessing(), "Service should not be ready if output directory does not exist.");
    }

}
