package com.teamc.isow.backend.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** 投稿 API のテストで共通に使う部品 */
final class PostApiTestSupport {

    static final String EMAIL = "post-api-test@example.com";

    private PostApiTestSupport() {
    }

    /**
     * POST /api/posts を送るリクエスト。必須項目は正しい値で埋めてある（写真1枚、タグは公式タグの「古着」）。
     * token が null の場合は Authorization ヘッダーを付けない（未ログイン）。
     *
     * @param overrides 既定値を置き換える項目（項目名, 値, 項目名, 値, ...）。
     *     .param() は値を追加するだけで置き換えないため、既定値を変えるときはこちらを使う
     */
    static MockMultipartHttpServletRequestBuilder validRequest(String token, long fashionCategoryId, String... overrides) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("title", "秋の羽織りもの");
        params.put("fashionCategoryId", String.valueOf(fashionCategoryId));
        params.put("tags", "古着");
        params.put("description", "丈が長めのコートなので、下は細めにまとめています。");
        for (int i = 0; i < overrides.length; i += 2) {
            params.put(overrides[i], overrides[i + 1]);
        }

        MockMultipartHttpServletRequestBuilder request = multipart("/api/posts");
        request.file(new MockMultipartFile("images", "1.png", "image/png", image(30, 20, "png")));
        params.forEach(request::param);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    static String token(JwtEncoder jwtEncoder, long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    static byte[] image(int width, int height, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 保存先フォルダにあるファイル名の一覧（テストの前後で比べ、画像が残っていないことを確かめる） */
    static Set<String> storedFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** テストで作った投稿・手入力のタグ・ユーザーを消し、無効にした公式タグを戻す（他のテストに影響させない） */
    static void cleanUp(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("UPDATE tags SET is_active = true");
        jdbcTemplate.update("UPDATE fashion_categories SET is_active = true");
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", EMAIL);
    }
}
