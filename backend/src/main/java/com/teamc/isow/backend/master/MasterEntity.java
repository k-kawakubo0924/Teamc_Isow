package com.teamc.isow.backend.master;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

/**
 * 選択肢として使うマスタデータの共通項目。
 * テーブルはマスタごとに分け（継承先のエンティティごとに作られる）、このクラス自体のテーブルは作らない。
 */
@MappedSuperclass
public abstract class MasterEntity {

    /** マスタを一意に識別するID。投稿やユーザーからはこのIDで参照する（表示名は変更されうるため） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 画面に表示する名前。同じマスタ内で重複不可 */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** 画面での並び順（小さいほど先に表示する）。同じ値も許容する */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 有効フラグ。false の場合は選択肢に出さない（参照中のデータがあるため削除はしない）。
     * SQL で直接 INSERT する場合に備えて、DB 側にも既定値 true を付ける
     */
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    /** 登録日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MasterEntity() {
        // JPA 用
    }

    protected MasterEntity(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
