package com.teamc.isow.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投稿エンティティの確認。保存して読み直したときに、写真の表示順とタグが保たれることを確かめる。
 * 各テストはトランザクション内で行い、終了時にロールバックする（他のテストに影響させない）。
 */
@SpringBootTest
@Transactional
class PostEntityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    private User author;
    private FashionCategory fashionCategory;
    private Tag furugi;
    private Tag outer;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("post-test@example.com", "09000000000", "hash", "post_test"));
        // 初期データのファッションの種類・公式タグを使う
        fashionCategory = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        furugi = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        outer = tagRepository.save(Tag.userInput("アウター"));
    }

    @Test
    void 保存して読み直すと写真が表示順に並び_1枚目がサムネイルになる() {
        Post saved = persistAndReload(newPost(List.of("https://example.com/a.jpg", "https://example.com/b.jpg", "https://example.com/c.jpg")));

        assertThat(saved.getImages()).extracting(PostImage::getImageUrl)
                .containsExactly("https://example.com/a.jpg", "https://example.com/b.jpg", "https://example.com/c.jpg");
        assertThat(saved.getImages()).extracting(PostImage::getSortOrder).containsExactly(1, 2, 3);
        assertThat(saved.getThumbnail().getImageUrl()).isEqualTo("https://example.com/a.jpg");
    }

    @Test
    void 保存して読み直すと各項目とタグが保たれる() {
        Post saved = persistAndReload(newPost(List.of("https://example.com/a.jpg")));

        assertThat(saved.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(saved.getTitle()).isEqualTo("秋の羽織りもの");
        assertThat(saved.getFashionCategory().getId()).isEqualTo(fashionCategory.getId());
        assertThat(saved.getWornItems()).isEqualTo("アウター：古着屋で購入（サイズL）\nパンツ：ブラックスラックス");
        assertThat(saved.getDescription()).isEqualTo("丈が長めのコートなので、下は細めにまとめています。");
        assertThat(saved.getReferenceUrl()).isNull();
        assertThat(saved.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(furugi.getId(), outer.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 保存済みの投稿の写真を並べ替え_減らし_増やしても表示順が連番のまま保たれる() {
        Post post = persistAndReload(newPost(List.of("https://example.com/a.jpg", "https://example.com/b.jpg", "https://example.com/c.jpg")));

        // 並べ替え＋1枚減らす（表示順の一意制約に違反しないこと）
        post.replaceImages(List.of("https://example.com/c.jpg", "https://example.com/a.jpg"));
        post = reload(post);
        assertThat(post.getImages()).extracting(PostImage::getImageUrl)
                .containsExactly("https://example.com/c.jpg", "https://example.com/a.jpg");
        assertThat(post.getImages()).extracting(PostImage::getSortOrder).containsExactly(1, 2);

        // 増やす
        post.replaceImages(List.of("https://example.com/d.jpg", "https://example.com/c.jpg", "https://example.com/a.jpg", "https://example.com/e.jpg"));
        post = reload(post);
        assertThat(post.getImages()).extracting(PostImage::getImageUrl)
                .containsExactly("https://example.com/d.jpg", "https://example.com/c.jpg", "https://example.com/a.jpg", "https://example.com/e.jpg");
        assertThat(post.getImages()).extracting(PostImage::getSortOrder).containsExactly(1, 2, 3, 4);
    }

    @Test
    void 写真は1枚以上10枚以下() {
        assertThat(newPost(urls(10)).getImages()).hasSize(10);
        assertThatThrownBy(() -> newPost(urls(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newPost(urls(11))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newPost(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void タグは1つ以上必要で_同じタグは1つにまとまる() {
        Post post = newPost(urls(1));
        post.replaceTags(List.of(furugi, furugi, outer));
        assertThat(post.getTags()).hasSize(2);

        assertThatThrownBy(() -> post.replaceTags(Collections.emptyList())).isInstanceOf(IllegalArgumentException.class);
    }

    private Post newPost(List<String> imageUrls) {
        return new Post(author, "秋の羽織りもの", fashionCategory,
                "アウター：古着屋で購入（サイズL）\nパンツ：ブラックスラックス",
                "丈が長めのコートなので、下は細めにまとめています。", null,
                imageUrls, List.of(furugi, outer));
    }

    private static List<String> urls(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "https://example.com/" + i + ".jpg").toList();
    }

    private Post persistAndReload(Post post) {
        entityManager.persist(post);
        return reload(post);
    }

    /** DB に書き込んでから、キャッシュを捨てて読み直す（DB に保存された内容で確認するため） */
    private Post reload(Post post) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Post.class, post.getId());
    }
}
