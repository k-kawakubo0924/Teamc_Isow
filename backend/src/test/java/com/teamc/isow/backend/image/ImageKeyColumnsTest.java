package com.teamc.isow.backend.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 画像を指す列は、保存先のキー（ファイル名）を持つ列だけにする（R2 の作業のステップ1）。
 * 以前はホスト名・ポート番号入りの URL（http://localhost:8081/uploads/…）を DB に持っていて、保存先や URL の形を変えると
 * DB を書き換える必要があった。表示の URL は、応答を作るときにキーから組み立てる
 */
@SpringBootTest
class ImageKeyColumnsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 画像を指す列は_キーの列で_URLの列は残っていない() {
        assertThat(columns("POST_IMAGES")).contains("IMAGE_KEY").doesNotContain("IMAGE_URL");
        assertThat(columns("MESSAGES")).contains("IMAGE_KEY").doesNotContain("IMAGE_URL");
        assertThat(columns("USERS")).contains("PROFILE_IMAGE_KEY").doesNotContain("PROFILE_IMAGE_URL");
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?", String.class, table);
    }
}
