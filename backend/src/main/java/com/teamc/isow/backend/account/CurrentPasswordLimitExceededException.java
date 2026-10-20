package com.teamc.isow.backend.account;

import com.teamc.isow.backend.common.RetryLaterException;
import java.time.Duration;

/** 現在のパスワードの誤りが続き、一時的に受け付けを止めている（429。CurrentPasswordVerifier） */
public class CurrentPasswordLimitExceededException extends RetryLaterException {

    public CurrentPasswordLimitExceededException(Duration retryAfter) {
        super("current password failure limit exceeded", retryAfter);
    }

    @Override
    public String userMessage() {
        return "現在のパスワードの誤りが続いたため、一時的に受け付けを止めています。あと約" + retryAfterMinutes()
                + "分で、もう一度お試しください。";
    }
}
