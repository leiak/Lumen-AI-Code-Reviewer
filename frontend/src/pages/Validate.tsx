import { useState } from 'react';
import { validateYaml, type ValidateResult } from '../api';

const SAMPLE = `council:
  name: team-java-backend
loop:
  max-rounds: 2
reviewers:
  - role: security
    model: claude-sonnet-5-20250929
    prompt-file: prompts/security.md
  - role: perf
    model: claude-sonnet-5-20250929
    prompt-file: prompts/perf.md
budget:
  max-tokens: 200000
  max-cost-usd: "1.00"`;

export default function Validate() {
  const [yaml, setYaml] = useState<string>(SAMPLE);
  const [result, setResult] = useState<ValidateResult | null>(null);
  const [loading, setLoading] = useState(false);

  async function run() {
    setLoading(true);
    try { setResult(await validateYaml(yaml)); }
    finally { setLoading(false); }
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold text-slate-800">Validate council.yaml</h2>
      <textarea
        className="w-full h-80 px-3 py-2 border border-slate-300 rounded font-mono text-sm"
        value={yaml}
        onChange={e => setYaml(e.target.value)}
      />
      <div className="flex gap-2">
        <button
          onClick={run} disabled={loading}
          className="px-4 py-2 bg-council-500 text-white rounded font-medium hover:bg-council-700 disabled:bg-slate-300">
          {loading ? 'Validating...' : 'Validate'}
        </button>
        <button
          onClick={() => setYaml(SAMPLE)}
          className="px-4 py-2 bg-slate-100 rounded text-slate-700 hover:bg-slate-200 border border-slate-300">
          ⚡ Load sample
        </button>
      </div>
      {result && (
        <div className={`p-4 rounded border ${result.valid ? 'border-minor bg-green-50' : 'border-critical bg-red-50'}`}>
          {result.valid ? (
            <div>
              <div className="font-semibold text-green-800">✓ Valid configuration</div>
              <dl className="mt-2 text-sm text-green-900 grid grid-cols-3 gap-1">
                <dt className="text-slate-600">Council:</dt><dd className="col-span-2 font-mono">{result.name}</dd>
                <dt className="text-slate-600">Reviewers:</dt><dd className="col-span-2">{result.reviewers}</dd>
                <dt className="text-slate-600">Max rounds:</dt><dd className="col-span-2">{result.maxRounds}</dd>
              </dl>
            </div>
          ) : (
            <div>
              <div className="font-semibold text-red-800">✗ Invalid</div>
              <pre className="mt-2 text-xs text-red-900 whitespace-pre-wrap font-mono">{result.error}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
