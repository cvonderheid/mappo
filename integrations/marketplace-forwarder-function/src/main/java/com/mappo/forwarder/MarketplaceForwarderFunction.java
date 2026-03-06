package com.mappo.forwarder;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class MarketplaceForwarderFunction {

    private final BackendPoster backendPoster;
    private final Map<String, String> environment;

    public MarketplaceForwarderFunction() {
        this(new JavaHttpBackendPoster(), System.getenv());
    }

    MarketplaceForwarderFunction(BackendPoster backendPoster, Map<String, String> environment) {
        this.backendPoster = backendPoster;
        this.environment = environment;
    }

    @FunctionName("marketplaceEvents")
    public HttpResponseMessage marketplaceEvents(
        @HttpTrigger(
            name = "request",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.FUNCTION,
            route = "marketplace/events"
        ) HttpRequestMessage<Optional<String>> request,
        ExecutionContext context
    ) {
        return handle(request, context);
    }

    @FunctionName("marketplaceEventsResource")
    public HttpResponseMessage marketplaceEventsResource(
        @HttpTrigger(
            name = "request",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.FUNCTION,
            route = "marketplace/events/resource"
        ) HttpRequestMessage<Optional<String>> request,
        ExecutionContext context
    ) {
        return handle(request, context);
    }

    private HttpResponseMessage handle(HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        Logger logger = context == null ? Logger.getLogger(MarketplaceForwarderFunction.class.getName()) : context.getLogger();
        MarketplaceForwarderHandler handler = new MarketplaceForwarderHandler(backendPoster, environment, logger);
        ForwarderResponse response = handler.handle(
            request.getBody().orElse(""),
            normalizeHeaders(request.getHeaders())
        );

        return request.createResponseBuilder(HttpStatus.valueOf(response.statusCode()))
            .header("Content-Type", "application/json")
            .body(response.body())
            .build();
    }

    private Map<String, String> normalizeHeaders(Map<String, String> requestHeaders) {
        Map<String, String> normalized = new LinkedHashMap<>();
        requestHeaders.forEach((key, value) -> {
            if (key != null) {
                normalized.put(key.toLowerCase(Locale.ROOT), value);
            }
        });
        return normalized;
    }
}
