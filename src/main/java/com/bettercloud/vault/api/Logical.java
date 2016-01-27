package com.bettercloud.vault.api;

import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.json.Json;
import com.bettercloud.vault.json.JsonObject;
import com.bettercloud.vault.response.LogicalResponse;
import com.bettercloud.vault.rest.RestResponse;
import com.bettercloud.vault.rest.Rest;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class Logical {

    private final VaultConfig config;

    public Logical(final VaultConfig config) {
        this.config = config;
    }

    /**
     * Basic read operation to retrieve a secret.
     *
     * TODO: Support reading from and writing to fields other than "value"
     *
     * @param path The Vault key value from which to read (e.g. <code>secret/hello</code>
     * @return Response metadata, as well as all values found for that key
     * @throws VaultException
     */
    public LogicalResponse read(final String path) throws VaultException {
        int retryCount = 0;
        while (true) {
            try {
                // Make an HTTP request to Vault
                final RestResponse restResponse = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/" + path)
                        .header("X-Vault-Token", config.getToken())
                        .get();

                // Validate response
                if (restResponse.getStatus() != 200) {
                    throw new VaultException("Vault responded with HTTP status code: " + restResponse.getStatus());
                }
                final String mimeType = restResponse.getMimeType() == null ? "null" : restResponse.getMimeType();
                if (!mimeType.equals("application/json")) {
                    throw new VaultException("Vault responded with MIME type: " + mimeType);
                }
                String jsonString;
                try {
                    jsonString = new String(restResponse.getBody(), "UTF-8");//NOPMD
                } catch (UnsupportedEncodingException e) {
                    throw new VaultException(e);
                }

                // Parse JSON
                final Map<String, String> data = new HashMap<String, String>();
                for (final JsonObject.Member member : Json.parse(jsonString).asObject().get("data").asObject()) {
                    data.put(member.getName(), member.getValue().asString());
                }
                return new LogicalResponse(restResponse, retryCount, data);
            } catch (Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }

    /**
     * Basic operation to store a secret.
     *
     * TODO: Support secret names other than the hard-coded "value".
     *
     * @param path The path on which the secret is to be stored (e.g. <code>secret/hello</code>)
     * @param value The secret value to be stored
     * @throws VaultException
     */
    public LogicalResponse write(final String path, final String value) throws VaultException {
        int retryCount = 0;
        while (true) {
            try {
                final RestResponse restResponse = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/" + path)
                        .body(Json.object().add("value", value).toString().getBytes("UTF-8"))
                        .header("X-Vault-Token", config.getToken())
                        .post();
                if (restResponse.getStatus() != 204) {
                    throw new VaultException("Expecting HTTP status 204, but instead receiving " + restResponse.getStatus());
                }
                return new LogicalResponse(null, retryCount);
            } catch (Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }

}