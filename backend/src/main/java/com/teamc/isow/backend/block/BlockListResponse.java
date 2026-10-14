package com.teamc.isow.backend.block;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ブロック中の一覧（GET /api/blocks。1ページ分）。ブロックした新しい順
 */
public record BlockListResponse(List<Item> items, int page, int size, boolean hasNext) {

    /** ブロックした相手1人 */
    public record Item(Long userId, String username, String displayName, String profileImageUrl,
            LocalDateTime blockedAt) {

        static Item from(Block block) {
            return new Item(block.getBlocked().getId(), block.getBlocked().getUsername(),
                    block.getBlocked().getDisplayName(), block.getBlocked().getProfileImageUrl(), block.getBlockedAt());
        }
    }
}
