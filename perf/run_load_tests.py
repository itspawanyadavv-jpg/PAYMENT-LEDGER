import subprocess
import json
import os
import sys
import time

PERF_DIR = r"D:\PROJECTS\RESUME PROJECTS\PAYMENT LEDGER MAIN\perf"

endpoints = [
    {
        "name": "POST /api/v1/payments (Double-Entry Transfer)",
        "script": "transfer_load.js",
        "tag": "transfer"
    },
    {
        "name": "GET /api/v1/accounts/{id} (Account Balance Lookup)",
        "script": "account_lookup_load.js",
        "tag": "account_lookup"
    },
    {
        "name": "GET /api/v1/ledger/entries (Audit Stream Limit=50)",
        "script": "ledger_entries_load.js",
        "tag": "ledger_entries"
    }
]

vu_tiers = [10, 50, 100]
duration = "60s"

results = []

print("=================================================================")
print(f"STARTING K6 LOAD TESTING SUITE (3 Endpoints x [10, 50, 100] VUs x 60s)")
print("=================================================================")

for ep in endpoints:
    for vus in vu_tiers:
        summary_file = os.path.join(PERF_DIR, f"summary_{ep['tag']}_{vus}vu.json")
        if os.path.exists(summary_file):
            try:
                os.remove(summary_file)
            except Exception:
                pass
        
        container_summary_path = f"/perf/summary_{ep['tag']}_{vus}vu.json"
        script_container_path = f"/perf/{ep['script']}"

        cmd = [
            "docker", "run", "--rm",
            "-v", f"{PERF_DIR}:/perf",
            "grafana/k6", "run",
            "--vus", str(vus),
            "--duration", duration,
            "--summary-trend-stats=avg,min,med,max,p(90),p(95),p(99)",
            f"--summary-export={container_summary_path}",
            script_container_path
        ]

        print(f"\n---> Running: {ep['name']} | VUs: {vus} | Duration: {duration}...")
        t0 = time.time()
        proc = subprocess.run(cmd, capture_output=True, text=True)
        elapsed = time.time() - t0

        if proc.returncode != 0:
            print(f"WARN: Test exited with code {proc.returncode}")
            print(proc.stderr[:400])

        # Parse summary
        rps = 0.0
        p50 = 0.0
        p95 = 0.0
        p99 = 0.0
        err_rate = 0.0
        total_reqs = 0

        if os.path.exists(summary_file):
            try:
                with open(summary_file, 'r', encoding='utf-8') as sf:
                    data = json.load(sf)
                m = data.get('metrics', {})
                reqs_metric = m.get('http_reqs', {})
                rps = reqs_metric.get('rate', 0.0)
                total_reqs = reqs_metric.get('count', 0)
                
                dur_metric = m.get('http_req_duration', {})
                p50 = dur_metric.get('med', 0.0)
                p95 = dur_metric.get('p(95)', 0.0)
                p99 = dur_metric.get('p(99)', 0.0)

                err_metric = m.get('http_req_failed', {})
                err_rate = err_metric.get('rate', 0.0) * 100.0
            except Exception as e:
                print(f"Error parsing summary: {e}")
        else:
            print(f"Warning: summary file {summary_file} not found!")

        result_entry = {
            "endpoint": ep["name"],
            "vus": vus,
            "duration": duration,
            "total_requests": total_reqs,
            "rps": round(rps, 2),
            "p50_ms": round(p50, 2),
            "p95_ms": round(p95, 2),
            "p99_ms": round(p99, 2),
            "error_rate_pct": round(err_rate, 2),
            "raw_output": proc.stdout
        }
        results.append(result_entry)

        print(f"DONE in {elapsed:.1f}s | Req/s: {result_entry['rps']} | p50: {result_entry['p50_ms']}ms | p95: {result_entry['p95_ms']}ms | p99: {result_entry['p99_ms']}ms | Errors: {result_entry['error_rate_pct']}%")

# Save all results to a JSON file
with open(os.path.join(PERF_DIR, "load_test_results.json"), "w", encoding="utf-8") as out:
    json.dump(results, out, indent=2)

print("\n=================================================================")
print("ALL LOAD TESTS COMPLETED. SUMMARY TABLE:")
print("=================================================================")
print(f"{'Endpoint':48} | {'VUs':3} | {'Req/s':8} | {'p50 (ms)':8} | {'p95 (ms)':8} | {'p99 (ms)':8} | {'Errors':7}")
print("-" * 108)
for r in results:
    print(f"{r['endpoint']:48} | {r['vus']:3} | {r['rps']:8.2f} | {r['p50_ms']:8.2f} | {r['p95_ms']:8.2f} | {r['p99_ms']:8.2f} | {r['error_rate_pct']:6.2f}%")
