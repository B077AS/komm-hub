package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "favorite_gifs",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "gif_id"})
    }
)
public class FavoriteGif {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "favorite_gif_id", nullable = false, length = 36)
    private UUID favoriteGifId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "gif_id", nullable = false)
    private String gifId;

    @Column(name = "title")
    private String title;

    @Column(name = "preview_url", length = 1024)
    private String previewUrl;

    @Column(name = "full_url", length = 1024)
    private String fullUrl;

    @Column(name = "preview_mp4_url", length = 1024)
    private String previewMp4Url;

    @Column(name = "full_mp4_url", length = 1024)
    private String fullMp4Url;

    @Column(name = "width")
    private int width;

    @Column(name = "height")
    private int height;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
