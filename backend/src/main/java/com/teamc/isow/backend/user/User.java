package com.teamc.isow.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 会員情報。ログイン・新規会員登録（docs/auth.md）で使用する。
 * PostgreSQL では user が予約語のため、テーブル名は users とする。
 */
@Entity
@Table(name = "users")
public class User {

    /** ユーザーを一意に識別するID。他機能のテーブルからの参照に使う（メール・ユーザ名は変更されうるため主キーにしない） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** メールアドレス。ログインIDとして使うため重複不可 */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** 電話番号（ハイフンなし）。重複可否は未確定のため一意制約は付けない */
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    /** ハッシュ化済みのパスワード。平文は保存しない */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** ユーザ名（画面上の @yuu_style の @ を除いた部分）。重複不可 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 登録日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最終更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {
        // JPA 用
    }

    public User(String email, String phoneNumber, String passwordHash, String username) {
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.username = username;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
