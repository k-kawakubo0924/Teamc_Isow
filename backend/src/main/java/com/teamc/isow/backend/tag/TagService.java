package com.teamc.isow.backend.tag;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    /** 入力候補として返す件数の上限 */
    static final int CANDIDATE_LIMIT = 20;

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * 入力中の文字に部分一致するタグを返す（公式・手入力の両方。無効なものは除く）。
     * 保存時と同じ正規化をしてから検索するため、大文字小文字・全角半角・先頭の # の違いは区別しない。
     * 正規化して空になる入力（未入力・空白のみ・# のみ）は、入力途中でよく起こるためエラーにせず空の一覧を返す
     */
    @Transactional(readOnly = true)
    public List<TagCandidateResponse> searchCandidates(String query) {
        String key = TagNameNormalizer.key(query);
        if (key == null || key.isEmpty()) {
            return List.of();
        }
        return tagRepository.searchCandidates(escapeLike(key), PageRequest.of(0, CANDIDATE_LIMIT)).stream()
                .map(TagCandidateResponse::from)
                .toList();
    }

    /** 入力中の % や _ が LIKE のワイルドカードとして働かないようにする（エスケープ文字は !） */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
