package com.xabber.android.data.extension.httpfileupload;

import android.text.TextUtils;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.NamedElement;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.xdata.FormField;

public class ExtendedFormField extends FormField {

    private Media media;
    private RedEnvelopeType redEnvelopeType;

    private OldMessageAction oldMessageAction;

    public ExtendedFormField() {
        super();
    }

    public ExtendedFormField(String variable) {
        super(variable);
    }

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media;
    }

    public RedEnvelopeType getRedEnvelopeType() {
        return redEnvelopeType;
    }

    public void setRedEnvelopeType(RedEnvelopeType redEnvelopeType) {
        this.redEnvelopeType = redEnvelopeType;
    }

    public OldMessageAction getOldMessageAction() {
        return oldMessageAction;
    }

    public void setOldMessageAction(OldMessageAction oldMessageAction) {
        this.oldMessageAction = oldMessageAction;
    }

    @Override
    public XmlStringBuilder toXML() {
        XmlStringBuilder buf = new XmlStringBuilder(this);
        // Add attributes
        buf.optAttribute("label", getLabel());
        buf.optAttribute("var", getVariable());
        buf.optAttribute("type", getType());
        buf.rightAngleBracket();
        // Add elements
        buf.optElement("desc", getDescription());
        buf.condEmptyElement(isRequired(), "required");
        // Loop through all the values and append them to the string buffer
        for (String value : getValues()) {
            buf.element("value", value);
        }
        // Loop through all the values and append them to the string buffer
        for (Option option : getOptions()) {
            buf.append(option.toXML());
        }

        if (media != null)
            buf.append(media.toXML());

        if (redEnvelopeType != null) {
            buf.append(redEnvelopeType.toXML());
        }

        if(oldMessageAction!=null){
            buf.append(oldMessageAction.toXML());
        }
        buf.closeElement(this);
        return buf;
    }

    public static class Uri implements NamedElement {

        public static final String ELEMENT = "uri";

        private String type;
        private String uri;
        private long size;
        private long duration;

        public Uri(String type, String uri) {
            this.type = type;
            this.uri = uri;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public String getType() {
            return type;
        }

        public String getUri() {
            return uri;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public CharSequence toXML() {
            XmlStringBuilder xml = new XmlStringBuilder(this);
            // Add attribute
            xml.optAttribute("type", getType());
            xml.optAttribute("size", String.valueOf(getSize()));
            xml.optAttribute("duration", String.valueOf(getDuration()));
            xml.rightAngleBracket();

            xml.append(getUri());

            xml.closeElement(this);
            return xml;
        }
    }

    public static class Media implements ExtensionElement {

        public static final String ELEMENT = "media";
        public static final String NAMESPACE = "urn:xmpp:media-element";

        private String height;
        private String width;
        private Uri uri;


        public Media(String height, String width, Uri uri) {
            this.height = height;
            this.width = width;
            this.uri = uri;
        }

        public String getHeight() {
            return height;
        }

        public String getWidth() {
            return width;
        }

        public Uri getUri() {
            return uri;
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public CharSequence toXML() {
            XmlStringBuilder xml = new XmlStringBuilder(this);
            // Add attribute
            if (!TextUtils.isEmpty(getHeight()))
                xml.optAttribute("height", getHeight());
            if (!TextUtils.isEmpty(getWidth()))
                xml.optAttribute("width", getWidth());


            xml.rightAngleBracket();

            // Add element
            if (uri != null)
                xml.append(uri.toXML());

            xml.closeElement(this);
            return xml;
        }
    }


    public static class RedEnvelopeType implements ExtensionElement {
        public static final String NAMESPACE = "urn:xmpp:redenvelope-element";
        public static final String ELEMENT = "redenvelope";

        private String type;
        private String remarks;
        private String money;

        public RedEnvelopeType(String type, String remarks, String money) {
            this.type = type;
            this.remarks = remarks;
            this.money = money;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }

        public String getMoney() {
            return money;
        }

        public void setMoney(String money) {
            this.money = money;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public CharSequence toXML() {
            XmlStringBuilder xml = new XmlStringBuilder(this);
            // Add attribute
            xml.optAttribute("type", getType());
            xml.optAttribute("remarks", remarks);
            xml.optAttribute("money", String.valueOf(money));
            xml.rightAngleBracket();

            xml.append("红包{" + remarks + "]");

            xml.closeElement(this);
            return xml;
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }
    }


    public static class OldMessageAction implements ExtensionElement {
        public static final String NAMESPACE = "urn:xmpp:oldmessage-element";
        public static final String ELEMENT = "oldmessage";




        private String messagteId;
        private int action;

        public OldMessageAction(String messagteId, int action) {
            this.messagteId = messagteId;
            this.action = action;
        }

        public String getMessagteId() {
            return messagteId;
        }

        public void setMessagteId(String messagteId) {
            this.messagteId = messagteId;
        }

        public int getAction() {
            return action;
        }

        public void setAction(int action) {
            this.action = action;
        }


        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public CharSequence toXML() {
            XmlStringBuilder xml = new XmlStringBuilder(this);
            // Add attribute
            xml.optAttribute("action", String.valueOf(getAction()));
            xml.optAttribute("messagteId", getMessagteId());
            xml.rightAngleBracket();
            xml.append("撤回了一条消息");
            xml.closeElement(this);
            return xml;
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }
    }


}
