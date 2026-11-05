# CreditDesk AI worker. Multi-arch friendly (arm64 for the t4g EC2, amd64 for Codespaces/CI): every dependency
# ships wheels for both. Base images from AWS's mirror of Docker Hub (no anonymous pull limits).

FROM public.ecr.aws/docker/library/python:3.12-slim AS build
WORKDIR /app
ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
RUN python -m venv /opt/venv
ENV PATH=/opt/venv/bin:$PATH
# Dependencies first: this layer is reused while only the code changes
COPY pyproject.toml ./
RUN mkdir -p src/creditdesk_worker && touch src/creditdesk_worker/__init__.py && pip install .
COPY src ./src
RUN pip install --no-deps --force-reinstall .

FROM public.ecr.aws/docker/library/python:3.12-slim
# No root inside the container: a bug in a dependency cannot write to the image or bind privileged ports
RUN useradd --system --uid 10001 --no-create-home worker
COPY --from=build /opt/venv /opt/venv
ENV PATH=/opt/venv/bin:$PATH PYTHONUNBUFFERED=1
USER 10001
EXPOSE 8000
# exec form: the process is PID 1 and receives SIGTERM, so Kubernetes' graceful shutdown works
ENTRYPOINT ["creditdesk-worker"]
