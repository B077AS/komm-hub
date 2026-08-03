package com.kommhub.service;

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the self-describing metadata off the client jar — the same
 * {@code app.properties} entry the client itself carries and the launcher
 * reads on its side. Shared by {@link com.kommhub.controller.ClientUpdateController}
 * and {@link ClientReleaseSyncService} so there's one place that knows how.
 */
@Slf4j
public class ClientJarInspector {

    /** Version embedded in the given jar's properties entry under {@code client.version}, or null if unreadable. */
    public static String readVersion(Path jar, String propertiesEntry) {
        return readProperty(jar, propertiesEntry, "client.version");
    }

    /** Same as {@link #readVersion}, but for a jar that self-describes under a different
     *  property key (e.g. the launcher's {@code launcher.version}). */
    public static String readProperty(Path jar, String propertiesEntry, String propertyKey) {
        if (!Files.exists(jar)) return null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jar.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(propertiesEntry)) {
                    Properties props = new Properties();
                    props.load(zis);
                    String value = props.getProperty(propertyKey);
                    return value == null || value.isBlank() ? null : value.trim();
                }
                zis.closeEntry();
            }
            return null;
        } catch (Exception e) {
            log.debug("Could not read {} from {}: {}", propertyKey, jar, e.toString());
            return null;
        }
    }

    /** Lowercase hex SHA-256 of the given file, or null if it can't be read. */
    public static String sha256(Path jar) {
        try (InputStream in = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.debug("Could not hash {}: {}", jar, e.toString());
            return null;
        }
    }
}
