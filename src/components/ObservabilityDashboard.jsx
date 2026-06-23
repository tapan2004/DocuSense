import { useEffect, useState } from 'react';

export default function ObservabilityDashboard() {
  const [stats, setStats] = useState({
    totalQueries: 0,
    totalCost: 0,
    totalTokens: 0,
    averageLatency: 0,
    cacheHitRate: 0,
    positiveFeedbackRate: 0
  });
  const [logs, setLogs] = useState([]);
  const [feedbacks, setFeedbacks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError('');
      const token = sessionStorage.getItem('docusense_token');

      try {
        const [statsRes, logsRes, feedbacksRes] = await Promise.all([
          fetch('http://localhost:8080/api/observability/stats', { headers: { 'Authorization': `Bearer ${token}` } }),
          fetch('http://localhost:8080/api/observability/logs', { headers: { 'Authorization': `Bearer ${token}` } }),
          fetch('http://localhost:8080/api/observability/feedbacks', { headers: { 'Authorization': `Bearer ${token}` } })
        ]);

        if (!statsRes.ok || !logsRes.ok || !feedbacksRes.ok) {
          throw new Error('Failed to retrieve dashboard telemetry. Verification of permissions required.');
        }

        const [statsData, logsData, feedbacksData] = await Promise.all([
          statsRes.json(),
          logsRes.json(),
          feedbacksRes.json()
        ]);

        setStats(statsData);
        setLogs(logsData);
        setFeedbacks(feedbacksData);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-[50vh] text-slate-400">
        <svg className="animate-spin h-8 w-8 text-purple-500 mb-4" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
        <span className="text-xs uppercase tracking-wider font-semibold">Loading system telemetry...</span>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-fade-in max-w-6xl mx-auto">
      <div>
        <h3 className="text-xl md:text-2xl font-bold tracking-tight text-white mb-1">RAG Observability & Telemetry</h3>
        <p className="text-slate-400 text-xs md:text-sm">Real-time metrics tracking cost, latency, cache effectiveness, and RLHF user ratings.</p>
      </div>

      {error && <div className="p-4 rounded bg-red-950/40 border border-red-500/50 text-red-400 text-sm">{error}</div>}

      {/* Telemetry Cards Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
        <div className="p-5 rounded-2xl glass-card border border-purple-500/10">
          <p className="text-[10px] md:text-xs font-semibold text-slate-400 uppercase tracking-wider">Total LLM Cost</p>
          <p className="text-xl md:text-3xl font-extrabold text-white mt-1.5">${stats.totalCost.toFixed(5)}</p>
          <p className="text-[10px] text-purple-400 font-bold uppercase tracking-wider mt-1">Based on token usage</p>
        </div>

        <div className="p-5 rounded-2xl glass-card border border-cyan-500/10">
          <p className="text-[10px] md:text-xs font-semibold text-slate-400 uppercase tracking-wider">Cache Hit Rate</p>
          <p className="text-xl md:text-3xl font-extrabold text-white mt-1.5">{stats.cacheHitRate.toFixed(1)}%</p>
          <p className="text-[10px] text-cyan-400 font-bold uppercase tracking-wider mt-1">Semantic caching hits</p>
        </div>

        <div className="p-5 rounded-2xl glass-card border border-emerald-500/10">
          <p className="text-[10px] md:text-xs font-semibold text-slate-400 uppercase tracking-wider">Helpful Rate (RLHF)</p>
          <p className="text-xl md:text-3xl font-extrabold text-white mt-1.5">{stats.positiveFeedbackRate.toFixed(1)}%</p>
          <p className="text-[10px] text-emerald-400 font-bold uppercase tracking-wider mt-1">Thumbs-up ratio</p>
        </div>

        <div className="p-5 rounded-2xl glass-card border border-amber-500/10">
          <p className="text-[10px] md:text-xs font-semibold text-slate-400 uppercase tracking-wider">Avg Response Time</p>
          <p className="text-xl md:text-3xl font-extrabold text-white mt-1.5">{stats.averageLatency.toFixed(0)}ms</p>
          <p className="text-[10px] text-amber-400 font-bold uppercase tracking-wider mt-1">End-to-end RAG speed</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Search Queries Logs */}
        <div className="p-5 rounded-2xl glass-card border border-slate-900 flex flex-col h-[400px]">
          <h4 className="text-xs md:text-sm font-bold uppercase tracking-wider text-slate-300 mb-4 flex items-center shrink-0">
            <span className="w-1.5 h-1.5 rounded-full bg-purple-500 mr-2 animate-pulse" />
            Query Telemetry Log
          </h4>
          <div className="overflow-y-auto flex-1 scrollbar-none pr-1">
            {logs.length === 0 ? (
              <div className="text-slate-500 text-xs italic text-center py-10">No recent queries tracked.</div>
            ) : (
              <div className="space-y-3">
                {logs.map((log) => (
                  <div key={log.id} className="p-3.5 rounded-xl bg-slate-950/40 border border-slate-900 text-xs flex flex-col gap-2">
                    <div className="flex justify-between items-start">
                      <span className="font-semibold text-slate-200 truncate max-w-[70%]">{log.query}</span>
                      <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold ${
                        log.cacheStatus === 'HIT' 
                          ? 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/25' 
                          : 'bg-purple-500/15 text-purple-400 border border-purple-500/25'
                      }`}>
                        Cache {log.cacheStatus}
                      </span>
                    </div>
                    <div className="flex justify-between items-center text-[10px] text-slate-500">
                      <span>User: <strong className="text-slate-400">{log.username}</strong></span>
                      <div className="flex items-center space-x-3">
                        <span>Latency: <strong className="text-slate-400">{log.latencyMs}ms</strong></span>
                        <span>Cost: <strong className="text-slate-400">${log.estimatedCost.toFixed(5)}</strong></span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* User Feedback (RLHF) Audits */}
        <div className="p-5 rounded-2xl glass-card border border-slate-900 flex flex-col h-[400px]">
          <h4 className="text-xs md:text-sm font-bold uppercase tracking-wider text-slate-300 mb-4 flex items-center shrink-0">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-2 animate-pulse" />
            RLHF Satisfaction Audit
          </h4>
          <div className="overflow-y-auto flex-1 scrollbar-none pr-1">
            {feedbacks.length === 0 ? (
              <div className="text-slate-500 text-xs italic text-center py-10">No feedback submitted yet.</div>
            ) : (
              <div className="space-y-3">
                {feedbacks.map((fb) => (
                  <div key={fb.id} className="p-3.5 rounded-xl bg-slate-950/40 border border-slate-900 text-xs flex flex-col gap-1.5">
                    <div className="flex justify-between items-start">
                      <span className="font-semibold text-slate-300 truncate max-w-[70%]">{fb.query}</span>
                      <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold flex items-center ${
                        fb.rating === 1 
                          ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/25' 
                          : 'bg-red-500/15 text-red-400 border border-red-500/25'
                      }`}>
                        {fb.rating === 1 ? '👍 Helpful' : '👎 Not Helpful'}
                      </span>
                    </div>
                    <p className="text-slate-400 text-[11px] line-clamp-2 leading-relaxed bg-slate-950/20 p-2 rounded-lg border border-slate-900/50 mt-1 italic">
                      "{fb.answer}"
                    </p>
                    <span className="text-[9px] text-slate-500 text-right mt-1">
                      Submitted by: <strong className="text-slate-400">{fb.username}</strong>
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
