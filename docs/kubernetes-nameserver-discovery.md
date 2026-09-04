# Kubernetes NameServer Discovery

RocketMQ Studio can discover RocketMQ NameServer candidates from one Kubernetes Namespace. Discovery is read-only and runs only when an administrator requests it from the NameServer management page.

## Enable Discovery

Discovery is disabled by default:

```bash
STUDIO_KUBERNETES_NAMESERVER_DISCOVERY_ENABLED=true
```

Fabric8 loads Kubernetes credentials using its standard auto-configuration:

- an in-cluster ServiceAccount when Studio runs in Kubernetes;
- `KUBECONFIG` when Studio runs outside the cluster;
- the standard local kubeconfig when available to the Studio process.

Do not send kubeconfig content or ServiceAccount tokens to the browser. When Studio runs through Docker Compose, mount the kubeconfig read-only into the backend container and set `KUBECONFIG` to the container path.

## Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `STUDIO_KUBERNETES_NAMESERVER_DISCOVERY_ENABLED` | `false` | Enables the discovery API. |
| `STUDIO_KUBERNETES_NAMESERVER_PORT` | `9876` | NameServer port used for strong matches and Pod fallback addresses. |
| `STUDIO_KUBERNETES_CLUSTER_DOMAIN` | `cluster.local` | Cluster DNS suffix used for Service candidates. |
| `STUDIO_KUBERNETES_POD_FALLBACK_ENABLED` | `true` | Enables label and image-based Pod fallback after Service and EndpointSlice discovery fail. |
| `STUDIO_KUBERNETES_DISCOVERY_MAX_CANDIDATES` | `50` | Bounds the returned candidate list; valid range is 1-500. |

## Discovery Order

Discovery stops at the first strategy that returns candidates:

1. Services exposing the configured NameServer port.
2. Services whose name, labels, or port name identify a NameServer.
3. Ready addresses from `discovery.k8s.io/v1` EndpointSlices.
4. Running and Ready Pods selected by `app.kubernetes.io/component=nameserver|namesrv`.
5. Running and Ready `app.kubernetes.io/name=rocketmq` Pods with NameServer role evidence.
6. A full Pod list in the selected Namespace, filtered by RocketMQ image plus NameServer role evidence.

The final Pod list is a last resort. Discovery never calls `inAnyNamespace()`.

Service DNS candidates are preferred because they remain stable when Pods change. EndpointSlice and Pod candidates contain workload IPs and are marked ephemeral in the UI. The Studio backend must share network and DNS reachability with the selected candidate; discovering an address does not make a cluster-internal address reachable from an external Studio deployment.

## Minimum RBAC

Create the Role in each Namespace that administrators may discover. Replace the Namespace and ServiceAccount names for the deployment.

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: rocketmq-studio-nameserver-discovery
  namespace: mq
rules:
  - apiGroups: [""]
    resources: ["services", "pods"]
    verbs: ["get", "list"]
  - apiGroups: ["discovery.k8s.io"]
    resources: ["endpointslices"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: rocketmq-studio-nameserver-discovery
  namespace: mq
subjects:
  - kind: ServiceAccount
    name: rocketmq-studio
    namespace: studio
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: rocketmq-studio-nameserver-discovery
```

The feature does not require Secret access, mutation verbs, `watch`, ClusterRole, or ClusterRoleBinding.

## Non-goals

- background watches or automatic registry synchronization;
- cross-Namespace scanning;
- Kubernetes workload mutation;
- certificate or Secret lifecycle management;
- automatically trusting or saving the first candidate.

## References

- [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client)
- [Kubernetes Services](https://kubernetes.io/docs/concepts/services-networking/service/)
- [Kubernetes EndpointSlices](https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/)
- [Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
