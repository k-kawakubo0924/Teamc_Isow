package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.common.NameNormalizer;
import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.master.MasterEntity;
import com.teamc.isow.backend.master.MasterRepository;
import com.teamc.isow.backend.master.PersonalColor;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.user.User;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * ファッションの種類・骨格タイプ・パーソナルカラーの管理（docs/admin.md「マスタの管理」）。
 * 3つは同じ作りのため、種類（AdminMasterKind）を指定して同じ処理を使う。
 * できるのは一覧・追加・無効化・有効に戻すことだけ（削除・名前の変更・並び替えはしない）。
 * 状態を変える操作は、同じトランザクションで管理操作のログを残す（残せなければ操作も取り消す）
 */
@Service
public class AdminMasterService {

    /** 名前の文字数の上限（列の長さ） */
    static final int MAX_NAME_LENGTH = 50;

    private final AdminAccess adminAccess;
    private final AdminOperationLogger operationLogger;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final BodyTypeRepository bodyTypeRepository;
    private final PersonalColorRepository personalColorRepository;

    public AdminMasterService(
            AdminAccess adminAccess,
            AdminOperationLogger operationLogger,
            FashionCategoryRepository fashionCategoryRepository,
            BodyTypeRepository bodyTypeRepository,
            PersonalColorRepository personalColorRepository) {
        this.adminAccess = adminAccess;
        this.operationLogger = operationLogger;
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.bodyTypeRepository = bodyTypeRepository;
        this.personalColorRepository = personalColorRepository;
    }

    /** 無効なものも含めて、並び順・ID 順に返す */
    @Transactional(readOnly = true)
    public AdminMasterListResponse list(String subject, AdminMasterKind kind) {
        adminAccess.requireAdmin(subject);
        return new AdminMasterListResponse(
                repository(kind).findAllByOrderByDisplayOrderAscIdAsc().stream().map(AdminMasterItem::from).toList());
    }

    /**
     * 末尾に追加する。名前は表記ゆれをそろえてから保存し、すでにあるもの（無効なものも含む）と
     * 表記ゆれだけが違う場合は重複として 400 にする
     */
    @Transactional
    public AdminMasterItem create(String subject, AdminMasterKind kind, String name) {
        User admin = adminAccess.requireAdmin(subject);
        String displayName = AdminMasterNames.requireValid(NameNormalizer.displayName(name), MAX_NAME_LENGTH);
        List<? extends MasterEntity> all = repository(kind).findAllByOrderByDisplayOrderAscIdAsc();
        // マスタは件数が少ないため、すべて読み込んで比べる（名前の列は表記ゆれをそろえる前の初期データもあるため、DB の一意制約だけでは防げない）。
        // タグと違い、先頭の # は削らない（マスタの名前では意味のある文字として残す）
        String key = NameNormalizer.key(displayName);
        if (all.stream().anyMatch(master -> key.equals(NameNormalizer.key(master.getName())))) {
            throw InputValidationException.of(AdminMasterNames.FIELD,
                    "同じ名前の" + kind.getLabel() + "がすでにあります（無効にしているものも含みます）");
        }
        Integer currentMax = all.stream().map(MasterEntity::getDisplayOrder).max(Integer::compare).orElse(null);
        MasterEntity created = save(kind, displayName, AdminMasterNames.nextDisplayOrder(currentMax));
        operationLogger.recordByAdmin(admin, AdminAction.MASTER_CREATED, kind.getTargetType(), created.getId(),
                AdminMasterNames.detail(created.getName()));
        return AdminMasterItem.from(created);
    }

    /** 選択肢に出さないようにする。すでに無効なら何もしない（ログも残さない） */
    @Transactional
    public AdminMasterItem deactivate(String subject, AdminMasterKind kind, Long id) {
        User admin = adminAccess.requireAdmin(subject);
        MasterEntity master = find(kind, id);
        if (master.isActive()) {
            master.deactivate();
            operationLogger.recordByAdmin(admin, AdminAction.MASTER_DEACTIVATED, kind.getTargetType(), id,
                    AdminMasterNames.detail(master.getName()));
        }
        return AdminMasterItem.from(master);
    }

    /** また選択肢に出すようにする。すでに有効なら何もしない（ログも残さない） */
    @Transactional
    public AdminMasterItem activate(String subject, AdminMasterKind kind, Long id) {
        User admin = adminAccess.requireAdmin(subject);
        MasterEntity master = find(kind, id);
        if (!master.isActive()) {
            master.activate();
            operationLogger.recordByAdmin(admin, AdminAction.MASTER_ACTIVATED, kind.getTargetType(), id,
                    AdminMasterNames.detail(master.getName()));
        }
        return AdminMasterItem.from(master);
    }

    private MasterEntity find(AdminMasterKind kind, Long id) {
        return repository(kind).findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private MasterRepository<? extends MasterEntity> repository(AdminMasterKind kind) {
        return switch (kind) {
            case FASHION_CATEGORY -> fashionCategoryRepository;
            case BODY_TYPE -> bodyTypeRepository;
            case PERSONAL_COLOR -> personalColorRepository;
        };
    }

    /** 種類ごとに違うのは、作るエンティティのクラスだけ */
    private MasterEntity save(AdminMasterKind kind, String name, int displayOrder) {
        return switch (kind) {
            case FASHION_CATEGORY -> fashionCategoryRepository.save(new FashionCategory(name, displayOrder));
            case BODY_TYPE -> bodyTypeRepository.save(new BodyType(name, displayOrder));
            case PERSONAL_COLOR -> personalColorRepository.save(new PersonalColor(name, displayOrder));
        };
    }
}
