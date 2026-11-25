package com.teamc.isow.backend.admin;

import java.util.List;

/** 管理画面のマスタ・公式タグの一覧。無効なものも含めて、並び順・ID 順に並ぶ */
public record AdminMasterListResponse(List<AdminMasterItem> items) {
}
