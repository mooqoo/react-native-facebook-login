package com.magus.fblogin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.share.Sharer;
import com.facebook.share.model.ShareVideo;
import com.facebook.share.model.ShareVideoContent;
import com.facebook.share.widget.ShareDialog;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FacebookLoginModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    private static final String TAG = "TEST_FB";

    private final String CALLBACK_TYPE_SUCCESS = "success";
    private final String CALLBACK_TYPE_ERROR = "error";
    private final String CALLBACK_TYPE_CANCEL = "cancel";

    private CallbackManager mCallbackManager;
    private Callback mLoginCallback;

    private ShareDialog mShareDialog;
    private Callback mShareCallback;

    public FacebookLoginModule(ReactApplicationContext reactContext) {
        super(reactContext);

        FacebookSdk.sdkInitialize(reactContext.getApplicationContext());
        mCallbackManager = CallbackManager.Factory.create();
        setupFBLoginCallback();

        reactContext.addActivityEventListener(this);
    }

    private void setupFBLoginCallback() {

        FacebookCallback<LoginResult> fbcb =
            new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(final LoginResult loginResult) {

                    GraphRequest request = GraphRequest.newMeRequest(
                        loginResult.getAccessToken(),
                            new GraphRequest.GraphJSONObjectCallback() {
                                @Override
                                public void onCompleted(JSONObject me, GraphResponse response) {
                                    if (mLoginCallback == null) return;

                                    FacebookRequestError error = response.getError();

                                    if (error != null) {
                                        WritableMap map = Arguments.createMap();

                                        map.putString("errorType", error.getErrorType());
                                        map.putString("message", error.getErrorMessage());
                                        map.putString("recoveryMessage", error.getErrorRecoveryMessage());
                                        map.putString("userMessage", error.getErrorUserMessage());
                                        map.putString("userTitle", error.getErrorUserTitle());
                                        map.putInt("code", error.getErrorCode());
                                        map.putString("eventName", "onError");

                                        invokeCallback(mLoginCallback, CALLBACK_TYPE_ERROR, map);
                                        mLoginCallback = null;
                                    } else {
                                        WritableMap map = Arguments.createMap();

                                        map.putString("token", loginResult.getAccessToken().getToken());
                                        map.putString("expiration", String.valueOf(loginResult.getAccessToken().getExpires()));

                                        // TODO:
                                        // figure out a way to return profile as WriteableMap
                                        // or expose method to get current profile
                                        map.putString("profile", me.toString());
                                        map.putString("eventName", "onLogin");

                                        invokeCallback(mLoginCallback, CALLBACK_TYPE_SUCCESS, map);
                                        mLoginCallback = null;
                                    }
                                }
                            }
                    );

                    Bundle parameters = new Bundle();
                    String fields = "id,name,email,first_name,last_name," +
                            "age_range,link,picture,gender,locale,timezone," +
                            "updated_time,verified";
                    parameters.putString("fields", fields);
                    request.setParameters(parameters);
                    request.executeAsync();
                }

                @Override
                public void onCancel() {
                    if (mLoginCallback == null) return;

                    WritableMap map = Arguments.createMap();
                    map.putString("message", "FacebookCallback onCancel event triggered");
                    map.putString("eventName", "onCancel");
                    invokeCallback(mLoginCallback, CALLBACK_TYPE_CANCEL, map);
                    mLoginCallback = null;
                }

                @Override
                public void onError(FacebookException exception) {
                    if (mLoginCallback == null) return;
                    WritableMap map = Arguments.createMap();
                    map.putString("message", exception.getMessage());
                    map.putString("eventName", "onError");
                    invokeCallback(mLoginCallback, CALLBACK_TYPE_ERROR, map);
                    mLoginCallback = null;
                }
            };

        LoginManager.getInstance().registerCallback(mCallbackManager, fbcb);
    }

    private void setupShareDialog() {
        Activity activity = getCurrentActivity();
        if (activity == null) return;

        mShareDialog = new ShareDialog(activity);
        mShareDialog.registerCallback(mCallbackManager, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                Log.d(TAG, "ShareApi.share: onSuccess: result = " + result.toString());
                if (mShareCallback == null) return;

                WritableMap map = Arguments.createMap();
                map.putString("postId", result.getPostId());
                map.putString("message", "ShareDialog success");
                map.putString("eventName", "Share: onSuccess");
                invokeCallback(mShareCallback, CALLBACK_TYPE_SUCCESS, map);
                mShareCallback = null;
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "ShareApi.share: onCancel");
                if (mShareCallback == null) return;

                WritableMap map = Arguments.createMap();
                map.putString("message", "ShareDialog onCancel event triggered");
                map.putString("eventName", "Share: onCancel");
                invokeCallback(mShareCallback, CALLBACK_TYPE_CANCEL, map);
                mShareCallback = null;
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "ShareApi.share: onError: error = " + error.toString());
                error.printStackTrace();
                if (mLoginCallback == null) return;

                WritableMap map = Arguments.createMap();
                map.putString("message", error.getMessage());
                map.putString("eventName", "Share: onError");
                invokeCallback(mShareCallback, CALLBACK_TYPE_ERROR, map);
                mShareCallback = null;
            }
        });
    }

    private void invokeCallback(Callback callback, String type, WritableMap map) {
        if (callback == null) return;

        map.putString("type", type);
        map.putString("provider", "facebook");

        if (type == CALLBACK_TYPE_SUCCESS) {
            callback.invoke(null, map);
        } else {
            callback.invoke(map, null);
        }
    }

    @Override
    public String getName() {
        return "FBLoginManager";
    }

    @ReactMethod
    public void loginWithPermissions(ReadableArray permissions, final Callback callback) {
        Activity activity = getCurrentActivity();
        if (activity == null) return;

        mLoginCallback = callback;
        List<String> _permissions = getPermissions(permissions);
        LoginManager.getInstance().logInWithReadPermissions(activity, _permissions);
    }

    @ReactMethod
    public void logout(final Callback callback) {
        WritableMap map = Arguments.createMap();
        LoginManager.getInstance().logOut();

        map.putString("message", "Facebook Logout executed");
        map.putString("eventName", "onLogout");
        invokeCallback(callback, CALLBACK_TYPE_SUCCESS, map);
    }

    private List<String> getPermissions(ReadableArray permissions) {
        List<String> _permissions = new ArrayList<String>();

        if (permissions != null && permissions.size() > 0) {
            for (int i = 0; i < permissions.size(); i++) {
                if (permissions.getType(i).name() == "String") {
                    String permission = permissions.getString(i);
                    Log.i("FBLoginPermissions", "adding permission: " + permission);
                    _permissions.add(permission);
                }
            }
        }

        return _permissions;
    }

    @ReactMethod
    public void getCurrentToken(final Callback callback) {
        AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
        String tokenString = null;

        if (currentAccessToken != null) {
            tokenString = currentAccessToken.getToken();
        }

        callback.invoke(tokenString);
    }

    @ReactMethod
    public void shareVideo(String uriString, String newsTitle, final Callback callback) {
        mShareCallback = callback;

        // test msg
        Log.d(TAG, "shareVideo2: uriString = " + uriString);
        // "/storage/emulated/0/Movies/Waffle/VID_20160120_152536.mp4"
        // test input

        Uri uri = Uri.parse("file://" + uriString);

        ShareVideo video = new ShareVideo.Builder()
            .setLocalUrl(uri)
            .build();

        ShareVideoContent content = new ShareVideoContent.Builder()
            .setVideo(video)
            .setContentTitle(newsTitle)
            .build();

        if (mShareDialog == null) {
            setupShareDialog();
        }

        if (mShareDialog.canShow(content)) {
          mShareDialog.show(content);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("FacebookLoginModule", "I got here");
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) {

    }
}
