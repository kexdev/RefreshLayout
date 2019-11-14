package com.kexdev.andlibs.refreshlayout.demo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kexdev.andlibs.refreshlayout.RefreshLayout;
import com.kexdev.andlibs.refreshlayout.RefreshView;

/**
 * 示例 - 嵌套WebView
 */
public class WebActivity extends AppCompatActivity {

    public static final String EXTRA_KEY_URL = "url";

    private RefreshLayout mRefreshLayout;
    private WebView mWebView;

    public static void startActivity(Context context, @NonNull String url) {
        Intent intent = new Intent(context, WebActivity.class);
        intent.putExtra(EXTRA_KEY_URL, url);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        String url = getIntent().getStringExtra(EXTRA_KEY_URL);
        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }

        initRefreshLayout();
        initWebView();
        loadUrl(url);
    }

    private void loadUrl(String url) {
        mWebView.loadUrl(url);
    }

    private void initRefreshLayout() {
        mRefreshLayout = findViewById(R.id.refresh_layout);

        mRefreshLayout.setPullDownEnable(true);
        mRefreshLayout.setPullUpEnable(false);
        mRefreshLayout.setHeaderView(new RefreshView(getApplicationContext()));

        mRefreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // 处理下拉刷新事件
                if (mWebView != null) {
                    mWebView.reload();
                }
            }

            @Override
            public void onLoadMore() {
                // 处理上拉加载更多事件
            }
        });
    }

    private void initWebView() {
        mWebView = findViewById(R.id.web_view);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                //加载失败
                mRefreshLayout.onRefreshComplete(false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                //加载成功
                mRefreshLayout.onRefreshComplete(true);
            }
        });
        initWebSettings();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebSettings() {
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        // 设置缩放控制
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true); //Build.VERSION.SDK_INT >= 14
        webSettings.setSupportZoom(true);
        webSettings.setDisplayZoomControls(false);
        // 设置文件、数据访问
        webSettings.setAllowFileAccess(false);// 设置允许访问文件数据
        webSettings.setAllowFileAccessFromFileURLs(true);// 设置通过file URL对http域进行访问
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        // 设置缓存
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        //webSettings.setAppCachePath(getActivity().getExternalCacheDir().getAbsolutePath() + "/.webcache");
        // 同时多开窗口
        webSettings.setSupportMultipleWindows(false);
        webSettings.setLoadWithOverviewMode(true);
        // 支持通过js打开新的窗口
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        // 支持http和https链接混合
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

}
