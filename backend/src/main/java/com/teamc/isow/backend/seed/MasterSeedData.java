package com.teamc.isow.backend.seed;

import java.util.List;

/**
 * マスタの初期データ。起動時に MasterDataSeeder が未投入のものだけを登録する。
 * 選択肢を追加するときは各一覧の末尾に足す（手順は README の「マスタデータ（選択肢）の追加」を参照）。
 * 投入済みかどうかは「種類:名前」をキーに履歴で管理しているため、既存の行の名前を書き換えたり消したりしないこと。
 */
final class MasterSeedData {

    private MasterSeedData() {
    }

    /** 初期データ1件分。displayOrder は後から間に追加できるよう 10 ずつ空けている */
    record Item(String name, int displayOrder) {
    }

    /** ファッションの種類（docs/post.md） */
    static final List<Item> FASHION_CATEGORIES = List.of(
            new Item("きれいめ", 10),
            new Item("カジュアル", 20),
            new Item("モード", 30),
            new Item("ストリート", 40));

    /** 骨格タイプ（docs/profile.md） */
    static final List<Item> BODY_TYPES = List.of(
            new Item("ストレート", 10),
            new Item("ウェーブ", 20),
            new Item("ナチュラル", 30));

    /** パーソナルカラー（docs/profile.md） */
    static final List<Item> PERSONAL_COLORS = List.of(
            new Item("イエベ春", 10),
            new Item("ブルベ夏", 20),
            new Item("イエベ秋", 30),
            new Item("ブルベ冬", 40));

    /** 公式タグ（docs/post.md）。ファッションの種類と重ならないよう、シーン・悩み・テイストで選んでいる */
    static final List<Item> OFFICIAL_TAGS = List.of(
            new Item("古着", 10),
            new Item("プチプラ", 20),
            new Item("韓国ファッション", 30),
            new Item("モノトーン", 40),
            new Item("オフィスカジュアル", 50),
            new Item("デートコーデ", 60),
            new Item("低身長コーデ", 70),
            new Item("高身長コーデ", 80),
            new Item("春コーデ", 90),
            new Item("夏コーデ", 100),
            new Item("秋コーデ", 110),
            new Item("冬コーデ", 120));
}
