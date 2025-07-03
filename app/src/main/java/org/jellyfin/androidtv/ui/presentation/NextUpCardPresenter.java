package org.jellyfin.androidtv.ui.presentation;

import org.jellyfin.androidtv.constant.ImageType;

/**
 * Card presenter specifically for Next Up items that displays vertical poster cards
 * instead of horizontal thumbnail cards for a more modern, Netflix-like appearance.
 */
public class NextUpCardPresenter extends CardPresenter {
    
    public NextUpCardPresenter() {
        // Use poster image type for vertical cards and show info
        super(true, ImageType.POSTER, 120);
    }
}