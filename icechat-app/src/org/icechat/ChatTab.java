package org.icechat;

import javax.swing.JComponent;
import javax.swing.JEditorPane;

import org.icelib.ChannelType;

public class ChatTab {
    private ChannelType channel;
    private String player;
    private final JComponent component;
    private final JEditorPane chat;

    public ChatTab(ChannelType channel, String player, JComponent component, JEditorPane chat) {
        this.channel = channel;
        this.player = player;
        this.component = component;
        this.chat = chat;
    }

    public JEditorPane getChat() {
        return chat;
    }

    public JComponent getComponent() {
        return component;
    }

    public ChannelType getChannel() {
        return channel;
    }

    public String getPlayer() {
        return player;
    }

    @Override
    public String toString() {
        return "ChatTab{" + "channel=" + channel + ", player=" + player + ", component=" + component + ", chat=" + chat + '}';
    }

}
