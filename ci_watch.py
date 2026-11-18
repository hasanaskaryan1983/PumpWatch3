import os, sys, time, requests
TOKEN = os.environ['GITHUB_TOKEN']; REPO = 'hasanaskaryan1983/PumpWatch3'
H = {'Authorization': f'token {TOKEN}'}
def latest():
    return requests.get(f'https://api.github.com/repos/{REPO}/actions/runs?per_page=1',
                        headers=H, timeout=30).json()['workflow_runs'][0]
run = latest()
while run['status'] in ('queued', 'in_progress'):
    print('...', run['status'], flush=True); time.sleep(20); run = latest()
print('run', run['id'], '->', run['conclusion'])
if run['conclusion'] == 'success':
    print('✅ SUCCESS'); sys.exit(0)
for j in requests.get(run['jobs_url'], headers=H, timeout=30).json()['jobs']:
    if j['conclusion'] == 'failure':
        print('❌ failed job:', j['name'])
        for s in j['steps']:
            if s['conclusion'] == 'failure':
                print('   failed step:', s['name'])
sys.exit(1)
