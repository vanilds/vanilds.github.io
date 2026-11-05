package io.github.vanilds.creditdesk.proposal.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Where documents live. On AWS only {@code bucket} and {@code region} are set: no endpoint (real S3) and no keys
 * (the EC2 instance profile provides short-lived credentials). Locally, SeaweedFS stands in for S3.
 *
 * @param endpoint       S3-compatible endpoint the API talks to; empty for real S3
 * @param publicEndpoint what goes into presigned URLs, i.e. the address the <i>browser</i> can reach
 *                       (in docker compose the API reaches "seaweedfs:8333", the browser "localhost:8333")
 * @param createBucket   create the bucket at startup if missing (local and tests only; on AWS it is infra code)
 */
@ConfigurationProperties("creditdesk.storage")
public record StorageProperties(
        String bucket,
        String region,
        URI endpoint,
        URI publicEndpoint,
        String accessKey,
        String secretKey,
        boolean createBucket,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl
) {

    URI presignEndpoint() {
        return publicEndpoint != null ? publicEndpoint : endpoint;
    }
}
