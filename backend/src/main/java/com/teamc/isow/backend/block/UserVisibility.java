package com.teamc.isow.backend.block;

import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostNotFoundException;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 1人の相手（ユーザー・投稿）を対象にする API で、見えない相手・投稿を「存在しない」ものとして扱う
 * （docs/profile.md「ブロック」・docs/admin.md「6. 措置」）。
 * プロフィール・投稿の詳細・投稿の編集と削除・フォロー・いいね・お気に入り・相談はここを通す。
 *
 * <p>見えないもの（VisibilityConditions と同じ）：ブロックの間柄にある相手、利用停止中のユーザー、
 * 管理者が非公開にした投稿（投稿者本人には見える）。
 * どれも、存在しないユーザー・投稿と同じ 404（同じ例外）にする（ブロック・措置のことを伝えないため）。
 * 一覧・件数の SQL は VisibilityConditions の条件を使う
 */
@Component
public class UserVisibility {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public UserVisibility(BlockRepository blockRepository, UserRepository userRepository, Clock clock) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * viewerId から userId が見えないか（どちらかがもう一方をブロックしている、または userId が利用停止中）。
     * 自分自身は常に見える
     */
    public boolean isHidden(Long viewerId, Long userId) {
        if (viewerId.equals(userId)) {
            return false;
        }
        if (blockRepository.existsBetween(viewerId, userId)) {
            return true;
        }
        return userRepository.findById(userId).map(user -> user.isSuspendedAt(now())).orElse(false);
    }

    /** userIds のうち、viewerId から見えない人の ID（ブロック・利用停止。人数に関係なく SQL は2本） */
    public Set<Long> hiddenAmong(Long viewerId, Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> hidden = new HashSet<>(blockRepository.findHiddenAmong(viewerId, userIds));
        hidden.addAll(userRepository.findSuspendedIdsAmong(userIds, now()));
        hidden.remove(viewerId);
        return hidden;
    }

    /** userId のユーザーが見えなければ、存在しないユーザーと同じ UserNotFoundException（404） */
    public void requireVisibleUser(Long viewerId, Long userId) {
        if (isHidden(viewerId, userId)) {
            throw new UserNotFoundException(userId);
        }
    }

    /**
     * 投稿が見えなければ、存在しない投稿と同じ PostNotFoundException（404）。
     * 投稿者本人には、非公開の投稿も見える。ほかの人には、非公開の投稿・見えない投稿者の投稿は見えない
     */
    public void requireVisiblePost(Long viewerId, Post post) {
        User author = post.getAuthor();
        if (author.getId().equals(viewerId)) {
            return;
        }
        if (post.isHidden() || author.isSuspendedAt(now()) || blockRepository.existsBetween(viewerId, author.getId())) {
            throw new PostNotFoundException(post.getId());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
