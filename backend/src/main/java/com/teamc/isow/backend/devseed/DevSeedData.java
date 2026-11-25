package com.teamc.isow.backend.devseed;

import com.teamc.isow.backend.devseed.DevSeedImages.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * 開発用のテストデータの中身（README「開発用のテストデータ」）。
 * 画面の確認で困らないよう、表示が分かれる場面（身長の有無・写真の枚数・空の項目・相談の状態など）がそろうようにしている。
 * ユーザーや投稿を足す・変えるときは、README の一覧と DevDataSeederTest もあわせて直すこと
 */
final class DevSeedData {

    /** 全員共通のパスワード（README に書いてある。公開するサーバーには投入しないこと） */
    static final String PASSWORD = "isho1234";

    private DevSeedData() {
    }

    /**
     * ユーザー1人分。メールアドレスは「ユーザー名@example.com」
     *
     * @param displayName 表示名。null ならプロフィールを保存しない（登録しただけの人）
     * @param gender 性別の code（Gender の定数名）。null は未設定
     * @param ageGroup 年代の code（AgeGroup の定数名）。null は未設定
     * @param bodyType 骨格タイプの名前（初期データの名前）。null は未設定
     * @param personalColor パーソナルカラーの名前（初期データの名前）。null は未設定
     */
    record UserDef(String username, String phoneNumber, String displayName, String gender, String ageGroup,
            Integer heightCm, String bodyType, String personalColor, boolean profileImage) {

        String email() {
            return username + "@example.com";
        }
    }

    // 電話番号は、既存のテストと同じく 0900000 で始まる番号にする
    static final List<UserDef> USERS = List.of(
            new UserDef("dev_aoi", "09000009001", "あおい", "FEMALE", "EARLY_20S", 158, "ストレート", "イエベ春", true),
            new UserDef("dev_ren", "09000009002", "れん", "MALE", "LATE_20S", 178, "ナチュラル", "ブルベ冬", true),
            new UserDef("dev_mio", "09000009003", "みお", "FEMALE", "TEENS", null, "ウェーブ", "ブルベ夏", false),
            new UserDef("dev_sora", "09000009004", "そら", "OTHER", "EARLY_30S", 165, "ナチュラル", "イエベ秋", false),
            new UserDef("dev_kenta", "09000009005", "けんた", "MALE", "FORTIES", 170, "ストレート", "イエベ秋", true),
            new UserDef("dev_yui", "09000009006", "ゆい", "FEMALE", "LATE_30S", null, "ウェーブ", "イエベ春", true),
            new UserDef("dev_haru", "09000009007", "はる", "MALE", "FIFTIES_AND_OVER", null, null, null, false),
            new UserDef("dev_new", "09000009008", null, null, null, null, null, null, false));

    /**
     * 投稿1件分
     *
     * @param category ファッションの種類の名前（初期データの名前）
     * @param photos 写真（枚数と縦横の比率）
     * @param tags タグ（公式タグも手入力のタグも名前で指定する。画面から送るのと同じ）
     * @param wornItems 着用アイテム。null は空
     * @param referenceUrl 参考情報の URL。null は空
     */
    record PostDef(String key, String author, String title, String category, List<Shape> photos, List<String> tags,
            String wornItems, String referenceUrl, String description) {
    }

    static final List<PostDef> POSTS = List.of(
            new PostDef("P1", "dev_aoi", "春のオフィスカジュアル", "きれいめ",
                    List.of(Shape.PORTRAIT),
                    List.of("オフィスカジュアル", "春コーデ"),
                    "ベージュのジャケット\n白のブラウス\nセンタープレスのパンツ",
                    "https://example.com/items/jacket",
                    "通勤用にそろえたコーデです。\nジャケットは少し大きめを選んでいます。"),
            new PostDef("P2", "dev_aoi", "休日のゆるっとコーデ", "カジュアル",
                    List.of(Shape.PORTRAIT, Shape.SQUARE, Shape.PORTRAIT),
                    List.of("プチプラ", "ゆるコーデ"),
                    "グレーのスウェット\nデニム",
                    null,
                    "全部プチプラでそろえました。3枚目は靴のアップです。"),
            new PostDef("P3", "dev_ren", "モノトーンのセットアップ", "モード",
                    List.of(Shape.PORTRAIT, Shape.PORTRAIT, Shape.SQUARE, Shape.LANDSCAPE, Shape.PORTRAIT),
                    List.of("モノトーン", "セットアップ"),
                    null,
                    "https://example.com/items/setup",
                    "黒のセットアップに白のインナーを合わせました。"),
            new PostDef("P4", "dev_ren", "スニーカーが主役", "ストリート",
                    List.of(Shape.LANDSCAPE),
                    // 「＃Ｙ２Ｋ」は、本番と同じ処理で「Y2K」に整えられる（全角を半角に、先頭の # を除く）
                    List.of("＃Ｙ２Ｋ", "スニーカー"),
                    null,
                    null,
                    "足元だけ。"),
            new PostDef("P5", "dev_mio", "低身長でもバランスよく", "カジュアル",
                    List.of(Shape.PORTRAIT, Shape.SQUARE),
                    List.of("韓国ファッション", "低身長コーデ"),
                    null,
                    null,
                    longDescription()),
            new PostDef("P6", "dev_sora", "デートの日のワンピース", "きれいめ",
                    List.of(Shape.SQUARE),
                    List.of("デートコーデ"),
                    "ネイビーのワンピース\nパールのピアス",
                    null,
                    "落ち着いた色で大人っぽくまとめました。"),
            new PostDef("P7", "dev_kenta", "古着のアメカジ", "カジュアル",
                    List.of(Shape.PORTRAIT, Shape.PORTRAIT, Shape.LANDSCAPE, Shape.SQUARE),
                    List.of("古着", "アメカジ"),
                    "ネルシャツ（古着）\nワークパンツ\nブーツ",
                    "https://example.com/shops/vintage",
                    "古着屋で見つけたネルシャツが主役です。"),
            new PostDef("P8", "dev_yui", "秋冬のロングコート", "モード",
                    List.of(Shape.PORTRAIT, Shape.PORTRAIT),
                    List.of("秋コーデ", "冬コーデ", "ロングコート"),
                    "キャメルのロングコート\n黒のタートルネック",
                    null,
                    "季節の変わり目に着回しています。"),
            new PostDef("P9", "dev_haru", "いつもの古着", "ストリート",
                    List.of(Shape.PORTRAIT),
                    List.of("古着"),
                    null,
                    null,
                    "定番です。"),
            new PostDef("P10", "dev_yui", "高身長コーデを10枚で", "きれいめ",
                    List.of(Shape.PORTRAIT, Shape.SQUARE, Shape.LANDSCAPE, Shape.PORTRAIT, Shape.SQUARE,
                            Shape.LANDSCAPE, Shape.PORTRAIT, Shape.SQUARE, Shape.LANDSCAPE, Shape.PORTRAIT),
                    List.of("高身長コーデ"),
                    null,
                    "https://example.com/items/long-skirt",
                    "写真の上限（10枚）まで載せた投稿です。"));

