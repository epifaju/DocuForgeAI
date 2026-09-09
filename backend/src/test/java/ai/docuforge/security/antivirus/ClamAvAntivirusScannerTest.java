package ai.docuforge.security.antivirus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.config.AntivirusProperties;
import ai.docuforge.storage.StorageException;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class ClamAvAntivirusScannerTest {

    @TempDir
    Path tempDir;

    private ExecutorService executor;
    private ServerSocket serverSocket;
    private Path sampleFile;

    @BeforeEach
    void setUp() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        serverSocket = new ServerSocket(0);
        sampleFile = tempDir.resolve("upload.bin");
        Files.writeString(sampleFile, "clean-payload");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void acceptsOkResponse() throws Exception {
        Future<?> server = startClamd("stream: OK\0");
        ClamAvAntivirusScanner scanner = scannerFor(serverSocket.getLocalPort());

        assertThatCode(() -> scanner.scan(sampleFile, "upload.bin")).doesNotThrowAnyException();
        server.get(3, TimeUnit.SECONDS);
    }

    @Test
    void rejectsFoundAsMalware() throws Exception {
        Future<?> server = startClamd("stream: Eicar-Test-Signature FOUND\0");
        ClamAvAntivirusScanner scanner = scannerFor(serverSocket.getLocalPort());

        assertThatThrownBy(() -> scanner.scan(sampleFile, "eicar.bin"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> {
                    StorageException se = (StorageException) ex;
                    org.assertj.core.api.Assertions.assertThat(se.getCode()).isEqualTo("MALWARE_DETECTED");
                    org.assertj.core.api.Assertions.assertThat(se.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
        server.get(3, TimeUnit.SECONDS);
    }

    @Test
    void rejectsUnexpectedReply() throws Exception {
        Future<?> server = startClamd("stream: UNKNOWN\0");
        ClamAvAntivirusScanner scanner = scannerFor(serverSocket.getLocalPort());

        assertThatThrownBy(() -> scanner.scan(sampleFile, "upload.bin"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> {
                    StorageException se = (StorageException) ex;
                    org.assertj.core.api.Assertions.assertThat(se.getCode()).isEqualTo("ANTIVIRUS_ERROR");
                    org.assertj.core.api.Assertions.assertThat(se.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        server.get(3, TimeUnit.SECONDS);
    }

    @Test
    void connectionRefusedMapsToUnavailable() {
        int closedPort = serverSocket.getLocalPort();
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // already closed
        }
        ClamAvAntivirusScanner scanner = scannerFor(closedPort);

        assertThatThrownBy(() -> scanner.scan(sampleFile, "upload.bin"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> {
                    StorageException se = (StorageException) ex;
                    org.assertj.core.api.Assertions.assertThat(se.getCode()).isEqualTo("ANTIVIRUS_UNAVAILABLE");
                    org.assertj.core.api.Assertions.assertThat(se.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    private ClamAvAntivirusScanner scannerFor(int port) {
        return new ClamAvAntivirusScanner(new AntivirusProperties(true, "127.0.0.1", port, 2_000));
    }

    private Future<?> startClamd(String reply) {
        return executor.submit(() -> {
            try (Socket client = serverSocket.accept();
                 DataInputStream in = new DataInputStream(client.getInputStream());
                 OutputStream out = client.getOutputStream()) {

                byte[] cmd = in.readNBytes(10);
                if (!"zINSTREAM\0".equals(new String(cmd, StandardCharsets.US_ASCII))) {
                    throw new IllegalStateException("unexpected command");
                }
                while (true) {
                    int len = in.readInt();
                    if (len == 0) {
                        break;
                    }
                    in.readFully(new byte[len]);
                }
                out.write(reply.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
