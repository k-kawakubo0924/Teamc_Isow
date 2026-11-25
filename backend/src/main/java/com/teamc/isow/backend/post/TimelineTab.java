package com.teamc.isow.backend.post;

import java.util.Arrays;
import java.util.Optional;

/** ホームの投稿一覧のタブ（docs/home.md） */
public enum TimelineTab {

    /** おすすめ（暫定でいいね数の多い順） */
    RECOMMENDED("recommended"),
    /** フォロー中（ログイン中のユーザーがフォローしている人の投稿を、投稿日時の新しい順） */
    FOLLOWING("following"),
    /** 新着（投稿日時の新しい順） */
    LATEST("latest");

    /** API の tab パラメータで使う値 */
    private final String param;

    TimelineTab(String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }

    public static Optional<TimelineTab> fromParam(String value) {
        return Arrays.stream(values()).filter(tab -> tab.param.equals(value)).findFirst();
    }
}
