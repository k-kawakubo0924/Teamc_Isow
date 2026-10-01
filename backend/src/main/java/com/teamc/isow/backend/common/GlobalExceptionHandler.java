package com.teamc.isow.backend.common;

import com.teamc.isow.backend.auth.DuplicateRegistrationException;
import com.teamc.isow.backend.auth.InvalidCredentialsException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** エラー応答の形式を ApiErrorResponse にそろえる */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_INPUT_MESSAGE = "入力内容に誤りがあります。赤い欄を修正してください。";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            // 1項目に複数のエラーがある場合は1つだけ返す。未入力のエラーを優先する
            if ("NotBlank".equals(error.getCode()) || !errors.containsKey(error.getField())) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
        }
        return new ApiErrorResponse(INVALID_INPUT_MESSAGE, errors);
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
}
