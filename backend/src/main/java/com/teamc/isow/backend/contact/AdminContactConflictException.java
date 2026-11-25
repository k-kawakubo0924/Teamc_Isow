package com.teamc.isow.backend.contact;

/** お問い合わせの状態を変えられない（409。すでにその状態） */
public class AdminContactConflictException extends RuntimeException {

    public AdminContactConflictException(String message) {
        super(message);
    }
}
