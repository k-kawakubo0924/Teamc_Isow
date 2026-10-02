package com.teamc.isow.backend.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 投稿の写真（docs/post.md）。画像そのものは DB に保存せず、保存先の URL のみを持つ。
 * 作成・並び替えは Post.replaceImages() からのみ行い、表示順が連番になるようにする。
 */
@Entity
@Table(
        name = "post_images",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_images_post_sort_order", columnNames = {"post_id", "sort_order"}))
public class PostImage {

    /** 写真を一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** この写真が属する投稿 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** 表示順（1 から始まる連番）。1 枚目は一覧のサムネイルになる。同じ投稿内で重複不可 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 画像の保存先 URL（開発中はローカル、本番はクラウドストレージを想定） */
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    protected PostImage() {
        // JPA 用
    }

    PostImage(Post post, int sortOrder, String imageUrl) {
        this.post = post;
        this.sortOrder = sortOrder;
        this.imageUrl = imageUrl;
    }

    /** 表示順はそのままで、画像だけを差し替える（Post.replaceImages() から使う） */
    void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
