package org.icechat;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.icelib.ChannelType;
import org.icelib.Icelib;
import org.icelib.Persona;
import org.icelib.SceneryItem;
import org.icelib.Zone;
import org.icelib.swing.CloseIcon;
import org.icelib.swing.ColorIcon;
import org.icelib.swing.Iceswing;
import org.icenet.GameQueryMessage;
import org.icenet.NetworkException;
import org.icenet.SimulatorMessage;
import org.icenet.client.Client;
import org.icenet.client.ClientListener;
import org.icenet.client.Spawn;

import net.miginfocom.swing.MigLayout;

public class IcechatPanel extends JPanel implements ClientListener {

	public final static int LONG_RECONNECT_TIME = 60;
	public final static int SHORT_RECONNECT_TIME = 5;
	public static final int MAX_STATUSES = 10;
	private final static String PREF_CHAT_SPLIT = "chatSplit";
	private final static String PREF_STATUS_LIST = "statusList";
	public static final String ON_CLICK = "onClick";
	public static final String LINK = "link";
	private static final Logger LOG = Logger.getLogger(IcechatPanel.class.getName());
	private String reconnectImmediately;
	// private Router router;
	private JLabel statusLabel;
	private JLabel progressTitle;
	private JProgressBar progress;
	private CardLayout cardLayout;
	private JPanel main;
	private String status = "Idle";
	// private Simulator simulator;
	private JLabel errorTitle;
	private JLabel errorCountdown;
	private int reconnectIn;
	private Timer reconnectTimer;
	private JLabel loginMessage;
	private JTextArea entry;
	private ChannelType currentChannel = ChannelType.REGION;
	private Map<ChannelType, String> subChannels = new EnumMap<>(ChannelType.class);
	private JList<Persona> personas;
	private DefaultListModel<Persona> personalModel;
	private JList<Persona> friends;
	private DefaultListModel<Persona> friendModel;
	private Persona persona;
	private boolean scrollLock;
	private JLabel channelBox;
	private JButton disconnect;
	private Address address;
	private JComboBox<String> myStatus;
	private DefaultComboBoxModel<String> myStatusModel;
	private final Font chatFont;
	private final Address[] routerUrls;
	private List<String> commandHistory = new ArrayList<String>();
	private String textBeforeSearch;
	private int currentHistoryIndex;
	private final IcechatContext context;
	private JPopupMenu channelPopup;
	private boolean selected;
	private Map<String, Persona> friendStatus = new HashMap<>();
	private JTabbedPane tabs;
	private List<ChatTab> chatTabs = new ArrayList<>();
	private boolean requestedDisconnect;
	// private long playerSpawnId;
	private Client client;

