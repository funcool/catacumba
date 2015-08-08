/*
 * Copyright 2015 the original author or authors.
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

package catacumba.websocket.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import catacumba.websocket.WebSocket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;


public class DefaultWebSocket implements WebSocket {
  // private static final Logger LOGGER = LoggerFactory.getLogger(WebSocket.class);
  private final Channel channel;
  private final Runnable onClose;
  private final AtomicBoolean open;

  public DefaultWebSocket(Channel channel, AtomicBoolean open, Runnable onClose) {
    this.channel = channel;
    this.onClose = onClose;
    this.open = open;
  }

  @Override
  public void close() {
    close(1000, null);
  }

  @Override
  public void close(int statusCode, String reason) {
    open.set(false);
    channel.writeAndFlush(new CloseWebSocketFrame(statusCode, reason));
    channel.close().addListener(future -> onClose.run());
  }

  @Override
  public boolean isOpen() {
    return open.get();
  }

  @Override
  public CompletionStage<Void> send(String text) {
    final CompletableFuture<Void> f = new CompletableFuture<Void>();
    channel.writeAndFlush(new TextWebSocketFrame(text)).addListener(future -> {
        if (future.isSuccess()) {
          f.complete(null);
        } else {
          f.completeExceptionally(future.cause());
        }
      });

    return f;
  }

  @Override
  public CompletionStage<Void> send(ByteBuf data) {
    final CompletableFuture<Void> f = new CompletableFuture<Void>();
    channel.writeAndFlush(new BinaryWebSocketFrame(data)).addListener(future -> {
        if (future.isSuccess()) {
          f.complete(null);
        } else {
          f.completeExceptionally(future.cause());
        }
      });

    return f;
  }
}
