// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.devicetelemetry.services.models;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

public final class MessageServiceModel {
    private final String deviceId;
    private final DateTime time;
    private final Object data;

    public MessageServiceModel() {
        this.deviceId = "";
        this.time = null;
        this.data = null;
    }

    public MessageServiceModel(
        final String deviceId,
        final long time,
        final Object data) {

        this.deviceId = deviceId;
        this.time = new DateTime(time, DateTimeZone.UTC);
        this.data = data;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public Object getData() {
        return this.data;
    }

    public DateTime getTime() {
        return this.time;
    }
}
