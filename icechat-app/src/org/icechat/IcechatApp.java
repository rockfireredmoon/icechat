package org.icechat;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.SystemUtils;
import org.icelib.XDesktop;

public class IcechatApp extends JFrame implements IcechatContext {
    
    private static final Logger LOG = Logger.getLogger(IcechatApp.class.getName());
    private final static String PREF_WINDOW_X = "windowX";
    private final static String PREF_WINDOW_Y = "windowY";
    private final static String PREF_WINDOW_W = "windowW";
    private final static String PREF_WINDOW_H = "windowH";
    private final static String PREF_CLOSE_OPERATION = "windowCloseOp";
    private final IcechatPanel iceChat;
    private boolean usingTrayIcon;
    private MenuItem showWindow;
    private final Address[] addresses;
    
    public IcechatApp(Address[] routerUrls) {
        super("Icechat");
        this.addresses = routerUrls;
        final ImageIcon icon = new ImageIcon(IcechatApp.class.getResource("/Ice-icon.png"));
        setIconImage(icon.getImage());
        setLayout(new BorderLayout());
        iceChat = new IcechatPanel(this, routerUrls);
        add(iceChat, BorderLayout.CENTER);
        
        if (SystemTray.isSupported()) {
            configureSystemTray();
        }
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                getPreferences().putInt(PREF_WINDOW_X, getX());
                getPreferences().putInt(PREF_WINDOW_Y, getY());
                getPreferences().putInt(PREF_WINDOW_W, getWidth());
                getPreferences().putInt(PREF_WINDOW_H, getHeight());
                if (!usingTrayIcon) {
                    System.exit(0);
                } else {
                    int closeOp = getPreferences().getInt(PREF_CLOSE_OPERATION, -1);
                    if (closeOp == -1) {
                        JPanel p = new JPanel();
                        p.setLayout(new BorderLayout(0, 10));
                        p.add(new JLabel("<html>When closing Icechat you can either have it<br/>"
                                + "shutdown completely, or minimize to your icon<br/>"
                                + "tray, but stay running.</html>"), BorderLayout.CENTER);
                        JCheckBox remember = new JCheckBox("Remember this choice");
                        p.add(remember, BorderLayout.SOUTH);
                        closeOp = JOptionPane.showOptionDialog(IcechatApp.this,
                                p, "Close Icechat", JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE, null, new String[]{"Minimise to tray", "Close", "Cancel"}, "Cancel");
                        if (closeOp != 2 && remember.isSelected()) {
                            getPreferences().putInt(PREF_CLOSE_OPERATION, closeOp);
                        }
                    }
                    
                    if (closeOp != 2) {
                        if (closeOp == 0) {
                            showWindow.setLabel("Show chat window");
                            setVisible(false);
                        } else {
                            System.exit(0);
                        }
                    }
                }
            }
        });
        
        Rectangle r = new Rectangle(getPreferences().getInt(PREF_WINDOW_X, 0),
                getPreferences().getInt(PREF_WINDOW_Y, 0),
                getPreferences().getInt(PREF_WINDOW_W, 550),
                getPreferences().getInt(PREF_WINDOW_H, 400));
        System.out.println(r);
        setBounds(r);
        
    }
    
    public void start() {
        if (addresses.length == 1) {
            iceChat.connect();
        }
    }
    

    public static CommandLine parseCommandLine(Options opts, String[] args) throws ParseException {
        CommandLineParser parse = new GnuParser();
        CommandLine cmdLine = parse.parse(opts, args);

        // Set logging level
        Level level = Level.WARNING;
        if (cmdLine.hasOption('S')) {
            level = Level.OFF;
        } else if (cmdLine.hasOption('Q')) {
            level = Level.WARNING;
        } else if (cmdLine.hasOption('V')) {
            level = Level.INFO;
        } else if (cmdLine.hasOption('D')) {
            level = Level.FINE;
        } else if (cmdLine.hasOption('T')) {
            level = Level.FINEST;
        }
        Logger root = Logger.getLogger("");
        Handler[] handlers = root.getHandlers();
        for (Handler h : handlers) {
            root.setLevel(level);
            h.setLevel(level);
        }
        return cmdLine;
    }
    
    public static void main(String[] xargs) throws Exception {
        if ("true".equalsIgnoreCase(System.getProperty("icechat.noire", "true"))) {
            try {
                UIManager.setLookAndFeel("com.jtattoo.plaf.noire.NoireLookAndFeel");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to load skin.", e);
                try {
                	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
                catch(Exception ex) {
                	// Give up
                }
            }
        }
        
        Options opts = new Options();
        opts.addOption("S", "silent", false, "Silent");
        opts.addOption("T", "trace", false, "Debug logging");
        opts.addOption("D", "debug", false, "Debug logging");
        opts.addOption("V", "verbose", false, "Verbose logging");
        CommandLine commandLIne = parseCommandLine(opts, xargs);
        String[] args = commandLIne.getArgs();
        
        String[] defaultRouterUrls = {"http://localhost"};
        String[] routerUrls = null;
        if (args.length > 0) {
            routerUrls = args;
        }
        if (routerUrls == null) {
            final String property = System.getProperty("routerUrls");
            if (property != null) {
                routerUrls = property.split("\\s+");
            }
        }
        if (routerUrls == null) {
            routerUrls = defaultRouterUrls;
        }
        

		Address[] addresses = new Address[routerUrls.length];
		int i = 0;
		for (String s : routerUrls) {
			addresses[i++] = new Address(s);
		}
        
        final IcechatApp ia = new IcechatApp(addresses);
        ia.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                ia.start();
            }
        });
        ia.setVisible(true);
    }
    
    @Override
    public void openUrl(String url) throws Exception {
        XDesktop.getDesktop().browse(new URI(url));
    }
    
    @Override
    public final Preferences getPreferences() {
        return Preferences.userRoot().node("icechat");
    }
    
    private void configureSystemTray() throws HeadlessException {
        SystemTray st = SystemTray.getSystemTray();
        Dimension ts = st.getTrayIconSize();
        String ticon = "/Ice-icon.png";
        if (SystemUtils.IS_OS_LINUX) {
            ticon = "/Medium-Ice-icon.png";
        } else {
            ticon = "/Small-Ice-icon.gif";
        }
        final ImageIcon tIconObj = new ImageIcon(IcechatApp.class.getResource(ticon));
        try {
            final TrayIcon trayIcon = new TrayIcon(tIconObj.getImage());
            String implementationVersion = IcechatPanel.class.getPackage().getImplementationVersion();
            if (implementationVersion == null) {
                implementationVersion = "DEV";
            }
            trayIcon.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleVisible();
                }
            });
            trayIcon.setToolTip("Icechat " + implementationVersion);
            
            showWindow = new MenuItem("Hide chat window");
            showWindow.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleVisible();
                }
            });
            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    System.exit(0);
                }
            });
            final PopupMenu popupMenu = new PopupMenu("Icechat");
            trayIcon.setPopupMenu(popupMenu);
            popupMenu.add(showWindow);
            trayIcon.getPopupMenu().addSeparator();
            trayIcon.getPopupMenu().add(exit);
            st.add(trayIcon);
            usingTrayIcon = true;
        } catch (AWTException ex) {
            LOG.log(Level.SEVERE, "Failed to load tray icon.", ex);
        }
    }
    
    private void toggleVisible() {
        if (isVisible()) {
            showWindow.setLabel("Show chat window");
            setVisible(false);
        } else {
            showWindow.setLabel("Hide chat window");
            setVisible(true);
        }
    }
}
