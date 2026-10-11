package com.teamc.isow.backend.devseed;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.auth.RegisterRequest;
import com.teamc.isow.backend.devseed.DevSeedData.FollowDef;
import com.teamc.isow.backend.devseed.DevSeedData.PostDef;
import com.teamc.isow.backend.devseed.DevSeedData.Reaction;
import com.teamc.isow.backend.devseed.DevSeedData.SearchDef;
import com.teamc.isow.backend.devseed.DevSeedData.UserDef;
import com.teamc.isow.backend.dm.ConsultationRequest;
import com.teamc.isow.backend.dm.ConsultationService;
import com.teamc.isow.backend.dm.ConversationService;
import com.teamc.isow.backend.dm.MessageSendRequest;
import com.teamc.isow.backend.dm.MessageService;
import com.teamc.isow.backend.follow.FollowService;
import com.teamc.isow.backend.master.MasterService;
import com.teamc.isow.backend.master.MastersResponse;
import com.teamc.isow.backend.master.MastersResponse.MasterOption;
import com.teamc.isow.backend.notification.NotificationQueryService;
import com.teamc.isow.backend.notification.NotificationType;
import com.teamc.isow.backend.post.PostCreateRequest;
import com.teamc.isow.backend.post.PostService;
import com.teamc.isow.backend.profile.ProfileEditService;
import com.teamc.isow.backend.profile.ProfileUpdateRequest;
import com.teamc.isow.backend.reaction.ReactionService;
import com.teamc.isow.backend.search.SearchService;
import com.teamc.isow.backend.seed.SeedHistory;
import com.teamc.isow.backend.seed.SeedHistoryRepository;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 開発用のテストデータを、起動時に投入する（README「開発用のテストデータ」）。
 *
 * <p>安全のための決まり：
 * <ul>
 *   <li>.env の DEV_SEED_ENABLED=true のときだけ動く。true 以外（未設定・空・書き間違い）では、この部品そのものを読み込まない</li>
 *   <li>追加だけを行い、既存のデータを消す・書き換える処理は持たない。やり直すときは docker compose down -v で DB ごと作り直す</li>
 *   <li>投入済みなら何もしない（master_seed_history に SEED_KEY を残す。選択肢の初期データと同じ考え方）</li>
 *   <li>投入済みの印がないのに投入用のユーザーがいる場合（前回が途中で失敗した、同じユーザー名で登録済みなど）は、何もしない</li>
 *   <li>管理者は作らない（管理者は今までどおり ADMIN_EMAIL で指定する）</li>
 * </ul>
 *
 * <p>できる限り画面の操作と同じサービスを通す（通知が本番と同じく作られ、タグ名なども本番と同じく整えられるようにするため）。
 * サービスはそれぞれ自分でトランザクションを扱い、通知はコミットの後に作られるため、全体を1つのトランザクションでは包まない。
 * 画面から送られる値の入力チェック（文字数など）は受け付け口で行われるため、ここでも同じチェックを通してから渡す。
 *
 * <p>起動時の処理の順番：選択肢の初期データ（MasterDataSeeder）→ これ → ADMIN_EMAIL による管理者の指定（AdminAccountInitializer）。
 * 投入ユーザーのメールアドレスを ADMIN_EMAIL に書けば、最初の起動から管理者になる
 */
@Component
@ConditionalOnProperty(prefix = "app.dev-seed", name = "enabled", havingValue = "true")
@Order(DevDataSeeder.ORDER)
public class DevDataSeeder implements ApplicationRunner {

    /** 起動時の処理の順番（MasterDataSeeder の後、AdminAccountInitializer の前） */
    public static final int ORDER = 2;

    /** 投入済みの印（master_seed_history の seed_key）。中身を大きく変えたら番号を上げる */
    public static final String SEED_KEY = "dev_seed:v1";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final SeedHistoryRepository seedHistoryRepository;
    private final UserRepository userRepository;
    private final Validator validator;
    private final MasterService masterService;
    private final AuthService authService;
    private final ProfileEditService profileEditService;
    private final PostService postService;
    private final FollowService followService;
    private final ReactionService reactionService;
    private final ConsultationService consultationService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final NotificationQueryService notificationQueryService;
    private final SearchService searchService;

    public DevDataSeeder(
            SeedHistoryRepository seedHistoryRepository,
            UserRepository userRepository,
            Validator validator,
            MasterService masterService,
            AuthService authService,
            ProfileEditService profileEditService,
            PostService postService,
            FollowService followService,
            ReactionService reactionService,
            ConsultationService consultationService,
            ConversationService conversationService,
            MessageService messageService,
            NotificationQueryService notificationQueryService,
            SearchService searchService) {
        this.seedHistoryRepository = seedHistoryRepository;
        this.userRepository = userRepository;
        this.validator = validator;
        this.masterService = masterService;
        this.authService = authService;
        this.profileEditService = profileEditService;
        this.postService = postService;
        this.followService = followService;
        this.reactionService = reactionService;
        this.consultationService = consultationService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.notificationQueryService = notificationQueryService;
        this.searchService = searchService;
    }

