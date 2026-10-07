package com.teamc.isow.backend.common;

import com.teamc.isow.backend.auth.DuplicateRegistrationException;
import com.teamc.isow.backend.auth.InvalidCredentialsException;
import com.teamc.isow.backend.auth.UnknownTokenUserException;
import com.teamc.isow.backend.dm.ConsultationUnavailableException;
import com.teamc.isow.backend.dm.ConsultationUnavailableReason;
import com.teamc.isow.backend.dm.ConversationNotFoundException;
import com.teamc.isow.backend.dm.ConversationOperationError;
import com.teamc.isow.backend.dm.ConversationOperationException;
import com.teamc.isow.backend.dm.DmErrorResponse;
import com.teamc.isow.backend.follow.SelfFollowException;
import com.teamc.isow.backend.image.InvalidImageException;
import com.teamc.isow.backend.notification.NotificationNotFoundException;
import com.teamc.isow.backend.post.PostNotFoundException;
import com.teamc.isow.backend.user.UserNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** エラー応答の形式を ApiErrorResponse にそろえる */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_INPUT_MESSAGE = "入力内容に誤りがあります。赤い欄を修正してください。";

    /** 未入力を表すチェック。1項目に複数のエラーがある場合は、これを優先して返す */
    private static final Set<String> REQUIRED_CODES = Set.of("NotBlank", "NotEmpty", "NotNull");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            // 数値の欄に文字が入っているなど、型が合わない場合の既定の文言は技術的な英語のため置き換える
            String message = error.isBindingFailure() ? "入力内容の形式が正しくありません" : error.getDefaultMessage();
            // 1項目に複数のエラーがある場合は1つだけ返す。未入力のエラーを優先する
            if (REQUIRED_CODES.contains(error.getCode()) || !errors.containsKey(error.getField())) {
                errors.put(error.getField(), message);
            }
        }
        return new ApiErrorResponse(INVALID_INPUT_MESSAGE, errors);
    }

    @ExceptionHandler(InputValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInputValidation(InputValidationException e) {
        return new ApiErrorResponse(INVALID_INPUT_MESSAGE, e.getErrors());
    }

    @ExceptionHandler(PostNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handlePostNotFound(PostNotFoundException e) {
        return new ApiErrorResponse("投稿が見つかりません。", Map.of());
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleUserNotFound(UserNotFoundException e) {
        return new ApiErrorResponse("ユーザーが見つかりません。", Map.of());
    }

    /** 通知が存在しない、または自分の通知でない（他人の通知の有無が分からないよう、どちらも同じ応答にする） */
    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotificationNotFound(NotificationNotFoundException e) {
        return new ApiErrorResponse("通知が見つかりません。", Map.of());
    }

    @ExceptionHandler(SelfFollowException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleSelfFollow(SelfFollowException e) {
        return new ApiErrorResponse("自分自身はフォローできません。", Map.of());
    }

    /** 相談を申し込めない。HTTP ステータスと文言は理由ごとに決まる。画面が出し分けられるよう reason も返す */
    @ExceptionHandler(ConsultationUnavailableException.class)
    public ResponseEntity<DmErrorResponse> handleConsultationUnavailable(ConsultationUnavailableException e) {
        ConsultationUnavailableReason reason = e.getReason();
        return ResponseEntity.status(reason.getHttpStatus())
                .body(new DmErrorResponse(reason.getMessage(), Map.of(), reason.name()));
    }

    /** 会話が存在しない、または当事者でない（会話の有無が分からないよう、どちらも同じ応答にする） */
    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleConversationNotFound(ConversationNotFoundException e) {
        return new ApiErrorResponse("会話が見つかりません。", Map.of());
    }

    /** 会話の承認・拒否・終了ができない。HTTP ステータスは理由ごとに決まり、文言は例外の message */
    @ExceptionHandler(ConversationOperationException.class)
    public ResponseEntity<DmErrorResponse> handleConversationOperation(ConversationOperationException e) {
        ConversationOperationError error = e.getError();
        return ResponseEntity.status(error.getHttpStatus())
                .body(new DmErrorResponse(e.getMessage(), Map.of(), error.name()));
    }

    /** multipart の上限（spring.servlet.multipart.*）を超えた。画面側でも送信前にサイズを確認すること */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public ApiErrorResponse handleMaxUploadSize(MaxUploadSizeExceededException e) {
        String message = "写真のファイルサイズが大きすぎます。1枚あたり10MB以下にしてください。";
        return new ApiErrorResponse(message, Map.of("images", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleUnreadable(HttpMessageNotReadableException e) {
        return new ApiErrorResponse(INVALID_INPUT_MESSAGE, Map.of());
    }

    @ExceptionHandler(DuplicateRegistrationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDuplicate(DuplicateRegistrationException e) {
        return new ApiErrorResponse("すでに使われている項目があります。赤い欄を修正してください。", e.getErrors());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleInvalidCredentials(InvalidCredentialsException e) {
        return new ApiErrorResponse("メールアドレスまたはパスワードが正しくありません。入力内容をご確認ください。", Map.of());
    }

    /** 画像の形式・サイズなどが条件を満たさない。理由は例外の message（画面にそのまま表示できる文言） */
    @ExceptionHandler(InvalidImageException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidImage(InvalidImageException e) {
        return new ApiErrorResponse(e.getMessage(), Map.of());
    }

    /** トークンなし・不正なトークンと同じく、本文なしの 401 を返す */
    @ExceptionHandler(UnknownTokenUserException.class)
    public ResponseEntity<Void> handleUnknownTokenUser(UnknownTokenUserException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
