package catacumba.impl;

import clojure.lang.IDeref;

public final class DelegatedContext implements IDeref {
  public final Object data;

  public DelegatedContext(final Object data) {
    this.data = data;
  }

  public boolean isEmpty() {
    return this.data == null;
  }

  public Object deref() {
    return this.data;
  }
}
