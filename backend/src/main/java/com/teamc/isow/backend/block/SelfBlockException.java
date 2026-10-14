package com.teamc.isow.backend.block;

/** 自分自身をブロックしようとした（400） */
public class SelfBlockException extends RuntimeException {

    public SelfBlockException() {
        super("cannot block yourself");
    }
}
