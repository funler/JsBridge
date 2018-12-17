package com.github.funler.jsbridge;

public class DefaultHandler implements BridgeHandler {

	String TAG = "DefaultHandler";
	
	@Override
	public void handler(String data, CallBackFunction function) {
		if(function != null){
			function.onCallBack(TAG + " response data");
		}
	}
}
