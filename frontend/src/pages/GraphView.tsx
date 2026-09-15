import { useEffect, useRef, useState } from 'react';
import { getGraph } from '../api';
import mermaid from 'mermaid';

mermaid.initialize({ startOnLoad: false, theme: 'default' });

export default function GraphView() {
  const [mermaidSrc, setMermaidSrc] = useState<string>('');
  const [error, setError] = useState<string>('');
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getGraph('mermaid')
      .then(t => setMermaidSrc(t))
      .catch(e => setError(String(e)));
  }, []);

  useEffect(() => {
    if (!mermaidSrc || !ref.current) return;
    ref.current.innerHTML = mermaidSrc;
    mermaid.run({ nodes: ref.current.querySelectorAll('.mermaid') })
      .catch(e => setError(String(e)));
  }, [mermaidSrc]);

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold text-slate-800">状态图（LangGraph4j）</h2>
      <p className="text-sm text-slate-500">
        后端编译出的真实拓扑。dispatch 节点并行扇出到所有 reviewer；条件边根据 signal 路由。
      </p>
      {error && <div className="p-3 rounded border border-critical bg-red-50 text-sm text-red-800">{error}</div>}
      <div className="bg-white rounded-lg p-6 shadow-sm border border-slate-200 overflow-auto">
        <div ref={ref} className="mermaid" />
      </div>
    </div>
  );
}
