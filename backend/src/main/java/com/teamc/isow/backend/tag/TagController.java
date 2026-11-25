package com.teamc.isow.backend.tag;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** タグの入力候補（投稿画面用）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest） */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** q が未指定の場合も、空の一覧を返す（エラーにしない） */
    @GetMapping
    public List<TagCandidateResponse> candidates(@RequestParam(name = "q", required = false) String q) {
        return tagService.searchCandidates(q);
    }
}
