package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.Tag;
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

/** プロフィールのテストで共通に使う部品 */
final class ProfileTestSupport {

    static final String EMAIL_ME = "profile-me@example.com";
    static final String EMAIL_OTHER = "profile-other@example.com";
    static final String EMAIL_THIRD = "profile-third@example.com";

    private ProfileTestSupport() {
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

    /** 画像ファイルは使わないため、URL だけを持つ投稿（写真3枚・タグ2つ）を作る */
    static Post savePost(PostRepository postRepository, FashionCategoryRepository fashionCategoryRepository,
            TagRepository tagRepository, User author, String title) {
        List<FashionCategory> categories = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        List<Tag> tags = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();
        return postRepository.save(new Post(author, title, categories.get(0), null, "説明", null,
                List.of("http://localhost/uploads/" + title + "-1.jpg", "http://localhost/uploads/" + title + "-2.jpg",
                        "http://localhost/uploads/" + title + "-3.jpg"),
                List.of(tags.get(0), tags.get(1))));
    }

    /** テストで作ったデータを消す（投稿を消すと likes・favorites は DB の連鎖削除で消える） */
    static void cleanUp(JdbcTemplate jdbcTemplate) {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?)", EMAIL_ME, EMAIL_OTHER, EMAIL_THIRD);
    }
}
