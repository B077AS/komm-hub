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
 * Serves the launcher's own self-update artifacts — mirrors {@link ClientUpdateController}
 * but per-OS, since the launcher's jar bundles platform-specific JavaFX natives.
 * The running client (never GitHub directly) reads {@link LauncherReleaseSyncService}'s
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

    @Value("${kommhub.launcher.jar.linux.path}")
    private String linuxJarPath;

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
        Path jar = resolveJarPath(os);
        if (jar == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            if (!Files.exists(jar)) {
                log.error("Launcher jar not found at: {}", jar.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(jar);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=komm-launcher.jar")
                    .contentLength(jar.toFile().length())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve launcher jar: {}", e.getMessage());
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

    private Path resolveJarPath(String os) {
        return switch (normalize(os)) {
            case "windows" -> Paths.get(windowsJarPath);
            case "linux" -> Paths.get(linuxJarPath);
            default -> null;
        };
    }

    private static String normalize(String os) {
        return os == null ? "" : os.trim().toLowerCase();
    }
}
