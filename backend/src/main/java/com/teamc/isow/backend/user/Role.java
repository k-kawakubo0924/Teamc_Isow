package com.teamc.isow.backend.user;

/**
 * ユーザーの権限（docs/admin.md「権限の仕組み」）。
 *
 * <p>DB には定数名を文字列の列として保存する（User.role を参照）。
 * {@code @Enumerated} は使わない。Hibernate が列に値の一覧の検査制約を付け、ddl-auto=update では定数を追加しても
 * 制約が更新されず、新しい値を保存できなくなるため（権限の段階を増やす可能性がある。docs/admin.md の未確定）。
 * 定数名は既存データとの対応に使うため変更しないこと。
 *
 * <p>管理者かどうかの判定は User.isAdmin() だけで行う（「ADMIN と一致するか」で判定し、NULL や想定外の値は管理者にしない）。
 */
public enum Role {

    /** 一般の利用者（新規会員登録で作られるユーザーは常にこれ） */
    USER,
    /** 管理者（/admin・/api/admin/** を使える）。画面からは付けられず、起動時に ADMIN_EMAIL のユーザーにだけ付ける */
    ADMIN
}
