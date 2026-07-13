package com.kommhub.service;

import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Catalog of valid badge icons, enumerated from the same
 * ikonli-materialdesign2-pack artifact the app client renders with — an icon
 * code accepted here is guaranteed to resolve on the client. Codepoints are
 * exposed so the web dashboard can render glyphs with the vendored MDI font.
 */
@Slf4j
@Service
public class BadgeIconService {

    /** Icon literal + hex codepoint, serialized compactly for the picker. */
    public record IconEntry(String n, String c) {}

    private final Map<String, Ikon> iconsByLiteral;
    private final List<IconEntry> catalog;

    public BadgeIconService() {
        Map<String, Ikon> byLiteral = new LinkedHashMap<>();
        Stream.of(
                MaterialDesignA.values(), MaterialDesignB.values(), MaterialDesignC.values(),
                MaterialDesignD.values(), MaterialDesignE.values(), MaterialDesignF.values(),
                MaterialDesignG.values(), MaterialDesignH.values(), MaterialDesignI.values(),
                MaterialDesignJ.values(), MaterialDesignK.values(), MaterialDesignL.values(),
                MaterialDesignM.values(), MaterialDesignN.values(), MaterialDesignO.values(),
                MaterialDesignP.values(), MaterialDesignQ.values(), MaterialDesignR.values(),
                MaterialDesignS.values(), MaterialDesignT.values(), MaterialDesignU.values(),
                MaterialDesignV.values(), MaterialDesignW.values(), MaterialDesignX.values(),
                MaterialDesignY.values(), MaterialDesignZ.values()
        ).flatMap(Arrays::stream).forEach(ikon -> byLiteral.put(ikon.getDescription(), ikon));

        this.iconsByLiteral = Map.copyOf(byLiteral);
        this.catalog = byLiteral.values().stream()
                .map(i -> new IconEntry(i.getDescription(), codepointHex(i)))
                .toList();
        log.info("Loaded {} badge icons from materialdesign2 pack", catalog.size());
    }

    public boolean isValid(String literal) {
        return literal != null && iconsByLiteral.containsKey(literal);
    }

    public List<IconEntry> getCatalog() {
        return catalog;
    }

    /** Hex codepoint (e.g. "F04C9") for a valid literal, or null. */
    public String codepointHex(String literal) {
        Ikon ikon = iconsByLiteral.get(literal);
        return ikon != null ? codepointHex(ikon) : null;
    }

    private static String codepointHex(Ikon ikon) {
        return Integer.toHexString(ikon.getCode()).toUpperCase(Locale.ROOT);
    }
}
