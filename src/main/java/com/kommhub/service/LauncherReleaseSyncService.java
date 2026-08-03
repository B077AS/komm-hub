package com.kommhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls the launcher repo's latest GitHub release for its two per-OS
 * self-update artifacts and mirrors them locally, same shape as
 * {@link ClientReleaseSyncService}. The running client reads
 * {@link #getCached()} (via the controller) to decide whether the launcher
 * that started it needs updating.
 *
 * <p>Windows and Linux need different artifact <em>types</em>, not just
 * different natives: on Windows the swap target is a single jar file inside
 * an otherwise-untouched install directory ({@code komm-launcher-windows.jar}),
 * self-describing via its {@code launcher.version} property the same way the
 * client jar is. An AppImage, by contrast, is one opaque read-only-mounted
 * unit — there's no equivalent "just the launcher's code" inside it — so the
 * Linux artifact ({@code komm-launcher-linux.AppImage}) is a full standalone
 * AppImage that can't be introspected as a zip. It's verified only against
 * GitHub's own per-asset digest (still a strong integrity guarantee); a
 * plain sidecar {@code .version} file written alongside it after each sync
 * is what lets {@link #primeFromDisk()} recover its version after a restart.
 */
@Slf4j
@Service
public class LauncherReleaseSyncService {

    private static final String WINDOWS_ASSET_NAME = "komm-launcher-windows.jar";
    private static final String LINUX_ASSET_NAME = "komm-launcher-linux.AppImage";

    private record CachedRelease(String version, String windowsSha256, String linuxSha256, String etag) {
    }

    public record CachedVersion(String version, String windowsSha256, String linuxSha256) {
    }

    @Value("${kommhub.launcher.jar.windows.path}")
    private String windowsJarPath;

    @Value("${kommhub.launcher.appimage.linux.path}")
    private String linuxAppImagePath;

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

    private Path linuxVersionSidecar() {
        return Paths.get(linuxAppImagePath + ".version");
    }

    @PostConstruct
    void primeFromDisk() {
        Path windowsJar = Paths.get(windowsJarPath);
        Path linuxAppImage = Paths.get(linuxAppImagePath);
        String windowsVersion = ClientJarInspector.readProperty(windowsJar, propertiesEntry, "launcher.version");
        String linuxVersion = readLinuxVersionSidecar();
        // Only prime from a matched pair — a lone leftover file from a previous
        // partial sync shouldn't be reported as "the" launcher version.
        if (windowsVersion != null && windowsVersion.equals(linuxVersion) && Files.exists(linuxAppImage)) {
            cached.set(new CachedRelease(windowsVersion, ClientJarInspector.sha256(windowsJar),
                    ClientJarInspector.sha256(linuxAppImage), null));
            log.info("Primed launcher release cache from disk: version={}", windowsVersion);
        }
    }

    private String readLinuxVersionSidecar() {
        try {
            Path sidecar = linuxVersionSidecar();
            if (!Files.isRegularFile(sidecar)) return null;
            String value = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? null : value;
        } catch (Exception e) {
            return null;
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

        Path windowsJar = Paths.get(windowsJarPath);
        Path linuxAppImage = Paths.get(linuxAppImagePath);
        Path windowsTmp = Paths.get(windowsJarPath + ".download");
        Path linuxTmp = Paths.get(linuxAppImagePath + ".download");
        try {
            for (Path target : new Path[]{windowsJar, linuxAppImage}) {
                Path parent = target.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
            }

            // Windows: verified against its own embedded launcher.version (like the client jar).
            String windowsSha256 = fetcher.downloadAndHash(windowsAsset, windowsTmp);
            String downloadedWindowsVersion = ClientJarInspector.readProperty(windowsTmp, propertiesEntry, "launcher.version");
            if (!version.equals(downloadedWindowsVersion)) {
                throw new IllegalStateException("Downloaded " + WINDOWS_ASSET_NAME + " states version "
                        + downloadedWindowsVersion + " (expected " + version + ")");
            }

            // Linux: an AppImage can't be introspected as a zip — GitHub's own digest
            // cross-check (inside downloadAndHash) is the only verification available.
            String linuxSha256 = fetcher.downloadAndHash(linuxAsset, linuxTmp);

            Files.move(windowsTmp, windowsJar, StandardCopyOption.REPLACE_EXISTING);
            Files.move(linuxTmp, linuxAppImage, StandardCopyOption.REPLACE_EXISTING);
            linuxAppImage.toFile().setExecutable(true, false);
            Files.writeString(linuxVersionSidecar(), version, StandardCharsets.UTF_8);

            cached.set(new CachedRelease(version, windowsSha256, linuxSha256, etag));
            log.info("Synced launcher artifacts to version {} from GitHub release", version);
        } catch (Exception e) {
            log.error("Downloading launcher release {} failed: {}", version, e.toString());
            deleteQuietly(windowsTmp);
            deleteQuietly(linuxTmp);
        }
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
