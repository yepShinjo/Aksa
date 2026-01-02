package com.mari.aksa.page.dictionary

import com.google.gson.annotations.SerializedName

data class KamusResponseItem(
	@field:SerializedName("imageAssetName")
	val imageAssetName: String,

	@field:SerializedName("latin")
	val latin: String,

	@field:SerializedName("description_id")
	val description_id: String,

	@field:SerializedName("description_en")
	val description_en: String
)
