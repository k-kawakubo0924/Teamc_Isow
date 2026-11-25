package com.teamc.isow.backend.contact;

/** 申し立て用の鍵を受け取った後に、利用停止が解除された・期限を過ぎた・別の利用停止になった（409） */
public class SuspensionChangedException extends RuntimeException {

    public SuspensionChangedException() {
        super("suspension changed");
    }
}
