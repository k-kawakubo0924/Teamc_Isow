package com.teamc.isow.backend.master;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * ファッションの種類のマスタ。投稿時にあらかじめ登録された選択肢から選ぶ（docs/post.md）。
 * 共通項目（ID・表示名・並び順・有効フラグ・登録日時）は {@link MasterEntity} を参照。
 */
@Entity
@Table(name = "fashion_categories")
public class FashionCategory extends MasterEntity {

    protected FashionCategory() {
        // JPA 用
    }

    public FashionCategory(String name, int displayOrder) {
        super(name, displayOrder);
    }
}
