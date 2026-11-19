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
    private Legal legal = new Legal();

    /**
     * Who runs this hub, shown on the Privacy Policy and Terms pages. Set both in
     * application-local.properties before going public; a hub that forks the site must put
     * its own operator here.
     */
    @Data
    public static class Legal {
        /**
         * Whether the Privacy Policy (/privacy) and Terms of Service (/terms) pages are served.
         * When false, both URLs redirect to the home page and every link to them (footer,
         * register page) is hidden. Off by default; komm.site.legal.pages-enabled=true turns them on.
         */
        private boolean pagesEnabled = false;
        /** Person or organisation responsible for the hub (the data controller). */
        private String operatorName;
        /** Where users send privacy requests and legal notices. */
        private String contactEmail;
    }

    @Data
    public static class Github {
        private String client;
        private String server;
        private String hub;
    }

    @Data
    public static class Downloads {
        private String clientWindows;
        private String clientLinux;
        private String serverWindows;
        private String serverLinux;
        private String server;
    }
}
