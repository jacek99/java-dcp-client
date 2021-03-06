/*
 * Copyright (c) 2016 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.couchbase.client.dcp.transport.netty;

import com.couchbase.client.core.logging.CouchbaseLogger;
import com.couchbase.client.core.logging.CouchbaseLoggerFactory;
import com.couchbase.client.dcp.ConnectionNameGenerator;
import com.couchbase.client.dcp.message.MessageUtil;
import com.couchbase.client.dcp.message.DcpOpenConnectionRequest;
import com.couchbase.client.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.deps.io.netty.buffer.Unpooled;
import com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext;
import com.couchbase.client.deps.io.netty.channel.ChannelOutboundHandler;
import com.couchbase.client.deps.io.netty.channel.ChannelPromise;
import com.couchbase.client.deps.io.netty.channel.SimpleChannelInboundHandler;
import com.couchbase.client.deps.io.netty.util.CharsetUtil;
import com.couchbase.client.deps.io.netty.util.concurrent.Future;
import com.couchbase.client.deps.io.netty.util.concurrent.GenericFutureListener;

import java.net.SocketAddress;

/**
 * Opens the DCP connection on the channel and once established removes itself.
 */
public class DcpConnectHandler
    extends SimpleChannelInboundHandler<ByteBuf>
    implements ChannelOutboundHandler {

    private static final CouchbaseLogger LOGGER = CouchbaseLoggerFactory.getInstance(DcpConnectHandler.class);

    private final ConnectionNameGenerator connectionNameGenerator;

    private ChannelPromise originalPromise;
    private String connectionName;

    public DcpConnectHandler(ConnectionNameGenerator connectionNameGenerator) {
        this.connectionNameGenerator = connectionNameGenerator;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        connectionName = connectionNameGenerator.name();

        ByteBuf request = ctx.alloc().buffer();
        DcpOpenConnectionRequest.init(request);
        DcpOpenConnectionRequest.connectionName(
            request,
            Unpooled.copiedBuffer(connectionName, CharsetUtil.UTF_8)
        );
        ctx.writeAndFlush(request);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        short status = MessageUtil.getStatus(msg);
        if (status == 0) {
            originalPromise.setSuccess();
            ctx.pipeline().remove(this);
            ctx.fireChannelActive();
            LOGGER.debug("DCP Connection opened with Name \"{}\" against Node {}", connectionName, ctx.channel().remoteAddress());
        } else {
            originalPromise.setFailure(new IllegalStateException("Could not open DCP Connection: " + status));
        }
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        originalPromise = promise;
        ChannelPromise downPromise = ctx.newPromise();
        downPromise.addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess() && !originalPromise.isDone()) {
                    originalPromise.setFailure(future.cause());
                }
            }
        });
        ctx.connect(remoteAddress, localAddress, downPromise);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
