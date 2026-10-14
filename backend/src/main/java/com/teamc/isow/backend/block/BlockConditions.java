package com.teamc.isow.backend.block;

/**
 * 「見ている人（:viewerId）とブロックの間柄にある相手を除く」JPQL の条件（docs/profile.md「ブロック」）。
 *
 * <p><b>一覧・件数の SQL で「ブロックした相手を除く」条件は、必ずここの定数を使うこと</b>（自分で書かない）。
 * 条件をここ1か所にまとめ、書き方の違いや片方向だけの条件になる書き間違いを防ぐ。
 * 使う側は {@code @Query} の文字列に {@code +} でつなぎ、パラメーター {@code :viewerId} に見ている人の ID を渡す。
 * 新しい API を作ったときは BlockVisibilityTest に登録すること（登録しないとテストが失敗する）。
 *
 * <p>ブロックの効果は両方向のため、「viewer が相手をブロックした」「相手が viewer をブロックした」のどちらでも除く。
 * NOT EXISTS の1本の副問い合わせで、件数に関係なく SQL の本数は増えない。
 * 別名 {@code bk} は、ここの副問い合わせで使うため、使う側のクエリでは使わないこと
 */
public final class BlockConditions {

    private static final String PREFIX = " NOT EXISTS (SELECT 1 FROM Block bk WHERE"
            + " (bk.blocker.id = :viewerId AND bk.blocked.id = ";
    private static final String MIDDLE = ") OR (bk.blocked.id = :viewerId AND bk.blocker.id = ";
    private static final String SUFFIX = ")) ";

    /** 投稿の投稿者が見える（クエリで投稿の別名を {@code p} にすること） */
    public static final String POST_AUTHOR_VISIBLE = PREFIX + "p.author.id" + MIDDLE + "p.author.id" + SUFFIX;

    /** ユーザーが見える（クエリでユーザーの別名を {@code u} にすること） */
    public static final String USER_VISIBLE = PREFIX + "u.id" + MIDDLE + "u.id" + SUFFIX;

    private BlockConditions() {
    }
}
