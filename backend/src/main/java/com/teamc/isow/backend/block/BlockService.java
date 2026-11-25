package com.teamc.isow.backend.block;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.dm.ConversationRepository;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.image.ImageUrls;
import com.teamc.isow.backend.notification.NotificationRepository;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.reaction.PostLikeRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ブロック・解除・ブロック中の一覧（docs/profile.md「ブロック」）。
 *
 * <p>ブロックした時点で、1つのトランザクションの中で2人の間のつながりを切る（読むときの条件を減らし、書き忘れを防ぐため）。
 * <ul>
 *   <li>フォロー：両方向とも削除する（解除と違って行を残さない。ブロックを解除しても戻らない）</li>
 *   <li>相談：申請中・進行中の会話を終了にする（終了した人はブロックした人。過去のメッセージは両方から読める）</li>
 *   <li>いいね・お気に入り・通知：2人の間のものを両方向で削除する（ブロックを解除しても戻らない）</li>
 * </ul>
 * ブロックされた人には通知しない
 */
@Service
public class BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final ConversationRepository conversationRepository;
    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;
    private final ImageUrls imageUrls;

    public BlockService(BlockRepository blockRepository, UserRepository userRepository,
            FollowRepository followRepository, ConversationRepository conversationRepository,
            PostLikeRepository likeRepository, PostFavoriteRepository favoriteRepository,
            NotificationRepository notificationRepository, AuthService authService,
            PlatformTransactionManager transactionManager, ImageUrls imageUrls) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.conversationRepository = conversationRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.notificationRepository = notificationRepository;
        this.authService = authService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.imageUrls = imageUrls;
    }

    /**
     * ブロックする。すでにブロックしていても成功として扱う（つながりを切る処理もしない）。
     * 自分自身は SelfBlockException（400）、存在しないユーザーは UserNotFoundException（404）
     */
    public void block(String subject, Long targetId) {
        Long blockerId = authService.requireCurrentUser(subject).getId();
        if (blockerId.equals(targetId)) {
            throw new SelfBlockException();
        }
        try {
            transactionTemplate.executeWithoutResult(status -> blockIfAbsent(blockerId, targetId));
        } catch (DataIntegrityViolationException e) {
            // 連打で同じリクエストがほぼ同時に届くと、片方が一意制約違反になる。先に届いた方でブロック済みなら成功として扱う
            if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
                throw e;
            }
        }
    }

    /** トランザクションの中で呼ぶ */
    private void blockIfAbsent(Long blockerId, Long targetId) {
        User target = userRepository.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
            return;
        }
        User blocker = userRepository.getReferenceById(blockerId);
        blockRepository.saveAndFlush(new Block(blocker, target));

        int follows = followRepository.deleteBetween(blockerId, targetId);
        // 承認・終了と同時に起きても1つずつ処理するよう、会話の行をロックしてから終了する
        List<Conversation> ongoing = conversationRepository.findOngoingWith(blockerId, List.of(targetId));
        for (Conversation found : ongoing) {
            conversationRepository.findByIdForUpdate(found.getId())
                    .filter(conversation -> conversation.getStatus().isOngoing())
                    .ifPresent(conversation -> conversation.endByBlock(blocker));
        }
        int likes = likeRepository.deleteBetween(blockerId, targetId);
        int favorites = favoriteRepository.deleteBetween(blockerId, targetId);
        int notifications = notificationRepository.deleteBetween(blockerId, targetId);
        log.info("ブロックしました: blockerId={}, blockedId={}, follows={}, conversations={}, likes={}, favorites={}, "
                + "notifications={}", blockerId, targetId, follows, ongoing.size(), likes, favorites, notifications);
    }

    /**
     * ブロックを解除する。ブロックしていなくても成功として扱う。存在しないユーザーは UserNotFoundException（404）。
     * 解除できるのは自分がしたブロックだけ（相手からのブロックは残る）。切ったつながりは戻さない
     */
    @Transactional
    public void unblock(String subject, Long targetId) {
        Long blockerId = authService.requireCurrentUser(subject).getId();
        if (!userRepository.existsById(targetId)) {
            throw new UserNotFoundException(targetId);
        }
        blockRepository.deleteByPair(blockerId, targetId);
    }

    /** 自分がブロックしている人の一覧（ブロックした新しい順）。page は 0 から。範囲外の page・size は丸める */
    @Transactional(readOnly = true)
    public BlockListResponse list(String subject, int page, int size) {
        Long blockerId = authService.requireCurrentUser(subject).getId();
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<Block> blocks = blockRepository.findPageByBlockerId(blockerId, pageable);
        return new BlockListResponse(
                blocks.getContent().stream().map(block -> BlockListResponse.Item.from(block, imageUrls)).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), blocks.hasNext());
    }
}
