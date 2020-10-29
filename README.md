# 用Netty实现简单的聊天
1. 一对一匹配聊天
2. 同一个聊天室多人聊天

## 通用的启动代码
```java
public class NettyService {

    private final int port;
    private final ChannelInitializer<Channel> handler;

    public NettyService(int port, ChannelInitializer<Channel> handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(handler);
            Channel channel = serverBootstrap.bind(port).sync().channel();
            channel.closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
```

## 1. 一对一匹配聊天

### main
```java
public class One2OneMatchingMain {
    public static void main(String[] args) {
        new NettyService(11111, new One2OneMatchingChannelInitializer()).start();
    }
}
```

### 自定义的ChannelInitializer
```java
public class One2OneMatchingChannelInitializer extends MyChannelInitializer {
    @Override
    protected SimpleChannelInboundHandler<Object> getHandler() {
        return new One2OneMatchingHandler();
    }
}

public abstract class MyChannelInitializer extends ChannelInitializer<Channel> {

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("http-codec", new HttpServerCodec()); // Http消息编码解码
        pipeline.addLast("aggregator", new HttpObjectAggregator(65536)); // Http消息组装
        pipeline.addLast("http-chunked", new ChunkedWriteHandler()); // WebSocket通信支持
        pipeline.addLast("handler", getHandler());
    }

    protected abstract SimpleChannelInboundHandler<Object> getHandler();
}
```

### 自定义Handler
```java
public class One2OneMatchingHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;
    private ChannelHandlerContext ctx;
    private String sessionId;
    private String name;

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object o) throws Exception {
        if (o instanceof FullHttpRequest) { // 传统的HTTP接入
            handleHttpRequest(ctx, (FullHttpRequest) o);
        } else if (o instanceof WebSocketFrame) { // WebSocket接入
            handleWebSocketFrame(ctx, (WebSocketFrame) o);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);
        //关闭连接将移除该用户消息
        Mage mage = new Mage();
        mage.setName(this.name);
        mage.setMessage("20002");
        //将用户下线信息发送给为下线用户
        String table = One2OneMatchingInformation.login.get(this.sessionId);
        ConcurrentMap<String, One2OneMatchingInformation> cmap = One2OneMatchingInformation.map.get(table);
        if (cmap != null) {
            cmap.forEach((id, iom) -> {
                try {
                    if (id != this.sessionId) iom.sead(mage);
                } catch (Exception e) {
                    System.err.println(e);
                }
            });
        }
        One2OneMatchingInformation.login.remove(this.sessionId);
        One2OneMatchingInformation.map.remove(table);
    }

    /**
     * 处理Http请求，完成WebSocket握手<br/>
     * 注意：WebSocket连接第一次请求使用的是Http
     * @param ctx
     * @param request
     * @throws Exception
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // 如果HTTP解码失败，返回HTTP异常
        if (!request.getDecoderResult().isSuccess() || (!"websocket".equals(request.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // 正常WebSocket的Http连接请求，构造握手响应返回
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory("ws://" + request.headers().get(HttpHeaders.Names.HOST), null, false);
        handshaker = wsFactory.newHandshaker(request);
        if (handshaker == null) { // 无法处理的websocket版本
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else { // 向客户端发送websocket握手,完成握手
            handshaker.handshake(ctx.channel(), request);
            // 记录管道处理上下文，便于服务器推送数据到客户端
            this.ctx = ctx;
        }
    }

    /**
     * Http返回
     * @param ctx
     * @param request
     * @param response
     */
    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        // 返回应答给客户端
        if (response.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(response.getStatus().toString(), CharsetUtil.UTF_8);
            response.content().writeBytes(buf);
            buf.release();
            HttpHeaders.setContentLength(response, response.content().readableBytes());
        }

        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(response);
        if (!HttpHeaders.isKeepAlive(request) || response.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 处理Socket请求
     * @param ctx
     * @param frame
     * @throws Exception
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // 判断是否是关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        // 判断是否是Ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        // 当前只支持文本消息，不支持二进制消息
        if ((frame instanceof TextWebSocketFrame)) {
            //获取发来的消息
            String text =((TextWebSocketFrame)frame).text();
            System.out.println("mage : " + text);
            //消息转成Mage
            Mage mage = Mage.strJson2Mage(text);
            if (mage.getMessage().equals("10001")) {
                if (!One2OneMatchingInformation.login.containsKey(mage.getId())) {
                    One2OneMatchingInformation.login.put(mage.getId(), "");
                    One2OneMatchingInformation.offer(ctx, mage);
                    if (queue.size() >= 2) {
                        String tableId = UUID.randomUUID().toString();
                        One2OneMatchingInformation iom1 = queue.poll().setTableId(tableId);
                        One2OneMatchingInformation iom2 = queue.poll().setTableId(tableId);
                        One2OneMatchingInformation.add(iom1.getChannelHandlerContext(), iom1.getMage());
                        One2OneMatchingInformation.add(iom2.getChannelHandlerContext(), iom2.getMage());
                        iom1.sead(iom2.getMage());
                        iom2.sead(iom1.getMage());
                    }
                } else {//用户已登录
                    mage.setMessage("-10001");
                    sendWebSocket(mage.toJson());
                    ctx.close();
                }
            } else {
                //将用户发送的消息发给所有在同一聊天室内的用户
                One2OneMatchingInformation.map.get(mage.getTable()).forEach((id, iom) -> {
                    try {
                        iom.sead(mage);
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                });
            }
            //记录id 当页面刷新或浏览器关闭时，注销掉此链路
            this.sessionId = mage.getId();
            this.name = mage.getName();
        } else {
            System.err.println("------------------error--------------------------");
        }
    }

    /**
     * WebSocket返回
     */
    public void sendWebSocket(String msg) throws Exception {
        if (this.handshaker == null || this.ctx == null || this.ctx.isRemoved()) {
            throw new Exception("尚未握手成功，无法向客户端发送WebSocket消息");
        }
        //发送消息
        this.ctx.channel().write(new TextWebSocketFrame(msg));
        this.ctx.flush();
    }
}
```

