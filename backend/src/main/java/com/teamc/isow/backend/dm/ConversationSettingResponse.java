package com.teamc.isow.backend.dm;

/**
 * 自分の、相談ごとの設定（POST・DELETE /api/conversations/{id}/pin・mute の応答。docs/dm.md「ピン留め」「通知オフ」）。
 * 相手の設定は返さない
 */
public record ConversationSettingResponse(Long conversationId, boolean pinned, boolean muted) {
}
