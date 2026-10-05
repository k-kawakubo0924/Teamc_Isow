package com.teamc.isow.backend.user;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.PersonalColor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 会員情報。ログイン・新規会員登録（docs/auth.md）とプロフィール（docs/profile.md）で使用する。
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

    /**
     * 表示名（プロフィール編集の「名前」。必須）。@ユーザ名とは別に、自由に付けられる名前。
     * 新規登録では名前を聞かないため、ユーザ名を初期値にする。
     * DB 上は NULL を許している。この列を追加する前に登録したユーザーは値がなく、
     * ddl-auto=update では既存の行がある表に NOT NULL の列を追加できないため。
     * 値がない場合は getDisplayName() がユーザ名を返す。マイグレーションを導入する際に値を埋めて NOT NULL にする
     */
    @Column(name = "display_name", length = 50)
    private String displayName;

    /**
     * 性別（任意。未設定は null）。Gender の定数名（MALE など）を文字列で持ち、getGender() で enum に戻す。
     * Gender 型の項目にすると Hibernate が列に値の一覧の検査制約を付け、定数を追加すると保存できなくなるため
     * （詳しくは Gender のコメント）
     */
    @Column(length = 20)
    private String gender;

    /**
     * 身長（cm）。任意で、未設定は null（docs/profile.md）。
     * 入力と範囲のチェックはプロフィール編集で行う。ホームの投稿一覧などに表示する
     */
    @Column(name = "height_cm")
    private Integer heightCm;

    /**
     * 年代（任意。未設定は null）。年齢の数値は時間が経つと古くなるため年代で持つ。
     * 性別と同じ理由で、AgeGroup の定数名を文字列で持ち、getAgeGroup() で enum に戻す
     */
    @Column(name = "age_group", length = 30)
    private String ageGroup;

    /** 骨格タイプ（任意。未設定は null）。マスタを参照する */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "body_type_id")
    private BodyType bodyType;

    /** パーソナルカラー（任意。未設定は null）。マスタを参照する */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_color_id")
    private PersonalColor personalColor;

    /** プロフィール画像の URL（任意。未設定は null）。画像そのものは DB に保存しない */
    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

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
        // 新規登録では名前を聞かないため、ユーザ名を表示名の初期値にする（プロフィール編集で変更できる）
        this.displayName = username;
    }

    /**
     * プロフィール編集の内容でまとめて置き換える（画面上部の「保存」でまとめて保存する仕様のため）。
     * 値は API 側で入力チェック済みであること。表示名以外は null で未設定に戻せる
     */
    public void updateProfile(String displayName, Gender gender, Integer heightCm, AgeGroup ageGroup,
            BodyType bodyType, PersonalColor personalColor) {
        this.displayName = displayName;
        this.gender = gender == null ? null : gender.name();
        this.heightCm = heightCm;
        this.ageGroup = ageGroup == null ? null : ageGroup.name();
        this.bodyType = bodyType;
        this.personalColor = personalColor;
    }

    /** プロフィール画像を変更する（「変更」から設定する）。null で未設定に戻す */
    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
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

    /** 表示名。表示名の列を追加する前に登録したユーザー（値がない）は、ユーザ名を返す */
    public String getDisplayName() {
        return displayName != null ? displayName : username;
    }

    public Gender getGender() {
        return gender == null ? null : Gender.valueOf(gender);
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public AgeGroup getAgeGroup() {
        return ageGroup == null ? null : AgeGroup.valueOf(ageGroup);
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public PersonalColor getPersonalColor() {
        return personalColor;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
