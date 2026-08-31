package com.docai.ingestor.application.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.docai.ingestor.domain.entity.TenantStorageConfig;
import com.docai.ingestor.domain.repository.TenantStorageConfigRepository;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Builds and caches per-tenant S3 clients based on each tenant's configured credentials and bucket.
 * Entries expire after {@link #TTL} so admin changes to a tenant's storage config are reflected
 * within that window without a service restart.
 *
 * Tenants with no row in {@code tenant_storage_configs} fall back to the platform's default
 * S3 client and bucket (the same SeaweedFS-backed store used before per-tenant storage was added).
 */
@Slf4j
@Component
public class TenantS3ClientCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    record TenantClients(S3Client client, S3Presigner presigner, String bucket, Instant loadedAt) {}

    private final ConcurrentHashMap<UUID, TenantClients> cache = new ConcurrentHashMap<>();

    private final S3Client platformClient;
    private final S3Presigner platformPresigner;
    private final String platformBucket;
    private final TenantStorageConfigRepository repository;
    private final SecretsCryptoService cryptoService;

    public TenantS3ClientCache(
            S3Client platformClient,
            S3Presigner platformPresigner,
            @Value("${ingestor.storage.s3.bucket}") String platformBucket,
            TenantStorageConfigRepository repository,
            SecretsCryptoService cryptoService) {
        this.platformClient = platformClient;
        this.platformPresigner = platformPresigner;
        this.platformBucket = platformBucket;
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    /**
     * Returns the S3 clients and bucket for a tenant. Loads from the database if not cached or if
     * the cached entry has expired. A null tenantId (malformed storage key) returns platform defaults.
     */
    public TenantClients getClients(UUID tenantId) {
        if (tenantId == null) {
            return platformClients();
        }
        TenantClients cached = cache.get(tenantId);
        if (cached != null && Duration.between(cached.loadedAt(), Instant.now()).compareTo(TTL) < 0) {
            return cached;
        }
        TenantClients fresh = load(tenantId);
        cache.put(tenantId, fresh);
        return fresh;
    }

    /** Removes a tenant's cached entry — call after their storage config is updated. */
    public void invalidate(UUID tenantId) {
        cache.remove(tenantId);
    }

    private TenantClients load(UUID tenantId) {
        return repository.findByTenantId(tenantId)
            .map(config -> {
                String accessKey = cryptoService.decrypt(config.getS3AccessKeyEnc());
                String secretKey = cryptoService.decrypt(config.getS3SecretKeyEnc());
                if (accessKey == null || secretKey == null) {
                    log.warn("Tenant {} has a storage config row but credentials could not be decrypted "
                        + "(wrong/rotated SECRETS_ENCRYPTION_KEY?) — falling back to platform storage", tenantId);
                    return platformClients();
                }
                log.debug("Building tenant-specific S3 client for tenant {} (bucket={})",
                    tenantId, config.getS3Bucket());
                return buildClients(config, accessKey, secretKey);
            })
            .orElseGet(this::platformClients);
    }

    private TenantClients buildClients(TenantStorageConfig config, String accessKey, String secretKey) {
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey));
        S3Configuration s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(config.isS3PathStyleAccess())
            .build();
        Region region = Region.of(config.getS3Region());

        S3Client.Builder clientBuilder = S3Client.builder()
            .region(region)
            .credentialsProvider(creds)
            .serviceConfiguration(s3Config);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
            .region(region)
            .credentialsProvider(creds)
            .serviceConfiguration(s3Config);

        if (StringUtils.hasText(config.getS3Endpoint())) {
            URI endpoint = URI.create(config.getS3Endpoint());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }

        return new TenantClients(clientBuilder.build(), presignerBuilder.build(),
            config.getS3Bucket(), Instant.now());
    }

    private TenantClients platformClients() {
        return new TenantClients(platformClient, platformPresigner, platformBucket, Instant.now());
    }
}
