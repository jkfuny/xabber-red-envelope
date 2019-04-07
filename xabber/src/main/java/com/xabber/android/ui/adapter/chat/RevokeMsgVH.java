package com.xabber.android.ui.adapter.chat;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.xabber.android.R;

public class RevokeMsgVH extends BasicMessageVH {

    public RevokeMsgVH(@NonNull View itemView) {
        super(itemView);
    }

    public void setUser(boolean self) {
        messageText.setText(self ? "撤回了一条消息" : "对方撤回了一条消息");
    }
}
