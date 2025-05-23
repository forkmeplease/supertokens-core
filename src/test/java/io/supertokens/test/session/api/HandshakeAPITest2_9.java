/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.session.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class HandshakeAPITest2_9 {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void inputErrorsInHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // null in request body with cdi-version set to 2.0
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/handshake", null, 1000, 1000, null, SemVer.v2_9.get(),
                    "session");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void signingKeyHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "testName");
        frontendSDKEntry.addProperty("version", "testVersion");

        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "testName");
        driver.addProperty("version", "testVersion");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject handshakeResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", deviceDriverInfo, 1000, 1000, null,
                SemVer.v2_9.get(), "session");
        checkHandshakeAPIResponse(handshakeResponse, process);
        assertEquals(handshakeResponse.entrySet().size(), 7);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void signingKeyHandshakeAPIWithCookiesTest() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("cookie_domain", "localhost");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "testName");
        frontendSDKEntry.addProperty("version", "testVersion");

        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "testName");
        driver.addProperty("version", "testVersion");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject handshakeResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", deviceDriverInfo, 1000, 1000, null,
                SemVer.v2_9.get(), "session");
        checkHandshakeAPIResponse(handshakeResponse, process);
        assertEquals(handshakeResponse.entrySet().size(), 7);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void changingSigningKeyHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00081"); // 0.00027*3 = 3 seconds
        Utils.setValueInConfig("access_token_validity", "1"); // 1 second
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String jsonInput = "{" + "\"deviceDriverInfo\": {" + "\"frontendSDK\": [{" + "\"name\": \"hName\","
                + "\"version\": \"hVersion\"" + "}]," + "\"driver\": {" + "\"name\": \"hDName\","
                + "\"version\": \"nDVersion\"" + "}" + "}" + "}";
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", new JsonParser().parse(jsonInput), 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(response.entrySet().size(), 7);

        List<String> keys = SigningKeys.getInstance(process.getProcess()).getDynamicKeys().stream()
                .map(key -> new io.supertokens.utils.Utils.PubPriKey(key.value).publicKey).collect(Collectors.toList());

        assertEquals(response.get("jwtSigningPublicKey").getAsString(), keys.get(0));

        JsonArray respPubKeyList = response.get("jwtSigningPublicKeyList").getAsJsonArray();
        assertEquals(keys.size(), respPubKeyList.size());
        for (int i = 0; i < respPubKeyList.size(); ++i) {
            String pubKey = respPubKeyList.get(i).getAsJsonObject().get("publicKey").getAsString();
            assertEquals(keys.get(i), pubKey);
        }

        Thread.sleep(4000);

        JsonObject changedResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", new JsonParser().parse(jsonInput), 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(changedResponse.entrySet().size(), 7);

        // check that changed response has the same signing key as the current signing key and it is different from
        // the previous signing key

        List<String> changedPubKeys = SigningKeys.getInstance(process.getProcess()).getDynamicKeys().stream()
                .map(key -> new io.supertokens.utils.Utils.PubPriKey(key.value).publicKey).collect(Collectors.toList());

        JsonArray changedRespPubKeyList = changedResponse.get("jwtSigningPublicKeyList").getAsJsonArray();

        boolean hasChangedKey = changedRespPubKeyList.size() != respPubKeyList.size();
        for (int i = 0; i < changedRespPubKeyList.size(); ++i) {
            String pubKey = changedRespPubKeyList.get(i).getAsJsonObject().get("publicKey").getAsString();
            assertEquals(changedPubKeys.get(i), pubKey);
            hasChangedKey = hasChangedKey || !keys.contains(pubKey);
        }

        assertTrue(hasChangedKey);
        assertEquals(changedResponse.get("jwtSigningPublicKey").getAsString(), changedPubKeys.get(0));
        assertNotEquals(changedResponse.get("jwtSigningPublicKey").getAsString(),
                response.get("jwtSigningPublicKey").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void checkHandshakeAPIResponse(JsonObject response, TestingProcessManager.TestingProcess process)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException,
            UnsupportedJWTSigningAlgorithmException {
        // check status
        assertEquals(response.get("status").getAsString(), "OK");

        List<KeyInfo> allKeys = SigningKeys.getInstance(process.getProcess()).getDynamicKeys();
        List<String> pubKeys = allKeys.stream()
                .map(key -> new io.supertokens.utils.Utils.PubPriKey(key.value).publicKey).collect(Collectors.toList());

        // check jwtSigningPublicKeyList
        assertTrue(response.has("jwtSigningPublicKeyList"));
        JsonArray respPubKeyList = response.get("jwtSigningPublicKeyList").getAsJsonArray();
        for (int i = 0; i < respPubKeyList.size(); ++i) {
            String pubKey = respPubKeyList.get(i).getAsJsonObject().get("publicKey").getAsString();
            assertEquals(pubKeys.get(i), pubKey);
            assertEquals(respPubKeyList.get(i).getAsJsonObject().get("expiryTime").getAsLong(),
                    allKeys.get(i).expiryTime);
            assertEquals(respPubKeyList.get(i).getAsJsonObject().get("createdAt").getAsLong(),
                    allKeys.get(i).createdAtTime);
        }
        assertEquals(allKeys.size(), respPubKeyList.size());

        // check jwtSigningPublicKey
        assertEquals(response.get("jwtSigningPublicKey").getAsString(), pubKeys.get(0));

        // check jwtSigningPublicKeyExpiryTime
        assertEquals(response.get("jwtSigningPublicKeyExpiryTime").getAsLong(),
                SigningKeys.getInstance(process.getProcess()).getDynamicSigningKeyExpiryTime());

        // check accessTokenBlacklistingEnabled
        assertEquals(response.get("accessTokenBlacklistingEnabled").getAsBoolean(),
                Config.getConfig(process.getProcess()).getAccessTokenBlacklisting());

        assertEquals(response.get("accessTokenValidity").getAsLong(),
                Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis());

        assertEquals(response.get("refreshTokenValidity").getAsLong(),
                Config.getConfig(process.getProcess()).getRefreshTokenValidityInMillis());
    }

}