    /** いいね（いいねした人 → 投稿） */
    record Reaction(String username, String postKey) {
    }

    static final List<Reaction> LIKES = List.of(
            new Reaction("dev_ren", "P1"), new Reaction("dev_mio", "P1"), new Reaction("dev_sora", "P1"),
            new Reaction("dev_kenta", "P1"),
            new Reaction("dev_ren", "P2"), new Reaction("dev_yui", "P2"),
            new Reaction("dev_aoi", "P3"), new Reaction("dev_mio", "P3"), new Reaction("dev_sora", "P3"),
            new Reaction("dev_aoi", "P5"), new Reaction("dev_ren", "P5"), new Reaction("dev_haru", "P5"),
            new Reaction("dev_aoi", "P7"),
            new Reaction("dev_aoi", "P10"));

    static final List<Reaction> FAVORITES = List.of(
            new Reaction("dev_aoi", "P3"), new Reaction("dev_aoi", "P5"), new Reaction("dev_aoi", "P7"),
            new Reaction("dev_ren", "P1"), new Reaction("dev_mio", "P1"));

    /** フォロー（フォローする人 → される人） */
    record FollowDef(String follower, String followee) {
    }

    static final List<FollowDef> FOLLOWS = List.of(
            new FollowDef("dev_aoi", "dev_ren"), new FollowDef("dev_aoi", "dev_mio"),
            new FollowDef("dev_aoi", "dev_kenta"),
            new FollowDef("dev_ren", "dev_aoi"), new FollowDef("dev_ren", "dev_kenta"),
            new FollowDef("dev_mio", "dev_aoi"),
            new FollowDef("dev_sora", "dev_aoi"),
            new FollowDef("dev_yui", "dev_aoi"), new FollowDef("dev_yui", "dev_ren"),
            new FollowDef("dev_haru", "dev_ren"));

    /** 検索履歴（検索した人 → キーワード）。投稿の検索とユーザーの検索を、画面と同じ処理で行って記録する */
    record SearchDef(String username, String keyword, boolean users) {
    }

    static final List<SearchDef> SEARCHES = List.of(
            new SearchDef("dev_aoi", "古着", false),
            new SearchDef("dev_aoi", "韓国ファッション", false),
            new SearchDef("dev_aoi", "セットアップ", false),
            // 1件も見つからない言葉
            new SearchDef("dev_aoi", "ワンピース", false),
            new SearchDef("dev_aoi", "dev_ren", true),
            new SearchDef("dev_ren", "モノトーン", false));

    /** 表示の確認用の長い投稿説明（約1500文字・改行あり） */
    private static String longDescription() {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("身長150cmで、いつも全身のバランスに悩んでいます。今回は、韓国ファッションを参考にしながら、"
                + "低身長でもすっきり見えるコーデを考えてみました。");
        String[] points = {
            "トップスは短めの丈を選び、ウエストの位置を高く見せています。",
            "ボトムスはハイウエストのワイドパンツで、脚が長く見えるようにしました。",
            "靴は厚底のスニーカーにして、目立たないように身長を足しています。",
            "色は上下を近い色でそろえて、縦のラインが途切れないようにしました。",
            "小物はバッグを小さめにして、全体が重たく見えないようにしています。",
        };
        for (int round = 1; round <= 5; round++) {
            paragraphs.add("【ポイント" + round + "】");
            for (String point : points) {
                paragraphs.add("・" + point);
            }
            paragraphs.add("ほかにも試したことがあれば、コメントの代わりに相談で教えてもらえるとうれしいです。"
                    + "同じように悩んでいる人の参考になればと思って、気づいたことをまとめておきます。");
        }
        paragraphs.add("最後まで読んでいただきありがとうございました。");
        return String.join("\n", paragraphs);
    }
}
