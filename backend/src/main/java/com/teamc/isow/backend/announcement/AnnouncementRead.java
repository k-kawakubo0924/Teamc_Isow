package com.teamc.isow.backend.announcement;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.domain.Persistable;

/**
 * お知らせの既読の記録（docs/notification.md「お知らせ」）。利用者がお知らせを開いたときに1行作る。
 *
 * <ul>
 *   <li>お知らせは全員に同じものを出すため、通知のように受け取る人ごとの行は発行時に作らない。
 *       行があれば既読、なければ未読（ただし登録より前に発行されたお知らせは、行がなくても既読として扱う）</li>
 *   <li>主キーは読んだ人とお知らせの組み合わせ。同じ人が何度開いても1行で、最初に開いた日時のまま</li>
 *   <li>未読に戻す・削除はない（共有のお知らせのため）</li>
 * </ul>
 */
@Entity
@Table(name = "announcement_reads")
public class AnnouncementRead implements Persistable<AnnouncementReadId> {

    @EmbeddedId
    private AnnouncementReadId id;

    /** 読んだ人。ユーザーを削除すると、既読の記録も DB が自動で削除する（ON DELETE CASCADE） */
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    /**
     * 読んだお知らせ。アプリではお知らせの行を消さないが、
     * 手作業でお知らせの行を消すときに既読の記録が邪魔をしないよう、DB が自動で削除する（ON DELETE CASCADE）
     */
    @MapsId("announcementId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Announcement announcement;

    /** 最初に開いた日時 */
    @Column(name = "read_at", nullable = false, updatable = false)
    private LocalDateTime readAt;

    protected AnnouncementRead() {
        // JPA 用
    }

    public AnnouncementRead(User user, Announcement announcement, LocalDateTime readAt) {
        this.id = new AnnouncementReadId(user.getId(), announcement.getId());
        this.user = user;
        this.announcement = announcement;
        this.readAt = readAt;
    }

    @Override
    public AnnouncementReadId getId() {
        return id;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    /**
     * 既読の記録は作るだけで書き換えないため、保存するときは常に新しい行として扱う
     * （主キーを自分で決めるため、Spring Data が既存の行と判断して読み込み直す SELECT を出さないように）。
     * すでに行があれば主キーの重複になる（AnnouncementQueryService.markRead で扱う）
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
