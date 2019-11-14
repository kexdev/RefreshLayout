package com.kexdev.andlibs.refreshlayout;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView;
import android.widget.Scroller;

import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.core.view.NestedScrollingChild;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

/**
 * 包含下拉刷新，上拉加载更多，无更多的ViewGroup
 */
public class RefreshLayout extends ViewGroup implements NestedScrollingParent, NestedScrollingChild {

    private static final int MSG_REST_WAITING = 100;
    private static final int MSG_DOWN_COMPLETE = 101;
    private static final int MSG_DOWN_RESET = 102;
    private static final int MSG_UP_COMPLETE = 111;
    private static final int MSG_UP_RESET = 112;
    private static final int MSG_NO_MORE = 123;

    private static final int SCROLL_NONE = -1; //无滚动
    private static final int SCROLL_UP = 0;  //下拉(currY>lastY)
    private static final int SCROLL_DOWN = 1;  //上拉(currY<lastY)
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2F; //滑动阻尼因子

    private static final int DEFAULT_HEADER_FOOTER_HEIGHT_BY_DP = 50;
    private static final int DEFAULT_IGNORE_HEIGHT_BY_DP = 8;
    //private static final int DEFAULT_STEP_BY_DP = 6;

    private static int COMPLETE_WAIT_TIME = 300;
    private static int ANIMATION_EXTEND_DURATION = 200;

    private int mDelta = 50;

    private int mChildHeaderHeight;
    private int mChildFooterHeight;
    private int mChildBottomHeight;

