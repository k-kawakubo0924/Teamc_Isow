package com.teamc.isow.backend.dm;

import java.util.List;

/**
 * 会話のメッセージ（1回分）。
 *
 * @param messages 古い順のメッセージ。before も after も指定しなければ最新の size 件
 * @param hasMore 続きがあるか。before を指定した（または何も指定しない）場合は、さらに古いメッセージがあるか
 *     （ある場合は before に messages の先頭の id を指定して読む）。after を指定した場合は、さらに新しいメッセージがあるか
 *     （ある場合は after に messages の最後の id を指定して読む）
 */
public record MessageListResponse(List<MessageResponse> messages, boolean hasMore) {
}
