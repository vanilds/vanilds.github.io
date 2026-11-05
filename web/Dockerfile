# syntax=docker/dockerfile:1
# One build, three images: the shell and each micro frontend are separate deployments, but they come from one
# Angular workspace (shared library, one lockfile). Pick the image with --target:
#   docker build --target shell         -t creditdesk-shell:dev web
#   docker build --target mfe-customers -t creditdesk-mfe-customers:dev web
#   docker build --target mfe-proposals -t creditdesk-mfe-proposals:dev web
# BuildKit builds the "build" stage once and reuses it for the three targets.

# ---- Build -------------------------------------------------------------------------------------------
# --platform=$BUILDPLATFORM: the output is plain JavaScript, identical on every CPU, so compile on the
# runner's own architecture and only copy the files into the per-arch nginx image (no QEMU for npm).
FROM --platform=$BUILDPLATFORM public.ecr.aws/docker/library/node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Runtime base ------------------------------------------------------------------------------------
# nginx-unprivileged: runs as uid 101 and listens on 8080, so the pod needs no root and no extra capabilities.
FROM public.ecr.aws/nginx/nginx-unprivileged:1.28-alpine AS runtime
USER root
# Drop the image's welcome page: a path that is not ours must be a 404, not "Welcome to nginx!"
RUN rm -rf /usr/share/nginx/html/*
COPY nginx/headers.conf nginx/common.conf /etc/nginx/snippets/
# Start nginx directly: the image's entrypoint scripts rewrite files in /etc/nginx, which fails on the
# read-only root filesystem the pods use. Temp files and the pid file go to /tmp (an emptyDir in the pod).
USER 101
ENTRYPOINT []
CMD ["nginx", "-g", "daemon off;"]
EXPOSE 8080

# ---- Images ------------------------------------------------------------------------------------------
FROM runtime AS shell
COPY nginx/shell.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/shell/browser/ /usr/share/nginx/html/

FROM runtime AS mfe-customers
COPY --from=build /src/nginx/mfe.conf /tmp/mfe.conf
USER root
RUN sed 's|MFE_PATH|/mfe/customers/|' /tmp/mfe.conf > /etc/nginx/conf.d/default.conf && rm /tmp/mfe.conf
USER 101
COPY --from=build /src/dist/mfe-customers/browser/ /usr/share/nginx/html/mfe/customers/

FROM runtime AS mfe-proposals
COPY --from=build /src/nginx/mfe.conf /tmp/mfe.conf
USER root
RUN sed 's|MFE_PATH|/mfe/proposals/|' /tmp/mfe.conf > /etc/nginx/conf.d/default.conf && rm /tmp/mfe.conf
USER 101
COPY --from=build /src/dist/mfe-proposals/browser/ /usr/share/nginx/html/mfe/proposals/