    private int mEffectivePullDownRange;
    private int mEffectivePullUpRange;
    private int mIgnorePullRange;

    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private NestedScrollingChildHelper mNestedScrollingChildHelper;

    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];

    private boolean mPullDownEnable = true; //是否允许下拉刷新
    private boolean mPullUpEnable = true; //是否允许加载更多
    private boolean mEnable = true; //是否允许视图滑动
    private boolean mShowBottom; //是否显示无更多
    private boolean mIsLastScrollComplete; //是否上一次滑动已结束
    private int mDirection;

    private View mTarget;
    private Scroller mScroller;

    private IHeaderWrapper mHeaderView;
    private IFooterWrapper mFooterView;
    private IBottomWrapper mBottomView;

    private int mCurrentState;
    private float mLastY;

    private boolean mRefreshSuccess;
    private boolean mLoadSuccess;

    private OnRefreshListener mRefreshListener;

    public RefreshLayout(Context context) {
        this(context, null);
    }

    public RefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        float density = getContext().getResources().getDisplayMetrics().density;

        mChildHeaderHeight = (int) (density * DEFAULT_HEADER_FOOTER_HEIGHT_BY_DP);
        mChildFooterHeight = (int) (density * DEFAULT_HEADER_FOOTER_HEIGHT_BY_DP);
        mChildBottomHeight = (int) (density * DEFAULT_HEADER_FOOTER_HEIGHT_BY_DP);

        mIgnorePullRange = (int) (density * DEFAULT_IGNORE_HEIGHT_BY_DP);
        mEffectivePullDownRange = mChildHeaderHeight;
        mEffectivePullUpRange = mChildFooterHeight;

        mDelta = Math.max(mChildHeaderHeight, mEffectivePullUpRange) * 3;

        mCurrentState = State.PULL_DOWN_NORMAL;
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        mScroller = new Scroller(getContext(), new LinearInterpolator());

        setNestedScrollingEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        //TODO 这里要做些事件销毁操作
        mHandler.removeMessages(MSG_DOWN_COMPLETE);
        mHandler.removeMessages(MSG_DOWN_RESET);
        mHandler.removeMessages(MSG_UP_COMPLETE);
        mHandler.removeMessages(MSG_UP_RESET);
        mHandler.removeMessages(MSG_NO_MORE);
        mHandler.removeMessages(MSG_REST_WAITING);
    }

    //设置刷新布局
    public void setHeaderView(IHeaderWrapper header) {
        setHeaderView(header, 0);
    }

    public void setHeaderView(IHeaderWrapper header, int height) {
        this.mHeaderView = header;

        View view = (View) mHeaderView;
        if (height == 0) {
            view.measure(0, 0);
            this.mChildHeaderHeight = view.getMeasuredHeight();
        } else {
            this.mChildHeaderHeight = height;
        }
        addView(view);

        mEffectivePullDownRange = mChildHeaderHeight;
    }

    //设置加载更多布局
    public void setFooterView(IFooterWrapper footer) {
        setFooterView(footer, 0);
    }

    public void setFooterView(IFooterWrapper footer, int height) {
        this.mFooterView = footer;

        View view = (View) mFooterView;
        if (height == 0) {
            view.measure(0, 0);
            this.mChildFooterHeight = view.getMeasuredHeight();
        } else {
            this.mChildFooterHeight = height;
        }
        addView(view);

        mEffectivePullUpRange = mChildFooterHeight;
    }

    //设置加载完成布局
    public void setBottomView(IBottomWrapper bottom) {
        setBottomView(bottom, 0);
    }

    public void setBottomView(IBottomWrapper bottom, int height) {
        this.mBottomView = bottom;

        View view = (View) mBottomView;
        if (height == 0) {
            view.measure(0, 0);
            this.mChildBottomHeight = view.getMeasuredHeight();
        } else {
            this.mChildBottomHeight = height;
        }
        addView(view);
    }

    public void showNoMore(boolean noMore) {
        //Handler是为了让上拉回弹先走完，再显示BottomView;
        this.mShowBottom = noMore;
        if (mShowBottom && ((mCurrentState != State.PULL_DOWN_FINISH && mCurrentState != State.PULL_UP_FINISH) || getScrollY() != 0)) {
            mHandler.sendEmptyMessageDelayed(MSG_NO_MORE, 5);
            return;
        }
        if (mBottomView != null) {
            ((View) mBottomView).setVisibility(mShowBottom ? VISIBLE : GONE);
        }
        if (mFooterView != null) {
            ((View) mFooterView).setVisibility(mShowBottom ? GONE : VISIBLE);
        }
    }

    public void setViewHeight(int height) {
        this.mChildHeaderHeight = height;
    }

    public void setRefreshAnimationDuration(int duration) {
        ANIMATION_EXTEND_DURATION = duration;
    }

    public void setCompleteWaitTime(int waitTime) {
        COMPLETE_WAIT_TIME = waitTime;
    }

    public void setEnable(boolean enable) {
        this.mEnable = enable;
    }

    public void setEffectivePullDownRange(int effectivePullDownRange) {
        this.mEffectivePullDownRange = effectivePullDownRange;
    }

    public void setEffectivePullUpRange(int effectivePullUpRange) {
        this.mEffectivePullUpRange = effectivePullUpRange;
    }

    public void setChildHeaderHeight(int childHeaderHeight) {
        this.mChildHeaderHeight = childHeaderHeight;
    }

    public void setChildFooterHeight(int childFooterHeight) {
        this.mChildFooterHeight = childFooterHeight;
    }

    public void setChildBottomHeight(int childBottomHeight) {
        this.mChildBottomHeight = childBottomHeight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mHeaderView) {
                child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(mChildHeaderHeight, MeasureSpec.EXACTLY));
            } else if (child == mFooterView) {
                child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(mChildFooterHeight, MeasureSpec.EXACTLY));
            } else if (child == mBottomView) {
                child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(mChildBottomHeight, MeasureSpec.EXACTLY));
            } else {
                child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mHeaderView) {
                child.layout(0, -child.getMeasuredHeight(), child.getMeasuredWidth(), 0);
            } else if (child == mFooterView) {
                child.layout(0, getMeasuredHeight(), child.getMeasuredWidth(), getMeasuredHeight() + child.getMeasuredHeight());
            } else if (child == mBottomView) {
                child.layout(0, getMeasuredHeight(), child.getMeasuredWidth(), getMeasuredHeight() + child.getMeasuredHeight());
            } else {
                child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + child.getMeasuredWidth(), getMeasuredHeight() - getPaddingBottom());
            }
        }
    }

    private void ensureTarget() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != mHeaderView && child != mFooterView && child != mBottomView) {
                mTarget = child;
                break;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean intercept = false;
        float y = event.getY();
        mDirection = y > mLastY ? SCROLL_UP : SCROLL_DOWN;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                intercept = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget)) {
                    if (y > mLastY) {//上滑
                        intercept = !canChildScrollUp();
                    } else if (y < mLastY) {
                        intercept = !canChildScrollDown();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                intercept = false;
                mDirection = SCROLL_NONE;
                break;
        }
        mLastY = y;
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnable) {
            return true;
        }

        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(getScrollY()) > mIgnorePullRange) {
                    requestDisallowInterceptTouchEvent(true);
                }
                doScroll((int) (mLastY - y));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onStopScroll();
                requestDisallowInterceptTouchEvent(false);
                break;
        }
        mLastY = y;
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // if this is a List < L or another mRootView that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if ((Build.VERSION.SDK_INT < 21 && mTarget instanceof AbsListView)
                || (mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget))) {
            // Nope.
        } else {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private boolean canChildScrollDown() {
        return ViewCompat.canScrollVertically(mTarget, 1);
    }

    private boolean canChildScrollUp() {
        return ViewCompat.canScrollVertically(mTarget, -1);
    }

    private void doScroll(int dy) {
        if (!mEnable) {
            return;
        }

        if (dy > 0) {
            //上拉加载
            if (mShowBottom) {
                //显示无更多布局
                if (mBottomView != null) {
                    ((View) mBottomView).setVisibility(VISIBLE);
                }
                if (mFooterView != null) {
                    ((View) mFooterView).setVisibility(GONE);
                }
                if (getScrollY() < 0) { //下拉过程中的上拉，无效上拉
                    if (Math.abs(getScrollY()) < mEffectivePullDownRange) {
                        if (mCurrentState != State.PULL_DOWN) {
                            updateStatus(State.PULL_DOWN);
                        }
                    }
                } else {
                    if (!mPullUpEnable) {
                        return;
                    }
                    int bHeight = 0;
                    if (mBottomView != null) {
                        bHeight = ((View) mBottomView).getMeasuredHeight();
                    }
                    if (Math.abs(getScrollY()) >= bHeight) {
                        return;
                    }
                    dy /= computeInterpolationFactor(getScrollY());
                    updateStatus(State.BOTTOM);
                }
            } else {
                if (mBottomView != null) {
                    ((View) mBottomView).setVisibility(GONE);
                }
                if (mFooterView != null) {
                    ((View) mFooterView).setVisibility(VISIBLE);
                }
                if (getScrollY() < 0) { //下拉过程中的上拉，无效上拉
                    if (Math.abs(getScrollY()) < mEffectivePullDownRange) {
                        if (mCurrentState != State.PULL_DOWN) {
                            updateStatus(State.PULL_DOWN);
                        }
                    }
                } else {
                    if (!mPullUpEnable) {
                        return;
                    }
                    if (Math.abs(getScrollY()) >= mEffectivePullUpRange) {
                        dy /= computeInterpolationFactor(getScrollY());
                        if (mCurrentState != State.PULL_UP_RELEASABLE) {
                            updateStatus(State.PULL_UP_RELEASABLE);
                        }
                    } else {
                        if (mCurrentState != State.PULL_UP) {
                            updateStatus(State.PULL_UP);
                        }
                    }
                }
            }
        } else {
            //下拉刷新
            if (getScrollY() > 0) {   //说明不是到达顶部的下拉，无效下拉
                if (Math.abs(getScrollY()) < mEffectivePullUpRange) {
                    if (mCurrentState != State.PULL_UP) {
                        updateStatus(State.PULL_UP);
                    }
                }
            } else {
                if (!mPullDownEnable) {
                    return;
                }
                if (Math.abs(getScrollY()) >= mEffectivePullDownRange) {
                    //到达下拉最大距离，增加阻尼因子
                    dy /= computeInterpolationFactor(getScrollY());
                    if (mCurrentState != State.PULL_DOWN_RELEASABLE) {
                        updateStatus(State.PULL_DOWN_RELEASABLE);
                    }
                } else {
                    if (mCurrentState != State.PULL_DOWN) {
                        updateStatus(State.PULL_DOWN);
                    }
                }
            }
        }

        dy /= DECELERATE_INTERPOLATION_FACTOR;
        scrollBy(0, dy);
    }

    private void onStopScroll() {
        if (mShowBottom && getScrollY() > 0) { //显示底部布局
            updateStatus(State.BOTTOM);
            if (Math.abs(getScrollY()) != 0) {
                mScroller.startScroll(0, getScrollY(), 0, -getScrollY());
                mScroller.extendDuration(ANIMATION_EXTEND_DURATION);
                invalidate();
            }
        } else {
            if ((Math.abs(getScrollY()) >= mEffectivePullDownRange) && getScrollY() < 0) {//有效的滑动距离
                updateStatus(State.PULL_DOWN_RELEASE);
                mScroller.startScroll(0, getScrollY(), 0, -(getScrollY() + mEffectivePullDownRange));
                mScroller.extendDuration(ANIMATION_EXTEND_DURATION);
                invalidate();
            } else if ((Math.abs(getScrollY()) >= mEffectivePullUpRange) && getScrollY() > 0) {
                updateStatus(State.PULL_UP_RELEASE);
                mScroller.startScroll(0, getScrollY(), 0, -(getScrollY() - mEffectivePullUpRange));
                mScroller.extendDuration(ANIMATION_EXTEND_DURATION);
                invalidate();
            } else {
                updateStatus(State.PULL_DOWN_NORMAL);
            }
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mIsLastScrollComplete = false;
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            invalidate();
        } else {
            mIsLastScrollComplete = true;
            if (mCurrentState == State.PULL_DOWN_NORMAL) {
                mCurrentState = State.PULL_DOWN_FINISH;
            }
            if (mCurrentState == State.PULL_UP_NORMAL) {
                mCurrentState = State.PULL_UP_FINISH;
            }
        }
    }

    private void updateStatus(int state) {
        switch (state) {
            case State.PULL_DOWN_NORMAL:
                pullDownReset();
                break;
            case State.PULL_DOWN:
                if (mHeaderView != null) {
                    mHeaderView.pullDown();
                }
                break;
            case State.PULL_DOWN_RELEASABLE:
                if (mHeaderView != null) {
                    mHeaderView.pullDownReleasable();
                }
                break;
            case State.PULL_DOWN_RELEASE:
                if (mHeaderView != null) {
                    mHeaderView.pullDownRelease();
                }
                if (mRefreshListener != null) {
                    mRefreshListener.onRefresh();
                }
                showNoMore(false);
                setEnable(false);
                break;
            case State.PULL_DOWN_RESET:
                if (mHeaderView != null) {
                    mHeaderView.pullDownComplete(mRefreshSuccess);
                }
                break;

            case State.PULL_UP_NORMAL:
                pullUpReset();
                break;
            case State.PULL_UP:
                if (mFooterView != null) {
                    mFooterView.pullUp();
                }
                break;
            case State.PULL_UP_RELEASABLE:
                if (mFooterView != null) {
                    mFooterView.pullUpReleasable();
                }
                break;
            case State.PULL_UP_RELEASE:
                if (mFooterView != null) {
                    mFooterView.pullUpRelease();
                }
                if (mRefreshListener != null) {
                    mRefreshListener.onLoadMore();
                }
                setEnable(false);
                break;
            case State.PULL_UP_RESET:
                if (mFooterView != null) {
                    mFooterView.pullUpComplete(mLoadSuccess);
                }
                break;
            case State.BOTTOM:
                if (mBottomView != null) {
                    mBottomView.showBottom();
                }
                break;
        }

        mCurrentState = state;
    }

    private void pullUpReset() {
        mHandler.sendEmptyMessageDelayed(MSG_REST_WAITING, ANIMATION_EXTEND_DURATION);
        if (Math.abs(getScrollY()) != 0) {
            mScroller.startScroll(0, getScrollY(), 0, -getScrollY());
            mScroller.extendDuration(ANIMATION_EXTEND_DURATION);
            invalidate();  //触发onDraw()
        }
    }

    @SuppressWarnings("Handlerleak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_DOWN_COMPLETE:
                    onRefreshReset();
                    break;
                case MSG_DOWN_RESET:
                    if (!mIsLastScrollComplete) {
                        mHandler.sendEmptyMessageDelayed(MSG_DOWN_RESET, 5);
                    } else {
                        onRefreshReset();
                    }
                    break;
                case MSG_UP_COMPLETE:
                    onLoadMoreRest();
                    break;
                case MSG_UP_RESET:
                    if (!mIsLastScrollComplete) {
                        mHandler.sendEmptyMessageDelayed(MSG_UP_RESET, 5);
                    } else {
                        onLoadMoreRest();
                    }
                    break;
                case MSG_NO_MORE:
                    if (getScrollY() == 0 && (mCurrentState == State.PULL_DOWN_FINISH || mCurrentState == State.PULL_UP_FINISH)) {
                        showNoMore(mShowBottom);
                    } else {
                        mHandler.sendEmptyMessageDelayed(MSG_NO_MORE, 5);
                    }
                    break;
                case MSG_REST_WAITING:
                    setEnable(true);
                    break;
            }
        }
    };

    private void pullDownReset() {
        mHandler.sendEmptyMessageDelayed(MSG_REST_WAITING, ANIMATION_EXTEND_DURATION);
        if (Math.abs(getScrollY()) != 0) {
            mScroller.startScroll(0, getScrollY(), 0, -getScrollY());
            mScroller.extendDuration(ANIMATION_EXTEND_DURATION);
            invalidate();
        }
    }

    private float computeInterpolationFactor(int dy) {
        int absY = Math.abs(dy);
        int delta;
        if (dy > 0) {
            if (absY <= mEffectivePullUpRange) {
                return DECELERATE_INTERPOLATION_FACTOR;
            }
            delta = (absY - mEffectivePullUpRange) / mDelta;  //增加DELTA_PARAM，阻尼系数+1
        } else {
            if (absY <= mEffectivePullDownRange) {
                return DECELERATE_INTERPOLATION_FACTOR;
            }
            delta = (absY - mEffectivePullDownRange) / mDelta;  //增加DELTA_PARAM，阻尼系数+1
        }

        return DECELERATE_INTERPOLATION_FACTOR + delta;
    }

    public void onRefreshComplete(boolean isSuccess) {
        mHandler.sendEmptyMessageDelayed(MSG_DOWN_COMPLETE, COMPLETE_WAIT_TIME);
        mRefreshSuccess = isSuccess;
        updateStatus(State.PULL_DOWN_RESET);
    }

    public void onRefreshReset() {
        if (!mIsLastScrollComplete) {
            mHandler.sendEmptyMessageDelayed(MSG_DOWN_RESET, 5);
            return;
        }
        updateStatus(State.PULL_DOWN_NORMAL);
    }

    public void onLoadMoreComplete(boolean isSuccess) {
        mHandler.sendEmptyMessageDelayed(MSG_UP_COMPLETE, COMPLETE_WAIT_TIME);
        mLoadSuccess = isSuccess;
        updateStatus(State.PULL_UP_RESET);
    }

    public void onLoadMoreRest() {
        if (!mIsLastScrollComplete) {
            mHandler.sendEmptyMessageDelayed(MSG_UP_RESET, 5);
            return;
        }
        updateStatus(State.PULL_UP_NORMAL);
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mRefreshListener = listener;
    }

    public void setPullDownEnable(boolean pullDownEnable) {
        this.mPullDownEnable = pullDownEnable;
    }

    public void setPullUpEnable(boolean pullUpEnable) {
        this.mPullUpEnable = pullUpEnable;
    }

    public void setScrollEnable(boolean enable) {
        this.mEnable = enable;
    }

    public boolean isPullDownEnable() {
        return mPullDownEnable;
    }

    public boolean isPullUpEnable() {
        return mPullUpEnable;
    }

    public boolean isScrollEnable() {
        return mEnable;
    }

    //--------------------  NestedScrollParent  -------------------------------

    // 子控件滑动，看父控件是否一起滑动（true 一起滑动，false 父控件不滑动）
    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return mEnable && isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    // 通过ParentHelper表明该次滑动是否被接受了
    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);

        //告诉父类开始滑动
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
    }

    // 子控件把要滑动的距离dy给父控件，父控件计算下自己要滑动多少，并传给子控件
    // 父控件会把自己消耗的距离通过cousumed[]回传给子控件
    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (getScrollY() != 0) { //只有在自己滑动的情形下才进行预消耗
            if (!mIsLastScrollComplete) {
                return;
            }

            //这里相当于做了一个边界条件
            if (getScrollY() > 0 && dy < 0 && Math.abs(dy) >= Math.abs(getScrollY())) {  //上拉过程中下拉
                consumed[1] = getScrollY();
                scrollTo(0, 0);
                return;
            }

            if (getScrollY() < 0 && dy > 0 && Math.abs(dy) >= Math.abs(getScrollY())) {
                consumed[1] = getScrollY();
                scrollTo(0, 0);
                return;
            }

            int yConsumed = Math.abs(dy) >= Math.abs(getScrollY()) ? getScrollY() : dy;
            doScroll(yConsumed);
            consumed[1] = yConsumed;
        }

        //父类消耗剩余距离
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    // 父控件接收到子控件消耗之后剩余的距离，并消耗掉剩余的距离
    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);
        int dy = dyUnconsumed + mParentOffsetInWindow[1];

        if (mEnable) {
            if (!mIsLastScrollComplete) {
                return;
            }

            //用户不开启加载
            if (mDirection == SCROLL_DOWN && !mPullUpEnable) {
                return;
            }

            //用户不开启下拉
            if (mDirection == SCROLL_UP && !mPullDownEnable) {
                return;
            }

            doScroll(dy);
        }
    }

    // 结束嵌套滑动过程
    @Override
    public void onStopNestedScroll(View child) {
        onStopScroll();
        mNestedScrollingParentHelper.onStopNestedScroll(child);

        stopNestedScroll();
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    //------------------------------ NestedScrollChild ---------------------//


    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable @Size(value = 2) int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable @Size(value = 2) int[] consumed, @Nullable @Size(value = 2) int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    private interface State {
        int PULL_DOWN_NORMAL = 0;  //下拉恢复正常或正常
        int PULL_DOWN = 1;  //下拉中
        int PULL_DOWN_RELEASABLE = 2;  //下拉可刷新
        int PULL_DOWN_RELEASE = 3;  //下拉正在刷新
        int PULL_DOWN_RESET = 4;  //下拉恢复正常
        int PULL_DOWN_FINISH = 5;  //下拉完成

        int PULL_UP_NORMAL = 6;  //上拉恢复正常
        int PULL_UP = 7;  //上拉中
        int PULL_UP_RELEASABLE = 8;  //上拉可刷新
        int PULL_UP_RELEASE = 9;  //上拉正在刷新
        int PULL_UP_RESET = 10;  //上拉恢复正常
        int PULL_UP_FINISH = 11;  //上拉完成

        int BOTTOM = 13; //无更多
    }


    public interface OnRefreshListener {
        void onRefresh();

        void onLoadMore();
    }

}
