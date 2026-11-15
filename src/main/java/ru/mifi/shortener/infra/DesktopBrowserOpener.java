package ru.mifi.shortener.infra;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import ru.mifi.shortener.core.service.BrowserOpener;

public class DesktopBrowserOpener implements BrowserOpener {

    @Override
    public boolean open(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return false;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
            return true;
        } catch (IOException | URISyntaxException | UnsupportedOperationException e) {
            return false;
        }
    }
}
