package com.teamc.isow.backend.announcement;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** お知らせの既読の記録（AnnouncementRead）の主キー。読んだ人とお知らせの組み合わせ */
@Embeddable
public class AnnouncementReadId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "announcement_id")
    private Long announcementId;

    protected AnnouncementReadId() {
        // JPA 用
    }

    public AnnouncementReadId(Long userId, Long announcementId) {
        this.userId = userId;
        this.announcementId = announcementId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAnnouncementId() {
        return announcementId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AnnouncementReadId other
                && Objects.equals(userId, other.userId)
                && Objects.equals(announcementId, other.announcementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, announcementId);
    }
}
