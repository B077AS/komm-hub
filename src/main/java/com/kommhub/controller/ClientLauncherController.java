package com.kommhub.controller;

import com.kommhub.model.dto.response.LauncherVersionResponse;
import com.kommhub.service.LauncherReleaseSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the launcher's own self-update artifacts - mirrors {@link ClientUpdateController}
 * but per-OS, since Windows and Linux need different artifact types (a plain jar
 * vs. a full AppImage - see {@link LauncherReleaseSyncService}). The running
 * client (never GitHub directly) reads {@link LauncherReleaseSyncService}'s
 * cache through here to decide whether the launcher that started it is stale.
 */
@Slf4j
@RestController
@RequestMapping("/api/launcher")
@RequiredArgsConstructor
public class ClientLauncherController {

    private final LauncherReleaseSyncService releaseSyncService;

    @Value("${kommhub.launcher.jar.windows.path}")
    private String windowsJarPath;

    @Value("${kommhub.launcher.appimage.linux.path}")
    private String linuxAppImagePath;

    @GetMapping("/latest")
    public ResponseEntity<LauncherVersionResponse> getLatest(@RequestParam String os) {
        LauncherReleaseSyncService.CachedVersion cached = releaseSyncService.getCached();
        if (cached == null) {
            log.error("No launcher version available yet (no local jars, no synced release)");
            return ResponseEntity.notFound().build();
        }
        String sha256 = resolveSha256(cached, os);
        if (sha256 == null) {
            return ResponseEntity.badRequest().build();
        }

        String downloadUrl = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/launcher/download")
                .replaceQueryParam("os", os)
                .toUriString();

        return ResponseEntity.ok(LauncherVersionResponse.builder()
                .version(cached.version())
                .sha256(sha256)
                .downloadUrl(downloadUrl)
                .build());
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String os) {
        Path artifact = resolveArtifactPath(os);
        if (artifact == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            if (!Files.exists(artifact)) {
                log.error("Launcher artifact not found at: {}", artifact.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(artifact);
            String filename = "windows".equals(normalize(os)) ? "komm-launcher.jar" : "komm-launcher.AppImage";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentLength(artifact.toFile().length())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve launcher artifact: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private String resolveSha256(LauncherReleaseSyncService.CachedVersion cached, String os) {
        return switch (normalize(os)) {
            case "windows" -> cached.windowsSha256();
            case "linux" -> cached.linuxSha256();
            default -> null;
        };
    }

    private Path resolveArtifactPath(String os) {
        return switch (normalize(os)) {
            case "windows" -> Paths.get(windowsJarPath);
            case "linux" -> Paths.get(linuxAppImagePath);
            default -> null;
        };
    }

    private static String normalize(String os) {
        return os == null ? "" : os.trim().toLowerCase();
    }
}
