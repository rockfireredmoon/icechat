package org.icechat;

import java.util.prefs.Preferences;

public interface IcechatContext {

    void openUrl(String url) throws Exception;
    
    Preferences getPreferences();
    
    boolean isActive();
}
