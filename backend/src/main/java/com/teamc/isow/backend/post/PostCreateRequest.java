package com.teamc.isow.backend.post;

import com.teamc.isow.backend.common.HttpUrl;
import com.teamc.isow.backend.tag.TagService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿作成のリクエスト（multipart/form-data）。各項目は同じ名前のパートで受け取る。
 * 受け取った時点で前後の空白を除き、任意項目は空なら未入力（null）として扱う。
 * 文字数の上限は docs/post.md の暫定値。
 *
 * @param images 写真（送られた順が表示順。1枚目がサムネイル）。中身の検証は ImageUploadService で行う
 * @param tags タグ名（公式タグ・手入力のタグとも名前で受け取る）。正規化・個数の確認は TagService で行う
 */
public record PostCreateRequest(
        @NotEmpty(message = "写真を1枚以上選択してください")
        @Size(max = Post.MAX_IMAGES, message = "写真は" + Post.MAX_IMAGES + "枚まで選択できます")
        List<MultipartFile> images,

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

    public PostCreateRequest {
        title = strip(title);
        wornItems = blankToNull(strip(normalizeLineBreaks(wornItems)));
        description = strip(normalizeLineBreaks(description));
        referenceUrl = blankToNull(strip(referenceUrl));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /**
     * 改行を \n にそろえる。ブラウザは multipart/form-data で送るときに改行を \r\n に変えるため、
     * そのままだと改行が2文字と数えられ、画面側（改行は1文字）と文字数の判定がずれる
     */
    private static String normalizeLineBreaks(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
