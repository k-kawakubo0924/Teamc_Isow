package com.teamc.isow.backend.tag;

import com.teamc.isow.backend.common.InputValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    /** 入力候補として返す件数の上限 */
    static final int CANDIDATE_LIMIT = 20;

    /** 1つの投稿に付けられるタグの数の上限（暫定。docs/post.md で未確定） */
    public static final int MAX_TAGS_PER_POST = 10;

    /** 投稿時に入力できるタグ1つあたりの文字数の上限（暫定。docs/post.md で未確定） */
    public static final int MAX_INPUT_LENGTH = 30;

    private static final String FIELD = "tags";

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

    /**
     * 投稿に付けるタグ名を確認し、表示名の形に整えて返す（まだ登録はしない）。
     * 表記ゆれで同じになるタグは1つにまとめる（先に入力した表記を使う）。
     *
     * @throws InputValidationException 空・文字数超過・個数超過・無効なタグが含まれる場合（項目名は tags）
     */
    @Transactional(readOnly = true)
    public List<String> validateForPost(List<String> names) {
        Map<String, String> displayNameByKey = new LinkedHashMap<>();
        for (String name : names == null ? List.<String>of() : names) {
            String displayName = TagNameNormalizer.displayName(name);
            if (displayName == null || displayName.isEmpty()) {
                throw InputValidationException.of(FIELD, "空のタグは設定できません");
            }
            if (displayName.length() > MAX_INPUT_LENGTH) {
                throw InputValidationException.of(FIELD, "タグは1つ" + MAX_INPUT_LENGTH + "文字以内で入力してください");
            }
            displayNameByKey.putIfAbsent(TagNameNormalizer.key(name), displayName);
        }
        if (displayNameByKey.isEmpty()) {
            throw InputValidationException.of(FIELD, "タグを1つ以上設定してください");
        }
        if (displayNameByKey.size() > MAX_TAGS_PER_POST) {
            throw InputValidationException.of(FIELD, "タグは" + MAX_TAGS_PER_POST + "個まで設定できます");
        }
        throwIfInactive(tagRepository.findByNormalizedNameIn(displayNameByKey.keySet()));
        return List.copyOf(displayNameByKey.values());
    }

    /**
     * validateForPost() で整えたタグ名から、投稿に付けるタグを返す。
     * 正規化した値で既存のタグを探し、なければ手入力のタグ（official = false）として登録する。
     * 呼び出し元のトランザクションの中で実行し、投稿の登録が失敗した場合は作成したタグもロールバックされるようにする。
     * 同じ新しいタグが同時に作られた場合は一意制約違反（DataIntegrityViolationException）になるため、呼び出し元でやり直すこと
     */
    @Transactional
    public List<Tag> findOrCreateForPost(List<String> displayNames) {
        Map<String, Tag> existingByKey = tagRepository.findByNormalizedNameIn(
                        displayNames.stream().map(TagNameNormalizer::key).toList()).stream()
                .collect(Collectors.toMap(Tag::getNormalizedName, Function.identity()));
        // 確認の後に無効にされた場合に備えて、もう一度確認する
        throwIfInactive(existingByKey.values());

        List<Tag> tags = new ArrayList<>();
        for (String displayName : displayNames) {
            Tag tag = existingByKey.get(TagNameNormalizer.key(displayName));
            tags.add(tag != null ? tag : tagRepository.save(Tag.userInput(displayName)));
        }
        return tags;
    }

    /** 無効にされたタグ（不適切なタグなど）は、手入力でも付けられないようにする */
    private static void throwIfInactive(Iterable<Tag> tags) {
        for (Tag tag : tags) {
            if (!tag.isActive()) {
                throw InputValidationException.of(FIELD, "「" + tag.getName() + "」は使用できないタグです");
            }
        }
    }

    /** 入力中の % や _ が LIKE のワイルドカードとして働かないようにする（エスケープ文字は !） */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
