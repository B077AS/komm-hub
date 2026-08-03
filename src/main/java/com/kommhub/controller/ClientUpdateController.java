package com.kommhub.controller;

import com.kommhub.model.dto.response.ClientVersionResponse;
import com.kommhub.service.ClientReleaseSyncService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientUpdateController {

    private final ClientReleaseSyncService releaseSyncService;

    @Value("${kommhub.client.jar.path}")
    private String jarPath;

    @GetMapping("/latest")
    public ResponseEntity<ClientVersionResponse> getLatest() {
        // Version/sha256 come from the last successful GitHub sync (or the on-disk
        // jar primed at startup) rather than re-reading the zip on every request —
        // see ClientReleaseSyncService.
        ClientReleaseSyncService.CachedVersion cached = releaseSyncService.getCached();
        if (cached == null) {
            log.error("No client version available yet (no local jar, no synced release)");
            return ResponseEntity.notFound().build();
        }

        String downloadUrl = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/client/download")
                .replaceQuery(null)
                .toUriString();

        return ResponseEntity.ok(ClientVersionResponse.builder()
                .version(cached.version())
                .sha256(cached.sha256())
                .downloadUrl(downloadUrl)
                .build());
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download() {
        try {
            Path jar = Paths.get(jarPath);
            if (!Files.exists(jar)) {
                log.error("Client JAR not found at: {}", jar.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(jar);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=komm-app.jar")
                    .contentLength(jar.toFile().length())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve client JAR: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
