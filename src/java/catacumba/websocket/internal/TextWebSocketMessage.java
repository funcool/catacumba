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

import catacumba.websocket.WebSocket;
import catacumba.websocket.WebSocketMessage;


public class TextWebSocketMessage implements WebSocketMessage<String> {
  private final WebSocket webSocket;
  private final String text;

  public TextWebSocketMessage(WebSocket webSocket, String text) {
    this.webSocket = webSocket;
    this.text = text;
  }

  @Override
  public WebSocket getConnection() {
    return webSocket;
  }

  @Override
  public String getData() {
    return text;
  }
}
