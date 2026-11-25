package com.teamc.isow.backend.dm;

/**
 * 会話が存在しない、または操作する人が当事者でない。どちらも 404 として扱い、会話の有無が分からないようにする
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(Long id) {
        super("conversation not found: " + id);
    }
}
