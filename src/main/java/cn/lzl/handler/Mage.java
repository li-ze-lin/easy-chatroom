package cn.lzl.handler;

import com.alibaba.fastjson.JSON;

/**
 * 解析消息
 * 将前台发过来的消息解析成Mage
 * 后台发送消息到前台转成json字符串
 */
public class Mage {

    /**
     * 那个聊天室
     */
    private String table;
    /**
     * 用户id
     */
    private String id;
    /**
     * 用户名
     */
    private String name;
    /**
     * 所发送的消息
     */
    private String message;

    /**
     * 将json字符串转成Mage
     */
    public static Mage strJson2Mage(String message) throws Exception{
        return JSON.parseObject(message, Mage.class);
    }

    /**
     * 将Mage转成json字符串
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }

    public Mage setTableId(String table) {
        this.setTable(table);
        return this;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
