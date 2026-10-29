package com.teamc.isow.backend.image;

import java.util.regex.Pattern;

/**
 * 画像のキー（保存先の中での名前）の形。ImageUploadService.store() が付ける「乱数32桁 + 拡張子」だけを、キーとして扱う。
 * この形には、パスの区切り（/）や LIKE の特別な文字（% と _）が含まれないため、保存先のフォルダの外やほかのファイルを指せない
 */
public final class ImageKeys {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{32}\\.(jpg|png)");

    private ImageKeys() {
    }

    /** 保存するときに付ける名前の形か */
    public static boolean isKey(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    /** 保存するときに付ける名前の形でなければ IllegalArgumentException（ほかのファイル・フォルダの外を指させない） */
    static String require(String value) {
        if (!isKey(value)) {
            throw new IllegalArgumentException("画像のキーではありません: " + value);
        }
        return value;
    }
}
