package com.teamc.isow.backend.post;

import com.teamc.isow.backend.common.HttpUrl;
import com.teamc.isow.backend.tag.TagService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 投稿の編集のリクエスト（JSON。docs/post.md「投稿の編集」）。
 * 写真は編集できないため受け取らない（写真の項目を送っても無視する）。
 * 入力チェックと文字の整え方は投稿の作成（PostCreateRequest）と同じ。変えるときは両方を直すこと
 *
 * @param tags タグ名（公式タグ・手入力のタグとも名前で受け取る）。正規化・個数の確認は TagService で行う
 */
public record PostEditRequest(
        @NotBlank(message = "題名を入力してください")
        @Size(max = 100, message = "題名は100文字以内で入力してください")
        String title,

        @NotNull(message = "ファッションの種類を選択してください")
        Long fashionCategoryId,

        @NotEmpty(message = "タグを1つ以上設定してください")
        @Size(max = TagService.MAX_TAGS_PER_POST, message = "タグは" + TagService.MAX_TAGS_PER_POST + "個まで設定できます")
        List<String> tags,

        @Size(max = 1000, message = "着用アイテムは1000文字以内で入力してください")
        String wornItems,

        @NotBlank(message = "投稿説明を入力してください")
        @Size(max = 2000, message = "投稿説明は2000文字以内で入力してください")
        String description,

        @Size(max = 2048, message = "参考情報のURLは2048文字以内で入力してください")
        @HttpUrl(message = "参考情報は http:// または https:// で始まるURLを入力してください")
        String referenceUrl) {

    public PostEditRequest {
        title = PostText.strip(title);
        wornItems = PostText.blankToNull(PostText.strip(PostText.normalizeLineBreaks(wornItems)));
        description = PostText.strip(PostText.normalizeLineBreaks(description));
        referenceUrl = PostText.blankToNull(PostText.strip(referenceUrl));
    }
}
