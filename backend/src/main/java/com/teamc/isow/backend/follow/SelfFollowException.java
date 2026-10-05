package com.teamc.isow.backend.follow;

/** 自分自身をフォローしようとした。400 として扱う */
public class SelfFollowException extends RuntimeException {

    public SelfFollowException() {
        super("cannot follow yourself");
    }
}
