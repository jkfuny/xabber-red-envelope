/**
 * Copyright (c) 2013, Redsolution LTD. All rights reserved.
 * <p>
 * This file is part of Xabber project; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License, Version 3.
 * <p>
 * Xabber is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License,
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.xabber.android.data.message;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.NetworkException;
import com.xabber.android.data.SettingsManager;
import com.xabber.android.data.connection.StanzaSender;
import com.xabber.android.data.database.MessageDatabaseManager;
import com.xabber.android.data.database.messagerealm.Attachment;
import com.xabber.android.data.database.messagerealm.ForwardId;
import com.xabber.android.data.database.messagerealm.MessageItem;
import com.xabber.android.data.database.messagerealm.RedEnvelope;
import com.xabber.android.data.database.messagerealm.SyncInfo;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.BaseEntity;
import com.xabber.android.data.entity.UserJid;
import com.xabber.android.data.extension.carbons.CarbonManager;
import com.xabber.android.data.extension.cs.ChatStateManager;
import com.xabber.android.data.extension.file.FileManager;
import com.xabber.android.data.extension.forward.ForwardComment;
import com.xabber.android.data.extension.httpfileupload.ExtendedFormField;
import com.xabber.android.data.extension.httpfileupload.HttpFileUploadManager;
import com.xabber.android.data.extension.muc.MUCManager;
import com.xabber.android.data.extension.otr.OTRManager;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.message.chat.ChatManager;
import com.xabber.android.data.notification.MessageNotificationManager;
import com.xabber.android.data.notification.NotificationManager;

import org.greenrobot.eventbus.EventBus;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Message.Type;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Resourcepart;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * Chat instance.
 *
 * @author alexander.ivanov
 */
public abstract class AbstractChat extends BaseEntity implements RealmChangeListener<RealmResults<MessageItem>> {

    /**
     * Number of messages from history to be shown for context purpose.
     */
    public static final int PRELOADED_MESSAGES = 50;

    /**
     * Whether chat is open and should be displayed as active chat.
     */
    protected boolean active;
    /**
     * Whether changes in status should be record.
     */
    private boolean trackStatus;
    /**
     * Whether user never received notifications from this chat.
     */
    private boolean firstNotification;

    /**
     * Current thread id.
     */
    private String threadId;

    private int lastPosition;
    private int unreadMessageCount;
    private boolean archived;
    protected NotificationState notificationState;

    private boolean isPrivateMucChat;
    private boolean isPrivateMucChatAccepted;

    private boolean isRemotePreviousHistoryCompletelyLoaded = false;

    private Date lastSyncedTime;
    private RealmResults<SyncInfo> syncInfo;
    private MessageItem lastMessage;
    private RealmResults<MessageItem> messages;

