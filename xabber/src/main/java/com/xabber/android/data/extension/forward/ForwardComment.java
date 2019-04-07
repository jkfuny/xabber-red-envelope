package com.xabber.android.data.extension.forward;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

public class ForwardComment implements ExtensionElement {

    public static final String NAMESPACE = "xabber/comment";
    public static final String ELEMENT = "comment";

    private final String comment111;
    public ForwardComment(String comment) {
        this.comment111 = comment;
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
        xml.rightAngleBracket();
        xml.append(comment111);
        xml.closeElement(this);
        return xml;
    }

    public String getComment() {
        return comment111;
    }
}