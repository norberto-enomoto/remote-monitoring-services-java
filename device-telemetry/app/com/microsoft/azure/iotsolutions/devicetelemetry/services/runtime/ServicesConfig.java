// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.devicetelemetry.services.runtime;

/**
 * Service layer configuration
 */
public class ServicesConfig implements IServicesConfig {

    private final String keyValueStorageUrl;

    private final MessagesConfig messagesConfig;

    private final AlarmsConfig alarmsConfig;

    private final DiagnosticsConfig diagnosticsConfig;

    public ServicesConfig(
        final String keyValueStorageUrl,
        MessagesConfig messagesConfig,
        AlarmsConfig alarmsConfig,
        DiagnosticsConfig diagnosticsConfig) {
        this.keyValueStorageUrl = keyValueStorageUrl;
        this.messagesConfig = messagesConfig;
        this.alarmsConfig = alarmsConfig;
        this.diagnosticsConfig = diagnosticsConfig;
    }

    /**
     * Get key value storage dependency url
     *
     * @return url for key value storage endpoint
     */
    public String getKeyValueStorageUrl() {
        return this.keyValueStorageUrl;
    }

    public MessagesConfig getMessagesConfig() {
        return this.messagesConfig;
    }

    public AlarmsConfig getAlarmsConfig() {
        return this.alarmsConfig;
    }

    public DiagnosticsConfig getDiagnosticsConfig() {
        return this.diagnosticsConfig;
    }
}
