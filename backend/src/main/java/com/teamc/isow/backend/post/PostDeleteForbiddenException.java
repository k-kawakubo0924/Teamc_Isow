package com.teamc.isow.backend.post;

/**
 * 他人の投稿を削除しようとした。403 として扱う。
 * 投稿はもともと誰でも見られるため、存在を隠す 404 にはしない（docs/post.md「投稿の削除」）
 */
public class PostDeleteForbiddenException extends RuntimeException {

    public PostDeleteForbiddenException(Long postId) {
        super("post delete forbidden: " + postId);
    }
}
