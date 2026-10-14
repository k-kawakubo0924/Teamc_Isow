package com.teamc.isow.backend.admin;

/** すでに取り消したお知らせを取り消そうとした（409。ほかの管理者が先に取り消した場合など） */
public class AnnouncementAlreadyWithdrawnException extends RuntimeException {

    public AnnouncementAlreadyWithdrawnException(Long id) {
        super("announcement already withdrawn: " + id);
    }
}
