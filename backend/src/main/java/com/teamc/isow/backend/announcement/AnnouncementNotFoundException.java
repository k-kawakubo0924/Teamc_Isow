package com.teamc.isow.backend.announcement;

/** お知らせが存在しない、または取り消されている（利用者からは、どちらも同じ 404 にする） */
public class AnnouncementNotFoundException extends RuntimeException {

    public AnnouncementNotFoundException(Long id) {
        super("announcement not found: " + id);
    }
}
