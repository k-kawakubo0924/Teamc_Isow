package com.teamc.isow.backend.admin;

/** マスタ・公式タグの追加。名前の確認（空・長さ・重複）は表記ゆれをそろえてからサービスで行う */
public record AdminMasterCreateRequest(String name) {
}
