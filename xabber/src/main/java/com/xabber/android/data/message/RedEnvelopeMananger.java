package com.xabber.android.data.message;

import com.xabber.android.data.database.messagerealm.Attachment;
import com.xabber.android.data.database.messagerealm.RedEnvelope;
import com.xabber.android.data.extension.file.FileManager;
import com.xabber.android.data.extension.httpfileupload.ExtendedFormField;
import com.xabber.android.data.log.LogManager;

import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import java.util.List;

import io.realm.RealmList;

public class RedEnvelopeMananger {

    public static RealmList<RedEnvelope> parseRedEnvelopeMessage(Stanza packet) {
        RealmList<RedEnvelope> redEnvelopes = new RealmList<>();

        DataForm dataForm = DataForm.from(packet);
        if (dataForm != null) {

            List<FormField> fields = dataForm.getFields();
            for (FormField field : fields) {
                if (field instanceof ExtendedFormField) {
                    ExtendedFormField.RedEnvelopeType redEnvelopeType = ((ExtendedFormField)field).getRedEnvelopeType();
                    if(redEnvelopeType!=null) {
                        RedEnvelope redEnvelope = new RedEnvelope();
                        redEnvelope.setRedenvelope_money(redEnvelopeType.getMoney());
                        redEnvelope.setRedenvelope_remarks(redEnvelopeType.getRemarks());
                        redEnvelopes.add(redEnvelope);
                    }
                }
            }
        }
        return redEnvelopes;
    }

}
