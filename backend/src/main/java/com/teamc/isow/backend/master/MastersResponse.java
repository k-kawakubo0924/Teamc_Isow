package com.teamc.isow.backend.master;

import com.teamc.isow.backend.tag.Tag;
import java.util.List;

/**
 * 画面の選択肢の一覧（GET /api/masters）。各一覧は表示する順に並んでいる。
 * DB のマスタは id、enum は code で識別する。保存するときは、マスタは id を、enum は code を送る。
 */
public record MastersResponse(
        /** ファッションの種類 */
        List<MasterOption> fashionCategories,
        /** 骨格タイプ */
        List<MasterOption> bodyTypes,
        /** パーソナルカラー */
        List<MasterOption> personalColors,
        /** 公式タグ */
        List<MasterOption> tags,
        /** 年代 */
        List<EnumOption> ageGroups,
        /** 性別 */
        List<EnumOption> genders) {

    /** DB のマスタの選択肢。保存するときは id を送る（name は変更されうるため識別に使わない） */
    public record MasterOption(Long id, String name) {

        public static MasterOption from(MasterEntity master) {
            return new MasterOption(master.getId(), master.getName());
        }

        public static MasterOption from(Tag tag) {
            return new MasterOption(tag.getId(), tag.getName());
        }
    }

    /** enum の選択肢。保存するときは code（定数名）を送る。label は表示専用 */
    public record EnumOption(String code, String label) {
    }
}
