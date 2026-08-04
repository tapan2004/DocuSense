import { useState } from 'react';
import SearchRag from './SearchRag';
import UploadDoc from './UploadDoc';
import ManageDocs from './ManageDocs';
import ObservabilityDashboard from './ObservabilityDashboard';

export default function Dashboard({ user, onLogout }) {
  const [activeTab, setActiveTab] = useState('search');
  const isManagerOrAdmin = user.role === 'ROLE_MANAGER' || user.role === 'ROLE_ADMIN';

  return (
    <div className="w-full max-w-6xl h-auto min-h-[90vh] md:h-[85vh] rounded-2xl glass-panel glow-cyan flex flex-col md:flex-row overflow-hidden animate-fade-in relative z-10">
      
      {/* Sidebar Panel - Top Bar on Mobile, Left Column on Desktop */}
      <div className="w-full md:w-64 bg-slate-950/50 border-b md:border-b-0 md:border-r border-slate-900/60 p-4 md:p-6 flex flex-col sm:flex-row md:flex-col justify-between items-stretch gap-4 sm:gap-2 md:gap-0">
        
        {/* Top Logo & Navigation Container */}
        <div className="flex flex-col sm:flex-row md:flex-col gap-4 sm:gap-6 md:gap-8 flex-1">
          {/* Logo */}
          <div className="flex items-center space-x-2.5 shrink-0">
            <div className="w-8 h-8 rounded bg-gradient-to-tr from-purple-500 to-cyan-500 flex items-center justify-center font-bold text-white text-sm">D</div>
            <span className="text-lg md:text-xl font-bold tracking-tight text-white">DocuSense</span>
          </div>

          {/* Navigation Links (Scrollable horizontally on tiny screens) */}
          <nav className="flex flex-row md:flex-col space-x-1.5 md:space-x-0 md:space-y-1.5 overflow-x-auto md:overflow-visible pb-2 sm:pb-0 scrollbar-none">
            <button 
              onClick={() => setActiveTab('search')}
              className={`flex items-center space-x-2 px-3 py-2 md:px-4 md:py-3 rounded-lg text-xs md:text-sm font-medium transition cursor-pointer whitespace-nowrap shrink-0 ${activeTab === 'search' ? 'bg-purple-600/20 text-purple-400 border border-purple-500/20' : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'}`}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
              <span>Search</span>
            </button>

            {isManagerOrAdmin && (
              <>
                <button 
                  onClick={() => setActiveTab('upload')}
                  className={`flex items-center space-x-2 px-3 py-2 md:px-4 md:py-3 rounded-lg text-xs md:text-sm font-medium transition cursor-pointer whitespace-nowrap shrink-0 ${activeTab === 'upload' ? 'bg-purple-600/20 text-purple-400 border border-purple-500/20' : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'}`}
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
                  <span>Upload</span>
                </button>

                <button 
                  onClick={() => setActiveTab('manage')}
                  className={`flex items-center space-x-2 px-3 py-2 md:px-4 md:py-3 rounded-lg text-xs md:text-sm font-medium transition cursor-pointer whitespace-nowrap shrink-0 ${activeTab === 'manage' ? 'bg-purple-600/20 text-purple-400 border border-purple-500/20' : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'}`}
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                  <span>Files</span>
                </button>

                <button 
                  onClick={() => setActiveTab('observability')}
                  className={`flex items-center space-x-2 px-3 py-2 md:px-4 md:py-3 rounded-lg text-xs md:text-sm font-medium transition cursor-pointer whitespace-nowrap shrink-0 ${activeTab === 'observability' ? 'bg-purple-600/20 text-purple-400 border border-purple-500/20' : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'}`}
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path>
                  </svg>
                  <span>Observability</span>
                </button>
              </>
            )}
          </nav>
        </div>

        {/* User Card Profile & Signout */}
        <div className="border-t sm:border-t-0 md:border-t border-slate-900/60 pt-4 sm:pt-0 md:pt-4 flex flex-row sm:flex-col justify-between items-center sm:items-stretch sm:justify-center md:justify-between shrink-0 gap-4 sm:gap-2">
          <div className="text-left">
            <p className="text-xs md:text-sm font-semibold text-white tracking-wide truncate max-w-[120px] sm:max-w-none">{user.username}</p>
            <p className="text-[10px] text-purple-400 font-bold uppercase tracking-wider mt-0.5">{user.role.replace('ROLE_', '')} • {user.department}</p>
          </div>
          <button 
            onClick={onLogout}
            className="py-1.5 px-3 md:py-2.5 md:px-0 rounded-lg border border-red-500/30 text-red-400 text-[11px] md:text-xs font-semibold hover:bg-red-500/10 hover:border-red-500/60 transition cursor-pointer whitespace-nowrap"
          >
            Sign Out
          </button>
        </div>
      </div>

      {/* Main Panel Content Area */}
      <div className="flex-1 bg-slate-950/20 p-4 sm:p-8 overflow-y-auto">
        {activeTab === 'search' && <SearchRag />}
        {activeTab === 'upload' && isManagerOrAdmin && <UploadDoc />}
        {activeTab === 'manage' && isManagerOrAdmin && <ManageDocs />}
        {activeTab === 'observability' && isManagerOrAdmin && <ObservabilityDashboard />}
      </div>
    </div>
  );
}
