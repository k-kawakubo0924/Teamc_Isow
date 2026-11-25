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
        String trimmed = exact(query);
        if (trimmed.isEmpty()) {
            return "%";
        }
        return "%" + escape(trimmed) + "%";
    }

    /** 前方一致の LIKE のパターンを作る（contains と同じく小文字にし、% _ \ は文字として扱う）。空なら "%" */
    public static String startsWith(String query) {
        return escape(exact(query)) + "%";
    }

    /** 完全一致の比較に使う値（contains と同じく前後の空白を除いて小文字にする。LIKE ではないためエスケープしない）。null は "" */
    public static String exact(String query) {
        return query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
