import csv

csv_path = r'D:\PROJECTS\RESUME PROJECTS\PAYMENT LEDGER MAIN\target\site\jacoco\jacoco.csv'

rows = []
with open(csv_path, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for r in reader:
        rows.append(r)

packages = {}
total_line_missed = 0
total_line_covered = 0
total_branch_missed = 0
total_branch_covered = 0

for r in rows:
    pkg = r['PACKAGE']
    lm = int(r['LINE_MISSED'])
    lc = int(r['LINE_COVERED'])
    bm = int(r['BRANCH_MISSED'])
    bc = int(r['BRANCH_COVERED'])
    
    total_line_missed += lm
    total_line_covered += lc
    total_branch_missed += bm
    total_branch_covered += bc
    
    if pkg not in packages:
        packages[pkg] = {'lm': 0, 'lc': 0, 'bm': 0, 'bc': 0}
    packages[pkg]['lm'] += lm
    packages[pkg]['lc'] += lc
    packages[pkg]['bm'] += bm
    packages[pkg]['bc'] += bc

tot_lines = total_line_covered + total_line_missed
tot_line_cov = (total_line_covered / tot_lines * 100) if tot_lines > 0 else 0
tot_branches = total_branch_covered + total_branch_missed
tot_branch_cov = (total_branch_covered / tot_branches * 100) if tot_branches > 0 else 0

print('=== OVERALL COVERAGE ===')
print(f'Line Coverage:   {total_line_covered}/{tot_lines} ({tot_line_cov:.2f}%)')
print(f'Branch Coverage: {total_branch_covered}/{tot_branches} ({tot_branch_cov:.2f}%)')
print()
print('=== PACKAGE BREAKDOWN ===')
for pkg in sorted(packages.keys()):
    d = packages[pkg]
    pl = d['lc'] + d['lm']
    pl_cov = (d['lc'] / pl * 100) if pl > 0 else 0
    pb = d['bc'] + d['bm']
    pb_cov = (d['bc'] / pb * 100) if pb > 0 else 0
    lc = d['lc']
    bc = d['bc']
    print(f"{pkg:55} | Lines: {lc:4}/{pl:4} ({pl_cov:6.2f}%) | Branches: {bc:3}/{pb:3} ({pb_cov:6.2f}%)")

print()
print('=== SERVICE LAYER BREAKDOWN (com.aman.payment.application.service) ===')
svc_lc = packages.get('com.aman.payment.application.service', {}).get('lc', 0)
svc_lm = packages.get('com.aman.payment.application.service', {}).get('lm', 0)
svc_tot_l = svc_lc + svc_lm
svc_bc = packages.get('com.aman.payment.application.service', {}).get('bc', 0)
svc_bm = packages.get('com.aman.payment.application.service', {}).get('bm', 0)
svc_tot_b = svc_bc + svc_bm
svc_l_cov = (svc_lc / svc_tot_l * 100) if svc_tot_l > 0 else 0
svc_b_cov = (svc_bc / svc_tot_b * 100) if svc_tot_b > 0 else 0
print(f'Service Lines:    {svc_lc}/{svc_tot_l} ({svc_l_cov:.2f}%)')
print(f'Service Branches: {svc_bc}/{svc_tot_b} ({svc_b_cov:.2f}%)')
