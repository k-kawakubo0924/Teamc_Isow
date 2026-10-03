package com.teamc.isow.backend.reaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    /** 投稿のいいね件数（公開情報） */
    long countByPostId(Long postId);

    /** いいねを取り消す。いいねしていなければ何もしない（削除した件数を返す） */
    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.user.id = :userId AND l.post.id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
}
