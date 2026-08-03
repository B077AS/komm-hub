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
 * Polls the launcher repo's latest GitHub release for its two stable-named,
 * per-OS jar assets ({@code komm-launcher-windows.jar}, {@code komm-launcher-linux.jar}
 * — unlike the client jar, never version-stamped in the filename, since the
 * launcher's self-update swap overwrites a fixed path by name) and mirrors
 * them locally, same shape as {@link ClientReleaseSyncService}. The running
 * client reads {@link #getCached()} (via the controller) to decide whether
 * the launcher that started it needs updating.
 */
@Slf4j
@Service
public class LauncherReleaseSyncService {

    private static final String WINDOWS_ASSET_NAME = "komm-launcher-windows.jar";
    private static final String LINUX_ASSET_NAME = "komm-launcher-linux.jar";

    private record CachedRelease(String version, String windowsSha256, String linuxSha256, String etag) {
    }

    public record CachedVersion(String version, String windowsSha256, String linuxSha256) {
    }

    @Value("${kommhub.launcher.jar.windows.path}")
    private String windowsJarPath;

    @Value("${kommhub.launcher.jar.linux.path}")
    private String linuxJarPath;

    @Value("${kommhub.launcher.properties-entry}")
    private String propertiesEntry;

    @Value("${kommhub.github.launcher.owner}")
    private String owner;

    @Value("${kommhub.github.launcher.repo}")
    private String repo;

    @Value("${kommhub.github.token:}")
    private String token;

    private final GithubReleaseFetcher fetcher;
    private final AtomicReference<CachedRelease> cached = new AtomicReference<>();

    public LauncherReleaseSyncService(GithubReleaseFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @PostConstruct
    void primeFromDisk() {
        Path windowsJar = Paths.get(windowsJarPath);
        Path linuxJar = Paths.get(linuxJarPath);
        String windowsVersion = ClientJarInspector.readProperty(windowsJar, propertiesEntry, "launcher.version");
        String linuxVersion = ClientJarInspector.readProperty(linuxJar, propertiesEntry, "launcher.version");
        // Only prime from a matched pair — a lone leftover file from a previous
        // partial sync shouldn't be reported as "the" launcher version.
        if (windowsVersion != null && windowsVersion.equals(linuxVersion)) {
            cached.set(new CachedRelease(windowsVersion, ClientJarInspector.sha256(windowsJar),
                    ClientJarInspector.sha256(linuxJar), null));
            log.info("Primed launcher release cache from disk: version={}", windowsVersion);
        }
    }

    public CachedVersion getCached() {
        CachedRelease c = cached.get();
        return c == null ? null : new CachedVersion(c.version(), c.windowsSha256(), c.linuxSha256());
    }

    @Scheduled(fixedDelayString = "${kommhub.github.client.poll-interval-ms:30000}")
    void syncLatestRelease() {
        try {
            CachedRelease before = cached.get();
            GithubReleaseFetcher.FetchResult result = fetcher.fetchLatest(
                    owner, repo, token, before == null ? null : before.etag());

            if (result.statusCode() == 304) {
                log.debug("Launcher release unchanged (304)");
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
                cached.set(new CachedRelease(before.version(), before.windowsSha256(), before.linuxSha256(), etag));
                log.debug("Launcher release tag {} unchanged", version);
                return;
            }

            downloadAndInstall(release, version, etag);
        } catch (Exception e) {
            log.warn("Launcher release sync failed: {}", e.toString());
        }
    }

    private void downloadAndInstall(GithubReleaseFetcher.GithubRelease release, String version, String etag) {
        GithubReleaseFetcher.GithubAsset windowsAsset = findAsset(release, WINDOWS_ASSET_NAME);
        GithubReleaseFetcher.GithubAsset linuxAsset = findAsset(release, LINUX_ASSET_NAME);
        if (windowsAsset == null || linuxAsset == null) {
            log.error("Release {} is missing a launcher asset (windows={}, linux={}); skipping sync",
                    version, windowsAsset != null, linuxAsset != null);
            return;
        }

        Path windowsTmp = Paths.get(windowsJarPath + ".download");
        Path linuxTmp = Paths.get(linuxJarPath + ".download");
        try {
            for (Path target : new Path[]{Paths.get(windowsJarPath), Paths.get(linuxJarPath)}) {
                Path parent = target.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
            }

            String windowsSha256 = downloadAndVerify(windowsAsset, windowsTmp, version);
            String linuxSha256 = downloadAndVerify(linuxAsset, linuxTmp, version);

            Files.move(windowsTmp, Paths.get(windowsJarPath), StandardCopyOption.REPLACE_EXISTING);
            Files.move(linuxTmp, Paths.get(linuxJarPath), StandardCopyOption.REPLACE_EXISTING);
            cached.set(new CachedRelease(version, windowsSha256, linuxSha256, etag));
            log.info("Synced launcher jars to version {} from GitHub release", version);
        } catch (Exception e) {
            log.error("Downloading launcher release {} failed: {}", version, e.toString());
            deleteQuietly(windowsTmp);
            deleteQuietly(linuxTmp);
        }
    }

    private String downloadAndVerify(GithubReleaseFetcher.GithubAsset asset, Path tmp, String expectedVersion) throws Exception {
        String sha256 = fetcher.downloadAndHash(asset, tmp);
        String downloadedVersion = ClientJarInspector.readProperty(tmp, propertiesEntry, "launcher.version");
        if (!expectedVersion.equals(downloadedVersion)) {
            throw new IllegalStateException("Downloaded " + asset.name() + " states version "
                    + downloadedVersion + " (expected " + expectedVersion + ")");
        }
        return sha256;
    }

    private static GithubReleaseFetcher.GithubAsset findAsset(GithubReleaseFetcher.GithubRelease release, String name) {
        return release.assets() == null ? null : release.assets().stream()
                .filter(a -> name.equals(a.name()))
                .findFirst()
                .orElse(null);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
