package com.teamc.isow.backend.dm;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationUserSettingRepository extends JpaRepository<ConversationUserSetting, Long> {

    /** userId の、その相談の設定（なければ空） */
    @Query("SELECT s FROM ConversationUserSetting s WHERE s.user.id = :userId AND s.conversation.id = :conversationId")
    Optional<ConversationUserSetting> findOne(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    /** userId の、指定した相談の設定（DM一覧のページ分をまとめて読む。設定のない相談は含まれない） */
    @Query("""
            SELECT s FROM ConversationUserSetting s
            WHERE s.user.id = :userId AND s.conversation.id IN :conversationIds
            """)
    List<ConversationUserSetting> findForConversations(
            @Param("userId") Long userId, @Param("conversationIds") Collection<Long> conversationIds);

    /** userId がピン留めしている相談の数（DM一覧の見出しのマークのバッジと、上限の確かめ） */
    @Query("SELECT COUNT(s) FROM ConversationUserSetting s WHERE s.user.id = :userId AND s.pinnedAt IS NOT NULL")
    long countPinned(@Param("userId") Long userId);
}
