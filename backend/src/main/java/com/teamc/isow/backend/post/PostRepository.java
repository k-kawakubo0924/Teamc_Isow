package com.teamc.isow.backend.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 指定したユーザーの投稿を新しい順に返す（同じ投稿日時なら ID の大きい順）。
     * 全件数を数えず、次のページの有無だけを判定する（Slice）
     */
    Slice<Post> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId, Pageable pageable);
}
