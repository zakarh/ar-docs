package com.blastrock.ardocs;

public class CallbackInterface {
    public interface CallbackVoid {
        void call();
    }

    public interface CallbackLong {
        void call(long value);
    }

    public interface CallbackBoolean {
        void call(Boolean value);
    }
}
