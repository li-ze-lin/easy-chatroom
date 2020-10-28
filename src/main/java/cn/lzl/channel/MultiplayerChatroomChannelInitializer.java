package cn.lzl.channel;

import cn.lzl.channel.MyChannelInitializer;
import cn.lzl.handler.MultiplayerChatroomHandler;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 *
 * @author Dream
 *
 *  指定房间
 */
public class MultiplayerChatroomChannelInitializer extends MyChannelInitializer {

    @Override
    protected SimpleChannelInboundHandler<Object> getHandler() {
        return new MultiplayerChatroomHandler();
    }
}
