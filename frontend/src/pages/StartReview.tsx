import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { startSession, type StartResponse } from '../api';
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

export default function StartReview() {
  const [yaml, setYaml] = useState<string>(SAMPLE);
  const [gitRef, setGitRef] = useState<string>('HEAD');
  const [diff, setDiff] = useState<string>('');
  const [busy, setBusy] = useState<boolean>(false);
  const [err, setErr] = useState<string>('');
  const [result, setResult] = useState<StartResponse | null>(null);
  const navigate = useNavigate();

  async function start() {
    setBusy(true);
    setErr('');
    setResult(null);
    try {
      const r = await startSession(yaml, diff, gitRef);
      setResult(r);
    } catch (e) {
      setErr(String((e as Error).message || e));
    } finally {
      setBusy(false);
    }
  }

  if (result) {
    return <ResultView result={result} onAgain={() => setResult(null)} />;
  }

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-xl font-semibold text-slate-800">启动评审</h2>
        <p className="mt-1 text-sm text-slate-500">
          粘贴 <code>council.yaml</code>，指定 git ref，点开始。后端会按 yaml
          配置并行调用 reviewer LLM；典型耗时 10s–几分钟（取决于文件数和模型）。
        </p>
      </div>

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
            onClick={start}
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

          <div className="text-xs text-slate-500 p-3 bg-slate-50 rounded border border-slate-200">
            <div className="font-semibold text-slate-700 mb-1">提示</div>
            <ul className="space-y-1 list-disc list-inside">
              <li>没配 <code>ANTHROPIC_API_KEY</code> / <code>DEEPSEEK_API_KEY</code> / <code>MINIMAX_API_KEY</code>？后端启动日志 <code>LLM providers loaded: ...</code> 会告诉你实际加载了哪些</li>
              <li>yaml 里的 <code>model</code> 字段决定用哪个 provider</li>
              <li>评审结果会存到 SQLite + 返回到这里</li>
            </ul>
          </div>
        </div>
      </div>
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
