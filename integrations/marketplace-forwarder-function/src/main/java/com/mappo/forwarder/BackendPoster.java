package com.mappo.forwarder;

import java.util.Map;

interface BackendPoster {

    HttpPostResult postJson(String endpoint, Object payload, double timeoutSeconds, Map<String, String> headers);
}
