package com.kommhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Public site configuration (kommvoice.com): canonical base URL, GitHub repos
 * and download links. All download URLs derive from the repo URLs in
 * application.properties, so pointing them at a new release location is a
 * one-line change.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "komm.site")
public class SiteProperties {

    private String baseUrl;

    private Github github = new Github();
    private Downloads downloads = new Downloads();

    @Data
    public static class Github {
        private String client;
        private String server;
        private String hub;
        private String launcher;
    }

    @Data
    public static class Downloads {
        private String clientWindows;
        private String clientLinux;
        private String server;
    }
}
