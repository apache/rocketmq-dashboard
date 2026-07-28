/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.config;

import java.util.Base64;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CredentialEncryptionServiceTest {

    @Test
    public void testEncryptDecryptRoundTrip() {
        CredentialEncryptionService service = new CredentialEncryptionService("my-configured-key");
        String cipher = service.encrypt("my-secret-credential");
        assertNotNull(cipher);
        assertNotEquals("my-secret-credential", cipher);
        assertEquals("my-secret-credential", service.decrypt(cipher));
    }

    @Test
    public void testEncryptProducesDifferentCipherTextEachTime() {
        CredentialEncryptionService service = new CredentialEncryptionService("my-configured-key");
        String first = service.encrypt("same-input");
        String second = service.encrypt("same-input");
        // Random IV per encryption
        assertNotEquals(first, second);
        assertEquals("same-input", service.decrypt(first));
        assertEquals("same-input", service.decrypt(second));
    }

    @Test
    public void testSameKeyAcrossInstancesCanDecrypt() {
        CredentialEncryptionService first = new CredentialEncryptionService("shared-key");
        CredentialEncryptionService second = new CredentialEncryptionService("shared-key");
        String cipher = first.encrypt("credential");
        assertEquals("credential", second.decrypt(cipher));
    }

    @Test
    public void testLongKeyIsTruncatedTo32Bytes() {
        String longKey = "0123456789abcdef0123456789abcdef-extra-bytes-beyond-32";
        CredentialEncryptionService service = new CredentialEncryptionService(longKey);
        String cipher = service.encrypt("data");
        assertEquals("data", service.decrypt(cipher));
    }

    @Test
    public void testEmptyKeyGeneratesRandomKey() {
        CredentialEncryptionService random = new CredentialEncryptionService("");
        String cipher = random.encrypt("data");
        assertEquals("data", random.decrypt(cipher));

        CredentialEncryptionService nullKey = new CredentialEncryptionService(null);
        assertEquals("x", nullKey.decrypt(nullKey.encrypt("x")));
    }

    @Test
    public void testDecryptWithWrongKeyThrows() {
        CredentialEncryptionService first = new CredentialEncryptionService("key-one");
        CredentialEncryptionService second = new CredentialEncryptionService("key-two");
        String cipher = first.encrypt("credential");
        try {
            second.decrypt(cipher);
            fail("Expected CredentialEncryptionException");
        } catch (CredentialEncryptionService.CredentialEncryptionException e) {
            assertTrue(e.getMessage().contains("Failed to decrypt"));
            assertNotNull(e.getCause());
        }
    }

    @Test(expected = CredentialEncryptionService.CredentialEncryptionException.class)
    public void testDecryptCorruptedDataThrows() {
        CredentialEncryptionService service = new CredentialEncryptionService("key");
        service.decrypt(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
    }

    @Test(expected = CredentialEncryptionService.CredentialEncryptionException.class)
    public void testDecryptTamperedCipherTextThrows() {
        CredentialEncryptionService service = new CredentialEncryptionService("key");
        byte[] combined = Base64.getDecoder().decode(service.encrypt("credential"));
        combined[combined.length - 1] ^= 0x01;
        service.decrypt(Base64.getEncoder().encodeToString(combined));
    }

    @Test
    public void testEncryptDecryptEmptyInput() {
        CredentialEncryptionService service = new CredentialEncryptionService("key");
        assertEquals("", service.encrypt(null));
        assertEquals("", service.encrypt(""));
        assertEquals("", service.decrypt(null));
        assertEquals("", service.decrypt(""));
    }

    @Test
    public void testIsEncrypted() {
        CredentialEncryptionService service = new CredentialEncryptionService("key");
        assertFalse(service.isEncrypted(null));
        assertFalse(service.isEncrypted(""));
        assertFalse(service.isEncrypted("!!!not-base64!!!"));
        // Too short to contain IV + tag
        assertFalse(service.isEncrypted(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
        assertTrue(service.isEncrypted(service.encrypt("credential")));
    }
}
