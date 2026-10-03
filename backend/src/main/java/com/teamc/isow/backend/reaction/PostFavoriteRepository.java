package com.teamc.isow.backend.reaction;

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
}
