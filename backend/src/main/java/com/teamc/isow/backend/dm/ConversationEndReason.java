package com.teamc.isow.backend.dm;

/**
 * 相談を終了した理由（docs/dm.md「30日やり取りがない相談の自動終了」）。DB には定数名を文字列で保存する（ConversationStatus と同じ理由）。
 *
 * <p><b>画面・API には理由そのものを返さない</b>。返すのは「自動終了かどうか」（{@link #INACTIVITY} か）だけにする。
 * ブロック・利用停止が理由だと相手に伝わると、ブロックで隠した意味がなくなるため（相手からは「相手が終了した」のと同じに見せる）。
 * この列ができる前に終了した相談は null（理由が分からない）
 */
public enum ConversationEndReason {

    /** 当事者が「会話を終了する」で終えた */
    USER,
    /** ブロックで終えた（docs/dm.md「ブロック」） */
    BLOCK,
    /** 利用停止で終えた（docs/dm.md「利用停止」） */
    SUSPENSION,
    /** 30日やり取りがなかったため、自動で終えた */
    INACTIVITY
}
