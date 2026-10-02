package com.teamc.isow.backend.master;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 選択肢の取得（GET /api/masters）とタグの入力候補（GET /api/tags）の確認。
 * 初期データ（MasterSeedData）が投入された状態から始まる。
 * 各テストはトランザクション内で行い、終了時にロールバックする（他のテストに影響させない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasterAndTagApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void トークンなしでは401() throws Exception {
        mockMvc.perform(get("/api/masters")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tags").param("q", "古着")).andExpect(status().isUnauthorized());
    }

    @Test
    void 選択肢はマスタがidとname_enumがcodeとlabelで返る() throws Exception {
        getWithToken("/api/masters")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fashionCategories[*].name").value(contains("きれいめ", "カジュアル", "モード", "ストリート")))
                .andExpect(jsonPath("$.fashionCategories[0].id").isNumber())
                .andExpect(jsonPath("$.bodyTypes[*].name").value(contains("ストレート", "ウェーブ", "ナチュラル")))
                .andExpect(jsonPath("$.personalColors[*].name").value(contains("イエベ春", "ブルベ夏", "イエベ秋", "ブルベ冬")))
                .andExpect(jsonPath("$.tags", hasSize(12)))
                .andExpect(jsonPath("$.ageGroups[*].code").value(contains(
                        "TEENS", "EARLY_20S", "LATE_20S", "EARLY_30S", "LATE_30S", "FORTIES", "FIFTIES_AND_OVER")))
                .andExpect(jsonPath("$.ageGroups[1].label").value("20代前半"))
                .andExpect(jsonPath("$.genders[*].code").value(contains("MALE", "FEMALE", "OTHER")))
                .andExpect(jsonPath("$.genders[2].label").value("その他"));
    }

    @Test
    void 選択肢は無効なものを除き_並び順の順に返る() throws Exception {
        jdbcTemplate.update("UPDATE fashion_categories SET is_active = false WHERE name = 'カジュアル'");
        jdbcTemplate.update("UPDATE fashion_categories SET display_order = 5 WHERE name = 'ストリート'");

        getWithToken("/api/masters")
                .andExpect(jsonPath("$.fashionCategories[*].name").value(contains("ストリート", "きれいめ", "モード")));
    }

    @Test
    void 選択肢のタグは有効な公式タグのみ() throws Exception {
        tagRepository.save(Tag.userInput("手入力タグ"));
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name = 'プチプラ'");

        getWithToken("/api/masters")
                .andExpect(jsonPath("$.tags", hasSize(11)))
                .andExpect(jsonPath("$.tags[*].name").value(not(hasItem("手入力タグ"))))
                .andExpect(jsonPath("$.tags[*].name").value(not(hasItem("プチプラ"))))
                .andExpect(jsonPath("$.tags[0].name").value("古着"));
    }

    @Test
    void タグ候補は公式と手入力の両方を返し_前方一致_公式_名前の順に並ぶ() throws Exception {
        tagRepository.save(Tag.userInput("古着MIX"));
        tagRepository.save(Tag.official("ヴィンテージ古着", 999));
        Tag inactive = tagRepository.save(Tag.userInput("古着NG"));
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE id = ?", inactive.getId());

        searchTags("古着")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("古着", "古着MIX", "ヴィンテージ古着")))
                .andExpect(jsonPath("$[0].official").value(true))
                .andExpect(jsonPath("$[1].official").value(false))
                .andExpect(jsonPath("$[0].id").isNumber());
    }

    @Test
    void タグ候補は大文字小文字_全角半角_先頭の記号の違いを区別しない() throws Exception {
        tagRepository.save(Tag.userInput("Korea"));

        for (String q : new String[] {"kor", "KOR", "ＫＯＲ", "#Kor", "＃ ｋｏｒ"}) {
            searchTags(q)
                    .andExpect(jsonPath("$[*].name").value(contains("Korea")));
        }
    }

    @Test
    void タグ候補は20件まで() throws Exception {
        for (int i = 1; i <= 25; i++) {
            tagRepository.save(Tag.userInput("テスト" + i));
        }

        searchTags("テスト").andExpect(jsonPath("$", hasSize(20)));
    }

    @Test
    void タグ候補の検索でパーセントやアンダースコアはワイルドカードにならない() throws Exception {
        tagRepository.save(Tag.userInput("綿100%"));
        tagRepository.save(Tag.userInput("snake_case"));

        searchTags("%").andExpect(jsonPath("$[*].name").value(contains("綿100%")));
        searchTags("_").andExpect(jsonPath("$[*].name").value(contains("snake_case")));
        searchTags("!").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void タグ候補は入力が空なら空の一覧() throws Exception {
        searchTags(null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        searchTags("").andExpect(jsonPath("$", hasSize(0)));
        searchTags("　 ").andExpect(jsonPath("$", hasSize(0)));
        searchTags("#").andExpect(jsonPath("$", hasSize(0)));
    }

    private ResultActions getWithToken(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token()));
    }

    /** q は .param() で渡す（URL に直接書くと % などが二重にエンコードされるため）。null なら q を付けない */
    private ResultActions searchTags(String q) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/tags").header("Authorization", "Bearer " + token());
        if (q != null) {
            request.param("q", q);
        }
        return mockMvc.perform(request);
    }

    private String token() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
