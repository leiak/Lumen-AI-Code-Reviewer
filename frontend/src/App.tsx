import { NavLink, Route, Routes } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import Validate from './pages/Validate';
import SessionDetail from './pages/SessionDetail';
import GraphView from './pages/GraphView';

export default function App() {
  const link = 'px-3 py-2 rounded-md text-sm font-medium hover:bg-council-50';
  const active = ({ isActive }: { isActive: boolean }) =>
    isActive ? `${link} bg-council-500 text-white` : `${link} text-slate-700`;

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-white border-b border-slate-200">
        <div className="max-w-6xl mx-auto px-6 py-3 flex items-center gap-4">
          <h1 className="text-lg font-bold text-council-700">AI Code Review Council</h1>
          <nav className="flex gap-1 ml-auto">
            <NavLink to="/" className={active} end>Dashboard</NavLink>
            <NavLink to="/validate" className={active}>Validate</NavLink>
            <NavLink to="/graph" className={active}>Graph</NavLink>
          </nav>
        </div>
      </header>
      <main className="flex-1 max-w-6xl mx-auto w-full px-6 py-8">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/validate" element={<Validate />} />
          <Route path="/graph" element={<GraphView />} />
          <Route path="/sessions/:id" element={<SessionDetail />} />
        </Routes>
      </main>
      <footer className="border-t border-slate-200 bg-white text-center text-xs text-slate-500 py-3">
        review-council v1.0 · Spring AI 1.1 + LangGraph4j 1.6
      </footer>
    </div>
  );
}
