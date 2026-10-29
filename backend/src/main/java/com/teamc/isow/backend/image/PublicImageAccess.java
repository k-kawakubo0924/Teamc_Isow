package com.teamc.isow.backend.image;

import com.teamc.isow.backend.block.VisibilityConditions;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存した画像（/uploads/**）を配ってよいか（docs/admin.md「6. 措置」の「非公開にした投稿の写真」）。
 * 規則は「アプリで見えないものは、URL でも取れない」。{@code <img>} のタグは認証のヘッダーを付けられず、誰が見ているか分からないため、
 * 誰にも見えないものを止める。
 *
 * <ul>
 *   <li>配るのは、公開されてよい画像として、次のどれかに参照されているファイルだけ（参照されていないファイルは、保存先にあっても配らない）
 *     <ul>
 *       <li>投稿の写真（post_images）。ただし、非公開にした投稿・利用停止中の投稿者の投稿は除く（投稿者本人も含め、誰にも配らない）</li>
 *       <li>プロフィール画像（users.profile_image_key）。利用停止中でも配る（DM の相手のアプリにアイコンが出ているため）</li>
 *     </ul>
 *   </li>
 *   <li>DM の画像（messages.image_key）は、ここに登録しない。特定の人（会話の当事者2人）にだけ見せる画像のため、
 *       認証付きの API（GET /api/conversations/{id}/messages/{messageId}/image）から配る。保存先は公開の画像と同じフォルダのままで、
 *       /uploads/** から取れないのは、ここに登録していないことだけによる（docs/dm.md「画像」）</li>
 *   <li>画像を保存する処理は、投稿（PostService）・プロフィール画像（ProfileEditService）・DM（MessageService）の3つだけ。
 *       新しく画像を保存する場所を作ったら、公開されてよい画像ならここに登録し、特定の人にだけ見せる画像なら登録せずに認証付きの API を通す
 *       （どちらにもしないと、その画像は配信されず 404 になる。CLAUDE.md）</li>
 *   <li>参照は、DB に保存したキー（ファイル名）と一致するかで探す</li>
 * </ul>
 */
@Component
public class PublicImageAccess {

    private static final String POST_IMAGE = "SELECT COUNT(i) FROM PostImage i JOIN i.post p"
            + " WHERE i.imageKey = :key AND" + VisibilityConditions.POST_PUBLIC;
    private static final String PROFILE_IMAGE = "SELECT COUNT(u) FROM User u WHERE u.profileImageKey = :key";

    private final EntityManager entityManager;
    private final Clock clock;

    public PublicImageAccess(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** 保存先のキー（保存先のフォルダ直下のファイル名）を配ってよいか。キーの形でない名前は配らない */
    @Transactional(readOnly = true)
    public boolean isServable(String key) {
        if (!ImageKeys.isKey(key)) {
            return false;
        }
        return count(entityManager.createQuery(POST_IMAGE, Long.class)
                        .setParameter("key", key)
                        .setParameter("now", LocalDateTime.now(clock))
                        .getSingleResult()) > 0
                || count(entityManager.createQuery(PROFILE_IMAGE, Long.class)
                        .setParameter("key", key).getSingleResult()) > 0;
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }
}
