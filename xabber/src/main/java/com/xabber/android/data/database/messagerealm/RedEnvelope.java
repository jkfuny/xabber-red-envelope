package com.xabber.android.data.database.messagerealm;

import android.support.annotation.Nullable;

import java.util.UUID;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class RedEnvelope extends RealmObject {

    public static class Fields {
        public static final String UNIQUE_ID = "uniqueId";
        public static final String RED_ENVELOPE_MONEY = "redenvelope_money";
        public static final String RED_ENVELOPE_REMARKS = "redenvelope_remarks";
        public static final String RED_ENVELOPE_RECEIVE = "redenvelope_receive";
    }

    @PrimaryKey
    @Required
    private String uniqueId;
    private String redenvelope_money;
    private String redenvelope_remarks;
    private boolean redenvelope_receive;


    public RedEnvelope() {
        this.uniqueId = UUID.randomUUID().toString();
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getRedenvelope_money() {
        return redenvelope_money;
    }

    public void setRedenvelope_money(String redenvelope_money) {
        this.redenvelope_money = redenvelope_money;
    }

    public String getRedenvelope_remarks() {
        return redenvelope_remarks;
    }

    public void setRedenvelope_remarks(String redenvelope_remarks) {
        this.redenvelope_remarks = redenvelope_remarks;
    }

    public boolean isRedenvelope_receive() {
        return redenvelope_receive;
    }

    public void setRedenvelope_receive(boolean redenvelope_receive) {
        this.redenvelope_receive = redenvelope_receive;
    }
}
