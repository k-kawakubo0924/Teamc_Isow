package com.teamc.isow.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 検索履歴の保存（docs/search.md）の確認 */
@SpringBootTest
class SearchHistoryServiceTest {

    private static final String EMAIL_A = "search-a@example.com";
    private static final String EMAIL_B = "search-b@example.com";

    @Autowired
    private SearchHistoryService searchHistoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private SearchHistoryRepository searchHistoryRepository;

    @AfterEach
    void tearDown() {
        // search_histories がユーザーを参照しているため先に消す
        jdbcTemplate.update("DELETE FROM search_histories");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", EMAIL_A, EMAIL_B);
    }

    @Test
    void キーワードを整えて記録する() {
        User user = createUser(EMAIL_A, "search_a");

        searchHistoryService.record(user.getId(), "  Ｙ２Ｋ　コーデ ");

        assertThat(keywords(user)).containsExactly("Y2K コーデ");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT normalized_keyword FROM search_histories WHERE user_id = ?", String.class, user.getId()))
                .isEqualTo("y2k コーデ");
    }

    @Test
    void キーワードなしの検索は記録しない() {
        User user = createUser(EMAIL_A, "search_a");

        searchHistoryService.record(user.getId(), null);
        searchHistoryService.record(user.getId(), "");
        searchHistoryService.record(user.getId(), " 　 ");

        assertThat(keywords(user)).isEmpty();
    }

    @Test
    void 同じキーワードで再度検索したら_行を増やさず日時と表示する形を更新する() {
        User user = createUser(EMAIL_A, "search_a");
        searchHistoryService.record(user.getId(), "y2k");
        searchHistoryService.record(user.getId(), "古着");
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        jdbcTemplate.update("UPDATE search_histories SET searched_at = ?", Timestamp.valueOf(past));

        searchHistoryService.record(user.getId(), "Ｙ２Ｋ");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_histories", Integer.class)).isEqualTo(2);
        // 再度検索した語が一番新しくなり、表示する形は最後に入力したものになる
        assertThat(keywords(user)).containsExactly("Y2K", "古着");
        LocalDateTime searchedAt = jdbcTemplate.queryForObject(
                "SELECT searched_at FROM search_histories WHERE normalized_keyword = 'y2k'", LocalDateTime.class);
        assertThat(searchedAt).isAfter(past);
    }

    @Test
    void 上限を超えたら_検索日時の古いものから消す() {
        User user = createUser(EMAIL_A, "search_a");
        for (int i = 1; i <= SearchHistoryService.MAX_PER_USER; i++) {
            searchHistoryService.record(user.getId(), "word" + i);
        }
        // 一番古い word1 を検索し直すと、word2 が一番古くなる
        searchHistoryService.record(user.getId(), "word1");

        searchHistoryService.record(user.getId(), "new");

        List<String> keywords = keywords(user);
        assertThat(keywords).hasSize(SearchHistoryService.MAX_PER_USER);
        assertThat(keywords).startsWith("new", "word1").doesNotContain("word2");
    }

    @Test
    void 上限は1ユーザーごとに数え_他のユーザーの履歴は消さない() {
        User userA = createUser(EMAIL_A, "search_a");
        User userB = createUser(EMAIL_B, "search_b");
        searchHistoryService.record(userB.getId(), "other");

        for (int i = 1; i <= SearchHistoryService.MAX_PER_USER + 1; i++) {
            searchHistoryService.record(userA.getId(), "word" + i);
        }

        assertThat(keywords(userA)).hasSize(SearchHistoryService.MAX_PER_USER);
        assertThat(keywords(userB)).containsExactly("other");
    }

    @Test
    void 同じキーワードが同時に記録されて一意制約違反になっても_日時の更新として成功する() {
        User user = createUser(EMAIL_A, "search_a");
        // 先に届いた検索が記録済み
        searchHistoryService.record(user.getId(), "y2k");
        // この検索の確認では「履歴にない」と判断させる（2回目以降の確認は本物の結果）
        Answer<?> callReal = mockingDetails(searchHistoryRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(Optional.empty()).doAnswer(callReal)
                .when(searchHistoryRepository).findByUserIdAndNormalizedKeyword(any(), any());

        searchHistoryService.record(user.getId(), "Y2K");

        assertThat(keywords(user)).containsExactly("Y2K");
    }

    @Test
    void 長すぎるキーワードは受け付けない() {
        User user = createUser(EMAIL_A, "search_a");
        String tooLong = "a".repeat(SearchHistory.MAX_KEYWORD_LENGTH + 1);

        assertThatThrownBy(() -> searchHistoryService.record(user.getId(), tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(keywords(user)).isEmpty();
    }

    @Test
    void 存在しないユーザーの記録は失敗する() {
        assertThatThrownBy(() -> searchHistoryService.record(-1L, "y2k"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser(String email, String username) {
        String phone = EMAIL_A.equals(email) ? "09000000040" : "09000000041";
        return userRepository.save(new User(email, phone, "hash", username));
    }

    /** ユーザーの履歴のキーワードを新しい順に返す */
    private List<String> keywords(User user) {
        return jdbcTemplate.queryForList(
                "SELECT keyword FROM search_histories WHERE user_id = ? ORDER BY searched_at DESC, id DESC",
                String.class, user.getId());
    }
}
