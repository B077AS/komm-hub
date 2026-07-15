package com.kommhub.service;

import com.kommhub.model.db.Installation;
import com.kommhub.model.dto.request.CreateInstallationRequest;
import com.kommhub.model.dto.request.InstallationValidationRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.response.InstallationValidationResponse;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.*;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDateTime;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationService {

    private final InstallationRepository installationRepository;
    private final JwtUtil jwtUtil;

    @Value("${kommhub.jar.path}")
    private String jarPath;

    @Value("${kommhub.jar.properties-entry}")
    private String propertiesEntry;

    @Value("${kommhub.jar.manifest-path}")
    private String manifestPath;

    @Value("${kommhub.jar.manifest-token-attr}")
    private String manifestTokenAttr;

    public ResponseEntity<?> createInstallation(CreateInstallationRequest request, UUID userId) {
        ResponseEntity<?> csrValidation = validateCsr(request, userId);
        if (csrValidation != null) return csrValidation;

        String setupToken = generateSetupToken();

        log.debug("Setup Token: {}", setupToken);

        Installation installation = Installation.builder()
                .installationName(request.getInstallationName())
                .ownerId(userId)
                .port(request.getInstallationPort())
                .signalPort(request.getSignalPort())
                .tcpPort(request.getTcpPort())
                .mediaPort(request.getMediaPort())
                .status(Installation.InstallationStatus.NOT_VERIFIED)
                .setupToken(setupToken)
                .csr(request.getInstallationCsr())
                .build();

        UUID id = installationRepository.save(installation).getInstallationId();
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    public ResponseEntity<?> buildInstallationJar(UUID installationId, UUID userId) {
        Installation installation = installationRepository.findById(installationId).orElse(null);

        if (installation == null)
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Installation not found");

        if (!installation.getOwnerId().equals(userId))
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not your installation");

        if (installation.getSetupToken() == null)
            return ErrorResponse.of(HttpStatus.GONE, "Setup token already used");

        try {
            byte[] jar = injectPropertiesIntoJar(installation);
            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=\"kommserver-" + installationId + ".jar\"")
                    .body(jar);
        } catch (IOException e) {
            log.error("Failed to build JAR for installation {}", installationId, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build installation JAR");
        }
    }

    private byte[] injectPropertiesIntoJar(Installation installation) throws IOException {
        Path templateJar = Paths.get(jarPath);
        if (!Files.exists(templateJar))
            throw new IOException("Template JAR not found at: " + jarPath);

        ByteArrayOutputStream outputJar = new ByteArrayOutputStream();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(templateJar.toFile()));
             ZipOutputStream zos = new ZipOutputStream(outputJar)) {

            boolean manifestFound = false;

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                if (entry.getName().equals(propertiesEntry)) {
                    Properties props = new Properties();
                    props.load(zis);
                    props.setProperty("server.port", String.valueOf(installation.getPort()));
                    props.setProperty("sfu.signal-port", String.valueOf(installation.getSignalPort()));
                    props.setProperty("sfu.tcp-port", String.valueOf(installation.getTcpPort()));
                    props.setProperty("sfu.media-port", String.valueOf(installation.getMediaPort()));
                    props.store(new OutputStreamWriter(zos), "KommServer Installation Config - DO NOT EDIT");
                } else if (entry.getName().equals(manifestPath)) {
                    manifestFound = true;
                    Manifest manifest = new Manifest(zis);
                    manifest.getMainAttributes().putValue(manifestTokenAttr, installation.getSetupToken());
                    manifest.write(zos);
                } else {
                    IOUtils.copy(zis, zos);
                }

                zos.closeEntry();
                zis.closeEntry();
            }

            if (!manifestFound) {
                log.warn("Manifest entry {} missing from template JAR — creating it for installation {}",
                        manifestPath, installation.getInstallationId());
                zos.putNextEntry(new ZipEntry(manifestPath));
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                manifest.getMainAttributes().putValue(manifestTokenAttr, installation.getSetupToken());
                manifest.write(zos);
                zos.closeEntry();
            }
        }

        log.info("JAR built for installation {}", installation.getInstallationId());
        return outputJar.toByteArray();
    }

    @Transactional
    public InstallationValidationResponse validateAndIssue(InstallationValidationRequest req, String ipAddress) {

        Installation installation = installationRepository
                .findBySetupToken(req.getSetupToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid setup token"));

        if (installation.getStatus() != Installation.InstallationStatus.NOT_VERIFIED) {
            throw new IllegalArgumentException("Installation already verified");
        }

        String signedCertPem = jwtUtil.signCsr(req.getCsr(), installation.getInstallationId());

        // Mutate the managed entity — no save() needed inside @Transactional,
        // but explicit save() is fine too
        installation.setCsr(req.getCsr());
        installation.setCertificate(signedCertPem);
        installation.setCertificateIssuedAt(LocalDateTime.now());
        installation.setIpAddress(resolveEffectiveIp(ipAddress));
        installation.setSetupToken(null);
        installation.setStatus(Installation.InstallationStatus.OFFLINE);

        installationRepository.save(installation);

        log.info("Installation verified and certificate issued: {} @ {}",
                installation.getInstallationId(), ipAddress);

        return InstallationValidationResponse.builder()
                .certificate(signedCertPem)
                .hubPublicKey(jwtUtil.getPublicKeyAsPem())
                .installationId(installation.getInstallationId())
                .installationName(installation.getInstallationName())
                .ownerId(installation.getOwnerId())
                .build();
    }

    private ResponseEntity<?> validateCsr(CreateInstallationRequest request, UUID userId) {
        try {
            PKCS10CertificationRequest csr = parseCsr(request.getInstallationCsr());
            if (csr == null) return ErrorResponse.of(HttpStatus.BAD_REQUEST, "PEM does not contain a CSR");

            JcaPKCS10CertificationRequest jcaCsr = new JcaPKCS10CertificationRequest(csr).setProvider("BC");

            if (!jcaCsr.isSignatureValid(
                    new JcaContentVerifierProviderBuilder().setProvider("BC").build(jcaCsr.getPublicKey())))
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR signature is invalid");

            X500Name subject = csr.getSubject();
            String cn = extractRdn(subject, BCStyle.CN);
            String ou = extractRdn(subject, BCStyle.OU);
            String o = extractRdn(subject, BCStyle.O);

            if (cn == null || !cn.equals(request.getInstallationName().trim()))
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR CN does not match installation name");

            if (!"KommInstallation".equals(o))
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR O field is invalid");

            if (ou == null || !ou.equals(userId.toString()))
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR OU does not match authenticated user");

            PublicKey publicKey = jcaCsr.getPublicKey();
            if (!(publicKey instanceof ECPublicKey ecKey))
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR public key must be EC");

            if (ecKey.getParams().getOrder().bitLength() != 384)
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR public key must be P-384");

            return null;

        } catch (Exception e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "CSR validation failed: " + e.getMessage());
        }
    }

    private PKCS10CertificationRequest parseCsr(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            return obj instanceof PKCS10CertificationRequest csr ? csr : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRdn(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        return rdns.length == 0 ? null : IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    public String resolveEffectiveIp(String ipAddress) {
        if ("127.0.0.1".equals(ipAddress) || "::1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 80);
                String outboundIp = socket.getLocalAddress().getHostAddress();
                log.info("Installation registered from loopback — substituting hub outbound IP: {}", outboundIp);
                return outboundIp;
            } catch (Exception e) {
                log.warn("Could not determine hub outbound IP, keeping loopback address", e);
            }
        }
        return ipAddress;
    }

    private String generateSetupToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}