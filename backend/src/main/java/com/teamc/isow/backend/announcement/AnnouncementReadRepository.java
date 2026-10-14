package com.teamc.isow.backend.announcement;

import org.springframework.data.repository.Repository;

/** お知らせの既読の記録。未読に戻す・削除はないため、保存と存在の確認だけ（JpaRepository を継承しない） */
public interface AnnouncementReadRepository extends Repository<AnnouncementRead, AnnouncementReadId> {

    AnnouncementRead saveAndFlush(AnnouncementRead read);

    boolean existsById(AnnouncementReadId id);
}
