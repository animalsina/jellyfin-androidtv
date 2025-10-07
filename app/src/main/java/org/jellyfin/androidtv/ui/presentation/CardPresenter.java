package org.jellyfin.androidtv.ui.presentation;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

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
import org.jellyfin.androidtv.ui.itemhandling.BaseRowType;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.apiclient.JellyfinImage;
import org.jellyfin.androidtv.util.apiclient.JellyfinImageKt;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.UserItemDataDto;
import org.koin.java.KoinJavaComponent;

import java.util.HashMap;
import java.util.Map;

import kotlin.Lazy;

public class CardPresenter extends Presenter {

    private int mStaticHeight = 150;
    private ImageType mImageType = ImageType.POSTER;
    private double aspect;
    private boolean mShowInfo = true;
    private boolean isUniformAspect = false;
    private final Lazy<ImageHelper> imageHelper = KoinJavaComponent.inject(ImageHelper.class);

    public CardPresenter() {
        super();
    }

    public CardPresenter(boolean showInfo) {
        this();
        mShowInfo = showInfo;
    }

    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight) {
        this(showInfo, staticHeight);
        mImageType = imageType;
    }

    public CardPresenter(boolean showInfo, int staticHeight) {
        this(showInfo);
        mStaticHeight = staticHeight;
    }

    private static final Map<BaseItemKind, Drawable> DEFAULT_IMAGES_CACHE = new HashMap<>();

    static {
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.AUDIO, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.MUSIC_ALBUM, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.PERSON, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.MUSIC_ARTIST, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.SEASON, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.SERIES, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.EPISODE, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.MOVIE, null);
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.VIDEO, null);
    }
    private static void initDefaultImages(Context context) {
        if (!DEFAULT_IMAGES_CACHE.isEmpty()) return;

        DEFAULT_IMAGES_CACHE.put(BaseItemKind.AUDIO, ContextCompat.getDrawable(context, R.drawable.tile_audio));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.MUSIC_ALBUM, ContextCompat.getDrawable(context, R.drawable.tile_audio));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.PERSON, ContextCompat.getDrawable(context, R.drawable.tile_port_person));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.MUSIC_ARTIST, ContextCompat.getDrawable(context, R.drawable.tile_port_person));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.SEASON, ContextCompat.getDrawable(context, R.drawable.tile_port_tv));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.SERIES, ContextCompat.getDrawable(context, R.drawable.tile_port_tv));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.EPISODE, ContextCompat.getDrawable(context, R.drawable.tile_land_tv));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.MOVIE, ContextCompat.getDrawable(context, R.drawable.tile_port_video));
        DEFAULT_IMAGES_CACHE.put(BaseItemKind.VIDEO, ContextCompat.getDrawable(context, R.drawable.tile_port_video));
    }

    class ViewHolder extends Presenter.ViewHolder {
        private int cardWidth = 115;
        private int cardHeight = 140;

        private BaseRowItem mItem;
        private final LegacyImageCardView mCardView;
        private Drawable mDefaultCardImage;

        public ViewHolder(View view) {
            super(view);
            initDefaultImages(view.getContext());

            mCardView = (LegacyImageCardView) view;
            mDefaultCardImage = DEFAULT_IMAGES_CACHE.getOrDefault(BaseItemKind.VIDEO,
                    ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_video));

            FrameLayout trailerContainer = new FrameLayout(mCardView.getContext());
            mCardView.addView(trailerContainer);
        }

        public void setItem(BaseRowItem m) {
            setItem(m, ImageType.POSTER, mStaticHeight, mStaticHeight, mStaticHeight);
        }

        public void setItem(BaseRowItem m, ImageType imageType, int lHeight, int pHeight, int sHeight) {
            mItem = m;

            if (mItem.getBaseRowType() == BaseRowType.BaseItem) {
                BaseItemDto itemDto = mItem.getBaseItem();
                boolean showWatched = true;
                boolean showProgress = false;

                if (imageType.equals(ImageType.BANNER)) aspect = ImageHelper.ASPECT_RATIO_BANNER;
                else if (imageType.equals(ImageType.THUMB)) aspect = ImageHelper.ASPECT_RATIO_16_9;
                else if (itemDto != null) aspect = imageHelper.getValue().getImageAspectRatio(itemDto, m.getPreferParentThumb());
                else aspect = 1.0;

                mDefaultCardImage = getDefaultImage(mCardView.getContext(), itemDto.getType());

                switch (itemDto.getType()) {
                    case AUDIO:
                    case MUSIC_ALBUM:
                    case PERSON:
                    case MUSIC_ARTIST:
                        if (isUniformAspect || aspect < .8) aspect = 1.0;
                        showWatched = false;
                        break;
                    case EPISODE:
                        aspect = ImageHelper.ASPECT_RATIO_16_9;
                        showProgress = true;
                        mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                        break;
                    case MOVIE:
                    case VIDEO:
                        showProgress = true;
                        if (imageType.equals(ImageType.POSTER)) aspect = ImageHelper.ASPECT_RATIO_2_3;
                        break;
                    default:
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
                    mCardView.setProgress((int) (userData.getPlaybackPositionTicks() * 100.0 / itemDto.getRunTimeTicks()));
                } else {
                    mCardView.setProgress(0);
                }

                mCardView.setMainImageDimensions(cardWidth, cardHeight);
            } else {
                mCardView.setMainImageDimensions(cardWidth, cardHeight);
            }
        }

        public BaseRowItem getItem() {
            return mItem;
        }

        protected void updateCardViewImage(@Nullable String url, @Nullable String blurHash) {
            mCardView.getMainImageView().load(url, blurHash, mDefaultCardImage, aspect, 32);
        }

        protected void resetCardView() {
            mCardView.clearBanner();
            mCardView.setUnwatchedCount(-1);
            mCardView.setProgress(0);
            mCardView.setRating(null);
            mCardView.setBadgeImage(null);
            mCardView.getMainImageView().setImageDrawable(null);
        }
    }

    private String[] resolveImage(BaseRowItem rowItem, LegacyImageCardView cardView) {
        String imageUrl = null;
        String blurHash = null;
        BaseItemDto baseItem = rowItem.getBaseItem();
        if (baseItem != null) {
            JellyfinImage primaryImage = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
            if (primaryImage != null) {
                imageUrl = imageHelper.getValue().getImageUrl(primaryImage);
                blurHash = primaryImage.getBlurHash();
            }
        }
        if (imageUrl == null) {
            int fillWidth = Math.round(cardView.getLayoutParams().width * cardView.getResources().getDisplayMetrics().density);
            int fillHeight = Math.round(cardView.getLayoutParams().height * cardView.getResources().getDisplayMetrics().density);
            imageUrl = rowItem.getImageUrl(cardView.getContext(), imageHelper.getValue(), mImageType, fillWidth, fillHeight);
        }
        return new String[]{imageUrl, blurHash};
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        LegacyImageCardView cardView = new LegacyImageCardView(parent.getContext(), mShowInfo);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = parent.getContext().getTheme();
        theme.resolveAttribute(R.attr.cardViewBackground, typedValue, true);
        cardView.setBackgroundColor(typedValue.data);

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

        String[] imageData = resolveImage(rowItem, holder.mCardView);
        holder.updateCardViewImage(imageData[0], imageData[1]);
    }

    private Drawable getDefaultImage(Context context, BaseItemKind kind) {
        if (!DEFAULT_IMAGES_CACHE.containsKey(kind)) {
            int drawableRes;
            switch (kind) {
                case AUDIO:
                case MUSIC_ALBUM:
                    drawableRes = R.drawable.tile_audio;
                    break;
                case PERSON:
                case MUSIC_ARTIST:
                    drawableRes = R.drawable.tile_port_person;
                    break;
                case SEASON:
                case SERIES:
                    drawableRes = R.drawable.tile_port_tv;
                    break;
                case EPISODE:
                    drawableRes = R.drawable.tile_land_tv;
                    break;
                case MOVIE:
                case VIDEO:
                default:
                    drawableRes = R.drawable.tile_port_video;
                    break;
            }
            DEFAULT_IMAGES_CACHE.put(kind, ContextCompat.getDrawable(context, drawableRes));
        }
        return DEFAULT_IMAGES_CACHE.get(kind);
    }


    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder) viewHolder).resetCardView();
    }

    public void setUniformAspect(boolean uniformAspect) {
        isUniformAspect = uniformAspect;
    }
}
