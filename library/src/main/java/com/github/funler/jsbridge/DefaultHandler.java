package com.github.funler.jsbridge;

import android.content.Context;

public class DefaultHandler implements BridgeHandler {

	@Override
	public void handler(Context context, String data, CallBackFunction function) {
		if(function != null){
			function.onCallBack("DefaultHandler response data");
		}
	}
}
