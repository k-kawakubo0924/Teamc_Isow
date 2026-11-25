package com.teamc.isow.backend.post;

/** 指定した投稿が存在しない。404 として扱う */
public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(Long id) {
        super("post not found: " + id);
    }
}
