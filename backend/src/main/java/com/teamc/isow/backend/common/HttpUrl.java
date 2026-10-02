package com.teamc.isow.backend.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * http:// または https:// で始まる URL であること（null は可）。
 * 画面でリンクとして表示するため、javascript: などの他の形式は受け付けない。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {

    String message() default "http:// または https:// で始まるURLを入力してください";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
