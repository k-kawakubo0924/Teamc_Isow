package com.teamc.isow.backend.image;

import com.teamc.isow.backend.block.VisibilityConditions;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存した画像（/uploads/**）を配ってよいか（docs/admin.md「6. 措置」の「非公開にした投稿の写真」）。
 * 規則は「アプリで見えないものは、URL でも取れない」。{@code <img>} のタグは認証のヘッダーを付けられず、誰が見ているか分からないため、
 * 誰にも見えないものを止める。
 *
 * <ul>
 *   <li>配るのは、次のどれかとして参照されているファイルだけ（参照されていないファイルは、保存先にあっても配らない）
 *     <ul>
 *       <li>投稿の写真（post_images）。ただし、非公開にした投稿・利用停止中の投稿者の投稿は除く（投稿者本人も含め、誰にも配らない）</li>
 *       <li>プロフィール画像（users.profile_image_url）。利用停止中でも配る（DM の相手のアプリには出ているため）</li>
 *       <li>DM の画像（messages.image_url）。送った人が利用停止中でも配る（相手は過去のメッセージを読めるため）</li>
 *     </ul>
 *   </li>
 *   <li>画像を保存する処理はこの3つ（PostService・ProfileEditService・MessageService）だけ。
 *       新しく画像を保存する場所を作ったら、ここにも足すこと（足さないと、その画像は 404 になる）</li>
 *   <li>参照は、URL の最後のファイル名で探す。DB の URL の先頭（app.image.local.public-base-url。既定はポート番号を含む）を
 *       途中で変えても、見つけられるようにするため</li>
 * </ul>
 */
@Component
public class PublicImageAccess {

    /**
     * 保存するときに付ける名前の形（ImageUploadService.store。乱数 + 拡張子）。これ以外の名前は配らない。
     * この形には LIKE の特別な文字（% と _）が含まれないため、そのまま LIKE の条件に使える
     */
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.(jpg|png)");

    private static final String POST_IMAGE = "SELECT COUNT(i) FROM PostImage i JOIN i.post p"
            + " WHERE i.imageUrl LIKE :suffix AND" + VisibilityConditions.POST_PUBLIC;
    private static final String PROFILE_IMAGE = "SELECT COUNT(u) FROM User u WHERE u.profileImageUrl LIKE :suffix";
    private static final String MESSAGE_IMAGE = "SELECT COUNT(m) FROM Message m WHERE m.imageUrl LIKE :suffix";

    private final EntityManager entityManager;
    private final Clock clock;

    public PublicImageAccess(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** 保存先のフォルダ直下の fileName を配ってよいか */
    @Transactional(readOnly = true)
    public boolean isServable(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return false;
        }
        String suffix = "%/" + fileName;
        return count(entityManager.createQuery(POST_IMAGE, Long.class)
                        .setParameter("suffix", suffix)
                        .setParameter("now", LocalDateTime.now(clock))
                        .getSingleResult()) > 0
                || count(entityManager.createQuery(PROFILE_IMAGE, Long.class)
                        .setParameter("suffix", suffix).getSingleResult()) > 0
                || count(entityManager.createQuery(MESSAGE_IMAGE, Long.class)
                        .setParameter("suffix", suffix).getSingleResult()) > 0;
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }
}
