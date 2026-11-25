package com.teamc.isow.backend.post;

/** 投稿の作成・編集のリクエストで、受け取った文字を整える（PostCreateRequest・PostEditRequest で共通） */
final class PostText {

    private PostText() {
    }

    static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /**
     * 改行を \n にそろえる。ブラウザは multipart/form-data で送るときに改行を \r\n に変えるため、
     * そのままだと改行が2文字と数えられ、画面側（改行は1文字）と文字数の判定がずれる
     */
    static String normalizeLineBreaks(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
