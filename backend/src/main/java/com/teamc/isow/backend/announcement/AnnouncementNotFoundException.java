package com.teamc.isow.backend.announcement;

/** お知らせが存在しない（404） */
public class AnnouncementNotFoundException extends RuntimeException {

    public AnnouncementNotFoundException(Long id) {
        super("announcement not found: " + id);
    }
}
