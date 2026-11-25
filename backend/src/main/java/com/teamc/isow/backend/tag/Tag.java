package com.teamc.isow.backend.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

/**
 * 投稿に付けるタグ（docs/post.md）。
 * あらかじめ登録された選択肢（公式タグ）と、投稿時にユーザーが手入力したタグを同じテーブルで持つ。
 * 選択肢だけを持つ他のマスタとは性質が異なるため、MasterEntity は継承しない。
 */
@Entity
@Table(name = "tags")
public class Tag {

    /** タグを一意に識別するID。投稿との関連付けはこのIDで行う */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 画面に表示する名前。最初に登録されたときの表記（TagNameNormalizer で整形済み）を残す */
    @Column(nullable = false, length = 50)
    private String name;

    /** 重複判定用の名前（表示名を小文字にそろえたもの）。表記ゆれによる重複を防ぐため一意 */
    @Column(name = "normalized_name", nullable = false, unique = true, length = 50)
    private String normalizedName;

    /** 公式タグかどうか。true は運営があらかじめ登録した選択肢、false は投稿時にユーザーが手入力したタグ */
    @Column(name = "is_official", nullable = false)
    @ColumnDefault("false")
    private boolean official;

    /** 選択肢画面での並び順（小さいほど先に表示する）。公式タグのみ設定し、手入力のタグは null */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 有効フラグ。false の場合は選択肢・入力候補に出さない（投稿から参照されているため削除はしない） */
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    /** 登録日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Tag() {
        // JPA 用
    }

    private Tag(String name, boolean official, Integer displayOrder) {
        this.name = TagNameNormalizer.displayName(name);
        this.normalizedName = TagNameNormalizer.key(name);
        this.official = official;
        this.displayOrder = displayOrder;
    }

    /** 運営があらかじめ登録する選択肢のタグを作る */
    public static Tag official(String name, int displayOrder) {
        return new Tag(name, true, displayOrder);
    }

    /** 投稿時にユーザーが手入力したタグを作る */
    public static Tag userInput(String name) {
        return new Tag(name, false, null);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 手入力のタグを公式タグにする（管理画面から。docs/admin.md）。
     * 同じ行のまま変えるため、投稿との関連付け（post_tags）はそのまま残る。表示名は最初に登録されたときの表記のまま。
     * 選択肢に出すために登録するものなので、無効になっていた場合は有効に戻す
     */
    public void makeOfficial(int displayOrder) {
        this.official = true;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    /** 選択肢・入力候補に出さないようにする（管理画面から。付けている投稿の表示は変わらない） */
    public void deactivate() {
        this.active = false;
    }

    /** 無効にしたものを、また選択肢・入力候補に出すようにする（管理画面から） */
    public void activate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public boolean isOfficial() {
        return official;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
