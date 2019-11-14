package com.kexdev.andlibs.refreshlayout.demo;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mListRefreshButton;
    private Button mViewRefreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mListRefreshButton = findViewById(R.id.test_list_button);
        mListRefreshButton.setOnClickListener(this);

        mViewRefreshButton = findViewById(R.id.test_webview_button);
        mViewRefreshButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mListRefreshButton) {
            ListActivity.startActivity(this);
        } else if (v == mViewRefreshButton) {
            WebActivity.startActivity(this, "https://www.so.com");
        }
    }
}
