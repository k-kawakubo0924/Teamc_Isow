package com.teamc.isow.backend.mute;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.block.UserVisibility;
import com.teamc.isow.backend.image.ImageUrls;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）。
 *
 * <p><b>ミュートは何も削除しない</b>（フォロー・いいね・お気に入り・通知・相談はそのまま）。ここが BlockService との違いの中心で、
 * つながりを切る処理をここに足さないこと（MuteApiTest で、残るものを1つずつ確かめている）
 */
@Service
public class MuteService {

    private final UserMuteRepository muteRepository;
    private final UserRepository userRepository;
    private final UserVisibility userVisibility;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;
    private final ImageUrls imageUrls;
    private final Clock clock;

    public MuteService(UserMuteRepository muteRepository, UserRepository userRepository, UserVisibility userVisibility,
            AuthService authService, PlatformTransactionManager transactionManager, ImageUrls imageUrls, Clock clock) {
        this.muteRepository = muteRepository;
        this.userRepository = userRepository;
        this.userVisibility = userVisibility;
        this.authService = authService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.imageUrls = imageUrls;
        this.clock = clock;
    }

    /**
     * ミュートする。すでにミュートしていても成功として扱う。自分自身は SelfMuteException（400）。
     * 存在しないユーザー・ブロックの間柄にある相手・利用停止中の相手は、存在しないユーザーと同じ UserNotFoundException（404）
     */
    public void mute(String subject, Long targetId) {
        Long muterId = authService.requireCurrentUser(subject).getId();
        if (muterId.equals(targetId)) {
            throw new SelfMuteException();
        }
        try {
            transactionTemplate.executeWithoutResult(status -> muteIfAbsent(muterId, targetId));
        } catch (DataIntegrityViolationException e) {
            // 連打で同じリクエストがほぼ同時に届くと、片方が一意制約違反になる。先に届いた方でミュート済みなら成功として扱う
            if (!muteRepository.existsByMuterIdAndMutedId(muterId, targetId)) {
                throw e;
            }
        }
    }

    /** トランザクションの中で呼ぶ */
    private void muteIfAbsent(Long muterId, Long targetId) {
        User target = userRepository.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        userVisibility.requireVisibleUser(muterId, targetId);
        if (muteRepository.existsByMuterIdAndMutedId(muterId, targetId)) {
            return;
        }
        muteRepository.saveAndFlush(new UserMute(userRepository.getReferenceById(muterId), target, LocalDateTime.now(clock)));
    }

    /**
     * ミュートを解除する。ミュートしていなくても成功として扱う。存在しないユーザーは UserNotFoundException（404）。
     * 解除できるのは自分がしたミュートだけ
     */
    @Transactional
    public void unmute(String subject, Long targetId) {
        Long muterId = authService.requireCurrentUser(subject).getId();
        if (!userRepository.existsById(targetId)) {
            throw new UserNotFoundException(targetId);
        }
        muteRepository.deleteByPair(muterId, targetId);
    }

    /** 自分がミュートしている人の一覧（ミュートした新しい順）。page は 0 から。範囲外の page・size は丸める */
    @Transactional(readOnly = true)
    public MuteListResponse list(String subject, int page, int size) {
        Long muterId = authService.requireCurrentUser(subject).getId();
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<UserMute> mutes = muteRepository.findPageByMuterId(muterId, LocalDateTime.now(clock), pageable);
        return new MuteListResponse(
                mutes.getContent().stream().map(mute -> MuteListResponse.Item.from(mute, imageUrls)).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), mutes.hasNext());
    }
}
