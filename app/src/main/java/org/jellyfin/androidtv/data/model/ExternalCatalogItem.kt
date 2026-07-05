package org.jellyfin.androidtv.data.model

import org.jellyfin.sdk.model.api.BaseItemKind
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ExternalCatalogItem(
	val providerId: String,
	val providerName: String,
	val title: String,
	val type: BaseItemKind = BaseItemKind.MOVIE,
	val streamUrl: String? = null,
	val detailUrl: String? = null,
	val posterUrl: String? = null,
	val backdropUrl: String? = null,
	val group: String? = null,
	val isFree: Boolean = true,
) {
	val stableId: UUID = UUID.nameUUIDFromBytes(
		"$providerId|$title|${streamUrl.orEmpty()}".toByteArray(StandardCharsets.UTF_8)
	)
}
