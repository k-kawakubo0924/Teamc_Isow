package com.teamc.isow.backend.post;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投稿の内容（投稿作成の応答）。
 *
 * @param imageUrls 写真の URL（表示順。1枚目がサムネイル）
 */
public record PostResponse(
        Long id,
        String title,
        FashionCategoryResponse fashionCategory,
        List<TagResponse> tags,
        String wornItems,
        String description,
        String referenceUrl,
        List<String> imageUrls,
        AuthorResponse author,
        LocalDateTime createdAt) {

    /** 遅延読み込みの項目を使うため、トランザクションの中で呼ぶこと */
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                new FashionCategoryResponse(post.getFashionCategory().getId(), post.getFashionCategory().getName()),
                post.getTags().stream().map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.isOfficial())).toList(),
                post.getWornItems(),
                post.getDescription(),
                post.getReferenceUrl(),
                post.getImages().stream().map(PostImage::getImageUrl).toList(),
                new AuthorResponse(post.getAuthor().getId(), post.getAuthor().getUsername()),
                post.getCreatedAt());
    }

    public record FashionCategoryResponse(Long id, String name) {
    }

    /** official は画面で公式タグに印を付けるために返す */
    public record TagResponse(Long id, String name, boolean official) {
    }

    /** 投稿者。メールアドレスなどの個人情報は返さない */
    public record AuthorResponse(Long id, String username) {
    }
}
