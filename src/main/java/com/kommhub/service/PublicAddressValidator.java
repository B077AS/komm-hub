package com.kommhub.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates the optional public address a host enters when creating an installation
 * (the name players should use instead of the IP the hub sees — e.g. a reverse-proxy domain).
 * <p>
 * Players verify an installation by its hub-signed certificate, never by this value, so a wrong
 * or hostile address cannot impersonate anyone; this check only keeps the stored value a plain
 * host name or IPv4 address (no scheme, port, path, whitespace) because the client builds
 * {@code host:port} from it. No DNS lookup is done here — the hub must never resolve names
 * chosen by third parties.
 */
public class PublicAddressValidator {

    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

    /** @return the normalised address, or {@code null} if absent or not acceptable */
    public static String validate(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || value.length() > 253) {
            return null;
        }
        String[] labels = value.split("\\.", -1);
        boolean allNumeric = true;
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                return null;
            }
            allNumeric &= NUMERIC.matcher(label).matches();
        }
        if (allNumeric && !isIpv4(labels)) {
            return null;
        }
        if (value.equals("localhost") || value.startsWith("127.") || value.equals("0.0.0.0")) {
            return null;
        }
        return value;
    }

    private static boolean isIpv4(String[] labels) {
        if (labels.length != 4) {
            return false;
        }
        for (String label : labels) {
            if (label.length() > 3 || Integer.parseInt(label) > 255) {
                return false;
            }
        }
        return true;
    }
}
