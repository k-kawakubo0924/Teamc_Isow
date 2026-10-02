package com.teamc.isow.backend.master;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 骨格タイプのマスタ。プロフィール編集で選択する（docs/profile.md）。
 * 共通項目（ID・表示名・並び順・有効フラグ・登録日時）は {@link MasterEntity} を参照。
 */
@Entity
@Table(name = "body_types")
public class BodyType extends MasterEntity {

    protected BodyType() {
        // JPA 用
    }

    public BodyType(String name, int displayOrder) {
        super(name, displayOrder);
    }
}