    /** 失敗しても起動は止めない（ADMIN_EMAIL の処理と同じ）。理由とやり直し方をログに出す */
    @Override
    public void run(ApplicationArguments args) {
        log.warn("開発用のテストデータの投入が有効になっています（DEV_SEED_ENABLED=true）。公開するサーバーでは絶対に有効にしないでください");
        if (seedHistoryRepository.existsById(SEED_KEY)) {
            log.info("開発用のテストデータは投入済みのため、何もしません");
            return;
        }
        List<String> existing = existingSeedUsers();
        if (!existing.isEmpty()) {
            log.error("開発用のテストデータの投入済みの印がないのに、投入用のユーザーがすでにいるため、何もしません"
                    + "（前回の投入が途中で失敗した可能性があります）: {}。"
                    + "やり直す場合は docker compose down -v で DB を作り直してください", existing);
            return;
        }
        try {
            seed();
            seedHistoryRepository.save(new SeedHistory(SEED_KEY));
            log.info("開発用のテストデータを投入しました（ユーザー{}人・投稿{}件）。パスワードは README を参照してください",
                    DevSeedData.USERS.size(), DevSeedData.POSTS.size());
        } catch (RuntimeException e) {
            log.error("開発用のテストデータの投入が途中で失敗しました。投入できた分は残っています。"
                    + "やり直す場合は docker compose down -v で DB を作り直してください", e);
        }
    }

    /** 投入用のユーザー（メールアドレス・ユーザー名）のうち、すでにいるもの */
    private List<String> existingSeedUsers() {
        List<String> existing = new ArrayList<>();
        for (UserDef user : DevSeedData.USERS) {
            if (userRepository.existsByEmail(user.email()) || userRepository.existsByUsername(user.username())) {
                existing.add(user.username());
            }
        }
        return existing;
    }

