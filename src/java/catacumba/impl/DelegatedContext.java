package catacumba.impl;

public final class DelegatedContext {
  public final Object data;

  public DelegatedContext(final Object data) {
    this.data = data;
  }

  public boolean isEmpty() {
    return this.data == null;
  }

}
