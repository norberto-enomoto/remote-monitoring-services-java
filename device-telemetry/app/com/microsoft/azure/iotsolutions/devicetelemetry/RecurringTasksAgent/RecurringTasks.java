package com.microsoft.azure.iotsolutions.devicetelemetry.RecurringTasksAgent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.eventprocessorhost.IEventProcessorFactory;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.notification.Agent;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.notification.IAgent;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.notification.IEventProcessorHostWrapper;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.runtime.IBlobStorageConfig;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.runtime.IServicesConfig;
import play.Logger;
import play.libs.ws.WSClient;

import java.util.concurrent.CompletableFuture;

@Singleton
public class RecurringTasks implements IRecurringTasks {
    private static final Logger.ALogger log = Logger.of(RecurringTasks.class);
    private IAgent notificationAgent;
    private IServicesConfig servicesConfig;
    private IBlobStorageConfig blobStorageConfig;
    private IEventProcessorHostWrapper eventProcessorHostWrapper;
    private IEventProcessorFactory notificationEventProcessorFactory;

    @Inject
    public RecurringTasks(
            IServicesConfig servicesConfig,
            IBlobStorageConfig blobStorageConfig,
            IEventProcessorHostWrapper eventProcessorHostWrapper,
            IEventProcessorFactory notificationEventProcessorFactory
    ){
        this.servicesConfig = servicesConfig;
        this.blobStorageConfig = blobStorageConfig;
        this.eventProcessorHostWrapper = eventProcessorHostWrapper;
        this.notificationEventProcessorFactory = notificationEventProcessorFactory;
        CompletableFuture.runAsync(() -> this.run());
    }

    @Override
    public void run() {
        tryMethod();
    }

    private void tryMethod(){
        this.notificationAgent = new Agent(servicesConfig, blobStorageConfig, eventProcessorHostWrapper, notificationEventProcessorFactory);
        notificationAgent.runAsync();
        /*while(true){
            log.info("Testing...");
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }*/

    }
}
