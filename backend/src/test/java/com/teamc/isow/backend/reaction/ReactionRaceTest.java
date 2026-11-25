package com.teamc.isow.backend.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
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
 * 連打で同じいいねがほぼ同時に届いた場合の確認。
 * 「まだいいねしていない」と判断した直後に別のリクエストが登録した状況を、確認の結果だけを差し替えて再現する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReactionRaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PostLikeRepository likeRepository;

    @AfterEach
    void tearDown() {
        ReactionTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void 同時に登録されて一意制約違反になっても_いいね済みとして成功する() throws Exception {
        User user = userRepository.save(new User(ReactionTestSupport.EMAIL_A, "09000000010", "hash", "reaction_a"));
        Post post = ReactionTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, user);
        // 先に届いたリクエストが登録済み
        likeRepository.save(new PostLike(user, post));
        // このリクエストの確認では「未登録」と判断させる（2回目以降の確認は本物の結果）
        Answer<?> callReal = mockingDetails(likeRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(false).doAnswer(callReal).when(likeRepository).existsByUserIdAndPostId(any(), any());

        mockMvc.perform(post("/api/posts/" + post.getId() + "/like")
                        .header("Authorization", "Bearer " + ReactionTestSupport.token(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM likes", Integer.class)).isEqualTo(1);
    }
}
