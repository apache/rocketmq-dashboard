# Kubernetes Certificate Provider Design

## Status

Proposed implementation contract for [#1314](https://github.com/apache/rocketmq-dashboard/issues/1314).

## Context

The existing K8s certificate page stores Studio-local metadata. Those records are not an inventory of
Kubernetes Secrets or cert-manager `Certificate` resources, and create, renew, or delete requests do
not change a Kubernetes cluster. Studio must not represent that local metadata as applied runtime
state.

This document defines the boundary required before adding a Kubernetes-backed certificate view.

## Goals

- Resolve certificate inventory through the selected managed instance, never through a process-global
  Kubernetes client.
- Support a read-only inventory phase before exposing certificate lifecycle actions.
- Keep Kubernetes credentials in the existing credential store as references; never return kubeconfig,
  bearer tokens, client private keys, or Secret data to the browser.
- Make unsupported capability and provider failures distinguishable from an empty certificate list.
- Define least-privilege RBAC for each release phase.

## Non-goals

- Infer a Kubernetes cluster from a RocketMQ NameServer or Proxy endpoint.
- Treat Studio-local `rmq_k8s_certificate` records as Kubernetes resources.
- Create, renew, or delete certificates before a provider can prove that it is operating against the
  selected Kubernetes context.
- Add a general-purpose Kubernetes resource browser.

## Instance Model

A Kubernetes certificate source is associated with one managed Studio instance. The instance must
reference a Kubernetes credential and an explicit Kubernetes target:

| Field | Meaning |
| --- | --- |
| `instanceId` | Studio instance that owns the operational context. |
| `credentialId` | Reference to an encrypted Kubernetes credential; the credential value is never returned by the API. |
| `kubernetesContext` | Named kubeconfig context or in-cluster identity selector. |
| `namespaceScope` | Optional allow-list of namespaces. An omitted value means the provider's configured default, not all namespaces. |
| `certificateSource` | `CERT_MANAGER`, `TLS_SECRET`, or both when a provider explicitly supports both inventories. |

Existing RocketMQ instance endpoints remain RocketMQ connection endpoints. They are not Kubernetes
credentials and must not be reused to construct a Kubernetes API URL.

## Provider Contract

The server owns provider resolution. A provider is selected by the instance vendor/capability and a
validated Kubernetes credential reference.

```java
public interface KubernetesCertificateProvider {
    KubernetesCertificateCapabilities capabilities(String instanceId);

    KubernetesCertificateInventory listCertificates(
            String instanceId, KubernetesCertificateQuery query);

    default KubernetesCertificate renewCertificate(
            String instanceId, KubernetesCertificateRef certificate) {
        throw new UnsupportedOperationException("Certificate renewal is not supported");
    }

    default void deleteCertificate(
            String instanceId, KubernetesCertificateRef certificate) {
        throw new UnsupportedOperationException("Certificate deletion is not supported");
    }
}
```

`KubernetesCertificateProviderRegistry` resolves this interface. It must reject an instance with no
Kubernetes binding rather than silently selecting a default context.

`KubernetesCertificateInventory` contains:

- `items`: certificates from the requested source;
- `source`: `CERT_MANAGER` or `TLS_SECRET`;
- `observedAt`: provider observation timestamp;
- `capabilities`: supported read/write operations;
- `partialFailures`: namespace/source failures that did not invalidate the whole response.

An empty `items` list means a successful observation with no matching resources. A missing provider,
invalid credential, RBAC denial, or API failure is an explicit structured error and must not be
converted to an empty inventory.

## API Shape

The first external API is read-only:

```
GET /api/instances/{instanceId}/kubernetes/certificates
    ?namespace=<optional>
    &source=CERT_MANAGER|TLS_SECRET
```

The response includes the selected instance ID and source so cached UI data cannot be reused for a
different instance. The request must validate that `namespace`, when supplied, is inside the instance
namespace scope.

The existing `/api/k8s-certs` endpoints remain explicitly Studio-local until they are removed or
migrated. They must not be mixed with provider inventory results in one table without a visible
source label.

Future mutation endpoints use resource references, not Studio-local record IDs:

```
POST /api/instances/{instanceId}/kubernetes/certificates/{namespace}/{name}/renew
DELETE /api/instances/{instanceId}/kubernetes/certificates/{namespace}/{name}
```

Mutations require an advertised provider capability and create an audit entry containing the Studio
user, instance ID, namespace, resource name, provider source, and result. They must never log token,
kubeconfig, Secret value, or certificate private key material.

## Credentials and RBAC

Phase 1 supports either a ServiceAccount token reference or a kubeconfig secret reference stored by
the server. The browser only receives the credential display name and non-sensitive metadata.

Read-only cert-manager inventory requires:

```yaml
apiGroups: ["cert-manager.io"]
resources: ["certificates"]
verbs: ["get", "list", "watch"]
```

Read-only TLS Secret inventory requires:

```yaml
apiGroups: [""]
resources: ["secrets"]
verbs: ["get", "list"]
```

The TLS Secret provider returns metadata only: namespace, name, type, creation time, and parsed
certificate expiry. It never returns `data.tls.crt` or `data.tls.key`.

Renewal requires cert-manager `patch` permission on `certificates` and must define the exact renewal
mechanism before implementation. Deletion is a separate high-risk capability and requires a
confirmation workflow plus `delete` permission.

## Delivery Phases

1. **Provider foundation:** introduce the provider registry, Kubernetes credential reference model,
   capability endpoint, and a provider-unavailable response. No mutation UI.
2. **Read-only cert-manager inventory:** list `Certificate` resources for one selected instance and
   namespace scope, including Ready condition and expiry data.
3. **Read-only TLS Secret inventory:** optional source for clusters without cert-manager, metadata
   only.
4. **Renewal:** add only after a cert-manager-compatible renewal operation and audit contract are
   tested against a real cluster.
5. **Deletion:** explicit opt-in, confirmation, audit, and post-condition verification.

Each phase is independently deployable. A missing Kubernetes provider must leave the rest of Studio
operational and show a capability-unavailable state rather than fabricated certificate data.

## Test Matrix

- provider registry rejects an instance without Kubernetes binding;
- selected instance A cannot read inventory using instance B credentials/context;
- empty inventory is distinct from RBAC denial and transport failure;
- namespace scope is enforced before a Kubernetes client call;
- TLS Secret results never serialize Secret data or private-key fields;
- cert-manager resources with missing status are rendered as unknown, not valid;
- write endpoints reject missing capability and produce audit records only after the provider call is
  attempted.

## Migration

Studio-local certificate records remain available only in the current local-metadata view during the
transition. They are not automatically migrated because their `cluster` field does not identify a
Kubernetes API context, credential, or authoritative resource name. Operators explicitly bind an
instance to a Kubernetes provider, then use the provider inventory as the runtime source of truth.
