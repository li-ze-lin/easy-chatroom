package cn.lzl;

import cn.lzl.channel.MultiplayerChatroomChannelInitializer;

public class MultiplayerChatroomMain {
    public static void main(String[] args) {
        new NettyService(11112, new MultiplayerChatroomChannelInitializer()).start();
    }
}
