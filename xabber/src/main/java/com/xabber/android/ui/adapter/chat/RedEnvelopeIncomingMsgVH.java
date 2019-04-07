package com.xabber.android.ui.adapter.chat;

import android.view.View;
import android.widget.TextView;

import com.xabber.android.R;
import com.xabber.android.data.database.messagerealm.Attachment;
import com.xabber.android.data.database.messagerealm.MessageItem;
import com.xabber.android.data.database.messagerealm.RedEnvelope;

import io.realm.RealmList;

public class RedEnvelopeIncomingMsgVH extends IncomingMessageVH {

    TextView tvRemaks;

    public RedEnvelopeIncomingMsgVH(View itemView, MessageClickListener messageListener,
                                    MessageLongClickListener longClickListener,
                                    FileListener fileListener, int appearance) {
        super(itemView, messageListener, longClickListener, fileListener, appearance);
    }

    @Override
    public void bind(MessageItem messageItem, MessagesAdapter.MessageExtraData extraData) {
        super.bind(messageItem, extraData);
        messageText.setVisibility(View.GONE);

        redEnvelopeLayout.setVisibility(View.VISIBLE);

        tvRemaks = itemView.findViewById(R.id.tv_remaks);
        View viewRecevice = itemView.findViewById(R.id.view_recevice);
        RealmList<RedEnvelope> redEnvelopes = messageItem.getRedenvelopes();
        if (redEnvelopes.size() == 1) {
            tvRemaks.setText(redEnvelopes.get(0).getRedenvelope_remarks());
            viewRecevice.setVisibility(redEnvelopes.get(0).isRedenvelope_receive() ? View.VISIBLE : View.GONE);
        }
    }
}
