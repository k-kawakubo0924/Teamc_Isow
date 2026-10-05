package com.teamc.isow.backend.follow;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.data.domain.Sort;

/** フォロー中一覧・フォロワー一覧の並び順（docs/profile.md） */
public enum FollowSort {

    /** フォローの新しい順 */
    NEWEST("newest", Sort.Direction.DESC),
    /** フォローの古い順 */
    OLDEST("oldest", Sort.Direction.ASC);

    /** API の sort パラメータで使う値 */
    private final String param;

    private final Sort.Direction direction;

    FollowSort(String param, Sort.Direction direction) {
        this.param = param;
        this.direction = direction;
    }

    public String getParam() {
        return param;
    }

    /** フォローした日時で並べる。同じ日時の場合も順番が毎回変わらないよう、ID でも並べる */
    Sort toSort() {
        return Sort.by(direction, "followedAt").and(Sort.by(direction, "id"));
    }

    public static Optional<FollowSort> fromParam(String value) {
        return Arrays.stream(values()).filter(sort -> sort.param.equals(value)).findFirst();
    }
}
