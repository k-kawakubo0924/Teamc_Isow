package com.teamc.isow.backend.image;

/** アップロードされたファイルが、保存できる画像の条件を満たさない。message は画面にそのまま表示できる文言 */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
