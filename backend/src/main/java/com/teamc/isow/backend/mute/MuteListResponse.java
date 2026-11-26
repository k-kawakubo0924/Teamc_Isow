package com.teamc.isow.backend.mute;

import com.teamc.isow.backend.image.ImageUrls;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ミュートしている人の一覧（GET /api/mutes。1ページ分）。ミュートした新しい順
 */
public record MuteListResponse(List<Item> items, int page, int size, boolean hasNext) {

    /** ミュートした相手1人 */
    public record Item(Long userId, String username, String displayName, String profileImageUrl,
            LocalDateTime mutedAt) {

        static Item from(UserMute mute, ImageUrls imageUrls) {
            return new Item(mute.getMuted().getId(), mute.getMuted().getUsername(),
                    mute.getMuted().getDisplayName(), imageUrls.of(mute.getMuted().getProfileImageKey()), mute.getMutedAt());
        }
    }
}
