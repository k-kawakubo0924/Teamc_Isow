package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.announcement.Announcement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** お知らせの発行。題名・本文は文字だけ（HTML として扱わない） */
public record AdminAnnouncementRequest(
        @NotBlank(message = "題名を入力してください")
        @Size(max = Announcement.MAX_TITLE_LENGTH,
                message = "題名は" + Announcement.MAX_TITLE_LENGTH + "文字以内で入力してください")
        String title,

        @NotBlank(message = "本文を入力してください")
        @Size(max = Announcement.MAX_BODY_LENGTH,
                message = "本文は" + Announcement.MAX_BODY_LENGTH + "文字以内で入力してください")
        String body) {

    public AdminAnnouncementRequest {
        title = strip(title);
        body = strip(normalizeLineBreaks(body));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /** 改行を \n にそろえる（\r\n を2文字と数えて、画面側と文字数の判定がずれないように。投稿の作成と同じ） */
    private static String normalizeLineBreaks(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
