# syntax=docker/dockerfile:1

# Base images come from Amazon ECR Public's copy of the Docker official images: identical content, and no
# anonymous pull rate limit (Docker Hub allows ~100 pulls per 6h per IP, which CI runners easily hit).

# ---- Build -------------------------------------------------------------------------------------------
# --platform=$BUILDPLATFORM: Java bytecode is the same on every CPU, so compile once on the runner's own
# architecture instead of emulating arm64 (QEMU makes Maven ~10x slower). Only the runtime stage is per-arch.
FROM --platform=$BUILDPLATFORM public.ecr.aws/docker/library/eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first: this layer is cached until pom.xml changes, so code-only changes rebuild fast
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/creditdesk-api.jar extract --layers --launcher --destination /layers

# ---- Runtime -----------------------------------------------------------------------------------------
# JRE only (no compiler, no Maven): smaller image, smaller attack surface. Multi-arch: amd64 + arm64 (t4g).
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Layers ordered from least to most frequently changed, so a code change only pushes the last small layer
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./

USER app
EXPOSE 8080

# MaxRAMPercentage: size the heap from the container's memory limit (k8s limit 768Mi → ~576Mi heap)
# ExitOnOutOfMemoryError: crash fast and let Kubernetes restart the pod instead of limping along
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", \
            "org.springframework.boot.loader.launch.JarLauncher"]
