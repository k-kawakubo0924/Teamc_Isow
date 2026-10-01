package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /** 存在しないメールアドレスでも照合処理を走らせるためのダミーのハッシュ（応答時間の差で登録有無を推測させない） */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** request は正規化・入力チェック済みであること */
    public UserResponse register(RegisterRequest request) {
        throwIfDuplicated(request);

        User user = new User(
                request.email(),
                request.phoneNumber(),
                passwordEncoder.encode(request.password()),
                request.username());
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // チェックの直後に同じ値で別の登録が入った場合。一意制約違反を重複エラーとして返す
            throwIfDuplicated(request);
            throw e;
        }
    }

    /**
     * request は正規化・入力チェック済みであること。
     * メールアドレスが存在しない場合もパスワードが違う場合も、同じ例外を投げる。
     */
    public LoginResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(request.email());
        String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), passwordHash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return jwtTokenService.issue(user.get());
    }

    /**
     * トークンの sub（ユーザーID）からログイン中のユーザーを返す。
     * トークンは正しいがユーザーが存在しない場合（退会後のトークンなど）は未認証として扱う。
     */
    public UserResponse currentUser(String subject) {
        return parseUserId(subject)
                .flatMap(userRepository::findById)
                .map(UserResponse::from)
                .orElseThrow(UnknownTokenUserException::new);
    }

    private static Optional<Long> parseUserId(String subject) {
        try {
            return Optional.of(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
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
