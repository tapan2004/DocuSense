import { useState, useRef, useEffect } from 'react';
import { api } from '../utils/api';

export default function SearchRag() {
  const [query, setQuery] = useState('');
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      role: 'assistant',
      content: "Hello! I am DocuSense, your secured AI assistant. You can ask me questions about Engineering, Architecture, or General guidelines. All retrieved context is dynamically restricted to your authorization scope.",
      sources: [],
      feedbackSubmitted: false,
      feedbackValue: null
    }
  ]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [expandedSourceIndex, setExpandedSourceIndex] = useState(null);

  const typingIntervalRef = useRef(null);
  const messagesEndRef = useRef(null);

  // Clean up typing interval on unmount
  useEffect(() => {
    return () => {
      if (typingIntervalRef.current) {
        clearInterval(typingIntervalRef.current);
      }
    };
  }, []);

  // Scroll to bottom when messages list size changes or loading starts
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages.length, loading]);

  const typeWriter = (text, messageId, finalSources, isGrounded) => {
    if (typingIntervalRef.current) {
      clearInterval(typingIntervalRef.current);
    }

    let index = 0;
    
    // Set initial character immediately to avoid empty bubble flash
    setMessages((prev) =>
      prev.map((msg) =>
        msg.id === messageId
          ? { ...msg, content: text.charAt(0), grounded: isGrounded }
          : msg
      )
    );

    typingIntervalRef.current = setInterval(() => {
      index++;
      if (index >= text.length) {
        clearInterval(typingIntervalRef.current);
        typingIntervalRef.current = null;
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === messageId
              ? { ...msg, content: text, sources: finalSources, grounded: isGrounded }
              : msg
          )
        );
      } else {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === messageId
              ? { ...msg, content: text.substring(0, index + 1) }
              : msg
          )
        );
      }
    }, 6);
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!query.trim() || loading) return;

    const currentQuery = query;
    setQuery('');

    // Cancel any active typewriter animation and force completion
    if (typingIntervalRef.current) {
      clearInterval(typingIntervalRef.current);
      typingIntervalRef.current = null;
    }

    setError('');

    // Create unique IDs for new messages
    const userMsgId = `user-${Date.now()}`;
    const assistantMsgId = `assistant-${Date.now()}`;

    const newUserMsg = {
      id: userMsgId,
      role: 'user',
      content: currentQuery
    };

    const newAssistantMsg = {
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      sources: [],
      loading: true,
      feedbackSubmitted: false,
      feedbackValue: null
    };

    // Calculate history based on current completed messages (excluding welcome)
    const historyPayload = messages
      .filter(msg => msg.id !== 'welcome' && !msg.loading)
      .map(msg => ({
        role: msg.role,
        content: msg.content
      }));

    setMessages((prev) => [...prev, newUserMsg, newAssistantMsg]);
    setLoading(true);

    try {
      const data = await api.chat(currentQuery, historyPayload);

      // Clear loading state on assistant message and set grounded
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantMsgId
            ? { ...msg, loading: false, grounded: data.grounded }
            : msg
        )
      );

      // Trigger the typewriter effect
      typeWriter(data.answer || '', assistantMsgId, data.sources || [], data.grounded);
    } catch (err) {
      setError(err.message);
      // Remove the incomplete assistant bubble on error
      setMessages((prev) => prev.filter((msg) => msg.id !== assistantMsgId));
    } finally {
      setLoading(false);
    }
  };

  const handleFeedback = async (messageId, value) => {
    const msgIndex = messages.findIndex((m) => m.id === messageId);
    if (msgIndex === -1 || messages[msgIndex].feedbackSubmitted) return;

    // Retrieve the matching query representing this assistant message's input
    const userMessage = messages[msgIndex - 1];
    if (!userMessage || userMessage.role !== 'user') return;

    const queryText = userMessage.content;
    const answerText = messages[msgIndex].content;

    setMessages((prev) =>
      prev.map((msg) =>
        msg.id === messageId
          ? { ...msg, feedbackSubmitted: true, feedbackValue: value }
          : msg
      )
    );

    try {
      await api.submitFeedback(queryText, answerText, value);
    } catch (err) {
      console.error('Failed to submit feedback', err);
    }
  };

  const handleClearChat = () => {
    if (typingIntervalRef.current) {
      clearInterval(typingIntervalRef.current);
      typingIntervalRef.current = null;
    }
    setMessages([
      {
        id: 'welcome',
        role: 'assistant',
        content: "Hello! I am DocuSense, your secured AI assistant. You can ask me questions about Engineering, Architecture, or General guidelines. All retrieved context is dynamically restricted to your authorization scope.",
        sources: [],
        feedbackSubmitted: false,
        feedbackValue: null
      }
    ]);
    setError('');
    setQuery('');
  };

  return (
    <div className="flex flex-col h-full max-w-4xl mx-auto">
      {/* Search Header and Clear Chat Toggle */}
      <div className="flex justify-between items-start mb-4 shrink-0 gap-4">
        <div>
          <h3 className="text-xl md:text-2xl font-bold tracking-tight text-white mb-1">Semantic Security Search</h3>
          <p className="text-slate-400 text-xs md:text-sm">Retrieval is dynamically restricted to your authorization scope.</p>
        </div>
        {messages.length > 1 && (
          <button 
            onClick={handleClearChat}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg border border-slate-800 bg-slate-950/20 text-[11px] font-medium text-slate-400 hover:text-white hover:bg-slate-900 transition duration-200 cursor-pointer shrink-0"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
            <span>Clear Chat</span>
          </button>
        )}
      </div>

      {error && (
        <div className="p-4 mb-4 rounded bg-red-950/40 border border-red-500/50 text-red-400 text-xs md:text-sm shrink-0 animate-fade-in">
          {error}
        </div>
      )}

      {/* Messages Feed Area */}
      <div className="flex-1 overflow-y-auto pr-1 space-y-4 my-4 min-h-[350px] max-h-[52vh] scrollbar-thin scrollbar-thumb-slate-800 scrollbar-track-transparent flex flex-col">
        {messages.map((msg) => {
          const isUser = msg.role === 'user';
          return (
            <div 
              key={msg.id}
              className={`flex flex-col mb-2 animate-fade-in ${
                isUser ? 'items-end self-end ml-auto max-w-[85%]' : 'items-start self-start mr-auto max-w-[85%]'
              }`}
            >
              {/* Message Bubble Container */}
              <div 
                className={`p-4 rounded-2xl shadow-md border leading-relaxed text-xs md:text-sm ${
                  isUser 
                    ? 'rounded-tr-none bg-purple-600/10 border-purple-500/30 text-slate-100' 
                    : 'rounded-tl-none glass-card border-slate-800 bg-slate-900/40 text-slate-200'
                }`}
              >
                {/* Header tag */}
                <h4 className="text-[10px] font-bold uppercase tracking-wider mb-2.5 flex items-center">
                  {isUser ? (
                    <>
                      <svg className="w-3.5 h-3.5 text-purple-400 mr-1.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
                      </svg>
                      You
                    </>
                  ) : (
                    <>
                      <svg className="w-3.5 h-3.5 mr-1.5 text-cyan-400 animate-pulse" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 21l-.813-5.096L3 15l5.096-.813L9 9l.813 5.096L15 15l-5.096.813zM3 5.625c0-.621.504-1.125 1.125-1.125h1.25c.621 0 1.125.504 1.125 1.125v1.25c0 .621-.504 1.125-1.125 1.125h-1.25A1.125 1.125 0 013 6.875v-1.25zm12.75 0c0-.621.504-1.125 1.125-1.125h1.25c.621 0 1.125.504 1.125 1.125v1.25c0 .621-.504 1.125-1.125 1.125h-1.25a1.125 1.125 0 01-1.125-1.125v-1.25z" />
                      </svg>
                      DocuSense
                    </>
                  )}
                </h4>

                {/* Content rendering */}
                {msg.loading ? (
                  <div className="flex space-x-1.5 py-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-cyan-500 mr-2 animate-pulse" />
                    <span className="text-[11px] text-cyan-400 font-bold uppercase tracking-wider animate-pulse">Retrieving Secured Context...</span>
                  </div>
                ) : (
                  <>
                    <p className="whitespace-pre-wrap font-light leading-relaxed">{msg.content}</p>
                    
                    {msg.grounded === false && (
                      <div className="mt-3 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-[11px] flex items-start space-x-2 animate-fade-in shrink-0">
                        <svg className="w-4 h-4 shrink-0 text-amber-400 mt-0.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                        </svg>
                        <span>
                          <strong>Guardrail Alert:</strong> This response was flagged by the safety evaluator as potentially containing ungrounded claims or hallucinations.
                        </span>
                      </div>
                    )}
                    
                    {/* Helpful Rating Action */}
                    {msg.id !== 'welcome' && (
                      <div className="mt-4 pt-3 border-t border-slate-900/60 flex items-center justify-between text-[10px] md:text-xs text-slate-500">
                        <span>Was this helpful?</span>
                        <div className="flex items-center space-x-2">
                          <button 
                            onClick={() => handleFeedback(msg.id, 1)}
                            disabled={msg.feedbackSubmitted}
                            className={`p-1.5 rounded transition cursor-pointer hover:bg-slate-900/50 ${
                              msg.feedbackValue === 1 ? 'text-emerald-400 bg-emerald-500/10' : 'text-slate-400 hover:text-slate-200'
                            }`}
                            title="Helpful"
                          >
                            <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
                              <path d="M2 13c0 1.25.462 2.4 1.214 3.286L8 21v-2c0-.55.45-1 1-1h7a3 3 0 003-3V9a3 3 0 00-3-3h-1.42l.504-2.518c.114-.567-.14-1.155-.632-1.425a1.18 1.18 0 00-1.288.167L8.707 6.707A2 2 0 008 8.12V13H2z"></path>
                            </svg>
                          </button>
                          <button 
                            onClick={() => handleFeedback(msg.id, -1)}
                            disabled={msg.feedbackSubmitted}
                            className={`p-1.5 rounded transition cursor-pointer hover:bg-slate-900/50 ${
                              msg.feedbackValue === -1 ? 'text-red-400 bg-red-500/10' : 'text-slate-400 hover:text-slate-200'
                            }`}
                            title="Not helpful"
                          >
                            <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24" style={{ transform: 'rotate(180deg)' }}>
                              <path d="M2 13c0 1.25.462 2.4 1.214 3.286L8 21v-2c0-.55.45-1 1-1h7a3 3 0 003-3V9a3 3 0 00-3-3h-1.42l.504-2.518c.114-.567-.14-1.155-.632-1.425a1.18 1.18 0 00-1.288.167L8.707 6.707A2 2 0 008 8.12V13H2z"></path>
                            </svg>
                          </button>
                        </div>
                      </div>
                    )}
                  </>
                )}
              </div>

              {/* Citations list for this assistant bubble */}
              {!isUser && msg.sources && msg.sources.length > 0 && (
                <div className="w-full mt-3 pl-2 border-l border-purple-500/20 max-w-full">
                  <h5 className="text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center">
                    <svg className="w-3 h-3 mr-1.5 text-purple-400" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                    </svg>
                    Cited Sources
                  </h5>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {msg.sources.map((source, sourceIdx) => {
                      const uniqueKey = `${msg.id}-${sourceIdx}`;
                      const isExpanded = expandedSourceIndex === uniqueKey;
                      return (
                        <div 
                          key={sourceIdx} 
                          className={`p-3 rounded-xl border transition-all duration-300 cursor-pointer ${
                            isExpanded 
                              ? 'bg-slate-900/60 border-purple-500/40 shadow-lg shadow-purple-500/5' 
                              : 'bg-slate-950/40 border-slate-900 hover:border-slate-800 hover:bg-slate-900/20'
                          }`}
                          onClick={() => setExpandedSourceIndex(isExpanded ? null : uniqueKey)}
                        >
                          <div className="flex justify-between items-start gap-2 mb-1.5">
                            <div className="flex items-center space-x-1.5 min-w-0">
                              <span className="p-0.5 rounded bg-purple-500/10 text-purple-400 shrink-0">
                                <svg className="w-3 h-3" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path>
                                </svg>
                              </span>
                              <span className="text-[11px] font-semibold text-slate-200 truncate">{source.title}</span>
                            </div>
                            <span className={`px-1.5 py-0.5 rounded-full text-[8px] font-bold shrink-0 ${
                              source.similarityScore >= 0.8
                                ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                                : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                            }`}>
                              {(source.similarityScore * 100).toFixed(0)}% Match
                            </span>
                          </div>
                          
                          <p className={`text-slate-400 text-[11px] leading-relaxed transition-all duration-300 ${
                            isExpanded ? '' : 'line-clamp-2'
                          }`}>
                            {source.textSnippet}
                          </p>

                          <div className="mt-1.5 flex items-center justify-end text-[8px] font-bold text-slate-500 uppercase tracking-wider">
                            <span>{isExpanded ? 'Collapse' : 'Expand source'}</span>
                            <svg 
                              className={`w-2.5 h-2.5 ml-0.5 transform transition-transform duration-300 ${isExpanded ? 'rotate-180' : ''}`} 
                              fill="none" 
                              stroke="currentColor" 
                              strokeWidth="2" 
                              viewBox="0 0 24 24"
                            >
                              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7"></path>
                            </svg>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Query Form Section */}
      <form onSubmit={handleSearch} className="mt-auto pt-2 flex flex-col sm:flex-row gap-3 shrink-0">
        <input 
          type="text" required value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask a question about Engineering, Architecture, or General guidelines..."
          className="flex-1 p-3.5 md:p-4 rounded-xl text-xs md:text-sm bg-slate-950/40 border border-slate-800 focus:outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500 text-white placeholder-slate-500"
          disabled={loading}
        />
        <button 
          type="submit" disabled={loading || !query.trim()}
          className="px-6 py-3.5 md:py-4 rounded-xl text-xs md:text-sm font-semibold text-white bg-gradient-to-r from-purple-600 to-cyan-600 hover:opacity-90 transition duration-300 disabled:opacity-50 cursor-pointer shadow-lg whitespace-nowrap flex items-center justify-center space-x-2"
        >
          {loading ? (
            <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
          ) : (
            <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
            </svg>
          )}
          <span>Send</span>
        </button>
      </form>
    </div>
  );
}
