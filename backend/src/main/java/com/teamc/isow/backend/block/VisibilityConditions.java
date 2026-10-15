package com.teamc.isow.backend.block;

/**
 * 「見ている人（:viewerId）から見えない相手・投稿を除く」JPQL の条件（docs/profile.md「ブロック」・docs/admin.md「6. 措置」）。
 *
 * <p><b>一覧・件数の SQL で「見えないものを除く」条件は、必ずここの定数を使うこと</b>（自分で書かない）。
 * 条件をここ1か所にまとめ、ブロック・非公開・利用停止のどれかを書き忘れることを防ぐ。
 * 使う側は {@code @Query} の文字列に {@code +} でつなぎ、パラメーター {@code :viewerId}（見ている人の ID）と
 * {@code :now}（今の時刻。Clock から読む）を渡す。新しい API を作ったときは VisibilityInventoryTest に登録すること。
 *
 * <p>見えないもの：
 * <ul>
 *   <li>ブロックの間柄にある相手（どちらの向きでも）とその投稿</li>
 *   <li>利用停止中のユーザーとその投稿（期限を過ぎていれば停止中ではない。User.isSuspendedAt と同じ判定）</li>
 *   <li>管理者が非公開にした投稿（投稿者本人には見える）</li>
 * </ul>
 * 自分自身は、利用停止・非公開でも見える（利用停止中は、そもそも API を使えない）。
 *
 * <p>今の時刻を DB の関数（CURRENT_TIMESTAMP など）ではなく :now で渡すのは、アプリの時刻は日本時間で保存しているが、
 * DB（PostgreSQL）の時計は UTC のため（9時間ずれる）。また、テストで時刻を固定できるようにするため。
 * <b>:now を DB の現在時刻に置き換えないこと</b>（エラーは出ず、期限の判定だけが静かに9時間ずれる。docs/admin.md「時刻の扱い」）。
 * NOT EXISTS の副問い合わせと、行の列の比較だけで、件数に関係なく SQL の本数は増えない。
 * 別名 {@code bk} は、ここの副問い合わせで使うため、使う側のクエリでは使わないこと
 */
public final class VisibilityConditions {

    private static final String BLOCK_PREFIX = " NOT EXISTS (SELECT 1 FROM Block bk WHERE"
            + " (bk.blocker.id = :viewerId AND bk.blocked.id = ";
    private static final String BLOCK_MIDDLE = ") OR (bk.blocked.id = :viewerId AND bk.blocker.id = ";
    private static final String BLOCK_SUFFIX = ")) ";

    /** ユーザー（別名 user）が利用停止中でない。期限を過ぎていれば停止中ではない */
    private static final String NOT_SUSPENDED_PREFIX = "(";
    private static final String NOT_SUSPENDED_MIDDLE = ".suspendedAt IS NULL OR (";
    private static final String NOT_SUSPENDED_SUFFIX = ".suspendedUntil IS NOT NULL AND ";

    /**
     * 投稿が見える（クエリで投稿の別名を {@code p} にすること）。
     * 投稿者とブロックの間柄になく、自分の投稿であるか、または（非公開でなく、投稿者が利用停止中でない）
     */
    public static final String POST_VISIBLE = " ("
            + BLOCK_PREFIX + "p.author.id" + BLOCK_MIDDLE + "p.author.id" + BLOCK_SUFFIX
            + " AND (p.author.id = :viewerId OR (p.hiddenAt IS NULL AND "
            + NOT_SUSPENDED_PREFIX + "p.author" + NOT_SUSPENDED_MIDDLE + "p.author" + NOT_SUSPENDED_SUFFIX
            + "p.author.suspendedUntil <= :now))))) ";

    /**
     * ユーザーが見える（クエリでユーザーの別名を {@code u} にすること）。
     * ブロックの間柄になく、自分自身であるか、または利用停止中でない
     */
    public static final String USER_VISIBLE = " ("
            + BLOCK_PREFIX + "u.id" + BLOCK_MIDDLE + "u.id" + BLOCK_SUFFIX
            + " AND (u.id = :viewerId OR "
            + NOT_SUSPENDED_PREFIX + "u" + NOT_SUSPENDED_MIDDLE + "u" + NOT_SUSPENDED_SUFFIX
            + "u.suspendedUntil <= :now)))) ";

    private VisibilityConditions() {
    }
}
