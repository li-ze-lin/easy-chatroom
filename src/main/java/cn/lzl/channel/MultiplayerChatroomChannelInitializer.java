package cn.lzl.channel;

import cn.lzl.handler.MultiplayerChatroomHandler;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 *
 *
 *  同一个聊天室多人聊天
 */
public class MultiplayerChatroomChannelInitializer extends MyChannelInitializer {

    @Override
    protected SimpleChannelInboundHandler<Object> getHandler() {
        return new MultiplayerChatroomHandler();
    }
}
