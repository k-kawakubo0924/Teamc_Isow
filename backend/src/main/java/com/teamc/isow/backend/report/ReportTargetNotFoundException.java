package com.teamc.isow.backend.report;

/**
 * 通報する対象が存在しない、通報した人から見えない（相手にブロックされている）、当事者でない会話のメッセージ（404）。
 * どの場合も同じ応答にする（ブロックされたこと・他人の会話の有無が分からないように）
 */
public class ReportTargetNotFoundException extends RuntimeException {

    public ReportTargetNotFoundException() {
        super("report target not found");
    }
}
