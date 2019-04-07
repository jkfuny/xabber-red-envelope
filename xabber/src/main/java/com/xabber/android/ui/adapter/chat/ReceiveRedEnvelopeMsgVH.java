package com.xabber.android.ui.adapter.chat;

import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.View;

import com.xabber.android.R;

public class ReceiveRedEnvelopeMsgVH extends BasicMessageVH {

    public ReceiveRedEnvelopeMsgVH(@NonNull View itemView) {
        super(itemView);
    }

    public void setMessage(String message) {
        messageText.setText(message);
        messageText.setBackgroundColor(ContextCompat.getColor(messageText.getContext(), R.color.red_600));
    }
}