    private void seed() {
        Map<String, String> subjects = registerUsers();
        Map<String, Long> userIds = subjects.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Long.valueOf(e.getValue())));
        Map<String, Long> postIds = createPosts(subjects);

        for (FollowDef follow : DevSeedData.FOLLOWS) {
            followService.follow(subjects.get(follow.follower()), userIds.get(follow.followee()));
        }
        for (Reaction like : DevSeedData.LIKES) {
            reactionService.like(subjects.get(like.username()), postIds.get(like.postKey()));
        }
        for (Reaction favorite : DevSeedData.FAVORITES) {
            reactionService.favorite(subjects.get(favorite.username()), postIds.get(favorite.postKey()));
        }
        createConversations(subjects, userIds);
        // 通知は、既読と未読が混ざるようにする（aoi に届いたフォローの通知だけを、画面と同じ処理で既読にする）
        markFollowNotificationsRead(subjects.get("dev_aoi"));
        for (SearchDef search : DevSeedData.SEARCHES) {
            if (search.users()) {
                searchService.searchUsers(subjects.get(search.username()), search.keyword(), 0, 20);
            } else {
                searchService.searchPosts(subjects.get(search.username()), search.keyword(), null, null, 0, 20);
            }
        }
    }

    /** 新規会員登録 → プロフィールの保存 → プロフィール画像の変更（画面と同じ順） */
    private Map<String, String> registerUsers() {
        MastersResponse masters = masterService.getMasters();
        Map<String, String> subjects = new HashMap<>();
        for (int i = 0; i < DevSeedData.USERS.size(); i++) {
            UserDef user = DevSeedData.USERS.get(i);
            Long id = authService.register(valid(new RegisterRequest(
                    user.email(), user.phoneNumber(), DevSeedData.PASSWORD, user.username()))).id();
            String subject = String.valueOf(id);
            subjects.put(user.username(), subject);
            if (user.displayName() != null) {
                profileEditService.update(subject, valid(new ProfileUpdateRequest(
                        user.displayName(), user.gender(), user.heightCm(), user.ageGroup(),
                        idOf(masters.bodyTypes(), user.bodyType()), idOf(masters.personalColors(), user.personalColor()))));
            }
            if (user.profileImage()) {
                profileEditService.changeImage(subject, DevSeedImages.profileImage(user.username(), i));
            }
        }
        return subjects;
    }

    private Map<String, Long> createPosts(Map<String, String> subjects) {
        List<MasterOption> categories = masterService.getMasters().fashionCategories();
        Map<String, Long> postIds = new HashMap<>();
        for (int p = 0; p < DevSeedData.POSTS.size(); p++) {
            PostDef post = DevSeedData.POSTS.get(p);
            List<MultipartFile> photos = new ArrayList<>();
            for (int i = 0; i < post.photos().size(); i++) {
                photos.add(DevSeedImages.postPhoto(post.author() + " " + post.key(), i + 1, post.photos().size(),
                        post.photos().get(i), p + i));
            }
            Long id = postService.create(subjects.get(post.author()), valid(new PostCreateRequest(
                    photos, post.title(), idOf(categories, post.category()), post.tags(),
                    post.wornItems(), post.description(), post.referenceUrl()))).id();
            postIds.put(post.key(), id);
        }
        return postIds;
    }

    /**
     * 相談（DM）。申請中・進行中・終了・拒否がそろうようにする。
     * ren は、受けている進行中の相談が上限（3件）に達した状態にする（上限の画面を確かめるための意図した状態。README に記載）
     */
    private void createConversations(Map<String, String> subjects, Map<String, Long> userIds) {
        String aoi = subjects.get("dev_aoi");
        String ren = subjects.get("dev_ren");
        String mio = subjects.get("dev_mio");

        // 進行中：aoi → ren。画像付きを含む5通。最後の ren のメッセージは aoi が未読のまま
        Long aoiRen = request(aoi, userIds.get("dev_ren"), "はじめまして。春のジャケットの合わせ方を相談させてください。");
        conversationService.accept(ren, aoiRen);
        send(ren, aoiRen, "こんにちは！どんなジャケットか見せてもらえますか？");
        send(aoi, aoiRen, "これです。", DevSeedImages.messageImage("dev_aoi"));
        send(aoi, aoiRen, "パンツとスカート、どちらが合いそうでしょうか。");
        // ren がチャットを開いて、aoi のメッセージを読む（画面と同じ処理）
        messageService.list(ren, aoiRen, null, 50);
        send(ren, aoiRen, "センタープレスのパンツが合うと思います。靴はローファーがおすすめです。");

        // 進行中：kenta → ren、yui → ren。これで ren が受けている相談が上限の3件になる
        conversationService.accept(ren, request(subjects.get("dev_kenta"), userIds.get("dev_ren"), "古着の合わせ方を教えてください。"));
        conversationService.accept(ren, request(subjects.get("dev_yui"), userIds.get("dev_ren"), null));

        // 拒否：kenta → aoi
        conversationService.reject(aoi, request(subjects.get("dev_kenta"), userIds.get("dev_aoi"), "相談してもいいですか？"));

        // 申請中：yui → aoi（aoi が受けた側）、aoi → sora（aoi が送った側。一言メッセージなし）
        request(subjects.get("dev_yui"), userIds.get("dev_aoi"), "コーデの色合わせについて相談したいです。");
        request(aoi, userIds.get("dev_sora"), null);

        // 終了：mio → aoi。3通のやりとりの後、aoi が読んでから終了する
        Long mioAoi = request(mio, userIds.get("dev_aoi"), "低身長コーデの相談をさせてください。");
        conversationService.accept(aoi, mioAoi);
        send(aoi, mioAoi, "もちろんです。どんな場面で着る服ですか？");
        send(mio, mioAoi, "学校の帰りに出かけるときです。ありがとうございました、参考になりました！");
        messageService.list(aoi, mioAoi, null, 50);
        conversationService.end(aoi, mioAoi);
    }

    private Long request(String subject, Long recipientId, String message) {
        return consultationService.request(subject, valid(new ConsultationRequest(recipientId, message))).conversationId();
    }

    private void send(String subject, Long conversationId, String body) {
        send(subject, conversationId, body, null);
    }

    private void send(String subject, Long conversationId, String body, MultipartFile image) {
        messageService.send(subject, conversationId, valid(new MessageSendRequest(body, image)));
    }

    private void markFollowNotificationsRead(String subject) {
        notificationQueryService.list(subject, 0, 50).notifications().stream()
                .filter(notification -> notification.type() == NotificationType.FOLLOWED)
                .forEach(notification -> notificationQueryService.markRead(subject, notification.id()));
    }

    /** 選択肢の名前から ID を探す（画面と同じく GET /api/masters と同じ一覧から選ぶ）。null は未設定 */
    private static Long idOf(List<MasterOption> options, String name) {
        if (name == null) {
            return null;
        }
        return options.stream().filter(option -> option.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("選択肢が見つかりません（無効にされている可能性があります）: " + name))
                .id();
    }

    /** 画面から送られた値と同じ入力チェックを通す。通らなければ、そのデータは作らずに失敗させる */
    private <T> T valid(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("開発用のテストデータが入力チェックを通りません: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList());
        }
        return request;
    }
}
