package com.teamc.isow.backend.contact;

/** 申し立て用の鍵が正しくない（署名・期限・用途の誤り）、または鍵の本人がいない（401） */
public class AppealTokenInvalidException extends RuntimeException {

    public AppealTokenInvalidException() {
        super("invalid appeal token");
    }
}
