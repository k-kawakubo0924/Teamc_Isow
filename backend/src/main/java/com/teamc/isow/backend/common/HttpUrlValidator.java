package com.teamc.isow.backend.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/** {@link HttpUrl} の判定。スキームが http / https で、ホスト名があり、空白を含まないこと */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    // 日本語を含む URL（https://example.com/商品 など）も貼り付けられるため、URI の厳密な形式までは求めない
    private static final Pattern HTTP_URL = Pattern.compile("^(?i)https?://[^\\s/?#@]+(?:[/?#][^\\s]*)?$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || HTTP_URL.matcher(value).matches();
    }
}
