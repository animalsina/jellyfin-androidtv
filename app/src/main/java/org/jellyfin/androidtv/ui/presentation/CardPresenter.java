package org.jellyfin.androidtv.ui.presentation;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.VideoView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.Presenter;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior;
import org.jellyfin.androidtv.ui.card.LegacyImageCardView;
import org.jellyfin.androidtv.ui.itemhandling.AudioQueueBaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.apiclient.JellyfinImage;
import org.jellyfin.androidtv.util.apiclient.JellyfinImageKt;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.UserItemDataDto;
import org.koin.java.KoinJavaComponent;

import java.util.concurrent.ConcurrentHashMap;

import kotlin.Lazy;
import timber.log.Timber;

public class CardPresenter extends Presenter {

    private int mStaticHeight = 150;
    private ImageType mImageType = ImageType.POSTER;
    private double aspect;
    private boolean mShowInfo = true;
    private boolean isUniformAspect = false;
    private final Lazy<ImageHelper> imageHelper = KoinJavaComponent.<ImageHelper>inject(ImageHelper.class);

    private static final int DELAY_MS = 5000;
    private final ConcurrentHashMap<String, String> trailerCache = new ConcurrentHashMap<>();
    private static final boolean ENABLE_VIDEO_PREVIEW = false;

    public CardPresenter() { super(); }
    public CardPresenter(boolean showInfo) { this(); mShowInfo = showInfo; }
    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight) { this(showInfo, staticHeight); mImageType = imageType; }
    public CardPresenter(boolean showInfo, int staticHeight) { this(showInfo); mStaticHeight = staticHeight; }

    class ViewHolder extends Presenter.ViewHolder {
        private int cardWidth = 115;
        private int cardHeight = 140;

        private BaseRowItem mItem;
        private LegacyImageCardView mCardView;
        private Drawable mDefaultCardImage;
        private Runnable trailerRunnable;

        private VideoView videoView;
        private FrameLayout videoContainer;

        public ViewHolder(View view) {
            super(view);
            mCardView = (LegacyImageCardView) view;
            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_video);

            // Contenitore video sopra immagine
            videoContainer = new FrameLayout(mCardView.getContext());
            videoView = new VideoView(mCardView.getContext());
            videoView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            videoContainer.addView(videoView);
            mCardView.addView(videoContainer);
            videoContainer.setVisibility(View.GONE);

            Timber.v("ViewHolder created for CardPresenter");
        }

        public int getCardWidth() { return cardWidth; }
        public int getCardHeight() { return cardHeight; }

        public void setItem(BaseRowItem m) { setItem(m, ImageType.POSTER, mStaticHeight, mStaticHeight, mStaticHeight); }

        public void setItem(BaseRowItem m, ImageType imageType, int lHeight, int pHeight, int sHeight) {
            mItem = m;
            Timber.v("Setting item: %s", mItem.getBaseItem() != null ? mItem.getBaseItem().getName() : "null");

            switch (mItem.getBaseRowType()) {
                case BaseItem:
                    BaseItemDto itemDto = mItem.getBaseItem();
                    boolean showWatched = true;
                    boolean showProgress = false;

                    if (imageType.equals(ImageType.BANNER)) aspect = ImageHelper.ASPECT_RATIO_BANNER;
                    else if (imageType.equals(ImageType.THUMB)) aspect = ImageHelper.ASPECT_RATIO_16_9;
                    else aspect = imageHelper.getValue().getImageAspectRatio(itemDto, m.getPreferParentThumb());

                    switch (itemDto.getType()) {
                        case AUDIO: case MUSIC_ALBUM:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_audio);
                            if (isUniformAspect || aspect < .8) aspect = 1.0;
                            showWatched = false;
                            break;
                        case PERSON: case MUSIC_ARTIST:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_person);
                            if (isUniformAspect || aspect < .8) aspect = 1.0;
                            showWatched = false;
                            break;
                        case SEASON: case SERIES:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_tv);
                            if (imageType.equals(ImageType.POSTER)) aspect = ImageHelper.ASPECT_RATIO_2_3;
                            break;
                        case EPISODE:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_land_tv);
                            aspect = ImageHelper.ASPECT_RATIO_16_9;
                            showProgress = true;
                            mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                            break;
                        case MOVIE: case VIDEO:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_video);
                            showProgress = true;
                            if (imageType.equals(ImageType.POSTER)) aspect = ImageHelper.ASPECT_RATIO_2_3;
                            break;
                        default:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_video);
                            if (imageType.equals(ImageType.POSTER)) aspect = ImageHelper.ASPECT_RATIO_2_3;
                            break;
                    }

                    cardHeight = !m.getStaticHeight() ? (aspect > 1 ? lHeight : pHeight) : sHeight;
                    cardWidth = (int) (aspect * cardHeight);
                    if (cardWidth < 5) cardWidth = 115;

                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null) {
                        WatchedIndicatorBehavior showIndicator = KoinJavaComponent.<UserPreferences>get(UserPreferences.class)
                                .get(UserPreferences.Companion.getWatchedIndicatorBehavior());
                        if (userData.getPlayed()) {
                            mCardView.setUnwatchedCount(
                                    (showIndicator != WatchedIndicatorBehavior.NEVER &&
                                            (showIndicator != WatchedIndicatorBehavior.EPISODES_ONLY || itemDto.getType() == BaseItemKind.EPISODE))
                                            ? 0 : -1);
                        } else if (userData.getUnplayedItemCount() != null) {
                            mCardView.setUnwatchedCount(showIndicator == WatchedIndicatorBehavior.ALWAYS ? userData.getUnplayedItemCount() : -1);
                        }
                    }

                    if (showProgress && itemDto.getRunTimeTicks() != null && itemDto.getRunTimeTicks() > 0 && userData != null && userData.getPlaybackPositionTicks() > 0) {
                        mCardView.setProgress((int)(userData.getPlaybackPositionTicks() * 100.0 / itemDto.getRunTimeTicks()));
                    } else { mCardView.setProgress(0); }

                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    Timber.v("Card dimensions set: width=%d height=%d aspect=%.2f", cardWidth, cardHeight, aspect);
                    break;

                default:
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    Timber.v("Default case for non-BaseItem row type");
                    break;
            }
        }

        public BaseRowItem getItem() { return mItem; }

        protected void updateCardViewImage(@Nullable String url, @Nullable String blurHash) {
            Timber.v("Updating card image: url=%s blurHash=%s", url, blurHash);
            mCardView.getMainImageView().load(url, blurHash, mDefaultCardImage, aspect, 32);
        }

        protected void resetCardView() {
            Timber.v("Resetting card view");
            mCardView.clearBanner();
            mCardView.setUnwatchedCount(-1);
            mCardView.setProgress(0);
            mCardView.setRating(null);
            mCardView.setBadgeImage(null);
            mCardView.getMainImageView().setImageDrawable(null);

            videoView.stopPlayback();
            videoContainer.setVisibility(View.GONE);

            if (trailerRunnable != null) mCardView.removeCallbacks(trailerRunnable);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        LegacyImageCardView cardView = new LegacyImageCardView(parent.getContext(), mShowInfo);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = parent.getContext().getTheme();
        theme.resolveAttribute(R.attr.cardViewBackground, typedValue, true);
        @ColorInt int color = typedValue.data;
        cardView.setBackgroundColor(color);

        Timber.v("onCreateViewHolder: card created");
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        if (!(item instanceof BaseRowItem)) return;
        ViewHolder holder = (ViewHolder) viewHolder;
        BaseRowItem rowItem = (BaseRowItem) item;

        holder.setItem(rowItem, mImageType, 130, 150, mStaticHeight);

        holder.mCardView.setTitleText("");
        holder.mCardView.setContentText("");
        if (ImageType.POSTER.equals(mImageType)) holder.mCardView.setOverlayInfo(rowItem);
        holder.mCardView.showFavIcon(rowItem.isFavorite());
        holder.mCardView.setPlayingIndicator(rowItem instanceof AudioQueueBaseRowItem && ((AudioQueueBaseRowItem) rowItem).getPlaying());

        JellyfinImage image = rowItem.getBaseItem() != null ? JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY) : null;
        int fillWidth = Math.round(holder.getCardWidth() * holder.mCardView.getResources().getDisplayMetrics().density);
        int fillHeight = Math.round(holder.getCardHeight() * holder.mCardView.getResources().getDisplayMetrics().density);
        holder.updateCardViewImage(
                image == null ? rowItem.getImageUrl(holder.mCardView.getContext(), imageHelper.getValue(), mImageType, fillWidth, fillHeight) : imageHelper.getValue().getImageUrl(image),
                image == null ? null : image.getBlurHash()
        );

        // Focus listener per mostrare video inline
        holder.mCardView.setOnFocusChangeListener((v, hasFocus) -> {
            if (!ENABLE_VIDEO_PREVIEW) return;

            if (hasFocus) {
                holder.trailerRunnable = () -> playVideoPreview(holder, rowItem);
                holder.mCardView.postDelayed(holder.trailerRunnable, DELAY_MS);
            } else {
                if (holder.trailerRunnable != null) holder.mCardView.removeCallbacks(holder.trailerRunnable);
                holder.videoView.stopPlayback();
                holder.videoContainer.setVisibility(View.GONE);
            }
        });
    }

    private void playVideoPreview(ViewHolder holder, BaseRowItem rowItem) {
        BaseItemDto base = rowItem.getBaseItem();
        if (base == null) return;

        String itemId = base.getId().toString();
        if (!trailerCache.containsKey(itemId)) {
            String query = base.getName() + " trailer";
            fetchTrailerFromYouTube(query, new TrailerCallback() {
                @Override
                public void onTrailerFound(String site, String videoKeyOrUrl) {
                    trailerCache.put(itemId, videoKeyOrUrl);
                    startVideo(holder, videoKeyOrUrl);
                }

                @Override public void onNoTrailer() {}
                @Override public void onError(Exception e) { Timber.e(e, "Error fetching trailer"); }
            });
        } else {
            startVideo(holder, trailerCache.get(itemId));
        }
    }

    private void startVideo(ViewHolder holder, String videoId) {
        holder.videoContainer.setVisibility(View.VISIBLE);
        // ATTENZIONE: YouTube diretto non funziona in VideoView su Android TV.
        // Qui serve usare ExoPlayer con stream diretto o YouTubeExtractor.
        holder.videoView.setVideoURI(Uri.parse("https://www.youtube.com/watch?v=" + videoId));
        holder.videoView.setOnCompletionListener(mp -> holder.videoContainer.setVisibility(View.GONE));
        holder.videoView.start();
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        Timber.v("onUnbindViewHolder called");
        ((ViewHolder) viewHolder).resetCardView();
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder viewHolder) {}

    public void setUniformAspect(boolean uniformAspect) { isUniformAspect = uniformAspect; }

    private interface TrailerCallback {
        void onTrailerFound(String site, String keyOrUrl);
        void onNoTrailer();
        void onError(Exception e);
    }

    private void fetchTrailerFromYouTube(String query, TrailerCallback cb) {
        new Thread(() -> {
            try {
                Timber.v("Fetching YouTube search results for query: %s", query);
                String q = java.net.URLEncoder.encode(query, "UTF-8");
                String url = "https://www.youtube.com/results?search_query=" + q;

                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                okhttp3.Response res = client.newCall(req).execute();
                if (!res.isSuccessful() || res.body() == null) { cb.onNoTrailer(); return; }

                String body = res.body().string();
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("/watch\\?v=([a-zA-Z0-9_-]{11})");
                java.util.regex.Matcher m = p.matcher(body);

                if (m.find()) {
                    String videoId = m.group(1);
                    cb.onTrailerFound("YouTube", videoId);
                } else { cb.onNoTrailer(); }

            } catch (Exception e) { cb.onError(e); }
        }).start();
    }
}
