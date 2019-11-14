package com.kexdev.andlibs.refreshlayout.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kexdev.andlibs.refreshlayout.BottomView;
import com.kexdev.andlibs.refreshlayout.LoadView;
import com.kexdev.andlibs.refreshlayout.RefreshLayout;
import com.kexdev.andlibs.refreshlayout.RefreshView;

import java.util.ArrayList;
import java.util.List;

/**
 * 示例 - 嵌套列表
 */
public class ListActivity extends AppCompatActivity {

    private static final int PAGE_FIRST_INDEX = 100;
    private static final int PAGE_SIZE = 20;

    private RefreshLayout mRefreshLayout;
    private RecyclerView mRecyclerView;
    private DataAdapter mDataAdapter;

    private List<DataBean> mDataList = new ArrayList<>();

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, ListActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        initRefreshLayout();
        initRecyclerView();
    }

    private void initRefreshLayout() {
        mRefreshLayout = findViewById(R.id.refresh_layout);

        mRefreshLayout.setPullDownEnable(true);
        mRefreshLayout.setPullUpEnable(true);
        mRefreshLayout.showNoMore(false);
        mRefreshLayout.setHeaderView(new RefreshView(getApplicationContext()));
        mRefreshLayout.setFooterView(new LoadView(getApplicationContext()));
        mRefreshLayout.setBottomView(new BottomView(getApplicationContext()));

        mRefreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // 处理下拉刷新事件
                refreshData();
            }

            @Override
            public void onLoadMore() {
                // 处理上拉加载更多事件
                loadMoreData();
            }
        });
    }

    private void initRecyclerView() {
        mDataList.addAll(createData(PAGE_FIRST_INDEX, PAGE_SIZE));
        mDataAdapter = new DataAdapter(mDataList);

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        mRecyclerView.setAdapter(mDataAdapter);
    }

    private void refreshData() {
        mRefreshLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mDataList.size() > 0) {
                    DataBean bean = mDataList.get(0);
                    if (bean.id > 0) {
                        List<DataBean> dataList = createData(bean.id - PAGE_SIZE, PAGE_SIZE);
                        mDataList.addAll(0, dataList);
                        mDataAdapter.notifyDataSetChanged();
                        //加载成功
                        mRefreshLayout.onRefreshComplete(true);
                    } else {
                        //加载失败
                        mRefreshLayout.onRefreshComplete(false);
                        mRefreshLayout.setPullDownEnable(true);
                        mRefreshLayout.showNoMore(true);
                    }
                }
            }
        }, 2000);
    }

    private void loadMoreData() {
        mRefreshLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mDataList.size() > 0) {
                    DataBean bean = mDataList.get(mDataList.size() - 1);
                    if (bean.id < 200 - 1) {
                        List<DataBean> dataList = createData(bean.id + 1, PAGE_SIZE);
                        mDataList.addAll(dataList);
                        mDataAdapter.notifyDataSetChanged();
                        //加载成功
                        mRefreshLayout.onLoadMoreComplete(true);
                    } else {
                        //加载失败
                        mRefreshLayout.onLoadMoreComplete(false);
                        //mRefreshLayout.setPullUpEnable(false);
                        mRefreshLayout.showNoMore(true);
                    }
                }
            }
        }, 2000);
    }

    private List<DataBean> createData(int firstId, int count) {
        List<DataBean> dataList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DataBean bean = new DataBean();
            bean.id = firstId + i;
            bean.name = "item " + (bean.id + 1);
            dataList.add(bean);
        }
        return dataList;
    }

    public class DataBean {
        public int id;
        public String name;
    }

    public class DataViewHolder extends RecyclerView.ViewHolder {
        private TextView mItemNameView;

        public DataViewHolder(@NonNull View itemView) {
            super(itemView);
            mItemNameView = itemView.findViewById(R.id.item_name);
        }

        public TextView getItemNameView() {
            return mItemNameView;
        }
    }

    public class DataAdapter extends RecyclerView.Adapter<DataViewHolder> {
        private List<DataBean> mDataList;

        public DataAdapter(List<DataBean> dataList) {
            mDataList = dataList;
        }

        @NonNull
        @Override
        public DataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_activity, parent, false);
            return new DataViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DataViewHolder holder, int position) {
            DataBean bean = mDataList.get(position);
            holder.getItemNameView().setText(bean.name);
        }

        @Override
        public int getItemCount() {
            return mDataList.size();
        }
    }

}
