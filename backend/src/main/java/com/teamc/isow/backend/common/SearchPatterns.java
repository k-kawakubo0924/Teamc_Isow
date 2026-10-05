package com.teamc.isow.backend.common;

import java.util.Locale;

/** 検索欄の文字から、SQL の LIKE で使うパターンを作る（フォロー一覧・DM一覧の検索で共通） */
public final class SearchPatterns {

    private SearchPatterns() {
    }

    /**
     * 部分一致の LIKE のパターンを作る（小文字にし、% _ \ は文字として扱う）。空なら "%"（すべてに一致する）。
     * SQL 側では LOWER(列) LIKE :pattern ESCAPE '\' と書くこと
     */
    public static String contains(String query) {
        String trimmed = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "%";
        }
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
