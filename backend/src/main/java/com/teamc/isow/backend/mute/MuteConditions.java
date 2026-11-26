package com.teamc.isow.backend.mute;

/**
 * 「見ている人（:viewerId）がミュートした人の投稿を、流れてくる一覧に出さない」JPQL の条件
 * （docs/profile.md「ミュート（投稿の非表示）」）。
 *
 * <p><b>流れてくる一覧のクエリにだけ使う</b>：ホームの3つのタブ（新着・フォロー中・おすすめ）、投稿の検索（件数も）、
 * ほかの人のプロフィールの「フォロー中の投稿」。
 * プロフィールの投稿一覧・投稿数、投稿の詳細、お気に入り、ユーザー検索、フォロー一覧、通知には使わない（自分から探しに行く場所・
 * 自分で選んだもの・人の一覧のため。使うとミュートがブロックと同じ意味になる）。
 *
 * <p>VisibilityConditions（見えない＝存在しないのと同じ）とは意味が違うため、<b>VisibilityConditions に混ぜないこと</b>。
 * 使う側は VisibilityConditions.POST_VISIBLE と並べて {@code AND} でつなぐ（投稿の別名は {@code p}、パラメーターは {@code :viewerId}）。
 * 件数を返すクエリ（countQuery）にも同じ条件をつなぐこと（一覧と件数がずれないため）。
 * 流れてくる一覧のクエリを新しく作ったら、VisibilityInventoryTest の登録で HIDES にすること。
 * 別名 {@code mt} は、ここの副問い合わせで使うため、使う側のクエリでは使わないこと
 */
public final class MuteConditions {

    /** 投稿者を、見ている人がミュートしていない（クエリで投稿の別名を {@code p} にすること） */
    public static final String POST_AUTHOR_NOT_MUTED = " NOT EXISTS (SELECT 1 FROM UserMute mt"
            + " WHERE mt.muter.id = :viewerId AND mt.muted.id = p.author.id) ";

    private MuteConditions() {
    }
}
