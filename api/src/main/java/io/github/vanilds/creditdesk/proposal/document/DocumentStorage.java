package io.github.vanilds.creditdesk.proposal.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The only class that talks to S3. The API never moves file bytes: it <i>signs</i> URLs and the browser talks to
 * S3 directly. So a 10 MB upload does not hold an API thread or its memory, and the API needs no extra capacity
 * for files.
 *
 * <p>A presigned URL is a permission for <i>one</i> operation on <i>one</i> key, for a few minutes, signed with the
 * API's credentials. The upload URL also signs the Content-Type and Content-Length, so S3 itself refuses a file of
 * another type or size than the one the API validated.
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class DocumentStorage implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorage.class);

    /** What the browser must send with the presigned request, exactly. */
    public record SignedRequest(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }

    public record StoredObject(long size, String contentType) {
    }

    private final StorageProperties properties;
    private final S3Client s3;
    private final S3Presigner presigner;

    public DocumentStorage(StorageProperties properties) {
        this.properties = properties;
        AwsCredentialsProvider credentials = properties.accessKey() == null || properties.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build() // EC2 instance profile on AWS: no keys anywhere
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        Region region = Region.of(properties.region());
        // Path-style (host/bucket/key) for S3-compatible servers; real S3 uses virtual-host style
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(properties.endpoint() != null).build();

        var client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // Only send/validate checksums when S3 requires them: works the same on S3 and S3-compatible stores
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        var signer = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        if (properties.endpoint() != null) {
            client.endpointOverride(properties.endpoint());
            signer.endpointOverride(properties.presignEndpoint());
        }
        this.s3 = client.build();
        this.presigner = signer.build();
    }

    /**
     * Local and test only. Never fails the startup: the local S3 container may still be booting, so this retries
     * for a minute and then only logs — uploads fail with a clear error until the bucket exists.
     */
    @EventListener(ApplicationReadyEvent.class)
    void createBucketIfAsked() {
        if (!properties.createBucket()) {
            return;
        }
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                try {
                    s3.headBucket(b -> b.bucket(properties.bucket()));
                } catch (S3Exception missing) {
                    if (missing.statusCode() != 404) {
                        throw missing;
                    }
                    s3.createBucket(b -> b.bucket(properties.bucket()));
                    log.info("Created bucket {}", properties.bucket());
                }
                return;
            } catch (SdkException notReady) {
                log.info("Storage not ready yet ({}), retrying in 2 s", notReady.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Could not create bucket {}: uploads will fail until it exists", properties.bucket());
    }

    SignedRequest presignUpload(String key, String contentType, long size) {
        var signed = presigner.presignPutObject(p -> p
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(put -> put
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)));
        // Content-Length is set by the browser from the body; only Content-Type must be sent explicitly
        return new SignedRequest(signed.url().toString(), "PUT", Map.of("Content-Type", contentType),
                signed.expiration());
    }

    SignedRequest presignDownload(String key, String fileName) {
        var signed = presigner.presignGetObject(p -> p
                .signatureDuration(properties.downloadUrlTtl())
                .getObjectRequest(get -> get
                        .bucket(properties.bucket())
                        .key(key)
                        // S3 answers with this header, so the browser saves the file under its original name
                        .responseContentDisposition("attachment; filename*=UTF-8''"
                                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))));
        return new SignedRequest(signed.url().toString(), "GET", Map.of(), signed.expiration());
    }

    /** HEAD: metadata only, the file is not downloaded. Empty when nothing was uploaded under the key. */
    Optional<StoredObject> find(String key) {
        try {
            HeadObjectResponse head = s3.headObject(h -> h.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    Duration uploadUrlTtl() {
        return properties.uploadUrlTtl();
    }

    @Override
    public void close() {
        presigner.close();
        s3.close();
    }
}
