package com.teamc.isow.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 管理者かどうかの判定（User.isAdmin()。docs/admin.md「権限の仕組み」の注意）の確認。DB は使わない。
 * role は NOT NULL のため DB 上は NULL になり得ないが、コード側でも NULL を管理者として扱わないことを確かめる。
 * 想定外の値を DB に入れた場合の通しの確認は SecurityConfigTest で行う。
 */
class UserRoleTest {

    @Test
    void 新しく作ったユーザーは一般の利用者() {
        assertThat(newUser().isAdmin()).isFalse();
    }

    @Test
    void roleがADMINなら管理者() throws Exception {
        assertThat(userWithRole("ADMIN").isAdmin()).isTrue();
    }

    @Test
    void roleがUSERなら管理者ではない() throws Exception {
        assertThat(userWithRole("USER").isAdmin()).isFalse();
    }

    @Test
    void roleがNULLなら管理者ではない() throws Exception {
        assertThat(userWithRole(null).isAdmin()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "Admin", "SUPER_ADMIN", " ADMIN", "ADMIN ", ""})
    void roleが想定外の値なら管理者ではない(String role) throws Exception {
        assertThat(userWithRole(role).isAdmin()).isFalse();
    }

    @Test
    void 管理者にすると管理者になる() {
        User user = newUser();

        user.promoteToAdmin();

        assertThat(user.isAdmin()).isTrue();
    }

    private static User newUser() {
        return new User("role-test@example.com", "09000000000", "hash", "role_test");
    }

    /** DB を使わずに role の値を入れる（NULL や想定外の値は、通常の操作では作れないため） */
    private static User userWithRole(String role) throws Exception {
        User user = newUser();
        Field field = User.class.getDeclaredField("role");
        field.setAccessible(true);
        field.set(user, role);
        return user;
    }
}
