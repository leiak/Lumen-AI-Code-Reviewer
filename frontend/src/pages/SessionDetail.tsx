import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getCost, getSession, getDemoSession, type CostResponse, type DemoSession } from '../api';
import { severityClass, severityCn, severityRank } from '../severity';

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const isDemo = id === 'rev-demo01' || id === 'demo';
  const [session, setSession] = useState<unknown>(null);
  const [cost, setCost] = useState<CostResponse | null>(null);
  const [demo, setDemo] = useState<DemoSession | null>(null);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    if (!id) return;
    if (isDemo) {
      getDemoSession().then(setDemo).catch(e => setError(String(e)));
      setCost({ sessionId: id, costUsd: 0.0423, tokens: 4521 });
    } else {
      getSession(id).then(setSession).catch(e => setError(String(e.message || e)));
      getCost(id).then(setCost).catch(() => {});
    }
  }, [id, isDemo]);

  if (error) {
    return (
      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-slate-800 font-mono">{id}</h2>
        <div className="p-3 rounded border border-critical bg-red-50 text-sm text-red-800">{error}</div>
        <Link to="/" className="text-council-500 underline">← 返回</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-baseline gap-3">
        <h2 className="text-xl font-semibold text-slate-800 font-mono">{id}</h2>
        {isDemo && (
          <span className="px-2 py-0.5 bg-amber-100 text-amber-800 text-xs font-medium rounded">演示</span>
        )}
        <Link to="/" className="ml-auto text-council-500 text-sm">← 返回仪表盘</Link>
      </div>

      {isDemo && (
        <div className="bg-amber-50 border border-amber-200 rounded p-3 text-sm text-amber-900">
          这是静态演示数据。真实评审需要 <code>ANTHROPIC_API_KEY</code>，并执行
          <code className="ml-1 px-1 bg-amber-100 rounded">java -jar review.jar run</code>。
        </div>
      )}

      {cost && (
        <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200 grid grid-cols-3 gap-4">
          <div>
            <div className="text-xs text-slate-500 uppercase">成本</div>
            <div className="text-2xl font-bold text-slate-800">${cost.costUsd.toFixed(4)}</div>
          </div>
          <div>
            <div className="text-xs text-slate-500 uppercase">Tokens</div>
            <div className="text-2xl font-bold text-slate-800">{cost.tokens.toLocaleString()}</div>
          </div>
          <div>
            <div className="text-xs text-slate-500 uppercase">轮数</div>
            <div className="text-2xl font-bold text-slate-800">
              {(demo?.rounds ?? (session as { rounds?: number })?.rounds ?? '—')}
            </div>
          </div>
        </div>
      )}

      {demo && (
        <>
          <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
            <h3 className="font-semibold text-slate-800 mb-3">
              问题清单（{demo.findings.length}）
            </h3>
            <div className="space-y-2">
              {demo.findings
                .slice()
                .sort((a, b) => severityRank(a.severity) - severityRank(b.severity))
                .map(f => (
                  <div key={f.id} className={`p-3 rounded border ${severityClass(f.severity)}`}>
                    <div className="flex items-baseline gap-2">
                      <span className="text-xs font-bold uppercase">{severityCn(f.severity)}</span>
                      <span className="text-xs text-slate-600">· {f.reviewer}</span>
                      <span className="text-xs text-slate-600">· {f.file}:{f.line}</span>
                    </div>
                    <div className="mt-1 text-sm text-slate-800">{f.message}</div>
                  </div>
                ))}
            </div>
          </div>

          <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
            <h3 className="font-semibold text-slate-800 mb-3">
              已应用补丁（{demo.appliedPatches.length}）
            </h3>
            <div className="space-y-2">
              {demo.appliedPatches.map(p => (
                <div key={p.id} className="p-3 bg-green-50 border border-green-200 rounded">
                  <div className="flex items-baseline gap-2">
                    <span className="text-xs font-bold text-green-800 uppercase">{p.status === 'applied' ? '已应用' : p.status}</span>
                    <span className="text-xs text-slate-600">· {p.file}</span>
                  </div>
                  <div className="mt-1 text-sm text-slate-800">{p.description}</div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {isDemo ? null : session ? (
        <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
          <h3 className="font-semibold text-slate-800 mb-2">Session 详细信息</h3>
          <pre className="text-xs bg-slate-50 p-3 rounded overflow-auto">
            {String(JSON.stringify(session, null, 2))}
          </pre>
        </div>
      ) : null}
    </div>
  );
}
