package com.teamc.isow.backend.post;

import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ファッションの投稿（docs/post.md）。
 * 写真は post_images（表示順つき）、タグは中間テーブル post_tags で持つ。
 */
@Entity
@Table(
        name = "posts",
        indexes = {
            // プロフィールの投稿一覧で使う
            @Index(name = "idx_posts_user_id", columnList = "user_id"),
            // ホームの新着で使う
            @Index(name = "idx_posts_created_at", columnList = "created_at")
        })
public class Post {

    /** 写真の枚数の下限・上限（docs/post.md） */
    public static final int MIN_IMAGES = 1;
    public static final int MAX_IMAGES = 10;

    /** 投稿を一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 投稿者 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    /** 題名（必須）。文字数の上限は仕様で未確定のため仮の値 */
    @Column(nullable = false, length = 100)
    private String title;

    /** ファッションの種類（必須、1投稿につき1つ）。マスタを参照する */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fashion_category_id", nullable = false)
    private FashionCategory fashionCategory;

    /** 着用アイテム（任意）。入力形式は未確定のため、複数行の自由記述として持つ */
    @Column(name = "worn_items", length = 1000)
    private String wornItems;

    /** 投稿説明（必須） */
    @Column(nullable = false, length = 2000)
    private String description;

    /** 参考情報の URL（任意）。どこのサイトで売っているかなど */
    @Column(name = "reference_url", length = 2048)
    private String referenceUrl;

    /** 写真（1枚以上10枚以下）。表示順に並ぶ。投稿と一緒に保存・削除される */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PostImage> images = new ArrayList<>();

    /**
     * タグ（1つ以上）。公式タグ・手入力のタグの両方を参照する。
     * 投稿→タグの一方向のみとし、タグ側には投稿の一覧を持たせない（人気のタグは投稿数が多く、読み込みが重くなるため）
     */
    @ManyToMany
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            // タグからの検索で使う（主キーは post_id が先頭のため、tag_id 単独のインデックスを別に作る）
            indexes = @Index(name = "idx_post_tags_tag_id", columnList = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    /** 投稿日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最終更新日時（投稿の編集に備える） */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Post() {
        // JPA 用
    }

    /**
     * 入力値は API 側で入力チェック済みであること。写真の枚数とタグの有無はここでも確認する。
     *
     * @param imageUrls 写真の保存先 URL。並びがそのまま表示順になる（先頭がサムネイル）
     */
    public Post(User author, String title, FashionCategory fashionCategory, String wornItems,
            String description, String referenceUrl, List<String> imageUrls, Collection<Tag> tags) {
        this.author = author;
        this.title = title;
        this.fashionCategory = fashionCategory;
        this.wornItems = wornItems;
        this.description = description;
        this.referenceUrl = referenceUrl;
        replaceImages(imageUrls);
        replaceTags(tags);
    }

    /**
     * 写真を、渡した URL の並びに置き換える（並びがそのまま表示順になる）。
     * 既存の行は表示順を変えずに URL だけを差し替え、余った分は削除・足りない分は追加する。
     * 全件を消して入れ直すと、Hibernate は削除より先に追加を実行するため、表示順の一意制約に違反してしまう
     */
    public void replaceImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.size() < MIN_IMAGES || imageUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("写真は" + MIN_IMAGES + "枚以上" + MAX_IMAGES + "枚以下にしてください");
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            if (i < images.size()) {
                images.get(i).changeImageUrl(imageUrls.get(i));
            } else {
                images.add(new PostImage(this, i + 1, imageUrls.get(i)));
            }
        }
        while (images.size() > imageUrls.size()) {
            images.remove(images.size() - 1);
        }
    }

    /** タグを置き換える。同じタグを重複して渡しても1つにまとまる */
    public void replaceTags(Collection<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("タグを1つ以上設定してください");
        }
        this.tags.clear();
        this.tags.addAll(tags);
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

    public User getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public FashionCategory getFashionCategory() {
        return fashionCategory;
    }

    public String getWornItems() {
        return wornItems;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    /** 表示順に並んだ写真（変更は replaceImages() で行う） */
    public List<PostImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    /** 一覧のサムネイルにする1枚目の写真 */
    public PostImage getThumbnail() {
        return images.get(0);
    }

    /** 変更は replaceTags() で行う */
    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
