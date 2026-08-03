package com.kommhub.service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Shared GitHub-releases polling mechanics: a conditional {@code releases/latest}
 * fetch plus a digest-verified streaming download. Used by both
 * {@link ClientReleaseSyncService} and {@link LauncherReleaseSyncService} — each
 * keeps its own asset-name resolution and cached-version bookkeeping, only the
 * GitHub HTTP protocol bits live here.
 */
@Component
public class GithubReleaseFetcher {

    public record GithubAsset(String name,
                               @SerializedName("browser_download_url") String browserDownloadUrl,
                               String digest) {
    }

    public record GithubRelease(@SerializedName("tag_name") String tagName, List<GithubAsset> assets) {
    }

    /** {@code statusCode} is 304 (unchanged), 200 ({@code release}/{@code etag} populated), or an error code. */
    public record FetchResult(int statusCode, GithubRelease release, String etag) {
    }

    private final Gson gson;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public GithubReleaseFetcher(Gson gson) {
        this.gson = gson;
    }

    /**
     * {@code GET /repos/{owner}/{repo}/releases/latest}, conditional on {@code etagIfAny}.
     * A {@code 304} (the common case) costs nothing against GitHub's rate limit.
     */
    public FetchResult fetchLatest(String owner, String repo, String token, String etagIfAny)
            throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        if (etagIfAny != null) {
            builder.header("If-None-Match", etagIfAny);
        }

        HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            return new FetchResult(res.statusCode(), null, null);
        }
        GithubRelease release = gson.fromJson(res.body(), GithubRelease.class);
        String etag = res.headers().firstValue("ETag").orElse(null);
        return new FetchResult(200, release, etag);
    }

    /**
     * Streams {@code asset} to {@code destination}, hashing as it writes, and cross-checks
     * against GitHub's own per-asset {@code digest} field when present. Returns the computed
     * lowercase-hex SHA-256. Throws on any HTTP failure or digest mismatch — callers should
     * delete a partial {@code destination} on catch.
     */
    public String downloadAndHash(GithubAsset asset, Path destination) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(asset.browserDownloadUrl())).GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("Downloading " + asset.name() + " failed: HTTP " + res.statusCode());
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = res.body(); var out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
        }
        String computedSha256 = HexFormat.of().formatHex(digest.digest());

        String expectedDigest = asset.digest();
        if (expectedDigest != null) {
            String expectedHex = expectedDigest.startsWith("sha256:")
                    ? expectedDigest.substring("sha256:".length())
                    : expectedDigest;
            if (!expectedHex.equalsIgnoreCase(computedSha256)) {
                throw new IllegalStateException("Downloaded " + asset.name() + " failed GitHub digest check (expected "
                        + expectedHex + ", got " + computedSha256 + ")");
            }
        }
        return computedSha256;
    }
}
