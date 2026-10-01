package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** request は正規化・入力チェック済みであること */
    public RegisterResponse register(RegisterRequest request) {
        throwIfDuplicated(request);

        User user = new User(
                request.email(),
                request.phoneNumber(),
                passwordEncoder.encode(request.password()),
                request.username());
        try {
            return RegisterResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // チェックの直後に同じ値で別の登録が入った場合。一意制約違反を重複エラーとして返す
            throwIfDuplicated(request);
            throw e;
        }
    }

    private void throwIfDuplicated(RegisterRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (userRepository.existsByEmail(request.email())) {
            errors.put("email", "このメールアドレスは既に登録されています");
        }
        if (userRepository.existsByUsername(request.username())) {
            errors.put("username", "このユーザー名は使用されています");
        }
        if (!errors.isEmpty()) {
            throw new DuplicateRegistrationException(errors);
        }
    }
}
