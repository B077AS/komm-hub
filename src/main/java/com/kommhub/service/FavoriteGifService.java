package com.kommhub.service;

import com.kommhub.model.db.FavoriteGif;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.summary.GifResult;
import com.kommhub.repository.FavoriteGifRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteGifService {

    private final FavoriteGifRepository favoriteGifRepository;

    public List<GifResult> getFavorites(User user) {
        return favoriteGifRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(fg -> GifResult.builder()
                        .id(fg.getGifId())
                        .title(fg.getTitle())
                        .previewUrl(fg.getPreviewUrl())
                        .fullUrl(fg.getFullUrl())
                        .previewMp4Url(fg.getPreviewMp4Url())
                        .fullMp4Url(fg.getFullMp4Url())
                        .width(fg.getWidth())
                        .height(fg.getHeight())
                        .build())
                .toList();
    }

    @Transactional
    public void addFavorite(User user, GifResult gif) {
        if (favoriteGifRepository.existsByUserAndGifId(user, gif.getId())) return;
        favoriteGifRepository.save(FavoriteGif.builder()
                .user(user)
                .gifId(gif.getId())
                .title(gif.getTitle())
                .previewUrl(gif.getPreviewUrl())
                .fullUrl(gif.getFullUrl())
                .previewMp4Url(gif.getPreviewMp4Url())
                .fullMp4Url(gif.getFullMp4Url())
                .width(gif.getWidth())
                .height(gif.getHeight())
                .build());
    }

    @Transactional
    public void removeFavorite(User user, String gifId) {
        favoriteGifRepository.deleteByUserAndGifId(user, gifId);
    }
}
