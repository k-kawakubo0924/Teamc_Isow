package com.teamc.isow.backend.dm;

/**
 * 会話の承認・拒否・終了ができない。HTTP ステータスは error で決まり、
 * message は画面にそのまま表示できる文言（GlobalExceptionHandler）
 */
public class ConversationOperationException extends RuntimeException {

    private final ConversationOperationError error;

    public ConversationOperationException(ConversationOperationError error, String message) {
        super(message);
        this.error = error;
    }

    public ConversationOperationError getError() {
        return error;
    }
}