	public void close() {
		if (client != null) {
			client.close();
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setStatusDisconnected();
			}
		});
	}

	public IcechatPanel(IcechatContext context, Address[] routerUrls) {
		this.routerUrls = routerUrls;
		this.context = context;
		if (routerUrls.length == 1) {
			address = routerUrls[0];
		}

		// Load fancy fany
		URL u = getClass().getClassLoader().getResource("MAIAN.TTF");
		try {
			InputStream in = u.openStream();
			try {
				chatFont = Font.createFont(Font.TRUETYPE_FONT, in);
			} finally {
				in.close();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		setLayout(new BorderLayout(0, 4));

		// Main cards
		main = new JPanel();
		cardLayout = new CardLayout();
		main.setLayout(cardLayout);
		add(main, BorderLayout.CENTER);

		main.add(createProgress(), "progress");
		main.add(createLogin(), "login");
		main.add(createError(), "error");
		main.add(createPersonaSelect(), "personas");
		main.add(createChat(), "chat");
		main.add(createServerChoice(), "servers");

		// Default chat tab
		addTab(createChatTab(null, "Default"));

		// Status
		add(createStatus(), BorderLayout.SOUTH);
		setStatus();
		showServerChoice();
	}

	public boolean receivedMessage(final SimulatorMessage mesg) {
		// if (mesg instanceof CreatureEventReplyMessage) {
		// CreatureEventReplyMessage plm = (CreatureEventReplyMessage) mesg;
		// if (plm.getType().equals(CreatureEventReplyMessage.Type.LOGOUT)) {
		// LOG.info(String.format("Player logout: %s", mesg));
		// // incomingMessage(ChannelType.SYSTEM, "Player logout " +
		// // plm.getSpawnId(), null, null);
		// loggedIn.remove(plm.getSpawnId());
		// new Thread() {
		// @Override
		// public void run() {
		// try {
		// loadFriends();
		// } catch (Exception ex) {
		// incomingMessage(ChannelType.ERROR, "Failed to load friends. "
		// + ex.getMessage(), null, null);
		// }
		// }
		// }.start();
		// return true;
		// } else if
		// (plm.getType().equals(CreatureEventReplyMessage.Type.SET_AVATAR)) {
		// if (loggedIn.contains(plm.getSpawnId())) {
		// LOG.warning(String.format("Got player login message again for %d, is this really 'login'?",
		// plm.getSpawnId()));
		// } else {
		// LOG.info(String.format("Player login: %s", mesg));
		// // incomingMessage(ChannelType.SYSTEM, "Player login " +
		// // plm.getSpawnId(), null, null);
		// loggedIn.add(plm.getSpawnId());
		// // new Thread() {
		// // @Override
		// // public void run() {
		// // try {
		// // loadFriends();
		// // } catch (Exception ex) {
		// // incomingMessage(ChannelType.ERROR,
		// // "Failed to load friends. " + ex.getMessage(), null,
		// // null);
		// // }
		// // }
		// // }.start();
		// }
		// return true;
		// }
		// }
		return false;
	}

	private void addTab(final ChatTab tab) {
		LOG.info(String.format("Adding tab %s", tab));
		int index = tabs.getTabCount();
		tabs.addTab(tab.getPlayer(), null, tab.getComponent(), null);
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		JButton b = new JButton(new CloseIcon(12));
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int index = chatTabs.indexOf(tab);
				tabs.removeTabAt(index);
				chatTabs.remove(index);
			}
		});
		Icon icon = new ColorIcon(Iceswing.toColor(tab.getChannel() == null ? ChannelType.REGION.getColor() : tab.getChannel()
				.getColor()));
		JLabel l = new JLabel(tab.getPlayer(), icon, JLabel.LEFT);
		p.add(l, BorderLayout.CENTER);
		if (tab.getChannel() != null || !tab.getPlayer().equals("Default")) {
			p.add(b, BorderLayout.EAST);
		}
		tabs.setTabComponentAt(index, p);
		chatTabs.add(tab);
	}

	private void previousHistory() {
		if (commandHistory.size() > 0) {
			if (textBeforeSearch == null) {
				textBeforeSearch = entry.getText();
				currentHistoryIndex = commandHistory.size();
			}
			currentHistoryIndex--;
			if (currentHistoryIndex < 0) {
				currentHistoryIndex = 0;
			}
			entry.setText(commandHistory.get(currentHistoryIndex));
		}
	}

	private void nextHistory() {
		if (commandHistory.size() > 0) {
			if (textBeforeSearch != null) {
				currentHistoryIndex++;
				if (currentHistoryIndex >= commandHistory.size()) {
					// Scrolled back to originally typed type
					entry.setText(textBeforeSearch);
					textBeforeSearch = null;
					currentHistoryIndex = -1;
				} else {
					entry.setText(commandHistory.get(currentHistoryIndex));
				}
			}
		}
	}

	private void doConnect() {
		try {
			URI serverUrl = new URI(address.getRouter());
			setStatusText("Connecting " + serverUrl);
			client = new Client(serverUrl);
			client.connect();
			client.addListener(this);
			Thread.sleep(1000);
			setStatusText("Connected to " + client.getRouter().getSimulatorHost() + ":" + client.getRouter().getSimulatorPort());
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					showCard("login");
				}
			});
		} catch (InterruptedException | URISyntaxException | NetworkException e) {
			LOG.log(Level.SEVERE, "Failed to connect.", e);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ex) {
			}
			client = null;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					showErrorAndReconnect(e.getMessage());
				}
			});
		} finally {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					LOG.info("Re-enabling panel");
					main.setEnabled(true);
				}
			});
		}
	}

	private void showErrorAndReconnect(String text) {
		showErrorAndReconnect(text, LONG_RECONNECT_TIME);
	}

	private void showErrorAndReconnect(final String text, final int time) {
		LOG.info("Showing error '" + text + "' and reconnecting in " + time);
		setStatusText("Error!");
		errorTitle.setText(text);
		reconnectIn = time;
		reconnectTimer = new Timer(1000, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				errorCountdown.setText("Reconnecting in " + reconnectIn);
				if (reconnectIn == 0) {
					reconnectTimer.stop();
					connect();
				} else {
					reconnectIn--;
				}
			}
		});
		reconnectTimer.setInitialDelay(0);
		reconnectTimer.setRepeats(true);
		reconnectTimer.start();
		showCard("error");
	}

	private void showCard(final String name) {
		LOG.info(String.format("Showing card %s", name));
		cardLayout.show(main, name);
	}

	private void setStatusText(final String statusText) {
		this.status = statusText;
		setStatus();
	}

	private void setStatus() {
		statusLabel.setText(status);
		channelBox.setBackground(Iceswing.toColor(currentChannel.getColor()));
		String s = subChannels.get(currentChannel);
		channelBox.setText(s == null ? toMnemonic(currentChannel) : toInitials(s));
		if (s != null) {
			channelBox.setToolTipText(s);
		} else {
			channelBox.setToolTipText("On " + Icelib.toEnglish(currentChannel));
		}
		final boolean connected = address != null && client != null && client.isConnected();
		disconnect.setEnabled(connected);
		channelBox.setEnabled(connected && selected);
	}

	private String toMnemonic(ChannelType t) {
		return t.name().substring(0, 1);
	}

	private String toInitials(String name) {
		StringBuilder ini = new StringBuilder();
		for (String s : name.split("\\s+")) {
			ini.append(s.charAt(0));
		}
		return ini.toString();
	}

	private JPanel createSetStatus() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout("fill", "[][grow]", "[]"));

		JLabel l = new JLabel("Status:");
		l.setFont(UIManager.getFont("Label.font").deriveFont(10f));
		p.add(l);

		myStatus = new JComboBox<>();
		myStatusModel = new DefaultComboBoxModel<>();

		// Get remembered statuses
		StringTokenizer s = new StringTokenizer(context.getPreferences().get(PREF_STATUS_LIST, ""), "\n");
		while (s.hasMoreTokens()) {
			final String nextToken = s.nextToken();
			myStatusModel.addElement(nextToken);
		}
		myStatusModel.setSelectedItem(null);

		myStatus.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final String newStatus = (String) myStatus.getSelectedItem();
				if (e.getActionCommand().equals("comboBoxEdited")) {

					myStatusModel.removeElement(newStatus);
					myStatus.addItem(newStatus);

					StringBuilder b = new StringBuilder();
					for (int i = 0; i < myStatusModel.getSize(); i++) {
						if (b.length() > 0) {
							b.append("\n");
						}
						b.append(myStatusModel.getElementAt(i));
					}
					context.getPreferences().put(PREF_STATUS_LIST, b.toString());
					while (myStatusModel.getSize() > MAX_STATUSES) {
						myStatusModel.removeElementAt(0);
					}

				} else {
					new Thread() {
						@Override
						public void run() {
							final String newStatus = (String) myStatus.getSelectedItem();
							try {
								client.setStatus(newStatus);
							} catch (NumberFormatException | NetworkException ex) {
								incomingMessage(ChannelType.ERROR, "Failed to set status. " + ex.getMessage(), null, null);
							}
						}
					}.start();
				}
				entry.requestFocusInWindow();
			}
		});
		myStatus.setModel(myStatusModel);
		myStatus.setEditable(true);
		p.add(myStatus, "growx");
		return p;
	}

	private JPanel createStatus() {
		JPanel p = new JPanel();
		p.setBorder(BorderFactory.createEtchedBorder());
		p.setLayout(new MigLayout("fill, ins 2", "[32!][grow][]", "[]"));

		// Channel popup
		channelPopup = new JPopupMenu();
		for (final ChannelType t : ChannelType.values()) {
			if (t.isPrimary()) {
				AbstractAction channelAction = new AbstractAction(Icelib.toEnglish(t)) {
					@Override
					public void actionPerformed(ActionEvent e) {
						currentChannel = t;
						setStatus();
						entry.requestFocusInWindow();
						entry.setCaretPosition(entry.getDocument().getLength());
					}
				};
				channelAction.putValue(Action.SMALL_ICON, new ColorIcon(Iceswing.toColor(t.getColor())));
				channelPopup.add(channelAction);
			}
		}

		channelBox = new JLabel();
		channelBox.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (channelBox.isEnabled()) {
					channelPopup.show(channelBox, e.getX(), e.getY());
				}
			}
		});
		channelBox.setFont(UIManager.getFont("Label.font").deriveFont(10f));
		channelBox.setOpaque(true);
		channelBox.setHorizontalTextPosition(SwingConstants.CENTER);
		channelBox.setHorizontalAlignment(SwingConstants.CENTER);
		channelBox.setForeground(Color.BLACK);
		p.add(channelBox, "growy, growx");

		statusLabel = new JLabel();
		statusLabel.setMinimumSize(new Dimension(100, 16));
		statusLabel.setFont(UIManager.getFont("Label.font").deriveFont(10f));
		p.add(statusLabel);

		disconnect = new JButton("Disconnect");
		disconnect.setFont(UIManager.getFont("Button.font").deriveFont(10f));
		disconnect.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				requestedDisconnect = true;
				close();
				showServerChoice();
			}
		});
		p.add(disconnect);
		return p;
	}

	private void setStatusDisconnected() {
		selected = false;
		client = null;
		progressTitle.setText("Disconnected from Planet Forever");
		setStatus();
		setStatusText("Disconnected");
	}

	private JPanel createProgress() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout("wrap 1, fill", "push[align center]push", "push[][]push"));
		p.add(progressTitle = new JLabel());
		p.add(progress = new JProgressBar());
		progress.setIndeterminate(true);
		return p;
	}

	private JPanel createError() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout("hidemode 1, wrap 1, fill", "push[align center]push", "push[][][]push"));
		p.add(errorTitle = new JLabel());
		errorTitle.setForeground(Color.red);
		p.add(errorCountdown = new JLabel());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				reconnectTimer.stop();
				reconnectIn = 0;
				showServerChoice();
			}
		});
		p.add(cancel);
		p.setVisible(routerUrls.length > 1);
		return p;
	}

	private JPanel createServerChoice() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout("wrap 1, fill", "[align center]", "push[]20[align center]push"));

		p.add(new JLabel("Select Server"));

		JPanel inner = new JPanel();
		inner.setLayout(new MigLayout("wrap 1", "[align center]", "[]"));

		for (final Address s : routerUrls) {
			JLabel l = new JLabel(s.getRouter());
			l.setForeground(new Color(64, 64, 255));
			l.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
			inner.add(l);
			l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			l.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					address = s;
					setStatus();
					connect();
				}
			});
		}
		p.add(inner);
		return p;
	}

	private JPanel createLogin() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout("wrap 2, fill", "push[][]push", "push[][][]push"));

		loginMessage = new JLabel();
		loginMessage.setForeground(Color.red);
		p.add(loginMessage, "span 2");
		p.add(new JLabel("Username"));
		final JTextField username = new JTextField(15);
		username.requestFocusInWindow();
		p.add(username);
		p.add(new JLabel("Password"));
		final JTextField password = new JPasswordField(15);
		p.add(password);
		username.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				password.requestFocusInWindow();
			}
		});

		final ActionListener actionListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				login(username.getText(), password.getText().toCharArray());
			}
		};
		password.addActionListener(actionListener);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
		final JButton login = new JButton("Login");
		login.addActionListener(actionListener);
		buttons.add(login);
		p.add(buttons, "span 2, ax 50%");
		return p;
	}

	private JPanel createPersonaSelect() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout("wrap 1, fill, ins 0", "[grow]", "[grow]"));

		personas = new JList<>();
		personas.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				if (e.getClickCount() == 2) {
					selectPersona(personas.getSelectedValue());
				}
			}
		});
		personas.setCellRenderer(new PersonaRender());
		personalModel = new DefaultListModel<>();
		personas.setModel(personalModel);

		JScrollPane scroller = new JScrollPane(personas);
		scroller.setBorder(BorderFactory.createLineBorder(Color.gray));
		p.add(scroller, "growx, growy");

		return p;
	}

	private void selectPersona(final Persona persona) {
		this.persona = persona;
		setStatusText("Selecting " + persona.getDisplayName());
		new Thread() {
			@Override
			public void run() {
				try {
					doSelectPersona();
					loadFriends();
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							startChat();
						}
					});
				} catch (Exception e) {
					client.close();
					LOG.log(Level.SEVERE, "Failed to select persona. ", e);
					showErrorAndReconnect("Failed to select persona. " + e.getMessage());
				}
			}
		}.start();
	}

	private void doSelectPersona() throws Exception {
		Spawn spawn = client.select(persona);
		client.setClientLoading(false);
		client.move(spawn.getServerLocation(), 0, 0, (short)0, true);
	}

	/*
	 * 
	 * private void doSelectPersona() throws Exception {
	 * 
	 * // Wait for some more initial messages simulator.sendAndAwaitReplies(new
	 * SelectPersonaMessage(simulator, (short) personalModel.indexOf(persona)),
	 * new Simulator.ReplyCallback() {
	 * 
	 * @Override public Simulator.ReplyAction onReply(SimulatorMessage mesg) {
	 * if (mesg instanceof LobbyErrorMessage) { // Will get disconnected shortly
	 * anyway reconnectImmediately = ((LobbyErrorMessage) mesg).getMessage();
	 * return Simulator.ReplyAction.HANDLED; } else if (mesg instanceof
	 * ProtocolChangeReply) { // Now in game mode
	 * simulator.setMode(Simulator.ProtocolState.GAME); return
	 * Simulator.ReplyAction.HANDLED; } else if (mesg instanceof
	 * EnvironmentUpdateMessage) { EnvironmentUpdateMessage terrainMesg =
	 * (EnvironmentUpdateMessage) mesg; return Simulator.ReplyAction.HANDLED; }
	 * else if (mesg instanceof SystemMessage) { if (shardName == null) {
	 * shardName = (((SystemMessage) mesg).getMessage()); } else { zoneName =
	 * (((SystemMessage) mesg).getMessage()); return
	 * Simulator.ReplyAction.RETURN; } } else if (mesg instanceof
	 * CreatureEventReplyMessage) { CreatureEventReplyMessage cerm =
	 * (CreatureEventReplyMessage) mesg;
	 * if(cerm.getType().equals(Type.SET_AVATAR)) { playerSpawnId =
	 * cerm.getSpawnId(); LOG.info("); return Simulator.ReplyAction.HANDLED; } }
	 * return Simulator.ReplyAction.SKIP; } });
	 * incomingMessage(ChannelType.SYSTEM, "You are in " + shardName + " (" +
	 * zoneName + ")", null, null);
	 * 
	 * 
	 * simulator.sendMessage(new RequestSpawnUpdateMessage(playerSpawnId));
	 * simulator.sendMessage(new GameQueryMessageWithReply("client.loading",
	 * "true") {
	 * 
	 * @Override public boolean isWaitForReply() { return false; }
	 * 
	 * @Override protected boolean onReply(SimulatorMessage reply) {
	 * LOG.info("Ack of client loading"); return true; } });
	 * 
	 * loadFriends();
	 * 
	 * 
	 * simulator.sendMessage(new GameQueryMessageWithReply("client.loading",
	 * "false") {
	 * 
	 * @Override public boolean isWaitForReply() { return false; }
	 * 
	 * @Override protected boolean onReply(SimulatorMessage reply) {
	 * LOG.info("Ack of client not loading"); return true; } }); selected =
	 * true; simulator.startSimulatorPing(); SwingUtilities.invokeLater(new
	 * Runnable() {
	 * 
	 * @Override public void run() { setStatus(); } }); }
	 */

	private ChatTab createChatTab(ChannelType channel, String name) {

		final JTextPane chat = new JTextPane();
		chat.setFont(chatFont.deriveFont(16f));
		chat.setEditable(false);
		chat.setBackground(Color.BLACK);
		chat.setSelectionColor(Color.white);
		chat.setSelectedTextColor(Color.black);
		chat.setToolTipText("Ctrl+Mouse Wheel to zoom");
		final JScrollPane scroller = new JScrollPane(chat);
		scroller.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if ((e.getModifiers() & MouseWheelEvent.CTRL_MASK) != 0) {
					final int size = chat.getFont().getSize();
					final int newSize = size + (e.getWheelRotation() * -1);
					chat.setFont(chat.getFont().deriveFont((float) Math.max(8, newSize)));
				} else {
				}

			}
		});
		scroller.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				System.out.println(e + " (" + scroller.getVerticalScrollBar().getMaximum());
				if (!e.getValueIsAdjusting()) {
					scrollLock = e.getValue() == scroller.getVerticalScrollBar().getMaximum();
				}
			}
		});
		scroller.setBorder(BorderFactory.createLineBorder(Color.gray));
		final ChatTab chatTab = new ChatTab(channel, name, scroller, chat);
		scroller.putClientProperty("chatTab", chatTab);

		chat.addMouseMotionListener(new MouseMotionListener() {
			@Override
			public void mouseDragged(MouseEvent e) {
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				Element el = getElementAtPoint(chatTab, e.getPoint());
				AttributeSet as = el.getAttributes();
				if (as.getAttribute(LINK) != null) {
					chat.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					return;
				}
				chat.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			}
		});
		chat.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				chat.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == 1) {
					Element el = getElementAtPoint(chatTab, e.getPoint());
					AttributeSet as = el.getAttributes();
					if (as.getAttribute(LINK) != null) {
						String link = (String) as.getAttribute(LINK);
						try {
							context.openUrl(link);
						} catch (Exception ex) {
							LOG.log(Level.SEVERE, "Failed to open link.", ex);
							incomingMessage(ChannelType.ERROR, "Failed to open link. " + ex.getMessage(), null, null);
						}
					} else {
						findOnClick(el);
					}
				}
			}
		});

		return chatTab;
	}

	private JSplitPane createChat() {

		JPanel p = new JPanel();
		p.setLayout(new MigLayout("wrap 1, fill, ins 0", "[grow]", "[][grow][48:48:]"));

		p.add(createSetStatus(), "growx");

		tabs = new JTabbedPane();

		p.add(tabs, "growx, growy");

		JPanel entryPanel = new JPanel(new MigLayout("fill, ins 0", "[grow]", "[]"));
		entry = new JTextArea();
		entry.setRows(3);
		entry.setColumns(85);
		ActionMap am = entry.getActionMap();
		InputMap im = entry.getInputMap();
		am.put(im.get(KeyStroke.getKeyStroke("UP")), new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				previousHistory();
			}
		});
		am.put(im.get(KeyStroke.getKeyStroke("DOWN")), new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				nextHistory();
			}
		});
		((AbstractDocument) entry.getDocument()).setDocumentFilter(new DocumentFilter() {
			public static final int MAX_CHAT_LENGTH = 200;

			@Override
			public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
					throws BadLocationException {
				if (offset + text.length() < MAX_CHAT_LENGTH) {
					super.replace(fb, offset, length, text, attrs);
				} else {
					Toolkit.getDefaultToolkit().beep();
				}

			}
		});
		entry.setWrapStyleWord(true);
		entry.setLineWrap(true);
		entry.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == KeyEvent.VK_ENTER) {
					sendMessage(entry.getText());
					entry.setText("");
					e.consume();
				}
			}
		});

		JScrollPane entryScroller = new JScrollPane(entry, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		entryPanel.add(entryScroller, "growx, growy");
		p.add(entryPanel, "");

		JPanel f = new JPanel(new MigLayout("fill, wrap 3", "[][grow][]", "[][grow]"));

		// Add friends
		f.add(new JLabel("Friend:"));
		final JTextField addFriendName = new JTextField();
		ActionListener addFriendListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				addFriend(addFriendName.getText());
			}
		};
		f.add(addFriendName, "growx, growy");
		addFriendName.setToolTipText("Enter name of friend to add");
		addFriendName.addActionListener(addFriendListener);
		JButton add = new JButton("+");
		add.setToolTipText("Add Friend");
		add.addActionListener(addFriendListener);
		f.add(add);

		// Friend list and popup
		final JPopupMenu popup = new JPopupMenu();
		popup.add(new AbstractAction("Remove friend") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (JOptionPane.showConfirmDialog(friends, "Are you sure you want to remove "
						+ friends.getSelectedValue().getDisplayName(), "Remove Friend", JOptionPane.YES_NO_OPTION) == JOptionPane.OK_OPTION) {
					removeSelectedFriend();
				}
			}
		});
		popup.add(new AbstractAction("Send private message") {
			@Override
			public void actionPerformed(ActionEvent e) {
				final String friendName = friends.getSelectedValue().getDisplayName();
				tellFriend(friendName);
			}
		});

		friends = new JList<>();
		friends.setCellRenderer(new FriendRender());
		friends.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					final String friendName = friends.getSelectedValue().getDisplayName();
					tellFriend(friendName);
				} else if (e.getButton() == 3) {
					if (friends.getSelectedIndex() == -1) {
						friends.setSelectedIndex(friends.locationToIndex(e.getPoint()));
					}
					popup.show(friends, e.getX(), e.getY());
				}
			}
		});
		friendModel = new DefaultListModel<>();
		friends.setBackground(Color.black);
		friends.setForeground(Color.white);
		friends.setModel(friendModel);
		friends.setVisibleRowCount(10);

		JScrollPane friendsScroller = new JScrollPane(friends);
		friendsScroller.setBorder(BorderFactory.createLineBorder(Color.gray));
		f.add(friendsScroller, "span 3, growx, growy");

		// Split
		final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, p, f);
		split.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().equals("dividerLocation")) {
					context.getPreferences().putInt(PREF_CHAT_SPLIT, split.getDividerLocation());
				}
			}
		});
		split.setDividerLocation(context.getPreferences().getInt(PREF_CHAT_SPLIT, 300));
		split.setOneTouchExpandable(true);
		split.setResizeWeight(1);
		return split;
	}

	private Element getElementAtPoint(ChatTab tab, Point p) {
		int docPos = tab.getChat().viewToModel(p);
		if (docPos != -1) {
			DefaultStyledDocument doc = (DefaultStyledDocument) tab.getChat().getDocument();
			Element el = doc.getCharacterElement(docPos);
			if (el != null) {
				return el;
			}

		}
		return null;
	}

	private boolean findOnClick(Element el) {
		AttributeSet as = findAttributeSetWith(el, ON_CLICK);
		if (as != null) {
			Runnable r = (Runnable) as.getAttribute(ON_CLICK);
			if (r != null) {
				r.run();
				return true;
			}
		}
		return false;
	}

	private AttributeSet findAttributeSetWith(Element el, String attrName) {
		AttributeSet as = el.getAttributes();
		if (as != null) {
			Object r = (Object) as.getAttribute(attrName);
			if (r != null) {
				return as;
			}
		}
		if (!el.isLeaf()) {
			for (int i = 0; i < el.getElementCount(); i++) {
				AttributeSet as2 = findAttributeSetWith(el.getElement(i), attrName);
				if (as2 != null) {
					return as2;
				}
			}
		}
		return null;
	}

	private void addFriend(final String name) {
		new Thread() {
			@Override
			public void run() {
				try {
					client.addFriend(name);
				} catch (Exception ex) {
					incomingMessage(ChannelType.ERROR, "Failed to add friend. " + ex.getMessage(), null, null);
				}
			}
		}.start();
	}

	private void removeSelectedFriend() {
		new Thread() {
			@Override
			public void run() {
				final Persona friend = friends.getSelectedValue();
				try {
					client.removeFriend(friend.getDisplayName());
				} catch (Exception ex) {
					incomingMessage(ChannelType.ERROR, "Failed to remove friend. " + ex.getMessage(), null, null);
				} finally {
					try {
						loadFriends();
					} catch (Exception ex) {
						incomingMessage(ChannelType.ERROR, "Failed to load friends. " + ex.getMessage(), null, null);
					}
				}
			}
		}.start();
	}

	private void tellFriend(final String friendName) {
		entry.setText("/tell \"" + friendName + "\" ");
		entry.requestFocusInWindow();
		entry.setCaretPosition(entry.getDocument().getLength());
	}

	private int findEnd(String text) {
		// Find end of scheme
		int idx = text.indexOf('/');
		if (idx != -1) {
			idx = text.indexOf('/', idx + 1);
			if (idx != -1) {
				// This is end of scheme, look for start of path
				int pidx = text.indexOf('/', idx + 1);
				if (pidx == -1) {
					// If no more slashes, this is just a hostname
					for (int i = idx + 1; i < text.length(); i++) {
						if (!isUrlHostChar(text.charAt(i))) {
							return i;
						}
					}
				} else {
					// Look for next non URL path character
					for (int i = pidx + 1; i < text.length(); i++) {
						if (!isUrlPathChar(text.charAt(i))) {
							return i;
						}
					}
				}
				return text.length();
			}
		}
		return -1;
	}

	private void appendSoFar(final ChatTab chat, StringBuilder bui, SimpleAttributeSet as) throws BadLocationException {
		if (bui.length() > 0) {
			chat.getChat().getDocument().insertString(chat.getChat().getDocument().getLength(), bui.toString(), as);
			bui.setLength(0);
		}
	}

	private void appendMessage(final ChatTab chat, final Color color, final boolean bold, final String text, final Runnable onClick)
			throws BadLocationException {

		if (chat.getChat().getDocument().getLength() > 0) {
			chat.getChat().getDocument().insertString(chat.getChat().getDocument().getLength(), "\n", null);
		}
		SimpleAttributeSet as = new SimpleAttributeSet();
		StyleConstants.setForeground(as, color);
		if (onClick != null) {
			as.addAttribute(ON_CLICK, onClick);
		}
		StyleConstants.setBold(as, bold);

		SimpleAttributeSet ls = new SimpleAttributeSet(as);
		StyleConstants.setBold(ls, true);
		StyleConstants.setUnderline(ls, true);
		StyleConstants.setForeground(ls, new Color(64, 64, 255));

		// Look for links
		StringBuilder bui = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			String str = text.substring(i);
			if (str.startsWith("http://") || str.startsWith("https://")) {
				appendSoFar(chat, bui, as);
				int idx = findEnd(str);
				if (idx != -1) {
					String linkText = str.substring(0, idx);
					SimpleAttributeSet als = new SimpleAttributeSet(ls);
					als.addAttribute(LINK, linkText);
					chat.getChat().getDocument().insertString(chat.getChat().getDocument().getLength(), linkText, als);
					i += idx - 1;
				}
			} else {
				bui.append(text.charAt(i));
			}
		}
		appendSoFar(chat, bui, as);
		if (!scrollLock) {
			chat.getChat().setCaretPosition(chat.getChat().getDocument().getLength());
			chat.getChat().scrollRectToVisible(chat.getChat().getVisibleRect());
		}
	}

	private boolean isUrlHostChar(char lastChar) {
		return Arrays.asList('.', '-', '_', ':').contains(lastChar) || Character.isAlphabetic(lastChar)
				|| Character.isDigit(lastChar);
	}

	private boolean isUrlPathChar(char lastChar) {
		return Arrays.asList('/', '.', '-', '_', '~', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '@').contains(
				lastChar)
				|| Character.isAlphabetic(lastChar) || Character.isDigit(lastChar);
	}

	private void sendMessage(String message) {
		while (message.endsWith("\n")) {
			message = message.substring(0, message.length() - 1);
		}

		commandHistory.add(message);
		currentHistoryIndex = -1;
		textBeforeSearch = null;

		if (message.startsWith("/")) {
			// Commands
			if (command(message)) {
				return;
			}

			// Channel
			try {
				message = message.trim();
				int idx = message.indexOf(" ");
				ChannelType t = ChannelType.valueOf((idx == -1 ? message.substring(1) : message.substring(1, idx)).toUpperCase());
				if (!t.isChat()) {
					throw new Exception("Not a chat channel.");
				}
				currentChannel = t;

				if (idx == -1) {
					return;
				}
				message = message.substring(idx + 1);
			} catch (Exception e) {
				incomingMessage(ChannelType.ERROR, "Failed to change channel. " + e.getMessage(), null, null);
			}
		}

		String subChannel = null;
		if (currentChannel.hasSubChannel()) {
			// Handle sub-channel name, e.g. the private channel needs a player
			// name
			int sidx = message.indexOf('"');
			if (sidx != -1) {
				int eidx = message.indexOf('"', sidx + 1);
				if (eidx != -1) {
					subChannel = message.substring(sidx + 1, eidx);
					message = message.substring(eidx + 1).trim();
					subChannels.put(currentChannel, subChannel);
					LOG.info(String.format("Channel %s is now on sub-channel %s", currentChannel, subChannel));
				}
			}

			if (subChannel == null) {
				subChannel = subChannels.get(currentChannel);
			}

			if (subChannel == null) {
				LOG.log(Level.SEVERE, String.format("Need sub-channel for %s ", currentChannel));
				return;
			}

		}

		String param = null;
		if (currentChannel.equals(ChannelType.TELL)) {
			param = subChannel;
		} else if (currentChannel.equals(ChannelType.GM)) {
			// TODO is this hardcoded?
			param = "earthsages";
		}

		if (currentChannel.equals(ChannelType.TELL)) {
			incomingMessage(currentChannel, message, null, param);
		}

		doSendMessage(currentChannel.format(), param, message);
		setStatus();
	}

	private boolean command(String cmd) {
		try {
			if (cmd.equals("/who")) {
				client.getSimulator().sendMessage(new GameQueryMessage("who"));
			} else if (cmd.startsWith("/me")) {
				int idx = cmd.indexOf(' ');
				if (idx != -1) {
					sendMessage("*" + cmd.substring(idx + 1) + "*");
				}
				return true;
			} else {
				return false;
			}
			return true;
		} catch (Exception e) {
			incomingMessage(ChannelType.ERROR, "Failed to run command. " + e.getMessage(), null, null);
			return true;
		}
	}

	private void showPersonaSelection() {
		showCard("personas");
		new Thread() {
			@Override
			public void run() {
				try {
					loadPersonas();
				} catch (NetworkException | NumberFormatException ex) {
					LOG.log(Level.SEVERE, "Failed to load personas.", ex);
					client.close();
					showErrorAndReconnect("Failed to load personas. " + ex.getMessage());
				}
			}
		}.start();
	}

	private void incomingMessage(final ChannelType channel, String message, final String sender, final String recipient) {
		if (channel.equals(ChannelType.EMOTE)) {
			return;
		}

		boolean action = false;
		if (message.startsWith("*") && message.endsWith("*")) {
			action = true;
			message = message.substring(1, message.length() - 1);
		}
		processIncomingMessage(action, channel, message, sender, recipient);
	}

	private void processIncomingMessage(final boolean action, final ChannelType channel, final String message, final String sender,
			final String recipient) {

		final StringBuilder bu = new StringBuilder();
		if (!channel.equals(ChannelType.SAY) && !channel.equals(ChannelType.SYSTEM)) {
			bu.append("[");
			bu.append(Icelib.toEnglish(channel));
			bu.append("] ");
		}
		if (sender != null) {
			if (action) {
				bu.append(sender);
				bu.append(' ');
			} else {
				if (channel.equals(ChannelType.TELL)) {
					bu.append(sender);
					bu.append(" tells you: ");
				} else {
					bu.append(sender);
					bu.append(" says: ");
				}
			}
		} else {
			if (action) {
				bu.append(persona.getDisplayName());
				bu.append(' ');
			} else if (channel.isChat()) {
				if (channel.hasSubChannel()) {
					bu.append("You tell ");
					bu.append(recipient);
					bu.append(":");
				} else {
					bu.append(persona.getDisplayName());
					bu.append(" says: ");
				}
			} else {
				// bu.append(Icelib.toEnglish(channel.name()));
				// bu.append(": ");
			}
		}
		bu.append(message);

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					List<ChatTab> t = new ArrayList<>();
					t.add(getTab(null, "Default"));
					if (channel.equals(ChannelType.TELL)) {
						ChatTab privateTab = getTab(ChannelType.TELL, sender);
						if (privateTab == null) {
							if (recipient != null) {
								privateTab = getTab(ChannelType.TELL, recipient);
							}
							if (privateTab == null) {
								privateTab = createChatTab(ChannelType.TELL, sender == null ? recipient : sender);
								addTab(privateTab);
							}
						}
						t.add(privateTab);
					}

					for (ChatTab tab : t) {
						appendMessage(tab, Iceswing.toColor(channel.getColor()), action, bu.toString(), new Runnable() {
							@Override
							public void run() {
								if (channel.isChat()) {
									if (sender != null) {
										entry.setText("/tell \"" + sender + "\" ");
										entry.requestFocusInWindow();
										entry.setCaretPosition(entry.getDocument().getLength());
									}
								}
							}
						});
					}
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to append to chat.", e);
				}
			}
		});

		if (sender == null || !sender.equals(persona.getDisplayName())) {
			if (!context.isActive()) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						PoptartManager.getInstance().popup(message, new ColorIcon(Iceswing.toColor(channel.getColor())),
								new ActionListener() {
									@Override
									public void actionPerformed(ActionEvent e) {
									}
								});

					}
				});
			}
		}
	}

	private ChatTab getTab(ChannelType type, String player) {
		LOG.info(String.format("There are %d tabs", tabs.getTabCount()));
		for (ChatTab ct : chatTabs) {
			boolean isChannel = (ct.getChannel() == null && type == null)
					|| (ct.getChannel() != null && type != null && type.equals(ct.getChannel()));
			boolean isPlayer = (ct.getPlayer() == null && player == null)
					|| (ct.getPlayer() != null && player != null && player.equals(ct.getPlayer()));
			if (isChannel && isPlayer) {
				return ct;
			}
		}
		return null;
	}

	private void startChat() {
		setStatusText("Chatting as " + persona.getDisplayName());
		showCard("chat");
		String implementationVersion = IcechatPanel.class.getPackage().getImplementationVersion();
		if (implementationVersion == null) {
			implementationVersion = "DEV";
		}
		// doSendMessage(ChannelType.REGION.toCode(), null, "[[Icechat " +
		// implementationVersion +
		// " Tester - http://tinyurl.com/ems-icechat]]");
		doSendMessage(ChannelType.REGION.format(), null, "[[Icechat " + implementationVersion + " Testing]]");
		entry.requestFocusInWindow();
	}

	private void login(final String username, final char[] password) {
		setStatusText("Logging in as " + username);
		new Thread() {
			@Override
			public void run() {
				try {
					client.login(username, password);
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							setStatusText("Logged in as " + username);
							showPersonaSelection();
						}
					});
				} catch (final Exception e) {
					LOG.log(Level.SEVERE, "Failed to login. ", e);
					reconnectImmediately = e.getMessage();
					close();
					// SwingUtilities.invokeLater(new Runnable() {
					// @Override
					// public void run() {
					// loginMessage.setText("Failed to login. " +
					// e.getMessage());
					// connect();
					// }
					// });
				}
			}
		}.start();
	}

	private void loadPersonas() throws NetworkException, NumberFormatException {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				personalModel.clear();
			}
		});
		for (final Persona p : client.getPersonas()) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					personalModel.addElement(p);
				}
			});
		}
	}

	private void showServerChoice() {
		showCard("servers");
	}

	public void connect() {
		// Connect
		progressTitle.setText("Connecting to Planet Forever");
		cardLayout.show(main, "progress");
		setStatus();
		new Thread() {
			@Override
			public void run() {
				doConnect();
			}
		}.start();
	}

	private void doSendMessage(final String channel, final String subChannel, final String message) {
		new Thread() {
			@Override
			public void run() {
				try {
					client.sendMessage(ChannelType.parseChannelName(channel), subChannel, message);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to send message.", e);
					incomingMessage(ChannelType.ERROR, "Failed to send messge. " + e.getMessage(), null, null);
				}
			}
		}.start();
	}

	@Override
	public void inventoryUpdate() {
	}

	@Override
	public void zoneChanged(Zone zone) {
	}

	@Override
	public void emote(long id, String sender, String emote) {
	}

	@Override
	public void disconnected(final Exception e) {
		LOG.log(Level.SEVERE, "Simulator disconnected.", e);
		client.close();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setStatusDisconnected();

				if (requestedDisconnect) {
					LOG.info("User requested disconnect, will not reconnect.");
					requestedDisconnect = false;
				} else {
					if (reconnectImmediately != null) {
						showErrorAndReconnect(reconnectImmediately, SHORT_RECONNECT_TIME);
						reconnectImmediately = null;
					} else {
						showErrorAndReconnect(e == null ? "Disconnected" : e.getMessage());
					}
				}
			}
		});
	}

	@Override
	public void spawned(Spawn spawn) {
	}

	@Override
	public void chatMessage(String sender, String recipient, ChannelType channel, String text) {
		incomingMessage(channel, text, sender, recipient);
	}

	@Override
	public void message(String message) {
		incomingMessage(ChannelType.SYSTEM, message, null, null);
	}

	@Override
	public void propAddedOrUpdated(SceneryItem prop) {
	}

	@Override
	public void propDeleted(SceneryItem prop) {
	}

	@Override
	public void playerLoggedIn(String name) {
		LOG.info(String.format("Player login: %s", name));
		if (friendStatus.containsKey(name)) {
			try {
				Persona p = friendStatus.get(name);
				p.setOnline(true);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						friends.repaint();
					}
				});
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to load friends.", e);
			}
		}
	}

	@Override
	public void playerLoggedOut(String name) {
		LOG.info(String.format("Player logout: %s", name));
		if (friendStatus.containsKey(name)) {
			Persona p = friendStatus.get(name);
			p.setOnline(false);
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					friends.repaint();
				}
			});
		}

	}

	@Override
	public void statusChanged(final String name, final String status) {
		Persona p = friendStatus.get(name);
		if (p != null) {
			p.setStatusText(status);
			incomingMessage(ChannelType.FRIENDS, String.format("%s's status now '%s'", name, status), null, null);
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					friends.repaint();
				}
			});
		}
	}

	@Override
	public void shardChanged(String name, String shard) {
		Persona p = friendStatus.get(name);
		if (p != null) {
			p.setShard(shard);
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					friends.repaint();
				}
			});
		}
	}

	@Override
	public void friendAdded(String name) {
		new Thread() {
			public void run() {
				try {
					loadFriends();
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to load friends.", e);
				}
			}
		}.start();
	}

	private synchronized List<Persona> loadFriends() throws Exception {
		// Get friends
		friendStatus.clear();
		final List<Persona> f = client.getFriends();
		Collections.sort(f, new Comparator<Persona>() {
			@Override
			public int compare(Persona o1, Persona o2) {
				int i = Boolean.valueOf(o1.isOnline()).compareTo(o2.isOnline()) * -1;
				if (i == 0) {
					return o1.getDisplayName().compareTo(o2.getDisplayName());
				}
				return i;
			}
		});
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				friendModel.clear();
				for (Persona gc : f) {
					friendModel.addElement(gc);
					friendStatus.put(gc.getDisplayName(), gc);
				}
			}
		});
		return f;
	}

	@Override
	public void popup(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				PoptartManager.getInstance().popup(message, new ColorIcon(Iceswing.toColor(ChannelType.SYSTEM.getColor())),
						new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
							}
						});
			}
		});

	}

}
