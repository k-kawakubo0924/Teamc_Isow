package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.master.MastersResponse.MasterOption;
import com.teamc.isow.backend.user.User;

/**
 * プロフィール（docs/profile.md。design/myprofile.png・design/otherprofile.png）。
 * メールアドレス・電話番号は返さない。未設定の項目は null。
 *
 * @param gender 性別の code（GET /api/masters の genders と同じ。表示する文言はそちらの label を使う）
 * @param ageGroup 年代の code（GET /api/masters の ageGroups と同じ）
 * @param bodyType 骨格タイプ（GET /api/masters の bodyTypes と同じ形）
 * @param personalColor パーソナルカラー（GET /api/masters の personalColors と同じ形）
 * @param postCount 投稿の件数（プロフィールの「投稿 〇件」）
 * @param followingCount フォロー中の件数（有効なフォローのみ）
 * @param followerCount フォロワーの件数（有効なフォローのみ）
 * @param me ログイン中のユーザー自身のプロフィールか（「編集」と「フォロー」のどちらのボタンを出すかに使う）
 * @param followingByMe ログイン中のユーザーがこのユーザーをフォローしているか。自分のプロフィールでは null
 */
public record ProfileResponse(
        Long id,
        String username,
        String displayName,
        String profileImageUrl,
        Gender gender,
        Integer heightCm,
        AgeGroup ageGroup,
        MasterOption bodyType,
        MasterOption personalColor,
        long postCount,
        long followingCount,
        long followerCount,
        boolean me,
        Boolean followingByMe) {

    /**
     * bodyType・personalColor は読み込み済みであること（UserRepository.findWithProfileById）。
     * followingByMe は自分のプロフィールなら null を渡す
     */
    static ProfileResponse of(
            User user, long postCount, long followingCount, long followerCount, Boolean followingByMe) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                user.getGender(),
                user.getHeightCm(),
                user.getAgeGroup(),
                user.getBodyType() == null ? null : MasterOption.from(user.getBodyType()),
                user.getPersonalColor() == null ? null : MasterOption.from(user.getPersonalColor()),
                postCount,
                followingCount,
                followerCount,
                followingByMe == null,
                followingByMe);
    }
}
