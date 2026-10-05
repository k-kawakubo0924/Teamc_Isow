package com.teamc.isow.backend.dm;

import java.util.List;

/**
 * 会話のメッセージ（1回分）。
 *
 * @param messages 古い順のメッセージ。before を指定しなければ最新の size 件
 * @param hasMore さらに古いメッセージがあるか。ある場合は before に messages の先頭の id を指定して読む
 */
public record MessageListResponse(List<MessageResponse> messages, boolean hasMore) {
}
