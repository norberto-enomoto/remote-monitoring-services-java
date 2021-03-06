// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.uiconfig.services.runtime;

import java.util.List;

/**
 * Service layer configuration
 */
public class ServicesConfig implements IServicesConfig {

    private String azureMapsKey;
    private String seedTemplate;
    private String storageAdapterApiUrl;
    private String deviceSimulationApiUrl;
    private String telemetryApiUrl;
    private String userManagementApiUrl;

    public ServicesConfig() {
    }

    public ServicesConfig(String telemetryApiUrl, String storageAdapterApiUrl, String deviceSimulationApiUrl,
                          String seedTemplate, String azureMapsKey) {
        this.storageAdapterApiUrl = storageAdapterApiUrl;
        this.deviceSimulationApiUrl = deviceSimulationApiUrl;
        this.seedTemplate = seedTemplate;
        this.telemetryApiUrl = telemetryApiUrl;
        this.azureMapsKey = azureMapsKey;
    }

    @Override
    public String getAzureMapsKey() {
        return azureMapsKey;
    }

    public void setAzureMapsKey(String azureMapsKey) {
        this.azureMapsKey = azureMapsKey;
    }

    @Override
    public String getTelemetryApiUrl() {
        return telemetryApiUrl;
    }

    public void setTelemetryApiUrl(String telemetryApiUrl) {
        this.telemetryApiUrl = telemetryApiUrl;
    }


    @Override
    public String getSeedTemplate() {
        return seedTemplate;
    }

    @Override
    public String getStorageAdapterApiUrl() {
        return storageAdapterApiUrl;
    }

    public void setStorageAdapterApiUrl(String storageAdapterApiUrl) {
        this.storageAdapterApiUrl = storageAdapterApiUrl;
    }

    @Override
    public String getDeviceSimulationApiUrl() {
        return this.deviceSimulationApiUrl;
    }


    public void setDeviceSimulationApiUrl(String deviceSimulationApiUrl) {
        this.deviceSimulationApiUrl = deviceSimulationApiUrl;
    }

    @Override
    public String getUserManagementApiUrl() { return this.userManagementApiUrl; }

    public void setUserManagementApiUrl(String userManagementApiUrl) {
        this.userManagementApiUrl = userManagementApiUrl;
    }
}