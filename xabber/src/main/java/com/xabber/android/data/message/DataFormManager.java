package com.xabber.android.data.message;

import com.xabber.android.data.database.messagerealm.MessageItem;
import com.xabber.android.data.database.messagerealm.RedEnvelope;
import com.xabber.android.data.extension.httpfileupload.ExtendedFormField;

import org.jivesoftware.smackx.xdata.packet.DataForm;

public  class DataFormManager {

    public static DataForm redEnvelopeDataFrom(MessageItem messageItem) {
        DataForm dataForm = new DataForm(DataForm.Type.form);
        for (RedEnvelope redEnvelope : messageItem.getRedenvelopes()) {
            ExtendedFormField formField = new ExtendedFormField("redenvelope");
            formField.setLabel("redenvelope");
            ExtendedFormField.RedEnvelopeType redEnvelopeType = new ExtendedFormField.RedEnvelopeType("redenvelope", redEnvelope.getRedenvelope_remarks(), redEnvelope.getRedenvelope_money());
            formField.setRedEnvelopeType(redEnvelopeType);
            dataForm.addField(formField);
        }
        return dataForm;
    }

    public static DataForm oldMessageDataFrom(String originalStanzaId,int action) {
        DataForm dataForm = new DataForm(DataForm.Type.form);

            ExtendedFormField formField = new ExtendedFormField("oldmessage");
            formField.setLabel("oldmessage");
            ExtendedFormField.OldMessageAction oldmessage = new ExtendedFormField.OldMessageAction(originalStanzaId,action);
            formField.setOldMessageAction(oldmessage);
            dataForm.addField(formField);
        return dataForm;
    }
}
