package com.teamc.isow.backend.post;

import com.teamc.isow.backend.block.VisibilityConditions;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 指定したユーザーの投稿を新しい順に返す（同じ投稿日時なら ID の大きい順）。
     * 全件数を数えず、次のページの有無だけを判定する（Slice）
     */
    Slice<Post> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId, Pageable pageable);

    // ---- ホームの投稿一覧（TimelineService）。ID だけを並び順どおりに取り、中身は別の SQL でまとめて読む ----
    // 一覧は、見ている人（viewerId）とブロックの間柄にある人・利用停止中の人の投稿と、非公開の投稿を除く
    // （VisibilityConditions。docs/profile.md「ブロック」・docs/admin.md「6. 措置」）

    /** 新着：投稿日時の新しい順（同じなら ID の大きい順） */
    @Query("SELECT p.id FROM Post p WHERE" + VisibilityConditions.POST_VISIBLE + "ORDER BY p.createdAt DESC, p.id DESC")
    Slice<Long> findLatestIds(@Param("viewerId") Long viewerId, @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * フォロー中：viewerId のユーザーが（有効に）フォローしている人の投稿を、新しい順（同じなら ID の大きい順）。
     * 解除から5分以内のフォローは含めない（解除済みのため）
     */
    @Query("""
            SELECT p.id FROM Post p
            WHERE p.author.id IN (
                SELECT f.followee.id FROM Follow f WHERE f.follower.id = :viewerId AND f.active = true)
              AND""" + VisibilityConditions.POST_VISIBLE + """
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Slice<Long> findFollowingIds(@Param("viewerId") Long viewerId, @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * プロフィールの投稿一覧：指定したユーザーの投稿を新しい順（同じなら ID の大きい順）。
     * 見ている人から見えない投稿（非公開など）は除く。本人が見る場合は、非公開の投稿も含む（VisibilityConditions）
     */
    @Query("SELECT p.id FROM Post p WHERE p.author.id = :authorId AND" + VisibilityConditions.POST_VISIBLE
            + "ORDER BY p.createdAt DESC, p.id DESC")
    Slice<Long> findIdsByAuthorId(@Param("authorId") Long authorId, @Param("viewerId") Long viewerId,
            @Param("now") LocalDateTime now, Pageable pageable);

    /** プロフィールの投稿件数。一覧と同じく、見ている人から見えない投稿は数えない */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId AND" + VisibilityConditions.POST_VISIBLE)
    long countVisibleByAuthorId(@Param("authorId") Long authorId, @Param("viewerId") Long viewerId,
            @Param("now") LocalDateTime now);

    /**
     * 相手のプロフィールの「フォロー中の投稿」：userId のユーザーが（有効に）フォローしている人の投稿を、ランダムな順で返す。
     *
     * <p>ページごとに並べ直すと、次のページで同じ投稿が出たり、出ない投稿ができたりするため、
     * 画面を開いたときに決めた seed で並び順を固定する（ID × seed を素数で割った余りの順。seed ごとに並び順が変わる）。
     * PostgreSQL と H2（テスト）の両方で動くよう、DB 固有の乱数関数は使わない。
     * seed は 1 以上 {@link #SHUFFLE_MODULUS} 未満であること。見ている人（viewerId）とブロックの間柄にある人の投稿は除く
     */
    @Query("""
            SELECT p.id FROM Post p
            WHERE p.author.id IN (
                SELECT f.followee.id FROM Follow f WHERE f.follower.id = :userId AND f.active = true)
              AND""" + VisibilityConditions.POST_VISIBLE + """
            ORDER BY MOD(p.id * :seed, 2147483647), p.id
            """)
    Slice<Long> findIdsByFollowedAuthorsShuffled(@Param("userId") Long userId, @Param("viewerId") Long viewerId, @Param("now") LocalDateTime now,
            @Param("seed") long seed, Pageable pageable);

    /** findIdsByFollowedAuthorsShuffled で割る素数（2^31 - 1。クエリの中の値と同じ） */
    long SHUFFLE_MODULUS = 2_147_483_647L;

    /**
     * おすすめ（暫定。docs/home.md）：いいね数の多い順 → 新しい順 → ID の大きい順。
     * 表示のたびに全投稿のいいね数を集計するため、投稿数が増えたら posts にいいね数の列を持たせる方式に切り替える
     */
    @Query("""
            SELECT p.id FROM Post p
            LEFT JOIN PostLike l ON l.post = p
            WHERE""" + VisibilityConditions.POST_VISIBLE + """
            GROUP BY p.id, p.createdAt
            ORDER BY COUNT(l) DESC, p.createdAt DESC, p.id DESC
            """)
    Slice<Long> findRecommendedIds(@Param("viewerId") Long viewerId, @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 投稿の検索（docs/search.md。SearchService）：条件をすべて満たす投稿を、いいね数の多い順 → 新しい順 → ID の大きい順。
     * null の条件は絞り込みに使わない。
     *
     * @param pattern 題名・投稿説明・タグ名の部分一致（小文字にした LIKE のパターン。SearchPatterns.contains で作る）
     * @param ageGroup 投稿者の年代（AgeGroup の定数名）
     * @param viewerId 検索する人。この人とブロックの間柄にある人の投稿は除く（件数も同じ）
     */
    @Query(value = """
            SELECT p.id FROM Post p
            LEFT JOIN PostLike l ON l.post = p
            WHERE""" + VisibilityConditions.POST_VISIBLE + """
              AND (:categoryId IS NULL OR p.fashionCategory.id = :categoryId)
              AND (:ageGroup IS NULL OR p.author.ageGroup = :ageGroup)
              AND (:pattern IS NULL
                   OR LOWER(p.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(p.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT t.id FROM p.tags t WHERE t.normalizedName LIKE :pattern ESCAPE '\\'))
            GROUP BY p.id, p.createdAt
            ORDER BY COUNT(l) DESC, p.createdAt DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(p) FROM Post p
            WHERE""" + VisibilityConditions.POST_VISIBLE + """
              AND (:categoryId IS NULL OR p.fashionCategory.id = :categoryId)
              AND (:ageGroup IS NULL OR p.author.ageGroup = :ageGroup)
              AND (:pattern IS NULL
                   OR LOWER(p.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(p.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT t.id FROM p.tags t WHERE t.normalizedName LIKE :pattern ESCAPE '\\'))
            """)
    Page<Long> searchIds(@Param("viewerId") Long viewerId, @Param("now") LocalDateTime now, @Param("pattern") String pattern, @Param("categoryId") Long categoryId,
            @Param("ageGroup") String ageGroup, Pageable pageable);

    /**
     * 投稿の詳細：投稿者とファッションの種類を一緒に読み込む（別々に SQL を発行しないため）。
     * 写真とタグは一緒に読み込まない（両方を JOIN すると「写真の枚数 × タグの数」の行が返るため）。
     * 写真・タグはそれぞれ1本の SQL でまとめて読まれる（PostService.get）
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author JOIN FETCH p.fashionCategory WHERE p.id = :id")
    Optional<Post> findDetailById(@Param("id") Long id);

    /** 管理画面の措置：非公開にした投稿（投稿者も一緒に読む）。非公開にした日時の新しい順（docs/admin.md「6. 措置」） */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.hiddenAt IS NOT NULL ORDER BY p.hiddenAt DESC, p.id DESC")
    Slice<Post> findHidden(Pageable pageable);

    /** 投稿者とファッションの種類を一緒に読み込む（投稿ごとに SQL を発行しないため）。並び順は保証しない */
    @Query("SELECT p FROM Post p JOIN FETCH p.author JOIN FETCH p.fashionCategory WHERE p.id IN :ids")
    List<Post> findWithAuthorAndFashionCategoryByIdIn(@Param("ids") Collection<Long> ids);

    /** 1枚目の写真（一覧のサムネイル）だけを、まとめて読み込む */
    @Query("""
            SELECT new com.teamc.isow.backend.post.PostThumbnail(i.post.id, i.imageUrl)
            FROM PostImage i
            WHERE i.post.id IN :postIds AND i.sortOrder = 1
            """)
    List<PostThumbnail> findThumbnails(@Param("postIds") Collection<Long> postIds);
}
