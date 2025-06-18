package com.fiec.voz_cidada.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Importações do AWS SDK 1.x
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.regions.Regions; // Para regiões na v1.x
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder; // Builder para a v1.x

@Configuration
public class S3Config {

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    /**
     * Configures and provides an AmazonS3 bean for interacting with AWS S3 using SDK 1.x.
     * Uses static credentials provided by application properties.
     *
     * @return A configured AmazonS3 instance.
     */
    @Bean
    public AmazonS3 s3Client() {
        // Cria credenciais AWS básicas a partir da access key e secret key
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

        // Constrói e retorna o AmazonS3 client
        return AmazonS3ClientBuilder.standard()
                // Fornece credenciais usando AWSStaticCredentialsProvider
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                // Define a região AWS. Regions.valueOf() é usado para SDK 1.x,
                // convertendo a string da região para o enum Regions (ex: "sa-east-1" -> Regions.SA_EAST_1)
                .withRegion(Regions.valueOf(region.toUpperCase().replace("-", "_")))
                .build();
    }
}
