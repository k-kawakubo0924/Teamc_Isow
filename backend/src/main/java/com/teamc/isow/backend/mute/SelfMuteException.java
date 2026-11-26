package com.teamc.isow.backend.mute;

/** 自分自身をミュートしようとした（400） */
public class SelfMuteException extends RuntimeException {

    public SelfMuteException() {
        super("cannot mute yourself");
    }
}
