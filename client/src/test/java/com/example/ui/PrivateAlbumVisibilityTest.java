package com.example.ui;

import com.example.service.ConfigPersistenceService;
import org.junit.Test;

import javax.swing.SwingUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateAlbumVisibilityTest {

    @Test
    public void configDefaultsAlbumOff() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        assertFalse(config.showPrivateAlbum);
    }

    @Test
    public void settingsSwitchDefaultsOff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            var tabs = PanelFactory.createServerConfigBody(
                    refs,
                    com.example.ui.UiFonts.chinesePlain(13),
                    com.example.ui.UiFonts.chineseBold(13),
                    com.example.ui.UiFonts.latinPlain(13));
            assertTrue(tabs.getTabCount() >= 2);
            assertTrue(refs.privateAlbumSwitch != null);
            assertFalse(refs.privateAlbumSwitch.isSelected());
        });
    }
}