// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.devicetelemetry.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.exceptions.ExternalDependencyException;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.exceptions.InvalidInputException;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.exceptions.ResourceNotFoundException;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.exceptions.ResourceOutOfDateException;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.external.IDiagnosticsClient;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.models.AlarmCountByRuleServiceModel;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.models.AlarmServiceModel;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.models.RuleServiceModel;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.runtime.IServicesConfig;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.serialization.JsonHelper;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class Rules implements IRules {

    private final static String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZZ";
    private static final Logger.ALogger log = Logger.of(Rules.class);

    private final String storageUrl;
    private final WSClient wsClient;

    private final IAlarms alarmsService;

    private final IDiagnosticsClient diagnosticsClient;

    @Inject
    public Rules(
        final IServicesConfig servicesConfig,
        final WSClient wsClient,
        final IAlarms alarmsService,
        final IDiagnosticsClient diagnosticsClient) {

        this.storageUrl = servicesConfig.getKeyValueStorageUrl() + "/collections/rules/values";
        this.wsClient = wsClient;
        this.alarmsService = alarmsService;
        this.diagnosticsClient = diagnosticsClient;
    }

    public CompletionStage<RuleServiceModel> getAsync(String id) {
        return this.prepareRequest(id)
            .get()
            .handle((result, error) -> {

                if (error != null) {
                    log.error("Key value storage request error: {}",
                        error.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(error.getMessage()));
                }

                if (result.getStatus() == HttpStatus.SC_NOT_FOUND) {
                    log.info("Rule id " + id + " not found.");
                    return null;
                }

                try {
                    return getServiceModelFromJson(Json.parse(result.getBody()));
                } catch (Exception e) {
                    log.error("Could not parse result from Key Value Storage: {}",
                        e.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(
                            "Could not parse result from Key Value Storage"));
                }
            });
    }

    public CompletionStage<List<RuleServiceModel>> getListAsync(
        String order,
        int skip,
        int limit,
        String groupId,
        boolean includeDeleted) {

        if (skip < 0 || limit <= 0) {
            log.error("Key value storage parameter bounds error");
            throw new CompletionException(
                new InvalidInputException("Parameter bounds error"));
        }

        return this.prepareRequest(null)
            .get()
            .handle((result, error) -> {

                if (error != null) {
                    log.error("Key value storage request error: {}",
                        error.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(error.getMessage()));
                }

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonResult = mapper.readTree(result.getBody());

                    ArrayList<JsonNode> jsonList =
                        getResultListFromJson(jsonResult);

                    ArrayList<RuleServiceModel> ruleList = new ArrayList<>();

                    for (JsonNode resultItem : jsonList) {
                        RuleServiceModel rule =
                            getServiceModelFromJson(resultItem);

                        if ((groupId == null ||
                            groupId.equalsIgnoreCase(rule.getGroupId()))
                            && (!rule.getDeleted() || includeDeleted)) {
                            ruleList.add(rule);
                        }
                    }

                    if (ruleList.isEmpty()) {
                        return ruleList;
                    }

                    if (order.equalsIgnoreCase("asc")) {
                        Collections.sort(ruleList);
                    } else {
                        Collections.sort(ruleList, Collections.reverseOrder());
                    }

                    if (skip >= ruleList.size()) {
                        log.debug("Skip value greater than size of listAsync");
                        return new ArrayList<>();
                    } else if ((limit + skip) > ruleList.size()) {
                        return ruleList.subList(skip, ruleList.size());
                    }

                    return ruleList.subList(skip, limit + skip);
                } catch (Exception e) {
                    log.error("Could not parse result from Key Value Storage: {}",
                        e.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(
                            "Could not parse result from Key Value Storage"));
                }
            });
    }

    @Override
    public CompletionStage<List<AlarmCountByRuleServiceModel>> getAlarmCountForList(
        DateTime from,
        DateTime to,
        String order,
        int skip,
        int limit,
        String[] devices
    ) throws ExternalDependencyException {

        ArrayList<AlarmCountByRuleServiceModel> alarmByRuleList = new ArrayList<>();

        // get list of rules
        return this.getListAsync(order, skip, limit, null, true)
            .thenApply(rulesList -> {

                // get open alarm count and most recent alarm for each rule
                for (RuleServiceModel rule : rulesList) {
                    int alarmCount;
                    try {
                        alarmCount = this.alarmsService.getCountByRuleId(
                            rule.getId(),
                            from,
                            to,
                            devices);
                    } catch (java.lang.Exception e) {
                        log.error("Could not retrieve alarm count for " +
                            "rule id {}", rule.getId(), e);
                        throw new CompletionException(
                            new ExternalDependencyException(
                                "Could not retrieve alarm count for " +
                                    "rule id " + rule.getId(), e));
                    }

                    // skip to next rule if no alarms found
                    if (alarmCount == 0) {
                        continue;
                    }

                    // get most recent alarm for rule
                    AlarmServiceModel recentAlarm = this.getMostRecentAlarmForRule(
                        rule.getId(),
                        from,
                        to,
                        devices);

                    // should always find alarm at this point
                    if (recentAlarm == null) {
                        log.error("Alarm count mismatch -- could not " +
                            "find alarm for rule id {} when alarm count for " +
                            "rule is {}.", rule.getId(), alarmCount);
                        throw new CompletionException(
                            new ExternalDependencyException(
                                "Alarm count mismatch -- could not " +
                                    "find alarm for rule id " + rule.getId()));
                    }

                    // Add alarm by rule to list
                    alarmByRuleList.add(
                        new AlarmCountByRuleServiceModel(
                            alarmCount,
                            recentAlarm.getStatus(),
                            recentAlarm.getDateCreated(),
                            rule));
                }

                return alarmByRuleList;
            });
    }

    public CompletionStage<RuleServiceModel> postAsync(
        RuleServiceModel ruleServiceModel) {

        // Ensure dates are correct
        ruleServiceModel.setDateCreated(DateTime.now(DateTimeZone.UTC).toString(DATE_FORMAT));
        ruleServiceModel.setDateModified(ruleServiceModel.getDateCreated());

        ObjectNode jsonData = new ObjectMapper().createObjectNode();
        jsonData.put("Data", ruleServiceModel.toJsonString());

        return this.prepareRequest(null)
            .post(jsonData.toString())
            .handle((result, error) -> {
                if (result.getStatus() != HttpStatus.SC_OK) {
                    log.error("Key value storage error code {}",
                        result.getStatusText());
                    throw new CompletionException(
                        new ExternalDependencyException(result.getStatusText()));
                }

                if (error != null) {
                    log.error("Key value storage request error: {}",
                        error.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(
                            "Could not connect to key value storage " +
                                error.getMessage()));
                }

                try {
                    this.logEventAndRuleCountToDiagnostics("Rule_Created");
                    return getServiceModelFromJson(
                        Json.parse(result.getBody()));
                } catch (Exception e) {
                    log.error("Could not parse result from Key Value Storage: {}",
                        e.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(
                            "Could not parse result from Key Value Storage"));
                }
            });
    }

    public CompletionStage<RuleServiceModel> upsertIfNotDeletedAsync(RuleServiceModel rule) {
        // Ensure dates are correct
        // Get the existing rule so we keep the created date correct; update the modified date to now
        RuleServiceModel savedRule = null;
        try {
            CompletableFuture<RuleServiceModel> savedRuleFuture = getAsync(rule.getId()).toCompletableFuture();
            savedRule = savedRuleFuture.get();
        } catch (Exception e) {
            log.error("Rule not found and will create new rule for Id:" + rule.getId(), e);
        }

        if (savedRule != null && savedRule.getDeleted()) {
            throw new CompletionException(
                new ResourceNotFoundException(String.format("Rule {%s} not found", rule.getId())));
        }

        return upsertAsync(rule, savedRule);
    }

    private CompletionStage<RuleServiceModel> upsertAsync(RuleServiceModel rule, RuleServiceModel savedRule) {
        // If rule does not exist and id is provided upsert rule with that id
        if (savedRule == null && rule.getId() != null) {
            rule.setDateCreated(DateTime.now(DateTimeZone.UTC).toString(DATE_FORMAT));
            rule.setDateModified(rule.getDateCreated());
        } else { // update rule with stored date created
            rule.setDateCreated(savedRule.getDateCreated());
            rule.setDateModified(DateTime.now(DateTimeZone.UTC).toString(DATE_FORMAT));
        }

        // Save the updated rule if it exists or create new rule with id
        ObjectNode jsonData = new ObjectMapper().createObjectNode();
        jsonData.put("Data", rule.toJsonString());
        jsonData.put("ETag", rule.getETag());

        return this.prepareRequest(rule.getId())
            .put(jsonData.toString())
            .handle((result, error) -> {

                if (error != null) {
                    log.error("Key value storage request error: {}",
                        error.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(error.getMessage()));
                }

                if (result.getStatus() == HttpStatus.SC_CONFLICT) {
                    log.error("Key value storage ETag mismatch");
                    throw new CompletionException(
                        new ResourceOutOfDateException(
                            "Key value storage ETag mismatch"));
                } else if (result.getStatus() != HttpStatus.SC_OK) {
                    log.error("Key value storage error code {}",
                        result.getStatusText());
                    throw new CompletionException(
                        new ExternalDependencyException(result.getStatusText()));
                }

                try {
                    RuleServiceModel updatedRule = getServiceModelFromJson(Json.parse(result.getBody()));
                    log.info("Successfully retrieved rule id " + updatedRule.getId());
                    return updatedRule;
                } catch (Exception e) {
                    log.error("Could not parse result from Key Value Storage: {}",
                        e.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(
                            "Could not parse result from Key Value Storage"));
                }
            });
    }

    public CompletionStage deleteAsync(String id) {
        RuleServiceModel savedRule;
        try {
            CompletableFuture<RuleServiceModel> savedRuleFuture = getAsync(id).toCompletableFuture();
            savedRule = savedRuleFuture.get();
            if (savedRule == null || savedRule.getDeleted()) {
                return CompletableFuture.completedFuture(true);
            }

        } catch (Exception e) {
            log.error("Could not get existing rule from Key Value Storage: {}", e.getMessage());
            throw new CompletionException(e);
        }

        savedRule.setDeleted(true);

        ObjectNode jsonData = new ObjectMapper().createObjectNode();
        jsonData.put("Data", savedRule.toJsonString());
        jsonData.put("ETag", savedRule.getETag());
        return this.prepareRequest(savedRule.getId())
            .put(jsonData.toString())
            .handle((result, error) -> {

                if (error != null) {
                    log.error("Key value storage request error: {}",
                        error.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(error.getMessage()));
                }

                if (result.getStatus() != HttpStatus.SC_OK) {
                    log.error("Key value storage error code {}",
                        result.getStatusText());
                    throw new CompletionException(
                        new ExternalDependencyException(result.getStatusText()));
                }

                this.logEventAndRuleCountToDiagnostics("Rule_Deleted");
                log.info("Successfully deleted rule id " + id);
                return true;
            });
    }

    private WSRequest prepareRequest(String id) {

        String url = this.storageUrl;
        if (id != null) {
            url = url + "/" + id;
        }

        WSRequest wsRequest = this.wsClient
            .url(url)
            .addHeader("Content-Type", "application/json");

        return wsRequest;
    }

    private AlarmServiceModel getMostRecentAlarmForRule(
        String ruleId,
        DateTime from,
        DateTime to,
        String[] devices) {

        AlarmServiceModel result = null;

        try {
            ArrayList<AlarmServiceModel> resultList
                = this.alarmsService.getListByRuleId(
                ruleId,
                from,
                to,
                "desc",
                0,
                1,
                devices);

            if (resultList.size() > 0) {
                result = resultList.get(0);
            }
        } catch (java.lang.Exception e) {
            String errorMsg = "Could not retrieve most recent alarm " +
                "for rule id " + ruleId;
            log.error(errorMsg, e);
            throw new CompletionException(
                new ExternalDependencyException(errorMsg, e));
        }

        return result;
    }

    private ArrayList<JsonNode> getResultListFromJson(JsonNode response) {

        ArrayList<JsonNode> resultList = new ArrayList<>();

        // ignore case when parsing items array
        String itemsKey = response.has("Items") ? "Items" : "items";

        for (JsonNode item : response.withArray(itemsKey)) {
            try {
                resultList.add(item);
            } catch (Exception e) {
                log.error("Could not parse data from Key Value Storage");
                throw new CompletionException(
                    new ExternalDependencyException(
                        "Could not parse data from Key Value Storage"));
            }
        }

        return resultList;
    }

    private RuleServiceModel getServiceModelFromJson(JsonNode response) {
        String jsonResultRule = null;

        try {
            jsonResultRule = JsonHelper.getNode(response, "Data").asText();
            RuleServiceModel rule = new ObjectMapper().readValue(jsonResultRule, RuleServiceModel.class);
            rule.setETag(JsonHelper.getNode(response, "ETag").asText());
            JsonNode idNode = JsonHelper.getNode(response, "id");
            if (idNode == null || idNode.asText().isEmpty()) {
                rule.setId(JsonHelper.getNode(response, "Key").asText());
            }
            return rule;
        } catch (Exception e) {
            log.error("Could not parse data from Key Value Storage. " +
                "Json result: {}", jsonResultRule);
            throw new CompletionException(
                new ExternalDependencyException(
                    "Could not parse data from Key Value Storage"));
        }
    }

    private CompletionStage<Integer> getRuleCountAsync() {
        return this.prepareRequest(null)
            .get()
            .handle((result, error) -> {
                if (error != null) {
                    log.error("Key value storage request error: {}",
                        error.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(error.getMessage()));
                }

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonResult = mapper.readTree(result.getBody());

                    ArrayList<JsonNode> jsonList =
                        getResultListFromJson(jsonResult);

                    int ruleCount = 0;

                    for (JsonNode resultItem : jsonList) {
                        RuleServiceModel rule =
                            getServiceModelFromJson(resultItem);

                        if (!rule.getDeleted()) {
                            ruleCount++;
                        }
                    }
                    return ruleCount;
                } catch (Exception e) {
                    log.error("Could not parse result from Key Value Storage: {}",
                        e.getMessage());
                    throw new CompletionException(
                        new ExternalDependencyException(
                            "Could not parse result from Key Value Storage"));
                }
            });
    }

    private void logEventAndRuleCountToDiagnostics(String eventName) {
        if (this.diagnosticsClient.canWriteToDiagnostics()) {
            this.diagnosticsClient.logEventAsync(eventName);
            this.getRuleCountAsync()
                .thenApplyAsync((ruleCount) -> {
                    Dictionary<String, Object> eventProperties = new Hashtable<>();
                    eventProperties.put("Count", ruleCount);
                    this.diagnosticsClient.logEventAsync("Rule_Count", eventProperties);
                    return true;
                });
        }
    }
}