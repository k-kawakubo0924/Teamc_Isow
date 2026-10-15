package com.teamc.isow.backend.contact;

/** 1人1日のお問い合わせの上限（ContactService.MAX_PER_DAY）を超えた（429） */
public class ContactLimitExceededException extends RuntimeException {

    public ContactLimitExceededException() {
        super("contact limit exceeded");
    }
}
