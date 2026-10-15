package com.teamc.isow.backend.moderation;

/** 措置ができない（すでに非公開・利用停止中ではない・管理者は利用停止にできないなど）。409。理由は message（画面にそのまま出せる文言） */
public class ModerationConflictException extends RuntimeException {

    public ModerationConflictException(String message) {
        super(message);
    }
}
