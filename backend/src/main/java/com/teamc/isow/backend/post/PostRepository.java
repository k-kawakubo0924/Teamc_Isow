package com.teamc.isow.backend.post;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 指定したユーザーの投稿を新しい順に返す（同じ投稿日時なら ID の大きい順）。
     * 全件数を数えず、次のページの有無だけを判定する（Slice）
     */
    Slice<Post> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId, Pageable pageable);

    // ---- ホームの投稿一覧（TimelineService）。ID だけを並び順どおりに取り、中身は別の SQL でまとめて読む ----

    /** 新着：投稿日時の新しい順（同じなら ID の大きい順） */
    @Query("SELECT p.id FROM Post p ORDER BY p.createdAt DESC, p.id DESC")
    Slice<Long> findLatestIds(Pageable pageable);

    /**
     * おすすめ（暫定。docs/home.md）：いいね数の多い順 → 新しい順 → ID の大きい順。
     * 表示のたびに全投稿のいいね数を集計するため、投稿数が増えたら posts にいいね数の列を持たせる方式に切り替える
     */
    @Query("""
            SELECT p.id FROM Post p
            LEFT JOIN PostLike l ON l.post = p
            GROUP BY p.id, p.createdAt
            ORDER BY COUNT(l) DESC, p.createdAt DESC, p.id DESC
            """)
    Slice<Long> findRecommendedIds(Pageable pageable);

    /** 投稿者とファッションの種類を一緒に読み込む（投稿ごとに SQL を発行しないため）。並び順は保証しない */
    @Query("SELECT p FROM Post p JOIN FETCH p.author JOIN FETCH p.fashionCategory WHERE p.id IN :ids")
    List<Post> findWithAuthorAndFashionCategoryByIdIn(@Param("ids") Collection<Long> ids);

    /** 1枚目の写真（一覧のサムネイル）だけを、まとめて読み込む */
    @Query("""
            SELECT new com.teamc.isow.backend.post.PostThumbnail(i.post.id, i.imageUrl)
            FROM PostImage i
            WHERE i.post.id IN :postIds AND i.sortOrder = 1
            """)
    List<PostThumbnail> findThumbnails(@Param("postIds") Collection<Long> postIds);
}
