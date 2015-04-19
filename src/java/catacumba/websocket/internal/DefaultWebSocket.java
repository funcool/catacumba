/*
 * Copyright 2013 the original author or authors.
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
import catacumba.websocket.WebSocket;
import ratpack.handling.Context;
import ratpack.exec.Promise;
import ratpack.exec.Fulfiller;
import ratpack.func.Action;

import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultWebSocket implements WebSocket {

  private final Context context;
  private final Channel channel;
  private final Runnable onClose;
  private final AtomicBoolean open;

  public DefaultWebSocket(Context context, Channel channel, AtomicBoolean open, Runnable onClose) {
    this.context = context;
    this.channel = channel;
    this.onClose = onClose;
    this.open = open;
  }

  @Override
  public Context getContext() {
    return this.context;
  }

  @Override
  public Promise close() {
    return close(1000, null);
  }

  @Override
  public Promise<Void> close(int statusCode, String reason) {
    open.set(false);
    channel.writeAndFlush(new CloseWebSocketFrame(statusCode, reason));
    final ChannelFuture future = channel.close();
    future.addListener(f -> onClose.run());

    return this.context.promise(new Action<Fulfiller<Void>>() {
        public void execute(Fulfiller<Void> f) {
          future.addListener(future -> f.success(null));
        }
      });
  }

  @Override
  public boolean isOpen() {
    return open.get();
  }

  @Override
  public Promise<Void> send(String text) {
    final ChannelFuture future = channel.writeAndFlush(new TextWebSocketFrame(text));
    return this.context.promise(new Action<Fulfiller<Void>>() {
        public void execute(Fulfiller<Void> f) {
          future.addListener(future -> f.success(null));
        }
      });
  }

  @Override
  public Promise<Void> send(ByteBuf text) {
    final ChannelFuture future = channel.writeAndFlush(new TextWebSocketFrame(text));
    return this.context.promise(new Action<Fulfiller<Void>>() {
        public void execute(Fulfiller<Void> f) {
          future.addListener(future -> f.success(null));
        }
      });
  }

}
