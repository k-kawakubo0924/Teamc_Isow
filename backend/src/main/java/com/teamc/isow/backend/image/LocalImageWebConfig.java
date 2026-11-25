package com.teamc.isow.backend.image;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * ローカルフォルダに保存した画像を、ブラウザから /uploads/** で表示できるようにする。
 * 有効になる条件は LocalImageStorage と同じ。クラウドストレージに差し替えた場合は、画像はそちらの URL から配信されるため無効になる。
 * <img> タグからの読み込みには認証ヘッダーを付けられないため、SecurityConfig で GET /uploads/** を認証なしで許可している
 * （ファイル名は推測できない乱数のため、URL を知らなければ取得できない）。
 * 配るのは、アプリで誰かに見えている画像だけ（PublicImageAccess）。応答の Cache-Control は Spring Security の既定（no-store）のまま
 * にし、ブラウザに残さない（残すと、非公開にした後も、開いたことのある人の手元で見え続けるため）。
 */
@Configuration
@ConditionalOnProperty(name = "app.image.storage", havingValue = "local", matchIfMissing = true)
public class LocalImageWebConfig implements WebMvcConfigurer {

    /** 画像を配信する URL のパス（app.image.local.public-base-url の末尾と合わせる） */
    public static final String URL_PATH = "/uploads/";

    private final LocalImageStorage storage;
    private final PublicImageAccess publicImageAccess;

    public LocalImageWebConfig(LocalImageStorage storage, PublicImageAccess publicImageAccess) {
        this.storage = storage;
        this.publicImageAccess = publicImageAccess;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // フォルダ外のファイル（../ など）を指すリクエストは Spring 側で拒否される
        registry.addResourceHandler(URL_PATH + "**")
                .addResourceLocations(storage.getBaseDir().toUri().toString());
    }

    /**
     * 配ってよい画像だけを配る（PublicImageAccess。非公開にした投稿の写真などを止める）。
     * 止めるときは、存在しないファイルと同じ例外を投げ、応答を同じ形にする（非公開になったことを URL から探れないように）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws NoResourceFoundException {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                String fileName = path.startsWith(URL_PATH) ? path.substring(URL_PATH.length()) : null;
                if (!publicImageAccess.isServable(fileName)) {
                    throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), path, path);
                }
                return true;
            }
        }).addPathPatterns(URL_PATH + "**");
    }
}
