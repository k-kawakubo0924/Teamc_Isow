package com.teamc.isow.backend.master;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 画面の選択肢（マスタ・enum）の取得。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest） */
@RestController
@RequestMapping("/api/masters")
public class MasterController {

    private final MasterService masterService;

    public MasterController(MasterService masterService) {
        this.masterService = masterService;
    }

    @GetMapping
    public MastersResponse masters() {
        return masterService.getMasters();
    }
}
