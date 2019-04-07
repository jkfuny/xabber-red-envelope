package com.xabber.android.data.message;

import com.xabber.android.data.database.messagerealm.RedEnvelope;
import com.xabber.android.data.extension.httpfileupload.ExtendedFormField;

import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import java.util.List;


public class OldMessageManager {
    public static ExtendedFormField.OldMessageAction parseOldMessageAction(Stanza packet) {

        DataForm dataForm = DataForm.from(packet);
        if (dataForm != null) {
            List<FormField> fields = dataForm.getFields();
            for (FormField field : fields) {
                if (field instanceof ExtendedFormField) {
                    ExtendedFormField.OldMessageAction oldMessageAction = ((ExtendedFormField)field).getOldMessageAction();
                   return oldMessageAction;
                }
            }
        }
        return null;
    }
}
