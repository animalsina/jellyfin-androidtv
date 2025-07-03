package org.jellyfin.androidtv.ui.presentation;

import org.jellyfin.androidtv.constant.ImageType;

/**
 * Card presenter specifically for Continue Watching items that displays vertical poster cards
 * instead of horizontal thumbnail cards for a more modern, Netflix-like appearance.
 */
public class ContinueWatchingCardPresenter extends CardPresenter {
    
    public ContinueWatchingCardPresenter() {
        // Use poster image type for vertical cards and show info
        super(true, ImageType.POSTER, 120);
    }
}