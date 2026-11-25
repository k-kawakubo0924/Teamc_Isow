package com.teamc.isow.backend.image;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * DB に保存した画像のキーから、ブラウザに渡す URL を組み立てる。応答に画像の URL を入れるところは、すべてここを通す。
 *
 * <p>DB にはキーだけを持ち、URL を持たない。以前はホスト名・ポート番号入りの URL を DB に持っていて、保存先や URL の形を変えると
 * DB を書き換える必要があった（R2 に移すと、URL の形が変わる）。URL を組み立てるところを1か所にしておけば、変えるのはここだけで済む。
 *
 * <p>今はローカルの保存先（/uploads/**。app.image.local.public-base-url）の URL を返す。
 */
@Component
public class ImageUrls {

    private final String publicBaseUrl;

    public ImageUrls(ImageProperties properties) {
        this.publicBaseUrl = properties.local().publicBaseUrl().replaceAll("/+$", "");
    }

    /**
     * キーの画像の URL。キーが null（画像がない）なら null。
     * キーの形でない値（以前の URL が残っている DB など）は IllegalArgumentException（壊れた URL を黙って返さない）
     */
    public String of(String key) {
        return key == null ? null : publicBaseUrl + "/" + ImageKeys.require(key);
    }

    /** キーの並びの URL（並び順は変えない） */
    public List<String> of(List<String> keys) {
        return keys.stream().map(this::of).toList();
    }
}
