package com.teamc.isow.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.PersonalColor;
import com.teamc.isow.backend.master.PersonalColorRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * プロフィールの項目（docs/profile.md）の保存と読み込みの確認。
 * 各テストはトランザクション内で行い、終了時にロールバックする（他のテストに影響させない）。
 */
@SpringBootTest
@Transactional
class UserProfileTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BodyTypeRepository bodyTypeRepository;

    @Autowired
    private PersonalColorRepository personalColorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 新規登録したユーザーはユーザ名が表示名になり_他の項目は未設定() {
        User user = reload(userRepository.save(newUser()));

        assertThat(user.getDisplayName()).isEqualTo("profile_test");
        assertThat(user.getGender()).isNull();
        assertThat(user.getHeightCm()).isNull();
        assertThat(user.getAgeGroup()).isNull();
        assertThat(user.getBodyType()).isNull();
        assertThat(user.getPersonalColor()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    void プロフィールを保存して読み直すと保たれ_enumは定数名で保存される() {
        BodyType wave = bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(1);
        PersonalColor summer = personalColorRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(1);
        User user = userRepository.save(newUser());

        user.updateProfile("ユウ", Gender.MALE, 172, AgeGroup.EARLY_20S, wave, summer);
        user.changeProfileImageUrl("http://localhost/uploads/icon.png");
        user = reload(user);

        assertThat(user.getDisplayName()).isEqualTo("ユウ");
        assertThat(user.getGender()).isEqualTo(Gender.MALE);
        assertThat(user.getHeightCm()).isEqualTo(172);
        assertThat(user.getAgeGroup()).isEqualTo(AgeGroup.EARLY_20S);
        assertThat(user.getBodyType().getName()).isEqualTo(wave.getName());
        assertThat(user.getPersonalColor().getName()).isEqualTo(summer.getName());
        assertThat(user.getProfileImageUrl()).isEqualTo("http://localhost/uploads/icon.png");

        // DB には順番の数値ではなく定数名で入っている（表示文言を変えても既存データが壊れない）
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT gender, age_group, body_type_id, personal_color_id FROM users WHERE id = ?", user.getId());
        assertThat(row.get("gender")).isEqualTo("MALE");
        assertThat(row.get("age_group")).isEqualTo("EARLY_20S");
        assertThat(((Number) row.get("body_type_id")).longValue()).isEqualTo(wave.getId());
        assertThat(((Number) row.get("personal_color_id")).longValue()).isEqualTo(summer.getId());
    }

    @Test
    void 任意の項目はnullで未設定に戻せる() {
        BodyType wave = bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(1);
        User user = userRepository.save(newUser());
        user.updateProfile("ユウ", Gender.FEMALE, 160, AgeGroup.TEENS, wave, null);
        user.changeProfileImageUrl("http://localhost/uploads/icon.png");
        user = reload(user);

        user.updateProfile("ユウ", null, null, null, null, null);
        user.changeProfileImageUrl(null);
        user = reload(user);

        assertThat(user.getGender()).isNull();
        assertThat(user.getHeightCm()).isNull();
        assertThat(user.getAgeGroup()).isNull();
        assertThat(user.getBodyType()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    void 性別と年代の列には許可する値の検査制約を付けない() {
        // enum に定数を後から追加しても保存できるよう、列には値の一覧の検査制約を付けない（Gender のコメント参照）。
        // 一覧にない値（後から追加される定数を想定）を直接書き込めることで確かめる
        User user = userRepository.save(newUser());
        entityManager.flush();

        int updated = jdbcTemplate.update(
                "UPDATE users SET gender = 'ADDED_LATER', age_group = 'SIXTIES_AND_OVER' WHERE id = ?", user.getId());

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void 表示名の列を追加する前に登録したユーザーはユーザ名を表示名として返す() {
        User user = userRepository.save(newUser());
        entityManager.flush();
        // 列を追加する前に登録されたユーザー（display_name が NULL）を再現する
        jdbcTemplate.update("UPDATE users SET display_name = NULL WHERE id = ?", user.getId());

        assertThat(reload(user).getDisplayName()).isEqualTo("profile_test");
    }

    private static User newUser() {
        return new User("profile-test@example.com", "09000000099", "hash", "profile_test");
    }

    /** DB に書き込んでから、キャッシュを捨てて読み直す（DB に保存された内容で確認するため） */
    private User reload(User user) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(User.class, user.getId());
    }
}
