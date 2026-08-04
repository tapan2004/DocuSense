import { useState } from 'react';
import { api } from '../utils/api';

export default function Auth({ onLogin }) {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [department, setDepartment] = useState('Engineering');
  const [role, setRole] = useState('ROLE_USER');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);

    try {
      if (isRegister) {
        await api.register(username, password, department, role);
        setMessage('Registration successful! Please login.');
        setIsRegister(false);
        setPassword('');
      } else {
        const data = await api.login(username, password);
        const tokenParts = data.token.split('.');
        const claims = JSON.parse(atob(tokenParts[1]));
        
        onLogin({
          username: claims.sub,
          role: claims.role,
          department: claims.department
        }, data.token);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md p-8 rounded-2xl glass-panel glow-purple animate-fade-in relative z-10">
      <div className="text-center mb-8">
        <h2 className="text-3xl font-extrabold tracking-tight text-white mb-2 font-sans">DocuSense</h2>
        <p className="text-sm text-slate-400">Enterprise Secured RAG</p>
      </div>

      <div className="flex border-b border-slate-800 mb-6">
        <button 
          onClick={() => { setIsRegister(false); setError(''); }}
          className={`flex-1 pb-3 text-sm font-semibold border-b-2 cursor-pointer ${!isRegister ? 'border-purple-500 text-white' : 'border-transparent text-slate-400'}`}
        >
          Sign In
        </button>
        <button 
          onClick={() => { setIsRegister(true); setError(''); }}
          className={`flex-1 pb-3 text-sm font-semibold border-b-2 cursor-pointer ${isRegister ? 'border-purple-500 text-white' : 'border-transparent text-slate-400'}`}
        >
          Register
        </button>
      </div>

      {error && <div className="p-3 mb-4 rounded bg-red-950/40 border border-red-500/50 text-red-400 text-xs">{error}</div>}
      {message && <div className="p-3 mb-4 rounded bg-green-950/40 border border-green-500/50 text-green-400 text-xs">{message}</div>}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Username</label>
          <input 
            type="text" required value={username} onChange={(e) => setUsername(e.target.value)}
            className="w-full p-3 rounded-lg text-sm bg-slate-950/40 border border-slate-800 focus:outline-none focus:border-purple-500 text-white tracking-wide"
            placeholder="john_doe"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Password</label>
          <input 
            type="password" required value={password} onChange={(e) => setPassword(e.target.value)}
            className="w-full p-3 rounded-lg text-sm bg-slate-950/40 border border-slate-800 focus:outline-none focus:border-purple-500 text-white tracking-wide"
            placeholder="••••••••"
          />
        </div>

        {isRegister && (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Department</label>
              <select 
                value={department} onChange={(e) => setDepartment(e.target.value)}
                className="w-full p-3 rounded-lg text-sm bg-slate-900 border border-slate-800 focus:outline-none focus:border-purple-500 text-white cursor-pointer"
              >
                <option value="Engineering">Engineering</option>
                <option value="HR">HR</option>
                <option value="Finance">Finance</option>
                <option value="General">General</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Clearance Role</label>
              <select 
                value={role} onChange={(e) => setRole(e.target.value)}
                className="w-full p-3 rounded-lg text-sm bg-slate-900 border border-slate-800 focus:outline-none focus:border-purple-500 text-white cursor-pointer"
              >
                <option value="ROLE_USER">User</option>
                <option value="ROLE_MANAGER">Manager</option>
                <option value="ROLE_ADMIN">Admin</option>
              </select>
            </div>
          </div>
        )}

        <button 
          type="submit" disabled={loading}
          className="w-full p-3.5 rounded-lg text-sm font-semibold text-white bg-purple-600 hover:bg-purple-700 transition duration-300 disabled:opacity-50 mt-6 cursor-pointer"
        >
          {loading ? 'Processing...' : isRegister ? 'Create Account' : 'Sign In'}
        </button>
      </form>
    </div>
  );
}
