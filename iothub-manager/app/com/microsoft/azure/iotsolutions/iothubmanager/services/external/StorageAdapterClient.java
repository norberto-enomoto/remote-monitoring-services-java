// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.services.external;

import com.google.inject.Inject;
import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.*;
import com.microsoft.azure.iotsolutions.iothubmanager.services.runtime.IServicesConfig;
import play.Logger;
import play.libs.Json;
import play.libs.ws.*;

import java.util.concurrent.*;

public class StorageAdapterClient implements IStorageAdapterClient {
    private WSClient wsClient;
    private static final Logger.ALogger log = Logger.of(StorageAdapterClient.class);
    private final String serviceUri;

    private static <A> A fromJson(String json, Class<A> clazz) {
        return Json.fromJson(Json.parse(json), clazz);
    }

    @Inject
    public StorageAdapterClient(WSClient wsClient, IServicesConfig config) {
        this.wsClient = wsClient;
        this.serviceUri = config.getStorageAdapterServiceUrl();
    }

    @Override
    public CompletionStage<ValueApiModel> getAsync(String collectionId, String key)
        throws ResourceNotFoundException, ConflictingResourceException, ExternalDependencyException {
        WSRequest request = wsClient.url(String.format("%s/collections/%s/values/%s", this.serviceUri, collectionId, key));
        WSResponse response;
        try {
            response = request.get().toCompletableFuture().get();
        } catch (Exception e) {
            throw new CompletionException(
                String.format("Unable to get collection - %s with key - %s", collectionId, key), e);
        }
        CheckStatusCode(response, request);
        return CompletableFuture.supplyAsync(() -> fromJson(response.getBody(), ValueApiModel.class));
    }

    @Override
    public CompletionStage<ValueListApiModel> getAllAsync(String collectionId)
        throws ResourceNotFoundException, ConflictingResourceException, ExternalDependencyException {
        WSRequest request = wsClient.url(String.format("%s/collections/%s/values", this.serviceUri, collectionId));

        return request.get()
            .thenApplyAsync(m -> {
                try {
                    CheckStatusCode(m, request);
                } catch (Exception e) {
                    throw new CompletionException("Unable to get all " + collectionId, e);
                }
                return fromJson(m.getBody(), ValueListApiModel.class);
            });
    }

    @Override
    public CompletionStage<ValueApiModel> createAsync(String collectionId, String value)
        throws ResourceNotFoundException, ConflictingResourceException, ExternalDependencyException {
        ValueApiModel model = new ValueApiModel();
        model.setData(value);
        WSRequest request = wsClient.url(String.format("%s/collections/%s/values", this.serviceUri, collectionId));
        return request.post(Json.toJson(model)).thenApplyAsync(m -> {
            try {
                CheckStatusCode(m, request);
                return fromJson(m.getBody(), ValueApiModel.class);
            } catch (Exception e) {
                throw new CompletionException("Unable to create resource " + request.getUrl(), e);
            }
        });
    }

    @Override
    public CompletionStage<ValueApiModel> updateAsync(String collectionId, String key, String value, String etag)
        throws ResourceNotFoundException, ConflictingResourceException, ExternalDependencyException {
        ValueApiModel model = new ValueApiModel();
        model.setData(value);
        model.setETag(etag);
        WSRequest request = wsClient.url(String.format("%s/collections/%s/values/%s", this.serviceUri, collectionId, key));
        return request.put(Json.toJson(model)).thenApplyAsync(m -> {
            try {
                CheckStatusCode(m, request);
                return fromJson(m.getBody(), ValueApiModel.class);
            } catch (Exception e) {
                throw new CompletionException("Unable to update resource " + request.getUrl(), e);
            }
        });
    }

    @Override
    public CompletionStage deleteAsync(String collectionId, String key)
        throws ResourceNotFoundException, ConflictingResourceException, ExternalDependencyException {
        WSRequest request = wsClient.url(String.format("%s/collections/%s/values/%s", this.serviceUri, collectionId, key));
        return request.delete().thenAcceptAsync(m -> {
            try {
                CheckStatusCode(m, request);
            } catch (Exception e) {
                throw new CompletionException("Unable to delete resource " + request.getUrl(), e);
            }
        });
    }

    private void CheckStatusCode(WSResponse response, WSRequest request)
        throws ResourceNotFoundException, ConflictingResourceException, ExternalDependencyException {
        if (response.getStatus() == 200) {
            return;
        }
        log.info(String.format("StorageAdapter returns %s for request %s",
            response.getStatus(), request.getUrl().toString()));
        switch (response.getStatus()) {
            case 404:
                throw new ResourceNotFoundException(
                    response.getBody() + ", request URL = " + request.getUrl());

            case 409:
                throw new ConflictingResourceException(
                    response.getBody() + ", request URL = " + request.getUrl());

            default:
                throw new ExternalDependencyException(
                    String.format("WS-request failed, status code = %s, content = %s, request URL = %s", response.getStatus(), response.getBody(), request.getUrl()));
        }
    }
}
