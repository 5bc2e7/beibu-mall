# 07 DevOps 与 K8s 部署

> 目标：从"能在 IDEA 跑"升级到"容器化 + 编排 + 可观测"，这是和普通学生项目拉开差距的地方。
> 路线：本地 Docker Compose 起中间件 → 各服务打镜像 → K8s 编排 → CI/CD 自动化 → 监控/日志/链路。

---

## 1. 容器化（Docker）

每个服务一个 `Dockerfile`（多阶段构建，镜像更小）：
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -pl mall-order/mall-order-server -am clean package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=build /app/mall-order/mall-order-server/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```
> 也可统一用一个构建脚本批量打包各服务镜像并打 tag（如 `beibu/order:1.0.0`）。

---

## 2. 本地编排（Docker Compose）

`docker/docker-compose-middleware.yml` 起全部中间件，开发时本地连：
```yaml
services:
  mysql:   { image: mysql:8.0, ports: ["3306:3306"], environment: {...} }
  redis:   { image: redis:7-alpine, ports: ["6379:6379"] }
  nacos:   { image: nacos/nacos-server:v3..., ports: ["8848:8848"] }
  rocketmq-namesrv: { image: apache/rocketmq:5..., ... }
  rocketmq-broker:  { ... }
  elasticsearch:    { image: elasticsearch:8..., ports: ["9200:9200"] }
  sentinel-dashboard: { ... , ports: ["8858:8858"] }
  seata-server:       { image: seataio/seata-server:2..., ports: ["8091:8091"] }
```
另一个 compose 文件可把**业务服务**也编排进去，实现 `docker compose up` 一键起整套（演示用）。

---

## 3. Kubernetes 部署

学习阶段用轻量集群起步：**k3s / minikube / kind**（本机即可，不用买云）。

每个服务一套：`Deployment`(多副本) + `Service`(ClusterIP) + 配置/密钥(`ConfigMap`/`Secret`)，
入口用 `Ingress`(nginx-ingress) 暴露网关。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: order-service }
spec:
  replicas: 2
  selector: { matchLabels: { app: order-service } }
  template:
    metadata: { labels: { app: order-service } }
    spec:
      containers:
        - name: order
          image: beibu/order:1.0.0
          ports: [{ containerPort: 9005 }]
          env:
            - { name: NACOS_ADDR, value: "nacos:8848" }
          readinessProbe: { httpGet: { path: /actuator/health/readiness, port: 9005 }, initialDelaySeconds: 20 }
          livenessProbe:  { httpGet: { path: /actuator/health/liveness,  port: 9005 }, initialDelaySeconds: 30 }
          resources: { requests: { cpu: "250m", memory: "512Mi" }, limits: { cpu: "1", memory: "1Gi" } }
---
apiVersion: v1
kind: Service
metadata: { name: order-service }
spec: { selector: { app: order-service }, ports: [{ port: 9005, targetPort: 9005 }] }
```

**要点 / 可讲的点**：
- 健康检查用 Spring Boot Actuator 的 liveness/readiness 探针。
- 中间件在 K8s 里可用官方 Helm Chart 或 StatefulSet（MySQL/ES 用 StatefulSet + PVC）。
- 进阶：HPA（按 CPU/QPS 自动扩缩容）——秒杀场景讲弹性扩容很加分。

---

## 4. CI/CD（GitHub Actions）

`.github/workflows/ci.yml`：
```yaml
on: [push, pull_request]
jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: maven }
      - run: mvn -B clean verify        # 编译 + 单元/集成测试 + jacoco:check 门禁
      - uses: actions/upload-artifact@v4 # 上传覆盖率报告
        with: { name: jacoco, path: '**/target/site/jacoco' }
  build-image:
    needs: build-test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - run: echo "docker build & push each service image"   # 推送到 GHCR/Docker Hub
```
> Actions runner 自带 Docker，Testcontainers 可直接跑。
> 流水线讲述："PR 触发测试+覆盖率门禁，合并到 main 才构建镜像"，这就是规范的 CI/CD。

---

## 5. 可观测性（监控 / 日志 / 链路）

| 维度 | 工具 | 接入 |
|---|---|---|
| 指标监控 | Prometheus + Grafana | 各服务 `micrometer-registry-prometheus` 暴露 `/actuator/prometheus`；Grafana 配 JVM/QPS/RT 看板 |
| 链路追踪 | SkyWalking | 启动挂 agent；可看跨服务调用链、慢接口、拓扑图 |
| 集中日志 | ELK 或 Loki+Promtail+Grafana | 收集各服务日志，按 traceId 串联（资源紧用 Loki 更轻） |
| 告警(选) | Grafana Alerting / Prometheus Alertmanager | 错误率/RT 超阈值告警 |

**演示价值**：SkyWalking 的服务拓扑图、Grafana 的 QPS/RT 看板**截图放进项目 README**，
面试时"我有全链路可观测"非常直观。

---

## 6. 部署演进路线（按时间投入选）

1. **必做**：各服务 Dockerfile + Compose 一键起中间件 + 服务本地连通。
2. **应做**：GitHub Actions 跑测试+覆盖率门禁；服务打镜像。
3. **加分**：k3s/minikube 部署核心几个服务 + Ingress 暴露 + 探针。
4. **锦上添花**：Prometheus/Grafana/SkyWalking 接入并截图；HPA 弹性扩容演示。

> 时间紧时，"Compose 一键起 + CI 测试门禁 + 监控截图"性价比最高，K8s 做核心 2-3 个服务即可讲。
