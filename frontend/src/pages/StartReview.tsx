import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { startSession, scanSession, type StartResponse, type ScanResponse } from '../api';
import { severityClass, severityCn, severityRank } from '../severity';

const SAMPLE = `council:
  name: team-java-backend
loop:
  max-rounds: 2
reviewers:
  - role: security
    model: claude-sonnet-5-20250929
    prompt-file: prompts/security.md
  - role: perf
    model: deepseek-chat
    prompt-file: prompts/perf.md
budget:
  max-tokens: 200000
  max-cost-usd: "1.00"`;

type Mode = 'diff' | 'scan';

export default function StartReview() {
  const [mode, setMode] = useState<Mode>('diff');
  const [yaml, setYaml] = useState<string>(SAMPLE);
  const [gitRef, setGitRef] = useState<string>('HEAD');
  const [diff, setDiff] = useState<string>('');
  const [scanPath, setScanPath] = useState<string>('.');
  const [scanMaxTokens, setScanMaxTokens] = useState<number>(50000);
  const [scanConcurrency, setScanConcurrency] = useState<number>(3);
  const [busy, setBusy] = useState<boolean>(false);
  const [err, setErr] = useState<string>('');
  const [diffResult, setDiffResult] = useState<StartResponse | null>(null);
  const [scanResult, setScanResult] = useState<ScanResponse | null>(null);
  const navigate = useNavigate();

  async function startDiff() {
    setBusy(true); setErr(''); setScanResult(null);
    try {
      const r = await startSession(yaml, diff, gitRef);
      setDiffResult(r);
    } catch (e) { setErr(String((e as Error).message || e)); }
    finally { setBusy(false); }
  }

  async function startScan() {
    setBusy(true); setErr(''); setDiffResult(null);
    try {
      const r = await scanSession(yaml, {
        path: scanPath, maxTokens: scanMaxTokens, concurrency: scanConcurrency,
      });
      setScanResult(r);
    } catch (e) { setErr(String((e as Error).message || e)); }
    finally { setBusy(false); }
  }

  if (diffResult) return <ResultView result={diffResult} onAgain={() => setDiffResult(null)} />;
  if (scanResult) return <ScanResultView result={scanResult} onAgain={() => setScanResult(null)} />;

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-xl font-semibold text-slate-800">启动评审</h2>
        <p className="mt-1 text-sm text-slate-500">
          粘贴 <code>council.yaml</code>，选择评审方式，点开始。后端会按 yaml
          配置并行调用 reviewer LLM。
        </p>
      </div>

      {/* Mode tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        <button
          onClick={() => setMode('diff')}
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
            mode === 'diff'
              ? 'border-council-500 text-council-700'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}>
          评审 PR diff
        </button>
        <button
          onClick={() => setMode('scan')}
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
            mode === 'scan'
              ? 'border-council-500 text-council-700'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}>
          扫描整个工程
        </button>
      </div>

      {mode === 'diff' && (
        <div className="grid lg:grid-cols-2 gap-4">
          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-700">council.yaml</label>
            <textarea
              className="w-full h-96 px-3 py-2 border border-slate-300 rounded font-mono text-xs"
              value={yaml}
              onChange={e => setYaml(e.target.value)}
              spellCheck={false}
            />
            <button
              onClick={() => setYaml(SAMPLE)}
              className="text-sm text-council-500 hover:underline">
              ⚡ 加载示例
            </button>
          </div>

          <div className="space-y-3">
            <div>
              <label className="text-sm font-medium text-slate-700">Git ref</label>
              <input
                type="text"
                className="mt-1 w-full px-3 py-2 border border-slate-300 rounded font-mono text-sm"
                value={gitRef}
                onChange={e => setGitRef(e.target.value)}
                placeholder="HEAD / main / commit hash / 留空 = working tree"
              />
              <p className="mt-1 text-xs text-slate-500">
                留空 = 当前 working tree 的未提交 diff
              </p>
            </div>

            <div>
              <label className="text-sm font-medium text-slate-700">
                自定义 diff（可选）
              </label>
              <textarea
                className="mt-1 w-full h-32 px-3 py-2 border border-slate-300 rounded font-mono text-xs"
                value={diff}
                onChange={e => setDiff(e.target.value)}
                placeholder="一般不用填；后端会按 git ref 自己取"
                spellCheck={false}
              />
            </div>

            <button
              onClick={startDiff}
              disabled={busy || !yaml.trim()}
              className="w-full px-4 py-3 rounded font-medium text-white bg-council-500 hover:bg-council-700 disabled:bg-slate-300 flex items-center justify-center gap-2">
              {busy ? (
                <>
                  <span className="inline-block h-4 w-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  评审中（可能 10s–几分钟）…
                </>
              ) : (
                <>▶ 启动评审</>
              )}
            </button>

            {err && (
              <div className="p-3 rounded border border-critical bg-red-50 text-sm text-red-800">
                <div className="font-semibold">启动失败</div>
                <pre className="mt-1 text-xs whitespace-pre-wrap font-mono">{err}</pre>
              </div>
            )}
          </div>
        </div>
      )}

      {mode === 'scan' && (
        <div className="grid lg:grid-cols-2 gap-4">
          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-700">council.yaml</label>
            <textarea
              className="w-full h-96 px-3 py-2 border border-slate-300 rounded font-mono text-xs"
              value={yaml}
              onChange={e => setYaml(e.target.value)}
              spellCheck={false}
            />
            <button
              onClick={() => setYaml(SAMPLE)}
              className="text-sm text-council-500 hover:underline">
              ⚡ 加载示例
            </button>
          </div>

          <div className="space-y-3">
            <div>
              <label className="text-sm font-medium text-slate-700">仓库路径</label>
              <input
                type="text"
                className="mt-1 w-full px-3 py-2 border border-slate-300 rounded font-mono text-sm"
                value={scanPath}
                onChange={e => setScanPath(e.target.value)}
                placeholder="."
              />
              <p className="mt-1 text-xs text-slate-500">
                必须是 git 仓库根目录（包含 .git）。后端用 <code>git ls-files</code> 枚举所有源代码文件。
              </p>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-sm font-medium text-slate-700">每 chunk token</label>
                <input
                  type="number"
                  min={1000}
                  step={1000}
                  className="mt-1 w-full px-3 py-2 border border-slate-300 rounded font-mono text-sm"
                  value={scanMaxTokens}
                  onChange={e => setScanMaxTokens(parseInt(e.target.value) || 50000)}
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">并发度</label>
                <input
                  type="number"
                  min={1}
                  max={20}
                  className="mt-1 w-full px-3 py-2 border border-slate-300 rounded font-mono text-sm"
                  value={scanConcurrency}
                  onChange={e => setScanConcurrency(parseInt(e.target.value) || 3)}
                />
              </div>
            </div>

            <button
              onClick={startScan}
              disabled={busy || !yaml.trim()}
              className="w-full px-4 py-3 rounded font-medium text-white bg-council-500 hover:bg-council-700 disabled:bg-slate-300 flex items-center justify-center gap-2">
              {busy ? (
                <>
                  <span className="inline-block h-4 w-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  扫描中（可能几分钟到几十分钟）…
                </>
              ) : (
                <>▶ 扫描整个工程</>
              )}
            </button>

            {err && (
              <div className="p-3 rounded border border-critical bg-red-50 text-sm text-red-800">
                <div className="font-semibold">启动失败</div>
                <pre className="mt-1 text-xs whitespace-pre-wrap font-mono">{err}</pre>
              </div>
            )}

            <div className="text-xs text-slate-500 p-3 bg-slate-50 rounded border border-slate-200">
              <div className="font-semibold text-slate-700 mb-1">提示</div>
              <ul className="space-y-1 list-disc list-inside">
                <li>扫描会按 (chunk × reviewer) 笛卡尔积并发跑 N 次 LLM 调用</li>
                <li>代码量 = 文件数 × 单文件平均 token。100 个 java 文件 ≈ 50k tokens</li>
                <li>没配 API key？后端启动日志会显示加载了哪些 provider</li>
                <li>结果会存到 SQLite，可通过 sessionId 在详情页查看</li>
              </ul>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ResultView({ result, onAgain }: { result: StartResponse; onAgain: () => void }) {
  const findings = [...(result.findings ?? [])].sort(
    (a, b) => severityRank(a.severity) - severityRank(b.severity)
  );

  return (
    <div className="space-y-5">
      <div className="bg-emerald-50 border border-emerald-200 rounded p-4 flex items-start gap-3">
        <span className="text-emerald-600 text-xl leading-none">✓</span>
        <div className="flex-1">
          <div className="font-semibold text-emerald-800">评审完成</div>
          <div className="text-sm text-emerald-700 font-mono">{result.sessionId}</div>
        </div>
        <div className="flex gap-2">
          <Link
            to={`/sessions/${result.sessionId}`}
            className="text-sm px-3 py-1.5 rounded border border-emerald-300 text-emerald-800 hover:bg-emerald-100">
            打开详情
          </Link>
          <button
            onClick={onAgain}
            className="text-sm px-3 py-1.5 rounded bg-council-500 text-white hover:bg-council-700">
            再评一次
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200 grid grid-cols-3 gap-4">
        <div>
          <div className="text-xs text-slate-500 uppercase">成本</div>
          <div className="text-2xl font-bold text-slate-800">${result.cost?.toFixed(4) ?? '0.0000'}</div>
        </div>
        <div>
          <div className="text-xs text-slate-500 uppercase">轮数</div>
          <div className="text-2xl font-bold text-slate-800">{result.rounds ?? 0}</div>
        </div>
        <div>
          <div className="text-xs text-slate-500 uppercase">已应用补丁</div>
          <div className="text-2xl font-bold text-slate-800">{result.appliedPatches ?? 0}</div>
        </div>
      </div>

      <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
        <h3 className="font-semibold text-slate-800 mb-3">
          问题清单（{findings.length}）
        </h3>
        {findings.length === 0 ? (
          <div className="text-sm text-slate-500 py-4 text-center">
            🎉 没发现问题
          </div>
        ) : (
          <div className="space-y-2">
            {findings.map(f => (
              <div key={f.id} className={`p-3 rounded border ${severityClass(f.severity)}`}>
                <div className="flex items-baseline gap-2 flex-wrap">
                  <span className="text-xs font-bold uppercase">{severityCn(f.severity)}</span>
                  <span className="text-xs text-slate-600">· {f.reviewer}</span>
                  <span className="text-xs text-slate-600">
                    · {f.file}{f.line ? `:${f.line}` : ''}
                  </span>
                  {f.category && (
                    <span className="text-xs text-slate-500">· {f.category}</span>
                  )}
                </div>
                <div className="mt-1 text-sm text-slate-800">{f.message}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function ScanResultView({ result, onAgain }: { result: ScanResponse; onAgain: () => void }) {
  const [reviewerFilter, setReviewerFilter] = useState<string | null>(null);
  const [severityFilter, setSeverityFilter] = useState<string | null>(null);
  const [showFiles, setShowFiles] = useState(false);

  const allFindings = result.findings ?? [];
  const findings = allFindings
    .filter(f => !reviewerFilter || f.reviewer === reviewerFilter)
    .filter(f => !severityFilter || f.severity === severityFilter)
    .sort((a, b) => severityRank(a.severity) - severityRank(b.severity));

  const reviewersInResult = Array.from(new Set(allFindings.map(f => f.reviewer)));

  return (
    <div className="space-y-5">
      <div className="bg-emerald-50 border border-emerald-200 rounded p-4 flex items-start gap-3">
        <span className="text-emerald-600 text-xl leading-none">✓</span>
        <div className="flex-1">
          <div className="font-semibold text-emerald-800">扫描完成</div>
          <div className="text-sm text-emerald-700 font-mono">{result.sessionId}</div>
          {result.scanRoot && (
            <div className="text-xs text-emerald-600 font-mono mt-1">
              扫描根：{result.scanRoot}
            </div>
          )}
        </div>
        <div className="flex gap-2">
          <Link
            to={`/sessions/${result.sessionId}`}
            className="text-sm px-3 py-1.5 rounded border border-emerald-300 text-emerald-800 hover:bg-emerald-100">
            打开详情
          </Link>
          <button
            onClick={onAgain}
            className="text-sm px-3 py-1.5 rounded bg-council-500 text-white hover:bg-council-700">
            再扫一次
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200 grid grid-cols-4 gap-4">
        <div>
          <div className="text-xs text-slate-500 uppercase">文件数</div>
          <div className="text-2xl font-bold text-slate-800">{result.totalFiles}</div>
        </div>
        <div>
          <div className="text-xs text-slate-500 uppercase">chunk 数</div>
          <div className="text-2xl font-bold text-slate-800">{result.totalChunks}</div>
        </div>
        <div>
          <div className="text-xs text-slate-500 uppercase">耗时</div>
          <div className="text-2xl font-bold text-slate-800">{Math.round(result.durationMs / 1000)}s</div>
        </div>
        <div>
          <div className="text-xs text-slate-500 uppercase">成本</div>
          <div className="text-2xl font-bold text-slate-800">${result.cost?.toFixed(4) ?? '0.0000'}</div>
        </div>
      </div>

      {/* Per-reviewer breakdown */}
      {result.perReviewer && Object.keys(result.perReviewer).length > 0 && (
        <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
          <h3 className="font-semibold text-slate-800 mb-3">各 reviewer 产出</h3>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {Object.entries(result.perReviewer).map(([role, sum]) => (
              <div key={role} className="p-3 rounded border border-slate-200 bg-slate-50">
                <div className="flex items-baseline justify-between">
                  <div className="font-medium text-slate-800">{role}</div>
                  <div className="text-sm text-slate-600">
                    ${sum.costUsd.toFixed(4)}
                  </div>
                </div>
                <div className="mt-1 text-xs text-slate-500">
                  {sum.findingsCount} findings · in={sum.tokensIn.toLocaleString()} out={sum.tokensOut.toLocaleString()}
                </div>
                <div className="mt-2 flex gap-2 text-xs">
                  <span className="px-1.5 py-0.5 rounded bg-red-100 text-red-700">
                    critical {sum.bySeverity.critical ?? 0}
                  </span>
                  <span className="px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">
                    major {sum.bySeverity.major ?? 0}
                  </span>
                  <span className="px-1.5 py-0.5 rounded bg-slate-200 text-slate-700">
                    minor {sum.bySeverity.minor ?? 0}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Scanned files (collapsible) */}
      {result.files && result.files.length > 0 && (
        <div className="bg-white rounded-lg shadow-sm border border-slate-200">
          <button
            onClick={() => setShowFiles(s => !s)}
            className="w-full p-4 flex items-center justify-between text-left hover:bg-slate-50">
            <span className="font-semibold text-slate-800">
              扫描的文件（{result.files.length}）
            </span>
            <span className="text-slate-400">{showFiles ? '▾' : '▸'}</span>
          </button>
          {showFiles && (
            <div className="px-4 pb-4 max-h-72 overflow-auto">
              <ul className="text-xs font-mono space-y-0.5">
                {result.files.map(p => (
                  <li key={p} className="text-slate-600 truncate" title={p}>{p}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Findings with filters */}
      <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
        <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
          <h3 className="font-semibold text-slate-800">
            问题清单（{findings.length}{findings.length !== allFindings.length ? ` / ${allFindings.length}` : ''}）
          </h3>
          <div className="flex gap-2 flex-wrap">
            <FilterChip
              label="全部 reviewer"
              active={reviewerFilter === null}
              onClick={() => setReviewerFilter(null)} />
            {reviewersInResult.map(r => (
              <FilterChip
                key={r}
                label={r}
                active={reviewerFilter === r}
                onClick={() => setReviewerFilter(reviewerFilter === r ? null : r)} />
            ))}
            <span className="w-px bg-slate-200 mx-1" />
            <FilterChip
              label="全部级别"
              active={severityFilter === null}
              onClick={() => setSeverityFilter(null)} />
            {(['critical', 'major', 'minor'] as const).map(s => (
              <FilterChip
                key={s}
                label={severityCn(s)}
                active={severityFilter === s}
                color={severityClass(s).split(' ').find(c => c.startsWith('border-'))}
                onClick={() => setSeverityFilter(severityFilter === s ? null : s)} />
            ))}
          </div>
        </div>
        {findings.length === 0 ? (
          <div className="text-sm text-slate-500 py-4 text-center">
            {allFindings.length === 0 ? '🎉 没发现问题' : '（当前筛选无匹配）'}
          </div>
        ) : (
          <div className="space-y-2">
            {findings.map(f => (
              <div key={f.id} className={`p-3 rounded border ${severityClass(f.severity)}`}>
                <div className="flex items-baseline gap-2 flex-wrap">
                  <span className="text-xs font-bold uppercase">{severityCn(f.severity)}</span>
                  <span className="text-xs text-slate-600">· {f.reviewer}</span>
                  {(f.filePath ?? f.file) && (
                    <span className="text-xs text-slate-600 font-mono">
                      · {f.filePath ?? f.file}{f.line ? `:${f.line}` : ''}
                    </span>
                  )}
                  {f.chunkId && (
                    <span className="text-xs text-slate-400 font-mono" title="所属 chunk">
                      · chunk {f.chunkId}
                    </span>
                  )}
                  {f.category && (
                    <span className="text-xs text-slate-500">· {f.category}</span>
                  )}
                </div>
                <div className="mt-1 text-sm text-slate-800">{f.message}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function FilterChip({ label, active, onClick, color }:
  { label: string; active: boolean; onClick: () => void; color?: string }) {
  return (
    <button
      onClick={onClick}
      className={`text-xs px-2.5 py-1 rounded-full border transition ${
        active
          ? 'bg-council-500 text-white border-council-500'
          : `bg-white text-slate-600 hover:bg-slate-100 ${color ?? 'border-slate-300'}`
      }`}>
      {label}
    </button>
  );
}