package org.icechat;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import org.icelib.Icelib;
import org.icelib.Persona;

@SuppressWarnings("serial")
public class PersonaRender extends DefaultListCellRenderer {

	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		Persona p = (Persona) value;
		setText("<html><span style=\"font-size: 2em;\">" + p.getDisplayName() + "</span><br/><span><b>Level: <i>" + p.getLevel()
				+ "</i> <i>" + Icelib.toEnglish(p.getAppearance().getRace()) + "</i> <i>" + Icelib.toEnglish(p.getProfession())
				+ "</i></html>");
		return this;
	}
}
