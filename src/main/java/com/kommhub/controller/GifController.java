package com.kommhub.controller;

import com.kommhub.model.db.User;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.summary.GifPage;
import com.kommhub.model.dto.summary.GifResult;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.FavoriteGifService;
import com.kommhub.service.GifService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gifs")
public class GifController {

    private final GifService gifService;
    private final FavoriteGifService favoriteGifService;
    private final SecurityUtil securityUtil;

    @GetMapping("/trending")
    public ResponseEntity<?> getTrending(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "1")  int page) {
        if (limit < 1 || limit > 50) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 50");
        }
        if (page < 1) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Page must be >= 1");
        }
        try {
            GifPage result = gifService.getTrending(limit, page);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to fetch trending GIFs: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch trending GIFs");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "1")  int page) {
        if (q == null || q.isBlank()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Search query is required");
        }
        if (limit < 1 || limit > 50) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 50");
        }
        if (page < 1) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Page must be >= 1");
        }
        try {
            GifPage result = gifService.search(q.trim(), limit, page);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to search GIFs for query '{}': {}", q, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search GIFs");
        }
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavorites() {
        User user = securityUtil.getCurrentUser();
        if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
        try {
            return ResponseEntity.ok(favoriteGifService.getFavorites(user));
        } catch (Exception e) {
            log.error("Failed to get favorite GIFs: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get favorites");
        }
    }

    @PostMapping("/favorites")
    public ResponseEntity<?> addFavorite(@RequestBody GifResult gif) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
        if (gif.getId() == null || gif.getId().isBlank()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "GIF id is required");
        }
        try {
            favoriteGifService.addFavorite(user, gif);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to add favorite GIF: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add favorite");
        }
    }

    @DeleteMapping("/favorites/{gifId}")
    public ResponseEntity<?> removeFavorite(@PathVariable String gifId) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
        try {
            favoriteGifService.removeFavorite(user, gifId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to remove favorite GIF: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to remove favorite");
        }
    }
}
