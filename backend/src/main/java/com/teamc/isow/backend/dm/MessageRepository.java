package com.teamc.isow.backend.dm;

import org.springframework.data.jpa.repository.JpaRepository;

/** メッセージ（messages） */
public interface MessageRepository extends JpaRepository<Message, Long> {
}
