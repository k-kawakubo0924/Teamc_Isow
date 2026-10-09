package com.teamc.isow.backend.announcement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.Repository;

/**
 * お知らせの保存と読み込み。発行後の訂正・取り消しは未確定のため（docs/admin.md「未確定・要確認」）、
 * 削除のメソッドは持たせない（JpaRepository を継承しない）
 */
public interface AnnouncementRepository extends Repository<Announcement, Long> {

    Announcement save(Announcement announcement);

    /** 新しい順（同じ日時なら ID の大きい順） */
    Slice<Announcement> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);
}
