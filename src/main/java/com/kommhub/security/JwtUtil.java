package com.kommhub.security;

import com.kommhub.model.db.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.keys.directory}")
    private String keysDirectory;

    @Value("${jwt.keys.private}")
    private String privateKeyFile;

    @Value("${jwt.keys.public}")
    private String publicKeyFile;

    @Value("${jwt.ca.cert-file}")
    private String caCertFile;

    @Value("${jwt.ca.cn}")
    private String caCN;

    @Value("${jwt.ca.org}")
    private String caOrg;

    @Value("${jwt.ca.country}")
    private String caCountry;

    @Value("${jwt.ca.validity-days}")
    private int caValidityDays;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.issuer}")
    private String issuer;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private X509Certificate caCertificate;

    @PostConstruct
    public void init() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPair keyPair = loadOrGenerateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            this.caCertificate = loadOrGenerateCACert(keyPair);
            log.info("Hub CA and JWT keys initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Hub CA", e);
            throw new RuntimeException("Failed to initialize Hub CA", e);
        }
    }

    private KeyPair loadOrGenerateKeyPair() throws Exception {
        File privFile = new File(privateKeyFile);
        File pubFile = new File(publicKeyFile);

        if (privFile.exists() && pubFile.exists()) {
            log.info("Loading existing EC key pair from {}", keysDirectory);
            return loadKeysFromFiles(privFile, pubFile);
        }

        log.info("Generating new EC P-384 key pair and saving to {}", keysDirectory);
        KeyPair keyPair = generateKeyPair();
        saveKeysToFiles(keyPair, privFile, pubFile);
        return keyPair;
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", "BC");
        gen.initialize(new ECNamedCurveGenParameterSpec("P-384"));
        return gen.generateKeyPair();
    }

    private void saveKeysToFiles(KeyPair keyPair, File privateKeyFile, File publicKeyFile) throws IOException {
        Path keysDir = Paths.get(keysDirectory);
        if (!Files.exists(keysDir)) {
            Files.createDirectories(keysDir);
        }

        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(privateKeyFile))) {
            writer.writeObject(keyPair.getPrivate());
        }

        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(publicKeyFile))) {
            writer.writeObject(keyPair.getPublic());
        }

        log.info("EC P-384 key pair saved to {}", keysDirectory);
    }

    private KeyPair loadKeysFromFiles(File privateKeyFile, File publicKeyFile) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

        PrivateKey privateKey;
        try (PEMParser parser = new PEMParser(new FileReader(privateKeyFile))) {
            Object obj = parser.readObject();
            if (obj instanceof PEMKeyPair) {
                privateKey = converter.getPrivateKey(((PEMKeyPair) obj).getPrivateKeyInfo());
            } else if (obj instanceof PrivateKeyInfo) {
                privateKey = converter.getPrivateKey((PrivateKeyInfo) obj);
            } else {
                throw new IllegalArgumentException("Unsupported private key format: " + obj.getClass().getName());
            }
        }

        PublicKey publicKey;
        try (PEMParser parser = new PEMParser(new FileReader(publicKeyFile))) {
            Object obj = parser.readObject();
            if (obj instanceof SubjectPublicKeyInfo) {
                publicKey = converter.getPublicKey((SubjectPublicKeyInfo) obj);
            } else if (obj instanceof PEMKeyPair) {
                publicKey = converter.getPublicKey(((PEMKeyPair) obj).getPublicKeyInfo());
            } else {
                throw new IllegalArgumentException("Unsupported public key format: " + obj.getClass().getName());
            }
        }

        return new KeyPair(publicKey, privateKey);
    }

    private X509Certificate loadOrGenerateCACert(KeyPair keyPair) throws Exception {
        File certFile = new File(caCertFile);

        if (certFile.exists()) {
            log.info("Loading existing Hub CA certificate from {}", caCertFile);
            try (PEMParser parser = new PEMParser(new FileReader(certFile))) {
                return new JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate((X509CertificateHolder) parser.readObject());
            }
        }

        log.info("Generating new Hub CA certificate");
        X509Certificate cert = generateCACert(keyPair);

        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(certFile))) {
            writer.writeObject(cert);
        }

        log.info("Hub CA certificate saved to {}", caCertFile);
        return cert;
    }

    private X509Certificate generateCACert(KeyPair keyPair) throws Exception {
        X500Name name = new X500Name(
                "CN=" + caCN + ", O=" + caOrg + ", C=" + caCountry
        );

        Date notBefore = new Date();
        Date notAfter = Date.from(Instant.now().plus(caValidityDays, ChronoUnit.DAYS));

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                notBefore,
                notAfter,
                name,               // self-signed: issuer == subject
                keyPair.getPublic()
        );

        // Mark as CA
        builder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(true));

        // Key usage: sign certs, sign CRLs, sign JWTs
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));

        ContentSigner signer = new JcaContentSignerBuilder("SHA384withECDSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(builder.build(signer));
    }

    public String signCsr(String csrPem, UUID installationId) {
        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
            PKCS10CertificationRequest csr =
                    (PKCS10CertificationRequest) parser.readObject();

            X500Name issuer  = new X500Name(caCertificate.getSubjectX500Principal().getName());
            X500Name subject = new X500Name("CN=" + installationId);

            Instant now = Instant.now();
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer,
                    new BigInteger(128, new SecureRandom()),   // unique serial
                    Date.from(now),
                    Date.from(now.plus(1825, ChronoUnit.DAYS)), // ~5 years
                    subject,
                    csr.getSubjectPublicKeyInfo()
            );

            ContentSigner signer = new JcaContentSignerBuilder("SHA384withECDSA")
                    .setProvider("BC")
                    .build(privateKey);

            X509Certificate signed = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certBuilder.build(signer));

            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(signed);
            }
            return sw.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to sign CSR for installation " + installationId, e);
        }
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .claim("userId", user.getUserId().toString())
                .claim("type", TokenType.ACCESS.name().toLowerCase())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiration, ChronoUnit.SECONDS)))
                .signWith(privateKey, Jwts.SIG.ES384)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .claim("userId", user.getUserId().toString())
                .claim("type", TokenType.REFRESH.name().toLowerCase())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenExpiration, ChronoUnit.SECONDS)))
                .signWith(privateKey, Jwts.SIG.ES384)
                .compact();
    }

    public String generateInstallationTicket(UUID userId, UUID installationId, UUID serverId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("installationId", installationId.toString())
                .claim("serverId", serverId.toString())
                .claim("type", "installation_ticket")
                .claim("jti", UUID.randomUUID().toString())  // for replay prevention
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.SECONDS)))  // 60s, one-time use
                .signWith(privateKey, Jwts.SIG.ES384)
                .compact();
    }

    public String getPublicKeyAsPem() {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(publicKey);
            }
            return sw.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize public key to PEM", e);
        }
    }


    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims verifyWithKey(String token, PublicKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        Claims claims = validateToken(token);
        return UUID.fromString(claims.get("userId", String.class));
    }

    public boolean isTokenValid(String token, UUID userId) {
        try {
            Claims claims = validateToken(token);
            UUID tokenUserId = UUID.fromString(claims.get("userId", String.class));
            return tokenUserId.equals(userId) && !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    public TokenType getTokenType(Claims claims) {
        String type = claims.get("type", String.class);
        if (type == null) {
            throw new IllegalArgumentException("Token type claim is missing");
        }
        return TokenType.valueOf(type.toUpperCase());
    }

    public enum TokenType {
        ACCESS, REFRESH
    }
}