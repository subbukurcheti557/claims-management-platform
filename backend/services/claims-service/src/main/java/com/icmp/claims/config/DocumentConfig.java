package com.icmp.claims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.icmp.claims.document.domain.EvidencePolicy;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** Object-storage client and the evidence rules. */
@Configuration
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentConfig {

    @Bean
    EvidencePolicy evidencePolicy(DocumentProperties props) {
        return new EvidencePolicy(props.maxFileSize().toBytes(), props.maxPerClaim(),
                props.photoRequiredForApproval());
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(DocumentProperties props) {
        DocumentProperties.Storage storage = props.storage();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .forcePathStyle(storage.pathStyleAccess())
                // SDK 2.30+ adds CRC checksums to every upload with aws-chunked framing by default; several
                // S3-compatible stores save that framing as part of the file. Only send checksums when required.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(10)));
        if (storage.endpoint() != null) {
            builder.endpointOverride(storage.endpoint());
        }
        if (storage.accessKey() != null && !storage.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
