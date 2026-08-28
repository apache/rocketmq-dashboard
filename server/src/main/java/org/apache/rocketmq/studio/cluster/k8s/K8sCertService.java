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
package org.apache.rocketmq.studio.cluster.k8s;

import org.apache.rocketmq.studio.common.domain.enums.CertStatus;
import org.apache.rocketmq.studio.common.domain.enums.CertType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class K8sCertService {

    private static final int EXPIRING_THRESHOLD_DAYS = 30;
    private static final byte[] KEY_MATCH_CHALLENGE = "rocketmq-studio-k8s-cert".getBytes();
    private static final byte[] DER_INTEGER_ZERO = new byte[] {0x02, 0x01, 0x00};
    private static final byte[] RSA_ALGORITHM_IDENTIFIER = new byte[] {
        0x30, 0x0d,
        0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00
    };
    private static final byte[] EC_PUBLIC_KEY_OID = new byte[] {
        0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01
    };

    private final K8sCertRepository k8sCertRepository;
    private final OperationAuditService operationAuditService;
    private final Clock clock;

    @Autowired
    public K8sCertService(K8sCertRepository k8sCertRepository, OperationAuditService operationAuditService) {
        this(k8sCertRepository, operationAuditService, Clock.systemDefaultZone());
    }

    K8sCertService(K8sCertRepository k8sCertRepository, OperationAuditService operationAuditService, Clock clock) {
        this.k8sCertRepository = k8sCertRepository;
        this.operationAuditService = operationAuditService;
        this.clock = clock;
    }

    public List<K8sCertVO> listCerts() {
        log.info("Listing all K8s certificates");
        LocalDateTime now = LocalDateTime.now(clock);
        return k8sCertRepository.findAll().stream()
                .map(cert -> refreshExpirationState(cert, now))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public K8sCertVO createCert(CreateCertDTO command) {
        requireCommand(command);
        if (command.getType() == null) {
            throw new BusinessException(400, "type is required");
        }
        CertType type = parseCertType(command.getType());
        log.info("Creating K8s certificate: {}", command.getK8sId());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime notBefore = now;
        LocalDateTime notAfter = now.plusYears(1);
        String issuer = command.getIssuer();
        List<String> san = command.getSan();
        String certPem = normalizeOptionalPem(command.getCertPem());
        String keyPem = normalizeOptionalPem(command.getKeyPem());
        ParsedCertificate parsed = null;
        if (certPem != null) {
            parsed = parseCertificate(certPem, now);
            notBefore = parsed.notBefore();
            notAfter = parsed.notAfter();
            issuer = parsed.issuer();
            san = parsed.san();
            validateMtlsKeyIfNeeded(type, parsed.certificate(), keyPem, true);
        }

        K8sCertVO cert = K8sCertVO.builder()
                .k8sId(command.getK8sId())
                .cluster(command.getCluster())
                .type(type)
                .issuer(issuer)
                .notBefore(notBefore)
                .notAfter(notAfter)
                .status(CertStatus.valid)
                .san(san)
                .certPem(certPem)
                .keyPem(keyPem)
                .build();
        cert.setGmtCreate(now);
        cert.setGmtModified(now);

        K8sCertVO saved = k8sCertRepository.save(refreshExpirationState(cert, now));
        auditCertificate("CREATE_K8S_CERTIFICATE", saved);
        log.info("K8s certificate created: {} (id={})", saved.getK8sId(), saved.getId());
        return saved;
    }

    public K8sCertVO updateCert(UpdateCertDTO command) {
        requireCommand(command);
        CertType type = command.getType() == null ? null : parseCertType(command.getType());
        log.info("Updating K8s certificate: {}", command.getId());
        K8sCertVO existing = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        K8sCertVO updated = copyOf(existing);
        String k8sId = normalizeOptionalIdentity(command.getK8sId(), "k8sId");
        String cluster = normalizeOptionalIdentity(command.getCluster(), "cluster");
        String issuer = normalizeOptionalIdentity(command.getIssuer(), "issuer");
        if (k8sId != null) {
            updated.setK8sId(k8sId);
        }
        if (cluster != null) {
            updated.setCluster(cluster);
        }
        if (type != null) {
            updated.setType(type);
        }
        if (issuer != null) {
            updated.setIssuer(issuer);
        }
        if (command.getSan() != null) {
            updated.setSan(command.getSan());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String certPem = normalizeOptionalPem(command.getCertPem());
        String keyPem = normalizeOptionalPem(command.getKeyPem());
        ParsedCertificate parsed = null;
        if (certPem != null) {
            parsed = parseCertificate(certPem, now);
            applyParsedCertificate(updated, parsed, certPem);
        }
        if (keyPem != null) {
            updated.setKeyPem(keyPem);
        }
        if (updated.getType() == CertType.mTLS && (parsed != null || keyPem != null)) {
            X509Certificate certificate = parsed == null ? parseCertificate(updated.getCertPem(), now).certificate()
                    : parsed.certificate();
            validateMtlsKeyIfNeeded(updated.getType(), certificate, updated.getKeyPem(), true);
        }
        updated.setGmtModified(now);

        K8sCertVO saved = k8sCertRepository.save(refreshExpirationState(updated, now));
        auditCertificate("UPDATE_K8S_CERTIFICATE", saved);
        log.info("K8s certificate updated: {} (id={})", saved.getK8sId(), saved.getId());
        return saved;
    }

    public K8sCertVO renewCert(RenewCertDTO command) {
        requireCommand(command);
        log.info("Renewing K8s certificate: {}", command.getId());
        K8sCertVO existing = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        LocalDateTime now = LocalDateTime.now(clock);
        String certPem = requirePem(command.getCertPem(), "certPem");
        String keyPem = normalizeOptionalPem(command.getKeyPem());
        ParsedCertificate parsed = parseCertificate(certPem, now);
        if (existing.getType() == CertType.mTLS) {
            validateMtlsKeyIfNeeded(existing.getType(), parsed.certificate(), keyPem, true);
        }

        K8sCertVO renewed = copyOf(existing);
        applyParsedCertificate(renewed, parsed, certPem);
        if (keyPem != null) {
            renewed.setKeyPem(keyPem);
        }
        renewed.setGmtModified(now);

        K8sCertVO saved = k8sCertRepository.save(refreshExpirationState(renewed, now));
        auditCertificate("RENEW_K8S_CERTIFICATE", saved);
        log.info("K8s certificate renewed: {} (id={}), new expiry: {}", saved.getK8sId(), saved.getId(),
                saved.getNotAfter());
        return saved;
    }

    public void deleteCert(DeleteCertDTO command) {
        requireCommand(command);
        log.info("Deleting K8s certificate: {}", command.getId());
        k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));
        if (!k8sCertRepository.deleteById(command.getId())) {
            throw new BusinessException(404, "Certificate not found: " + command.getId());
        }
        recordAudit("DELETE_K8S_CERTIFICATE", "K8S_CERTIFICATE", String.valueOf(command.getId()), null,
                null);
        log.info("K8s certificate deleted: {}", command.getId());
    }

    private CertType parseCertType(String type) {
        try {
            return CertType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Invalid certificate type: " + type
                    + ". Valid types: TLS, mTLS, ServiceAccount");
        }
    }

    private void requireCommand(Object command) {
        if (command == null) {
            throw new BusinessException(400, "K8s certificate request is required");
        }
    }

    private String normalizeOptionalIdentity(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "Certificate " + field + " cannot be blank");
        }
        return normalized;
    }

    private String normalizeOptionalPem(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requirePem(String value, String field) {
        String normalized = normalizeOptionalPem(value);
        if (normalized == null) {
            throw new BusinessException(400, field + " is required");
        }
        return normalized;
    }

    private K8sCertVO refreshExpirationState(K8sCertVO cert, LocalDateTime now) {
        K8sCertVO refreshed = copyOf(cert);
        LocalDateTime notAfter = refreshed.getNotAfter();
        if (notAfter == null) {
            return refreshed;
        }

        int daysRemaining = (int) ChronoUnit.DAYS.between(now, notAfter);
        refreshed.setDaysRemaining(daysRemaining);
        if (!notAfter.isAfter(now)) {
            refreshed.setStatus(CertStatus.expired);
        } else if (daysRemaining <= EXPIRING_THRESHOLD_DAYS) {
            refreshed.setStatus(CertStatus.expiring);
        } else {
            refreshed.setStatus(CertStatus.valid);
        }
        return refreshed;
    }

    private K8sCertVO copyOf(K8sCertVO cert) {
        K8sCertVO copy = K8sCertVO.builder()
                .k8sId(cert.getK8sId())
                .cluster(cert.getCluster())
                .type(cert.getType())
                .issuer(cert.getIssuer())
                .notBefore(cert.getNotBefore())
                .notAfter(cert.getNotAfter())
                .status(cert.getStatus())
                .daysRemaining(cert.getDaysRemaining())
                .san(cert.getSan())
                .certPem(cert.getCertPem())
                .keyPem(cert.getKeyPem())
                .build();
        copy.setId(cert.getId());
        copy.setGmtCreate(cert.getGmtCreate());
        copy.setGmtModified(cert.getGmtModified());
        return copy;
    }

    private void auditCertificate(String operation, K8sCertVO certificate) {
        recordAudit(operation, "K8S_CERTIFICATE", String.valueOf(certificate.getId()), null,
                "k8sId=" + certificate.getK8sId() + ", cluster=" + certificate.getCluster());
    }

    private ParsedCertificate parseCertificate(String certPem, LocalDateTime now) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = factory.generateCertificates(
                    new ByteArrayInputStream(certPem.getBytes(StandardCharsets.US_ASCII)));
            X509Certificate certificate = certificates.stream()
                    .filter(X509Certificate.class::isInstance)
                    .map(X509Certificate.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(400,
                            "Invalid certificate content, expected a PEM encoded X.509 certificate"));
            LocalDateTime notBefore = LocalDateTime.ofInstant(certificate.getNotBefore().toInstant(),
                    ZoneOffset.UTC);
            LocalDateTime notAfter = LocalDateTime.ofInstant(certificate.getNotAfter().toInstant(),
                    ZoneOffset.UTC);
            if (!notAfter.isAfter(now)) {
                throw new BusinessException(400, "Certificate is expired");
            }
            return new ParsedCertificate(certificate, certificate.getIssuerX500Principal().getName(), notBefore,
                    notAfter, extractSubjectAlternativeNames(certificate));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(400, "Invalid certificate content, expected a PEM encoded X.509 certificate");
        }
    }

    private void applyParsedCertificate(K8sCertVO target, ParsedCertificate parsed, String certPem) {
        target.setCertPem(certPem);
        target.setIssuer(parsed.issuer());
        target.setNotBefore(parsed.notBefore());
        target.setNotAfter(parsed.notAfter());
        target.setSan(parsed.san());
    }

    private void validateMtlsKeyIfNeeded(CertType type, X509Certificate certificate, String keyPem,
                                         boolean requireKey) {
        if (type != CertType.mTLS) {
            return;
        }
        if (keyPem == null || keyPem.isBlank()) {
            if (requireKey) {
                throw new BusinessException(400, "keyPem is required for mTLS certificates");
            }
            return;
        }
        PrivateKey privateKey = parsePrivateKey(keyPem);
        if (!matchesCertificatePublicKey(certificate, privateKey)) {
            throw new BusinessException(400, "Private key does not match certificate public key");
        }
    }

    private PrivateKey parsePrivateKey(String keyPem) {
        String type = privateKeyType(keyPem);
        byte[] keyDer = decodePemBlock(keyPem, type);
        byte[] pkcs8Der = switch (type) {
            case "PRIVATE KEY" -> keyDer;
            case "RSA PRIVATE KEY" -> wrapPkcs1RsaPrivateKey(keyDer);
            case "EC PRIVATE KEY" -> wrapSec1EcPrivateKey(keyDer);
            default -> throw new BusinessException(400,
                    "Invalid private key content, expected a PEM encoded private key");
        };
        PKCS8EncodedKeySpec keySpec;
        try {
            keySpec = new PKCS8EncodedKeySpec(pkcs8Der);
        } catch (Exception exception) {
            throw new BusinessException(400, "Invalid private key content, expected a PEM encoded private key");
        }
        for (String algorithm : List.of("RSA", "EC", "DSA")) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
            } catch (GeneralSecurityException ignored) {
                // Try the next common Kubernetes client key algorithm.
            }
        }
        throw new BusinessException(400, "Invalid private key content, expected a PEM encoded private key");
    }

    private String privateKeyType(String keyPem) {
        if (keyPem.contains("-----BEGIN PRIVATE KEY-----")) {
            return "PRIVATE KEY";
        }
        if (keyPem.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            return "RSA PRIVATE KEY";
        }
        if (keyPem.contains("-----BEGIN EC PRIVATE KEY-----")) {
            return "EC PRIVATE KEY";
        }
        throw new BusinessException(400, "Invalid private key content, expected a PEM encoded private key");
    }

    private byte[] decodePemBlock(String pem, String type) {
        String base64 = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (Exception exception) {
            throw new BusinessException(400, "Invalid private key content, expected a PEM encoded private key");
        }
    }

    private byte[] wrapPkcs1RsaPrivateKey(byte[] pkcs1Key) {
        return derSequence(DER_INTEGER_ZERO, RSA_ALGORITHM_IDENTIFIER, derOctetString(pkcs1Key));
    }

    private byte[] wrapSec1EcPrivateKey(byte[] sec1Key) {
        byte[] namedCurveParameters = extractSec1EcParameters(sec1Key);
        byte[] algorithmIdentifier = derSequence(EC_PUBLIC_KEY_OID, namedCurveParameters);
        return derSequence(DER_INTEGER_ZERO, algorithmIdentifier, derOctetString(sec1Key));
    }

    private byte[] extractSec1EcParameters(byte[] sec1Key) {
        DerValue sequence = readDerValue(sec1Key, 0);
        if (sequence.tag() != 0x30 || sequence.nextOffset() != sec1Key.length) {
            throw new BusinessException(400, "Invalid EC private key content");
        }
        int offset = sequence.contentOffset();
        while (offset < sequence.nextOffset()) {
            DerValue value = readDerValue(sec1Key, offset);
            if (value.tag() == 0xa0) {
                byte[] parameters = value.content();
                if (parameters.length > 0 && parameters[0] == 0x06) {
                    return parameters;
                }
                throw new BusinessException(400, "Invalid EC private key parameters");
            }
            offset = value.nextOffset();
        }
        throw new BusinessException(400, "EC private key must include named curve parameters");
    }

    private DerValue readDerValue(byte[] data, int offset) {
        if (offset < 0 || offset + 2 > data.length) {
            throw new BusinessException(400, "Invalid DER content");
        }
        int cursor = offset + 1;
        int lengthByte = data[cursor++] & 0xff;
        int length;
        if ((lengthByte & 0x80) == 0) {
            length = lengthByte;
        } else {
            int lengthBytes = lengthByte & 0x7f;
            if (lengthBytes == 0 || lengthBytes > 4 || cursor + lengthBytes > data.length) {
                throw new BusinessException(400, "Invalid DER length");
            }
            length = 0;
            for (int i = 0; i < lengthBytes; i++) {
                length = (length << 8) | (data[cursor++] & 0xff);
            }
        }
        int valueOffset = cursor;
        int nextOffset = valueOffset + length;
        if (length < 0 || nextOffset > data.length) {
            throw new BusinessException(400, "Invalid DER length");
        }
        return new DerValue(data[offset] & 0xff, valueOffset, nextOffset, data);
    }

    private byte[] derSequence(byte[]... values) {
        return derTagged(0x30, concatenate(values));
    }

    private byte[] derOctetString(byte[] value) {
        return derTagged(0x04, value);
    }

    private byte[] derTagged(int tag, byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(tag);
        output.writeBytes(derLength(value.length));
        output.writeBytes(value);
        return output.toByteArray();
    }

    private byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }
        int lengthBytes = 0;
        int value = length;
        while (value > 0) {
            lengthBytes++;
            value >>= 8;
        }
        byte[] encoded = new byte[lengthBytes + 1];
        encoded[0] = (byte) (0x80 | lengthBytes);
        for (int i = lengthBytes; i > 0; i--) {
            encoded[i] = (byte) (length & 0xff);
            length >>= 8;
        }
        return encoded;
    }

    private byte[] concatenate(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }

    private boolean matchesCertificatePublicKey(X509Certificate certificate, PrivateKey privateKey) {
        String publicKeyAlgorithm = certificate.getPublicKey().getAlgorithm();
        String signatureAlgorithm = switch (publicKeyAlgorithm) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            case "DSA" -> "SHA256withDSA";
            default -> throw new BusinessException(400,
                    "Unsupported certificate public key algorithm: " + publicKeyAlgorithm);
        };
        try {
            Signature signature = Signature.getInstance(signatureAlgorithm);
            signature.initSign(privateKey);
            signature.update(KEY_MATCH_CHALLENGE);
            byte[] signed = signature.sign();

            signature.initVerify(certificate.getPublicKey());
            signature.update(KEY_MATCH_CHALLENGE);
            return signature.verify(signed);
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    private List<String> extractSubjectAlternativeNames(X509Certificate certificate) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) {
                return List.of();
            }
            return names.stream()
                    .filter(entry -> entry.size() >= 2 && entry.get(1) instanceof String)
                    .map(entry -> (String) entry.get(1))
                    .collect(Collectors.toList());
        } catch (CertificateParsingException exception) {
            log.warn("Failed to parse SANs of certificate {}: {}", certificate.getSubjectX500Principal(),
                    exception.getMessage());
            return List.of();
        }
    }

    private void recordAudit(String operation, String resourceType, String resourceName,
                             String clusterId, String detail) {
        try {
            operationAuditService.record(operation, resourceType, resourceName, clusterId, detail, "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }

    private record ParsedCertificate(X509Certificate certificate, String issuer, LocalDateTime notBefore,
                                     LocalDateTime notAfter, List<String> san) {
    }

    private record DerValue(int tag, int contentOffset, int nextOffset, byte[] source) {

        private byte[] content() {
            byte[] value = new byte[nextOffset - contentOffset];
            System.arraycopy(source, contentOffset, value, 0, value.length);
            return value;
        }
    }

}
