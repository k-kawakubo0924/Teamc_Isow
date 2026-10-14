package com.teamc.isow.backend.block;

import com.teamc.isow.backend.post.PostNotFoundException;
import com.teamc.isow.backend.user.UserNotFoundException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 1人の相手（ユーザー・投稿）を対象にする API で、ブロックの間柄にある相手を「存在しない」ものとして扱う
 * （docs/profile.md「ブロック」）。プロフィール・投稿の詳細・フォロー・いいね・お気に入り・相談はここを通す。
 *
 * <p>ブロックされた側にブロックを伝えないため、存在しないユーザー・投稿と同じ 404（同じ例外）にする。
 * 一覧・件数の SQL は BlockConditions の条件を使う
 */
@Component
public class UserVisibility {

    private final BlockRepository blockRepository;

    public UserVisibility(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    /** viewerId から userId が見えないか（どちらかがもう一方をブロックしている）。自分自身は常に見える */
    public boolean isHidden(Long viewerId, Long userId) {
        return !viewerId.equals(userId) && blockRepository.existsBetween(viewerId, userId);
    }

    /** userIds のうち、viewerId から見えない人の ID（まとめて調べる。人数に関係なく SQL は1本） */
    public Set<Long> hiddenAmong(Long viewerId, Collection<Long> userIds) {
        return userIds.isEmpty() ? Set.of() : new HashSet<>(blockRepository.findHiddenAmong(viewerId, userIds));
    }

    /** userId のユーザーが見えなければ、存在しないユーザーと同じ UserNotFoundException（404） */
    public void requireVisibleUser(Long viewerId, Long userId) {
        if (isHidden(viewerId, userId)) {
            throw new UserNotFoundException(userId);
        }
    }

    /** 投稿者が見えなければ、存在しない投稿と同じ PostNotFoundException（404） */
    public void requireVisiblePost(Long viewerId, Long postId, Long authorId) {
        if (isHidden(viewerId, authorId)) {
            throw new PostNotFoundException(postId);
        }
    }
}
