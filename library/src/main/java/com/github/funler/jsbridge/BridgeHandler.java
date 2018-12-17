package com.github.funler.jsbridge;

import android.content.Context;

public interface BridgeHandler {
//	void handler(String data, CallBackFunction function);
	void handler(Context context, String data, CallBackFunction function);
}
