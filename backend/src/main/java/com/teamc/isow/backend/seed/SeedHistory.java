package com.teamc.isow.backend.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 初期データの投入履歴。一度投入した初期データを、再起動のたびに登録し直さないために使う。
 * 名前の一致では判定しないため、投入後にマスタの名前を変更しても元の名前で再登録されることはない。
 */
@Entity
@Table(name = "master_seed_history")
public class SeedHistory {

    /** 投入済みの初期データを表すキー（例: fashion_category:きれいめ）。MasterSeedData の定義から作る */
    @Id
    @Column(name = "seed_key", length = 100)
    private String seedKey;

    /** 投入日時 */
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    protected SeedHistory() {
        // JPA 用
    }

    public SeedHistory(String seedKey) {
        this.seedKey = seedKey;
    }

    @PrePersist
    void onCreate() {
        this.appliedAt = LocalDateTime.now();
    }

    public String getSeedKey() {
        return seedKey;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }
}
