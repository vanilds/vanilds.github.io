import json

def m(s):  # small mono caption inside a node
    return f"<span style='font-family:DejaVu Sans Mono,monospace;font-size:13px;color:#8b9bb8'>{s}</span>"

CLASSES = """
    classDef host fill:#1b1508,stroke:#f5a03a,stroke-width:2px,color:#ffffff
    classDef app fill:#0e1a2c,stroke:#38bdf8,stroke-width:2px,color:#e8eef8
    classDef data fill:#0c1d1a,stroke:#34d399,stroke-width:2px,color:#e8eef8
    classDef ext fill:#16122a,stroke:#a78bfa,stroke-width:2px,color:#e8eef8
    classDef sec fill:#1f0f14,stroke:#f472b6,stroke-width:2px,color:#e8eef8
    classDef person fill:#111a2e,stroke:#8b9bb8,stroke-width:1.5px,color:#e8eef8
    linkStyle default stroke:#5b6b8c,stroke-width:1.6px
"""

def sg(*names):
    return "\n".join(f"    style {n} fill:transparent,stroke:#2a3654,stroke-width:1.5px,stroke-dasharray:6 5,color:#8b9bb8" for n in names)

overview = f"""flowchart LR
    visitor(["Recruiter / visitor"]):::person
    demo(["Demo user<br/>{m('analista.demo · gestor.demo')}"]):::person

    subgraph gh["GITHUB · always on · free"]
        pages["<b>shell</b> on GitHub Pages<br/>{m('portfolio · resume · demo host')}"]:::host
        repo[("Repository<br/>{m('code · manifests · docs')}")]:::ext
    end

    subgraph aws["AWS · us-east-1 · on when needed"]
        subgraph ec2["EC2 t4g.medium · k3s"]
            remotes["mfe-customers · mfe-proposals<br/>{m('Native Federation remotes')}"]:::app
            backend["api · ai-worker · Keycloak<br/>{m('Postgres · RabbitMQ')}"]:::app
        end
        s3[("Amazon S3<br/>{m('documents · backups')}")]:::data
    end

    llm[("LLM API")]:::ext

    visitor --> pages
    demo --> pages
    pages -. "Try the demo<br/>loads remotes at runtime" .-> remotes
    remotes -- "/api · JWT" --> backend
    backend --> s3
    backend --> llm
    repo -. "GitOps · Argo CD" .-> ec2
{CLASSES}
{sg('gh','aws','ec2')}
"""

infra = f"""flowchart TB
    internet(["Internet"]):::person
    duck["creditdesk.duckdns.org<br/>{m('IP updated at every boot')}"]:::ext
    sched["GitHub Actions<br/>{m('start 08:00 · stop 20:00 · manual button')}"]:::ext

    subgraph aws["AWS ACCOUNT · us-east-1"]
        budgets["AWS Budgets<br/>{m('alerts at $10 · $50 · $80')}"]:::sec
        role["IAM Role · instance profile<br/>{m('S3 + SSM on creditdesk-* only')}"]:::sec
        ssm["SSM Parameter Store<br/>{m('/creditdesk/* SecureString')}"]:::sec
        subgraph vpc["DEFAULT VPC · public subnet"]
            sgp{{{{"Security Group<br/>{m('in 80/443 · no SSH')}"}}}}:::sec
            subgraph ec2["EC2 t4g.medium · arm64 · 4 GB + 2 GB swap · gp3 30 GB"]
                ingress["Traefik Ingress<br/>{m('cert-manager · Let’s Encrypt')}"]:::host
                apps["creditdesk namespace<br/>{m('mfe-* · api · ai-worker · keycloak')}"]:::app
                data[("Postgres · RabbitMQ<br/>{m('StatefulSets · local-path PVC')}")]:::data
                ops["Argo CD · Prometheus · Grafana<br/>{m('CronJob pg_dump → S3')}"]:::app
            end
        end
        s3docs[("S3 · documents<br/>{m('private · SSE-S3 · versioning')}")]:::data
        s3bkp[("S3 · backups<br/>{m('expire after 30 days')}")]:::data
    end

    internet --> duck --> sgp --> ingress --> apps --> data
    apps -- "presigned URLs" --> s3docs
    ops --> s3bkp
    role -. "temp credentials · IMDSv2" .-> apps
    ssm -. "secrets at deploy" .-> apps
    sched -. "OIDC role · start/stop" .-> ec2
{CLASSES}
{sg('aws','vpc','ec2')}
"""

cluster = f"""flowchart LR
    user(["Analyst / manager"]):::person
    shell["<b>shell</b><br/>{m('GitHub Pages')}"]:::host

    subgraph k8s["k3s · namespace creditdesk"]
        ing["Traefik Ingress<br/>{m('one address · TLS')}"]:::host
        mfec["mfe-customers<br/>{m('customer CRUD')}"]:::app
        mfep["mfe-proposals<br/>{m('workflow · AI opinion')}"]:::app
        kc["Keycloak<br/>{m('OIDC · roles')}"]:::sec
        api["api<br/>{m('Spring Boot 3 · Java 21 · HPA 1–3')}"]:::app
        worker["ai-worker<br/>{m('Python · LangGraph')}"]:::app
        mq[("RabbitMQ<br/>{m('proposals exchange')}")]:::data
        db[("PostgreSQL")]:::data
        keda["KEDA<br/>{m('0–3 pods by queue length')}"]:::sec
    end

    s3[("Amazon S3")]:::data
    llm[("LLM API")]:::ext

    user --> shell
    shell -. "import map" .-> mfec
    shell -. "import map" .-> mfep
    user --> ing
    ing -- "/mfe/*" --> mfec
    ing -- "/mfe/*" --> mfep
    ing -- "/auth" --> kc
    ing -- "/api" --> api
    api -- "validate JWT" --> kc
    api --> db
    api -- "presigned URLs" --> s3
    api -- "proposal.submitted" --> mq
    mq --> worker
    keda -. "scales" .-> worker
    keda -. "reads queue" .-> mq
    worker --> llm
    worker -- "opinion" --> api
{CLASSES}
{sg('k8s')}
"""

specs = {
    "system-overview": dict(kicker="// CREDITDESK · OVERVIEW", title="A portfolio that *actually runs*",
        subtitle="Resume always online on GitHub Pages. The demo — a real credit-proposal desk — <b>boots on AWS when it's needed</b>.",
        mermaid=overview, width=1600),
    "aws-infrastructure": dict(kicker="// INFRA · AWS", title="EC2 + S3 with *zero* access keys",
        subtitle="k3s on a single t4g.medium, IAM role, SSM secrets, a budget alarm — and <b>no SSH port open</b>.",
        mermaid=infra, width=1600),
    "inside-the-cluster": dict(kicker="// KUBERNETES · INSIDE", title="What runs *inside* the cluster",
        subtitle="Micro frontends, a Spring Boot API, an AI worker and <b>the queue that keeps the UI fast</b>.",
        mermaid=cluster, width=1700),
}
for name, s in specs.items():
    json.dump(s, open(f"{name}.json", "w"))
print("specs ok")
