# AGENTS.md — quizup-console

> **Console d'exploitation Axon** : équivalent fonctionnel open source de la Axoniq Platform
> (voir [`../../product/axon-platform-parity.md`](../../product/axon-platform-parity.md)).
> Ingère les snapshots des agents SDK (`quizup.axon.console.*`), expose l'état courant et les
> **actions d'exploitation** (DLQ, replay, cycle de vie des processors) — **auditées**.
>
> **Surface REST admin dédiée** (exception assumée à « le BFF est la seule surface » : console
> d'exploitation, ingress dédié, `ROLE_ADMIN` obligatoire). L'UI est `quizup-web-console`.

**Package** : `io.github.quizup.console`

---

## 1. Rôle

- **Ingestion M2M** : `POST /internal/ingest/snapshot` reçoit les snapshots `v1` des agents SDK
  (une instance = un réplica par application) : processors, segments, erreurs, stats DLQ.
- **État courant** : table par table (pas d'historique — les timelines viennent de Prometheus,
  métriques `quizup.axon.console.*`).
- **Actions** : retry/delete de séquence DLQ, clear DLQ, pause/start, reset (tail/head/instant),
  split/merge de segment — **proxy M2M** vers l'API interne `/internal/console/**` du service
  ciblé (endpoints du SDK, Lot A), puis **audit** (`console_audit` : acteur JWT, action, cible,
  résultat).
- **Orchestration replay** : le reset exige un processor à l'arrêt (`pause` → `reset` → `start`
  sur toutes les instances du processor) — la console/le web-console pilote cette séquence.

## 2. Architecture

- **Pas de runtime Axon** : dépend de `quizup-microservice-autoconfigure` (web, sécurité OAuth2,
  observabilité, swagger, M2M `server-client`) **sans** le starter Axon/Kafka. Conséquence :
  `ExceptionAutoConfiguration` du SDK est **exclue** (`spring.autoconfigure.exclude`) car ses
  intercepteurs s'enregistrent sur les bus distribués — la console utilise la gestion d'erreurs
  Spring standard.
- **Base** : Postgres `quizup_console`, Flyway `db/migration/console` (V1 + V2 champs natifs), JPA `validate`.
- **M2M sortant** : `RestTemplate` OAuth2 `@Primary` du SDK (client credentials `server-client`) ;
  URLs des services : `quizup.console.services.urls` (défaut prod : `http://<application>`, Service
  k8s port 80 ; surcharges locales dans `application-local.yml`).
- **Prometheus** : `quizup.console.prometheus.url` (défaut in-cluster
  `kube-prometheus-stack-prometheus.monitoring:9090`, vide en local = métriques désactivées).
  Handlers commandes/queries lus via `/command-capabilities` + `/query-capabilities` des services.
- **Purge** : `ConsoleCleanupService` supprime les instances sans push depuis 10 min (pods remplacés)
  et leurs lignes filles.

## 3. Surface REST

| Endpoint | Accès | Rôle |
|---|---|---|
| `POST /internal/ingest/snapshot` | JWT M2M (serveur à serveur, jamais ingressed) | ingestion d'un snapshot d'agent (204) |
| `GET /api/console/version` | `ROLE_ADMIN` | version du service (`build-info`) — lecture légère pour le pied de page de la console |
| `GET /api/console/dashboard` | `ROLE_ADMIN` | tableau de bord (réplicas/processors/DLQ, débits Prometheus, volumétrie event store, version) |
| `GET /api/console/applications` | `ROLE_ADMIN` | instances par application (version, uptime, first/last seen) |
| `GET /api/console/applications/{application}` | `ROLE_ADMIN` | détail : instances, handlers (commandes/queries), processors enrichis |
| `GET /api/console/processors?application=` | `ROLE_ADMIN` | processors (flags, token store, threads, warnings) |
| `GET /api/console/processors/{application}/{instanceId}/{name}` | `ROLE_ADMIN` | détail + segments (`onePartOf`) |
| `GET /api/console/metrics/{applications\|processors\|messages}/...` | `ROLE_ADMIN` | séries Prometheus (requêtes fixes, `range=1h\|6h\|24h`) |
| `GET /api/console/search/events?application=&aggregateId=` | `ROLE_ADMIN` | événements d'un agrégat (payload) |
| `GET /api/console/search/events/by-type?application=&type=` | `ROLE_ADMIN` | recherche bornée par type |
| `GET /api/console/search/snapshots?application=&aggregateId=` | `ROLE_ADMIN` | snapshots d'un agrégat |
| `GET /api/console/scheduled-events?application=` | `ROLE_ADMIN` | deadlines persistantes (db-scheduler) |
| `GET /api/console/dlq` | `ROLE_ADMIN` | stats DLQ par processor |
| `GET /api/console/users` | `ROLE_ADMIN` | admin OIDC courant + acteurs d'audit |
| `GET /api/console/audit?page=&size=` | `ROLE_ADMIN` | journal d'audit |
| `POST /api/console/actions/dlq/{application}/{instanceId}/{processor}/sequences/{sequenceId}/retry` | `ROLE_ADMIN` + audit | retry d'une séquence DLQ |
| `DELETE /api/console/actions/dlq/{application}/{instanceId}/{processor}/sequences/{sequenceId}` | `ROLE_ADMIN` + audit | suppression d'une séquence |
| `POST /api/console/actions/dlq/{application}/{instanceId}/{processor}/clear` | `ROLE_ADMIN` + audit | purge DLQ |
| `POST /api/console/actions/processors/{application}/{instanceId}/{name}/{pause\|start}` | `ROLE_ADMIN` + audit | cycle de vie |
| `POST /api/console/actions/processors/{application}/{instanceId}/{name}/reset` | `ROLE_ADMIN` + audit | replay (`{"position":"TAIL\|HEAD\|INSTANT","instant":...}`, processor arrêté) |
| `POST /api/console/actions/processors/{application}/{instanceId}/{name}/segments/{segmentId}/{split\|merge}` | `ROLE_ADMIN` + audit | segments |

## 4. Read models

`console_instance`, `console_processor`, `console_segment`, `console_dlq_stat`, `console_audit`
(`V1__init_console_schema.sql`). L'ingestion remplace (delete + insert) les lignes filles de
l'instance — les processors/segments supprimés côté application disparaissent.

## 5. Déploiement & TODO

- GitOps : `devops/quizup-gitops/apps/console/` (+ Application ArgoCD, Image Updater
  `ghcr.io/quizup-organization/console`) ; **pas** de label `component: axon` (aucun runtime Axon).
  L'hôte `console.quizup.cnadjim.fr` route `/api/console` ici (Ingress de la SPA) —
  `/internal/**` et `/actuator/**` restent in-cluster.
- Client OIDC public `console` déclaré dans `quizup-identity` (dev + prod).
- **Avant sync** : sceller `quizup-console-secret` (`./scripts/seal-secrets.sh console`), créer
  `quizup_console` sur le Postgres live, release train (SDK → `console`) puis release identity
  (client `console`) et web-console.
- TODO : détection de transitions & feed d'alertes, historisation courte/réglages
  (`console_setting`) si besoin.
