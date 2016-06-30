package org.icechat;

import java.awt.Color;
import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import org.icelib.Icelib;
import org.icelib.Persona;

public class FriendRender extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        Persona p = (Persona) value;
        setForeground(p.isOnline() ? Color.white : Color.gray);
        setText("<html><span style=\"font-size: 1.2em;\">" + p.getDisplayName() + "</span>" + "<br/><span style=\"font-size: 0.8em; font-weight: bold;\">" + "Level: <i>" + p.getLevel() + "</i> " + "<i>" + Icelib.toEnglish(p.getProfession()) + "</i> " + "(" + p.getShard() + ")</span><br/>" + "<span style=\"font-size: 0.8em; font-weight: normal;\">" + p.getStatusText() + "</span></html>");
        return this;
    }
}
