package com.github.funler.jsbridge;

import android.content.Context;

public interface BridgeHandler {
	void handler(Context context, String data, CallBackFunction function);
}
