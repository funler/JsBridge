package com.github.funler.jsbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

public enum JS2JavaHandlers {

    setNativeStorageItem((Context context, String data, CallBackFunction function) -> {
        Log.d(getTag(), "setNativeStorageItem, data " + data);

        try {
            JSONArray dataArray = new JSONArray(data);
            SharedPreferences.Editor prefs = context.getSharedPreferences(JS2JavaHandlers.STORAGE_KEY, Context.MODE_PRIVATE).edit();

            String key = dataArray.getString(0);
            String value = dataArray.getString(1);
            if (value == null || data.equals("null")) {
                prefs.remove(key);
            } else {
                prefs.putString(key, value);
            }

            prefs.apply();
            function.onCallBack("true");
        } catch (JSONException e) {
            e.printStackTrace();
            function.onCallBack("false");
        }
    }),

    getNativeStorageItem((Context context, String data, CallBackFunction function) -> {
        Log.d(getTag(), "getNativeStorageItem, data " + data);

        try {
            JSONArray dataArray = new JSONArray(data);
            SharedPreferences prefs = context.getSharedPreferences(JS2JavaHandlers.STORAGE_KEY, Context.MODE_PRIVATE);

            String result = prefs.getString(dataArray.getString(0), null);
            Log.d(getTag(), "getNativeStorageItem, result " + result);

            function.onCallBack(result);
        } catch (JSONException e) {
            e.printStackTrace();
            function.onCallBack("false");
        }
    });

    private BridgeHandler handler;
    private static final String STORAGE_KEY = "storage";

    JS2JavaHandlers(BridgeHandler handler) {
        this.handler = handler;
    }

    public BridgeHandler handler() {
        return handler;
    }

    private static String getTag() {
        return JS2JavaHandlers.class.getSimpleName();
    }
}
