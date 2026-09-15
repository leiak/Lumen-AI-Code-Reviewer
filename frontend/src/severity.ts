// 共享 severity 工具（StartReview 和 SessionDetail 都用）

export const SEVERITY_CLASS: Record<string, string> = {
  critical: 'bg-red-100 text-red-800 border-red-300',
  major: 'bg-amber-100 text-amber-800 border-amber-300',
  minor: 'bg-emerald-100 text-emerald-800 border-emerald-300',
};

export const SEVERITY_CN: Record<string, string> = {
  critical: '严重',
  major: '重要',
  minor: '次要',
};

export const SEVERITY_ORDER: Record<string, number> = { critical: 0, major: 1, minor: 2 };

export function severityClass(s: string): string {
  return SEVERITY_CLASS[s] ?? 'bg-slate-100 text-slate-800 border-slate-300';
}

export function severityCn(s: string): string {
  return SEVERITY_CN[s] ?? s;
}

export function severityRank(s: string): number {
  return SEVERITY_ORDER[s] ?? 9;
}
