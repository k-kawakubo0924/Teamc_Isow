package com.teamc.isow.backend.dm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DM・相談に関する設定（application.properties の app.dm.*）。
 *
 * @param maxReceivedActiveConversations 1人が受けられる「進行中」の相談の上限（docs/dm.md「相談を受けられる件数の上限」）。
 *     自分から申し込んだ会話と、申請中の会話は数えない
 */
@ConfigurationProperties(prefix = "app.dm")
public record DmProperties(int maxReceivedActiveConversations) {
}
