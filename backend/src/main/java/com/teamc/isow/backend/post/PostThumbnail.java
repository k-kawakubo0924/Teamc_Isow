package com.teamc.isow.backend.post;

/** 投稿の1枚目の写真（PostRepository.findThumbnails の1行） */
public record PostThumbnail(Long postId, String imageUrl) {
}
