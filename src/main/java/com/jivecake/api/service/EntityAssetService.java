package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;

public class EntityAssetService {
    public Datastore datastore;

    @Inject
    public EntityAssetService(Datastore datastore) {
        this.datastore = datastore;
    }

    public List<EntityAsset> getMostRecentSelfiesPerUser() {
        List<EntityAsset> assets = this.datastore.createQuery(EntityAsset.class)
            .field("assetType").equal(AssetType.IMGUR_IMAGE)
            .field("entityType").equal(EntityType.USER)
            .order("entityId, -timeCreated")
            .asList();

        List<EntityAsset> result = new ArrayList<>();

        if (!assets.isEmpty()) {
            ObjectId last = assets.get(0).id;
            result.add(assets.get(0));

            for (int index = 1, size = assets.size(); index < size; index++) {
                EntityAsset asset = assets.get(index);

                if (!asset.id.equals(last)) {
                    result.add(asset);
                    last = asset.id;
                }
            }
        }

        return result;
    }
}
