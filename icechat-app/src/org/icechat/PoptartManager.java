package org.icechat;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicArrowButton;

import com.sun.jna.platform.WindowUtils;

public class PoptartManager implements ActionListener, MouseListener {
    
    private static PoptartManager instance;
    
    private Position position = Position.bottomRight;
    private RoundedRectangleWindow popup;
    private PoptartPanel panel;
    private Timer timer;
    private List<Poptart> poptarts = new ArrayList<Poptart>();
    private GraphicsDevice screenDevice;

    PoptartManager() {
    }
    
    public static PoptartManager getInstance() {
        if(instance == null) {
            instance = new PoptartManager();
        }
        return instance;
    }
    
    public void setScreen(GraphicsDevice screenDevice) {
        this.screenDevice = screenDevice;
    }
    
    public void setPosition(Position position) {
        this.position = position;
    }
    
    public synchronized void popup(String text, Icon icon, ActionListener callback) {
        Poptart poptart = new Poptart(text, icon, callback);
        poptarts.add(poptart);        
        while(poptarts.size() > 10) {
            poptarts.remove(0);
        }
        showPoptart(poptart);
    }

    void showPoptart(Poptart poptart) {
        hidePopup();
        panel = new PoptartPanel(poptart);
        popup = new RoundedRectangleWindow(null, panel, false, screenDevice == null ? null : screenDevice.getDefaultConfiguration());
        try {
            Method m = popup.getClass().getMethod("setFocusableWindowState", new Class[] { boolean.class });
            m.invoke(popup, new Object[] { Boolean.FALSE });
        } catch (Exception e) {
        }
        try {
            Method m = popup.getClass().getMethod("setAlwaysOnTop", new Class[] { boolean.class });
            m.invoke(popup, new Object[] { Boolean.TRUE });
        } catch (Exception e) {
        }
        popup.addMouseListener(this);
        WindowUtils.setWindowAlpha(popup, 0.9f);
        popup.setLocation(position.getPosition(popup, screenDevice));
        if(!popup.isVisible()) {
            popup.setVisible(true);
        }
        if(timer == null) {
            timer = new Timer(10000, this);
            timer.setRepeats(false);
            timer.start();
        }
        else {
            timer.restart();
        }
    }

    public void actionPerformed(ActionEvent e) {
        hidePopup();
    }
    
    void hidePopup() {
        if(popup != null) {
            if(popup.isVisible()) {
                popup.setVisible(false);
            }
            popup.removeMouseListener(this);
            popup = null;
        }
    }

    public void mouseClicked(MouseEvent e) { 
    	if(panel != null && panel.getPoptart().getCallback() != null) {
    		panel.getPoptart().getCallback().actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, panel.getPoptart().getText()));
    	}      
        hidePopup(); 
    }

    public void mouseEntered(MouseEvent e) {        
    }

    public void mouseExited(MouseEvent e) {        
    }

    public void mousePressed(MouseEvent e) {        
    }

    public void mouseReleased(MouseEvent e) {        
    }
    
    public class PoptartPanel extends JPanel {
        
        private JLabel textLabel;
        private JLabel iconLabel;
        private Poptart poptart;
        
        public PoptartPanel(Poptart poptart) {
        	this.poptart = poptart;
            
            setLayout(new BorderLayout());
            setSize(new Dimension(300, 300));
            
            // Top
            iconLabel = new JLabel(poptart.getIcon());
            iconLabel.setVerticalAlignment(JLabel.CENTER);
            iconLabel.setOpaque(true);
            iconLabel.setBackground(UIManager.getDefaults().getColor("ToolTip.background").darker());
            iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            
            // Text
            textLabel = new JLabel(poptart.getText());
            textLabel.setVerticalAlignment(JLabel.CENTER);
            textLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            textLabel.setForeground(UIManager.getDefaults().getColor("ToolTip.foreground").darker());
            
            // Close
            JButton closeButton = new BasicArrowButton(BasicArrowButton.NORTH);
            closeButton.setVerticalAlignment(BasicArrowButton.TOP);
            JPanel close = new JPanel(new BorderLayout());
            close.setOpaque(false);
            close.add(closeButton, BorderLayout.NORTH);
            close.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 8));
            closeButton.addActionListener(PoptartManager.this);
            
            // Navigation
            final int tartIdx = poptarts.indexOf(poptart);
            JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            nav.setOpaque(false);
            JButton next = new BasicArrowButton(BasicArrowButton.EAST);
            if(tartIdx >= poptarts.size() - 1) {
                next.setEnabled(false);
            }
            else {
                next.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showPoptart(poptarts.get(tartIdx + 1));
                    }
                });
            }
            JButton previous = new BasicArrowButton(BasicArrowButton.WEST);
            if(tartIdx == 0) {
                previous.setEnabled(false);
            }
            else {
                previous.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showPoptart(poptarts.get(tartIdx - 1));
                    }
                });
            }
            nav.add(previous);
            nav.add(next);
            nav.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));
            
            // Center
            JPanel center = new JPanel(new BorderLayout());
            center.add(textLabel, BorderLayout.CENTER);
            center.add(close, BorderLayout.EAST);
            center.add(nav, BorderLayout.SOUTH);
            center.setOpaque(false);
            
            //
            add(iconLabel, BorderLayout.WEST);
            add(center, BorderLayout.CENTER);
            setOpaque(true);
            setForeground(UIManager.getDefaults().getColor("ToolTip.foreground"));
            setBackground(UIManager.getDefaults().getColor("ToolTip.background").brighter());
            setFont(UIManager.getDefaults().getFont("ToolTip.font"));
        }
        
        public Poptart getPoptart() {
        	return poptart;
        }
    }
    
    class Poptart {
        String text;
        Icon icon;
        ActionListener callback;
        
        Poptart(String text, Icon icon, ActionListener callback) {
            this.text = text;
            this.icon = icon;
            this.callback = callback;
        }
        
        public ActionListener getCallback() {
        	return callback;
        }

        public String getText() {
            return text;
        }

        public Icon getIcon() {
            return icon;
        }
    }
    
}
