package org.icechat;

import java.awt.BorderLayout;
import java.net.URL;
import java.util.prefs.Preferences;

import javax.swing.JApplet;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

@SuppressWarnings("serial")
public class IcechatApplet extends JApplet implements IcechatContext {

	private IcechatPanel panel;
	private Address[] addresses;

	@Override
	public void init() {
		super.init();
		try {
			UIManager.setLookAndFeel("com.jtattoo.plaf.noire.NoireLookAndFeel");
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		String routerUrls = getParameter("routerUrls");
		if (routerUrls == null) {
			routerUrls = System.getProperty("routerUrls");
		}
		if (routerUrls == null) {
			routerUrls = "http://localhost";
		}

		setLayout(new BorderLayout(0, 0));
		String[] routerUrlsArr = routerUrls.split("\\s+");
		addresses = new Address[routerUrlsArr.length];
		int i = 0;
		for (String s : routerUrlsArr) {
			addresses[i++] = new Address(s);
		}
		add(panel = new IcechatPanel(this, addresses), BorderLayout.CENTER);

	}

	@Override
	public void start() {
		super.start();
		if (addresses.length == 1) {
			panel.connect();
		}
	}

	@Override
	public void destroy() {
		super.destroy();
		if (panel != null) {
			panel.close();
		}
	}

	@Override
	public void openUrl(String url) throws Exception {
		getAppletContext().showDocument(new URL(url), "icechatBrowse");
	}

	@Override
	public Preferences getPreferences() {
		return Preferences.userRoot().node("icechat");
	}
}
