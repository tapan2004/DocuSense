import { useState } from 'react';
import { api } from '../utils/api';

export default function UploadDoc() {
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [departmentOwner, setDepartmentOwner] = useState('Engineering');
  const [requiredRole, setRequiredRole] = useState('ROLE_USER');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isDragActive, setIsDragActive] = useState(false);

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setIsDragActive(true);
    } else if (e.type === "dragleave") {
      setIsDragActive(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setFile(e.dataTransfer.files[0]);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) return;

    setLoading(true);
    setError('');
    setSuccess('');

    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('departmentOwner', departmentOwner);
    formData.append('requiredRole', requiredRole);

    try {
      const data = await api.uploadDocument(formData);
      setSuccess(`Document "${data.title}" successfully ingested and vectorized!`);
      setTitle('');
      setFile(null);
      const fileInput = document.getElementById('file-input');
      if (fileInput) fileInput.value = '';
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-xl mx-auto animate-fade-in px-2 sm:px-0">
      <h3 className="text-xl md:text-2xl font-bold tracking-tight text-white mb-1">Ingest Secure Document</h3>
      <p className="text-slate-400 text-xs md:text-sm mb-4 sm:mb-6">Parsing, indexing, chunking, and embedding generation occur asynchronously.</p>

      {error && <div className="p-4 mb-4 rounded bg-red-950/40 border border-red-500/50 text-red-400 text-xs md:text-sm">{error}</div>}
      {success && <div className="p-4 mb-4 rounded bg-green-950/40 border border-green-500/50 text-green-400 text-xs md:text-sm">{success}</div>}

      <form onSubmit={handleSubmit} className="space-y-4 sm:space-y-5 glass-card p-4 sm:p-6 rounded-2xl border border-slate-900">
        <div>
          <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Document Title</label>
          <input 
            type="text" required value={title} onChange={(e) => setTitle(e.target.value)}
            className="w-full p-3 rounded-lg text-xs md:text-sm bg-slate-950/40 border border-slate-800 focus:outline-none focus:border-purple-500 text-white"
            placeholder="e.g. Q3 Roadmap Guidelines"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Department Scope</label>
            <select 
              value={departmentOwner} onChange={(e) => setDepartmentOwner(e.target.value)}
              className="w-full p-3 rounded-lg text-xs md:text-sm bg-slate-900 border border-slate-800 focus:outline-none focus:border-purple-500 text-white cursor-pointer"
            >
              <option value="Engineering">Engineering</option>
              <option value="HR">HR</option>
              <option value="Finance">Finance</option>
              <option value="General">General (All)</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Required Access Clearance</label>
            <select 
              value={requiredRole} onChange={(e) => setRequiredRole(e.target.value)}
              className="w-full p-3 rounded-lg text-xs md:text-sm bg-slate-900 border border-slate-800 focus:outline-none focus:border-purple-500 text-white cursor-pointer"
            >
              <option value="ROLE_USER">User (Standard)</option>
              <option value="ROLE_MANAGER">Manager (Clearance)</option>
              <option value="ROLE_ADMIN">Admin (Strict)</option>
            </select>
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Document File (PDF / DOCX / TXT)</label>
          
          <div 
            onDragEnter={handleDrag}
            onDragOver={handleDrag}
            onDragLeave={handleDrag}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-xl p-6 sm:p-8 text-center transition cursor-pointer relative ${
              isDragActive ? 'border-purple-500 bg-purple-500/5' : 'border-slate-800 bg-slate-950/20'
            }`}
          >
            <input 
              id="file-input" type="file" required={!file} onChange={(e) => setFile(e.target.files[0])}
              accept=".pdf,.docx,.txt"
              className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
            />
            <svg className="w-8 h-8 text-slate-400 mx-auto mb-3" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0l3 3m-3-3l-3 3M6.75 19.5a4.5 4.5 0 01-1.41-8.775 5.25 5.25 0 0110.233-2.33 3 3 0 013.758 3.848A3.752 3.752 0 0118 19.5H6.75z"></path></svg>
            <p className="text-xs md:text-sm font-semibold text-slate-200">
              {file ? file.name : "Drag & drop your file here, or click to browse"}
            </p>
            <p className="text-[10px] md:text-xs text-slate-500 mt-1">Accepts PDF, DOCX, and TXT (Max 10MB)</p>
          </div>
        </div>

        <button 
          type="submit" disabled={loading || !file}
          className="w-full p-3 sm:p-3.5 rounded-lg text-xs md:text-sm font-semibold text-white bg-purple-600 hover:bg-purple-700 transition duration-300 disabled:opacity-50 mt-4 sm:mt-6 cursor-pointer flex items-center justify-center space-x-2"
        >
          {loading && (
            <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
          )}
          <span>{loading ? 'Processing Document Chunks...' : 'Vectorize Document'}</span>
        </button>
      </form>
    </div>
  );
}
