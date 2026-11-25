package com.teamc.isow.backend.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 連打で同じフォローがほぼ同時に届いた場合の確認。
 * 「まだフォローしていない」と判断した直後に別のリクエストが登録した状況を、確認の結果だけを差し替えて再現する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FollowRaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private FollowRepository followRepository;

    @AfterEach
    void tearDown() {
        FollowTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void 同時に登録されて一意制約違反になっても_フォロー済みとして成功する() throws Exception {
        User follower = userRepository.save(new User(FollowTestSupport.EMAIL_A, "09000000020", "hash", "follow_a"));
        User followee = userRepository.save(new User(FollowTestSupport.EMAIL_B, "09000000021", "hash", "follow_b"));
        // 先に届いたリクエストが登録済み
        followRepository.save(new Follow(follower, followee));
        // このリクエストの確認では「未フォロー」と判断させる（2回目以降の確認は本物の結果）
        Answer<?> callReal = mockingDetails(followRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(false).doAnswer(callReal).when(followRepository).existsActive(any(), any());

        mockMvc.perform(post("/api/users/" + followee.getId() + "/follow")
                        .header("Authorization", "Bearer " + FollowTestSupport.token(jwtEncoder, follower.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM follows", Integer.class)).isEqualTo(1);
    }
}
