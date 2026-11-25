package com.teamc.isow.backend.post;

/**
 * 他人の投稿を編集しようとした。403 として扱う。
 * 削除と同じく、投稿はもともと誰でも見られるため、存在を隠す 404 にはしない（docs/post.md「投稿の編集」）
 */
public class PostEditForbiddenException extends RuntimeException {

    public PostEditForbiddenException(Long postId) {
        super("post edit forbidden: " + postId);
    }
}
