package com.kommhub.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kommhub.model.dto.summary.GifPage;
import com.kommhub.model.dto.summary.GifResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GifService {

    private static final String KLIPY_BASE = "https://api.klipy.com/api/v1";
    private static final int DEFAULT_PER_PAGE = 20;

    @Value("${klipy.api-key}")
    private String apiKey;

    private final HttpClient http = HttpClient.newHttpClient();

    public GifPage getTrending(int perPage, int page) throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(KLIPY_BASE + "/" + apiKey + "/gifs/trending")
                .queryParam("per_page", perPage)
                .queryParam("page", page)
                .queryParam("rating", "pg")
                .build().toUri();

        return fetch(uri);
    }

    public GifPage search(String query, int perPage, int page) throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(KLIPY_BASE + "/" + apiKey + "/gifs/search")
                .queryParam("q", query)
                .queryParam("per_page", perPage)
                .queryParam("page", page)
                .queryParam("rating", "pg")
                .build().toUri();

        return fetch(uri);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private GifPage fetch(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Klipy returned {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Klipy API error: " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

        boolean resultOk = root.has("result") && root.get("result").getAsBoolean();
        if (!resultOk) {
            log.error("Klipy returned result=false: {}", response.body());
            throw new RuntimeException("Klipy API returned result=false");
        }

        JsonObject dataWrapper = root.getAsJsonObject("data");
        if (dataWrapper == null) return GifPage.builder().items(List.of()).hasNext(false).build();

        JsonArray data = dataWrapper.getAsJsonArray("data");
        boolean hasNext = dataWrapper.has("has_next") && dataWrapper.get("has_next").getAsBoolean();

        List<GifResult> results = new ArrayList<>();
        if (data == null) return GifPage.builder().items(results).hasNext(false).build();

        for (JsonElement element : data) {
            try {
                GifResult gif = parseGif(element.getAsJsonObject());
                if (gif != null) results.add(gif);
            } catch (Exception e) {
                log.warn("Skipping malformed GIF entry: {}", e.getMessage());
            }
        }

        return GifPage.builder().items(results).hasNext(hasNext).build();
    }

    /**
     * Maps a Klipy GIF object to our {@link GifResult}.
     * <p>
     * Rendition strategy:
     * <ul>
     *   <li>{@code previewUrl}   — {@code sm.gif}  (~220 px wide) for the picker grid</li>
     *   <li>{@code fullUrl}      — {@code md.gif}  (~640 px wide) for in-chat display</li>
     *   <li>{@code previewMp4Url} — {@code sm.mp4} for lightweight picker preview</li>
     *   <li>{@code fullMp4Url}   — {@code md.mp4}  for high-quality chat display</li>
     *   <li>Width / height taken from {@code md.gif} (the send-in-chat rendition)</li>
     * </ul>
     */
    private GifResult parseGif(JsonObject item) {
        JsonObject file = item.getAsJsonObject("file");
        if (file == null) return null;

        JsonObject sm = file.getAsJsonObject("sm");
        JsonObject md = file.getAsJsonObject("md");
        if (sm == null || md == null) return null;

        JsonObject smGif = sm.getAsJsonObject("gif");
        JsonObject mdGif = md.getAsJsonObject("gif");
        if (smGif == null || mdGif == null) return null;

        JsonObject smMp4 = sm.getAsJsonObject("mp4");
        JsonObject mdMp4 = md.getAsJsonObject("mp4");

        String previewMp4Url = extractUrl(smMp4);
        String fullMp4Url    = extractUrl(mdMp4);

        return GifResult.builder()
                .id(item.get("id").getAsString())
                .title(item.has("title") ? item.get("title").getAsString() : "")
                .previewUrl(smGif.get("url").getAsString())
                .fullUrl(mdGif.get("url").getAsString())
                .previewMp4Url(previewMp4Url)
                .fullMp4Url(fullMp4Url)
                .width(mdGif.get("width").getAsInt())
                .height(mdGif.get("height").getAsInt())
                .build();
    }

    private String extractUrl(JsonObject rendition) {
        if (rendition == null) return null;
        JsonElement url = rendition.get("url");
        return (url != null && !url.isJsonNull()) ? url.getAsString() : null;
    }
}