### 页面
```html
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>一对一聊天室</title>
    <script src="jquery.min.js"></script>
</head>
<body>

    <div id="top">请等待匹配</div>

    <div id="bottom">
        <div id="title"></div>
        <div id = 'users'></div>
        <div>
            <input type="text" id="mag"/>
            <input type="button" value="发送" onclick="send()"/>
        </div>
        <div id="sed" style="height: 300px;width: 500px;border:1px solid;"></div>
    </div>
</body>
<script>
    var user;
    var socket;
    $(function() {
        var random = Math.ceil(Math.random()*1000);
        $('#bottom').hide();
        user = {
            id:"id_" + random,
            name:"name_" + random,
            pwd:"pwd_" + random
        };
        console.log(user);
        var temp = typeof(user);
        console.log(temp);
        inChat();
    });

    //进入聊天室
    function inChat() {
        if (!window.WebSocket) {
            window.WebSocket = window.MozWebSocket;
        }
        if (window.WebSocket) {
            //获取h5 socket
            socket = new WebSocket("ws://127.0.0.1:11111/");
            //接收消息
            socket.onmessage = function(data){
                console.log("socket.onmessage:")
                console.log(data);
                var mage = JSON.parse(data.data);
                console.log(mage.message);
                if (mage.message == '10001') {//10001为上线
                    $('#top').hide();
                    $('#bottom').show();
                    $('#title').text('chat' + mage.table);
                    $('#users').append('<span>'+ mage.name + '\t</span>');
                    user.table = mage.table;
                } else if (mage.message == '20002') {//对方下线
                    $('#bottom').hide();
                    $('#top').show();
                    $('#top').text('对方已下线');
                    socket.close();
                } else if (mage.message == '-10001') {//已经在线
                    $('#bottom').hide();
                    $('#top').text('已经在线!');
                } else {//用户发的消息
                    $('#sed').append('<span>'+ mage.name + ' : ' + mage.message + '</span><br/>');
                }
            }
            //webSocket的链接
            socket.onopen = function(data) {
                $('#top').text('链接成功,请等待匹配');
                console.log("socket.onopen:")
                console.log(data);
                user.table = '';
                user.message = '10001';
                delete user.pwd;
                console.log(user);
                //链接成功后发送用户信息进入聊天室
                socket.send(JSON.stringify(user));
            }
            //webSocket关闭
            socket.onclose = function(data) {
                console.log("socket.onclose:")
                console.log(data);
            }
            //webSocket错误信息
            socket.onerror = function(data) {
                console.log("socket.onerror:")
                console.log(data);
            }
        } else {
            alert("抱歉，您的浏览器不支持WebSocket协议!");
        }
    }

    //发送消息
    function send() {
        user.message = $('#mag').val();
        socket.send(JSON.stringify(user));
    }
</script>
</html>
```


