package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.master.MasterEntity;
import com.teamc.isow.backend.tag.Tag;

/**
 * 管理画面のマスタ・公式タグの1件。無効なものも返すため active を持つ
 *
 * @param displayOrder 並び順（小さいほど先に表示する）
 * @param active 有効か（false は利用者側の選択肢に出ない）
 */
public record AdminMasterItem(Long id, String name, int displayOrder, boolean active) {

    public static AdminMasterItem from(MasterEntity master) {
        return new AdminMasterItem(master.getId(), master.getName(), master.getDisplayOrder(), master.isActive());
    }

    /** 公式タグであること（並び順を持つ）。手入力のタグは管理画面の一覧に出さない */
    public static AdminMasterItem from(Tag tag) {
        return new AdminMasterItem(tag.getId(), tag.getName(), tag.getDisplayOrder(), tag.isActive());
    }
}
