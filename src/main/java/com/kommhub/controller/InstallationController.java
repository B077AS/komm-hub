package com.kommhub.controller;

import com.kommhub.model.db.Installation;
import com.kommhub.model.dto.request.InstallationValidationRequest;
import com.kommhub.model.dto.request.JoinInstallationRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.response.InstallationValidationResponse;
import com.kommhub.model.dto.summary.InstallationAccessTokenSummary;
import com.kommhub.model.dto.summary.InstallationDetailSummary;
import com.kommhub.model.dto.summary.InstallationSummary;
import com.kommhub.model.dto.summary.InstallationSummary.InstallationRole;
import com.kommhub.model.dto.summary.ServerSummary;
import com.kommhub.model.dto.request.CreateInstallationRequest;
import com.kommhub.repository.InstallationMemberRepository;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.InstallationAccessTokenService;
import com.kommhub.service.InstallationDeletionService;
import com.kommhub.service.InstallationService;
import com.kommhub.service.ServerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/installations")
public class InstallationController {

    private final SecurityUtil securityUtil;
    private final ServerRepository serverRepository;
    private final InstallationRepository installationRepository;
    private final InstallationMemberRepository installationMemberRepository;
    private final InstallationService installationService;
    private final InstallationDeletionService installationDeletionService;
    private final InstallationAccessTokenService tokenService;
    private final ServerService serverService;

    @GetMapping("/list")
    public ResponseEntity<?> getUserInstallations() {
        UUID userId = securityUtil.getCurrentUserId();

        List<InstallationSummary> owned = installationRepository.findByOwnerId(userId).stream()
                .map(i -> toSummary(i, InstallationRole.OWNER))
                .collect(Collectors.toList());

        List<InstallationSummary> member = installationMemberRepository.findByUserId(userId).stream()
                .map(m -> installationRepository.findById(m.getInstallationId()).orElse(null))
                .filter(i -> i != null)
                .map(i -> toSummary(i, InstallationRole.MEMBER))
                .collect(Collectors.toList());

        List<InstallationSummary> all = new ArrayList<>();
        all.addAll(owned);
        all.addAll(member);
        return ResponseEntity.ok(all);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody InstallationValidationRequest req,
                                      HttpServletRequest httpRequest) {
        if (req.getSetupToken() == null || req.getSetupToken().isBlank()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "setupToken is required");
        }
        if (req.getCsr() == null || req.getCsr().isBlank()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "csr is required");
        }

        try {
            String ipAddress = extractClientIp(httpRequest);
            InstallationValidationResponse result = installationService.validateAndIssue(req, ipAddress);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Server validation rejected: {}", e.getMessage());
            return ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during server validation", e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Validation failed");
        }
    }

    @GetMapping("/{installationId}")
    public ResponseEntity<?> getInstallationDetails(@PathVariable UUID installationId) {
        UUID userId = securityUtil.getCurrentUserId();
        Installation installation = installationRepository.findById(installationId).orElse(null);
        if (installation == null) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Installation not found");
        }
        boolean isOwner = installation.getOwnerId().equals(userId);
        boolean isMember = installationMemberRepository.existsByInstallationIdAndUserId(installationId, userId);
        if (!isOwner && !isMember) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Access denied");
        }
        InstallationDetailSummary detail = InstallationDetailSummary.builder()
                .installationId(installation.getInstallationId())
                .installationName(installation.getInstallationName())
                .installationPort(installation.getPort() != null ? installation.getPort() : 0)
                .signalPort(installation.getSignalPort() != null ? installation.getSignalPort() : 0)
                .tcpPort(installation.getTcpPort() != null ? installation.getTcpPort() : 0)
                .mediaPort(installation.getMediaPort() != null ? installation.getMediaPort() : 0)
                .hostedServersCount((int) serverRepository.countByInstallationId(installationId))
                .status(installation.getStatus())
                .ipAddress(installation.getIpAddress())
                .build();
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{installationId}/servers")
    public ResponseEntity<?> getInstallationServers(@PathVariable UUID installationId) {
        UUID userId = securityUtil.getCurrentUserId();
        Installation installation = installationRepository.findById(installationId).orElse(null);
        if (installation == null) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Installation not found");
        }
        if (!installation.getOwnerId().equals(userId)) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Access denied");
        }
        List<ServerSummary> servers = serverService.getServersForInstallation(installationId);
        return ResponseEntity.ok(servers);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createInstallation(@RequestBody CreateInstallationRequest request) {
        return installationService.createInstallation(request, securityUtil.getCurrentUserId());
    }

    @DeleteMapping("/{installationId}")
    public ResponseEntity<?> deleteInstallation(@PathVariable UUID installationId) {
        try {
            installationDeletionService.deleteInstallation(installationId, securityUtil.getCurrentUserId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete installation {}", installationId, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete installation");
        }
    }

    @GetMapping("/jar")
    public ResponseEntity<?> downloadInstallationJar(@RequestParam UUID installationId) {
        return installationService.buildInstallationJar(installationId, securityUtil.getCurrentUserId());
    }

    // ── Access token endpoints ────────────────────────────────────────────────

    @PostMapping("/{installationId}/tokens")
    public ResponseEntity<?> generateToken(@PathVariable UUID installationId) {
        try {
            InstallationAccessTokenSummary token =
                    tokenService.generateToken(installationId, securityUtil.getCurrentUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(token);
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to generate access token for installation {}", installationId, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate access token");
        }
    }

    @GetMapping("/{installationId}/tokens")
    public ResponseEntity<?> listTokens(@PathVariable UUID installationId) {
        try {
            List<InstallationAccessTokenSummary> tokens =
                    tokenService.listTokens(installationId, securityUtil.getCurrentUserId());
            return ResponseEntity.ok(tokens);
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to list tokens for installation {}", installationId, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list tokens");
        }
    }

    @DeleteMapping("/{installationId}/tokens/{tokenId}")
    public ResponseEntity<?> deleteToken(@PathVariable UUID installationId,
                                         @PathVariable UUID tokenId) {
        try {
            tokenService.deleteToken(installationId, tokenId, securityUtil.getCurrentUserId());
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete token {} from installation {}", tokenId, installationId, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete token");
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinViaToken(@RequestBody JoinInstallationRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "code is required");
        }
        try {
            String installationName = tokenService.joinViaToken(
                    securityUtil.getCurrentUserId(), request.getCode().trim());
            return ResponseEntity.ok(java.util.Map.of("installationName", installationName));
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.GONE, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to join installation via token", e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to join installation");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InstallationSummary toSummary(Installation i, InstallationRole role) {
        return InstallationSummary.builder()
                .installationId(i.getInstallationId())
                .installationName(i.getInstallationName())
                .installationPort(i.getPort() != null ? i.getPort() : 0)
                .hostedServersCount((int) serverRepository.countByInstallationId(i.getInstallationId()))
                .status(i.getStatus())
                .ipAddress(i.getIpAddress())
                .role(role)
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