## 2. 同一个聊天室多人聊天

### main
```java
public class MultiplayerChatroomMain {
    public static void main(String[] args) {
        new NettyService(11112, new MultiplayerChatroomChannelInitializer()).start();
    }
}
```

### 自定义的ChannelInitializer
```java
public class MultiplayerChatroomChannelInitializer extends MyChannelInitializer {
    @Override
    protected SimpleChannelInboundHandler<Object> getHandler() {
        return new MultiplayerChatroomHandler();
    }
}

public abstract class MyChannelInitializer extends ChannelInitializer<Channel> {

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("http-codec", new HttpServerCodec()); // Http消息编码解码
        pipeline.addLast("aggregator", new HttpObjectAggregator(65536)); // Http消息组装
        pipeline.addLast("http-chunked", new ChunkedWriteHandler()); // WebSocket通信支持
        pipeline.addLast("handler", getHandler());
    }

    protected abstract SimpleChannelInboundHandler<Object> getHandler();
}
```

### 自定义Handler
```java
public class MultiplayerChatroomHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;
    private ChannelHandlerContext ctx;
    private String sessionId;
    private String table;
    private String name;

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object o) throws Exception {
        if (o instanceof FullHttpRequest) { // 传统的HTTP接入
            handleHttpRequest(ctx, (FullHttpRequest) o);
        } else if (o instanceof WebSocketFrame) { // WebSocket接入
            handleWebSocketFrame(ctx, (WebSocketFrame) o);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);
        System.out.println("delete : id = " + this.sessionId + " table = " + this.table);
        //关闭连接将移除该用户消息
        MultiplayerChatroomInformation.delete(this.sessionId, this.table);
        Mage mage = new Mage();
        mage.setName(this.name);
        mage.setMessage("20002");
        //将用户下线信息发送给为下线用户
        MultiplayerChatroomInformation.map.get(this.table).forEach((id, iom) -> {
            try {
                iom.sead(mage);
            } catch (Exception e) {
                System.err.println(e);
            }
        });
    }

    /**
     * 处理Http请求，完成WebSocket握手<br/>
     * 注意：WebSocket连接第一次请求使用的是Http
     * @param ctx
     * @param request
     * @throws Exception
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // 如果HTTP解码失败，返回HTTP异常
        if (!request.getDecoderResult().isSuccess() || (!"websocket".equals(request.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // 正常WebSocket的Http连接请求，构造握手响应返回
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory("ws://" + request.headers().get(HttpHeaders.Names.HOST), null, false);
        handshaker = wsFactory.newHandshaker(request);
        if (handshaker == null) { // 无法处理的websocket版本
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else { // 向客户端发送websocket握手,完成握手
            handshaker.handshake(ctx.channel(), request);
            // 记录管道处理上下文，便于服务器推送数据到客户端
            this.ctx = ctx;
        }
    }

    /**
     * Http返回
     * @param ctx
     * @param request
     * @param response
     */
    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        System.out.println("sendHttpResponse:7");
        // 返回应答给客户端
        if (response.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(response.getStatus().toString(), CharsetUtil.UTF_8);
            response.content().writeBytes(buf);
            buf.release();
            HttpHeaders.setContentLength(response, response.content().readableBytes());
        }

        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(response);
        if (!HttpHeaders.isKeepAlive(request) || response.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 处理Socket请求
     * @param ctx
     * @param frame
     * @throws Exception
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // 判断是否是关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        // 判断是否是Ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        // 当前只支持文本消息，不支持二进制消息
        if ((frame instanceof TextWebSocketFrame)) {
            //throw new UnsupportedOperationException("当前只支持文本消息，不支持二进制消息");
            //获取发来的消息
            String text =((TextWebSocketFrame)frame).text();
            System.out.println("mage : " + text);
            //消息转成Mage
            Mage mage = Mage.strJson2Mage(text);
            //判断是以存在用户信息
            if (MultiplayerChatroomInformation.isNo(mage)) {
                //判断是否有这个聊天室
                if (MultiplayerChatroomInformation.map.containsKey(mage.getTable())) {
                    //判断是否有其他用户
                    if (MultiplayerChatroomInformation.map.get(mage.getTable()).size() > 0) {
                        MultiplayerChatroomInformation.map.get(mage.getTable()).forEach((id, iom) -> {
                            try {
                                Mage mag = iom.getMage();
                                mag.setMessage("30003");
                                //发送其他用户信息给要注册用户
                                this.sendWebSocket(mag.toJson());
                            } catch (Exception e) {
                                System.err.println(e);
                            }
                        });
                    }
                }
                //添加用户
                MultiplayerChatroomInformation.add(ctx, mage);
                System.out.println("add : " + mage.toJson());
            }
            //将用户发送的消息发给所有在同一聊天室内的用户
            MultiplayerChatroomInformation.map.get(mage.getTable()).forEach((id, iom) -> {
                try {
                    iom.sead(mage);
                } catch (Exception e) {
                    System.err.println(e);
                }
            });
            //记录id和table 当页面刷新或浏览器关闭时，注销掉此链路
            this.sessionId = mage.getId();
            this.table = mage.getTable();
            this.name = mage.getName();
        } else {
            System.err.println("------------------error--------------------------");
        }
    }

    /**
     * WebSocket返回
     */
    public void sendWebSocket(String msg) throws Exception {
        if (this.handshaker == null || this.ctx == null || this.ctx.isRemoved()) {
            throw new Exception("尚未握手成功，无法向客户端发送WebSocket消息");
        }
        //发送消息
        this.ctx.channel().write(new TextWebSocketFrame(msg));
        this.ctx.flush();
    }
}
```

