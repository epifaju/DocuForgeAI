package ai.docuforge.security.antivirus;

import ai.docuforge.config.AntivirusProperties;
import ai.docuforge.storage.StorageException;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * ClamAV INSTREAM scan over TCP (clamd).
 */
@Component
@ConditionalOnProperty(prefix = "docuforge.antivirus", name = "enabled", havingValue = "true")
public class ClamAvAntivirusScanner implements AntivirusScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvAntivirusScanner.class);
    private static final int CHUNK = 8192;

    private final AntivirusProperties properties;

    public ClamAvAntivirusScanner(AntivirusProperties properties) {
        this.properties = properties;
    }

    @Override
    public void scan(Path file, String originalFilename) {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(properties.host(), properties.port()),
                    properties.timeoutMs()
            );
            socket.setSoTimeout(properties.timeoutMs());

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 InputStream fileIn = new BufferedInputStream(Files.newInputStream(file));
                 InputStream reply = socket.getInputStream()) {

                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

                byte[] buffer = new byte[CHUNK];
                int read;
                while ((read = fileIn.read(buffer)) != -1) {
                    out.writeInt(read);
                    out.write(buffer, 0, read);
                }
                out.writeInt(0);
                out.flush();

                String response = new String(reply.readAllBytes(), StandardCharsets.US_ASCII).trim();
                if (response.endsWith("OK")) {
                    return;
                }
                if (response.contains("FOUND")) {
                    log.warn("Antivirus blocked upload filename={} response={}", originalFilename, response);
                    throw new StorageException(
                            "MALWARE_DETECTED",
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "error.antivirus.malware"
                    );
                }
                throw new StorageException(
                        "ANTIVIRUS_ERROR",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "error.antivirus.unexpected"
                );
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("ClamAV unreachable host={}:{}", properties.host(), properties.port());
            throw new StorageException(
                    "ANTIVIRUS_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "error.antivirus.unavailable",
                    ex
            );
        }
    }
}
