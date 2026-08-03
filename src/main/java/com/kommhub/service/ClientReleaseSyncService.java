package com.kommhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls the client repo's latest GitHub release and pulls the jar asset down
 * itself the moment the tag changes, replacing manual/SSH placement of
 * {@code kommhub.client.jar.path}. {@link com.kommhub.controller.ClientUpdateController}
 * reads the result of the last successful sync from {@link #getCached()}.
 *
 * <p>GitHub polling mechanics (conditional requests, digest-verified download)
 * live in {@link GithubReleaseFetcher}; this class only resolves the expected
 * asset name and does the jar-specific corruption/version checks.
 *
 * <p>A GitHub hiccup or a release published without its jar asset yet never
 * touches the already-installed jar — sync failures are logged and retried on
 * the next tick, same "keep serving what works" philosophy as the launcher's
 * own update flow.
 */
@Slf4j
@Service
public class ClientReleaseSyncService {

    private record CachedRelease(String version, String sha256, String etag) {
    }

    @Value("${kommhub.client.jar.path}")
    private String jarPath;

    @Value("${kommhub.client.properties-entry}")
    private String propertiesEntry;

    @Value("${kommhub.github.client.owner}")
    private String owner;

    @Value("${kommhub.github.client.repo}")
    private String repo;

    @Value("${kommhub.github.token:}")
    private String token;

    private final GithubReleaseFetcher fetcher;
    private final AtomicReference<CachedRelease> cached = new AtomicReference<>();

    public ClientReleaseSyncService(GithubReleaseFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @PostConstruct
    void primeFromDisk() {
        Path jar = Paths.get(jarPath);
        String version = ClientJarInspector.readVersion(jar, propertiesEntry);
        if (version != null) {
            String sha256 = ClientJarInspector.sha256(jar);
            cached.set(new CachedRelease(version, sha256, null));
            log.info("Primed client release cache from disk: version={}", version);
        }
    }

    /** Best-effort snapshot of the last successfully synced (or on-disk primed) release. */
    public CachedVersion getCached() {
        CachedRelease c = cached.get();
        return c == null ? null : new CachedVersion(c.version(), c.sha256());
    }

    public record CachedVersion(String version, String sha256) {
    }

    @Scheduled(fixedDelayString = "${kommhub.github.client.poll-interval-ms:30000}")
    void syncLatestRelease() {
        try {
            CachedRelease before = cached.get();
            GithubReleaseFetcher.FetchResult result = fetcher.fetchLatest(
                    owner, repo, token, before == null ? null : before.etag());

            if (result.statusCode() == 304) {
                log.debug("Client release unchanged (304)");
                return;
            }
            if (result.statusCode() != 200) {
                log.warn("GitHub releases/latest returned HTTP {} for {}/{}", result.statusCode(), owner, repo);
                return;
            }

            GithubReleaseFetcher.GithubRelease release = result.release();
            if (release == null || release.tagName() == null) {
                log.warn("Malformed GitHub release response for {}/{}", owner, repo);
                return;
            }
            String etag = result.etag();
            String version = release.tagName().startsWith("v")
                    ? release.tagName().substring(1)
                    : release.tagName();

            if (before != null && version.equals(before.version())) {
                cached.set(new CachedRelease(before.version(), before.sha256(), etag));
                log.debug("Client release tag {} unchanged", version);
                return;
            }

            downloadAndInstall(release, version, etag);
        } catch (Exception e) {
            log.warn("Client release sync failed: {}", e.toString());
        }
    }

    private void downloadAndInstall(GithubReleaseFetcher.GithubRelease release, String version, String etag) {
        String expectedName = "komm-" + version + ".jar";
        GithubReleaseFetcher.GithubAsset asset = release.assets() == null ? null : release.assets().stream()
                .filter(a -> expectedName.equals(a.name()))
                .findFirst()
                .orElse(null);
        if (asset == null) {
            log.error("Release {} has no asset named {} yet; skipping sync", version, expectedName);
            return;
        }

        Path jar = Paths.get(jarPath);
        Path tmp = Paths.get(jarPath + ".download");
        try {
            Path parent = jar.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);

            String computedSha256 = fetcher.downloadAndHash(asset, tmp);

            String downloadedVersion = ClientJarInspector.readVersion(tmp, propertiesEntry);
            if (!version.equals(downloadedVersion)) {
                log.error("Downloaded {} states version {} (expected {}); discarding",
                        expectedName, downloadedVersion, version);
                Files.deleteIfExists(tmp);
                return;
            }

            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
            cached.set(new CachedRelease(version, computedSha256, etag));
            log.info("Synced client jar to version {} from GitHub release", version);
        } catch (Exception e) {
            log.error("Downloading {} failed: {}", expectedName, e.toString());
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }
}