    protected AbstractChat(@NonNull final AccountJid account, @NonNull final UserJid user, boolean isPrivateMucChat) {
        super(account, isPrivateMucChat ? user : user.getBareUserJid());
        threadId = StringUtils.randomString(12);
        active = false;
        trackStatus = false;
        firstNotification = true;
        this.isPrivateMucChat = isPrivateMucChat;
        isPrivateMucChatAccepted = false;
        notificationState = new NotificationState(NotificationState.NotificationMode.bydefault, 0);

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getMessages();
            }
        });
    }

    public boolean isRemotePreviousHistoryCompletelyLoaded() {
        return isRemotePreviousHistoryCompletelyLoaded;
    }

    public void setRemotePreviousHistoryCompletelyLoaded(boolean remotePreviousHistoryCompletelyLoaded) {
        isRemotePreviousHistoryCompletelyLoaded = remotePreviousHistoryCompletelyLoaded;
    }

    public Date getLastSyncedTime() {
        return lastSyncedTime;
    }

    public void setLastSyncedTime(Date lastSyncedTime) {
        this.lastSyncedTime = lastSyncedTime;
    }


    public boolean isActive() {
        if (isPrivateMucChat && !isPrivateMucChatAccepted) {
            return false;
        }

        return active;
    }

    public void openChat() {
        active = true;
        trackStatus = true;
    }

    void closeChat() {
        active = false;
        firstNotification = true;
    }

    private String getAccountString() {
        return account.toString();
    }

    private String getUserString() {
        return user.toString();
    }

    public RealmResults<MessageItem> getMessages() {
        if (messages == null) {
            messages = MessageDatabaseManager.getChatMessages(
                    MessageDatabaseManager.getInstance().getRealmUiThread(),
                    account,
                    user);
            updateLastMessage();

            messages.addChangeListener(this);
        }

        return messages;
    }

    public RealmResults<SyncInfo> getSyncInfo() {
        if (syncInfo == null) {
            syncInfo = MessageDatabaseManager.getInstance()
                    .getRealmUiThread().where(SyncInfo.class)
                    .equalTo(SyncInfo.FIELD_ACCOUNT, getAccountString())
                    .equalTo(SyncInfo.FIELD_USER, getUserString())
                    .findAllAsync();
        }

        return syncInfo;
    }

    boolean isStatusTrackingEnabled() {
        return trackStatus;
    }

    /**
     * @return Target address for sending message.
     */
    @NonNull
    public abstract Jid getTo();

    /**
     * @return Message type to be assigned.
     */
    public abstract Type getType();

    /**
     * @return Whether user never received notifications from this chat. And
     * mark as received.
     */
    public boolean getFirstNotification() {
        boolean result = firstNotification;
        firstNotification = false;
        return result;
    }

    /**
     * @return Whether user should be notified about incoming messages in chat.
     */
    public boolean notifyAboutMessage() {
        if (notificationState.getMode().equals(NotificationState.NotificationMode.bydefault))
            return SettingsManager.eventsOnChat();
        if (notificationState.getMode().equals(NotificationState.NotificationMode.enabled))
            return true;
        else return false;
    }

    private void enableNotificationsIfNeed() {
        int currentTime = (int) (System.currentTimeMillis() / 1000L);
        NotificationState.NotificationMode mode = notificationState.getMode();

        if ((mode.equals(NotificationState.NotificationMode.snooze15m)
                && currentTime > notificationState.getTimestamp() + TimeUnit.MINUTES.toSeconds(15))
                || (mode.equals(NotificationState.NotificationMode.snooze1h)
                && currentTime > notificationState.getTimestamp() + TimeUnit.HOURS.toSeconds(1))
                || (mode.equals(NotificationState.NotificationMode.snooze2h)
                && currentTime > notificationState.getTimestamp() + TimeUnit.HOURS.toSeconds(2))
                || (mode.equals(NotificationState.NotificationMode.snooze1d)
                && currentTime > notificationState.getTimestamp() + TimeUnit.DAYS.toSeconds(1))) {

            setNotificationStateOrDefault(new NotificationState(
                    NotificationState.NotificationMode.enabled, 0), true);
        }
    }

    abstract protected MessageItem createNewMessageItem(String text);

    abstract protected MessageItem createRedEnvelopeMessageItem(String money, String remarks);

    public void sendReVokeMessage(final String messageId) {
        Realm realm = MessageDatabaseManager.getInstance().getRealmUiThread();


        MessageItem messageItem = realm.where(MessageItem.class)
                .equalTo(MessageItem.Fields.UNIQUE_ID, messageId)
                .findFirst();
        realm.beginTransaction();
        if (messageItem != null) {
            messageItem.setRevoke(true);
            messageItem.setMessage_type(MessageItem.MessageType.REVOKE);
        }
        realm.copyToRealmOrUpdate(messageItem);
        sendMessage(messageItem);
        realm.commitTransaction();

    }

    public void saveReceiveRedMessage(MessageItem newMessageItem,boolean isSelf, final String stanzaId) {
        Realm realm = MessageDatabaseManager.getInstance().getRealmUiThread();
        MessageItem messageItem = realm.where(MessageItem.class)
                .equalTo(MessageItem.Fields.STANZA_ID, stanzaId)
                .findFirst();
        if (!realm.isInTransaction()) {
            realm.beginTransaction();
        }
        if (messageItem != null) {
            RealmList<RedEnvelope> redenvelopes = messageItem.getRedenvelopes();
            for (RedEnvelope redEnvelope : redenvelopes) {
                redEnvelope.setRedenvelope_receive(true);
            }

            realm.copyToRealmOrUpdate(messageItem);

            newMessageItem.setOriginalStanza(messageItem.getStanzaId());
            newMessageItem.setMessage_type(MessageItem.MessageType.RECEIVE_RED_ENVELOPE);
            realm.copyToRealmOrUpdate(newMessageItem);
            if (realm.isInTransaction()) {
                realm.commitTransaction();
            }
            if (!isSelf) {
                if (canSendMessage())
                    sendMessages();
            }
        }

    }

    /**
     * Creates new action.
     *
     * @param resource can be <code>null</code>.
     * @param text     can be <code>null</code>.
     */
    public void newAction(Resourcepart resource, String text, ChatAction action, boolean fromMUC) {
        createAndSaveNewMessage(true, UUID.randomUUID().toString(), resource, text, action,
                null, true, false, false, false,
                null, null, null, null, null, fromMUC, false);
    }

    /**
     * Creates new message.
     * <p/>
     * Any parameter can be <code>null</code> (except boolean values).
     *
     * @param resource       Contact's resource or nick in conference.
     * @param text           message.
     * @param action         Informational message.
     * @param delayTimestamp Time when incoming message was sent or outgoing was created.
     * @param incoming       Incoming message.
     * @param notify         Notify user about this message when appropriated.
     * @param encrypted      Whether encrypted message in OTR chat was received.
     * @param offline        Whether message was received from server side offline storage.
     * @return
     */
    protected void createAndSaveNewMessage(boolean ui, String uid, Resourcepart resource, String text, final ChatAction action,
                                           final Date delayTimestamp, final boolean incoming, boolean notify,
                                           final boolean encrypted, final boolean offline, final String stanzaId,
                                           final String originalStanza, final String parentMessageId, final String originalFrom,
                                           final RealmList<ForwardId> forwardIds, boolean fromMUC, boolean fromMAM) {

        final MessageItem messageItem = createMessageItem(uid, resource, text, action, delayTimestamp,
                incoming, notify, encrypted, offline, stanzaId, null,
                originalStanza, parentMessageId, originalFrom, forwardIds, fromMUC, fromMAM);

        saveMessageItem(ui, messageItem);
        EventBus.getDefault().post(new NewMessageEvent());
    }

    protected void createAndSaveReceviceRedEnvelopeMessage(boolean ui, String uid, Resourcepart resource, String text, final ChatAction action,
                                           final Date delayTimestamp, final boolean incoming, boolean notify,
                                           final boolean encrypted, final boolean offline, final String stanzaId,
                                           final String originalStanza, final String parentMessageId, final String originalFrom,
                                           final RealmList<ForwardId> forwardIds, boolean fromMUC, boolean fromMAM) {

        final MessageItem messageItem = createMessageItem(uid, resource, text, action, delayTimestamp,
                incoming, notify, encrypted, offline, stanzaId, null,
                originalStanza, parentMessageId, originalFrom, forwardIds, fromMUC, fromMAM);
        messageItem.setMessage_type(MessageItem.MessageType.RECEIVE_RED_ENVELOPE);
        saveMessageItem(ui, messageItem);
        EventBus.getDefault().post(new NewMessageEvent());
    }

    protected void doOldMessageAction(ExtendedFormField.OldMessageAction oldMessageAction) {
        Realm realm = MessageDatabaseManager.getInstance().getRealmUiThread();
        Log.i("messageId--",oldMessageAction.getMessagteId());
        MessageItem messageItem = realm.where(MessageItem.class).equalTo(MessageItem.Fields.STANZA_ID, oldMessageAction.getMessagteId()).findFirst();
        if (messageItem != null) {
            realm.beginTransaction();
            if (oldMessageAction.getAction() == MessageItem.MessageType.REVOKE) {
                messageItem.setMessage_type(oldMessageAction.getAction());
                messageItem.setRevoke(true);
            } else if (oldMessageAction.getAction() == MessageItem.MessageType.RECEIVE_RED_ENVELOPE) {
                RealmList<RedEnvelope> redEnvelopes = messageItem.getRedenvelopes();
                if (redEnvelopes != null) {
                    redEnvelopes.get(0).setRedenvelope_receive(true);
                }

            }
            realm.commitTransaction();
        }
    }

    protected void createAndRedEnvelopeNewMessage(boolean ui, String uid, Resourcepart resource, String text, final ChatAction action,
                                                  final Date delayTimestamp, final boolean incoming, boolean notify,
                                                  final boolean encrypted, final boolean offline, final String stanzaId,
                                                  final String originalStanza, final String parentMessageId, final String originalFrom,
                                                  final RealmList<ForwardId> forwardIds, boolean fromMUC, boolean fromMAM, RealmList<RedEnvelope> redEnvelopes) {

        final MessageItem messageItem = createMessageItem(uid, resource, text, action, delayTimestamp,
                incoming, notify, encrypted, offline, stanzaId, null,
                originalStanza, parentMessageId, originalFrom, forwardIds, fromMUC, fromMAM);
        messageItem.setRedenvelopes(redEnvelopes);
        messageItem.setMessage_type(MessageItem.MessageType.REDENVELOPE);
        saveMessageItem(ui, messageItem);
        EventBus.getDefault().post(new NewMessageEvent());
    }

    protected void createAndSaveFileMessage(boolean ui, String uid, Resourcepart resource, String text, final ChatAction action,
                                            final Date delayTimestamp, final boolean incoming, boolean notify,
                                            final boolean encrypted, final boolean offline, final String stanzaId,
                                            RealmList<Attachment> attachments, final String originalStanza,
                                            final String parentMessageId, final String originalFrom, boolean fromMUC, boolean fromMAM) {

        final MessageItem messageItem = createMessageItem(uid, resource, text, action, delayTimestamp,
                incoming, notify, encrypted, offline, stanzaId, attachments,
                originalStanza, parentMessageId, originalFrom, null, fromMUC, fromMAM);

        saveMessageItem(ui, messageItem);
        EventBus.getDefault().post(new NewMessageEvent());
    }

    public void saveMessageItem(boolean ui, final MessageItem messageItem) {
        final long startTime = System.currentTimeMillis();
        Realm realm;
        if (ui) realm = MessageDatabaseManager.getInstance().getRealmUiThread();
        else realm = MessageDatabaseManager.getInstance().getNewBackgroundRealm();

        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.copyToRealm(messageItem);
                LogManager.d("REALM", Thread.currentThread().getName()
                        + " save message item: " + (System.currentTimeMillis() - startTime));
            }
        });
    }

    protected MessageItem createMessageItem(Resourcepart resource, String text, ChatAction action,
                                            Date delayTimestamp, boolean incoming, boolean notify, boolean encrypted,
                                            boolean offline, String stanzaId, RealmList<Attachment> attachments,
                                            String originalStanza, String parentMessageId, String originalFrom,
                                            RealmList<ForwardId> forwardIds, boolean fromMUC) {

        return createMessageItem(UUID.randomUUID().toString(), resource, text, action,
                delayTimestamp, incoming, notify, encrypted, offline, stanzaId, attachments,
                originalStanza, parentMessageId, originalFrom, forwardIds, fromMUC, false);
    }

    protected MessageItem createMessageItem(String uid, Resourcepart resource, String text, ChatAction action,
                                            Date delayTimestamp, boolean incoming, boolean notify, boolean encrypted,
                                            boolean offline, String stanzaId, RealmList<Attachment> attachments,
                                            String originalStanza, String parentMessageId, String originalFrom,
                                            RealmList<ForwardId> forwardIds, boolean fromMUC, boolean fromMAM) {

        final boolean visible = MessageManager.getInstance().isVisibleChat(this);
        boolean read = incoming ? visible : true;
        boolean send = incoming;
        if (action == null && text == null) {
            throw new IllegalArgumentException();
        }
        if (text == null) {
            text = " ";
        }
        if (action != null) {
            read = true;
            send = true;
        }

        final Date timestamp = new Date();

        if (text.trim().isEmpty()) {
            notify = false;
        }

        if (notify || !incoming) {
            openChat();
        }
        if (!incoming) {
            notify = false;
        }

        if (isPrivateMucChat) {
            if (!isPrivateMucChatAccepted) {
                notify = false;
            }
        }

        MessageItem messageItem = new MessageItem(uid);

        messageItem.setAccount(account);
        messageItem.setUser(user);

        if (resource == null) {
            messageItem.setResource(Resourcepart.EMPTY);
        } else {
            messageItem.setResource(resource);
        }

        if (action != null) {
            messageItem.setAction(action.toString());
        }
        messageItem.setText(text);
        messageItem.setTimestamp(timestamp.getTime());
        if (delayTimestamp != null) {
            messageItem.setDelayTimestamp(delayTimestamp.getTime());
        }
        messageItem.setIncoming(incoming);
        messageItem.setRead(fromMAM || read);
        messageItem.setSent(send);
        messageItem.setEncrypted(encrypted);
        messageItem.setOffline(offline);
        messageItem.setFromMUC(fromMUC);
        messageItem.setStanzaId(stanzaId);
        if (attachments != null) messageItem.setAttachments(attachments);
        FileManager.processFileMessage(messageItem);

        // forwarding
        if (forwardIds != null) messageItem.setForwardedIds(forwardIds);
        messageItem.setOriginalStanza(originalStanza);
        messageItem.setOriginalFrom(originalFrom);
        messageItem.setParentMessageId(parentMessageId);

        // notification
        enableNotificationsIfNeed();
        if (notify && notifyAboutMessage() && !visible)
            NotificationManager.getInstance().onMessageNotification(messageItem);

        // unread message count
        if (!visible && action == null) {
            if (incoming && !fromMAM) increaseUnreadMessageCount();
            else resetUnreadMessageCount();
        }

        // remove notifications if get outgoing message with 2 sec delay
        if (!incoming) {
            MessageNotificationManager.getInstance().removeChatWithTimer(account, user);
        }

        // when getting new message, unarchive chat if chat not muted
        if (this.notifyAboutMessage())
            this.archived = false;

        return messageItem;
    }

    public String newRedEnvelopeMessage(final String money, final String remarks) {
        Realm realm = MessageDatabaseManager.getInstance().getNewBackgroundRealm();

        final String messageId = UUID.randomUUID().toString();

        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {

                RealmList<RedEnvelope> redEnvelopes = new RealmList<>();

                RedEnvelope redEnvelope = new RedEnvelope();

                redEnvelope.setRedenvelope_money(money);
                redEnvelope.setRedenvelope_remarks(remarks);
                redEnvelopes.add(redEnvelope);

                MessageItem messageItem = new MessageItem(messageId);
                messageItem.setAccount(account);
                messageItem.setUser(user);
                messageItem.setText("红包[" + remarks + "]");
                messageItem.setRedenvelopes(redEnvelopes);
                messageItem.setTimestamp(System.currentTimeMillis());
                messageItem.setRead(true);
                messageItem.setSent(true);
                messageItem.setError(false);
                messageItem.setIncoming(false);
                messageItem.setStanzaId(UUID.randomUUID().toString());
                realm.copyToRealm(messageItem);
            }
        });
        return messageId;
    }


    public String newFileMessage(final List<File> files) {
        Realm realm = MessageDatabaseManager.getInstance().getNewBackgroundRealm();

        final String messageId = UUID.randomUUID().toString();

        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {

                RealmList<Attachment> attachments = new RealmList<>();
                for (File file : files) {
                    Attachment attachment = new Attachment();
                    attachment.setFilePath(file.getPath());
                    attachment.setFileSize(file.length());
                    attachment.setTitle(file.getName());
                    attachment.setIsImage(FileManager.fileIsImage(file));
                    attachment.setMimeType(HttpFileUploadManager.getMimeType(file.getPath()));
                    attachment.setDuration((long) 0);

                    if (attachment.isImage()) {
                        HttpFileUploadManager.ImageSize imageSize =
                                HttpFileUploadManager.getImageSizes(file.getPath());
                        attachment.setImageHeight(imageSize.getHeight());
                        attachment.setImageWidth(imageSize.getWidth());
                    }
                    attachments.add(attachment);
                }

                MessageItem messageItem = new MessageItem(messageId);
                messageItem.setAccount(account);
                messageItem.setUser(user);
                messageItem.setText("Sending files..");
                messageItem.setAttachments(attachments);
                messageItem.setTimestamp(System.currentTimeMillis());
                messageItem.setRead(true);
                messageItem.setSent(true);
                messageItem.setError(false);
                messageItem.setIncoming(false);
                messageItem.setInProgress(true);
                messageItem.setStanzaId(UUID.randomUUID().toString());
                realm.copyToRealm(messageItem);
            }
        });

        return messageId;
    }

    /**
     * @return Whether chat accepts packets from specified user.
     */
    private boolean accept(UserJid jid) {
        return this.user.equals(jid);
    }

    @Nullable
    public synchronized MessageItem getLastMessage() {
        return lastMessage;
    }

    private void updateLastMessage() {
        if (messages.isValid() && messages.isLoaded() && !messages.isEmpty()) {
            List<MessageItem> textMessages = MessageDatabaseManager.getInstance()
                    .getRealmUiThread()
                    .copyFromRealm(messages.where().isNull(MessageItem.Fields.ACTION)
                            .or().equalTo(MessageItem.Fields.ACTION, ChatAction.available.toString()).findAll());

            if (!textMessages.isEmpty())
                lastMessage = textMessages.get(textMessages.size() - 1);
            else
                lastMessage = MessageDatabaseManager.getInstance()
                        .getRealmUiThread()
                        .copyFromRealm(messages.last());
        } else {
            lastMessage = null;
        }
    }

    /**
     * @return Time of last message in chat. Can be <code>null</code>.
     */
    public Date getLastTime() {
        MessageItem lastMessage = getLastMessage();
        if (lastMessage != null) {
            return new Date(lastMessage.getTimestamp());
        } else {
            return null;
        }
    }

    public Message createMessagePacket(String body, String stanzaId) {
        Message message = createMessagePacket(body);
        if (stanzaId != null) message.setStanzaId(stanzaId);
        return message;
    }

    /**
     * @return New message packet to be sent.
     */
    public Message createMessagePacket(String body) {
        Message message = new Message();
        message.setTo(getTo());
        message.setType(getType());
        message.setBody(body);
        message.setThread(threadId);
        return message;
    }

    /**
     * Send stanza with XEP-0221
     */
    public Message createFileMessagePacket(String stanzaId, RealmList<Attachment> attachments, String body) {

        Message message = new Message();
        message.setTo(getTo());
        message.setType(getType());
        message.setThread(threadId);
        if (stanzaId != null) message.setStanzaId(stanzaId);

        DataForm dataForm = new DataForm(DataForm.Type.form);

        int i = 1;
        for (Attachment attachment : attachments) {
            ExtendedFormField formField = new ExtendedFormField("media" + i);
            i++;
            formField.setLabel(attachment.getTitle());

            ExtendedFormField.Uri uri = new ExtendedFormField.Uri(attachment.getMimeType(), attachment.getFileUrl());
            uri.setSize(attachment.getFileSize());
            uri.setDuration(attachment.getDuration());

            formField.setMedia(
                    new ExtendedFormField.Media(String.valueOf(attachment.getImageHeight()),
                            String.valueOf(attachment.getImageWidth()), uri));

            dataForm.addField(formField);
        }
        message.addExtension(dataForm);
        message.setBody(body);

        Log.d("XEP-0221", message.toXML().toString());
        return message;
    }

    /**
     * Prepare text to be send.
     *
     * @return <code>null</code> if text shouldn't be send.
     */
    protected String prepareText(String text) {
        return text;
    }


    public void sendMessages() {
        Application.getInstance().runInBackgroundUserRequest(new Runnable() {
            @Override
            public void run() {
                Realm realm = MessageDatabaseManager.getInstance().getNewBackgroundRealm();

                RealmResults<MessageItem> messagesToSend = realm.where(MessageItem.class)
                        .equalTo(MessageItem.Fields.ACCOUNT, account.toString())
                        .equalTo(MessageItem.Fields.USER, user.toString())
                        .equalTo(MessageItem.Fields.SENT, false)
                        .findAllSorted(MessageItem.Fields.TIMESTAMP, Sort.ASCENDING);

                realm.beginTransaction();

                for (final MessageItem messageItem : messagesToSend) {
                    if (messageItem.isInProgress()) continue;
                    if (!sendMessage(messageItem)) {
                        break;
                    }
                }
                realm.commitTransaction();

                realm.close();
            }
        });
    }

    protected boolean canSendMessage() {
        return true;
    }

    @SuppressWarnings("WeakerAccess")
    boolean sendMessage(MessageItem messageItem) {
        String text = prepareText(messageItem.getText());
        messageItem.setEncrypted(OTRManager.getInstance().isEncrypted(text));
        Long timestamp = messageItem.getTimestamp();

        Date currentTime = new Date(System.currentTimeMillis());
        Date delayTimestamp = null;

        if (timestamp != null) {
            if (currentTime.getTime() - timestamp > 60000) {
                delayTimestamp = currentTime;
            }
        }
        Message message = null;
        if (messageItem.isRevoke()) {

            message = createMessagePacket(text, messageItem.getStanzaId());
            DataForm dataForm = DataFormManager.oldMessageDataFrom(messageItem.getStanzaId(), MessageItem.MessageType.REVOKE);
            message.addExtension(dataForm);
        } else if (messageItem.getMessage_type() == MessageItem.MessageType.RECEIVE_RED_ENVELOPE) {
            message = createMessagePacket(text, messageItem.getStanzaId());
            DataForm dataForm = DataFormManager.oldMessageDataFrom(messageItem.getOriginalStanza(), MessageItem.MessageType.RECEIVE_RED_ENVELOPE);
            message.addExtension(dataForm);
        } else if (messageItem.haveAttachments()) {
            message = createFileMessagePacket(messageItem.getStanzaId(),
                    messageItem.getAttachments(), text);

        } else if (messageItem.haveForwardedMessages()) {

            int count = messageItem.getForwardedIds().size();
            String body = String.format(Application.getInstance().getResources()
                    .getString(R.string.forwarded_support_text), count);
            if (text != null && !text.isEmpty()) body += "\n" + text;

            message = createMessagePacket(body, messageItem.getStanzaId());

            Realm realm = MessageDatabaseManager.getInstance().getNewBackgroundRealm();

            // forwarded
            if (messageItem.getForwardedIds() != null && messageItem.getForwardedIds().size() > 0) {
                final String[] ids = new String[messageItem.getForwardedIds().size()];
                int i = 0;
                for (ForwardId id : messageItem.getForwardedIds()) {
                    ids[i] = id.getForwardMessageId();
                    i++;
                }

                RealmResults<MessageItem> items = realm.where(MessageItem.class)
                        .in(MessageItem.Fields.UNIQUE_ID, ids).findAll();
                for (MessageItem item : items) {
                    try {
                        Message forwarded = (Message) PacketParserUtils.parseStanza(item.getOriginalStanza());
                        message.addExtension(new Forwarded(new DelayInformation(new Date(item.getTimestamp())), forwarded));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                message.addExtension(new ForwardComment(text));
            }

        } else if (text != null) {
            message = createMessagePacket(text, messageItem.getStanzaId());
            if (messageItem.getMessage_type() == MessageItem.MessageType.REDENVELOPE) {
                DataForm dataForm = DataFormManager.redEnvelopeDataFrom(messageItem);
                message.addExtension(dataForm);
            }
            Log.d("XEP-0221", message.toXML().toString());

        }

        if (message != null) {
            ChatStateManager.getInstance().updateOutgoingMessage(AbstractChat.this, message);
            CarbonManager.getInstance().updateOutgoingMessage(AbstractChat.this, message);
            if (delayTimestamp != null) {
                message.addExtension(new DelayInformation(delayTimestamp));
            }
            Log.i("messageId--",messageItem.getStanzaId());
            final String messageId = messageItem.getUniqueId();
            try {
                StanzaSender.sendStanza(account, message, new StanzaListener() {
                    @Override
                    public void processStanza(Stanza packet) throws SmackException.NotConnectedException {
                        Realm realm = MessageDatabaseManager.getInstance().getNewBackgroundRealm();
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(Realm realm) {
                                MessageItem acknowledgedMessage = realm
                                        .where(MessageItem.class)
                                        .equalTo(MessageItem.Fields.UNIQUE_ID, messageId)
                                        .findFirst();

                                if (acknowledgedMessage != null) {
                                    acknowledgedMessage.setAcknowledged(true);
                                }
                            }
                        });
                        realm.close();
                    }
                });
            } catch (NetworkException e) {
                return false;
            }
        }

        if (message == null) {
            messageItem.setError(true);
            messageItem.setErrorDescription("Internal error: message is null");
        } else {
            message.setFrom(account.getFullJid());
            messageItem.setOriginalStanza(message.toXML().toString());
        }

        if (delayTimestamp != null) {
            messageItem.setDelayTimestamp(delayTimestamp.getTime());
        }
        if (messageItem.getTimestamp() == null) {
            messageItem.setTimestamp(currentTime.getTime());
        }
        messageItem.setSent(true);
        return true;
    }

    public String getThreadId() {
        return threadId;
    }


    /**
     * Update thread id with new value.
     *
     * @param threadId <code>null</code> if current value shouldn't be changed.
     */
    protected void updateThreadId(String threadId) {
        if (threadId == null) {
            return;
        }
        this.threadId = threadId;
    }

    /**
     * Processes incoming packet.
     *
     * @param userJid
     * @param packet
     * @return Whether packet was directed to this chat.
     */
    protected boolean onPacket(UserJid userJid, Stanza packet, boolean isCarbons) {
        return accept(userJid);
    }

    /**
     * Connection complete.f
     */
    protected void onComplete() {
    }

    /**
     * Disconnection occured.
     */
    protected void onDisconnect() {
    }

    public void setIsPrivateMucChatAccepted(boolean isPrivateMucChatAccepted) {
        this.isPrivateMucChatAccepted = isPrivateMucChatAccepted;
    }

    boolean isPrivateMucChat() {
        return isPrivateMucChat;
    }

    boolean isPrivateMucChatAccepted() {
        return isPrivateMucChatAccepted;
    }

    @Override
    public void onChange(RealmResults<MessageItem> messageItems) {
        updateLastMessage();
    }

    public int getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public void increaseUnreadMessageCount() {
        this.unreadMessageCount++;
        ChatManager.getInstance().saveOrUpdateChatDataToRealm(this);
    }

    public void resetUnreadMessageCount() {
        this.unreadMessageCount = 0;
        ChatManager.getInstance().saveOrUpdateChatDataToRealm(this);
    }

    public void setUnreadMessageCount(int unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived, boolean needSaveToRealm) {
        this.archived = archived;
        if (needSaveToRealm) ChatManager.getInstance().saveOrUpdateChatDataToRealm(this);
    }

    public NotificationState getNotificationState() {
        return notificationState;
    }

    public void setNotificationState(NotificationState notificationState, boolean needSaveToRealm) {
        this.notificationState = notificationState;
        if (notificationState.getMode() == NotificationState.NotificationMode.disabled && needSaveToRealm)
            NotificationManager.getInstance().removeMessageNotification(account, user);
        if (needSaveToRealm) ChatManager.getInstance().saveOrUpdateChatDataToRealm(this);
    }

    public void setNotificationStateOrDefault(NotificationState notificationState, boolean needSaveToRealm) {
        if (notificationState.getMode() != NotificationState.NotificationMode.enabled
                && notificationState.getMode() != NotificationState.NotificationMode.disabled)
            throw new IllegalStateException("In this method mode must be enabled or disabled.");

        if (!eventsOnChatGlobal() && notificationState.getMode() == NotificationState.NotificationMode.disabled
                || eventsOnChatGlobal() && notificationState.getMode() == NotificationState.NotificationMode.enabled)
            notificationState.setMode(NotificationState.NotificationMode.bydefault);

        setNotificationState(notificationState, needSaveToRealm);
    }

    private boolean eventsOnChatGlobal() {
        if (MUCManager.getInstance().hasRoom(account, user.getJid().asEntityBareJidIfPossible()))
            return SettingsManager.eventsOnMuc();
        else return SettingsManager.eventsOnChat();
    }

    public int getLastPosition() {
        return lastPosition;
    }

    public void saveLastPosition(int lastPosition) {
        this.lastPosition = lastPosition;
        ChatManager.getInstance().saveOrUpdateChatDataToRealm(this);
    }

    public void setLastPosition(int lastPosition) {
        this.lastPosition = lastPosition;
    }

    public RealmList<ForwardId> parseForwardedMessage(boolean ui, Stanza packet, String parentMessageId) {
        List<ExtensionElement> elements = packet.getExtensions(Forwarded.ELEMENT, Forwarded.NAMESPACE);
        if (elements == null || elements.size() == 0) return null;

        RealmList<ForwardId> forwarded = new RealmList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof Forwarded) {
                Stanza stanza = ((Forwarded) element).getForwardedStanza();
                if (stanza instanceof Message) {
                    forwarded.add(new ForwardId(parseInnerMessage(ui, (Message) stanza, parentMessageId)));
                }
            }
        }
        return forwarded;
    }

    protected abstract String parseInnerMessage(boolean ui, Message message, String parentMessageId);
}