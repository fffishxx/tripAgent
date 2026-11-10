package com.tripagent.favorite.model;

import com.tripagent.favorite.entity.FavoritePlace;

public record FavoriteAddResult(FavoritePlace favorite, boolean duplicate) {
}
