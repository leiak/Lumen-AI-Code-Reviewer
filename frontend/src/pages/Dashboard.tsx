import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { health } from '../api';

export default function Dashboard() {
  const [status, setStatus] = useState<string>('checking...');
  const [sessionId, setSessionId] = useState<string>('');

  useEffect(() => { health().then(h => setStatus(h.status)).catch(() => setStatus('unreachable')); }, []);

  return (
    <div className="space-y-8">
      <section className="bg-white rounded-lg p-6 shadow-sm border border-slate-200">
        <h2 className="text-xl font-semibold text-slate-800">Backend status</h2>
        <div className="mt-3 flex items-center gap-3">
          <span className={`h-3 w-3 rounded-full ${status === 'ok' ? 'bg-minor' : 'bg-critical'}`} />
          <span className="font-mono text-sm">{status}</span>
        </div>
        <p className="text-xs text-slate-500 mt-2">
          Backend is the Spring Boot server on <code>localhost:8090</code>. Start it with:
          <code className="ml-2 px-2 py-0.5 bg-slate-100 rounded">java -jar review.jar serve --server.port=8090</code>
        </p>
      </section>

      <section className="bg-white rounded-lg p-6 shadow-sm border border-slate-200">
        <h2 className="text-xl font-semibold text-slate-800">Quick actions</h2>
        <div className="mt-4 grid sm:grid-cols-2 gap-4">
          <Link to="/validate" className="block p-4 border border-slate-200 rounded hover:border-council-500 hover:bg-council-50">
            <div className="font-semibold text-council-700">Validate council.yaml</div>
            <div className="text-sm text-slate-500 mt-1">Paste your config to check schema + reviewers</div>
          </Link>
          <Link to="/graph" className="block p-4 border border-slate-200 rounded hover:border-council-500 hover:bg-council-50">
            <div className="font-semibold text-council-700">View state graph</div>
            <div className="text-sm text-slate-500 mt-1">See the live LangGraph4j topology</div>
          </Link>
        </div>
      </section>

      <section className="bg-white rounded-lg p-6 shadow-sm border border-slate-200">
        <h2 className="text-xl font-semibold text-slate-800">Look up a session</h2>
        <div className="mt-3 flex gap-2">
          <input
            type="text" placeholder="rev-abc12345"
            value={sessionId}
            onChange={e => setSessionId(e.target.value)}
            className="flex-1 px-3 py-2 border border-slate-300 rounded font-mono text-sm"
          />
          <Link
            to={sessionId ? `/sessions/${sessionId}` : '#'}
            className={`px-4 py-2 rounded text-white font-medium ${sessionId ? 'bg-council-500 hover:bg-council-700' : 'bg-slate-300 cursor-not-allowed'}`}>
            Open
          </Link>
        </div>
      </section>
    </div>
  );
}
