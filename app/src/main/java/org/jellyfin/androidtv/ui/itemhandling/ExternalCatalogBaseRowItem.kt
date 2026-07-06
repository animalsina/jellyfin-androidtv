package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalCatalogItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

class ExternalCatalogBaseRowItem(
	val catalogItem: ExternalCatalogItem,
) : BaseRowItem(
	baseRowType = BaseRowType.BaseItem,
	staticHeight = false,
	selectAction = BaseRowItemSelectAction.Play,
	baseItem = catalogItem.toBaseItemDto(),
) {
	override val itemId: UUID = catalogItem.stableId
	override val showCardInfoOverlay: Boolean = true
	override val externalImageUrl: String? = catalogItem.posterUrl ?: catalogItem.backdropUrl
	override val externalBackdropUrl: String? = catalogItem.backdropUrl ?: catalogItem.posterUrl

	override fun getCardName(context: Context) = catalogItem.title
	override fun getFullName(context: Context) = catalogItem.title
	override fun getName(context: Context) = catalogItem.title
	override fun getSubText(context: Context) = when {
		catalogItem.localItemId != null -> context.getString(R.string.lbl_external_catalog_available_on_server)
		!catalogItem.availabilityNote.isNullOrBlank() -> catalogItem.availabilityNote
		else -> catalogItem.providerName
	}
	override fun getSummary(context: Context) = buildString {
		append(catalogItem.providerName)
		if (catalogItem.localItemId != null) append(" · ").append(context.getString(R.string.lbl_external_catalog_available_on_server))
		else if (catalogItem.isFree) append(" · ").append(context.getString(R.string.lbl_external_catalog_free_badge))
		catalogItem.releaseDate?.takeIf { it.isNotBlank() }?.let { append(" · ").append(context.getString(R.string.lbl_external_catalog_release_date, it)) }
		catalogItem.group?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
	}
}

private fun ExternalCatalogItem.toBaseItemDto() = BaseItemDto(
	id = stableId,
	type = type,
	mediaType = MediaType.VIDEO,
	name = title,
	overview = buildString {
		append(providerName)
		if (localItemId != null) append(" · available on your server")
		else if (isFree) append(" · free")
		releaseDate?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
		group?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
		availabilityNote?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
	},
	primaryImageAspectRatio = 2.0 / 3.0,
)
