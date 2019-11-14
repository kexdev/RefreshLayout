package com.kexdev.andlibs.refreshlayout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * RefreshLayout下拉刷新view
 */
public class RefreshView extends LinearLayout implements IHeaderWrapper {

    private ImageView mImageViewIcon;
    private TextView mTextViewMsg;

    private Animation mRotateAnim;

    public RefreshView(Context context) {
        this(context, null);
    }

    public RefreshView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RefreshView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.view_refresh_header, this, false);
        mImageViewIcon = (ImageView) view.findViewById(R.id.icon_image);
        mTextViewMsg = (TextView) view.findViewById(R.id.msg_text);

        /*
        setOrientation(VERTICAL);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        addView(mRootView, params);
        */
        addView(view);
    }

    @Override
    public View getHeaderView() {
        return this;
    }

    @Override
    public void pullDown() {
        mImageViewIcon.setImageResource(R.drawable.ic_refresh_view_arrow_down);
        mTextViewMsg.setText(R.string.refresh_view_header_normal);
    }

    @Override
    public void pullDownReleasable() {
        mImageViewIcon.setImageResource(R.drawable.ic_refresh_view_arrow_up);
        mTextViewMsg.setText(R.string.refresh_view_header_ready);
    }

    @Override
    public void pullDownRelease() {
        mImageViewIcon.setImageResource(R.drawable.ic_refresh_view_loading);
        mTextViewMsg.setText(R.string.refresh_view_header_loading);

        // 加载动画
        mRotateAnim = AnimationUtils.loadAnimation(getContext(), R.anim.anim_rotate);
        mImageViewIcon.setAnimation(mRotateAnim);
    }

    @Override
    public void pullDownComplete(boolean isSuccess) {
        if (mRotateAnim != null) {
            mRotateAnim.cancel();
            mRotateAnim = null;
        }

        if (isSuccess) {
            mImageViewIcon.setImageResource(R.drawable.ic_refresh_view_ok);
            mTextViewMsg.setText(R.string.refresh_view_header_finish);
        } else {
            mImageViewIcon.setImageResource(R.drawable.ic_refresh_view_no_data);
            mTextViewMsg.setText(R.string.refresh_view_header_fail);
        }
    }
}
