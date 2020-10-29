package cn.lzl;

import cn.lzl.channel.MultiplayerChatroomChannelInitializer;

/**
 * 同一个聊天室多人聊天
 */
public class MultiplayerChatroomMain {
    public static void main(String[] args) {
        new NettyService(11112, new MultiplayerChatroomChannelInitializer()).start();
    }
}
