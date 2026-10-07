package com.teamc.isow.backend.reaction;

import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** いいね・お気に入りのテストで共通に使う部品 */
final class ReactionTestSupport {

    static final String EMAIL_A = "reaction-a@example.com";
    static final String EMAIL_B = "reaction-b@example.com";

    private ReactionTestSupport() {
    }

    /** 画像ファイルは使わないため、URL だけを持つ投稿を作る */
    static Post savePost(PostRepository postRepository, FashionCategoryRepository fashionCategoryRepository,
            TagRepository tagRepository, User author) {
        return postRepository.save(new Post(author, "投稿", fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0),
                null, "説明", null, List.of("http://localhost/uploads/a.jpg"),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));
    }

    static String token(JwtEncoder jwtEncoder, long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** テストで作ったデータを消す（投稿を消すと likes・favorites は DB の連鎖削除で消える） */
    static void cleanUp(JdbcTemplate jdbcTemplate) {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", EMAIL_A, EMAIL_B);
    }
}
