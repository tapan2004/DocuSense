import { useEffect, useState } from 'react';
import { api } from '../utils/api';

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
  const [logsLoading, setLogsLoading] = useState(false);
  const [feedbacksLoading, setFeedbacksLoading] = useState(false);
  const [error, setError] = useState('');

  // Pagination and Filtering States
  const [logsPage, setLogsPage] = useState(0);
  const [logsTotalPages, setLogsTotalPages] = useState(0);
  const [feedbacksPage, setFeedbacksPage] = useState(0);
  const [feedbacksTotalPages, setFeedbacksTotalPages] = useState(0);
  const [filterUsername, setFilterUsername] = useState('');

  const fetchStats = async () => {
    try {
      const statsData = await api.getObservabilityStats();
      setStats(statsData);
    } catch (err) {
      console.error("Failed to load statistics: ", err);
    }
  };

  const fetchLogs = async (page = 0, username = '') => {
    setLogsLoading(true);
    try {
      const logsData = await api.getObservabilityLogs(page, 5, username);
      setLogs(logsData.content || []);
      setLogsTotalPages(logsData.totalPages || 0);
    } catch (err) {
      setError(err.message);
    } finally {
      setLogsLoading(false);
    }
  };

  const fetchFeedbacks = async (page = 0) => {
    setFeedbacksLoading(true);
    try {
      const feedbacksData = await api.getObservabilityFeedbacks(page, 5);
      setFeedbacks(feedbacksData.content || []);
      setFeedbacksTotalPages(feedbacksData.totalPages || 0);
    } catch (err) {
      setError(err.message);
    } finally {
      setFeedbacksLoading(false);
    }
  };

  // Initial Load
  useEffect(() => {
    const initTelemetry = async () => {
      setLoading(true);
      setError('');
      await Promise.all([
        fetchStats(),
        fetchLogs(0, ''),
        fetchFeedbacks(0)
      ]);
      setLoading(false);
    };
    initTelemetry();
  }, []);

  const handleSearchLogs = (e) => {
    e.preventDefault();
    setLogsPage(0);
    fetchLogs(0, filterUsername);
  };

  const handleLogsPageChange = (newPage) => {
    if (newPage < 0 || newPage >= logsTotalPages) return;
    setLogsPage(newPage);
    fetchLogs(newPage, filterUsername);
  };

  const handleFeedbacksPageChange = (newPage) => {
    if (newPage < 0 || newPage >= feedbacksTotalPages) return;
    setFeedbacksPage(newPage);
    fetchFeedbacks(newPage);
  };

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
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h3 className="text-xl md:text-2xl font-bold tracking-tight text-white mb-1">RAG Observability & Telemetry</h3>
          <p className="text-slate-400 text-xs md:text-sm">Real-time metrics tracking cost, latency, cache effectiveness, and RLHF user ratings.</p>
        </div>
        <button 
          onClick={async () => {
            setLoading(true);
            await Promise.all([fetchStats(), fetchLogs(logsPage, filterUsername), fetchFeedbacks(feedbacksPage)]);
            setLoading(false);
          }}
          className="px-3.5 py-2 border border-slate-800 rounded-lg text-slate-400 hover:text-white hover:bg-slate-900 transition duration-200 text-xs font-semibold cursor-pointer"
        >
          Refresh Data
        </button>
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
        <div className="p-5 rounded-2xl glass-card border border-slate-900 flex flex-col h-[460px]">
          <div className="flex justify-between items-center mb-3 shrink-0">
            <h4 className="text-xs md:text-sm font-bold uppercase tracking-wider text-slate-300 flex items-center">
              <span className="w-1.5 h-1.5 rounded-full bg-purple-500 mr-2 animate-pulse" />
              Query Telemetry Log
            </h4>
          </div>

          {/* Search filter form */}
          <form onSubmit={handleSearchLogs} className="flex gap-2 mb-3.5 shrink-0">
            <input 
              type="text" 
              value={filterUsername} 
              onChange={(e) => setFilterUsername(e.target.value)}
              placeholder="Filter by username..."
              className="flex-1 p-2.5 rounded-xl text-xs bg-slate-950/40 border border-slate-800 focus:outline-none focus:border-purple-500 text-white placeholder-slate-500"
            />
            <button 
              type="submit"
              className="px-4 py-2.5 rounded-xl text-[10px] font-bold text-white bg-purple-600 hover:bg-purple-700 transition cursor-pointer"
            >
              Search
            </button>
          </form>

          <div className="overflow-y-auto flex-1 scrollbar-none pr-1 relative">
            {logsLoading ? (
              <div className="absolute inset-0 bg-slate-950/40 backdrop-blur-xs flex items-center justify-center">
                <span className="text-[10px] text-slate-400 animate-pulse font-bold tracking-wider uppercase">Loading Query Logs...</span>
              </div>
            ) : null}

            {logs.length === 0 ? (
              <div className="text-slate-500 text-xs italic text-center py-10">No recent queries found.</div>
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

          {/* Pagination Controls */}
          {logsTotalPages > 1 && (
            <div className="flex justify-between items-center mt-4 pt-3 border-t border-slate-900/60 shrink-0 text-[10px] font-bold text-slate-500">
              <button 
                disabled={logsPage === 0 || logsLoading} 
                onClick={() => handleLogsPageChange(logsPage - 1)}
                className="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-950/20 hover:bg-slate-900 hover:text-white transition disabled:opacity-30 cursor-pointer"
              >
                Previous
              </button>
              <span>Page {logsPage + 1} of {logsTotalPages}</span>
              <button 
                disabled={logsPage >= logsTotalPages - 1 || logsLoading} 
                onClick={() => handleLogsPageChange(logsPage + 1)}
                className="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-950/20 hover:bg-slate-900 hover:text-white transition disabled:opacity-30 cursor-pointer"
              >
                Next
              </button>
            </div>
          )}
        </div>

        {/* User Feedback (RLHF) Audits */}
        <div className="p-5 rounded-2xl glass-card border border-slate-900 flex flex-col h-[460px]">
          <h4 className="text-xs md:text-sm font-bold uppercase tracking-wider text-slate-300 mb-4 flex items-center shrink-0">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-2 animate-pulse" />
            RLHF Satisfaction Audit
          </h4>
          <div className="overflow-y-auto flex-1 scrollbar-none pr-1 relative">
            {feedbacksLoading ? (
              <div className="absolute inset-0 bg-slate-950/40 backdrop-blur-xs flex items-center justify-center">
                <span className="text-[10px] text-slate-400 animate-pulse font-bold tracking-wider uppercase">Loading Feedbacks...</span>
              </div>
            ) : null}

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

          {/* Pagination Controls */}
          {feedbacksTotalPages > 1 && (
            <div className="flex justify-between items-center mt-4 pt-3 border-t border-slate-900/60 shrink-0 text-[10px] font-bold text-slate-500">
              <button 
                disabled={feedbacksPage === 0 || feedbacksLoading} 
                onClick={() => handleFeedbacksPageChange(feedbacksPage - 1)}
                className="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-950/20 hover:bg-slate-900 hover:text-white transition disabled:opacity-30 cursor-pointer"
              >
                Previous
              </button>
              <span>Page {feedbacksPage + 1} of {feedbacksTotalPages}</span>
              <button 
                disabled={feedbacksPage >= feedbacksTotalPages - 1 || feedbacksLoading} 
                onClick={() => handleFeedbacksPageChange(feedbacksPage + 1)}
                className="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-950/20 hover:bg-slate-900 hover:text-white transition disabled:opacity-30 cursor-pointer"
              >
                Next
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
