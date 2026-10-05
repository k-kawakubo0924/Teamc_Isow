package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;

/**
 * メッセージ1件（チャット画面）。
 *
 * @param id メッセージのID。さらに古いメッセージを読むときの before に使う
 * @param senderId 送信者のユーザーID
 * @param mine ログイン中のユーザーが送ったものか（画面の左右の出し分けに使う）
 * @param body 本文。画像だけのメッセージは null
 * @param imageUrl 画像の URL。画像がなければ null
 * @param sentAt 送信日時
 * @param readAt 相手が読んだ日時。未読は null
 */
public record MessageResponse(
        Long id,
        Long senderId,
        boolean mine,
        String body,
        String imageUrl,
        LocalDateTime sentAt,
        LocalDateTime readAt) {

    /** 送信者は ID だけを使う（送信者のユーザーを読み込む SQL を発行しないため） */
    static MessageResponse of(Message message, Long viewerId) {
        Long senderId = message.getSender().getId();
        return new MessageResponse(
                message.getId(),
                senderId,
                senderId.equals(viewerId),
                message.getBody(),
                message.getImageUrl(),
                message.getSentAt(),
                message.getReadAt());
    }
}
