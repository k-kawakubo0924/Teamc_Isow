package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.auth.UnknownTokenUserException;
import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.image.ImageUploadService;
import com.teamc.isow.backend.image.InvalidImageException;
import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.MasterEntity;
import com.teamc.isow.backend.master.PersonalColor;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * プロフィール編集（docs/profile.md。design/EditProfile.png）。
 * 応答は更新後のプロフィール（GET /api/users/me と同じ形）。
 */
@Service
public class ProfileEditService {

    private final UserRepository userRepository;
    private final BodyTypeRepository bodyTypeRepository;
    private final PersonalColorRepository personalColorRepository;
    private final ImageUploadService imageUploadService;
    private final ProfileService profileService;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public ProfileEditService(
            UserRepository userRepository,
            BodyTypeRepository bodyTypeRepository,
            PersonalColorRepository personalColorRepository,
            ImageUploadService imageUploadService,
            ProfileService profileService,
            AuthService authService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.bodyTypeRepository = bodyTypeRepository;
        this.personalColorRepository = personalColorRepository;
        this.imageUploadService = imageUploadService;
        this.profileService = profileService;
        this.authService = authService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * プロフィール画像以外の項目をまとめて置き換える。
     *
     * @throws InputValidationException 存在しない選択肢を指定した場合（何も保存しない）
     */
    public ProfileResponse update(String subject, ProfileUpdateRequest request) {
        Long userId = authService.requireCurrentUser(subject).getId();
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findWithProfileById(userId).orElseThrow(UnknownTokenUserException::new);
            Map<String, String> errors = new LinkedHashMap<>();
            Gender gender = parseCode(Gender.values(), request.gender(), "gender", "性別", errors);
            AgeGroup ageGroup = parseCode(AgeGroup.values(), request.ageGroup(), "ageGroup", "年代", errors);
            BodyType bodyType = findSelectable(bodyTypeRepository, request.bodyTypeId(), user.getBodyType(),
                    "bodyTypeId", "骨格タイプ", errors);
            PersonalColor personalColor = findSelectable(personalColorRepository, request.personalColorId(),
                    user.getPersonalColor(), "personalColorId", "パーソナルカラー", errors);
            if (!errors.isEmpty()) {
                throw new InputValidationException(errors);
            }
            user.updateProfile(request.displayName(), gender, request.heightCm(), ageGroup, bodyType, personalColor);
        });
        return profileService.getMine(subject);
    }

    /**
     * プロフィール画像を変更する（投稿の写真と同じ仕組みで検証・保存する）。
     * DB の更新に失敗したら新しい画像を削除し、成功したら前の画像を削除する。
     *
     * @throws InvalidImageException 画像の条件を満たさない場合（何も保存しない）
     */
    public ProfileResponse changeImage(String subject, MultipartFile image) {
        Long userId = authService.requireCurrentUser(subject).getId();
        String newUrl = imageUploadService.store(imageUploadService.prepare(image));
        String oldUrl;
        try {
            oldUrl = transactionTemplate.execute(status -> {
                User user = userRepository.findById(userId).orElseThrow(UnknownTokenUserException::new);
                String previous = user.getProfileImageUrl();
                user.changeProfileImageUrl(newUrl);
                return previous;
            });
        } catch (RuntimeException e) {
            imageUploadService.deleteQuietly(List.of(newUrl));
            throw e;
        }
        if (oldUrl != null) {
            imageUploadService.deleteQuietly(List.of(oldUrl));
        }
        return profileService.getMine(subject);
    }

    /** enum の code（定数名）を変換する。null は未設定。存在しない code はエラーに追加して null を返す */
    private static <E extends Enum<E>> E parseCode(
            E[] values, String code, String field, String label, Map<String, String> errors) {
        if (code == null) {
            return null;
        }
        Optional<E> found = Arrays.stream(values).filter(value -> value.name().equals(code)).findFirst();
        if (found.isEmpty()) {
            errors.put(field, label + "の選択肢が正しくありません");
        }
        return found.orElse(null);
    }

    /**
     * マスタの選択肢を探す。null は未設定。選択肢として出しているもの（有効なもの）だけを受け付けるが、
     * すでに設定済みの選択肢は、非表示になっていてもそのまま保存できる（docs/profile.md。設定済みのプロフィールを壊さないため）
     */
    private static <M extends MasterEntity> M findSelectable(JpaRepository<M, Long> repository, Long id, M current,
            String field, String label, Map<String, String> errors) {
        if (id == null) {
            return null;
        }
        if (current != null && current.getId().equals(id)) {
            return current;
        }
        Optional<M> found = repository.findById(id).filter(MasterEntity::isActive);
        if (found.isEmpty()) {
            errors.put(field, label + "の選択肢が正しくありません");
        }
        return found.orElse(null);
    }
}
