package com.kexdev.andlibs.refreshlayout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

/**
 * RefreshLayout无更多view
 */
public class BottomView extends LinearLayout implements IBottomWrapper {

    public BottomView(Context context) {
        this(context, null);
    }

    public BottomView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.view_refresh_bottom, this, false);

        /*
        setOrientation(VERTICAL);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        addView(mRootView, params);
        */
        addView(view);
    }

    @Override
    public View getBottomView() {
        return this;
    }

    @Override
    public void showBottom() {

    }
}
