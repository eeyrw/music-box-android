package com.yuan.midiplayer;

public interface PlayerEventListener {
    void onPlayStateChange(Player.PlayerState state);

    void onNoteOn(int note);
}