### 页面
```html
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>简单的聊天室</title>
    <script src="jquery.min.js"></script>
</head>
<body>
    <div id="top">
        <input type="button" value="1" id="button1" onclick="inChat('1')"/>
        <input type="button" value="2" id="button2" onclick="inChat('2')"/><br/>
    </div>

    <div id="bottom">
        <div id="title"></div>
        <div id = 'users'></div>
        <div>
            <input type="text" id="mag"/>
            <input type="button" value="发送" onclick="send()"/>
        </div>
        <div id="sed" style="height: 300px;width: 500px;border:1px solid;"></div>
    </div>
</body>
<script>
    var user;
    var socket;
    $(function() {
        var random = Math.ceil(Math.random()*1000);
        $('#bottom').hide();
        user = {
            id:"id_" + random,
            name:"name_" + random,
            pwd:"pwd_" + random
        };
        console.log(user);
        var temp = typeof(user);
        console.log(temp);
    });

    //进入聊天室
    function inChat(num) {
        if (!window.WebSocket) {
            window.WebSocket = window.MozWebSocket;
        }
        if (window.WebSocket) {
            //获取h5 socket
            socket = new WebSocket("ws://127.0.0.1:11112/");
            //接收消息
            socket.onmessage = function(data){
                console.log("socket.onmessage:")
                console.log(data);
                var mage = JSON.parse(data.data);
                console.log(mage.message);
                if (mage.message == '10001') {//10001为上线
                    $('#top').hide();
                    $('#bottom').show();
                    $('#title').text('chat' + mage.table);
                    $('#users').append('<span>'+ mage.name + '\t</span>');
                    $('#sed').append('<span>'+ mage.name +'上线</span><br/>');
                } else if (mage.message == '20002') {//有人下线
                    var cns = $('#users').children();
                    console.log(cns);
                    for (var i = 0; i < cns.length; i++) {
                        if ($(cns[i]).text() == mage.name + '\t') {
                            $(cns[i]).remove();
                        }
                    }
                    $('#sed').append('<span>'+ mage.name +'下线</span><br/>');
                } else if (mage.message == '30003') {//加载已上线用户
                    $('#users').append('<span>'+ mage.name + '\t</span>');
                } else {//用户发的消息
                    $('#sed').append('<span>'+ mage.name + ' : ' + mage.message + '</span><br/>');
                }
            }
            //webSocket的链接
            socket.onopen = function(data) {
                console.log("socket.onopen:")
                console.log(data);
                user.table = num;
                user.message = '10001';
                delete user.pwd;
                console.log(user);
                //链接成功后发送用户信息进入聊天室
                socket.send(JSON.stringify(user));
            }
            //webSocket关闭
            socket.onclose = function(data) {
                console.log("socket.onclose:")
                console.log(data);
            }
            //webSocket错误信息
            socket.onerror = function(data) {
                console.log("socket.onerror:")
                console.log(data);
            }
        } else {
            alert("抱歉，您的浏览器不支持WebSocket协议!");
        }
    }

    //发送消息
    function send() {
        user.message = $('#mag').val();
        socket.send(JSON.stringify(user));
    }
</script>
</html>
```
