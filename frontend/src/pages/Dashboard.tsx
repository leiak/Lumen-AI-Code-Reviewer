import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { health } from '../api';

const STEPS = [
  { n: 1, title: '看状态图', desc: '查看 LangGraph4j 编排的评审流水线拓扑', to: '/graph' },
  { n: 2, title: '校验 council.yaml', desc: '粘贴你的配置文件，检查 schema 和 reviewer 配置', to: '/validate' },
  { n: 3, title: '试用 Demo Session', desc: '看一个真实风格的评审结果（无需 API key）', to: '/sessions/rev-demo01' },
  { n: 4, title: '跑真实评审（需 API key）', desc: '设置 ANTHROPIC_API_KEY，从 CLI 跑 review run', to: '#', cli: 'java -jar review.jar run HEAD' },
];

export default function Dashboard() {
  const [status, setStatus] = useState<string>('checking...');
  const [sessionId, setSessionId] = useState<string>('');
  const navigate = useNavigate();

  useEffect(() => { health().then(h => setStatus(h.status)).catch(() => setStatus('unreachable')); }, []);

  return (
    <div className="space-y-8">
      {/* Hero */}
      <section className="bg-gradient-to-br from-council-500 to-council-700 text-white rounded-lg p-8 shadow">
        <h1 className="text-3xl font-bold">AI 代码评审委员会</h1>
        <p className="mt-2 text-council-50">
          多模型评审：架构、安全、性能。可配置、可审计、免费试用。
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Link to="/graph" className="px-4 py-2 bg-white text-council-700 rounded font-medium hover:bg-council-50">
            查看状态图 →
          </Link>
          <button onClick={() => navigate('/sessions/rev-demo01')}
            className="px-4 py-2 bg-council-900 text-white rounded font-medium hover:bg-council-700">
            试用 Demo Session
          </button>
        </div>
      </section>

      {/* Backend status */}
      <section className="bg-white rounded-lg p-4 shadow-sm border border-slate-200 flex items-center gap-4">
        <span className={`h-3 w-3 rounded-full shrink-0 ${status === 'ok' ? 'bg-minor' : 'bg-critical'}`} />
        <div className="flex-1">
          <div className="font-mono text-sm">后端状态：{status === 'ok' ? '正常' : status === 'unreachable' ? '无法连接' : '检测中…'}</div>
          <div className="text-xs text-slate-500">
            预期地址 <code>http://localhost:8090</code>。启动命令：
            <code className="ml-1 px-1.5 py-0.5 bg-slate-100 rounded">java -jar target\review.jar serve --server.port=8090</code>
          </div>
        </div>
      </section>

      {/* Getting started */}
      <section>
        <h2 className="text-lg font-semibold text-slate-800 mb-3">快速上手 — 4 步</h2>
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {STEPS.map(s => (
            <div key={s.n} className="bg-white rounded-lg p-4 shadow-sm border border-slate-200 flex flex-col">
              <div className="text-xs font-bold text-council-500">第 {s.n} 步</div>
              <div className="mt-1 font-semibold text-slate-800">{s.title}</div>
              <div className="mt-1 text-xs text-slate-500 flex-1">{s.desc}</div>
              {s.cli ? (
                <code className="mt-2 text-xs bg-slate-100 p-2 rounded block overflow-x-auto">{s.cli}</code>
              ) : (
                <Link to={s.to} className="mt-3 text-sm text-council-500 hover:underline">打开 →</Link>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Quick actions */}
      <section className="grid sm:grid-cols-3 gap-3">
        <Link to="/validate" className="block p-4 bg-white border border-slate-200 rounded hover:border-council-500">
          <div className="font-semibold text-council-700">校验配置</div>
          <div className="text-sm text-slate-500 mt-1">检查一个 council.yaml</div>
        </Link>
        <Link to="/graph" className="block p-4 bg-white border border-slate-200 rounded hover:border-council-500">
          <div className="font-semibold text-council-700">状态图</div>
          <div className="text-sm text-slate-500 mt-1">查看 LangGraph4j 拓扑</div>
        </Link>
        <Link to="/sessions/rev-demo01" className="block p-4 bg-white border border-slate-200 rounded hover:border-council-500">
          <div className="font-semibold text-council-700">Demo Session</div>
          <div className="text-sm text-slate-500 mt-1">看一个真实风格的评审结果</div>
        </Link>
      </section>

      {/* Session lookup */}
      <section className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
        <h2 className="font-semibold text-slate-800 mb-2">按 Session ID 查询</h2>
        <div className="flex gap-2">
          <input
            type="text" placeholder="rev-abc12345（或 rev-demo01）"
            value={sessionId}
            onChange={e => setSessionId(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && sessionId && navigate(`/sessions/${sessionId}`)}
            className="flex-1 px-3 py-2 border border-slate-300 rounded font-mono text-sm"
          />
          <button
            onClick={() => sessionId && navigate(`/sessions/${sessionId}`)}
            disabled={!sessionId}
            className="px-4 py-2 rounded text-white font-medium bg-council-500 hover:bg-council-700 disabled:bg-slate-300">
            打开
          </button>
        </div>
      </section>
    </div>
  );
}
