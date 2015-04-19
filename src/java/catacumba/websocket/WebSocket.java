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

package catacumba.websocket;

import io.netty.buffer.ByteBuf;
import ratpack.api.NonBlocking;
import ratpack.handling.Context;
import ratpack.exec.Promise;

public interface WebSocket {

  Context getContext();

  @NonBlocking
  Promise<Void> close();

  @NonBlocking
  Promise<Void> close(int statusCode, String reason);

  boolean isOpen();

  @NonBlocking
  Promise<Void> send(String text);

  @NonBlocking
  Promise<Void> send(ByteBuf text);

}
