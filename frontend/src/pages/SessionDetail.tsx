import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getCost, getSession, type CostResponse } from '../api';

const SEVERITY_CLASS: Record<string, string> = {
  critical: 'bg-red-100 text-red-800 border-red-300',
  major: 'bg-amber-100 text-amber-800 border-amber-300',
  minor: 'bg-emerald-100 text-emerald-800 border-emerald-300',
};

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const [session, setSession] = useState<unknown>(null);
  const [cost, setCost] = useState<CostResponse | null>(null);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    if (!id) return;
    getSession(id).then(setSession).catch(e => setError(String(e.message || e)));
    getCost(id).then(setCost).catch(() => {});
  }, [id]);

  if (error) {
    return (
      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-slate-800">Session {id}</h2>
        <div className="p-3 rounded border border-critical bg-red-50 text-sm text-red-800">{error}</div>
        <Link to="/" className="text-council-500 underline">← Back</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-baseline gap-3">
        <h2 className="text-xl font-semibold text-slate-800 font-mono">{id}</h2>
        <Link to="/" className="text-council-500 text-sm">← Dashboard</Link>
      </div>

      {cost && (
        <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200 grid grid-cols-2 gap-4">
          <div>
            <div className="text-xs text-slate-500 uppercase">Cost</div>
            <div className="text-2xl font-bold text-slate-800">${cost.costUsd.toFixed(4)}</div>
          </div>
          <div>
            <div className="text-xs text-slate-500 uppercase">Tokens</div>
            <div className="text-2xl font-bold text-slate-800">{cost.tokens.toLocaleString()}</div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg p-4 shadow-sm border border-slate-200">
        <h3 className="font-semibold text-slate-800 mb-2">Session details</h3>
        <pre className="text-xs bg-slate-50 p-3 rounded overflow-auto">
          {JSON.stringify(session, null, 2)}
        </pre>
      </div>
    </div>
  );
}
