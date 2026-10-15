package com.teamc.isow.backend.contact;

/** お問い合わせ・申し立ての結果（201）。受け付けたことだけを返す */
public record ContactResponse(Status status) {

    public enum Status {
        ACCEPTED
    }

    static ContactResponse accepted() {
        return new ContactResponse(Status.ACCEPTED);
    }
}
