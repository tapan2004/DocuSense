import { useState, useEffect } from 'react';
import { api } from '../utils/api';

export default function ManageDocs() {
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchDocs = async () => {
    setLoading(true);
    try {
      const data = await api.listDocuments();
      setDocs(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this document? This will purge its text chunks and vector embeddings.')) return;

    try {
      await api.deleteDocument(id);
      setDocs(docs.filter(doc => doc.id !== id));
    } catch (err) {
      alert(err.message);
    }
  };

  useEffect(() => {
    fetchDocs();
  }, []);

  return (
    <div className="max-w-4xl mx-auto animate-fade-in">
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4 mb-6">
        <div>
          <h3 className="text-xl md:text-2xl font-bold tracking-tight text-white mb-1">Corporate File Catalog</h3>
          <p className="text-slate-400 text-xs md:text-sm">Managing records will synchronise relational and vector data structures.</p>
        </div>
        <button 
          onClick={fetchDocs} disabled={loading}
          className="self-start sm:self-center px-4 py-2 text-xs font-semibold text-slate-300 border border-slate-800 rounded-lg hover:bg-slate-900 transition cursor-pointer"
        >
          {loading ? 'Refreshing...' : 'Refresh Catalog'}
        </button>
      </div>

      {error && <div className="p-4 mb-4 rounded bg-red-950/40 border border-red-500/50 text-red-400 text-sm">{error}</div>}

      <div className="glass-card rounded-2xl overflow-hidden border border-slate-900">
        <div className="overflow-x-auto w-full">
          <table className="w-full text-left border-collapse min-w-[600px]">
            <thead>
              <tr className="bg-slate-950/50 border-b border-slate-900 text-slate-400 text-[10px] md:text-xs font-bold uppercase tracking-wider">
                <th className="p-4">Document Title</th>
                <th className="p-4">Department Owner</th>
                <th className="p-4">Required Role</th>
                <th className="p-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-900 text-xs md:text-sm font-light text-slate-200">
              {docs.length === 0 ? (
                <tr>
                  <td colSpan="4" className="p-12 text-center text-slate-400">
                    <svg className="w-12 h-12 text-slate-700 mx-auto mb-3" fill="none" stroke="currentColor" strokeWidth="1" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m8.25 3v6.75m0 0l-3-3m3 3l3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z"></path></svg>
                    <p className="font-semibold text-slate-300">No Authorized Documents Found</p>
                    <p className="text-xs text-slate-500 mt-1">Upload files scoped to your department or role to populate the catalog.</p>
                  </td>
                </tr>
              ) : (
                docs.map((doc) => (
                  <tr key={doc.id} className="hover:bg-slate-900/30 transition">
                    <td className="p-4 font-semibold text-white tracking-wide">{doc.title}</td>
                    <td className="p-4 text-xs font-mono">{doc.departmentOwner}</td>
                    <td className="p-4"><span className="px-2.5 py-1 text-[9px] md:text-[10px] font-bold bg-purple-600/10 text-purple-400 border border-purple-500/20 rounded-full">{doc.requiredRole.replace('ROLE_', '')}</span></td>
                    <td className="p-4 text-right">
                      <button 
                        onClick={() => handleDelete(doc.id)}
                        className="px-3.5 py-1.5 rounded-lg border border-red-500/20 text-red-400 text-[10px] md:text-xs font-semibold hover:bg-red-500/15 hover:border-red-500/60 transition cursor-pointer"
                      >
                        Delete
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        </div>
      </div>
    </div>
  );
}
