package cn.lzl;

import cn.lzl.channel.One2OneMatchingChannelInitializer;

/**
 * 一对一匹配聊天
 */
public class One2OneMatchingMain {
    public static void main(String[] args) {
        new NettyService(11111, new One2OneMatchingChannelInitializer()).start();
    }
}
