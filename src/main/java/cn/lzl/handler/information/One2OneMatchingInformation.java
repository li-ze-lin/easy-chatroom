package cn.lzl.handler.information;

import cn.lzl.handler.Mage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * 存储信息
 */
public class One2OneMatchingInformation {

    public static ConcurrentMap<String, ConcurrentMap<String, One2OneMatchingInformation>> map = new ConcurrentHashMap<>();
    public static Queue<One2OneMatchingInformation> queue = new ConcurrentLinkedQueue<>();
    public static ConcurrentMap<String, String> login = new ConcurrentHashMap<>();

    private ChannelHandlerContext ctx;
    private Mage mage;

    private One2OneMatchingInformation(ChannelHandlerContext ctx, Mage mage) {
        this.ctx = ctx;
        this.mage = mage;
    }

    /**
     * 添加到队列当中等待其他用户登录后匹配
     * @param ctx
     * @param mage
     */
    public static void offer(ChannelHandlerContext ctx, Mage mage) {
        queue.offer(new One2OneMatchingInformation(ctx, mage));
    }

    /**
     * 添加用户信息
     * @param ctx
     * @param mage
     */
    public static void add(ChannelHandlerContext ctx, Mage mage) {
        One2OneMatchingInformation iom = new One2OneMatchingInformation(ctx, mage);
        ConcurrentMap<String, One2OneMatchingInformation> cmap = new ConcurrentHashMap<>();
        if (map.containsKey(mage.getTable())) {
            map.get(mage.getTable()).put(mage.getId(), iom);
        } else {
            cmap.put(mage.getId(), iom);
            map.put(mage.getTable(), cmap);
        }
        login.replace(mage.getId(), mage.getTable());
    }

    /**
     * 删除用户信息
     * @param id
     * @param table
     */
    public static void delete(String id, String table) {
        ConcurrentMap<String, One2OneMatchingInformation> cmap = map.get(table);
        if (cmap.size() <= 1) {
            map.remove(table);
        } else {
            cmap.remove(id);
        }
    }

    /**
     * 给用户发送消息
     * @param mage
     * @throws Exception
     */
    public void sead(Mage mage) throws Exception{
        //this.ctx.channel().write(new TextWebSocketFrame(mage.toJson()));
        //this.ctx.flush();
        this.ctx.writeAndFlush(new TextWebSocketFrame(mage.toJson()));
    }

    public Mage getMage() {
        return this.mage;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return this.ctx;
    }

    public One2OneMatchingInformation setTableId(String table) {
        this.mage.setTableId(table);
        return this;
    }
}
