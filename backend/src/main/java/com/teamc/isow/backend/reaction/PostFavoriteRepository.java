package com.teamc.isow.backend.reaction;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** お気に入りは本人以外に見せないため、投稿ごとの件数を数えるメソッドは用意しない */
public interface PostFavoriteRepository extends JpaRepository<PostFavorite, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    /** お気に入りから外す。お気に入りにしていなければ何もしない（削除した件数を返す） */
    @Modifying
    @Query("DELETE FROM PostFavorite f WHERE f.user.id = :userId AND f.post.id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 自分のお気に入り一覧：お気に入りにした投稿の ID を、お気に入りにした新しい順（同じなら ID の大きい順） */
    @Query("SELECT f.post.id FROM PostFavorite f WHERE f.user.id = :userId ORDER BY f.createdAt DESC, f.id DESC")
    Slice<Long> findPostIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 指定した投稿のうち、ユーザーがお気に入りにしている投稿の ID（1回の SQL で調べる） */
    @Query("SELECT f.post.id FROM PostFavorite f WHERE f.user.id = :userId AND f.post.id IN :postIds")
    List<Long> findFavoritedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
