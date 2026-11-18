import os, time, requests

REPO = "hasanaskaryan1983/PumpWatch3"
TOK = os.environ.get("GITHUB_TOKEN", "")
H = {"Authorization": f"token {TOK}", "Accept": "application/vnd.github+json"}

def latest():
    for _ in range(30):  # حداکثر ۵ دقیقه صبر برای شروع run
        try:
            r = requests.get(
                f"https://api.github.com/repos/{REPO}/actions/runs?per_page=1",
                headers=H, timeout=30
            )
            runs = r.json().get("workflow_runs", [])
            if runs:
                return runs[0]
            print("... waiting for run to start", flush=True)
        except Exception as e:
            print(f"... retry ({e.__class__.__name__})", flush=True)
        time.sleep(10)
    raise RuntimeError("No workflow run appeared within 5 minutes")

run = latest()
rid = run["id"]
print(f"run {rid} -> {run['status']}", flush=True)

while run["status"] in ("queued", "in_progress"):
    time.sleep(15)
    r = requests.get(
        f"https://api.github.com/repos/{REPO}/actions/runs/{rid}",
        headers=H, timeout=30
    )
    run = r.json()
    print(f"... {run['status']}", flush=True)

if run["conclusion"] == "success":
    print("✅ SUCCESS")
else:
    print(f"❌ {run['conclusion']}")
    jobs = requests.get(
        f"https://api.github.com/repos/{REPO}/actions/runs/{rid}/jobs",
        headers=H, timeout=30
    ).json().get("jobs", [])
    for j in jobs:
        if j.get("conclusion") == "failure":
            print(f"   failed job: {j['name']}")
            for s in j.get("steps", []):
                if s.get("conclusion") == "failure":
                    print(f"   failed step: {s['name']}")
