package com.kommhub.websocket.security;

import com.kommhub.model.db.Installation;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstallationHandshakeInterceptor implements HandshakeInterceptor {

    private final InstallationRepository installationRepository;
    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        // ── 1. Read both headers ──────────────────────────────────────────────
        String authHeader = request.getHeaders().getFirst("Authorization");
        String connectToken = request.getHeaders().getFirst("X-Connect-Token");

        if (authHeader == null || !authHeader.startsWith("Certificate ")) {
            log.warn("WS upgrade rejected — missing or malformed Authorization header");
            return false;
        }
        if (connectToken == null || connectToken.isBlank()) {
            log.warn("WS upgrade rejected — missing X-Connect-Token header");
            return false;
        }

        // ── 2. Decode and parse the presented certificate ─────────────────────
        X509Certificate presentedCert;
        try {
            String pem = new String(Base64.getDecoder().decode(authHeader.substring(12).trim()));
            Security.addProvider(new BouncyCastleProvider());
            try (PEMParser parser = new PEMParser(new StringReader(pem))) {
                presentedCert = new JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate((X509CertificateHolder) parser.readObject());
            }
        } catch (Exception e) {
            log.warn("WS upgrade rejected — could not parse certificate: {}", e.getMessage());
            return false;
        }

        // ── 3. Verify the cert was signed by the hub CA ───────────────────────
        try {
            presentedCert.verify(jwtUtil.getCaCertificate().getPublicKey());
        } catch (Exception e) {
            log.warn("WS upgrade rejected — certificate not signed by hub CA");
            return false;
        }

        // ── 4. Check cert validity period ─────────────────────────────────────
        try {
            presentedCert.checkValidity();
        } catch (Exception e) {
            log.warn("WS upgrade rejected — certificate expired or not yet valid");
            return false;
        }

        // ── 5. Extract installationId from cert CN ────────────────────────────
        UUID installationId = extractCn(presentedCert);
        if (installationId == null) {
            log.warn("WS upgrade rejected — cert CN is not a valid UUID");
            return false;
        }

        // ── 6. DB check — revocation and status only ──────────────────────────
        Installation installation = installationRepository.findById(installationId).orElse(null);
        if (installation == null) {
            log.warn("WS upgrade rejected — unknown installationId: {}", installationId);
            return false;
        }
        if (installation.getStatus() == Installation.InstallationStatus.NOT_VERIFIED) {
            log.warn("WS upgrade rejected — installation not verified: {}", installationId);
            return false;
        }
        if (Boolean.TRUE.equals(installation.getCertificateRevoked())) {
            log.warn("WS upgrade rejected — certificate revoked: {}", installationId);
            return false;
        }

        // ── 7. Verify JWT signed with the installation's private key ──────────
        // Uses the public key from the presented cert — proves the connecting
        // party holds the private key that matches the hub-issued certificate.
        try {
            PublicKey installationPublicKey = presentedCert.getPublicKey();
            jwtUtil.verifyWithKey(connectToken, installationPublicKey);
        } catch (Exception e) {
            log.warn("WS upgrade rejected — connect token invalid for {}: {}", installationId, e.getMessage());
            return false;
        }

        // ── 8. Stash attributes for the handler ───────────────────────────────
        attributes.put("installationId", installationId);
        attributes.put("clientIpAddress", extractIpAddress(request));
        log.debug("WS handshake accepted for installationId={}", installationId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private UUID extractCn(X509Certificate cert) {
        try {
            String dn = cert.getSubjectX500Principal().getName();
            for (String part : dn.split(",")) {
                part = part.trim();
                if (part.startsWith("CN=")) {
                    return UUID.fromString(part.substring(3).trim());
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private String extractIpAddress(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : null;
    }
}
