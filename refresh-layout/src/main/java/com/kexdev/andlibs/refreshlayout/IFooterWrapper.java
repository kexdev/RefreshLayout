package com.kexdev.andlibs.refreshlayout;

import android.view.View;

public interface IFooterWrapper {

    /**
     * 获取加载更多布局
     *
     * @return
     */
    View getFooterView();

    /**
     * 上拉中
     */
    void pullUp();

    /**
     * 上拉可释放
     */
    void pullUpReleasable();

    /**
     * 上拉已释放
     */
    void pullUpRelease();

    /**
     * 上拉完成
     * @param isSuccess
     */
    void pullUpComplete(boolean isSuccess);
}
