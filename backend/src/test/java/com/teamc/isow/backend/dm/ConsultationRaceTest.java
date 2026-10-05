package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2人がほぼ同時に申し込んだ場合の確認。
 * 「会話がない」と判断した直後に相手の申込が保存された状況を、確認の結果だけを差し替えて再現する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsultationRaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ConversationRepository conversationRepository;

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void 同時に申し込まれて一意制約違反になっても_500ではなく理由付きの409を返す() throws Exception {
        User a = userRepository.save(new User(DmTestSupport.EMAILS[0], "09000000030", "hash", "dm_a"));
        User b = userRepository.save(new User(DmTestSupport.EMAILS[1], "09000000031", "hash", "dm_b"));
        // 先に届いた B からの申込が保存済み
        conversationRepository.save(new Conversation(b, a));
        // このリクエストの確認では「会話なし」と判断させる（2回目以降の確認は本物の結果）
        Answer<?> callReal = mockingDetails(conversationRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(List.of()).doAnswer(callReal).when(conversationRepository).findOngoingWith(any(), any());

        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + DmTestSupport.token(jwtEncoder, a.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + b.getId() + ",\"message\":\"よろしくお願いします\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("REQUEST_RECEIVED"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM conversations", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM messages", Integer.class)).isZero();
    }
}
