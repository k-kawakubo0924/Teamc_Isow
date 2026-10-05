package com.teamc.isow.backend.profile;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * プロフィール・フォローの API は、すべてログインが必要であることの確認。
 * トークンがない場合も、正しくないトークンの場合も 401 になり、処理（存在確認なども含む）は行わない。
 * 認証の確認は処理より先に行われるため、ユーザーID は存在しない値でよい
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileAndFollowAuthTest {

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/users/me"),
                Arguments.of(HttpMethod.PUT, "/api/users/me"),
                Arguments.of(HttpMethod.POST, "/api/users/me/profile-image"),
                Arguments.of(HttpMethod.GET, "/api/users/me/favorites"),
                Arguments.of(HttpMethod.GET, "/api/users/1"),
                Arguments.of(HttpMethod.GET, "/api/users/1/posts"),
                Arguments.of(HttpMethod.GET, "/api/users/1/following-posts?seed=1"),
                Arguments.of(HttpMethod.POST, "/api/users/1/follow"),
                Arguments.of(HttpMethod.DELETE, "/api/users/1/follow"),
                Arguments.of(HttpMethod.GET, "/api/users/1/followings"),
                Arguments.of(HttpMethod.GET, "/api/users/1/followers"),
                Arguments.of(HttpMethod.GET, "/api/posts?tab=following"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void トークンがない場合は401(HttpMethod method, String url) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.request(method, url)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void 正しくないトークンの場合は401(HttpMethod method, String url) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.request(method, url).header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }
}
