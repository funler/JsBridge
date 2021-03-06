package com.github.funler.jsbridge;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("SetJavaScriptEnabled")
public class BridgeWebView extends WebView implements WebViewJavascriptBridge {

    private final String TAG = "BridgeWebView";

    public static final String[] LOCAL_SCRIPTS_TO_LOAD = {
            "Object.assign.js",
            "Object.values.js",
            "Array.prototype.includes.js",
            "Promise.js",
            "WebViewJavascriptBridge.js"
    };

    Map<String, CallBackFunction> responseCallbacks = new HashMap<String, CallBackFunction>();
    Map<String, BridgeHandler> messageHandlers = new HashMap<String, BridgeHandler>();
    BridgeHandler defaultHandler = new DefaultHandler();

    private List<Message> startupMessage = new ArrayList<Message>();
    private Context context;

    public List<Message> getStartupMessage() {
        return startupMessage;
    }

    public void setStartupMessage(List<Message> startupMessage) {
        this.startupMessage = startupMessage;
    }

    private long uniqueId = 0;

    public BridgeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BridgeWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public BridgeWebView(Context context) {
        super(context);
        init(context);
    }

    /**
     * @param handler default handler,handle messages send by js without assigned handler name,
     *                if js message has handler name, it will be handled by named handlers registered by native
     */
    public void setDefaultHandler(BridgeHandler handler) {
        this.defaultHandler = handler;
    }

    private void init(Context context) {
        this.context = context;

        this.setVerticalScrollBarEnabled(true);
        this.setHorizontalScrollBarEnabled(true);

        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAppCacheEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false); // lock pinch to zooom
        settings.setDisplayZoomControls(false);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT); // This was added when clear cache was removed

        WebView.setWebContentsDebuggingEnabled(true);
        this.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        this.setWebViewClient(generateBridgeWebViewClient());

        for (JS2JavaHandlers handler : JS2JavaHandlers.values()) {
            messageHandlers.put(handler.name(), handler.handler());
        }
    }

    protected BridgeWebViewClient generateBridgeWebViewClient() {
        return new BridgeWebViewClient(this);
    }

    /**
     * CallBackFunction data
     *
     * @param url
     */
    void handlerReturnData(String url) {
        String functionName = BridgeUtil.getFunctionFromReturnUrl(url);
        CallBackFunction f = responseCallbacks.get(functionName);
        String data = BridgeUtil.getDataFromReturnUrl(url);
        if (f != null) {
            f.onCallBack(data);
            responseCallbacks.remove(functionName);
            return;
        }
    }

    @Override
    public void send(String data) {
        send(data, null);
    }

    @Override
    public void send(String data, CallBackFunction responseCallback) {
        doSend(null, data, responseCallback);
    }

    /**
     * message
     *
     * @param handlerName      handlerName
     * @param data             data
     * @param responseCallback CallBackFunction
     */
    private void doSend(String handlerName, String data, CallBackFunction responseCallback) {
        Message m = new Message();
        if (!TextUtils.isEmpty(data)) {
            m.setData(data);
        }
        if (responseCallback != null) {
            String callbackStr = String.format(BridgeUtil.CALLBACK_ID_FORMAT, ++uniqueId + (BridgeUtil.UNDERLINE_STR + SystemClock.currentThreadTimeMillis()));
            responseCallbacks.put(callbackStr, responseCallback);
            m.setCallbackId(callbackStr);
        }
        if (!TextUtils.isEmpty(handlerName)) {
            m.setHandlerName(handlerName);
        }
        queueMessage(m);
    }

    /**
     * list<message> != null
     *
     * @param m Message
     */
    private void queueMessage(Message m) {
        if (startupMessage != null) {
            startupMessage.add(m);
        } else {
            dispatchMessage(m);
        }
    }

    /**
     * message
     *
     * @param m Message
     */
    void dispatchMessage(Message m) {
        String messageJson = m.toJson();
        //escape special characters for json string json
        messageJson = messageJson.replaceAll("(\\\\)([^utrn])", "\\\\\\\\$1$2");
        messageJson = messageJson.replaceAll("(?<=[^\\\\])(\")", "\\\\\"");
        messageJson = messageJson.replaceAll("(?<=[^\\\\])(\')", "\\\\\'");
        String javascriptCommand = String.format(BridgeUtil.JS_HANDLE_MESSAGE_FROM_JAVA, messageJson);
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            this.loadUrl(javascriptCommand);
        }
    }

    void flushMessageQueue() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            loadUrl(BridgeUtil.JS_FETCH_QUEUE_FROM_JAVA, (data) -> {
                // deserializeMessage
                List<Message> list = null;
                try {
                    list = Message.toArrayList(data);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                if (list == null || list.size() == 0) {
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    Message m = list.get(i);
                    String responseId = m.getResponseId();
                    // response  CallBackFunction
                    if (!TextUtils.isEmpty(responseId)) {
                        CallBackFunction function = responseCallbacks.get(responseId);
                        String responseData = m.getResponseData();
                        function.onCallBack(responseData);
                        responseCallbacks.remove(responseId);
                    } else {
                        CallBackFunction responseFunction = null;
                        // if had callbackId
                        final String callbackId = m.getCallbackId();
                        if (!TextUtils.isEmpty(callbackId)) {

                            responseFunction = (data1) -> {
                                Log.d(TAG, "Prepare message for queue with data: " + data1);
                                Message responseMsg = new Message();
                                responseMsg.setResponseId(callbackId);
                                responseMsg.setResponseData(data1);
                                queueMessage(responseMsg);
                            };
                        } else {
                            responseFunction = (data12) -> {
                                // do nothing
                            };
                        }
                        // BridgeHandler
                        BridgeHandler handler;
                        if (!TextUtils.isEmpty(m.getHandlerName())) {
                            handler = messageHandlers.get(m.getHandlerName());
                        } else {
                            handler = defaultHandler;
                        }
                        if (handler != null) {
                            handler.handler(this.context, m.getData(), responseFunction);
                        }
                    }
                }
            });
        }
    }


    public void loadUrl(String jsUrl, CallBackFunction returnCallback) {
        this.loadUrl(jsUrl);
        responseCallbacks.put(BridgeUtil.parseFunctionName(jsUrl), returnCallback);
    }

    /**
     * register handler,so that javascript can call it
     *
     * @param handlerName handlerName
     * @param handler     BridgeHandler
     */
    public void registerHandler(String handlerName, BridgeHandler handler) {
        if (handler != null) {
            messageHandlers.put(handlerName, handler);
        }
    }

    /**
     * unregister handler
     *
     * @param handlerName
     */
    public void unregisterHandler(String handlerName) {
        if (handlerName != null) {
            messageHandlers.remove(handlerName);
        }
    }

    /**
     * call javascript registered handler
     *
     * @param handlerName handlerName
     * @param data        data
     * @param callBack    CallBackFunction
     */
    public void callHandler(String handlerName, String data, CallBackFunction callBack) {
        doSend(handlerName, data, callBack);
    }

    /**
     * call javascript registered handler
     *
     * @param handlerName handlerName
     * @param data        data
     * @param callBack    CallBackFunction
     */
    public void callHandler(String handlerName, JSONObject data, CallBackFunction callBack) {
        doSend(handlerName, data.toString(), callBack);
    }
}
