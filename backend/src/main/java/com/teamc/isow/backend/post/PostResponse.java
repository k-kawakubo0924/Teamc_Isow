package com.teamc.isow.backend.post;

import com.teamc.isow.backend.image.ImageUrls;
import com.teamc.isow.backend.reaction.ReactionSummary;
import com.teamc.isow.backend.tag.Tag;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 投稿の内容（投稿の作成・詳細・自分の投稿一覧で共通）。
 *
 * @param tags タグ（ID 順）
 * @param imageUrls 写真の URL（表示順。1枚目がサムネイル）。非公開の投稿では空（写真を配らないため。画面は代わりの表示を出す）
 * @param likeCount いいね件数（公開情報）
 * @param likedByMe ログイン中のユーザーがいいねしているか
 * @param favoritedByMe ログイン中のユーザーがお気に入りにしているか（本人にだけ返す）
 * @param ownedByMe ログイン中のユーザーの投稿か（画面で編集・削除のメニューを出すかの判断だけに使う。できるかはサーバーで確かめる）
 * @param editedAt 投稿者が最後に編集した日時。編集していなければ null（画面の「編集済み」の表示に使う）
 * @param hidden 管理者が非公開にしているか（docs/admin.md「6. 措置」）。非公開の投稿を受け取るのは投稿者本人だけ
 * @param hiddenReason 非公開にした理由（ReportReason の定数名）。投稿者本人にだけ返し、それ以外は null
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
        long likeCount,
        boolean likedByMe,
        boolean favoritedByMe,
        boolean ownedByMe,
        LocalDateTime createdAt,
        LocalDateTime editedAt,
        boolean hidden,
        String hiddenReason) {

    /**
     * 遅延読み込みの項目を使うため、トランザクションの中で呼ぶこと。
     * reactions は ReactionSummaryService でまとめて調べたもの（一覧で投稿ごとに SQL を発行しないため）
     * viewerId はログイン中のユーザーの ID（ownedByMe の判定に使う）。写真・プロフィール画像の URL は urls でキーから組み立てる
     */
    public static PostResponse from(Post post, ReactionSummary reactions, Long viewerId, ImageUrls urls) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                new FashionCategoryResponse(post.getFashionCategory().getId(), post.getFashionCategory().getName()),
                // 付けた順は保存していないため、毎回同じ並びになるよう ID 順にする
                post.getTags().stream()
                        .sorted(Comparator.comparing(Tag::getId))
                        .map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.isOfficial()))
                        .toList(),
                post.getWornItems(),
                post.getDescription(),
                post.getReferenceUrl(),
                // 非公開の投稿（受け取るのは投稿者本人だけ）の写真は、本人にも配らないため、URL を返さない（PublicImageAccess）
                post.isHidden() ? List.of() : urls.of(post.getImages().stream().map(PostImage::getImageKey).toList()),
                new AuthorResponse(post.getAuthor().getId(), post.getAuthor().getUsername(),
                        urls.of(post.getAuthor().getProfileImageKey()), post.getAuthor().getHeightCm()),
                reactions.likeCount(),
                reactions.likedByMe(),
                reactions.favoritedByMe(),
                post.getAuthor().getId().equals(viewerId),
                post.getCreatedAt(),
                post.getEditedAt(),
                post.isHidden(),
                post.getAuthor().getId().equals(viewerId) ? post.getHiddenReason() : null);
    }

    public record FashionCategoryResponse(Long id, String name) {
    }

    /** official は画面で公式タグに印を付けるために返す */
    public record TagResponse(Long id, String name, boolean official) {
    }

    /**
     * 投稿者。メールアドレスなどの個人情報は返さない。
     *
     * @param profileImageUrl プロフィール画像。未設定は null
     * @param heightCm 身長（cm）。未設定は null で、画面では表示を省く（ホームの一覧のカードと同じ）
     */
    public record AuthorResponse(Long id, String username, String profileImageUrl, Integer heightCm) {
    }
}
