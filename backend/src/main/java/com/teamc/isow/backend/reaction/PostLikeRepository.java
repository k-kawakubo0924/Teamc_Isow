package com.teamc.isow.backend.reaction;

import java.util.Collection;
import java.util.List;
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

    /** 複数の投稿のいいね件数を1回の SQL で集計する（いいねが0件の投稿は結果に含まれない） */
    @Query("""
            SELECT new com.teamc.isow.backend.reaction.PostLikeCount(l.post.id, COUNT(l))
            FROM PostLike l
            WHERE l.post.id IN :postIds
            GROUP BY l.post.id
            """)
    List<PostLikeCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** 指定した投稿のうち、ユーザーがいいねしている投稿の ID（1回の SQL で調べる） */
    @Query("SELECT l.post.id FROM PostLike l WHERE l.user.id = :userId AND l.post.id IN :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
