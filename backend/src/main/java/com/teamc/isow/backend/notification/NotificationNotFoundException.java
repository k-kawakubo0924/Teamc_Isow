package com.teamc.isow.backend.notification;

/** 通知が存在しない、または自分の通知でない（他人の通知の有無が分からないよう、どちらも同じ 404 にする） */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long id) {
        super("notification not found: " + id);
    }
}
