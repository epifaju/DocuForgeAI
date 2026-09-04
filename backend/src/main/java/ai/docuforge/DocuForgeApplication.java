package ai.docuforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocuForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocuForgeApplication.class, args);
    }
}