package com.teamc.isow.backend.tag;

/** タグの入力候補（GET /api/tags）。official は画面で公式タグに印を付けるために返す */
public record TagCandidateResponse(Long id, String name, boolean official) {

    public static TagCandidateResponse from(Tag tag) {
        return new TagCandidateResponse(tag.getId(), tag.getName(), tag.isOfficial());
    }
}
