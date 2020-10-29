package cn.lzl.channel;

import cn.lzl.handler.One2OneMatchingHandler;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 *
 * @author Dream
 *
 *  每两个匹配房间
 */
public class One2OneMatchingChannelInitializer extends MyChannelInitializer {
    @Override
    protected SimpleChannelInboundHandler<Object> getHandler() {
        return new One2OneMatchingHandler();
    }
}
