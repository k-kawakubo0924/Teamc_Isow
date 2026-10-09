package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagNameNormalizer;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.tag.TagService;
import com.teamc.isow.backend.user.User;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 公式タグの管理（docs/admin.md「マスタの管理」）。
 * タグは手入力のタグと同じ表で持ち、追加するときに手入力のタグを公式にすることがあるため、他のマスタ（AdminMasterService）とは別に作る。
 * 状態を変える操作は、同じトランザクションで管理操作のログを残す（残せなければ操作も取り消す）
 */
@Service
public class AdminOfficialTagService {

    private final AdminAccess adminAccess;
    private final AdminOperationLogger operationLogger;
    private final TagRepository tagRepository;

    public AdminOfficialTagService(AdminAccess adminAccess, AdminOperationLogger operationLogger,
            TagRepository tagRepository) {
        this.adminAccess = adminAccess;
        this.operationLogger = operationLogger;
        this.tagRepository = tagRepository;
    }

    /** 公式タグを、無効なものも含めて並び順・ID 順に返す（手入力のタグは含めない） */
    @Transactional(readOnly = true)
    public AdminMasterListResponse list(String subject) {
        adminAccess.requireAdmin(subject);
        return new AdminMasterListResponse(
                tagRepository.findByOfficialTrueOrderByDisplayOrderAscIdAsc().stream().map(AdminMasterItem::from).toList());
    }

    /**
     * 公式タグを末尾に追加する。名前の文字数の上限は、投稿時に手入力できるタグと同じ。
     * 表記ゆれだけが違うタグがすでにある場合：
     * <ul>
     *   <li>公式タグ（無効なものも含む）なら、重複として 400</li>
     *   <li>手入力のタグなら、新しく作らずにそのタグを公式にする（同じ行のままのため、投稿との関連付けは残る）</li>
     * </ul>
     */
    @Transactional
    public AdminOfficialTagCreateResponse create(String subject, String name) {
        User admin = adminAccess.requireAdmin(subject);
        String displayName =
                AdminMasterNames.requireValid(TagNameNormalizer.displayName(name), TagService.MAX_INPUT_LENGTH);
        int displayOrder = AdminMasterNames.nextDisplayOrder(tagRepository.findMaxOfficialDisplayOrder());
        Optional<Tag> existing = tagRepository.findByNormalizedName(TagNameNormalizer.key(displayName));
        if (existing.isPresent()) {
            Tag tag = existing.get();
            if (tag.isOfficial()) {
                throw InputValidationException.of(AdminMasterNames.FIELD,
                        "同じ名前の公式タグがすでにあります（無効にしているものも含みます）");
            }
            tag.makeOfficial(displayOrder);
            operationLogger.recordByAdmin(admin, AdminAction.TAG_MADE_OFFICIAL, AdminTargetType.TAG, tag.getId(),
                    AdminMasterNames.detail(tag.getName()));
            return new AdminOfficialTagCreateResponse(AdminMasterItem.from(tag), true);
        }
        Tag created = tagRepository.save(Tag.official(displayName, displayOrder));
        operationLogger.recordByAdmin(admin, AdminAction.MASTER_CREATED, AdminTargetType.TAG, created.getId(),
                AdminMasterNames.detail(created.getName()));
        return new AdminOfficialTagCreateResponse(AdminMasterItem.from(created), false);
    }

    /** 選択肢・入力候補に出さないようにする。すでに無効なら何もしない（ログも残さない） */
    @Transactional
    public AdminMasterItem deactivate(String subject, Long id) {
        User admin = adminAccess.requireAdmin(subject);
        Tag tag = findOfficial(id);
        if (tag.isActive()) {
            tag.deactivate();
            operationLogger.recordByAdmin(admin, AdminAction.MASTER_DEACTIVATED, AdminTargetType.TAG, id,
                    AdminMasterNames.detail(tag.getName()));
        }
        return AdminMasterItem.from(tag);
    }

    /** また選択肢・入力候補に出すようにする。すでに有効なら何もしない（ログも残さない） */
    @Transactional
    public AdminMasterItem activate(String subject, Long id) {
        User admin = adminAccess.requireAdmin(subject);
        Tag tag = findOfficial(id);
        if (!tag.isActive()) {
            tag.activate();
            operationLogger.recordByAdmin(admin, AdminAction.MASTER_ACTIVATED, AdminTargetType.TAG, id,
                    AdminMasterNames.detail(tag.getName()));
        }
        return AdminMasterItem.from(tag);
    }

    /** 公式タグを探す。手入力のタグは管理の対象外のため、存在しないものと同じ 404 にする */
    private Tag findOfficial(Long id) {
        return tagRepository.findById(id)
                .filter(Tag::isOfficial)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
